package com.local.agremote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class Conversation(
    val id: String,
    val title: String,
    val status: String, // idle / running / canceling / busy
    val steps: Int,
    val updated: String,
    val workspace: String,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val unread: Boolean = false,
    val waiting: Boolean = false,
    val permissionPreset: String = "",
)

/** 要随消息发给 agent 的附件（照片、文件）。 */
data class Attachment(val name: String, val mime: String, val base64: String, val size: Int, val thumb: android.graphics.Bitmap? = null)

/** 已发出消息里带的附件（文件在电脑上，图片按需再取）。 */
/** 消息里 @ 的连接器（插件），和电脑上输入框里的小标签一样。 */
data class Mention(val name: String, val logo: String)

data class StepAttachment(val name: String, val mime: String, val path: String, val ref: String = "")

data class ArtifactFile(val name: String, val path: String, val mime: String, val size: Long, val updated: String, val summary: String)

data class FileDiffItem(val path: String, val name: String, val dir: String, val isArtifact: Boolean, val updated: String)

data class UsedSkillItem(val name: String, val path: String)

data class SubagentItem(
    val id: String, val role: String, val typeName: String, val status: String,
    val steps: Int, val killed: Boolean, val created: String, val updated: String,
)

data class BackgroundTaskItem(
    val id: String, val title: String, val command: String, val running: Boolean,
    val status: String, val progress: String, val logPath: String, val startTime: String,
)

data class ContextUsage(val used: Int, val max: Int)

data class OverviewData(
    val artifacts: List<ArtifactFile> = emptyList(),
    val fileDiffs: List<FileDiffItem> = emptyList(),
    val skillsUsed: List<UsedSkillItem> = emptyList(),
    val subagents: List<SubagentItem> = emptyList(),
    val backgroundTasks: List<BackgroundTaskItem> = emptyList(),
    val contextUsage: ContextUsage? = null,
)

data class BrowserTab(val id: String, val url: String, val title: String, val favicon: String, val cid: String, val convTitle: String)

data class BrowserTabsData(val activeTabId: String = "", val tabs: List<BrowserTab> = emptyList(), val otherTabs: List<BrowserTab> = emptyList())

data class BrowserAction(val title: String, val text: String, val sub: String, val status: String, val time: String, val source: String)

data class BrowserActivityData(val running: Boolean = false, val actions: List<BrowserAction> = emptyList(), val screenshots: List<StepAttachment> = emptyList())

data class PcCursor(val id: String, val xHot: Int, val yHot: Int, val w: Int, val h: Int, val bmp: android.graphics.Bitmap)

data class Skill(val name: String, val description: String, val builtin: Boolean)

data class Project(val id: String, val name: String, val folders: List<String>, val conversations: List<String>, val sidecar: Boolean)

data class Automation(
    val id: String, val name: String, val description: String, val schedule: String, val prompt: String,
    val enabled: Boolean, val status: String, val error: String,
)

/** 连接器：已装的插件（Google Drive、Gmail…）或 MCP 服务。 */
data class Connector(val key: String, val kind: String, val name: String, val description: String, val logo: String,
                     val enabled: Boolean, val connected: Boolean?, val account: String, val needsAuth: Boolean, val error: String)

data class Plugin(val id: String, val title: String, val description: String, val trust: String, val kind: String,
                  val installed: Boolean, val link: String = "")

/** 插件市场（和电脑上“自定义扩展”页同一份数据）。 */
data class MarketItem(val id: String, val name: String, val description: String, val logo: String, val author: String,
                      val version: String, val homepage: String)
data class MarketGroup(val id: String, val name: String, val description: String, val members: List<String>)
data class Market(val featured: List<String>, val groups: List<MarketGroup>, val items: List<MarketItem>, val installed: Set<String>)

data class Customization(val type: String, val name: String, val path: String, val scope: String, val status: String, val error: String)

data class McpServer(val name: String, val status: String, val error: String, val tools: Int, val enabled: Boolean)

data class ModelInfo(
    val model: String, // 枚举名，发消息时用
    val id: String,
    val label: String,
    val family: String,
    val level: String,
    val levelZh: String,
    val desc: String,
    val tag: String,
    val remaining: Double?,
    val reset: String,
    val grouped: Boolean = true, // Antigravity 有没有把它归进带思考强度的系列
)

/** Antigravity 官方额度汇总里的一个限额（每周 / 5 小时）。 */
data class QuotaBucket(val name: String, val window: String, val remaining: Double, val reset: String, val desc: String)

/** 一组共用额度的模型。 */
data class QuotaGroup(val name: String, val models: List<String>, val buckets: List<QuotaBucket>)

data class Account(
    val name: String,
    val plan: String,
    val promptAvail: Int?, val promptMonthly: Int?,
    val flowAvail: Int?, val flowMonthly: Int?,
    val upgradeText: String,
    val groups: List<QuotaGroup>,
    val note: String,
)

data class Waiting(
    val kind: String,
    val label: String,
    val action: String,
    val target: String,
    val reason: String,
    val grant: String,
    val persistBlocked: Boolean,
    val scopes: List<String>,
    val command: String,
    val path: String,
    val questions: List<Question>,
)

data class Question(val question: String, val multi: Boolean, val options: List<Pair<String, String>>)

data class QueuedMsg(val id: String, val text: String, val time: String, val immediate: Boolean)

data class Step(
    val i: Int,
    val type: String,
    val kind: String, // user / assistant / tool / file / error / ask / hidden
    val status: String, // running / waiting / done / error / canceled
    val title: String,
    val text: String,
    val sub: String,
    val detail: String,
    val thinking: String,
    val time: String = "",
    val trajectoryId: String,
    val waiting: Waiting?,
    val attachments: List<StepAttachment> = emptyList(),
    val mentions: List<Mention> = emptyList(),
)

class ApiException(val code: Int, message: String) : IOException(message)

/** 外观与主题配置：风格（Material You / Miuix）、深色模式、配色（跟随系统 / 自定义颜色）。 */
data class ThemeConfig(
    val style: String = "material",      // material / miuix
    val darkMode: String = "system",     // system / light / dark
    val colorMode: String = "system",    // system / custom
    val seedColor: Int = 0xFF3482FF.toInt(),
)

/** 已保存的电脑端设备（支持多设备切换，每个设备可自定义名称和 IP/端口）。 */
data class SavedHost(
    val id: String,
    val name: String,
    val host: String,
    val port: Int = 5100,
    val token: String = "",
)

/** 保存连接设置（地址、配对令牌、多设备列表）。 */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("agremote", Context.MODE_PRIVATE)
    var host: String
        get() = sp.getString("host", "") ?: ""
        set(v) = sp.edit().putString("host", v).apply()
    var port: Int
        get() = sp.getInt("port", 5100)
        set(v) = sp.edit().putInt("port", v).apply()
    var token: String
        get() = sp.getString("token", "") ?: ""
        set(v) = sp.edit().putString("token", v).apply()
    var model: String
        get() = sp.getString("model", "") ?: ""
        set(v) = sp.edit().putString("model", v).apply()
    /** 后台消息推送开关（默认开）。 */
    var notify: Boolean
        get() = sp.getBoolean("notify", true)
        set(v) = sp.edit().putBoolean("notify", v).apply()
    var i18nJson: String
        get() = sp.getString("i18n_json", "{}") ?: "{}"
        set(v) = sp.edit().putString("i18n_json", v).apply()
    var modelsJson: String
        get() = sp.getString("models_json", "{}") ?: "{}"
        set(v) = sp.edit().putString("models_json", v).apply()
    var votesJson: String
        get() = sp.getString("votes_json", "{}") ?: "{}"
        set(v) = sp.edit().putString("votes_json", v).apply()
    /** 远程电脑虚拟光标缩放倍率（默认 1.0，范围 0.4..2.2）。 */
    var cursorScale: Float
        get() = sp.getFloat("cursor_scale", 1.0f).coerceIn(0.4f, 2.2f)
        set(v) = sp.edit().putFloat("cursor_scale", v.coerceIn(0.4f, 2.2f)).apply()
    /** 界面语言：system / zh-CN / zh-TW / zh-HK / ja / en / ko / es / fr / de / ru / pt */
    var language: String
        get() = sp.getString("app_language", "system") ?: "system"
        set(v) = sp.edit().putString("app_language", v).apply()
    /** 点击网页链接的打开方式：ask（每次询问）/ system（默认浏览器）/ builtin（内置浏览器） */
    var linkOpenMode: String
        get() = sp.getString("link_open_mode", "ask") ?: "ask"
        set(v) = sp.edit().putString("link_open_mode", v).apply()
    var activeHostId: String
        get() = sp.getString("active_host_id", "") ?: ""
        set(v) = sp.edit().putString("active_host_id", v).apply()
    var savedHosts: List<SavedHost>
        get() {
            val raw = sp.getString("saved_hosts_json", "") ?: ""
            val list = runCatching {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    SavedHost(
                        id = o.optString("id"),
                        name = o.optString("name"),
                        host = o.optString("host"),
                        port = o.optInt("port", 5100),
                        token = o.optString("token"),
                    )
                }.filter { it.id.isNotBlank() && it.host.isNotBlank() }
            }.getOrElse { emptyList() }
            if (list.isEmpty() && host.isNotBlank() && token.isNotBlank()) {
                val migrated = listOf(SavedHost("default", host, host, port, token))
                savedHosts = migrated
                if (activeHostId.isBlank()) activeHostId = "default"
                return migrated
            }
            return list
        }
        set(v) {
            val arr = JSONArray()
            v.forEach { h ->
                arr.put(JSONObject()
                    .put("id", h.id)
                    .put("name", h.name)
                    .put("host", h.host)
                    .put("port", h.port)
                    .put("token", h.token))
            }
            sp.edit().putString("saved_hosts_json", arr.toString()).apply()
        }
    var themeConfig: ThemeConfig
        get() = ThemeConfig(
            style = sp.getString("theme_style", "material") ?: "material",
            darkMode = sp.getString("theme_dark", "system") ?: "system",
            colorMode = sp.getString("theme_color_mode", "system") ?: "system",
            seedColor = sp.getInt("theme_seed", 0xFF3482FF.toInt()),
        )
        set(v) = sp.edit()
            .putString("theme_style", v.style)
            .putString("theme_dark", v.darkMode)
            .putString("theme_color_mode", v.colorMode)
            .putInt("theme_seed", v.seedColor)
            .apply()
    val base get() = "http://$host:$port"
}

class Api(private val prefs: Prefs) {
    private val json = "application/json; charset=utf-8".toMediaType()
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()
    /** SSE 用：读取超时 20 秒，服务端每 1.2 秒推一次心跳，超时即说明断线。 */
    val streamClient: OkHttpClient = client.newBuilder().readTimeout(20, TimeUnit.SECONDS).build()

    private fun req(path: String) = Request.Builder().url(prefs.base + path)
        .header("Authorization", "Bearer ${prefs.token}")

    private suspend fun call(r: Request): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(r).execute().use { resp ->
            val body = resp.body.string()
            val o = runCatching { JSONObject(body) }.getOrElse { JSONObject() }
            if (!resp.isSuccessful) throw ApiException(resp.code, o.optString("error").ifBlank { "HTTP ${resp.code}" })
            o
        }
    }

    private suspend fun post(path: String, body: JSONObject) =
        call(req(path).post(body.toString().toRequestBody(json)).build())

    suspend fun pair(code: String, name: String): String = pairAt(prefs.host, prefs.port, code, name)

    suspend fun pairAt(host: String, port: Int, code: String, name: String): String {
        val o = call(Request.Builder().url("http://${host.trim()}:$port/api/pair")
            .post(JSONObject().put("code", code).put("name", name).toString().toRequestBody(json)).build())
        return o.getString("token")
    }

    suspend fun conversations(): List<Conversation> {
        val a = call(req("/api/conversations").build()).optJSONArray("conversations") ?: JSONArray()
        return (0 until a.length()).map { parseConv(a.getJSONObject(it)) }
    }

    data class ConvDetail(val conv: Conversation?, val steps: List<Step>, val model: String, val queued: List<QueuedMsg> = emptyList())

    suspend fun conversation(id: String, offset: Int): ConvDetail {
        val o = call(req("/api/conversations/$id?offset=$offset").build())
        val c = o.optJSONObject("conversation")?.let { parseConv(it) }
        val a = o.optJSONArray("steps") ?: JSONArray()
        val q = o.optJSONArray("queued") ?: JSONArray()
        val queued = (0 until q.length()).map {
            val x = q.getJSONObject(it)
            QueuedMsg(x.optString("id"), x.optString("text"), x.optString("time"), x.optBoolean("immediate"))
        }
        return ConvDetail(c, (0 until a.length()).map { parseStep(a.getJSONObject(it)) }, o.optString("model"), queued)
    }

    suspend fun send(id: String, text: String, model: String, attachments: List<Attachment> = emptyList(),
                     mentions: List<Connector> = emptyList()) {
        post("/api/conversations/$id/send", JSONObject().put("text", text).put("model", model)
            .put("attachments", attachmentsJson(attachments)).put("mentions", mentionsJson(mentions)))
    }
    private fun mentionsJson(l: List<Connector>) = JSONArray().also { a ->
        l.forEach { a.put(JSONObject().put("key", it.key).put("kind", it.kind).put("name", it.name)) }
    }

    private fun attachmentsJson(list: List<Attachment>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("mime", it.mime).put("name", it.name).put("data", it.base64)) }
    }

    // ---------- Skills / MCP 连接器 / 官方中文词条 ----------
    suspend fun skills(): List<Skill> {
        val a = call(req("/api/skills").build()).optJSONArray("skills") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { o -> Skill(o.optString("name"), o.optString("description"), o.optBoolean("builtin")) } }
    }

    suspend fun mcpServers(): List<McpServer> {
        val a = call(req("/api/mcp").build()).optJSONArray("servers") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            McpServer(o.optString("name"), o.optString("status"), o.optString("error"), o.optInt("tools"), o.optBoolean("enabled"))
        } }
    }

    suspend fun mcpToggle(name: String, enabled: Boolean) {
        post("/api/mcp/toggle", JSONObject().put("name", name).put("enabled", enabled))
    }

    suspend fun i18n(): Map<String, String> {
        val o = call(req("/api/i18n").build()).optJSONObject("zh") ?: JSONObject()
        prefs.i18nJson = o.toString()
        return o.keys().asSequence().associateWith { o.optString(it) }
    }

    suspend fun annotate(id: String, body: JSONObject) { post("/api/conversations/$id/annotate", body) }
    suspend fun delete(id: String) { post("/api/conversations/$id/delete", JSONObject()) }

    suspend fun models(): Pair<String, List<ModelInfo>> {
        val o = call(req("/api/models").build())
        prefs.modelsJson = o.toString()
        return parseModels(o)
    }

    companion object {
        fun parseModels(o: JSONObject): Pair<String, List<ModelInfo>> {
        val a = o.optJSONArray("models") ?: JSONArray()
        return o.optString("defaultModel") to (0 until a.length()).map {
            val m = a.getJSONObject(it)
            ModelInfo(m.optString("model"), m.optString("id"), m.optString("label"), m.optString("family"),
                m.optString("level"), m.optString("level"), m.optString("desc"), m.optString("tag"),
                if (m.isNull("remaining")) null else m.optDouble("remaining"), m.optString("reset"), m.optBoolean("grouped"))
        }
        }
    }

    suspend fun account(): Account {
        val o = call(req("/api/account").build())
        fun n(obj: JSONObject?, k: String) = obj?.takeIf { !it.isNull(k) }?.optInt(k)
        val pc = o.optJSONObject("promptCredits"); val fc = o.optJSONObject("flowCredits")
        val q = o.optJSONArray("quotaGroups") ?: JSONArray()
        return Account(
            o.optString("name"), o.optString("plan"), n(pc, "available"), n(pc, "monthly"), n(fc, "available"), n(fc, "monthly"),
            o.optString("upgradeText"),
            (0 until q.length()).map { k ->
                val g = q.getJSONObject(k)
                val ms = g.optJSONArray("models") ?: JSONArray()
                val bs = g.optJSONArray("buckets") ?: JSONArray()
                QuotaGroup(
                    g.optString("name"),
                    (0 until ms.length()).map { ms.getString(it) },
                    (0 until bs.length()).map { i ->
                        val b = bs.getJSONObject(i)
                        QuotaBucket(b.optString("name"), b.optString("window"), b.optDouble("remaining", 0.0),
                            b.optString("reset"), b.optString("desc"))
                    },
                )
            },
            o.optString("quotaNote"),
        )
    }
    suspend fun cancel(id: String) { post("/api/conversations/$id/cancel", JSONObject()) }
    suspend fun sendQueued(id: String, messageId: String, text: String) {
        post("/api/conversations/$id/sendQueued", JSONObject().put("messageId", messageId).put("text", text))
    }
    suspend fun deleteQueued(id: String, messageId: String) {
        post("/api/conversations/$id/deleteQueued", JSONObject().put("messageId", messageId))
    }
    suspend fun overview(id: String): OverviewData {
        val root = call(req("/api/conversations/$id/overview").build())
        val arts = root.optJSONArray("artifacts") ?: JSONArray()
        val diffs = root.optJSONArray("fileDiffs") ?: JSONArray()
        val sks = root.optJSONArray("skillsUsed") ?: JSONArray()
        val subs = root.optJSONArray("subagents") ?: JSONArray()
        val bgs = root.optJSONArray("backgroundTasks") ?: JSONArray()
        val cw = root.optJSONObject("contextUsage")?.let { ContextUsage(it.optInt("used"), it.optInt("max")) }
        return OverviewData(
            artifacts = (0 until arts.length()).map {
                val o = arts.getJSONObject(it)
                ArtifactFile(o.optString("name"), o.optString("path"), o.optString("mime"),
                    o.optLong("size"), o.optString("updated"), o.optString("summary"))
            },
            fileDiffs = (0 until diffs.length()).map {
                val o = diffs.getJSONObject(it)
                FileDiffItem(o.optString("path"), o.optString("name"), o.optString("dir"),
                    o.optBoolean("isArtifact"), o.optString("updated"))
            },
            skillsUsed = (0 until sks.length()).map {
                val o = sks.getJSONObject(it)
                UsedSkillItem(o.optString("name"), o.optString("path"))
            },
            subagents = (0 until subs.length()).map {
                val o = subs.getJSONObject(it)
                SubagentItem(o.optString("id"), o.optString("role"), o.optString("typeName"),
                    o.optString("status"), o.optInt("steps"), o.optBoolean("killed"),
                    o.optString("created"), o.optString("updated"))
            },
            backgroundTasks = (0 until bgs.length()).map {
                val o = bgs.getJSONObject(it)
                BackgroundTaskItem(o.optString("id"), o.optString("title"), o.optString("command"),
                    o.optBoolean("running"), o.optString("status"), o.optString("progress"),
                    o.optString("logPath"), o.optString("startTime"))
            },
            contextUsage = cw,
        )
    }

    suspend fun taskLog(cid: String, taskId: String): String =
        call(req("/api/conversations/$cid/taskLog?taskId=" + java.net.URLEncoder.encode(taskId, "UTF-8")).build()).optString("log")

    suspend fun browserTabs(cid: String): BrowserTabsData {
        val o = call(req("/api/browser/tabs?cid=" + java.net.URLEncoder.encode(cid, "UTF-8")).build())
        return parseBrowserTabs(o)
    }

    suspend fun closeBrowserTab(tabCid: String, tabId: String, curCid: String): BrowserTabsData {
        val o = post("/api/browser/tabs/close", JSONObject()
            .put("tabCid", tabCid)
            .put("tabId", tabId)
            .put("curCid", curCid))
        return parseBrowserTabs(o)
    }

    private fun parseBrowserTabs(o: JSONObject): BrowserTabsData {
        fun parse(a: JSONArray?) = (0 until (a?.length() ?: 0)).map {
            val t = a!!.getJSONObject(it)
            BrowserTab(t.optString("id"), t.optString("url"), t.optString("title"),
                t.optString("favicon"), t.optString("cid"), t.optString("convTitle"))
        }
        return BrowserTabsData(o.optString("activeTabId"), parse(o.optJSONArray("tabs")), parse(o.optJSONArray("otherTabs")))
    }

    suspend fun browserScreen(mode: String): ByteArray? = withContext(Dispatchers.IO) {
        client.newCall(req("/api/browser/screen?mode=$mode").build()).execute().use { r ->
            if (!r.isSuccessful) null else r.body.bytes()
        }
    }

    suspend fun browserActivity(cid: String): BrowserActivityData {
        val o = call(req("/api/browser/activity?cid=" + java.net.URLEncoder.encode(cid, "UTF-8")).build())
        val acts = o.optJSONArray("actions") ?: JSONArray()
        val shots = o.optJSONArray("screenshots") ?: JSONArray()
        return BrowserActivityData(
            running = o.optBoolean("running"),
            actions = (0 until acts.length()).map {
                val a = acts.getJSONObject(it)
                BrowserAction(a.optString("title"), a.optString("text"), a.optString("sub"),
                    a.optString("status"), a.optString("time"), a.optString("source"))
            },
            screenshots = (0 until shots.length()).map {
                val s = shots.getJSONObject(it)
                StepAttachment(s.optString("name"), s.optString("mime"), s.optString("path"), s.optString("ref"))
            },
        )
    }

    suspend fun browserInput(
        action: String,
        mode: String = "screen",
        nx: Float = 0.5f,
        ny: Float = 0.5f,
        delta: Int = 0,
        text: String = "",
        key: String = "",
    ): JSONObject = post("/api/browser/input", JSONObject()
        .put("action", action)
        .put("mode", mode)
        .put("nx", nx.toDouble())
        .put("ny", ny.toDouble())
        .put("delta", delta)
        .put("text", text)
        .put("key", key))

    suspend fun browserCursor(lastId: String): Pair<String, PcCursor?> {
        val o = call(req("/api/browser/cursor?last=" + java.net.URLEncoder.encode(lastId, "UTF-8")).build())
        val id = o.optString("id")
        if (o.optBoolean("same")) return id to null
        val b64 = o.optString("png")
        if (b64.isBlank()) return id to null
        val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return id to null
        return id to PcCursor(id, o.optInt("xHot"), o.optInt("yHot"), o.optInt("w", bmp.width), o.optInt("h", bmp.height), bmp)
    }
    suspend fun newConversation(text: String, model: String, attachments: List<Attachment> = emptyList(), preset: String = "",
                                projectId: String = "", mentions: List<Connector> = emptyList()): String =
        post("/api/conversations", JSONObject().put("text", text).put("model", model)
            .put("attachments", attachmentsJson(attachments)).put("permissionPreset", preset).put("projectId", projectId)
            .put("mentions", mentionsJson(mentions))).getString("id")

    // ---------- 项目 ----------
    private fun strs(a: JSONArray?) = (0 until (a?.length() ?: 0)).map { a!!.getString(it) }

    suspend fun projects(): List<Project> {
        val a = call(req("/api/projects").build()).optJSONArray("projects") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            Project(o.optString("id"), o.optString("name"), strs(o.optJSONArray("folders")), strs(o.optJSONArray("conversations")), o.optBoolean("sidecar"))
        } }
    }
    suspend fun createProject(name: String, folder: String): String =
        post("/api/projects", JSONObject().put("name", name).put("folder", folder)).getString("id")
    suspend fun deleteProject(id: String) { post("/api/projects/$id/delete", JSONObject()) }
    suspend fun renameProject(id: String, name: String) { post("/api/projects/$id/rename", JSONObject().put("name", name)) }
    suspend fun moveConversation(cid: String, projectId: String) { post("/api/move", JSONObject().put("cid", cid).put("projectId", projectId)) }

    // ---------- 自动化 ----------
    suspend fun automations(): List<Automation> {
        val a = call(req("/api/automations").build()).optJSONArray("automations") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            Automation(o.optString("id"), o.optString("name"), o.optString("description"), o.optString("schedule"), o.optString("prompt"),
                o.optBoolean("enabled"), o.optString("status"), o.optString("error"))
        } }
    }
    suspend fun saveAutomation(id: String?, name: String, schedule: String, prompt: String, model: String): String =
        post("/api/automations", JSONObject().put("id", id ?: "").put("name", name).put("schedule", schedule)
            .put("prompt", prompt).put("model", model)).getString("id")
    suspend fun automationAction(id: String, action: String) { post("/api/automations/$id/$action", JSONObject()) }

    // ---------- 插件 ----------
    suspend fun plugins(): Pair<List<Plugin>, List<Plugin>> {
        val o = call(req("/api/plugins").build())
        fun list(a: JSONArray?, inst: Boolean) = (0 until (a?.length() ?: 0)).map { a!!.getJSONObject(it).let { x ->
            Plugin(x.optString("id"), x.optString("title"), x.optString("description"), x.optString("trust"), x.optString("kind"),
                inst || x.optBoolean("installed"), x.optString("link"))
        } }
        return list(o.optJSONArray("installed"), true) to list(o.optJSONArray("market"), false)
    }
    suspend fun pluginAction(id: String, action: String) { post("/api/plugins/$action", JSONObject().put("id", id)) }

    suspend fun connectors(): List<Connector> {
        val a = call(req("/api/connectors").build()).optJSONArray("connectors") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            Connector(o.optString("key"), o.optString("kind"), o.optString("name"), o.optString("description"), o.optString("logo"),
                o.optBoolean("enabled"), if (o.isNull("connected")) null else o.optBoolean("connected"),
                o.optString("account"), o.optBoolean("needsAuth"), o.optString("error"))
        } }
    }
    suspend fun toggleConnector(c: Connector, enabled: Boolean) {
        post("/api/connectors/toggle", JSONObject().put("key", c.key).put("kind", c.kind).put("enabled", enabled))
    }

    // ---------- 和电脑同步：当前模型、权限 ----------
    suspend fun sync(): Pair<String, String> {
        val o = call(req("/api/sync").build())
        return o.optString("model") to o.optString("permissionPreset")
    }
    suspend fun setSync(model: String? = null, preset: String? = null) {
        val b = JSONObject()
        model?.let { b.put("model", it) }
        preset?.let { b.put("permissionPreset", it) }
        post("/api/sync", b)
    }

    suspend fun drafts(): Map<String, String> {
        val o = call(req("/api/drafts").build()).optJSONObject("drafts") ?: return emptyMap()
        val out = mutableMapOf<String, String>()
        val it = o.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = o.optString(k)
            if (v.isNotEmpty()) out[k] = v
        }
        return out
    }

    suspend fun saveDraft(cid: String, text: String) {
        post("/api/drafts", JSONObject().put("cid", cid).put("text", text))
    }

    // ---------- 插件市场 ----------
    suspend fun market(q: String = ""): Market {
        val o = call(req("/api/market?q=" + java.net.URLEncoder.encode(q, "UTF-8")).build())
        val its = o.optJSONArray("items") ?: JSONArray()
        val gs = o.optJSONArray("groups") ?: JSONArray()
        return Market(
            strs(o.optJSONArray("featured")),
            (0 until gs.length()).map { gs.getJSONObject(it).let { g -> MarketGroup(g.optString("id"), g.optString("name"), g.optString("description"), strs(g.optJSONArray("members"))) } },
            (0 until its.length()).map { its.getJSONObject(it).let { x ->
                MarketItem(x.optString("id"), x.optString("name"), x.optString("description"), x.optString("logo"), x.optString("author"),
                    x.optString("version"), x.optString("homepage"))
            } },
            strs(o.optJSONArray("installed")).toSet(),
        )
    }
    suspend fun marketAction(id: String, action: String) { post("/api/market/$action", JSONObject().put("id", id)) }
    suspend fun logo(url: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(req("/api/logo?u=" + java.net.URLEncoder.encode(url, "UTF-8")).build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
            r.body.bytes()
        }
    }

    // ---------- 自定义扩展 ----------
    suspend fun customizations(): List<Customization> {
        val a = call(req("/api/customizations").build()).optJSONArray("items") ?: JSONArray()
        return (0 until a.length()).map { a.getJSONObject(it).let { o ->
            Customization(o.optString("type"), o.optString("name"), o.optString("path"), o.optString("scope"), o.optString("status"), o.optString("error"))
        } }
    }

    suspend fun respond(id: String, step: Step, accept: Boolean, scope: String? = null,
                        answers: JSONArray? = null, text: String? = null) {
        val w = step.waiting ?: return
        val b = JSONObject().put("kind", w.kind).put("accept", accept)
            .put("stepIndex", step.i).put("trajectoryId", step.trajectoryId)
            .put("command", w.command).put("path", w.path)
        if (w.grant.isNotBlank()) b.put("grant", w.grant)
        scope?.let { b.put("scope", it) }
        answers?.let { b.put("answers", it) }
        text?.let { b.put("text", it) }
        post("/api/conversations/$id/respond", b)
    }

    /** 取电脑上 Antigravity 存的附件文件。 */
    suspend fun media(path: String, cid: String = "", ref: String = ""): ByteArray = withContext(Dispatchers.IO) {
        fun e(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
        val r = req("/api/media?path=${e(path)}&cid=${e(cid)}&ref=${e(ref)}").build()
        client.newCall(r).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException(resp.code, "HTTP ${resp.code}")
            resp.body.bytes()
        }
    }

    suspend fun translate(text: String, to: String): String =
        post("/api/translate", JSONObject().put("text", text).put("to", to)).optString("text")

    suspend fun feedback(id: String, stepIndex: Int, vote: Int) {
        post("/api/conversations/$id/feedback", JSONObject().put("stepIndex", stepIndex).put("vote", vote))
    }

    // ---------- 语音转写（电脑上的 Antigravity 识别） ----------
    suspend fun sttStart(): String = post("/api/stt/start", JSONObject()).getString("sid")

    suspend fun sttChunk(sid: String, seq: Int, pcm: ByteArray, len: Int) {
        call(req("/api/stt/chunk?sid=$sid&seq=$seq")
            .post(pcm.toRequestBody("application/octet-stream".toMediaType(), 0, len)).build())
    }

    suspend fun sttEnd(sid: String, last: Int): String =
        post("/api/stt/end", JSONObject().put("sid", sid).put("last", last)).optString("text")

    fun eventsRequest(): Request = req("/api/events").build()

    private fun parseConv(o: JSONObject) = Conversation(
        id = o.optString("id"), title = o.optString("title"), status = o.optString("status"),
        steps = o.optInt("steps"), updated = o.optString("updated"), workspace = o.optString("workspace"),
        pinned = o.optBoolean("pinned"), archived = o.optBoolean("archived"), unread = o.optBoolean("unread"),
        waiting = o.optBoolean("waiting"),
        permissionPreset = o.optString("permissionPreset"),
    )

    private fun parseStep(o: JSONObject): Step {
        val w = o.optJSONObject("waiting")?.let { wj ->
            val spec = wj.optJSONObject("spec") ?: JSONObject()
            val qs = wj.optJSONArray("questions") ?: JSONArray()
            val sc = wj.optJSONArray("scopes") ?: JSONArray()
            Waiting(
                kind = wj.optString("kind"),
                label = wj.optString("label"),
                action = wj.optString("action"),
                target = wj.optString("target"),
                reason = wj.optString("reason"),
                grant = wj.optString("grant"),
                persistBlocked = wj.optBoolean("persistBlocked"),
                scopes = (0 until sc.length()).mapNotNull { sc.optString(it).takeIf { s -> s.isNotBlank() } },
                command = o.optString("text"),
                path = spec.optString("absolutePathUri"),
                questions = (0 until qs.length()).map { k ->
                    val q = qs.getJSONObject(k)
                    val opts = q.optJSONArray("options") ?: JSONArray()
                    Question(q.optString("question"), q.optBoolean("multi"),
                        (0 until opts.length()).map { j -> opts.getJSONObject(j).let { it.optString("id") to it.optString("label") } })
                },
            )
        }
        return Step(
            i = o.optInt("i"), type = o.optString("type"), kind = o.optString("kind"), status = o.optString("status"),
            title = o.optString("title"), text = o.optString("text"), sub = o.optString("sub"),
            detail = o.optString("detail"), thinking = o.optString("thinking"),
            time = o.optString("time"),
            trajectoryId = o.optString("trajectoryId"), waiting = w,
            attachments = (o.optJSONArray("attachments") ?: JSONArray()).let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { x -> StepAttachment(x.optString("name"), x.optString("mime"), x.optString("path"), x.optString("ref")) } }
            },
            mentions = (o.optJSONArray("mentions") ?: JSONArray()).let { a ->
                (0 until a.length()).map { a.getJSONObject(it).let { x -> Mention(x.optString("name"), x.optString("logo")) } }
            },
        )
    }
}
