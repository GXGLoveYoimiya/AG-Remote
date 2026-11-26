package com.local.agremote.ui

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
private fun normalizeUrl(raw: String, pcHost: String): String {
    val s = raw.trim()
    if (s.isEmpty()) return ""
    val withScheme = when {
        s.startsWith("http://", true) || s.startsWith("https://", true) -> s
        s.contains(' ') || (!s.contains('.') && !s.startsWith("localhost", true)) ->
            "https://www.google.com/search?q=" + java.net.URLEncoder.encode(s, "UTF-8")
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
    val pcData by vm.pcBrowserTabs.collectAsState()
    val pcLoading by vm.pcBrowserLoading.collectAsState()
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceArgb = surfaceColor.toArgb()
    val isDark = surfaceColor.luminance() < 0.5f

    // explicitUrl 非空（点链接进来）→ 默认网页浏览；点右上角图标进来 → 默认电脑实况
    var viewMode by remember(explicitUrl) { mutableStateOf(if (explicitUrl != null) "web" else "live") }
    var webEverMounted by remember(explicitUrl) { mutableStateOf(explicitUrl != null) }
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

    var activeUrl by remember { mutableStateOf(explicitUrl?.let { normalizeUrl(it, vm.prefs.host) } ?: "") }
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
    var localTabs by remember {
        mutableStateOf(
            if (activeUrl.isNotBlank()) listOf(
                BrowserTab(
                    id = "local-1",
                    url = activeUrl,
                    title = runCatching { android.net.Uri.parse(activeUrl).host ?: activeUrl }.getOrElse { activeUrl },
                    favicon = "",
                    cid = chatId,
                    convTitle = "",
                )
            ) else emptyList()
        )
    }
    var activeTabId by remember { mutableStateOf(if (activeUrl.isNotBlank()) "local-1" else "") }

    val pcAllTabs = remember(pcData) { pcData.tabs + pcData.otherTabs }
    val allSyncedTabs = remember(pcAllTabs, localTabs, vm.prefs.host) {
        val pcUrls = pcAllTabs.map { normalizeUrl(it.url, vm.prefs.host) }.toSet()
        pcAllTabs + localTabs.filter { normalizeUrl(it.url, vm.prefs.host) !in pcUrls }
    }

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
        val closingCurrent = (activeTabId.isNotBlank() && t.id == activeTabId) ||
            (activeUrl.isNotBlank() && normalizeUrl(t.url, vm.prefs.host) == activeUrl)
        if (t.id.startsWith("local-")) {
            val nextLocal = localTabs.filterNot { it.id == t.id }
            localTabs = nextLocal
            val pcUrls = pcAllTabs.map { normalizeUrl(it.url, vm.prefs.host) }.toSet()
            val remaining = (pcAllTabs + nextLocal.filter { normalizeUrl(it.url, vm.prefs.host) !in pcUrls })
                .filter { it.url.isNotBlank() }
            if (remaining.isEmpty()) {
                clearToHome()
            } else if (closingCurrent) {
                val nextTab = remaining.first()
                activeTabId = nextTab.id
                if (viewMode == "web") {
                    openUrl(nextTab.url, tabId = nextTab.id)
                } else {
                    activeUrl = normalizeUrl(nextTab.url, vm.prefs.host)
                    urlInput = nextTab.url
                    webViewRef?.loadUrl(activeUrl)
                }
            }
            return
        }
        vm.closeBrowserTab(t, chatId) { updated ->
            val pcNext = updated.tabs + updated.otherTabs
            val pcUrls = pcNext.map { normalizeUrl(it.url, vm.prefs.host) }.toSet()
            val remaining = (pcNext + localTabs.filter { normalizeUrl(it.url, vm.prefs.host) !in pcUrls })
                .filter { it.url.isNotBlank() }
            if (remaining.isEmpty()) {
                clearToHome()
            } else if (closingCurrent) {
                val nextTab = remaining.first()
                activeTabId = nextTab.id
                if (viewMode == "web") {
                    openUrl(nextTab.url, tabId = nextTab.id)
                } else {
                    activeUrl = normalizeUrl(nextTab.url, vm.prefs.host)
                    urlInput = nextTab.url
                    webViewRef?.loadUrl(activeUrl)
                }
            }
        }
    }

    // 打开期间每 4 秒同步一次电脑标签页
    LaunchedEffect(chatId) {
        vm.refreshBrowserTabs(chatId)
        while (true) {
            delay(4000)
            vm.refreshBrowserTabs(chatId)
        }
    }

    val curPcTab = remember(pcData) {
        pcData.tabs.find { it.id == pcData.activeTabId } ?: pcData.tabs.firstOrNull()
    }
    LaunchedEffect(curPcTab?.url) {
        val pcUrl = curPcTab?.url?.trim().orEmpty()
        if (pcUrl.isNotBlank() && pcUrl != lastSyncedPcUrl) {
            lastSyncedPcUrl = pcUrl
            if (!userNavigated || activeUrl.isBlank()) {
                activeTabId = curPcTab?.id.orEmpty()
                urlInput = pcUrl
                activeUrl = normalizeUrl(pcUrl, vm.prefs.host)
            }
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = true),
    ) {
        BackHandler {
            val wv = webViewRef
            val pv = popupView
            when {
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
                Box(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { topMenu = true }) { Icon(Icons.Rounded.MoreHoriz, tr("更多操作")) }
                    DropdownMenu(topMenu, { topMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
                        if (viewMode == "live") {
                            DropdownMenuItem(text = { Text(if (livePaused) tr("继续实时同步") else tr("暂停同步")) },
                                leadingIcon = { Icon(if (livePaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null) },
                                onClick = { topMenu = false; livePaused = !livePaused })
                            DropdownMenuItem(text = { Text(tr("刷新画面")) }, leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                onClick = { topMenu = false; liveTick++ })
                        } else {
                            if (activeUrl.isNotBlank()) {
                                DropdownMenuItem(text = { Text(if (loading) tr("停止加载") else tr("刷新")) },
                                    leadingIcon = { Icon(if (loading) Icons.Rounded.Close else Icons.Rounded.Refresh, null) },
                                    onClick = { topMenu = false; if (loading) webViewRef?.stopLoading() else webViewRef?.reload() })
                                DropdownMenuItem(text = { Text(tr("复制网址")) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                                    onClick = { topMenu = false; vm.copy(activeUrl, "已复制网址") })
                                DropdownMenuItem(text = { Text(tr("在外部浏览器打开")) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                    onClick = { topMenu = false; openWebLink(ctx, activeUrl) })
                            }
                            DropdownMenuItem(text = { Text(tr("同步电脑标签页")) }, leadingIcon = { Icon(Icons.Rounded.Sync, null) },
                                onClick = { topMenu = false; vm.refreshBrowserTabs(chatId); vm.showToast("已同步电脑标签页") })
                        }
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
                        Row(
                            Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 2.dp, bottom = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(onClick = { webViewRef?.goBack() }, enabled = canBack, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("后退"), Modifier.size(20.dp))
                            }
                            IconButton(onClick = { webViewRef?.goForward() }, enabled = canForward, modifier = Modifier.size(40.dp)) {
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, tr("前进"), Modifier.size(20.dp))
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
                        }

                        if (allSyncedTabs.isNotEmpty()) {
                            SyncedTabsRow(
                                tabs = allSyncedTabs,
                                activeTabId = activeTabId,
                                activeUrl = activeUrl,
                                pcHost = vm.prefs.host,
                                onSelect = { t -> if (t.url.isNotBlank()) openUrl(t.url, tabId = t.id) },
                                onCloseTab = ::handleCloseTab,
                            )
                        }

                        if (activeUrl.isNotBlank() && loading && progress in 1..99) {
                            LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(2.dp))
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }

                        Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().background(surfaceColor)) {
                            if (activeUrl.isBlank()) {
                                BrowserSyncHome(
                                    pcTabs = pcData.tabs,
                                    otherPcTabs = pcData.otherTabs,
                                    quickLinks = quickLinks,
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

                // 同步标签栏：点一下即可切到「网页浏览」互动打开该页面，点 × 与电脑同步关闭
                if (allSyncedTabs.isNotEmpty()) {
                    SyncedTabsRow(
                        tabs = allSyncedTabs,
                        activeTabId = activeTabId,
                        activeUrl = activeUrl,
                        pcHost = vm.prefs.host,
                        onSelect = { t -> if (t.url.isNotBlank()) openUrl(t.url, tabId = t.id) },
                        onCloseTab = ::handleCloseTab,
                    )
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

@Composable
private fun BrowserSyncHome(
    pcTabs: List<BrowserTab>,
    otherPcTabs: List<BrowserTab>,
    quickLinks: List<String>,
    syncing: Boolean,
    onOpen: (String) -> Unit,
    onCloseTab: (BrowserTab) -> Unit,
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
                    Text(
                        if (pcTabs.isEmpty()) tr("当前对话在电脑上还没有打开网页") else tr("已同步当前对话的 {0} 个标签页", pcTabs.size),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    Row(Modifier.fillMaxWidth().clickable { onOpen(u) }.padding(horizontal = 16.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(u, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
