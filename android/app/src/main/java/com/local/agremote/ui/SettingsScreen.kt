package com.local.agremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.local.agremote.SavedHost
import kotlinx.coroutines.launch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.agremote.AppViewModel
import com.local.agremote.Conversation
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.DataUsage
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.automirrored.rounded.Logout

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit, startPage: String = "main") {
    val account by vm.account.collectAsState()
    val list by vm.list.collectAsState()
    val link by vm.link.collectAsState()
    val themeCfg by vm.theme.collectAsState()
    val langCode by vm.language.collectAsState()
    val linkMode by vm.linkOpenMode.collectAsState()
    val hosts by vm.hosts.collectAsState()
    val activeHostId by vm.activeHostId.collectAsState()
    val currentLang = LocalAppLang.current
    var pickLang by remember { mutableStateOf(false) }
    var addingHost by remember { mutableStateOf(false) }
    var editingHost by remember { mutableStateOf<SavedHost?>(null) }
    val scope = rememberCoroutineScope()
    val archived = list.filter { it.archived }
    LaunchedEffect(Unit) { vm.loadAccount() }

    // 一级页面是分组列表（参照 Claude 移动端），具体设置都放在二级页面里
    var page by rememberSaveable { mutableStateOf(startPage) }
    var confirmUnpair by remember { mutableStateOf(false) }
    val voiceCfg by vm.voiceCfg.collectAsState()
    androidx.activity.compose.BackHandler(enabled = page != startPage) { page = startPage }
    val linkModes = listOf(
        "ask" to tr("每次询问"),
        "system" to tr("通过默认浏览器"),
        "builtin" to tr("通过内置浏览器"),
        "chooser" to tr("选择打开应用"),
    )
    val activeHost = hosts.find { it.id == activeHostId }
    val title = when (page) {
        "usage" -> tr("用量"); "archived" -> tr("已归档的对话"); "hosts" -> tr("连接的电脑"); "voice" -> tr("实时语音")
        "notify" -> tr("消息推送"); "link" -> tr("链接打开方式"); "theme" -> tr("主题风格与配色"); else -> tr("设置")
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface, topBar = {
        AppTopBar(title, onBack = { if (page == startPage) onBack() else page = startPage }) {
            if (page == "main" || page == "usage") IconButton(onClick = { vm.loadAccount(); vm.refreshList() }) { Icon(Icons.Rounded.Refresh, tr("刷新")) }
        }
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            when (page) {
                "main" -> {
                    // 顶部：Antigravity 账户 + 套餐
                    item {
                        Surface(onClick = { page = "usage" }, color = sheetCardColor(), shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
                            Row(Modifier.padding(horizontal = 16.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(account?.name?.ifBlank { null } ?: "Antigravity", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                account?.plan?.takeIf { it.isNotBlank() }?.let { plan ->
                                    Surface(color = MaterialTheme.colorScheme.primary, shape = RoundedCornerShape(50)) {
                                        Text(plan, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onPrimary, maxLines = 1)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { SectionLabel(tr("账户")) }
                    item {
                        CardGroup {
                            ListRow(tr("用量"), Icons.Rounded.DataUsage, value = account?.plan) { page = "usage" }
                            CardDivider()
                            ListRow(tr("已归档的对话"), Icons.Rounded.Archive, value = "${archived.size}") { page = "archived" }
                        }
                    }
                    item { SectionLabel(tr("应用")) }
                    item {
                        CardGroup {
                            ListRow(tr("连接的电脑"), Icons.Rounded.Computer,
                                value = activeHost?.name?.ifBlank { activeHost.host } ?: "${vm.prefs.host}:${vm.prefs.port}") { page = "hosts" }
                            CardDivider()
                            ListRow(tr("实时语音"), Icons.Rounded.GraphicEq,
                                value = if (voiceCfg.available) voiceCfg.settings.name else tr("不可用")) { page = "voice" }
                            CardDivider()
                            ListRow(tr("消息推送"), Icons.Rounded.Notifications, value = if (vm.prefs.notify) tr("开") else tr("关")) { page = "notify" }
                            CardDivider()
                            ListRow(tr("链接打开方式"), Icons.Rounded.Link, value = linkModes.find { it.first == linkMode }?.second) { page = "link" }
                        }
                    }
                    item { SectionLabel(tr("外观")) }
                    item {
                        CardGroup {
                            ColorModeRow(themeCfg.darkMode) { vm.updateTheme(themeCfg.copy(darkMode = it)) }
                            CardDivider()
                            ListRow(tr("主题风格与配色"), Icons.Rounded.Palette, value = when (themeCfg.style) {
                                "material" -> "Material You"; "miuix" -> "Miuix"; else -> tr("默认") }) { page = "theme" }
                            CardDivider()
                            ListRow(tr("语言"), Icons.Rounded.Language, value = appLanguageDisplayName(langCode, currentLang)) { pickLang = true }
                        }
                    }
                    item {
                        CardGroup {
                            ListRow(tr("取消配对"), Icons.AutoMirrored.Rounded.Logout, end = RowEnd.None,
                                titleColor = MaterialTheme.colorScheme.error) { confirmUnpair = true }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                "usage" -> {
                    val a = account
                    if (a == null) item { Text(tr("读取中…"), Modifier.padding(6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else {
                        if (a.note.isNotBlank()) item { Text(a.note, Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.bodyLarge) }
                        a.groups.forEach { g ->
                            item { SectionLabel(g.name) }
                            if (g.models.isNotEmpty()) item {
                                Text(g.models.joinToString("、"), Modifier.padding(horizontal = 6.dp), style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            items(g.buckets) { b ->
                                val sp = if (LocalAppLang.current.startsWith("zh") || LocalAppLang.current == "ja") "" else " "
                                UsageCard(b.name, 1.0 - b.remaining, if (b.reset.isNotBlank()) "${untilText(b.reset)}$sp${tr("重置")}" else "", b.desc)
                            }
                        }
                        if (a.promptAvail != null || a.flowAvail != null) {
                            item { SectionLabel("AI Credits") }
                            item {
                                CardGroup {
                                    a.promptAvail?.let { ListRow("Prompt credits", value = creditText(it, a.promptMonthly), end = RowEnd.None) }
                                    if (a.promptAvail != null && a.flowAvail != null) CardDivider(indent = false)
                                    a.flowAvail?.let { ListRow("Flow credits", value = creditText(it, a.flowMonthly), end = RowEnd.None) }
                                }
                            }
                        }
                    }
                }

                "archived" -> {
                    if (archived.isEmpty()) item { Text(tr("没有归档的对话"), Modifier.padding(6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else item {
                        CardGroup {
                            archived.forEachIndexed { i, c ->
                                if (i > 0) CardDivider(indent = false)
                                ArchivedRow(vm, c)
                            }
                        }
                    }
                }

                "voice" -> item { VoiceSettingsCard(vm) }
                "notify" -> item { NotifySettings(vm) }
                "theme" -> item { ThemeSettingsCard(themeCfg) { vm.updateTheme(it) } }

                "link" -> item {
                    CardGroup {
                        linkModes.forEachIndexed { idx, (mode, label) ->
                            if (idx > 0) CardDivider(indent = false)
                            ListRow(label, end = RowEnd.Checked(linkMode == mode)) { vm.updateLinkOpenMode(mode) }
                        }
                    }
                }

                "hosts" -> {
                    item { CardBox { LinkLine(link) } }
                    item { SectionLabel(tr("电脑")) }
                    item {
                        CardGroup {
                            hosts.forEachIndexed { idx, h ->
                                if (idx > 0) CardDivider()
                                val isActive = h.id == activeHostId
                                ListRow(h.name.ifBlank { h.host }, Icons.Rounded.Computer, desc = "${h.host}:${h.port}", end = RowEnd.Checked(isActive),
                                    trailing = {
                                        IconButton(onClick = { editingHost = h }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Rounded.Edit, tr("编辑设备"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        IconButton(onClick = { vm.removeHost(h.id) }, modifier = Modifier.size(36.dp)) {
                                            Icon(Icons.Rounded.Delete, tr("删除"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        }
                                    }) { if (!isActive) vm.switchHost(h.id) }
                            }
                            if (hosts.isEmpty()) ListRow("${vm.prefs.host}:${vm.prefs.port}", Icons.Rounded.Computer, end = RowEnd.Checked(true))
                        }
                    }
                    item { CardGroup { ListRow(tr("添加设备"), Icons.Rounded.Add) { addingHost = true } } }
                }
            }
        }
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            title = { Text(tr("取消配对")) },
            text = { Text(tr("断开和这台电脑的配对？之后需要重新输入配对码才能再连接。")) },
            confirmButton = { TextButton(onClick = { confirmUnpair = false; vm.unpair() }) { Text(tr("取消配对"), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmUnpair = false }) { Text(tr("取消")) } },
        )
    }

    if (pickLang) {
        ModalBottomSheet(
            onDismissRequest = { pickLang = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
                Text(
                    tr("选择语言"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                )
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    APP_LANGUAGES.forEachIndexed { idx, item ->
                        if (idx > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        val selected = langCode == item.code
                        val label = if (item.code == "system") appLanguageDisplayName("system", currentLang) else item.label
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.updateLanguage(item.code); pickLang = false }
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (selected) {
                                Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (addingHost) {
        var devName by remember { mutableStateOf("") }
        var devHost by remember { mutableStateOf("") }
        var devPort by remember { mutableStateOf("5100") }
        var devCode by remember { mutableStateOf("") }
        var devBusy by remember { mutableStateOf(false) }
        var devErr by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { if (!devBusy) addingHost = false },
            title = { Text(tr("添加设备")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(devName, { devName = it }, Modifier.fillMaxWidth(), label = { Text(tr("设备名称")) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(devHost, { devHost = it.trim() }, Modifier.weight(1f), label = { Text("IP") },
                            placeholder = { Text("100.x.x.x") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        OutlinedTextField(devPort, { devPort = it.filter(Char::isDigit) }, Modifier.weight(0.5f), label = { Text("Port") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                    OutlinedTextField(devCode, { devCode = it.filter(Char::isDigit).take(6) }, Modifier.fillMaxWidth(),
                        label = { Text(tr("配对码")) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    devErr?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        devBusy = true; devErr = null
                        scope.launch {
                            val err = vm.pair(devHost, devPort.toIntOrNull() ?: 5100, devCode, devName)
                            devBusy = false
                            if (err == null) addingHost = false else devErr = err
                        }
                    },
                    enabled = !devBusy && devHost.isNotBlank() && devCode.length == 6,
                ) {
                    if (devBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(tr("配对"))
                }
            },
            dismissButton = {
                TextButton(onClick = { addingHost = false }, enabled = !devBusy) { Text(tr("取消")) }
            },
        )
    }

    editingHost?.let { target ->
        var devName by remember(target.id) { mutableStateOf(target.name) }
        var devHost by remember(target.id) { mutableStateOf(target.host) }
        var devPort by remember(target.id) { mutableStateOf(target.port.toString()) }
        AlertDialog(
            onDismissRequest = { editingHost = null },
            title = { Text(tr("编辑设备")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(devName, { devName = it }, Modifier.fillMaxWidth(), label = { Text(tr("设备名称")) }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(devHost, { devHost = it.trim() }, Modifier.weight(1f), label = { Text("IP") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
                        OutlinedTextField(devPort, { devPort = it.filter(Char::isDigit) }, Modifier.weight(0.5f), label = { Text("Port") },
                            singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.updateHost(target.id, devName, devHost, devPort.toIntOrNull() ?: 5100)
                        editingHost = null
                    },
                    enabled = devHost.isNotBlank(),
                ) { Text(tr("保存")) }
            },
            dismissButton = {
                TextButton(onClick = { editingHost = null }) { Text(tr("取消")) }
            },
        )
    }
}

/** 颜色模式：浅色 / 深色 / 跟随系统三个小预览块，直接点选。 */
@Composable
private fun ColorModeRow(mode: String, onPick: (String) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.DarkMode, null, Modifier.size(22.dp))
            Spacer(Modifier.width(14.dp))
            Text(tr("颜色模式"), style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf("light" to tr("浅色"), "dark" to tr("深色"), "system" to tr("跟随系统")).forEach { (k, label) ->
                val sel = mode == k
                Column(Modifier.weight(1f).clickable { onPick(k) }, horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.foundation.Canvas(
                        Modifier.fillMaxWidth().height(60.dp).clip(RoundedCornerShape(12.dp))
                            .border(if (sel) 2.dp else 1.dp, if (sel) accent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    ) {
                        val light = Color(0xFFF8F7F5); val dark = Color(0xFF262624)
                        when (k) {
                            "light" -> drawRect(light)
                            "dark" -> drawRect(dark)
                            else -> {
                                drawRect(light)
                                val p = androidx.compose.ui.graphics.Path().apply { moveTo(size.width, 0f); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
                                drawPath(p, dark)
                            }
                        }
                        val line = if (k == "dark") Color(0xFF6B6A65) else Color(0xFFB9B8B3)
                        drawRoundRect(line, androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.22f),
                            androidx.compose.ui.geometry.Size(size.width * 0.42f, 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        drawRoundRect(line, androidx.compose.ui.geometry.Offset(size.width * 0.14f, size.height * 0.36f),
                            androidx.compose.ui.geometry.Size(size.width * 0.28f, 4.dp.toPx()), androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                        drawCircle(accent, 6.dp.toPx(), androidx.compose.ui.geometry.Offset(size.width * 0.8f, size.height * 0.72f))
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(label, style = MaterialTheme.typography.labelMedium,
                        color = if (sel) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** 额度数字：有月度上限时显示“剩余 / 上限”。 */
private fun creditText(avail: Int, monthly: Int?) = if (monthly != null && monthly > 0) "$avail / $monthly" else "$avail"

@Composable
private fun ArchivedRow(vm: AppViewModel, c: Conversation) {
    var deleting by remember { mutableStateOf(false) }
    ListRow(c.title, desc = relTime(c.updated), end = RowEnd.None, trailing = {
        IconButton(onClick = { vm.setArchived(c.id, false) }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Unarchive, tr("取消归档")) }
        IconButton(onClick = { deleting = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.Delete, tr("删除"), tint = MaterialTheme.colorScheme.error) }
    })
    if (deleting) DeleteDialog(c.title, onDismiss = { deleting = false }) { deleting = false; vm.delete(c.id) }
}


/** 消息推送：开关 + 系统设置入口 + 说明（和其他设置页同一套行样式）。 */
@Composable
private fun NotifySettings(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(vm.prefs.notify) }
    val askPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardGroup {
            ListRow(tr("后台推送"), Icons.Rounded.Notifications, desc = tr("App 不在前台时，agent 完成或需要你确认会发通知"),
                end = RowEnd.Toggle(on) {
                    on = it; vm.prefs.notify = it
                    if (it) {
                        if (android.os.Build.VERSION.SDK_INT >= 33) askPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        com.local.agremote.WatchService.start(ctx)
                    } else com.local.agremote.WatchService.stop(ctx)
                })
            CardDivider()
            ListRow(tr("打开应用设置"), Icons.Rounded.Settings) {
                runCatching {
                    ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + ctx.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        Text(tr("为了锁屏后也能收到，请在系统设置里把本 App 的省电策略设为“无限制”，并允许自启动。"), Modifier.padding(horizontal = 6.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
