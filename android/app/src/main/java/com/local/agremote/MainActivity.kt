package com.local.agremote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalFocusManager
import com.local.agremote.ui.AgAppTheme
import com.local.agremote.ui.ChatScreen
import com.local.agremote.ui.DrawerContent
import com.local.agremote.ui.SettingsScreen
import com.local.agremote.ui.SetupScreen

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        // 推送开着就要通知权限（Android 13+）
        if (Build.VERSION.SDK_INT >= 33 && vm.prefs.notify &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent {
            val themeCfg by vm.theme.collectAsState()
            val langCode by vm.language.collectAsState()
            val resolvedLang = com.local.agremote.ui.resolveAppLang(langCode)
            com.local.agremote.ui.I18n.currentLang = resolvedLang
            androidx.compose.runtime.CompositionLocalProvider(
                com.local.agremote.ui.LocalAppLang provides resolvedLang,
            ) {
                AgAppTheme(themeCfg) {
                    LaunchedEffect(Unit) {
                        vm.toast.collect { Toast.makeText(this@MainActivity, com.local.agremote.ui.I18n.translateToast(it), Toast.LENGTH_SHORT).show() }
                    }
                    Root(vm)
                }
            }
        }
    }

    override fun onStart() { super.onStart(); AppState.foreground = true; vm.start(); WatchService.start(this) }
    override fun onStop() { super.onStop(); AppState.foreground = false; vm.stop() }

    /** 点通知打开对应的对话。 */
    private fun handleIntent(i: Intent?) {
        i?.getStringExtra(WatchService.EXTRA_CONV)?.let { vm.openChat(it) }
        i?.removeExtra(WatchService.EXTRA_CONV)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handleIntent(intent) }
}

@Composable
private fun Root(vm: AppViewModel) {
    val paired by vm.paired.collectAsState()
    if (!paired) {
        SetupScreen(vm)
        return
    }
    // 当前页面：chat / settings / automations / customizations
    var screen by rememberSaveable { mutableStateOf("chat") }
    var showPalette by rememberSaveable { mutableStateOf(false) }
    var showOverview by rememberSaveable { mutableStateOf(false) }
    val settings = screen != "chat"
    val drawer = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val browserOpen by vm.browserOpen.collectAsState()
    val browserExplicitUrl by vm.browserExplicitUrl.collectAsState()
    val chatState by vm.chat.collectAsState()
    // 侧边栏一打开（点按钮或右滑）就收起键盘
    LaunchedEffect(drawer.targetValue) { if (drawer.targetValue == DrawerValue.Open) focusManager.clearFocus(force = true) }

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = settings && !drawer.isOpen) { screen = "chat" }

    ModalNavigationDrawer(
        drawerState = drawer,
        gesturesEnabled = !settings,
        drawerContent = {
            DrawerContent(
                vm,
                onClose = { scope.launch { drawer.close() } },
                onOpen = { screen = it },
                onCommandPalette = { showPalette = true },
            )
        },
    ) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState != "chat") (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                else fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
            },
            label = "nav",
        ) { s ->
            when (s) {
                "settings" -> SettingsScreen(vm, onBack = { screen = "chat" })
                "usage" -> SettingsScreen(vm, onBack = { screen = "chat" }, startPage = "usage")
                "automations" -> com.local.agremote.ui.AutomationsScreen(vm, onBack = { screen = "chat" })
                "customizations" -> com.local.agremote.ui.CustomizationsScreen(vm, onBack = { screen = "chat" })
                "projects" -> com.local.agremote.ui.ProjectsScreen(vm, onBack = { screen = "chat" }, onOpenChat = { screen = "chat" })
                else -> ChatScreen(
                    vm,
                    onMenu = { scope.launch { drawer.open() } },
                    showOverview = showOverview,
                    onShowOverview = { showOverview = it },
                    onOpenCustomizations = { screen = "customizations" },
                    onOpenUsage = { screen = "usage" },
                )
            }
        }
    }

    if (showPalette) {
        com.local.agremote.ui.CommandPaletteDialog(
            vm = vm,
            onDismiss = { showPalette = false },
            onNavigate = { screen = it },
            onOpenOverview = { showOverview = true },
        )
    }

    if (browserOpen) {
        com.local.agremote.ui.BrowserDialog(
            vm = vm,
            chatId = chatState.id ?: "",
            explicitUrl = browserExplicitUrl,
            quickLinks = com.local.agremote.ui.extractConversationUrls(chatState.steps),
            onClose = { vm.closeBrowser() },
        )
    }

    val pendingLink by vm.pendingLinkUrl.collectAsState()
    pendingLink?.let { url ->
        var chosenMode by remember(url) { mutableStateOf("system") }
        var rememberChoice by remember(url) { mutableStateOf(false) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { vm.dismissPendingLink() },
            title = { androidx.compose.material3.Text(com.local.agremote.ui.tr("是否打开链接")) },
            text = {
                androidx.compose.foundation.layout.Column(
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp),
                ) {
                    androidx.compose.material3.Text(
                        url,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                        color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                    androidx.compose.foundation.layout.Spacer(androidx.compose.ui.Modifier.height(4.dp))
                    listOf(
                        "system" to com.local.agremote.ui.tr("通过默认浏览器"),
                        "builtin" to com.local.agremote.ui.tr("通过内置浏览器"),
                        "chooser" to com.local.agremote.ui.tr("选择打开应用"),
                    ).forEach { (mode, label) ->
                        val sel = chosenMode == mode
                        androidx.compose.foundation.layout.Row(
                            androidx.compose.ui.Modifier
                                .fillMaxWidth()
                                .clickable { chosenMode = mode }
                                .padding(vertical = 4.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            androidx.compose.material3.RadioButton(
                                selected = sel,
                                onClick = { chosenMode = mode },
                            )
                            androidx.compose.material3.Text(
                                label,
                                style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                    androidx.compose.foundation.layout.Row(
                        androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .clickable { rememberChoice = !rememberChoice }
                            .padding(top = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = rememberChoice,
                            onCheckedChange = { rememberChoice = it },
                        )
                        androidx.compose.material3.Text(
                            com.local.agremote.ui.tr("记住本次决定"),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = { vm.confirmOpenLink(url, chosenMode, rememberChoice) },
                ) {
                    androidx.compose.material3.Text(com.local.agremote.ui.tr("确认"))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    onClick = { vm.dismissPendingLink() },
                ) {
                    androidx.compose.material3.Text(com.local.agremote.ui.tr("取消"))
                }
            },
        )
    }
}
