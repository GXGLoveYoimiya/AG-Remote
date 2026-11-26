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

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr("设置")) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("返回")) } },
            actions = { IconButton(onClick = { vm.loadAccount(); vm.refreshList() }) { Icon(Icons.Rounded.Refresh, tr("刷新")) } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Section(tr("额度")) }
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

            item { Section(tr("实时语音")) }
            item { VoiceSettingsCard(vm) }

            item { Section(tr("语言")) }
            item {
                val miuix = LocalUiStyle.current == "miuix"
                Surface(
                    onClick = { pickLang = true },
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    shape = RoundedCornerShape(if (miuix) 16.dp else 20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Language, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(tr("语言"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                appLanguageDisplayName(langCode, currentLang),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item { Section(tr("外观与主题")) }
            item { ThemeSettingsCard(themeCfg) { vm.updateTheme(it) } }

            item { Section(tr("链接打开方式")) }
            item {
                Card {
                    listOf(
                        "ask" to tr("每次询问"),
                        "system" to tr("通过默认浏览器"),
                        "builtin" to tr("通过内置浏览器"),
                        "chooser" to tr("选择打开应用"),
                    ).forEachIndexed { idx, (mode, label) ->
                        if (idx > 0) HorizontalDivider(
                            Modifier.padding(vertical = 4.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                        )
                        val sel = linkMode == mode
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { vm.updateLinkOpenMode(mode) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                label,
                                Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            )
                            if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            item { Section(tr("消息推送")) }
            item { NotifyCard(vm) }

            item { Section(tr("连接")) }
            item {
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
                            Icon(
                                Icons.Rounded.Computer,
                                null,
                                Modifier.size(20.dp),
                                tint = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        h.name.ifBlank { h.host },
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (isActive) {
                                        Spacer(Modifier.width(6.dp))
                                        Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                    }
                                }
                                Text(
                                    "${h.host}:${h.port}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
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
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { addingHost = true }) {
                            Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("添加设备"))
                        }
                        if (hosts.size <= 1) {
                            OutlinedButton(onClick = { vm.unpair() }) { Text(tr("取消配对")) }
                        }
                    }
                }
            }

            item { Section("${tr("已归档的对话")}（${archived.size}）") }
            if (archived.isEmpty()) item { Text(tr("没有归档的对话"), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(archived, key = { it.id }) { ArchivedRow(vm, it) }
            item { Spacer(Modifier.height(24.dp)) }
        }
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
    val miuix = LocalUiStyle.current == "miuix"
    Text(t, Modifier.padding(start = 4.dp, top = 8.dp), style = MaterialTheme.typography.labelLarge,
        color = if (miuix) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    val miuix = LocalUiStyle.current == "miuix"
    Surface(color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(if (miuix) 16.dp else 20.dp), modifier = Modifier.fillMaxWidth()) {
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
