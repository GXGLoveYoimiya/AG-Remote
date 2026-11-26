package com.local.agremote.ui

import androidx.compose.material.icons.rounded.Settings
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.LocalContentColor
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreHoriz
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Base64
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Mouse
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Web
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.local.agremote.AppViewModel
import com.local.agremote.BrowserActivityData
import com.local.agremote.BrowserTab
import kotlinx.coroutines.delay

/** 把用户输入的网址/关键词规范化；localhost/127.0.0.1 自动转发到电脑 Tailscale 地址。 */
/** 可选的搜索引擎：key、名字、搜索网址前缀。 */
internal val SEARCH_ENGINES = listOf(
    Triple("google", "Google", "https://www.google.com/search?q="),
    Triple("bing", "Bing", "https://www.bing.com/search?q="),
    Triple("baidu", "百度", "https://www.baidu.com/s?wd="),
    Triple("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q="),
    Triple("yahoo", "Yahoo", "https://search.yahoo.com/search?p="),
    Triple("yandex", "Yandex", "https://yandex.com/search/?text="),
)
/** 当前搜索引擎的网址前缀（打开浏览器时按设置更新）。 */
private var searchPrefix = SEARCH_ENGINES[0].third

private fun normalizeUrl(raw: String, pcHost: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    val withScheme = when {
        s.startsWith("http://", true) || s.startsWith("https://", true) -> s
        s.contains(' ') || (!s.contains('.') && !s.startsWith("localhost", true)) ->
            searchPrefix + java.net.URLEncoder.encode(s, "UTF-8")
        s.startsWith("localhost", true) || s.startsWith("127.0.0.1") || s.startsWith("100.") || s.startsWith("192.168.") -> "http://$s"
        else -> "https://$s"
    }
    if (pcHost.isNotBlank()) {
        return withScheme.replace(Regex("^(https?://)(?:localhost|127\\.0\\.0\\.1)(?=[:/]|$)", RegexOption.IGNORE_CASE), "$1$pcHost")
    }
    return withScheme
}

private fun decodeFavicon(dataUrl: String): Bitmap? {
    val i = dataUrl.indexOf("base64,")
    if (i < 0) return null
    return runCatching {
        val bytes = Base64.decode(dataUrl.substring(i + 7), Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

/**
 * 与电脑 Antigravity 内置浏览器同步的全屏浏览器：
 * - 「电脑实况」模式（点右上角地球图标默认进入）：实时同步电脑 Antigravity 客户端内置浏览器的真实画面（含 Gemini 操作过程、登录态与页面变化），支持双指缩放，并附带 Gemini 浏览器动态与截图；
 * - 「网页浏览」模式（点消息里的网页链接默认进入，也可随时一键切换）：走电脑网络的交互式 WebView，导航栏置于上方，强制跟随 App 浅色/深色主题。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserDialog(
    vm: AppViewModel,
    chatId: String,
    explicitUrl: String?,
    quickLinks: List<String>,
    onClose: () -> Unit,
) {
    val ctx = LocalContext.current
    val focusManager = LocalFocusManager.current
    var engineKey by remember { mutableStateOf(vm.prefs.searchEngine) }
    searchPrefix = (SEARCH_ENGINES.find { it.first == engineKey } ?: SEARCH_ENGINES[0]).third
    val pcData by vm.pcBrowserTabs.collectAsState()
    val pcLoading by vm.pcBrowserLoading.collectAsState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceArgb = surfaceColor.toArgb()
    val isDark = surfaceColor.luminance() < 0.5f

    // explicitUrl 非空（点链接进来）→ 默认网页浏览；点右上角图标进来 → 默认电脑实况
    // 手机浏览器独立管理自己的标签页（不自动和电脑同步）；关掉再打开、重启 App 都会恢复
    val savedTabs = remember { vm.loadBrowserTabs() }
    var viewMode by remember(explicitUrl) { mutableStateOf(if (explicitUrl != null) "web" else savedTabs.third) }
    var webEverMounted by remember(explicitUrl) { mutableStateOf(explicitUrl != null || savedTabs.third == "web") }
    LaunchedEffect(viewMode) { if (viewMode == "web") webEverMounted = true }

    // 「电脑实况」状态（默认截取实时电脑屏幕 + 默认开启虚拟鼠标操控）
    val captureCrop = "screen" // 始终显示整个电脑屏幕
    var controlMode by remember { mutableStateOf("mouse") } // mouse(鼠标操控) / touch(直接触控) / view(仅查看)
    var livePaused by remember { mutableStateOf(false) }
    var liveFrame by remember { mutableStateOf<Bitmap?>(null) }
    var liveError by remember { mutableStateOf<String?>(null) }
    var liveTick by remember { mutableIntStateOf(0) }
    var activity by remember { mutableStateOf(BrowserActivityData()) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var cursorNorm by remember { mutableStateOf(Offset(0.5f, 0.5f)) }
    var lastMoveSentAt by remember { mutableStateOf(0L) }
    var leftDragging by remember { mutableStateOf(false) }
    var pcCursor by remember { mutableStateOf<com.local.agremote.PcCursor?>(null) }
    var lastCursorId by remember { mutableStateOf("") }
    var cursorScale by remember { mutableFloatStateOf(vm.prefs.cursorScale) }
    var showCursorSlider by remember { mutableStateOf(false) }
    var showRemoteKeyboard by remember { mutableStateOf(false) }
    var remoteTv by remember { mutableStateOf(TextFieldValue("")) }
    var readingField by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // 实时同步电脑当前光标样式（跟随 Windows 主题、自定义光标包以及手型/输入条/缩放等动态形状）
    LaunchedEffect(viewMode, controlMode) {
        if (viewMode != "live" || controlMode != "mouse") return@LaunchedEffect
        while (true) {
            val res = vm.fetchPcCursor(lastCursorId)
            if (res != null) {
                lastCursorId = res.first
                res.second?.let { pcCursor = it }
            }
            delay(450)
        }
    }

    fun loadPcField() {
        scope.launch {
            readingField = true
            val txt = vm.readPcFieldText(captureCrop)
            readingField = false
            if (txt != null) {
                remoteTv = TextFieldValue(txt, TextRange(txt.length))
                if (txt.isEmpty()) vm.showToast("电脑输入框当前为空（可先点一下输入框再读取）")
            } else {
                vm.showToast("读取电脑输入框失败")
            }
        }
    }

    var streaming by remember { mutableStateOf(false) }
    fun refreshSoon() {
        if (streaming) return // 视频流本来就是实时的
        scope.launch {
            delay(160)
            val bmp = vm.capturePcBrowser(captureCrop)
            if (bmp != null) { liveFrame = bmp; liveError = null }
        }
    }

    // 实时电脑画面：优先用视频流（连续推帧），连不上再退回一张张截图
    var streamQ by remember { mutableStateOf(vm.prefs.streamQuality) }
    LaunchedEffect(viewMode, captureCrop, livePaused, liveTick, streamQ) {
        if (viewMode != "live") return@LaunchedEffect
        if (!livePaused) {
            var failures = 0
            while (failures < 3) {
                var got = false
                runCatching {
                    vm.pcScreenStream(vm.streamPreset()).collect { bmp -> liveFrame = bmp; liveError = null; streaming = true; got = true }
                }
                streaming = false
                if (got) failures = 0 else failures++
                delay(800)
            }
        }
        while (true) {
            val bmp = vm.capturePcBrowser(captureCrop)
            if (bmp != null) {
                liveFrame = bmp
                liveError = null
            } else if (liveFrame == null) {
                liveError = "未捕获到电脑 Antigravity 窗口（可能已最小化到托盘或锁屏）"
            }
            if (livePaused) break
            delay(650)
        }
    }
    LaunchedEffect(viewMode, chatId) {
        if (viewMode != "live") return@LaunchedEffect
        while (true) {
            if (chatId.isNotBlank()) activity = vm.loadBrowserActivity(chatId)
            delay(2500)
        }
    }

    // 「网页浏览」代理：走电脑网络（仅在进入网页浏览时启用，关闭时还原）
    var proxyReady by remember { mutableStateOf(false) }
    DisposableEffect(vm.prefs.host, vm.prefs.port) {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE) && vm.prefs.host.isNotBlank()) {
            val cfg = ProxyConfig.Builder()
                .addProxyRule("http://${vm.prefs.host}:${vm.prefs.port}")
                .build()
            runCatching {
                ProxyController.getInstance().setProxyOverride(cfg, { r -> r.run() }) { proxyReady = true }
            }.onFailure { proxyReady = true }
        } else {
            proxyReady = true
        }
        onDispose {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.PROXY_OVERRIDE)) {
                runCatching { ProxyController.getInstance().clearProxyOverride({ r -> r.run() }) {} }
            }
        }
    }

    val initTabs = remember {
        val tabs = savedTabs.first.toMutableList()
        var active = savedTabs.second.takeIf { id -> tabs.any { it.id == id } } ?: ""
        explicitUrl?.let { normalizeUrl(it, vm.prefs.host) }?.takeIf { it.isNotBlank() }?.let { u ->
            val hit = tabs.find { normalizeUrl(it.url, vm.prefs.host) == u }
            if (hit != null) active = hit.id
            else {
                val id = "local-${System.currentTimeMillis()}"
                tabs += BrowserTab(id, u, runCatching { android.net.Uri.parse(u).host ?: u }.getOrElse { u }, "", chatId, "")
                active = id
            }
        }
        tabs.toList() to active
    }
    var activeUrl by remember { mutableStateOf(initTabs.first.find { it.id == initTabs.second }?.url?.let { normalizeUrl(it, vm.prefs.host) } ?: "") }
    var urlInput by remember { mutableStateOf(activeUrl) }
    // 地址栏光标：网址被页面跳转等自动改写时，光标放到末尾（否则会停在旧位置，出现在网址中间）
    var urlField by remember { mutableStateOf(TextFieldValue(urlInput, TextRange(urlInput.length))) }
    var pageTitle by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var pageCommitted by remember { mutableStateOf(false) }
    var webError by remember { mutableStateOf<String?>(null) }
    var canBack by remember { mutableStateOf(false) }
    var canForward by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    // 网页开的弹窗（第三方账号登录等），关掉时置空，WebView 在 AndroidView 释放时销毁
    var popupView by remember { mutableStateOf<WebView?>(null) }
    var popupTitle by remember { mutableStateOf("") }
    var lastSyncedPcUrl by remember { mutableStateOf("") }
    var userNavigated by remember { mutableStateOf(explicitUrl != null) }
    var localTabs by remember { mutableStateOf(initTabs.first) }
    var activeTabId by remember { mutableStateOf(initTabs.second) }
    LaunchedEffect(localTabs, activeTabId, viewMode) { vm.saveBrowserTabs(localTabs, activeTabId, viewMode) }

    val allSyncedTabs = localTabs

    fun updateActiveLocalTab(newUrl: String? = null, newTitle: String? = null) {
        if (!activeTabId.startsWith("local-")) return
        localTabs = localTabs.map { t ->
            if (t.id != activeTabId) t
            else t.copy(
                url = newUrl?.takeIf { it.isNotBlank() } ?: t.url,
                title = newTitle?.takeIf { it.isNotBlank() } ?: t.title,
            )
        }
    }

    val hiddenLinks by vm.hiddenLinks.collectAsState()

    /** 新建标签页：回到浏览器首页，下一个打开的网址会开成新的标签页。 */
    fun newTab() {
        activeTabId = ""
        activeUrl = ""
        urlInput = ""
        webError = null
        webEverMounted = true
        viewMode = "web"
    }

    fun openUrl(raw: String, manual: Boolean = true, tabId: String = "") {
        val u = normalizeUrl(raw, vm.prefs.host)
        if (u.isBlank()) return
        if (tabId.isNotBlank()) {
            activeTabId = tabId
        } else {
            val matched = allSyncedTabs.firstOrNull { normalizeUrl(it.url, vm.prefs.host) == u }
            if (matched != null) {
                activeTabId = matched.id
            } else if (activeTabId.startsWith("local-") && localTabs.any { it.id == activeTabId }) {
                val hostTitle = runCatching { android.net.Uri.parse(u).host ?: u }.getOrElse { u }
                updateActiveLocalTab(newUrl = u, newTitle = hostTitle)
            } else {
                val newId = "local-${System.currentTimeMillis()}"
                val hostTitle = runCatching { android.net.Uri.parse(u).host ?: u }.getOrElse { u }
                localTabs = localTabs + BrowserTab(newId, u, hostTitle, "", chatId, "")
                activeTabId = newId
            }
        }
        if (manual) {
            userNavigated = true
            webEverMounted = true
            viewMode = "web"
            webViewRef?.visibility = android.view.View.VISIBLE
        }
        urlInput = u
        webError = null
        if (u != activeUrl) {
            pageCommitted = false
            loading = true
            progress = 10
            activeUrl = u
            webViewRef?.loadUrl(u)
        } else {
            webViewRef?.reload()
        }
        focusManager.clearFocus()
    }

    fun clearToHome() {
        activeTabId = ""
        activeUrl = ""
        urlInput = ""
        pageTitle = ""
        loading = false
        pageCommitted = true
        canBack = false
        canForward = false
        // WebView 随 activeUrl 变空离开界面，由 AndroidView 的 onRelease 销毁
        webViewRef?.stopLoading()
    }

    fun handleCloseTab(t: BrowserTab) {
        val closingCurrent = t.id == activeTabId
        val next = localTabs.filterNot { it.id == t.id }
        localTabs = next
        if (next.isEmpty()) clearToHome()
        else if (closingCurrent) {
            val nextTab = next.last()
            if (viewMode == "web") openUrl(nextTab.url, tabId = nextTab.id)
            else { activeTabId = nextTab.id; activeUrl = normalizeUrl(nextTab.url, vm.prefs.host); urlInput = nextTab.url }
        }
    }

    // “同步电脑标签页”：把电脑上 Antigravity 打开的网页复制一份到手机（之后各管各的，不再自动同步）
    var importPcTabs by remember { mutableStateOf(false) }
    LaunchedEffect(pcData, importPcTabs) {
        if (!importPcTabs || pcLoading) return@LaunchedEffect
        importPcTabs = false
        val have = localTabs.map { normalizeUrl(it.url, vm.prefs.host) }.toSet()
        val add = (pcData.tabs + pcData.otherTabs).filter { it.url.isNotBlank() && normalizeUrl(it.url, vm.prefs.host) !in have }
            .distinctBy { normalizeUrl(it.url, vm.prefs.host) }
            .mapIndexed { i, pt -> BrowserTab("local-${System.currentTimeMillis()}-$i", pt.url, pt.title.ifBlank { pt.url }, "", chatId, "") }
        localTabs = localTabs + add
        vm.showToast(if (add.isEmpty()) "电脑上没有新的标签页" else "已从电脑复制 ${add.size} 个标签页")
    }

    // 标签页切换页（照 Chrome 的布局，用我们自己的样式）和每个标签页的缩略图
    var showTabs by remember { mutableStateOf(false) }
    val thumbs = remember { androidx.compose.runtime.mutableStateMapOf<String, Bitmap>() }
    fun snapThumb() {
        val wv = webViewRef ?: return
        if (activeTabId.isBlank() || wv.width <= 0 || wv.height <= 0) return
        runCatching {
            val full = Bitmap.createBitmap(wv.width, wv.height, Bitmap.Config.ARGB_8888)
            wv.draw(android.graphics.Canvas(full))
            thumbs[activeTabId] = Bitmap.createScaledBitmap(full, wv.width / 3, wv.height / 3, true)
            full.recycle()
        }
    }

    // 网页翻译：取出页面上的文字，交给电脑整批翻译，再替换回页面
    var pageTranslated by remember { mutableStateOf(false) }
    var translating by remember { mutableStateOf(false) }
    fun translatePage() {
        val wv = webViewRef ?: return
        translating = true
        vm.showToast("正在翻译…")
        wv.evaluateJavascript(PAGE_TEXT_JS) { res ->
            scope.launch {
                val arr = runCatching { org.json.JSONArray(org.json.JSONTokener(res).nextValue() as String) }.getOrNull()
                val texts = if (arr == null) emptyList() else (0 until arr.length()).map { arr.getString(it) }.distinct()
                var done = 0
                // 一批 250 段，翻好一批就换一批（长页面不会等太久，也不会超时）
                for (chunk in texts.chunked(250)) {
                    val map = vm.translatePageTexts(chunk, I18n.currentLang)
                    if (map.isEmpty()) continue
                    done += map.size
                    webViewRef?.evaluateJavascript("($APPLY_TEXT_JS)(${org.json.JSONObject(map as Map<*, *>)})", null)
                    pageTranslated = true
                }
                translating = false
                if (done == 0) vm.showToast(if (texts.isEmpty()) "这个页面没有可翻译的文字" else "翻译失败")
            }
        }
    }

    // 打开外语网页时自动问要不要翻译（像 Chrome 那样）；关掉后这个网站这次不再问
    var offerTranslate by remember { mutableStateOf<String?>(null) } // 网页的语言名（空 = 不确定）
    var offerLangCode by remember { mutableStateOf("") }
    val noOfferHosts = remember { mutableSetOf<String>() }
    fun checkTranslateOffer() {
        val wv = webViewRef ?: return
        val host = runCatching { android.net.Uri.parse(activeUrl).host }.getOrNull() ?: return
        if (host in noOfferHosts || host in vm.prefs.trNeverHosts || pageTranslated) return
        wv.evaluateJavascript(LANG_SAMPLE_JS) { res ->
            val arr = runCatching { org.json.JSONArray(org.json.JSONTokener(res).nextValue() as String) }.getOrNull() ?: return@evaluateJavascript
            val declared = arr.optString(0).lowercase().substringBefore('-').substringBefore('_')
            val sample = arr.optString(1)
            val app = I18n.currentLang
            val (foreign, guessed) = mostlyForeign(sample, declared, app)
            val pageBase = declared.ifBlank { guessed }
            if (foreign && !pageTranslated) {
                if (pageBase.isNotBlank() && pageBase in vm.prefs.trNeverLangs) return@evaluateJavascript
                if (pageBase.isNotBlank() && pageBase in vm.prefs.trAutoLangs) { translatePage(); return@evaluateJavascript } // 一律翻译这种语言
                offerLangCode = pageBase
                offerTranslate = if (pageBase.isBlank()) "" else
                    java.util.Locale.forLanguageTag(pageBase).getDisplayLanguage(java.util.Locale.forLanguageTag(app))
            }
        }
    }

    /** 显示原文：把保存的原文换回去，不刷新页面。 */
    fun showOriginal() {
        webViewRef?.evaluateJavascript(RESTORE_TEXT_JS, null)
        pageTranslated = false
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        BackHandler {
            val wv = webViewRef
            val pv = popupView
            when {
                showTabs -> showTabs = false
                pv != null && pv.canGoBack() -> pv.goBack()
                pv != null -> popupView = null
                viewMode == "web" && wv != null && wv.canGoBack() -> wv.goBack()
                viewMode == "web" && explicitUrl == null -> {
                    webViewRef?.visibility = android.view.View.INVISIBLE
                    viewMode = "live"
                }
                else -> onClose()
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
        ) {
            // 顶栏（照 Claude）：左边关闭，中间模式切换，右边“⋯”菜单收起其余操作
            var topMenu by remember { mutableStateOf(false) }
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.Rounded.Close, tr("关闭"))
                }
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.align(Alignment.Center).height(38.dp),
                ) {
                    Row(Modifier.padding(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        ModePill(tr("电脑实况"), viewMode == "live") {
                            webViewRef?.visibility = android.view.View.INVISIBLE
                            viewMode = "live"
                        }
                        ModePill(tr("网页浏览"), viewMode == "web") {
                            webEverMounted = true
                            viewMode = "web"
                            webViewRef?.visibility = android.view.View.VISIBLE
                        }
                    }
                }
                if (viewMode == "live") Box(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { topMenu = true }) { Icon(Icons.Rounded.MoreHoriz, tr("更多操作")) }
                    DropdownMenu(topMenu, { topMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
                        DropdownMenuItem(text = { Text(if (livePaused) tr("继续实时同步") else tr("暂停同步")) },
                            leadingIcon = { Icon(if (livePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null) },
                            onClick = { topMenu = false; livePaused = !livePaused })
                        DropdownMenuItem(text = { Text(tr("刷新画面")) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { topMenu = false; liveTick++ })
                    }
                }
            }

            // 双页面容器：WebView 一旦挂载就常驻不销毁，切换「电脑实况 ↔ 网页浏览」零闪烁、不重载页面
            Box(Modifier.weight(1f).fillMaxWidth().background(surfaceColor)) {
                // 1) 网页浏览层（常驻保活）
                if (webEverMounted) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .zIndex(if (viewMode == "web") 2f else 0f)
                            .graphicsLayer { alpha = if (viewMode == "web") 1f else 0f }
                            .background(surfaceColor),
                    ) {
                        if (!showTabs) Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { snapThumb(); newTab() }, modifier = Modifier.size(44.dp)) {
                                Icon(Icons.Rounded.Home, tr("主页"))
                            }
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = sheetCardColor(),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                                modifier = Modifier.weight(1f).height(42.dp),
                            ) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(if (activeUrl.startsWith("https://")) Icons.Rounded.Lock else Icons.Rounded.Search, null, Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(8.dp))
                                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                        if (urlInput.isEmpty()) {
                                            Text(tr("输入网址或搜索（走电脑网络）…"), style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        BasicTextField(
                                            value = if (urlField.text == urlInput) urlField
                                                else TextFieldValue(urlInput, TextRange(urlInput.length)),
                                            onValueChange = { urlField = it; urlInput = it.text },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                            keyboardActions = KeyboardActions(onGo = { openUrl(urlInput) }),
                                        )
                                    }
                                    if (urlInput.isNotEmpty()) {
                                        IconButton(onClick = { urlInput = "" }, modifier = Modifier.size(28.dp)) {
                                            Icon(Icons.Rounded.Close, "清空", Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            // 翻译：翻过后图标亮起，再点显示原文
                            IconButton(onClick = { offerTranslate = null; if (pageTranslated) showOriginal() else translatePage() },
                                enabled = activeUrl.isNotBlank() && !translating, modifier = Modifier.size(44.dp)
                                    .then(if (pageTranslated) Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape) else Modifier)) {
                                Icon(Icons.Rounded.Translate, if (pageTranslated) tr("显示原文") else tr("翻译此页"),
                                    tint = if (pageTranslated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) // 未翻译：黑色无框；已翻译：加圆框（默认主题黑色，其他主题强调色）
                            }
                            // 标签页数：点开标签页切换页
                            IconButton(onClick = { snapThumb(); showTabs = true }, modifier = Modifier.size(44.dp)) {
                                Box(Modifier.size(22.dp).border(1.8.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(6.dp)),
                                    contentAlignment = Alignment.Center) {
                                    Text("${localTabs.size.coerceAtMost(99)}", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            // ⋮：上面一排圆形按钮（后退 / 前进 / 刷新），下面几项常用操作
                            Box {
                                var webMenu by remember { mutableStateOf(false) }
                                IconButton(onClick = { webMenu = true }, modifier = Modifier.size(44.dp)) { Icon(Icons.Rounded.MoreVert, tr("更多操作")) }
                                DropdownMenu(webMenu, { webMenu = false }, shape = RoundedCornerShape(24.dp), containerColor = sheetCardColor()) {
                                    Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        RoundAction(Icons.AutoMirrored.Rounded.ArrowBack, tr("后退"), canBack) { webMenu = false; webViewRef?.goBack() }
                                        RoundAction(Icons.AutoMirrored.Rounded.ArrowForward, tr("前进"), canForward) { webMenu = false; webViewRef?.goForward() }
                                        RoundAction(if (loading) Icons.Rounded.Close else Icons.Rounded.Refresh, if (loading) tr("停止加载") else tr("刷新"),
                                            activeUrl.isNotBlank()) { webMenu = false; if (loading) webViewRef?.stopLoading() else webViewRef?.reload() }
                                    }
                                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                    DropdownMenuItem(text = { Text(tr("新建标签页")) }, leadingIcon = { Icon(Icons.Rounded.Add, null) },
                                        onClick = { webMenu = false; snapThumb(); newTab() })
                                    DropdownMenuItem(text = { Text(tr("同步电脑标签页")) }, leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                                        onClick = { webMenu = false; importPcTabs = true; vm.refreshBrowserTabs(chatId) })
                                    Box {
                                        var engineMenu by remember { mutableStateOf(false) }
                                        val cur = SEARCH_ENGINES.find { it.first == engineKey } ?: SEARCH_ENGINES[0]
                                        DropdownMenuItem(text = { Text(tr("搜索引擎") + " · " + tr(cur.second)) }, leadingIcon = { Icon(Icons.Rounded.Search, null) },
                                            onClick = { engineMenu = true })
                                        DropdownMenu(engineMenu, { engineMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
                                            SEARCH_ENGINES.forEach { (key, name, _) ->
                                                DropdownMenuItem(text = { Text(tr(name)) },
                                                    trailingIcon = { if (key == engineKey) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                                                    onClick = { engineMenu = false; webMenu = false; engineKey = key; vm.prefs.searchEngine = key })
                                            }
                                        }
                                    }
                                    if (activeUrl.isNotBlank()) {
                                        HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                                        DropdownMenuItem(text = { Text(tr("复制网址")) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                            onClick = { webMenu = false; vm.copy(activeUrl, "已复制网址") })
                                        DropdownMenuItem(text = { Text(tr("分享")) }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                            onClick = {
                                                webMenu = false
                                                val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
                                                    .putExtra(android.content.Intent.EXTRA_TEXT, activeUrl)
                                                runCatching { ctx.startActivity(android.content.Intent.createChooser(send, null)) }
                                            })
                                        DropdownMenuItem(text = { Text(tr("在外部浏览器打开")) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                            onClick = { webMenu = false; openWebLink(ctx, activeUrl) })
                                    }
                                }
                            }
                        }


                        if (showTabs) {
                        } else if (activeUrl.isNotBlank() && loading && progress in 1..99) {
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                        if (showTabs) TabSwitcher(
                            tabs = localTabs, activeId = activeTabId, thumbs = thumbs,
                            onNew = { showTabs = false; newTab() },
                            onSelect = { tab -> showTabs = false; if (tab.id != activeTabId) openUrl(tab.url, tabId = tab.id) },
                            onClose = { tab -> thumbs.remove(tab.id); handleCloseTab(tab) },
                            onDismiss = { showTabs = false },
                        )

                        Box(Modifier.weight(if (showTabs) 0.0001f else 1f).fillMaxWidth().clipToBounds().background(surfaceColor)) {
                            if (activeUrl.isBlank()) {
                                BrowserSyncHome(
                                    pcTabs = emptyList(),
                                    otherPcTabs = emptyList(),
                                    quickLinks = quickLinks.filter { it !in hiddenLinks },
                                    onRemoveLink = { vm.hideLink(it) },
                                    onCopyLink = { vm.copy(it, "已复制链接") },
                                    syncing = pcLoading,
                                    onOpen = { openUrl(it) },
                                    onCloseTab = { t -> vm.closeBrowserTab(t, chatId) },
                                )
                            } else if (!proxyReady) {
                                Box(Modifier.fillMaxSize().background(surfaceColor), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                                }
                            } else {
                              // 网页深浅色跟随 app：WebView 按它所在 Context 的主题告诉网站 prefers-color-scheme，
                              // 而 app 的 XML 主题是深色 Theme.Material，所以这里按 app 当前主题包一层；切换主题时重建并重新加载
                              androidx.compose.runtime.key(isDark) {
                                AndroidView(
                                    factory = { c ->
                                        WebView(android.view.ContextThemeWrapper(c,
                                            if (isDark) android.R.style.Theme_Material_NoActionBar
                                            else android.R.style.Theme_Material_Light_NoActionBar)).apply {
                                            // 必须是 MATCH_PARENT：AndroidView 默认给 WRAP_CONTENT，WebView 会因此把 vh/vmin 当 0，
                                            // 导致 B 站等用 vh/vmin 排版的网站标题、顶栏、图片尺寸变成 0
                                            layoutParams = android.view.ViewGroup.LayoutParams(
                                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                            )
                                            setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)
                                            setBackgroundColor(surfaceArgb)
                                            isForceDarkAllowed = false
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            // 只看网页，不让网页读手机本地文件
                                            settings.allowFileAccess = false
                                            settings.allowContentAccess = false
                                            settings.loadsImagesAutomatically = true
                                            settings.blockNetworkImage = false
                                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                                            settings.textZoom = 100
                                            settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING
                                            settings.javaScriptCanOpenWindowsAutomatically = true
                                            settings.setSupportMultipleWindows(true)
                                            settings.mediaPlaybackRequiresUserGesture = false
                                            settings.builtInZoomControls = true
                                            settings.displayZoomControls = false
                                            settings.useWideViewPort = true
                                            settings.loadWithOverviewMode = true
                                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                            // 去掉 `; wv)` 标记，避免 Google / Cloudflare / 知乎 / B站 等站点屏蔽 WebView
                                            settings.userAgentString = settings.userAgentString
                                                .replace("; wv)", ")")
                                                .replace("Version/4.0 ", "")
                                            runCatching {
                                                val cm = android.webkit.CookieManager.getInstance()
                                                cm.setAcceptCookie(true)
                                                cm.setAcceptThirdPartyCookies(this, true)
                                            }
                                            if (Build.VERSION.SDK_INT >= 33) {
                                                settings.isAlgorithmicDarkeningAllowed = false
                                            }
                                            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                                                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, false)
                                            }
                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                                    val u = request?.url?.toString() ?: return false
                                                    if (u.startsWith("http://") || u.startsWith("https://") ||
                                                        u.startsWith("about:") || u.startsWith("blob:") ||
                                                        u.startsWith("data:") || u.startsWith("javascript:")) {
                                                        return false
                                                    }
                                                    if (u.startsWith("mailto:") || u.startsWith("tel:")) {
                                                        openWebLink(ctx, u)
                                                        return true
                                                    }
                                                    // 跳 App（bilibili://、intent://…）：只放行你亲手点的；网页自己偷偷发起的跳转仍然拦截
                                                    if (request.hasGesture()) {
                                                        openAppLink(ctx, u) { fallback -> view?.loadUrl(fallback) }
                                                    }
                                                    return true
                                                }
                                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                                    pageTranslated = false
                                                    offerTranslate = null
                                                    loading = true
                                                    webError = null
                                                    url?.let { urlInput = it; activeUrl = it }
                                                    canBack = view?.canGoBack() == true
                                                    canForward = view?.canGoForward() == true
                                                }
                                                override fun onPageCommitVisible(view: WebView?, url: String?) { pageCommitted = true }
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    loading = false
                                                    pageCommitted = true
                                                    progress = 100
                                                    url?.let { urlInput = it; activeUrl = it }
                                                    pageTitle = view?.title.orEmpty()
                                                    updateActiveLocalTab(newUrl = url, newTitle = view?.title)
                                                    checkTranslateOffer()
                                                    canBack = view?.canGoBack() == true
                                                    canForward = view?.canGoForward() == true
                                                }
                                                override fun onReceivedError(
                                                    view: WebView?,
                                                    request: WebResourceRequest?,
                                                    error: android.webkit.WebResourceError?,
                                                ) {
                                                    if (request?.isForMainFrame == true) {
                                                        loading = false
                                                        pageCommitted = true
                                                        webError = error?.description?.toString()?.takeIf { it.isNotBlank() } ?: "网页加载失败"
                                                    }
                                                }
                                            }
                                            webChromeClient = object : WebChromeClient() {
                                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                    progress = newProgress
                                                    if (newProgress >= 30) pageCommitted = true
                                                    loading = newProgress < 100
                                                }
                                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                                    pageTitle = title.orEmpty()
                                                    updateActiveLocalTab(newTitle = title)
                                                }
                                                override fun onCreateWindow(
                                                    view: WebView?,
                                                    isDialog: Boolean,
                                                    isUserGesture: Boolean,
                                                    resultMsg: android.os.Message?,
                                                ): Boolean {
                                                    val hit = view?.hitTestResult
                                                    val href = hit?.extra
                                                    // 普通的“新标签页打开”链接：直接在当前页打开
                                                    if (hit?.type == WebView.HitTestResult.SRC_ANCHOR_TYPE && !href.isNullOrBlank() &&
                                                        (href.startsWith("http://") || href.startsWith("https://"))) {
                                                        openUrl(href)
                                                        return true
                                                    }
                                                    // 网页脚本开的弹窗（“用 Google 账号登录”等）：开一个真的子窗口，
                                                    // 保留和原网页的联系，登录完结果才能传回去（以前改成当前页打开，登录后就卡住/空白）
                                                    val child = WebView(view?.context ?: c)
                                                    child.layoutParams = android.view.ViewGroup.LayoutParams(
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                                    )
                                                    child.setBackgroundColor(surfaceArgb)
                                                    child.settings.javaScriptEnabled = true
                                                    child.settings.domStorageEnabled = true
                                                    child.settings.javaScriptCanOpenWindowsAutomatically = true
                                                    child.settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                                    child.settings.useWideViewPort = true
                                                    child.settings.loadWithOverviewMode = true
                                                    view?.settings?.userAgentString?.let { child.settings.userAgentString = it }
                                                    runCatching { android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(child, true) }
                                                    child.webViewClient = object : WebViewClient() {
                                                        override fun shouldOverrideUrlLoading(v: WebView?, req: WebResourceRequest?): Boolean {
                                                            val u = req?.url?.toString() ?: return false
                                                            if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:") ||
                                                                u.startsWith("blob:") || u.startsWith("data:") || u.startsWith("javascript:")) return false
                                                            if (req.hasGesture()) openAppLink(ctx, u) { fb -> v?.loadUrl(fb) }
                                                            return true
                                                        }
                                                    }
                                                    child.webChromeClient = object : WebChromeClient() {
                                                        override fun onCloseWindow(window: WebView?) { if (popupView === window) popupView = null }
                                                        override fun onReceivedTitle(v: WebView?, title: String?) { popupTitle = title.orEmpty() }
                                                    }
                                                    popupTitle = ""
                                                    popupView = child
                                                    (resultMsg?.obj as? WebView.WebViewTransport)?.webView = child
                                                    resultMsg?.sendToTarget()
                                                    return true
                                                }
                                            }
                                            setDownloadListener { url, _, _, _, _ -> openWebLink(ctx, url) }
                                            webViewRef = this
                                            loadUrl(activeUrl)
                                        }
                                    },
                                    update = { wv ->
                                        wv.setBackgroundColor(surfaceArgb)
                                        wv.visibility = if (viewMode == "web") android.view.View.VISIBLE else android.view.View.INVISIBLE
                                    },
                                    modifier = Modifier.fillMaxSize(),
                                    // 切深浅色重建、回到首页、关浏览器时，旧 WebView 在这里统一销毁（之前切主题会泄漏一个）
                                    onRelease = { wv -> if (webViewRef === wv) webViewRef = null; wv.stopLoading(); wv.destroy() },
                                )
                              }
                                offerTranslate?.let { src ->
                                    val app = I18n.currentLang
                                    val target = java.util.Locale.forLanguageTag(app).getDisplayLanguage(java.util.Locale.forLanguageTag(app))
                                    Surface(
                                        shape = RoundedCornerShape(20.dp), color = sheetCardColor(), shadowElevation = 6.dp,
                                        modifier = Modifier.align(Alignment.TopCenter).zIndex(4f).padding(top = 8.dp, start = 12.dp, end = 12.dp).fillMaxWidth(),
                                    ) {
                                        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Rounded.Translate, null, tint = accent())
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(tr("翻译网页吗？"), style = MaterialTheme.typography.bodyLarge)
                                                Text(if (src.isBlank()) tr("翻译成{0}", target) else tr("从{0}到{1}", src, target),
                                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                            Box {
                                                var gear by remember { mutableStateOf(false) }
                                                IconButton(onClick = { gear = true }) { Icon(Icons.Rounded.Settings, tr("翻译设置"), Modifier.size(20.dp)) }
                                                DropdownMenu(gear, { gear = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
                                                    val code = offerLangCode
                                                    val host = runCatching { android.net.Uri.parse(activeUrl).host }.getOrNull().orEmpty()
                                                    if (code.isNotBlank()) {
                                                        DropdownMenuItem(text = { Text(tr("一律翻译{0}网页", src)) }, onClick = {
                                                            gear = false; vm.prefs.trAutoLangs = vm.prefs.trAutoLangs + code
                                                            vm.prefs.trNeverLangs = vm.prefs.trNeverLangs - code
                                                            offerTranslate = null; translatePage()
                                                        })
                                                        DropdownMenuItem(text = { Text(tr("一律不翻译{0}网页", src)) }, onClick = {
                                                            gear = false; vm.prefs.trNeverLangs = vm.prefs.trNeverLangs + code
                                                            vm.prefs.trAutoLangs = vm.prefs.trAutoLangs - code; offerTranslate = null
                                                        })
                                                    }
                                                    if (host.isNotBlank()) DropdownMenuItem(text = { Text(tr("一律不翻译此网站")) }, onClick = {
                                                        gear = false; vm.prefs.trNeverHosts = vm.prefs.trNeverHosts + host; offerTranslate = null
                                                    })
                                                }
                                            }
                                            androidx.compose.material3.TextButton(onClick = { offerTranslate = null; translatePage() }) { Text(tr("翻译")) }
                                            IconButton(onClick = {
                                                runCatching { android.net.Uri.parse(activeUrl).host }.getOrNull()?.let { noOfferHosts += it }
                                                offerTranslate = null
                                            }) { Icon(Icons.Rounded.Close, tr("关闭"), Modifier.size(18.dp)) }
                                        }
                                    }
                                }
                                if (webError != null) {
                                    Surface(
                                        shape = RoundedCornerShape(16.dp),
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            Text(
                                                "加载失败：$webError",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onErrorContainer,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f, fill = false),
                                            )
                                            Text(tr("重试"),
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.clickable {
                                                    webError = null
                                                    webViewRef?.reload()
                                                },
                                            )
                                        }
                                    }
                                } else if (!pageCommitted) {
                                    Surface(
                                        shape = RoundedCornerShape(18.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                                        tonalElevation = 2.dp,
                                        modifier = Modifier
                                            .align(Alignment.TopCenter)
                                            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                                    ) {
                                        Row(
                                            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                            Text(tr("正在加载 ") + runCatching { android.net.Uri.parse(activeUrl).host ?: activeUrl }.getOrElse { activeUrl },
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                                // 网页开的弹窗（第三方账号登录等）：盖在网页上面，登录完网页会自己关掉它
                                popupView?.let { pv ->
                                    Column(Modifier.fillMaxSize().zIndex(5f).background(surfaceColor)) {
                                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                                            verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = { popupView = null }) { Icon(Icons.Rounded.Close, tr("关闭")) }
                                            Text(popupTitle.ifBlank { pv.url.orEmpty() }, Modifier.weight(1f),
                                                style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        androidx.compose.runtime.key(pv) {
                                            AndroidView(factory = { pv }, modifier = Modifier.weight(1f).fillMaxWidth(),
                                                onRelease = { it.destroy() })
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2) 电脑实况层（覆盖在 WebView 上方，切换时瞬间显隐零闪烁）
                if (viewMode == "live") {
                    Column(Modifier.fillMaxSize().zIndex(3f).background(surfaceColor)) {
                // 「电脑实况」顶部控制条：触控操控开关 + 实时状态点
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TogglePill(
                        selected = controlMode != "view",
                        onClick = {
                            controlMode = when (controlMode) {
                                "mouse" -> "touch"
                                "touch" -> "view"
                                else -> "mouse"
                            }
                        },
                        leadingIcon = {
                            Icon(
                                if (controlMode == "mouse") Icons.Rounded.Mouse else Icons.Rounded.TouchApp,
                                null,
                                Modifier.size(16.dp),
                            )
                        },
                        label = {
                            Text(when (controlMode) { "mouse" -> tr("鼠标操控"); "touch" -> tr("直接触控"); else -> tr("仅查看") })
                        },
                    )
                    // 画质档位：流畅 / 平衡 / 清晰 / 极致清晰（切换后按新档位重连，并记住）
                    Box {
                        var qMenu by remember { mutableStateOf(false) }
                        val cur = vm.streamPresets.find { it.key == streamQ } ?: vm.streamPresets[1]
                        TogglePill(selected = false, onClick = { qMenu = true },
                            leadingIcon = { Icon(Icons.Rounded.HighQuality, null, Modifier.size(16.dp)) },
                            label = { Text(tr(cur.label)) })
                        DropdownMenu(qMenu, { qMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
                            vm.streamPresets.forEach { ps ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(tr(ps.label), style = MaterialTheme.typography.bodyLarge)
                                            Text(tr(ps.desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    },
                                    trailingIcon = { if (ps.key == streamQ) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                                    onClick = { qMenu = false; vm.prefs.streamQuality = ps.key; streamQ = ps.key },
                                )
                            }
                        }
                    }
                    if (controlMode == "mouse") {
                        TogglePill(
                            selected = showCursorSlider,
                            onClick = { showCursorSlider = !showCursorSlider },
                            label = { Text("${tr("光标")} ${String.format("%.1f", cursorScale)}×") },
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(start = 4.dp)) {
                        if (!livePaused && liveError == null) {
                            StatusDot(true, color = Green)
                            Text(if (activity.running) tr("运行中") else tr("实时同步"),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (livePaused) {
                            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outline, CircleShape))
                            Text(tr("已暂停"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

                // 实时画面区域：支持单击左键、长按右键、未放大时上下滑滚轮、双指捏合缩放（放大后点击依然精准映射像素）
                var boxSize by remember { mutableStateOf(IntSize.Zero) }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .onSizeChanged { boxSize = it },
                ) {
                    // 光标大小：浮在画面上方的小卡片，不挤占画面（改动立即生效并保存）
                    if (controlMode == "mouse" && showCursorSlider) {
                        Surface(
                            shape = RoundedCornerShape(20.dp), color = sheetCardColor(), shadowElevation = 6.dp,
                            modifier = Modifier.align(Alignment.TopCenter).zIndex(10f).padding(top = 10.dp, start = 16.dp, end = 16.dp).fillMaxWidth(),
                        ) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(tr("光标大小"), style = MaterialTheme.typography.labelLarge)
                                AppSlider(value = cursorScale, onValueChange = { cursorScale = it; vm.prefs.cursorScale = it },
                                    valueRange = 0.4f..2.2f, modifier = Modifier.weight(1f))
                                Text("${String.format("%.1f", cursorScale)}×", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick = { showCursorSlider = false }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Rounded.Close, tr("关闭"), Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                    val bmp = liveFrame
                    if (bmp != null) {
                        fun fitMetrics(): Pair<Float, Float>? {
                            val bw = boxSize.width.toFloat()
                            val bh = boxSize.height.toFloat()
                            if (bw <= 1f || bh <= 1f || bmp.width <= 0 || bmp.height <= 0) return null
                            val fit = minOf(bw / bmp.width, bh / bmp.height)
                            return (bmp.width * fit) to (bmp.height * fit)
                        }
                        fun toNorm(pos: Offset): Offset? {
                            val (imgW, imgH) = fitMetrics() ?: return null
                            val bw = boxSize.width.toFloat()
                            val bh = boxSize.height.toFloat()
                            val ux = (pos.x - bw / 2f - offset.x) / scale + bw / 2f
                            val uy = (pos.y - bh / 2f - offset.y) / scale + bh / 2f
                            val nx = (ux - (bw - imgW) / 2f) / imgW
                            val ny = (uy - (bh - imgH) / 2f) / imgH
                            return if (nx in 0f..1f && ny in 0f..1f) Offset(nx, ny) else null
                        }
                        fun normToScreen(n: Offset): Offset? {
                            val (imgW, imgH) = fitMetrics() ?: return null
                            val bw = boxSize.width.toFloat()
                            val bh = boxSize.height.toFloat()
                            val ux = (bw - imgW) / 2f + n.x * imgW
                            val uy = (bh - imgH) / 2f + n.y * imgH
                            return Offset((ux - bw / 2f) * scale + bw / 2f + offset.x, (uy - bh / 2f) * scale + bh / 2f + offset.y)
                        }

                        var lastQuickTapAt by remember { mutableStateOf(0L) }
                        Box(
                            Modifier
                                .fillMaxSize()
                                .then(
                                    if (controlMode == "mouse") {
                                        // 完整虚拟鼠标触控板手势：
                                        // 单指滑动=移动鼠标；轻点=左键；双击并按住=按住左键拖拽；快速双击=双击；长按=右键；双指=缩放平移
                                        Modifier.pointerInput(captureCrop, bmp.width, bmp.height) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                val downTime = System.currentTimeMillis()
                                                val downPos = down.position
                                                val isSecondTap = (downTime - lastQuickTapAt) <= 320L
                                                var moved = false
                                                var multi = false
                                                var leftHeld = false
                                                var rightFired = false
                                                var prevDist = 0f
                                                var prevCent = Offset.Zero

                                                // 长按计时（非双击第二次按下时，450ms 触发右键；双击第二次按下时，150ms 自动进入按住左键）
                                                val holdJob = scope.launch {
                                                    if (isSecondTap) {
                                                        delay(150)
                                                        if (!multi && !leftHeld) {
                                                            leftHeld = true
                                                            leftDragging = true
                                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                            vm.sendBrowserInput("mousedown", captureCrop, cursorNorm.x, cursorNorm.y)
                                                        }
                                                    } else {
                                                        delay(450)
                                                        if (!moved && !multi && !rightFired) {
                                                            rightFired = true
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            vm.sendBrowserInput("rightclick", captureCrop, cursorNorm.x, cursorNorm.y) { refreshSoon() }
                                                        }
                                                    }
                                                }

                                                try {
                                                    while (true) {
                                                        val ev = awaitPointerEvent()
                                                        val pressed = ev.changes.filter { it.pressed }
                                                        if (pressed.isEmpty()) break

                                                        if (pressed.size >= 2) {
                                                            if (!multi) {
                                                                multi = true
                                                                holdJob.cancel()
                                                                if (leftHeld) {
                                                                    leftHeld = false
                                                                    leftDragging = false
                                                                    vm.sendBrowserInput("mouseup", captureCrop, cursorNorm.x, cursorNorm.y)
                                                                }
                                                                val p0 = pressed[0].position
                                                                val p1 = pressed[1].position
                                                                prevDist = (p0 - p1).getDistance().coerceAtLeast(1f)
                                                                prevCent = (p0 + p1) / 2f
                                                            } else {
                                                                val p0 = pressed[0].position
                                                                val p1 = pressed[1].position
                                                                val dist = (p0 - p1).getDistance().coerceAtLeast(1f)
                                                                val cent = (p0 + p1) / 2f
                                                                val zoom = dist / prevDist
                                                                val pan = cent - prevCent
                                                                scale = (scale * zoom).coerceIn(1f, 5f)
                                                                offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                                                                prevDist = dist
                                                                prevCent = cent
                                                            }
                                                            ev.changes.forEach { it.consume() }
                                                            continue
                                                        }

                                                        if (multi) {
                                                            ev.changes.forEach { it.consume() }
                                                            continue
                                                        }

                                                        val ch = pressed[0]
                                                        val delta = ch.positionChange()
                                                        if (delta != Offset.Zero) {
                                                            if (!moved && (ch.position - downPos).getDistance() > 10f) {
                                                                moved = true
                                                                if (!isSecondTap) holdJob.cancel()
                                                                if (isSecondTap && !leftHeld) {
                                                                    holdJob.cancel()
                                                                    leftHeld = true
                                                                    leftDragging = true
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    vm.sendBrowserInput("mousedown", captureCrop, cursorNorm.x, cursorNorm.y)
                                                                }
                                                            }
                                                            if (moved && !rightFired) {
                                                                val (imgW, imgH) = fitMetrics() ?: continue
                                                                val sens = 1.35f
                                                                val next = Offset(
                                                                    (cursorNorm.x + delta.x / (imgW * scale) * sens).coerceIn(0f, 1f),
                                                                    (cursorNorm.y + delta.y / (imgH * scale) * sens).coerceIn(0f, 1f),
                                                                )
                                                                cursorNorm = next
                                                                val now = System.currentTimeMillis()
                                                                if (now - lastMoveSentAt >= 40L) {
                                                                    lastMoveSentAt = now
                                                                    vm.sendBrowserInput("move", captureCrop, next.x, next.y)
                                                                }
                                                                ch.consume()
                                                            }
                                                        }
                                                    }
                                                } finally {
                                                    holdJob.cancel()
                                                }

                                                if (leftHeld) {
                                                    leftDragging = false
                                                    lastQuickTapAt = 0L
                                                    vm.sendBrowserInput("mouseup", captureCrop, cursorNorm.x, cursorNorm.y) { refreshSoon() }
                                                } else if (!multi && !rightFired) {
                                                    if (moved) {
                                                        // 拖动结束补发最终坐标，确保电脑鼠标和虚拟箭头完全重合
                                                        lastQuickTapAt = 0L
                                                        vm.sendBrowserInput("move", captureCrop, cursorNorm.x, cursorNorm.y)
                                                    } else if (isSecondTap) {
                                                        // 快速双击（第二次没按住也没拖动）→ 发送双击
                                                        lastQuickTapAt = 0L
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        vm.sendBrowserInput("dblclick", captureCrop, cursorNorm.x, cursorNorm.y) { refreshSoon() }
                                                    } else {
                                                        // 普通轻点 → 左键单击，并记录时间以便紧接着“双击并按住”进入拖拽
                                                        lastQuickTapAt = System.currentTimeMillis()
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        vm.sendBrowserInput("click", captureCrop, cursorNorm.x, cursorNorm.y) { refreshSoon() }
                                                    }
                                                }
                                            }
                                        }
                                    } else if (controlMode == "touch") {
                                        // 真正 Windows 触摸屏注入模式（InjectTouchInput）：支持原生触摸点击、平滑触摸滚动与拖拽，不强行搬移鼠标光标；双指捏合缩放平移
                                        Modifier.pointerInput(captureCrop, bmp.width, bmp.height) {
                                            awaitEachGesture {
                                                val down = awaitFirstDown(requireUnconsumed = false)
                                                var multi = false
                                                var touchStarted = false
                                                var lastNorm = toNorm(down.position)
                                                var lastTouchSentAt = 0L
                                                var prevDist = 0f
                                                var prevCent = Offset.Zero

                                                if (lastNorm != null) {
                                                    touchStarted = true
                                                    cursorNorm = lastNorm
                                                    vm.sendBrowserInput("touchdown", captureCrop, lastNorm.x, lastNorm.y)
                                                }

                                                while (true) {
                                                    val ev = awaitPointerEvent()
                                                    val pressed = ev.changes.filter { it.pressed }
                                                    if (pressed.isEmpty()) break

                                                    if (pressed.size >= 2) {
                                                        if (!multi) {
                                                            multi = true
                                                            if (touchStarted) {
                                                                touchStarted = false
                                                                val n = lastNorm ?: cursorNorm
                                                                vm.sendBrowserInput("touchcancel", captureCrop, n.x, n.y)
                                                            }
                                                            val p0 = pressed[0].position
                                                            val p1 = pressed[1].position
                                                            prevDist = (p0 - p1).getDistance().coerceAtLeast(1f)
                                                            prevCent = (p0 + p1) / 2f
                                                        } else {
                                                            val p0 = pressed[0].position
                                                            val p1 = pressed[1].position
                                                            val dist = (p0 - p1).getDistance().coerceAtLeast(1f)
                                                            val cent = (p0 + p1) / 2f
                                                            val zoom = dist / prevDist
                                                            val pan = cent - prevCent
                                                            scale = (scale * zoom).coerceIn(1f, 5f)
                                                            offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                                                            prevDist = dist
                                                            prevCent = cent
                                                        }
                                                        ev.changes.forEach { it.consume() }
                                                        continue
                                                    }

                                                    if (multi) {
                                                        ev.changes.forEach { it.consume() }
                                                        continue
                                                    }

                                                    val ch = pressed[0]
                                                    val n = toNorm(ch.position)
                                                    if (n != null && touchStarted) {
                                                        lastNorm = n
                                                        cursorNorm = n
                                                        val now = System.currentTimeMillis()
                                                        if (now - lastTouchSentAt >= 32L) {
                                                            lastTouchSentAt = now
                                                            vm.sendBrowserInput("touchmove", captureCrop, n.x, n.y)
                                                        }
                                                        ch.consume()
                                                    }
                                                }

                                                if (touchStarted) {
                                                    val n = lastNorm ?: cursorNorm
                                                    vm.sendBrowserInput("touchup", captureCrop, n.x, n.y) { refreshSoon() }
                                                }
                                            }
                                        }
                                    } else {
                                        // 仅查看模式：纯双指缩放与双击放大还原
                                        Modifier
                                            .pointerInput(captureCrop, bmp.width, bmp.height) {
                                                detectTransformGestures { _, pan, zoom, _ ->
                                                    scale = (scale * zoom).coerceIn(1f, 5f)
                                                    offset = if (scale <= 1.01f) Offset.Zero else offset + pan
                                                }
                                            }
                                            .pointerInput(captureCrop, bmp.width, bmp.height) {
                                                detectTapGestures(onDoubleTap = {
                                                    if (scale > 1.05f) { scale = 1f; offset = Offset.Zero } else scale = 2.2f
                                                })
                                            }
                                    },
                                ),
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "电脑实时画面",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
                            )
                            // 鼠标模式下直接绘制电脑端实时获取的真实 Windows 光标样式（按热点精准对齐，无多余触摸高亮）
                            if (controlMode == "mouse") {
                                normToScreen(cursorNorm)?.let { tip ->
                                    val cur = pcCursor
                                    if (cur != null) {
                                        val imgBmp = remember(cur.id, cur.bmp) { cur.bmp.asImageBitmap() }
                                        val imgW = fitMetrics()?.first ?: 1080f
                                        Canvas(Modifier.fillMaxSize()) {
                                            // 按画面实际比例计算自然基准尺寸，再乘以用户可调倍率 cursorScale（默认 1.0×，精致不挡内容）
                                            val base = ((imgW / 1400f) * scale).coerceIn(0.65f, 1.45f)
                                            val k = (base * cursorScale).coerceIn(0.35f, 3.0f)
                                            val dstW = (cur.w * k).roundToInt().coerceAtLeast(1)
                                            val dstH = (cur.h * k).roundToInt().coerceAtLeast(1)
                                            val left = (tip.x - cur.xHot * k).roundToInt()
                                            val top = (tip.y - cur.yHot * k).roundToInt()
                                            drawImage(
                                                image = imgBmp,
                                                dstOffset = IntOffset(left, top),
                                                dstSize = IntSize(dstW, dstH),
                                            )
                                        }
                                    } else {
                                        Canvas(Modifier.fillMaxSize()) {
                                            val s = (0.65f * cursorScale).coerceIn(0.3f, 2.0f)
                                            val p = Path().apply {
                                                moveTo(tip.x, tip.y)
                                                lineTo(tip.x, tip.y + 42f * s)
                                                lineTo(tip.x + 11f * s, tip.y + 32f * s)
                                                lineTo(tip.x + 20f * s, tip.y + 48f * s)
                                                lineTo(tip.x + 27f * s, tip.y + 44f * s)
                                                lineTo(tip.x + 18f * s, tip.y + 28f * s)
                                                lineTo(tip.x + 31f * s, tip.y + 27f * s)
                                                close()
                                            }
                                            drawPath(p, Color.White, style = Stroke(width = 4.5f * s))
                                            drawPath(p, Color(0xFF111827), style = Fill)
                                        }
                                    }
                                }
                                if (leftDragging) {
                                    Surface(
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
                                    ) {
                                        Text(tr("左键按住拖拽中（松手释放）"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        )
                                    }
                                }
                            }
                        }
                    } else if (liveError != null) {
                        Column(
                            Modifier.align(Alignment.Center).padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Icon(Icons.Rounded.Computer, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.outline)
                            Text(liveError ?: "", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(tr("电脑上打开 Antigravity 窗口后这里会自动出画面；你也可以点上方「网页浏览」直接走电脑网络看网页。"),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp)
                        }
                    }
                }

                // 远程文字编辑面板：自动读取电脑当前输入框的已有文字，可自由增删改后再替换写回电脑（或追加/实时发按键）
                if (showRemoteKeyboard) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        // 顶部：读取电脑已有文字 + 实时光标/编辑快捷键
                        Row(
                            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ToolPill(
                                onClick = { loadPcField() },
                                leadingIcon = {
                                    if (readingField) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.8.dp)
                                    else Icon(Icons.Rounded.Sync, null, Modifier.size(15.dp))
                                },
                                label = { Text(tr("读取电脑已有文字")) },
                            )
                            ToolPill(onClick = { vm.sendBrowserInput("key", captureCrop, key = "left") { refreshSoon() } }, label = { Text("←") })
                            ToolPill(onClick = { vm.sendBrowserInput("key", captureCrop, key = "right") { refreshSoon() } }, label = { Text("→") })
                            ToolPill(onClick = { vm.sendBrowserInput("key", captureCrop, key = "backspace") { refreshSoon() } }, label = { Text("⌫") })
                            ToolPill(onClick = { vm.sendBrowserInput("key", captureCrop, key = "del") { refreshSoon() } }, label = { Text("Del") })
                            ToolPill(onClick = { vm.sendBrowserInput("key", captureCrop, key = "ctrl+a") { refreshSoon() } }, label = { Text(tr("全选")) })
                            ToolPill(onClick = { vm.sendBrowserInput("key", captureCrop, key = "ctrl+z") { refreshSoon() } }, label = { Text(tr("撤销")) })
                        }
                        // 可多行自由移动光标编辑的输入框
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = sheetCardColor(),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp, max = 110.dp),
                        ) {
                            Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), contentAlignment = Alignment.CenterStart) {
                                if (remoteTv.text.isEmpty()) {
                                    Text(tr("先点电脑输入框再点上方「读取电脑已有文字」即可修改原文，或直接打字…"),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                                BasicTextField(
                                    value = remoteTv,
                                    onValueChange = { remoteTv = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                )
                            }
                        }
                        // 底部操作：替换电脑文字 / 替换并回车 / 仅追加
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = {
                                    vm.sendBrowserInput("replacefield", captureCrop, text = remoteTv.text) { refreshSoon() }
                                },
                                modifier = Modifier.weight(1f).height(40.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp),
                            ) { Text(tr("替换写入"), style = MaterialTheme.typography.labelLarge) }
                            OutlinedButton(
                                onClick = {
                                    vm.sendBrowserInput("replacefield", captureCrop, text = remoteTv.text) {
                                        vm.sendBrowserInput("key", captureCrop, key = "enter") { refreshSoon() }
                                    }
                                },
                                modifier = Modifier.height(36.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            ) { Text(tr("写入+回车"), style = MaterialTheme.typography.labelLarge) }
                            OutlinedButton(
                                onClick = {
                                    val txt = remoteTv.text
                                    if (txt.isNotEmpty()) {
                                        remoteTv = TextFieldValue("")
                                        vm.sendBrowserInput("text", captureCrop, text = txt) { refreshSoon() }
                                    }
                                },
                                enabled = remoteTv.text.isNotEmpty(),
                                modifier = Modifier.height(36.dp),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                            ) { Text(tr("追加"), style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }

                // 底部快捷操控条（鼠标左/右/双击、输入文字、滚轮上/下、回车、退格、Esc、复原缩放）
                // 底部操控条始终保留、高度固定：切到“仅查看”时只换成提示，画面不会被挤动
                run {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Row(
                        Modifier.fillMaxWidth().height(52.dp).background(MaterialTheme.colorScheme.surface)
                            .horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (controlMode == "view") Text(tr("仅查看：不会操控电脑"), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
                        else {
                        if (controlMode == "mouse") {
                            ToolPill(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    vm.sendBrowserInput("click", captureCrop, cursorNorm.x, cursorNorm.y) { refreshSoon() }
                                },
                                label = { Text(tr("左键")) },
                            )
                            ToolPill(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    vm.sendBrowserInput("rightclick", captureCrop, cursorNorm.x, cursorNorm.y) { refreshSoon() }
                                },
                                label = { Text(tr("右键")) },
                            )
                            ToolPill(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    vm.sendBrowserInput("dblclick", captureCrop, cursorNorm.x, cursorNorm.y) { refreshSoon() }
                                },
                                label = { Text(tr("双击")) },
                            )
                        }
                        ToolPill(
                            onClick = {
                                val next = !showRemoteKeyboard
                                showRemoteKeyboard = next
                                if (next) loadPcField()
                            },
                            leadingIcon = { Icon(Icons.Rounded.Keyboard, null, Modifier.size(16.dp)) },
                            label = { Text(if (showRemoteKeyboard) tr("收起键盘") else tr("键盘编辑")) },
                        )
                        ToolPill(
                            onClick = { vm.sendBrowserInput("wheel", captureCrop, cursorNorm.x, cursorNorm.y, delta = 360) { refreshSoon() } },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowUp, null, Modifier.size(16.dp)) },
                            label = { Text(tr("上滚")) },
                        )
                        ToolPill(
                            onClick = { vm.sendBrowserInput("wheel", captureCrop, cursorNorm.x, cursorNorm.y, delta = -360) { refreshSoon() } },
                            leadingIcon = { Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(16.dp)) },
                            label = { Text(tr("下滚")) },
                        )
                        ToolPill(
                            onClick = { vm.sendBrowserInput("key", captureCrop, key = "enter") { refreshSoon() } },
                            label = { Text(tr("回车")) },
                        )
                        ToolPill(
                            onClick = { vm.sendBrowserInput("key", captureCrop, key = "backspace") { refreshSoon() } },
                            label = { Text(tr("退格")) },
                        )
                        ToolPill(
                            onClick = { vm.sendBrowserInput("key", captureCrop, key = "esc") { refreshSoon() } },
                            label = { Text("Esc") },
                        )
                        if (scale > 1.05f) {
                            ToolPill(
                                onClick = { scale = 1f; offset = Offset.Zero },
                                label = { Text("1× ${tr("复原")}") },
                            )
                        }
                        }
                    }
                }

                // 底部紧凑条：Gemini 浏览器动态（如果有 browser_subagent / 网页操作步骤）
                if (activity.actions.isNotEmpty() || activity.screenshots.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Column(
                        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
                            .heightIn(max = 170.dp).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(tr("Gemini 浏览器动态"), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (activity.running) StatusDot(true)
                        }
                        activity.actions.take(5).forEach { act ->
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(act.title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                                Text(act.text.ifBlank { act.sub }, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) sheetCardColor() else androidx.compose.ui.graphics.Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        modifier = Modifier.fillMaxHeight().clip(RoundedCornerShape(50)).clickable(onClick = onClick),
    ) {
        Box(Modifier.padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, softWrap = false)
        }
    }
}

/** 开关型胶囊（实况顶部的操控方式、光标大小）：选中是浅灰底，没选中是白底细边框。 */
@Composable
private fun TogglePill(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, leadingIcon: (@Composable () -> Unit)? = null) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else sheetCardColor(),
        contentColor = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.height(36.dp),
    ) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (leadingIcon != null) { leadingIcon(); Spacer(Modifier.width(6.dp)) }
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge) { label() }
        }
    }
}

/** 操作胶囊（左键、右键、滚动、回车…）：白底细边框。 */
@Composable
private fun ToolPill(onClick: () -> Unit, label: @Composable () -> Unit, leadingIcon: (@Composable () -> Unit)? = null) =
    TogglePill(selected = false, onClick = onClick, label = label, leadingIcon = leadingIcon)

/** 菜单最上面那排圆形按钮。 */
@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.size(46.dp)) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, label, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.35f))
        }
    }
}

/** 标签页切换页：上面“新建标签页”，下面两列卡片（标题 + 关闭 + 页面缩略图），当前标签页边框加粗。 */
@Composable
private fun ColumnScope.TabSwitcher(
    tabs: List<BrowserTab>, activeId: String, thumbs: Map<String, Bitmap>,
    onNew: () -> Unit, onSelect: (BrowserTab) -> Unit, onClose: (BrowserTab) -> Unit, onDismiss: () -> Unit,
) {
    Column(Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onNew, modifier = Modifier.height(42.dp), contentPadding = PaddingValues(start = 14.dp, end = 18.dp)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("新建标签页"))
            }
            Spacer(Modifier.weight(1f))
            Text(tr("{0} 个标签页", tabs.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, tr("关闭")) }
        }
        if (tabs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(tr("还没有标签页"), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(tabs.size, key = { tabs[it].id }) { i ->
                val tab = tabs[i]
                val cur = tab.id == activeId
                Surface(onClick = { onSelect(tab) }, shape = RoundedCornerShape(20.dp), color = sheetCardColor(),
                    border = BorderStroke(if (cur) 2.dp else 1.dp, if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) {
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Language, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(tab.title.ifBlank { tab.url }, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { onClose(tab) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, tr("关闭标签页"), Modifier.size(16.dp))
                            }
                        }
                        Box(Modifier.fillMaxWidth().height(190.dp).padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                            .clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow),
                            contentAlignment = Alignment.Center) {
                            val th = thumbs[tab.id]
                            if (th != null) Image(th.asImageBitmap(), null, Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter)
                            else Text(runCatching { android.net.Uri.parse(tab.url).host ?: tab.url }.getOrElse { tab.url },
                                Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SyncedTabsRow(
    tabs: List<BrowserTab>,
    activeTabId: String,
    activeUrl: String,
    pcHost: String,
    onSelect: (BrowserTab) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { t ->
            val selected = (activeTabId.isNotBlank() && t.id == activeTabId) ||
                (activeUrl.isNotBlank() && normalizeUrl(t.url, pcHost) == activeUrl)
            val fav = remember(t.favicon) { decodeFavicon(t.favicon) }
            Surface(
                shape = RoundedCornerShape(50),
                color = if (selected) MaterialTheme.colorScheme.secondaryContainer else sheetCardColor(),
                border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.widthIn(max = 220.dp).clip(RoundedCornerShape(50)).clickable { onSelect(t) },
            ) {
                Row(
                    Modifier.padding(start = 12.dp, end = 6.dp, top = 7.dp, bottom = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (fav != null) {
                        Image(fav.asImageBitmap(), null, Modifier.size(14.dp))
                    } else {
                        Icon(Icons.Rounded.Computer, null, Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(6.dp))
                    Text(
                        t.title.ifBlank { t.url },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .clickable { onCloseTab(t) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            "关闭标签页",
                            Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun BrowserSyncHome(
    pcTabs: List<BrowserTab>,
    otherPcTabs: List<BrowserTab>,
    quickLinks: List<String>,
    syncing: Boolean,
    onOpen: (String) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
    onRemoveLink: (String) -> Unit = {},
    onCopyLink: (String) -> Unit = {},
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CardBox {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Computer, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr("走电脑网络浏览"), style = MaterialTheme.typography.bodyLarge)
                    Text(tr("在上方输入网址或搜索；右上角“⋯”可以把电脑上打开的网页复制过来"),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (syncing) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        if (otherPcTabs.isNotEmpty()) {
            SectionLabel(tr("电脑上已打开的标签页"))
            CardGroup {
                otherPcTabs.forEachIndexed { i, t ->
                    if (i > 0) CardDivider()
                    val fav = remember(t.favicon) { decodeFavicon(t.favicon) }
                    Row(Modifier.fillMaxWidth().clickable { if (t.url.isNotBlank()) onOpen(t.url) }
                        .padding(start = 16.dp, end = 6.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (fav != null) Image(fav.asImageBitmap(), null, Modifier.size(22.dp))
                        else Icon(Icons.Rounded.Language, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t.title.ifBlank { t.url }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(t.convTitle.takeIf { it.isNotBlank() }, t.url).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { onCloseTab(t) }) {
                            Icon(Icons.Rounded.Close, tr("关闭标签页"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }

        if (quickLinks.isNotEmpty()) {
            SectionLabel(tr("当前对话中的链接"))
            CardGroup {
                quickLinks.take(8).forEachIndexed { i, u ->
                    if (i > 0) CardDivider()
                    // 点开；长按弹出“复制链接 / 删除”
                    var menu by remember(u) { mutableStateOf(false) }
                    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                    Box {
                        Row(Modifier.fillMaxWidth()
                            .combinedClickable(onClick = { onOpen(u) }, onLongClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress); menu = true
                            })
                            .padding(horizontal = 16.dp, vertical = 13.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Link, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(14.dp))
                            Text(u, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
                            DropdownMenuItem(text = { Text(tr("复制链接")) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                onClick = { menu = false; onCopyLink(u) })
                            DropdownMenuItem(text = { Text(tr("删除"), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { menu = false; onRemoveLink(u) })
                        }
                    }
                }
            }
        }
    }
}

/** 网页里点的“在 App 中打开”链接：交给手机上对应的 App；没装就走网页提供的备用网址，再不行提示。 */
private fun openAppLink(ctx: android.content.Context, url: String, onFallback: (String) -> Unit) {
    val intent = runCatching {
        if (url.startsWith("intent:")) android.content.Intent.parseUri(url, android.content.Intent.URI_INTENT_SCHEME)
        else android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
    }.getOrNull() ?: return
    // 安全：网页不能指定具体组件，只能像普通链接一样交给系统选 App
    intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
    intent.component = null
    intent.selector = null
    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(intent)
    } catch (_: android.content.ActivityNotFoundException) {
        val fb = intent.getStringExtra("browser_fallback_url")
        if (fb != null && (fb.startsWith("https://") || fb.startsWith("http://"))) onFallback(fb)
        else android.widget.Toast.makeText(ctx, I18n.t("没有可以打开这个链接的应用"), android.widget.Toast.LENGTH_SHORT).show()
    } catch (_: SecurityException) {
        android.widget.Toast.makeText(ctx, I18n.t("没有可以打开这个链接的应用"), android.widget.Toast.LENGTH_SHORT).show()
    }
}

/** 取出页面上要翻译的文字（跳过脚本、样式、代码），节点存在 window.__agr 里；已翻过的取原文。返回 JSON 数组。 */
private const val PAGE_TEXT_JS = """(function(){var w=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT,{acceptNode:function(n){var p=n.parentElement;if(!p)return 2;var t=p.tagName;if(t==='SCRIPT'||t==='STYLE'||t==='NOSCRIPT'||t==='CODE'||t==='PRE'||t==='TEXTAREA')return 2;var v=n.__o!=null?n.__o:n.nodeValue;if(!v||v.trim().length<2)return 2;return 1;}});var a=[],n;window.__agr=[];while((n=w.nextNode())&&a.length<3000){window.__agr.push(n);a.push((n.__o!=null?n.__o:n.nodeValue).trim());}return JSON.stringify(a);})()"""

/** 按“原文 → 译文”换上译文（原文存在节点的 __o 上，保留前后空白）。 */
private const val APPLY_TEXT_JS = """function(m){var ns=window.__agr||[];for(var i=0;i<ns.length;i++){var n=ns[i];var v=n.__o!=null?n.__o:n.nodeValue;var tr=m[v.trim()];if(!tr)continue;if(n.__o==null)n.__o=v;var a=v.match(/^\s*/)[0],b=v.match(/\s*$/)[0];n.nodeValue=a+tr+b;}}"""

/** 换回原文。 */
private const val RESTORE_TEXT_JS = """(function(){var ns=window.__agr||[];for(var i=0;i<ns.length;i++){var n=ns[i];if(n.__o!=null){n.nodeValue=n.__o;n.__o=null;}}})()"""

/** 网页声明的语言 + 一段正文，用来判断要不要问“翻译网页吗？”。 */
private const val LANG_SAMPLE_JS = """(function(){var b=document.body;return JSON.stringify([document.documentElement.lang||'',(b&&b.innerText||'').slice(0,2500)]);})()"""

/**
 * 网页的大部分文字是不是“不是当前界面语言”：按文字的书写系统数（汉字、假名、韩文、西里尔字母、拉丁字母），
 * 界面语言用的那种文字不到一半才算外语；都是拉丁字母（比如英文界面看法语页面）时再看网页自己声明的语言。
 * 返回 (是否外语, 猜的语言代码)。
 */
private fun mostlyForeign(sample: String, declared: String, app: String): Pair<Boolean, String> {
    var han = 0; var kana = 0; var hangul = 0; var cyr = 0; var latin = 0
    for (ch in sample) {
        when {
            ch in '\u4e00'..'\u9fff' -> han++
            ch in '\u3040'..'\u30ff' -> kana++
            ch in '\uac00'..'\ud7af' -> hangul++
            ch in '\u0400'..'\u04ff' -> cyr++
            ch.isLetter() && ch.code < 0x250 -> latin++
        }
    }
    val total = han + kana + hangul + cyr + latin
    if (total < 60) return false to "" // 字太少，不问
    val guessed = when (maxOf(han, kana, hangul, cyr, latin)) {
        hangul -> "ko"; cyr -> "ru"; latin -> "en"
        else -> if (kana > total / 10) "ja" else "zh"
    }
    val appBase = app.lowercase().substringBefore('-')
    val mine = when (appBase) {
        "zh" -> if (kana > total / 10) 0 else han          // 假名多就是日文
        "ja" -> han + kana
        "ko" -> hangul
        "ru" -> cyr
        else -> latin
    }
    if (mine * 2 >= total) {
        // 主要文字和界面语言同一种书写系统：只有拉丁字母的语言之间才需要看网页声明的语言
        val latinApp = appBase !in setOf("zh", "ja", "ko", "ru")
        return (latinApp && declared.isNotBlank() && declared != appBase) to declared
    }
    return true to guessed
}
