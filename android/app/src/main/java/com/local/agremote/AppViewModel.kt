package com.local.agremote

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class Link { Connecting, Online, NoAntigravity, Offline }

/** id == null 表示还没发第一条消息的新对话。 */
data class ChatState(
    val id: String?,
    val conv: Conversation? = null,
    val steps: List<Step> = emptyList(),
    val queued: List<QueuedMsg> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val model: String = "", // 这个对话最近一次回复用的模型
    val pending: String? = null, // 刚发出、电脑那边还没出现的消息
    val pendingFiles: List<Attachment> = emptyList(),
    val pendingMentions: List<Mention> = emptyList(),
    val pendingBase: Int = 0, // 发送时已有的用户消息条数
    val voiceChats: List<VoiceChatRec> = emptyList(), // 这个对话里的实时语音记录
)

class AppViewModel(app: Application) : AndroidViewModel(app) {
    val prefs = Prefs(app)
    private val api = Api(prefs).also { Translator.api = it }

    private val _paired = MutableStateFlow(prefs.token.isNotBlank())
    val paired = _paired.asStateFlow()

    private val _link = MutableStateFlow(Link.Connecting)
    val link = _link.asStateFlow()

    private val _list = MutableStateFlow<List<Conversation>>(emptyList())
    val list = _list.asStateFlow()

    private val _chat = MutableStateFlow(ChatState(id = null))
    val chat = _chat.asStateFlow()

    // 先用上次存下的模型列表，打开 App 就能显示具体模型
    private val _models = MutableStateFlow(runCatching { Api.parseModels(JSONObject(prefs.modelsJson)).second }.getOrElse { emptyList() })
    val models = _models.asStateFlow()
    private var defaultModel = runCatching { JSONObject(prefs.modelsJson).optString("defaultModel") }.getOrElse { "" }

    /** 当前聊天里选中的模型（枚举名）。 */
    private val _selected = MutableStateFlow(prefs.model.ifBlank { defaultModel })
    val selected = _selected.asStateFlow()
    private var userPicked = false

    private val _account = MutableStateFlow<Account?>(null)
    val account = _account.asStateFlow()

    private val _toast = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toast = _toast.asSharedFlow()
    fun showToast(msg: String) { _toast.tryEmit(msg) }

    /** 编辑排队消息或从命令面板插入草稿时，把内容放回底部输入框（跨页面切换也不丢失）。 */
    private val _pendingDraft = MutableStateFlow<String?>(null)
    val pendingDraft = _pendingDraft.asStateFlow()
    fun insertDraft(text: String) { _pendingDraft.value = text }
    fun clearPendingDraft() { _pendingDraft.value = null }

    /** 每个对话的输入框草稿：常驻 ViewModel 并同步保存到电脑端，切页面/切对话/重开 App 都不丢失。 */
    private val _drafts = MutableStateFlow<Map<String, String>>(emptyMap())
    val drafts = _drafts.asStateFlow()
    private val draftSaveJobs = mutableMapOf<String, Job>()
    fun draftKey(cid: String?) = cid?.takeIf { it.isNotBlank() } ?: "__new__"
    fun getDraft(cid: String?): String = _drafts.value[draftKey(cid)].orEmpty()
    fun updateDraft(cid: String?, text: String) {
        val key = draftKey(cid)
        if (_drafts.value[key].orEmpty() == text) return
        _drafts.value = if (text.isEmpty()) _drafts.value - key else _drafts.value + (key to text)
        draftSaveJobs.remove(key)?.cancel()
        draftSaveJobs[key] = viewModelScope.launch {
            delay(450)
            runCatching { api.saveDraft(key, text) }
        }
    }
    fun clearDraft(cid: String?) {
        val key = draftKey(cid)
        draftSaveJobs.remove(key)?.cancel()
        if (key in _drafts.value) _drafts.value = _drafts.value - key
        viewModelScope.launch { runCatching { api.saveDraft(key, "") } }
    }
    fun loadDrafts() {
        viewModelScope.launch {
            runCatching { api.drafts() }.onSuccess { remote ->
                if (remote.isEmpty()) return@onSuccess
                val cur = _drafts.value.toMutableMap()
                remote.forEach { (k, v) -> if (cur[k].isNullOrEmpty()) cur[k] = v }
                _drafts.value = cur
            }
        }
    }

    /** 当前对话概览 / 子代理 / 后台任务（与电脑 Antigravity 同步）。 */
    private val _overview = MutableStateFlow(OverviewData())
    val overview = _overview.asStateFlow()
    private val _overviewLoading = MutableStateFlow(false)
    val overviewLoading = _overviewLoading.asStateFlow()
    fun loadOverview(id: String) {
        _overviewLoading.value = true
        viewModelScope.launch {
            runCatching { api.overview(id) }
                .onSuccess { if (_chat.value.id == id) _overview.value = it }
                .onFailure { _toast.tryEmit("读取概览失败：" + err(it)) }
            _overviewLoading.value = false
        }
    }
    suspend fun loadTaskLog(cid: String, taskId: String): String =
        runCatching { api.taskLog(cid, taskId) }.getOrElse { "读取日志失败：" + err(it) }
    suspend fun loadSubagentSteps(subagentId: String): List<Step> =
        runCatching { api.conversation(subagentId, 0).steps }.getOrElse { emptyList() }

    /** 内置浏览器状态：与电脑 Antigravity 内置浏览器标签页同步。 */
    private val _browserOpen = MutableStateFlow(false)
    val browserOpen = _browserOpen.asStateFlow()
    private val _browserExplicitUrl = MutableStateFlow<String?>(null)
    val browserExplicitUrl = _browserExplicitUrl.asStateFlow()
    private val _pcBrowserTabs = MutableStateFlow(BrowserTabsData())
    val pcBrowserTabs = _pcBrowserTabs.asStateFlow()
    private val _pcBrowserLoading = MutableStateFlow(false)
    val pcBrowserLoading = _pcBrowserLoading.asStateFlow()

    fun openBrowser(url: String? = null) {
        _browserExplicitUrl.value = url?.trim()?.takeIf { it.isNotBlank() }
        _browserOpen.value = true
        refreshBrowserTabs()
    }
    fun closeBrowser() { _browserOpen.value = false; _browserExplicitUrl.value = null }
    fun refreshBrowserTabs(cid: String = _chat.value.id ?: "") {
        _pcBrowserLoading.value = true
        viewModelScope.launch {
            runCatching { api.browserTabs(cid) }
                .onSuccess { _pcBrowserTabs.value = it }
            _pcBrowserLoading.value = false
        }
    }
    fun closeBrowserTab(tab: BrowserTab, curCid: String = _chat.value.id ?: "", onDone: (BrowserTabsData) -> Unit = {}) {
        val cur = _pcBrowserTabs.value
        val optimistic = cur.copy(
            tabs = cur.tabs.filterNot { it.id == tab.id && it.cid == tab.cid },
            otherTabs = cur.otherTabs.filterNot { it.id == tab.id && it.cid == tab.cid },
        )
        _pcBrowserTabs.value = optimistic
        onDone(optimistic)
        viewModelScope.launch {
            runCatching { api.closeBrowserTab(tab.cid, tab.id, curCid) }
                .onSuccess { _pcBrowserTabs.value = it; onDone(it) }
                .onFailure {
                    _pcBrowserTabs.value = cur
                    showToast("关闭标签页失败：${it.message ?: "请重试"}")
                }
        }
    }
    /** 电脑实况视频流：连续收帧；断开或出错时结束（调用方再决定重连或退回截图）。 */
    fun pcScreenStream(width: Int = 1280, fps: Int = 20): kotlinx.coroutines.flow.Flow<android.graphics.Bitmap> = kotlinx.coroutines.flow.flow {
        val call = api.browserStreamCall(width, fps)
        currentCoroutineContext()[Job]?.invokeOnCompletion { call.cancel() } // 离开页面时立刻断开
        call.execute().use { r ->
            if (!r.isSuccessful) return@use
            val input = java.io.DataInputStream(java.io.BufferedInputStream(r.body.byteStream(), 1 shl 16))
            while (true) {
                val n = input.readInt()
                if (n <= 0 || n > (16 shl 20)) break
                val bytes = ByteArray(n)
                input.readFully(bytes)
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, n)?.let { emit(it) }
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun capturePcBrowser(mode: String): android.graphics.Bitmap? = withContext(Dispatchers.IO) {
        val bytes = runCatching { api.browserScreen(mode) }.getOrNull() ?: return@withContext null
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
    suspend fun loadBrowserActivity(cid: String): BrowserActivityData =
        runCatching { api.browserActivity(cid) }.getOrElse { BrowserActivityData() }
    fun sendBrowserInput(
        action: String,
        mode: String = "screen",
        nx: Float = 0.5f,
        ny: Float = 0.5f,
        delta: Int = 0,
        text: String = "",
        key: String = "",
        onDone: () -> Unit = {},
    ) {
        viewModelScope.launch {
            runCatching { api.browserInput(action, mode, nx, ny, delta, text, key) }
                .onSuccess { onDone() }
                .onFailure { _toast.tryEmit("操控失败：" + err(it)) }
        }
    }
    suspend fun readPcFieldText(mode: String = "screen"): String? =
        runCatching { api.browserInput("readfield", mode).optString("text") }.getOrNull()
    suspend fun fetchPcCursor(lastId: String): Pair<String, PcCursor?>? =
        runCatching { api.browserCursor(lastId) }.getOrNull()

    /** 外观与主题配置（即时生效并持久化）。 */
    private val _theme = MutableStateFlow(prefs.themeConfig)
    val theme = _theme.asStateFlow()
    fun updateTheme(cfg: ThemeConfig) {
        _theme.value = cfg
        prefs.themeConfig = cfg
    }

    private val _language = MutableStateFlow(prefs.language)
    val language = _language.asStateFlow()
    fun updateLanguage(code: String) {
        _language.value = code
        prefs.language = code
    }

    private val _linkOpenMode = MutableStateFlow(prefs.linkOpenMode)
    val linkOpenMode = _linkOpenMode.asStateFlow()
    fun updateLinkOpenMode(mode: String) {
        _linkOpenMode.value = mode
        prefs.linkOpenMode = mode
    }

    private val _pendingLinkUrl = MutableStateFlow<String?>(null)
    val pendingLinkUrl = _pendingLinkUrl.asStateFlow()
    fun dismissPendingLink() { _pendingLinkUrl.value = null }
    fun confirmOpenLink(url: String, mode: String, remember: Boolean) {
        _pendingLinkUrl.value = null
        if (remember) updateLinkOpenMode(mode)
        when (mode) {
            "system" -> com.local.agremote.ui.openWebLink(getApplication(), url)
            "chooser" -> com.local.agremote.ui.openWebLinkChooser(getApplication(), url)
            else -> openBrowser(url)
        }
    }

    /** 已切换为中文翻译的思考过程（key: "$convId:$stepIndex"），滚出屏幕或折叠后不丢失。 */
    private val _showZh = MutableStateFlow<Set<String>>(emptySet())
    val showZh = _showZh.asStateFlow()

    fun setThinkingZh(convId: String, stepIndex: Int, zh: Boolean) {
        val k = "$convId:$stepIndex"
        _showZh.value = if (zh) _showZh.value + k else _showZh.value - k
    }

    /** 回复的点赞(1)/点踩(-1)状态（key: "$convId:$stepIndex"），本地持久化并同步上报。 */
    private val _votes = MutableStateFlow(runCatching {
        val o = JSONObject(prefs.votesJson)
        o.keys().asSequence().associateWith { o.optInt(it, 0) }.filterValues { it != 0 }
    }.getOrElse { emptyMap() })
    val votes = _votes.asStateFlow()

    fun vote(stepIndex: Int, v: Int) {
        val id = _chat.value.id ?: return
        val k = "$id:$stepIndex"
        val next = _votes.value.toMutableMap()
        if (v == 0) next.remove(k) else next[k] = v
        _votes.value = next
        prefs.votesJson = JSONObject().apply { next.forEach { (key, value) -> put(key, value) } }.toString()
        viewModelScope.launch { runCatching { api.feedback(id, stepIndex, v) } }
    }

    private var events: Job? = null

    // ---------- 项目 ----------
    private var newChatProject = ""
    private val _newChatProjectName = MutableStateFlow("")
    val newChatProjectName = _newChatProjectName.asStateFlow()
    private val _projects = MutableStateFlow<List<Project>>(emptyList())
    val projects = _projects.asStateFlow()
    fun refreshProjects() {
        viewModelScope.launch { runCatching { api.projects() }.onSuccess { _projects.value = it } }
    }
    private fun projectOp(ok: String? = null, block: suspend () -> Unit) {
        viewModelScope.launch {
            runCatching { block() }.onSuccess { ok?.let { _toast.tryEmit(it) }; refreshProjects(); refreshList() }
                .onFailure { _toast.tryEmit("操作失败：" + err(it)) }
        }
    }
    fun createProject(name: String, folder: String) = projectOp("已新建项目") { api.createProject(name, folder) }
    fun renameProject(id: String, name: String) = projectOp { api.renameProject(id, name) }
    fun deleteProject(id: String) = projectOp("已删除项目（对话还在）") { api.deleteProject(id) }
    fun moveConversation(cid: String, projectId: String) = projectOp(if (projectId.isBlank()) "已移出项目" else "已移到项目") {
        api.moveConversation(cid, projectId)
    }

    // ---------- 自动化 ----------
    private val _automations = MutableStateFlow<List<Automation>?>(null)
    val automations = _automations.asStateFlow()
    fun loadAutomations() {
        viewModelScope.launch { runCatching { api.automations() }.onSuccess { _automations.value = it }.onFailure { _toast.tryEmit("读取自动化失败：" + err(it)); _automations.value = emptyList() } }
    }
    fun saveAutomation(id: String?, name: String, schedule: String, prompt: String, model: String, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            runCatching { api.saveAutomation(id, name, schedule, prompt, model) }
                .onSuccess { _toast.tryEmit(if (id == null) "已新建自动化" else "已保存"); loadAutomations(); onDone(true) }
                .onFailure { _toast.tryEmit("保存失败：" + err(it)); onDone(false) }
        }
    }
    fun automationAction(id: String, action: String) {
        viewModelScope.launch {
            runCatching { api.automationAction(id, action) }.onFailure { _toast.tryEmit("操作失败：" + err(it)) }
            loadAutomations()
        }
    }

    // ---------- 插件 ----------
    private val _plugins = MutableStateFlow<Pair<List<Plugin>, List<Plugin>>?>(null)
    val plugins = _plugins.asStateFlow()
    fun loadPlugins() {
        viewModelScope.launch { runCatching { api.plugins() }.onSuccess { _plugins.value = it }.onFailure { _toast.tryEmit("读取插件失败：" + err(it)); _plugins.value = emptyList<Plugin>() to emptyList() } }
    }
    fun pluginAction(id: String, action: String) {
        viewModelScope.launch {
            runCatching { api.pluginAction(id, action) }
                .onSuccess { _toast.tryEmit(if (action == "install") "已安装，可能需要在电脑上登录对应账号" else "已删除") }
                .onFailure { _toast.tryEmit("操作失败：" + err(it)) }
            loadPlugins(); loadExtras()
        }
    }

    // ---------- 插件市场 ----------
    private val _market = MutableStateFlow<Market?>(null)
    val market = _market.asStateFlow()
    private var marketAll: Market? = null
    private var searchJob: Job? = null
    fun loadMarket(q: String = "") {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (q.isNotBlank()) delay(350) // 输入时稍等再搜
            runCatching { api.market(q) }
                .onSuccess { m -> if (q.isBlank()) marketAll = m; _market.value = if (q.isBlank()) m else m.copy(featured = emptyList(), groups = emptyList()) }
                .onFailure { _toast.tryEmit("读取插件市场失败：" + err(it)); if (_market.value == null) _market.value = Market(emptyList(), emptyList(), emptyList(), emptySet()) }
        }
    }
    private val _installing = MutableStateFlow<Set<String>>(emptySet())
    val installing = _installing.asStateFlow()
    fun marketAction(id: String, action: String) {
        _installing.value = _installing.value + id
        viewModelScope.launch {
            runCatching { api.marketAction(id, action) }
                .onSuccess { _toast.tryEmit(if (action == "install") "已添加。有的插件需要在电脑上登录对应账号后才能用" else "已移除") }
                .onFailure { _toast.tryEmit((if (action == "install") "添加" else "移除") + "失败：" + err(it)) }
            _installing.value = _installing.value - id
            loadMarket(); loadCustomizations(); loadExtras()
        }
    }
    /** 插件图标（电脑代取；SVG 用 AndroidSVG 画成位图）。 */
    private val logoCache = android.util.LruCache<String, android.graphics.Bitmap>(160)
    suspend fun logoBitmap(url: String): android.graphics.Bitmap? {
        if (url.isBlank()) return null
        logoCache.get(url)?.let { return it }
        return runCatching {
            val bytes = api.logo(url)
            withContext(Dispatchers.Default) {
                val head = String(bytes, 0, minOf(bytes.size, 200)).trimStart()
                if (head.startsWith("<svg") || head.startsWith("<?xml")) {
                    val svg = com.caverock.androidsvg.SVG.getFromString(String(bytes))
                    val px = 144
                    if (svg.documentViewBox == null) svg.setDocumentViewBox(0f, 0f, svg.documentWidth.takeIf { it > 0 } ?: 24f, svg.documentHeight.takeIf { it > 0 } ?: 24f)
                    svg.setDocumentWidth(px.toFloat()); svg.setDocumentHeight(px.toFloat())
                    val bmp = android.graphics.Bitmap.createBitmap(px, px, android.graphics.Bitmap.Config.ARGB_8888)
                    svg.renderToCanvas(android.graphics.Canvas(bmp))
                    bmp
                } else android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        }.getOrNull()?.also { logoCache.put(url, it) }
    }

    // ---------- 自定义扩展 ----------
    private val _customizations = MutableStateFlow<List<Customization>?>(null)
    val customizations = _customizations.asStateFlow()
    fun loadCustomizations() {
        viewModelScope.launch { runCatching { api.customizations() }.onSuccess { _customizations.value = it }.onFailure { _toast.tryEmit("读取失败：" + err(it)); _customizations.value = emptyList() } }
    }

    // ---------- 附件 / 权限 / Skills / 连接器 ----------
    private val _attachments = MutableStateFlow<List<Attachment>>(emptyList())
    val attachments = _attachments.asStateFlow()
    fun addAttachment(a: Attachment) {
        if (_attachments.value.size >= 10) { _toast.tryEmit("最多 10 个附件"); return }
        _attachments.value = _attachments.value + a
    }
    fun removeAttachment(a: Attachment) { _attachments.value = _attachments.value - a }

    /** 输入框上方 @ 的连接器（和电脑上一样，发送时作为上下文带过去）。 */
    private val _mentions = MutableStateFlow<List<Connector>>(emptyList())
    val mentions = _mentions.asStateFlow()
    fun addMention(c: Connector) {
        if (_mentions.value.none { it.key == c.key && it.kind == c.kind }) _mentions.value = _mentions.value + c
    }
    fun removeMention(c: Connector) { _mentions.value = _mentions.value.filterNot { it.key == c.key && it.kind == c.kind } }

    /** 读取相机/相册/文件选出的内容（在 ViewModel 里做，菜单关了也不会中断）。 */
    fun addFromUris(uris: List<android.net.Uri>) {
        if (uris.isEmpty()) return
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            uris.forEach { u ->
                runCatching { Attachments.load(ctx, u) }
                    .onSuccess { addAttachment(it) }
                    .onFailure { _toast.tryEmit("附件读取失败：" + (it.message ?: it.javaClass.simpleName)) }
            }
        }
    }

    /** 把从剪贴板或输入法粘贴进来的图片字节转成附件。 */
    fun addPastedBytes(name: String, mime: String, bytes: ByteArray) {
        viewModelScope.launch {
            runCatching { Attachments.loadBytes(name, mime, bytes) }
                .onSuccess { addAttachment(it) }
                .onFailure { _toast.tryEmit("粘贴图片失败：" + (it.message ?: it.javaClass.simpleName)) }
        }
    }

    /** 权限 = 电脑上 Antigravity 的全局权限设置，两边同步。 */
    private val _preset = MutableStateFlow("")
    val preset = _preset.asStateFlow()
    /** 电脑上当前选的模型（新对话默认用它）。 */
    private var pcModel = ""

    fun setPreset(p: String) {
        val old = _preset.value
        _preset.value = p
        viewModelScope.launch {
            runCatching { api.setSync(preset = p) }.onFailure { _preset.value = old; _toast.tryEmit("设置权限失败：" + err(it)) }
        }
    }

    fun loadSync() {
        viewModelScope.launch {
            runCatching { api.sync() }.onSuccess { (m, p) -> applySync(m, p) }
        }
    }

    /** 电脑那边的选择变了：权限和模型都默认实时跟随电脑（新对话和已有对话都同步）。 */
    private fun applySync(model: String, preset: String) {
        if (preset.isNotBlank()) _preset.value = preset
        if (model.isNotBlank()) {
            pcModel = model
            if (_selected.value != model) {
                _selected.value = model
                prefs.model = model
            }
        }
    }

    private val _skills = MutableStateFlow<List<Skill>>(emptyList())
    val skills = _skills.asStateFlow()
    private val _mcp = MutableStateFlow<List<McpServer>?>(null)
    val mcp = _mcp.asStateFlow()
    private val _connectors = MutableStateFlow<List<Connector>?>(null)
    val connectors = _connectors.asStateFlow()
    fun loadConnectors() {
        viewModelScope.launch { runCatching { api.connectors() }.onSuccess { _connectors.value = it }.onFailure { _connectors.value = emptyList(); _toast.tryEmit("读取连接器失败：" + err(it)) } }
    }
    fun toggleConnector(c: Connector, on: Boolean) {
        _connectors.value = _connectors.value?.map { if (it.key == c.key && it.kind == c.kind) it.copy(enabled = on) else it }
        viewModelScope.launch {
            runCatching { api.toggleConnector(c, on) }.onFailure { _toast.tryEmit("操作失败：" + err(it)) }
            loadConnectors()
        }
    }

    fun loadExtras() {
        loadConnectors()
        viewModelScope.launch { runCatching { api.skills() }.onSuccess { _skills.value = it } }
        viewModelScope.launch { runCatching { api.mcpServers() }.onSuccess { _mcp.value = it }.onFailure { _mcp.value = emptyList() } }
    }
    /**
     * 提前把插件简介、分类、技能说明这些基本不变的内容翻译好并存下来（思考过程除外），
     * 打开“自定义”页、点翻译时直接显示，不用现等谷歌翻译。
     */
    private suspend fun prefetchTranslations() {
        val lang = com.local.agremote.ui.I18n.currentLang
        val m = marketAll ?: runCatching { api.market("") }.getOrNull()?.also { marketAll = it; if (_market.value == null) _market.value = it }
        val sk = _skills.value.ifEmpty { runCatching { api.skills() }.getOrNull()?.also { _skills.value = it } ?: emptyList() }
        val texts = buildList {
            m?.items?.forEach { add(zh(it.description)) }
            m?.groups?.forEach { add(zh(it.name)); add(zh(it.description)) }
            sk.forEach { add(zh(it.description)) }
        }.filter { t ->
            // 和页面上的判断一致：目标是英文时翻非英文的，其他语言时翻英文的
            t.isNotBlank() && (if (lang == "en") !com.local.agremote.ui.needsGoogleTranslate(t) else com.local.agremote.ui.needsGoogleTranslate(t))
        }
        runCatching { Translator.prefetch(texts, lang) }
    }

    fun toggleMcp(name: String, enabled: Boolean) {
        viewModelScope.launch {
            runCatching { api.mcpToggle(name, enabled) }.onFailure { _toast.tryEmit("操作失败：" + err(it)) }
            runCatching { api.mcpServers() }.onSuccess { _mcp.value = it }
        }
    }

    /** 消息里附件图片的缓存。 */
    private val mediaCache = android.util.LruCache<String, android.graphics.Bitmap>(24)
    /** 取消息附件的原始内容（图片、文件）。 */
    suspend fun mediaBytes(a: StepAttachment): ByteArray = api.media(a.path, _chat.value.id ?: "", a.ref)

    private val _preview = MutableStateFlow<com.local.agremote.ui.PreviewItem?>(null)
    val preview = _preview.asStateFlow()
    fun openPreview(item: com.local.agremote.ui.PreviewItem?) { _preview.value = item }
    fun previewOf(a: StepAttachment) = com.local.agremote.ui.PreviewItem(a.name, a.mime) {
        runCatching { mediaBytes(a) }.onFailure { _toast.tryEmit("读取附件失败：" + err(it)) }.getOrNull()
    }

    /** 回复里的链接：电脑上的文件（file:///C:/… 或 C:/…）从电脑取回来预览；APK 下载用系统浏览器，其他网页走内置浏览器（电脑网络）。 */
    fun openLink(url: String) {
        val trimmed = url.trim()
        val m = Regex("^(?:file:///?)?([A-Za-z]:[\\\\/].+)$").find(trimmed)
        if (m != null) {
            val path = runCatching { java.net.URLDecoder.decode(m.groupValues[1].replace("+", "%2B"), "UTF-8") }
                .getOrElse { m.groupValues[1] }.substringBefore('#').replace('\\', '/')
            val name = path.substringAfterLast('/')
            openPreview(previewOf(StepAttachment(name, Attachments.mimeOf(name), path)))
        } else if ((trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true)) &&
            !trimmed.endsWith("/apk", true) && !trimmed.endsWith(".apk", true)) {
            when (_linkOpenMode.value) {
                "system" -> com.local.agremote.ui.openWebLink(getApplication(), trimmed)
                "chooser" -> com.local.agremote.ui.openWebLinkChooser(getApplication(), trimmed)
                "builtin" -> openBrowser(trimmed)
                else -> _pendingLinkUrl.value = trimmed
            }
        } else {
            com.local.agremote.ui.openWebLink(getApplication(), trimmed)
        }
    }

    suspend fun mediaBitmap(a: StepAttachment): android.graphics.Bitmap? {
        val key = a.path.ifBlank { (_chat.value.id ?: "") + "#" + a.ref }
        mediaCache.get(key)?.let { return it }
        return runCatching {
            val bytes = mediaBytes(a)
            withContext(Dispatchers.Default) {
                val o = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
                var k = 1
                while (maxOf(o.outWidth, o.outHeight) / (k * 2) >= 720) k *= 2
                android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, android.graphics.BitmapFactory.Options().apply { inSampleSize = k })
            }
        }.getOrNull()?.also { mediaCache.put(key, it) }
    }

    suspend fun videoThumbBitmap(a: StepAttachment): android.graphics.Bitmap? {
        val key = "v:" + a.path.ifBlank { (_chat.value.id ?: "") + "#" + a.ref }
        mediaCache.get(key)?.let { return it }
        return runCatching {
            val bytes = mediaBytes(a)
            withContext(Dispatchers.IO) {
                val dir = java.io.File(getApplication<Application>().cacheDir, "vthumb").apply { mkdirs() }
                val ext = a.name.substringAfterLast('.', "mp4").take(6)
                val f = java.io.File(dir, "${key.hashCode().toUInt()}.$ext")
                f.writeBytes(bytes)
                Attachments.videoThumbFromFile(f)
            }
        }.getOrNull()?.also { mediaCache.put(key, it) }
    }

    /** Antigravity 自带的中文词条：有官方译名就用。 */
    private val _i18n = MutableStateFlow(runCatching {
        JSONObject(prefs.i18nJson).let { o -> o.keys().asSequence().associateWith { o.optString(it) } }
    }.getOrElse { emptyMap() })
    val i18n = _i18n.asStateFlow()
    fun zh(s: String): String = _i18n.value[s] ?: s

    // ---------- 语音输入状态 ----------
    private val _recording = MutableStateFlow(false)
    val recording = _recording.asStateFlow()
    private val _sttFinalizing = MutableStateFlow(false)
    val sttFinalizing = _sttFinalizing.asStateFlow()
    private val _sttText = MutableStateFlow("")
    val sttText = _sttText.asStateFlow()
    /** 识别结果里已经确认的前半段（其余是还会变的临时结果）。 */
    private val _sttStable = MutableStateFlow("")
    val sttStable = _sttStable.asStateFlow()
    /** 识别结果：text + 是否直接发送。 */
    private val _sttResult = MutableSharedFlow<Pair<String, Boolean>>(extraBufferCapacity = 4)
    val sttResult = _sttResult.asSharedFlow()
    /** 声纹：最近若干个 50ms 片段的音量（0..1）。 */
    private val _levels = MutableStateFlow<List<Float>>(emptyList())
    val levels = _levels.asStateFlow()
    @Volatile private var stopMode = "keep" // keep / send / cancel
    @Volatile private var sttSid: String? = null
    private var recJob: Job? = null

    // ---------- 配对 ----------
    private val _hosts = MutableStateFlow(prefs.savedHosts)
    val hosts = _hosts.asStateFlow()
    private val _activeHostId = MutableStateFlow(prefs.activeHostId.ifBlank { prefs.savedHosts.firstOrNull()?.id ?: "" })
    val activeHostId = _activeHostId.asStateFlow()

    private fun applyHost(h: SavedHost, reconnect: Boolean = true) {
        prefs.activeHostId = h.id
        prefs.host = h.host
        prefs.port = h.port
        prefs.token = h.token
        _activeHostId.value = h.id
        _paired.value = h.token.isNotBlank()
        if (reconnect) {
            stop()
            _link.value = Link.Connecting
            _list.value = emptyList()
            _chat.value = ChatState(id = null)
            _models.value = emptyList()
            _account.value = null
            _projects.value = emptyList()
            _overview.value = OverviewData()
            _drafts.value = emptyMap()
            _pcBrowserTabs.value = BrowserTabsData()
            start()
        }
    }

    suspend fun pair(host: String, port: Int, code: String, deviceName: String = ""): String? {
        val cleanHost = host.trim()
        val cleanName = deviceName.trim().ifBlank { cleanHost }
        return runCatching {
            val tok = api.pairAt(cleanHost, port, code.trim(), Build.MODEL ?: "手机")
            val cur = prefs.savedHosts.toMutableList()
            val existIdx = cur.indexOfFirst { it.host.equals(cleanHost, true) && it.port == port }
            val entry = if (existIdx >= 0) {
                cur[existIdx].copy(name = cleanName, token = tok).also { cur[existIdx] = it }
            } else {
                SavedHost(java.util.UUID.randomUUID().toString(), cleanName, cleanHost, port, tok).also { cur.add(it) }
            }
            prefs.savedHosts = cur
            _hosts.value = cur
            applyHost(entry, reconnect = true)
            null
        }.getOrElse { it.message ?: "连接失败" }
    }

    fun switchHost(id: String) {
        val target = _hosts.value.find { it.id == id } ?: return
        if (target.id == _activeHostId.value && _paired.value) return
        applyHost(target, reconnect = true)
        _toast.tryEmit(target.name)
    }

    fun updateHost(id: String, name: String, host: String, port: Int) {
        val cleanHost = host.trim()
        if (cleanHost.isBlank()) return
        val cleanName = name.trim().ifBlank { cleanHost }
        val cur = _hosts.value.map {
            if (it.id == id) it.copy(name = cleanName, host = cleanHost, port = port) else it
        }
        prefs.savedHosts = cur
        _hosts.value = cur
        if (id == _activeHostId.value) {
            val active = cur.find { it.id == id } ?: return
            val addrChanged = prefs.host != active.host || prefs.port != active.port
            applyHost(active, reconnect = addrChanged)
        }
        _toast.tryEmit("已保存")
    }

    fun removeHost(id: String) {
        val cur = _hosts.value.filterNot { it.id == id }
        prefs.savedHosts = cur
        _hosts.value = cur
        if (id == _activeHostId.value) {
            val next = cur.firstOrNull()
            if (next != null) {
                applyHost(next, reconnect = true)
            } else {
                prefs.activeHostId = ""
                prefs.token = ""
                _activeHostId.value = ""
                _paired.value = false
                stop()
            }
        }
    }

    fun unpair() {
        val curId = _activeHostId.value
        if (curId.isNotBlank() && _hosts.value.any { it.id == curId }) {
            removeHost(curId)
        } else {
            prefs.token = ""
            _paired.value = false
            stop()
        }
    }

    // ---------- 前后台 ----------
    fun start() {
        if (!_paired.value || events?.isActive == true) return
        refreshList()
        loadModels()
        loadSync()
        Translator.init(getApplication<Application>().filesDir)
        viewModelScope.launch {
            if (_i18n.value.isEmpty()) runCatching { api.i18n() }.onSuccess { _i18n.value = it }
            prefetchTranslations()
        }
        if (_account.value == null) viewModelScope.launch { runCatching { api.account() }.onSuccess { _account.value = it } }
        loadVoiceConfig()
        _chat.value.id?.let { reloadChat(it, full = false) }
        events = viewModelScope.launch(Dispatchers.IO) { eventLoop() }
    }

    fun stop() {
        events?.cancel(); events = null
    }

    private suspend fun eventLoop() {
        var backoff = 1000L
        while (currentCoroutineContext().isActive) {
            try {
                api.streamClient.newCall(api.eventsRequest()).execute().use { resp ->
                    if (resp.code == 401) { withContext(Dispatchers.Main) { unpair() }; return }
                    if (!resp.isSuccessful) throw ApiException(resp.code, "HTTP ${resp.code}")
                    backoff = 1000L
                    val src = resp.body.source()
                    while (currentCoroutineContext().isActive) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data: ")) continue
                        val ev = JSONObject(line.removePrefix("data: "))
                        when (ev.optString("type")) {
                            "alive" -> withContext(Dispatchers.Main) {
                                val wasOffline = _link.value != Link.Online
                                _link.value = if (ev.optBoolean("ls")) Link.Online else Link.NoAntigravity
                                // 连上或重连时补拉模型列表与电脑当前同步状态
                                if (_link.value == Link.Online) {
                                    if (_models.value.isEmpty() && !modelsLoading) loadModels()
                                    if (wasOffline) { loadSync(); loadDrafts() }
                                }
                            }
                            "stt" -> if (ev.optString("sid") == sttSid) { _sttText.value = ev.optString("text"); _sttStable.value = ev.optString("stable") }
                            "draft" -> withContext(Dispatchers.Main) {
                                val k = ev.optString("cid")
                                val txt = ev.optString("text")
                                // 本机正在保存这条草稿时不让电脑端的旧值覆盖（保存完的任务不算）
                                if (k.isNotBlank() && draftSaveJobs[k]?.isActive != true) {
                                    _drafts.value = if (txt.isEmpty()) _drafts.value - k else _drafts.value + (k to txt)
                                }
                            }
                            "sync" -> withContext(Dispatchers.Main) {
                                applySync(ev.optString("model"), ev.optString("permissionPreset"))
                                ev.optJSONObject("voice")?.let { v -> _voiceCfg.value = _voiceCfg.value.copy(settings = parseVoiceSettings(v)) }
                                if (_market.value != null) loadMarket()
                            }
                            "changed" -> withContext(Dispatchers.Main) {
                                val ids = ev.optJSONArray("ids") ?: JSONArray()
                                refreshList()
                                val cur = _chat.value.id
                                if (cur != null && (0 until ids.length()).any { ids.getString(it) == cur }) reloadChat(cur, full = false)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!currentCoroutineContext().isActive) return
            }
            _link.value = Link.Offline
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(15000L)
        }
    }

    // ---------- 列表 ----------
    /** 用户手动在菜单里标为未读的对话，避免自动已读立刻覆盖。 */
    private val manuallyUnread = mutableSetOf<String>()
    private var lastViewedId = ""
    private var lastViewedAt = 0L

    private fun markViewed(id: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && id == lastViewedId && now - lastViewedAt < 1500L) return
        lastViewedId = id
        lastViewedAt = now
        _list.value = _list.value.map { if (it.id == id && it.unread) it.copy(unread = false) else it }
        _chat.value.let { st -> if (st.id == id && st.conv?.unread == true) _chat.value = st.copy(conv = st.conv.copy(unread = false)) }
        viewModelScope.launch { runCatching { api.annotate(id, JSONObject().put("viewed", true)) } }
    }

    fun refreshList() {
        refreshProjects()
        viewModelScope.launch {
            runCatching { api.conversations() }
                .onSuccess { raw ->
                    val cur = _chat.value.id
                    val autoRead = cur != null && AppState.foreground && cur !in manuallyUnread
                    val list = if (autoRead && raw.any { it.id == cur && it.unread }) {
                        markViewed(cur)
                        raw.map { if (it.id == cur) it.copy(unread = false) else it }
                    } else raw
                    _list.value = list
                    if (_link.value == Link.Connecting) _link.value = Link.Online
                    cur?.let { id -> list.find { c -> c.id == id }?.let { c -> _chat.value = _chat.value.copy(conv = c) } }
                }
                .onFailure { if (it is ApiException && it.code == 401) unpair() }
        }
    }

    // ---------- 模型 / 账户 ----------
    @Volatile private var modelsLoading = false

    fun loadModels() {
        if (modelsLoading) return
        modelsLoading = true
        viewModelScope.launch {
            runCatching { api.models() }.also { modelsLoading = false }.onSuccess { (def, list) ->
                defaultModel = def
                _models.value = list
                val preferred = pcModel.takeIf { m -> m.isNotBlank() && list.any { it.model == m } }
                if (preferred != null) {
                    _selected.value = preferred
                    prefs.model = preferred
                } else if (_selected.value.isBlank() || list.none { it.model == _selected.value }) {
                    _selected.value = prefs.model.takeIf { m -> list.any { it.model == m } } ?: def
                }
            }
        }
    }

    fun pickModel(model: String) {
        val old = _selected.value
        _selected.value = model
        userPicked = true
        prefs.model = model
        pcModel = model
        viewModelScope.launch {
            runCatching { api.setSync(model = model) }
                .onFailure {
                    _toast.tryEmit("同步模型到电脑失败：" + err(it))
                    if (_selected.value == model && old.isNotBlank()) { _selected.value = old; prefs.model = old; pcModel = old }
                }
        } // 电脑上的选择也跟着变
    }

    fun loadAccount() {
        viewModelScope.launch {
            runCatching { api.account() }.onSuccess { _account.value = it }.onFailure { _toast.tryEmit("读取额度失败：" + err(it)) }
            loadModels()
        }
    }

    /** 账号头像（侧边栏左下角，点开是设置）。 */
    private val _avatar = MutableStateFlow<android.graphics.Bitmap?>(null)
    val avatar = _avatar.asStateFlow()
    fun loadAvatar() {
        viewModelScope.launch {
            runCatching { api.avatar() }.getOrNull()?.let { b ->
                withContext(Dispatchers.Default) { android.graphics.BitmapFactory.decodeByteArray(b, 0, b.size) }?.let { _avatar.value = it }
            }
        }
    }

    /** 静默刷新额度（顶栏的 5 小时额度圆环用），失败不提示。 */
    fun refreshQuota() {
        viewModelScope.launch { runCatching { api.account() }.onSuccess { _account.value = it } }
    }

    /** 当前模型所在额度组的 5 小时限额（比如 Opus 5.5 属于“Claude and GPT models”）。 */
    fun fiveHourBucket(model: String): QuotaBucket? {
        val a = _account.value ?: return null
        val m = _models.value.find { it.model == model } ?: return null
        val names = listOf(m.label, m.family).filter { it.isNotBlank() }
        val g = a.groups.find { g -> g.models.any { gm -> names.any { n -> n.startsWith(gm, true) || gm.startsWith(n, true) } } } ?: return null
        return g.buckets.find { it.window == "5h" } ?: g.buckets.find { it.name.contains("Five", true) }
    }

    // ---------- 对话 ----------
    /** 在某个项目里开新对话。 */
    fun newChatInProject(projectId: String) {
        newChat()
        newChatProject = projectId
        _newChatProjectName.value = _projects.value.find { it.id == projectId }?.name ?: ""
    }

    fun newChat() {
        newChatProject = ""
        _newChatProjectName.value = ""
        _chat.value = ChatState(id = null)
        userPicked = false
        _selected.value = pcModel.takeIf { m -> m.isNotBlank() && (_models.value.isEmpty() || _models.value.any { it.model == m }) }
            ?: prefs.model.takeIf { m -> _models.value.any { it.model == m } } ?: defaultModel
    }

    fun openChat(id: String) {
        manuallyUnread.remove(id)
        if (_chat.value.id == id) {
            if (_list.value.find { it.id == id }?.unread == true) markViewed(id, force = true)
            return
        }
        userPicked = false
        _chat.value = ChatState(id, conv = _list.value.find { it.id == id }?.copy(unread = false), loading = true)
        reloadChat(id, full = true)
        markViewed(id, force = true)
    }

    private var chatJob: Job? = null
    private var chatJobId: String? = null
    private var chatPending = false
    private var chatPendingFull = false

    /** 增量刷新：从第一个还没结束的步骤开始重新拉，前面已完成的保留。 */
    fun reloadChat(id: String, full: Boolean) {
        // 旧对话的发送/排队等回调晚到时，别把当前对话正在进行的加载取消掉
        if (_chat.value.id != id) return
        // 同一个对话正在拉：记下来，拉完再补一次；换了对话：直接取消旧的（否则新对话会一直卡在加载中）
        if (chatJob?.isActive == true && chatJobId == id) { chatPending = true; chatPendingFull = chatPendingFull || full; return }
        chatJob?.cancel()
        chatJobId = id
        chatPending = false; chatPendingFull = false
        chatJob = viewModelScope.launch {
            var useFull = full
            do {
                chatPending = false
                // 上一轮请求期间有人要求全量刷新，这一轮就全量
                val isFull = useFull || chatPendingFull
                useFull = false; chatPendingFull = false
                val cur = _chat.value
                if (cur.id != id) return@launch
                val firstOpen = if (isFull) 0 else cur.steps.indexOfFirst { it.status == "running" || it.status == "waiting" || it.status == "queued" }
                    .let { if (it < 0) cur.steps.size else it }
                runCatching { api.conversation(id, firstOpen) }
                    .onSuccess { d ->
                        val now = _chat.value
                        if (now.id != id) return@launch
                        val model = d.model.ifBlank { now.model }
                        val steps = now.steps.take(firstOpen) + d.steps
                        val totalUsers = steps.count { it.kind == "user" } + d.queued.size
                        val pending = now.pending?.takeIf { totalUsers <= now.pendingBase }
                        val conv = d.conv ?: now.conv
                        val autoRead = AppState.foreground && id !in manuallyUnread
                        val finalConv = if (autoRead && conv?.unread == true) {
                            markViewed(id, force = conv.status == "idle")
                            conv.copy(unread = false)
                        } else conv
                        _chat.value = now.copy(conv = finalConv, steps = steps, queued = d.queued, voiceChats = d.voiceChats,
                            pendingFiles = if (pending == null) emptyList() else now.pendingFiles,
                            loading = false, error = null, model = model, pending = pending)
                        // 模型默认始终跟随电脑端同步；仅在还没拿到电脑端模型时才兜底用本对话历史模型
                        if (!userPicked && pcModel.isBlank() && model.isNotBlank()) _selected.value = model
                    }
                    .onFailure { e ->
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        if (_chat.value.id == id)
                            _chat.value = _chat.value.copy(loading = false, error = if (isFull) err(e) else _chat.value.error)
                    }
            } while (chatPending)
        }
    }

    fun send(text: String, onDone: (Boolean) -> Unit) {
        val cur = _chat.value
        val id = cur.id
        val model = _selected.value
        val files = _attachments.value
        _attachments.value = emptyList()
        val ms = _mentions.value
        _mentions.value = emptyList()
        val shownMs = ms.map { Mention(it.name, it.logo) }
        // 先把消息显示出来，下面接“正在运行”
        val shown = text
        _chat.value = cur.copy(pending = shown, pendingFiles = files, pendingMentions = shownMs,
            pendingBase = cur.steps.count { it.kind == "user" } + cur.queued.size)
        onDone(true)
        viewModelScope.launch {
            if (id == null) {
                val proj = newChatProject
                runCatching { api.newConversation(text, model, files, projectId = proj, mentions = ms) }
                    .onSuccess { newId ->
                        refreshList()
                        newChatProject = ""
                        refreshProjects()
                        _chat.value = ChatState(newId, loading = true, model = model, pending = shown, pendingFiles = files, pendingMentions = shownMs, pendingBase = 0)
                        userPicked = true
                        reloadChat(newId, full = true)
                    }
                    .onFailure { _toast.tryEmit("新建失败：" + err(it)); _chat.value = _chat.value.copy(pending = null); _attachments.value = files; _mentions.value = ms }
            } else {
                runCatching { api.send(id, text, model, files, ms) }
                    .onSuccess { reloadChat(id, full = false) }
                    .onFailure {
                        _toast.tryEmit("发送失败：" + err(it))
                        if (_chat.value.id == id) { _chat.value = _chat.value.copy(pending = null); _attachments.value = files; _mentions.value = ms }
                    }
            }
        }
    }

    /** 开始录音：16kHz 单声道 PCM 分段发给电脑，由 Antigravity 自己的模型识别。 */
    @android.annotation.SuppressLint("MissingPermission")
    fun startVoice() {
        if (_recording.value) return
        _recording.value = true
        _sttText.value = ""
        _sttStable.value = ""
        _levels.value = emptyList()
        stopMode = "keep"
        recJob = viewModelScope.launch(Dispatchers.IO) {
            val sid = runCatching { api.sttStart() }.getOrElse {
                _toast.tryEmit("语音识别启动失败：" + err(it)); _recording.value = false; return@launch
            }
            sttSid = sid
            val rate = 16000
            val min = android.media.AudioRecord.getMinBufferSize(rate, android.media.AudioFormat.CHANNEL_IN_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT)
            val rec = runCatching {
                android.media.AudioRecord(android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION, rate,
                    android.media.AudioFormat.CHANNEL_IN_MONO, android.media.AudioFormat.ENCODING_PCM_16BIT, maxOf(min, rate * 2))
            }.getOrElse {
                // 没麦克风权限或被占用时构造会抛异常，不能让它把 App 弄崩
                _toast.tryEmit("无法打开麦克风：" + err(it)); runCatching { api.sttEnd(sid, -1) }
                sttSid = null; _recording.value = false; return@launch
            }
            val frame = ByteArray(rate / 10)      // 50ms，用来算声纹
            val batch = java.io.ByteArrayOutputStream()
            var seq = 0
            try {
                rec.startRecording()
                val t0 = System.currentTimeMillis()
                while (_recording.value && System.currentTimeMillis() - t0 < 120_000) {
                    val n = rec.read(frame, 0, frame.size)
                    if (n <= 0) break
                    _levels.value = (_levels.value + level(frame, n)).takeLast(48)
                    batch.write(frame, 0, n)
                    if (batch.size() >= rate / 2) { // 每 0.25 秒发一段
                        val bytes = batch.toByteArray(); batch.reset()
                        api.sttChunk(sid, seq++, bytes, bytes.size)
                    }
                }
                if (batch.size() > 0 && stopMode != "cancel") {
                    val bytes = batch.toByteArray()
                    api.sttChunk(sid, seq++, bytes, bytes.size)
                }
            } catch (e: Exception) {
                _toast.tryEmit("录音出错：" + err(e))
            } finally {
                runCatching { rec.stop() }
                rec.release()
            }
            _recording.value = false
            val partial = _sttText.value
            val finalizing = stopMode != "cancel"
            if (finalizing) _sttFinalizing.value = true
            val text = runCatching { api.sttEnd(sid, seq - 1) }.getOrElse { partial }.ifBlank { partial }
            sttSid = null
            _sttFinalizing.value = false
            _sttText.value = ""
            _sttStable.value = ""
            _levels.value = emptyList()
            if (stopMode != "cancel" && text.isNotBlank()) _sttResult.tryEmit(text to (stopMode == "send"))
        }
    }

    /** 50ms 片段的响度，换算成 0..1 方便画声纹。 */
    private fun level(buf: ByteArray, n: Int): Float {
        var sum = 0.0
        var i = 0
        while (i + 1 < n) {
            val v = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toDouble()
            sum += v * v
            i += 2
        }
        val rms = kotlin.math.sqrt(sum / (n / 2).coerceAtLeast(1))
        val db = 20 * kotlin.math.log10(rms.coerceAtLeast(1.0))  // 安静 ~30dB，大声 ~80dB
        return ((db - 35) / 40).toFloat().coerceIn(0.04f, 1f)
    }

    /** 结束录音。mode：keep=填进输入框，send=识别完直接发，cancel=不要了。 */
    fun stopVoice(mode: String = "keep") {
        stopMode = mode
        _recording.value = false
    }

    fun cancel() {
        val id = _chat.value.id ?: return
        viewModelScope.launch {
            runCatching { api.cancel(id) }.onFailure { _toast.tryEmit("停止失败：" + err(it)) }
        }
    }

    private val _sendingQueuedIds = MutableStateFlow<Set<String>>(emptySet())
    val sendingQueuedIds = _sendingQueuedIds.asStateFlow()

    fun sendQueuedNow(m: QueuedMsg) {
        val id = _chat.value.id ?: return
        if (m.id in _sendingQueuedIds.value) return
        _sendingQueuedIds.value = _sendingQueuedIds.value + m.id
        viewModelScope.launch {
            runCatching { api.sendQueued(id, m.id, m.text) }
                .onSuccess {
                    _sendingQueuedIds.value = _sendingQueuedIds.value - m.id
                    reloadChat(id, full = false)
                }
                .onFailure {
                    _sendingQueuedIds.value = _sendingQueuedIds.value - m.id
                    if (_chat.value.id == id) {
                        _chat.value = _chat.value.copy(
                            queued = _chat.value.queued.map { q -> if (q.id == m.id) q.copy(immediate = false) else q }
                        )
                    }
                    _toast.tryEmit("立刻发送失败：" + err(it))
                    reloadChat(id, full = false)
                }
        }
    }

    fun deleteQueued(m: QueuedMsg) {
        val id = _chat.value.id ?: return
        _chat.value = _chat.value.copy(queued = _chat.value.queued.filterNot { it.id == m.id })
        viewModelScope.launch {
            runCatching { api.deleteQueued(id, m.id) }
                .onSuccess { reloadChat(id, full = false) }
                .onFailure {
                    reloadChat(id, full = false)
                    val msg = it.message ?: ""
                    _toast.tryEmit(if (msg.contains("already been delivered")) "这条消息已经发出去了" else "取消排队失败：" + err(it))
                }
        }
    }

    fun editQueued(m: QueuedMsg) {
        val id = _chat.value.id ?: return
        _chat.value = _chat.value.copy(queued = _chat.value.queued.filterNot { it.id == m.id })
        viewModelScope.launch {
            runCatching { api.deleteQueued(id, m.id) }
                .onSuccess {
                    insertDraft(m.text)
                    reloadChat(id, full = false)
                }
                .onFailure {
                    reloadChat(id, full = false)
                    val msg = it.message ?: ""
                    _toast.tryEmit(if (msg.contains("already been delivered")) "这条消息已经发出去了，无法再编辑" else "编辑失败：" + err(it))
                }
        }
    }

    fun respond(step: Step, accept: Boolean, scope: String? = null, answers: JSONArray? = null,
                text: String? = null, onError: (() -> Unit)? = null) {
        val id = _chat.value.id ?: return
        viewModelScope.launch {
            runCatching { api.respond(id, step, accept, scope, answers, text) }
                .onSuccess { reloadChat(id, full = false) }
                .onFailure { _toast.tryEmit("操作失败：" + err(it)); onError?.invoke() }
        }
    }

    // ---------- 对话管理（长按菜单） ----------
    private fun annotate(id: String, body: JSONObject, ok: String? = null) {
        viewModelScope.launch {
            runCatching { api.annotate(id, body) }
                .onSuccess { ok?.let { _toast.tryEmit(it) }; refreshList() }
                .onFailure { _toast.tryEmit("操作失败：" + err(it)) }
        }
    }

    fun rename(id: String, title: String) = annotate(id, JSONObject().put("title", title))
    fun setPinned(id: String, v: Boolean) = annotate(id, JSONObject().put("pinned", v))
    fun setUnread(id: String, v: Boolean) {
        if (v) manuallyUnread.add(id) else manuallyUnread.remove(id)
        annotate(id, JSONObject().put("unread", v))
    }
    fun setArchived(id: String, v: Boolean) {
        annotate(id, JSONObject().put("archived", v), if (v) "已归档，可在设置里找回" else "已取消归档")
        if (v && _chat.value.id == id) newChat()
    }

    fun delete(id: String) {
        viewModelScope.launch {
            runCatching { api.delete(id) }
                .onSuccess { _toast.tryEmit("已删除"); if (_chat.value.id == id) newChat(); refreshList() }
                .onFailure { _toast.tryEmit("删除失败：" + err(it)) }
        }
    }

    fun copy(text: String, label: String = "已复制") {
        val cm = getApplication<Application>().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Antigravity", text))
        if (Build.VERSION.SDK_INT < 33) _toast.tryEmit(label)
    }

    /** 把整个对话整理成文本复制。 */
    fun copyConversation(id: String) {
        viewModelScope.launch {
            runCatching { api.conversation(id, 0) }.onSuccess { d ->
                val title = d.conv?.title ?: _list.value.find { it.id == id }?.title ?: ""
                val sb = StringBuilder("# $title\n\n")
                d.steps.forEach { s ->
                    when (s.kind) {
                        "user" -> sb.append("**我：**\n").append(s.text).append("\n\n")
                        "assistant" -> sb.append("**Agent：**\n").append(s.text).append("\n\n")
                        "tool", "file" -> if (s.text.isNotBlank()) sb.append("> ${s.title}：`${s.text.lineSequence().first()}`\n\n")
                    }
                }
                copy(sb.toString().trim(), "已复制全部内容")
            }.onFailure { _toast.tryEmit("复制失败：" + err(it)) }
        }
    }

    // ---------- 实时语音通话（Antigravity 2.35+） ----------
    private val _voiceCfg = MutableStateFlow(VoiceConfig())
    val voiceCfg = _voiceCfg.asStateFlow()
    private val _voiceCall = MutableStateFlow<VoiceCall?>(null)
    val voiceCall = _voiceCall.asStateFlow()
    private var previewPlayer: android.media.MediaPlayer? = null
    private val _previewing = MutableStateFlow<String?>(null)
    val previewing = _previewing.asStateFlow()

    fun loadVoiceConfig() {
        viewModelScope.launch { runCatching { api.voiceConfig() }.onSuccess { _voiceCfg.value = it } }
    }

    /** 改音色/语速/音量：先改界面，再写回电脑（电脑上的 Antigravity 设置同步变化）；通话中的语速和音量立即生效。 */
    fun saveVoice(name: String? = null, speed: Double? = null, volume: Int? = null) {
        val cur = _voiceCfg.value.settings
        _voiceCfg.value = _voiceCfg.value.copy(settings = cur.copy(name = name ?: cur.name, speed = speed ?: cur.speed, volume = volume ?: cur.volume))
        _voiceCall.value?.let { c -> speed?.let { c.setSpeed(it) }; volume?.let { c.setVolume(it) } }
        viewModelScope.launch {
            runCatching { api.saveVoiceSettings(name, speed, volume) }
                .onSuccess { _voiceCfg.value = _voiceCfg.value.copy(settings = it) }
                .onFailure { _toast.tryEmit("保存语音设置失败：" + err(it)) }
        }
    }

    /** 试听音色（电脑上的 Antigravity 自带的样音，每个音色 3 段随机一段）。再点一次停止。 */
    fun previewVoice(name: String) {
        previewPlayer?.release(); previewPlayer = null
        if (_previewing.value == name) { _previewing.value = null; return }
        _previewing.value = name
        viewModelScope.launch {
            runCatching {
                val bytes = api.voiceSample(name, (1..3).random())
                val f = java.io.File(getApplication<Application>().cacheDir, "voice_preview.mp3").apply { writeBytes(bytes) }
                withContext(Dispatchers.Main) {
                    if (_previewing.value != name) return@withContext
                    previewPlayer = android.media.MediaPlayer().apply {
                        setDataSource(f.absolutePath)
                        setVolume(_voiceCfg.value.settings.volume / 100f, _voiceCfg.value.settings.volume / 100f)
                        setOnCompletionListener { if (_previewing.value == name) _previewing.value = null; it.release(); if (previewPlayer === it) previewPlayer = null }
                        prepare(); start()
                    }
                }
            }.onFailure { _previewing.value = null; _toast.tryEmit("试听失败：" + err(it)) }
        }
    }

    /** 停止试听（离开语音设置时调用）。 */
    fun stopPreview() { previewPlayer?.release(); previewPlayer = null; _previewing.value = null }

    /** 开始实时语音：在当前对话里说；新对话时电脑会先建一个，通话里会告诉我们对话 ID。 */
    fun startVoiceCall() {
        if (_voiceCall.value != null) return
        previewPlayer?.release(); previewPlayer = null; _previewing.value = null
        val s = _voiceCfg.value.settings
        val call = VoiceCall(getApplication(), api)
        _voiceCall.value = call
        call.start(_chat.value.id, _selected.value, s.name, s.speed, s.volume)
    }

    /** 通话中换模型：和平时一样同步到电脑，同时告诉正在进行的通话。 */
    fun pickVoiceModel(model: String) {
        pickModel(model)
        _voiceCfg.value.models[model]?.let { _voiceCall.value?.setModel(it) }
    }

    /** 通话中把照片/文件发进当前对话（和电脑上 Antigravity 语音模式里加附件一样）。 */
    fun sendVoiceAttachments(uris: List<android.net.Uri>) {
        val call = _voiceCall.value ?: return
        val cid = call.convId.value ?: run { _toast.tryEmit("对话还没建好，稍等一下再加附件"); return }
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            val files = uris.mapNotNull { u -> runCatching { Attachments.load(ctx, u) }.onFailure { _toast.tryEmit("附件读取失败：" + err(it)) }.getOrNull() }
            if (files.isEmpty()) return@launch
            runCatching { api.send(cid, "请看附件。", _selected.value, files) }
                .onSuccess { _toast.tryEmit("已发送附件") }
                .onFailure { _toast.tryEmit("发送失败：" + err(it)) }
        }
    }

    /** 结束通话并关掉通话界面；新建的对话会出现在列表里并自动打开。 */
    fun endVoiceCall() {
        val call = _voiceCall.value ?: return
        call.stop()
        _voiceCall.value = null
        val cid = call.convId.value
        refreshList()
        if (cid != null && cid != _chat.value.id) openChat(cid) else cid?.let { reloadChat(it, full = false) }
    }

    /** 输入栏上的短名字：去掉品牌前缀，比如“Hydrogen Medium”“Opus 5.5”。 */
    fun modelChipLabel(model: String): String = _models.value.find { it.model == model }?.let {
        val full = if (it.level.isNotBlank()) "${it.family} ${it.level}" else it.family
        full.replace(Regex("^(Gemini\\s+[\\d.]+|Gemini|Claude)\\s+", RegexOption.IGNORE_CASE), "").ifBlank { full }
    } ?: "模型"

    fun modelLabel(model: String): String = _models.value.find { it.model == model }?.let {
        if (it.levelZh.isNotBlank()) "${it.family} · ${it.levelZh}" else it.family
    } ?: "选择模型"

    private fun err(e: Throwable): String = when (e) {
        is ApiException -> e.message ?: "出错了"
        is java.net.ConnectException, is java.net.SocketTimeoutException, is java.net.UnknownHostException ->
            "连不上电脑（${prefs.host}:${prefs.port}），检查电脑端服务和 Tailscale"
        else -> e.message ?: e.javaClass.simpleName
    }
}
