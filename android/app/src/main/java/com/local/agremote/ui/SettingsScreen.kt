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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.agremote.AppViewModel
import com.local.agremote.Conversation
import com.local.agremote.QuotaGroup
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.widthIn
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
fun SettingsScreen(vm: AppViewModel, onBack: () -> Unit) {
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
    var page by rememberSaveable { mutableStateOf("main") }
    var confirmUnpair by remember { mutableStateOf(false) }
    val voiceCfg by vm.voiceCfg.collectAsState()
    androidx.activity.compose.BackHandler(enabled = page != "main") { page = "main" }
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
        TopAppBar(
            title = { Text(title) },
            navigationIcon = {
                IconButton(onClick = { if (page == "main") onBack() else page = "main" }) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("返回"))
                }
            },
            actions = {
                if (page == "main" || page == "usage") IconButton(onClick = { vm.loadAccount(); vm.refreshList() }) { Icon(Icons.Rounded.Refresh, tr("刷新")) }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp),
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
                                    Surface(color = MaterialTheme.colorScheme.onSurface, shape = RoundedCornerShape(50)) {
                                        Text(plan, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.surface, maxLines = 1)
                                    }
                                }
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    item { Section(tr("账户")) }
                    item {
                        RowGroup {
                            NavRow(Icons.Rounded.DataUsage, tr("用量"), account?.plan.orEmpty()) { page = "usage" }
                            GroupDivider()
                            NavRow(Icons.Rounded.Archive, tr("已归档的对话"), "${archived.size}") { page = "archived" }
                        }
                    }
                    item { Section(tr("应用")) }
                    item {
                        RowGroup {
                            NavRow(Icons.Rounded.Computer, tr("连接的电脑"),
                                activeHost?.name?.ifBlank { activeHost.host } ?: "${vm.prefs.host}:${vm.prefs.port}") { page = "hosts" }
                            GroupDivider()
                            NavRow(Icons.Rounded.GraphicEq, tr("实时语音"),
                                if (voiceCfg.available) voiceCfg.settings.name else tr("不可用")) { page = "voice" }
                            GroupDivider()
                            NavRow(Icons.Rounded.Notifications, tr("消息推送"), if (vm.prefs.notify) tr("开") else tr("关")) { page = "notify" }
                            GroupDivider()
                            NavRow(Icons.Rounded.Link, tr("链接打开方式"), linkModes.find { it.first == linkMode }?.second.orEmpty()) { page = "link" }
                        }
                    }
                    item { Section(tr("外观")) }
                    item {
                        RowGroup {
                            ColorModeRow(themeCfg.darkMode) { vm.updateTheme(themeCfg.copy(darkMode = it)) }
                            GroupDivider()
                            NavRow(Icons.Rounded.Palette, tr("主题风格与配色"), when (themeCfg.style) {
                                "material" -> "Material You"; "miuix" -> "Miuix"; else -> tr("默认") }) { page = "theme" }
                            GroupDivider()
                            NavRow(Icons.Rounded.Language, tr("语言"), appLanguageDisplayName(langCode, currentLang)) { pickLang = true }
                        }
                    }
                    item {
                        RowGroup {
                            Row(Modifier.fillMaxWidth().clickable { confirmUnpair = true }.padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Rounded.Logout, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(14.dp))
                                Text(tr("取消配对"), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(24.dp)) }
                }

                "usage" -> {
                    val a = account
                    if (a == null) item { Text(tr("读取中…"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else {
                        item {
                            Card {
                                Text(a.plan.ifBlank { "Antigravity" }, style = MaterialTheme.typography.titleMedium)
                                if (a.name.isNotBlank()) Text(a.name, style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        items(a.groups) { QuotaCard(it) }
                        if (a.note.isNotBlank()) item {
                            Text(a.note, Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (a.promptAvail != null || a.flowAvail != null) item {
                            Card {
                                Text("AI Credits", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(6.dp))
                                a.promptAvail?.let { CreditRow("Prompt credits", it, a.promptMonthly) }
                                a.flowAvail?.let { CreditRow("Flow credits", it, a.flowMonthly) }
                            }
                        }
                    }
                }

                "archived" -> {
                    if (archived.isEmpty()) item { Text(tr("没有归档的对话"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items(archived, key = { it.id }) { ArchivedRow(vm, it) }
                }

                "voice" -> item { VoiceSettingsCard(vm) }
                "notify" -> item { NotifyCard(vm) }
                "theme" -> item { ThemeSettingsCard(themeCfg, showDarkMode = false) { vm.updateTheme(it) } }

                "link" -> item {
                    RowGroup {
                        linkModes.forEachIndexed { idx, (mode, label) ->
                            if (idx > 0) GroupDivider()
                            val sel = linkMode == mode
                            Row(Modifier.fillMaxWidth().clickable { vm.updateLinkOpenMode(mode) }.padding(horizontal = 16.dp, vertical = 15.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                                if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                "hosts" -> item {
                    Card {
                        hosts.forEachIndexed { idx, h ->
                            if (idx > 0) HorizontalDivider(
                                Modifier.padding(vertical = 8.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                            )
                            val isActive = h.id == activeHostId
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { if (!isActive) vm.switchHost(h.id) }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Rounded.Computer, null, Modifier.size(20.dp),
                                    tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(h.name.ifBlank { h.host }, style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (isActive) {
                                            Spacer(Modifier.width(6.dp))
                                            Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                    Text("${h.host}:${h.port}", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (isActive) {
                                        Spacer(Modifier.height(2.dp))
                                        LinkLine(link)
                                    }
                                }
                                IconButton(onClick = { editingHost = h }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Rounded.Edit, tr("编辑设备"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { vm.removeHost(h.id) }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Rounded.Delete, tr("删除"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                        if (hosts.isEmpty()) {
                            LinkLine(link)
                            Spacer(Modifier.height(4.dp))
                            Text("${vm.prefs.host}:${vm.prefs.port}", style = MaterialTheme.typography.bodyMedium)
                        }
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(onClick = { addingHost = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("添加设备"))
                        }
                    }
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

@Composable
private fun Section(t: String) {
    Text(t, Modifier.padding(start = 6.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 一组设置行：卡片底色 + 圆角，行之间细分割线。 */
@Composable
private fun RowGroup(content: @Composable () -> Unit) {
    Surface(color = sheetCardColor(), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun GroupDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** 图标 + 名称 + 右侧当前值 + 箭头，点开二级页面。 */
@Composable
private fun NavRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Text(title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (value.isNotBlank()) Text(value, Modifier.padding(start = 8.dp).widthIn(max = 160.dp), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
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

@Composable
private fun Card(content: @Composable () -> Unit) {
    Surface(color = sheetCardColor(),
        shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun QuotaCard(g: QuotaGroup) {
    Card {
        Text(g.name, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        g.models.forEach {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        g.buckets.forEach { b ->
            val pct = (b.remaining * 100).toInt().coerceIn(0, 100)
            val color = when {
                pct <= 10 -> MaterialTheme.colorScheme.error
                pct <= 30 -> Amber
                else -> MaterialTheme.colorScheme.primary
            }
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(b.name, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Text("$pct%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = color)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(progress = { b.remaining.toFloat() }, modifier = Modifier.fillMaxWidth().height(8.dp),
                color = color, trackColor = MaterialTheme.colorScheme.surfaceContainerHighest, strokeCap = StrokeCap.Round,
                gapSize = 0.dp, drawStopIndicator = {})
            Spacer(Modifier.height(6.dp))
            if (b.reset.isNotBlank()) {
                val sp = if (LocalAppLang.current.startsWith("zh") || LocalAppLang.current == "ja") "" else " "
                Text("${untilText(b.reset)}$sp${tr("重置")}", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (b.desc.isNotBlank()) Text(b.desc, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CreditRow(label: String, avail: Int, monthly: Int?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(if (monthly != null && monthly > 0) "$avail / $monthly" else "$avail", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ArchivedRow(vm: AppViewModel, c: Conversation) {
    var deleting by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(c.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
                Text(relTime(c.updated), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { vm.setArchived(c.id, false) }) { Icon(Icons.Rounded.Unarchive, tr("取消归档")) }
            IconButton(onClick = { deleting = true }) { Icon(Icons.Rounded.Delete, tr("删除"), tint = MaterialTheme.colorScheme.error) }
        }
    }
    if (deleting) DeleteDialog(c.title, onDismiss = { deleting = false }) { deleting = false; vm.delete(c.id) }
}


@Composable
private fun NotifyCard(vm: AppViewModel) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var on by remember { mutableStateOf(vm.prefs.notify) }
    val askPerm = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr("后台推送"), style = MaterialTheme.typography.titleSmall)
                Text(tr("App 不在前台时，agent 完成或需要你确认会发通知"), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.Switch(checked = on, onCheckedChange = {
                on = it; vm.prefs.notify = it
                if (it) {
                    if (android.os.Build.VERSION.SDK_INT >= 33) askPerm.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    com.local.agremote.WatchService.start(ctx)
                } else com.local.agremote.WatchService.stop(ctx)
            })
        }
        Spacer(Modifier.height(8.dp))
        Text(tr("为了锁屏后也能收到，请在系统设置里把本 App 的省电策略设为“无限制”，并允许自启动。"),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            runCatching {
                ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + ctx.packageName)).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }) { Text(tr("打开应用设置")) }
    }
}
