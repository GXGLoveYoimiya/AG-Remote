package com.local.agremote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.local.agremote.AppViewModel
import com.local.agremote.Automation

private val WEEK = listOf("日", "一", "二", "三", "四", "五", "六") // cron 里 0=周日

/** 把 “CRON_TZ=Asia/Shanghai 30 9 * * 1-5” 这种翻成 “工作日 09:30”。看不懂的就原样显示。 */
fun cronText(s: String): String {
    val parts = s.trim().split(Regex("\\s+")).filterNot { it.startsWith("CRON_TZ=") }
    if (parts.size != 5) return s
    val (min, hour, dom, mon, dow) = parts
    val m = min.toIntOrNull(); val h = hour.toIntOrNull()
    fun f(key: String, vararg a: Any): String {
        var r = I18n.t(key)
        a.forEachIndexed { i, v -> r = r.replace("{$i}", v.toString()) }
        return r
    }
    if (m != null && hour == "*" && dom == "*" && mon == "*" && dow == "*") return f("每小时第 {0} 分", m)
    if (m == null || h == null || dom != "*" || mon != "*") return parts.joinToString(" ")
    val t = "%02d:%02d".format(h, m)
    return when (dow) {
        "*" -> f("每天 {0}", t)
        "1-5" -> f("工作日 {0}", t)
        "0,6", "6,0" -> f("周末 {0}", t)
        else -> {
            val zh = I18n.currentLang.startsWith("zh")
            val days = dow.split(',').mapNotNull { it.toIntOrNull()?.let { d -> if (zh) WEEK.getOrNull(d % 7) else weekdayName(d) } }
            f("每周{0} {1}", days.joinToString(if (zh) "、" else ", "), t)
        }
    }
}

/** 星期几的简称（cron 里 0 = 周日），按界面语言显示：周一 / Mon / 月… */
fun weekdayName(d: Int): String {
    val lang = I18n.currentLang
    if (lang.startsWith("zh")) return "周" + WEEK[d % 7]
    val dw = java.time.DayOfWeek.of(if (d % 7 == 0) 7 else d % 7)
    return dw.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.forLanguageTag(lang))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val list by vm.automations.collectAsState()
    var editing by remember { mutableStateOf<Automation?>(null) }
    var creating by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadAutomations() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(tr("自动化"), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(tr("新建自动化")) },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(50)) // 和项目页的“新建项目”同款
        },
    ) { pad ->
        val l = list
        when {
            l == null -> Box(Modifier.padding(pad).fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            l.isEmpty() -> Column(Modifier.padding(pad).fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                Spacer(Modifier.height(12.dp))
                Text(tr("还没有自动化"), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Text(tr("自动化会按你设定的时间，在电脑上自动开一个新对话，让 agent 去做你写好的事。"),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            else -> LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { CardGroup { l.forEachIndexed { i, a ->
                    if (i > 0) CardDivider(indent = false)
                    run {
                        Row(Modifier.fillMaxWidth().clickable { editing = a }.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(a.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(cronText(a.schedule), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(a.prompt, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (a.error.isNotBlank()) Text(a.error, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.error)
                            }
                            Spacer(Modifier.width(8.dp))
                            Switch(checked = a.enabled, onCheckedChange = { vm.automationAction(a.id, if (it) "enable" else "disable") })
                        }
                    }
                } } }
            }
        }
    }

    if (creating || editing != null) AutomationEditor(vm, editing) { creating = false; editing = null }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun AutomationEditor(vm: AppViewModel, a: Automation?, onClose: () -> Unit) {
    // 从已有的 cron 里尽量还原出表单
    val parts = a?.schedule?.split(Regex("\\s+"))?.filterNot { it.startsWith("CRON_TZ=") } ?: emptyList()
    val initMin = parts.getOrNull(0)?.toIntOrNull() ?: 0
    val initHour = parts.getOrNull(1)?.toIntOrNull() ?: 9
    val initDow = parts.getOrNull(4) ?: "*"
    val initMode = when {
        a == null -> "daily"
        parts.size == 5 && parts[1] == "*" -> "hourly"
        parts.size == 5 && parts[1].toIntOrNull() != null && parts[2] == "*" && parts[3] == "*" -> when (initDow) {
            "*" -> "daily"; "1-5" -> "weekday"; else -> "weekly"
        }
        else -> "custom"
    }
    var name by remember { mutableStateOf(a?.name ?: "") }
    var prompt by remember { mutableStateOf(a?.prompt ?: "") }
    var mode by remember { mutableStateOf(initMode) }
    val days = remember { mutableStateListOf<Int>().apply {
        if (initMode == "weekly") addAll(initDow.split(',').mapNotNull { it.toIntOrNull()?.rem(7) }) else add(1)
    } }
    var custom by remember { mutableStateOf(a?.schedule?.split(Regex("\\s+"))?.filterNot { it.startsWith("CRON_TZ=") }?.joinToString(" ") ?: "0 9 * * *") }
    val time = rememberTimePickerState(initHour, initMin, true)
    var pickTime by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val tz = java.util.TimeZone.getDefault().id

    fun cron(): String {
        val body = when (mode) {
            "hourly" -> "${time.minute} * * * *"
            "daily" -> "${time.minute} ${time.hour} * * *"
            "weekday" -> "${time.minute} ${time.hour} * * 1-5"
            "weekly" -> "${time.minute} ${time.hour} * * ${days.sorted().joinToString(",").ifBlank { "1" }}"
            else -> custom.trim()
        }
        return "CRON_TZ=$tz $body"
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding().padding(horizontal = 20.dp)
            .padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (a == null) tr("新建自动化") else tr("编辑自动化"), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if (a != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, tr("删除"), tint = MaterialTheme.colorScheme.error) }
            }
            OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(tr("名称")) }, singleLine = true)
            OutlinedTextField(prompt, { prompt = it }, Modifier.fillMaxWidth().height(140.dp), label = { Text(tr("到时间让 agent 做什么")) })
            Text(tr("什么时候运行"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("daily" to "每天", "weekday" to "工作日", "weekly" to "每周", "hourly" to "每小时", "custom" to "自定义").forEach { (k, l) ->
                    FilterChip(selected = mode == k, onClick = { mode = k }, label = { Text(tr(l)) })
                }
            }
            if (mode == "weekly") FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..7).map { it % 7 }.forEach { d ->
                    FilterChip(selected = d in days, onClick = { if (d in days) days.remove(d) else days.add(d) }, label = { Text(weekdayName(d)) })
                }
            }
            when (mode) {
                "custom" -> OutlinedTextField(custom, { custom = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(tr("cron 表达式（分 时 日 月 周）")) }, supportingText = { Text(tr("时区：{0}", tz)) })
                "hourly" -> TextButton(onClick = { pickTime = true }) { Text(tr("每小时第 {0} 分（点这里修改）", time.minute)) }
                else -> TextButton(onClick = { pickTime = true }) { Text(tr("时间：{0}（点这里修改）", "%02d:%02d".format(time.hour, time.minute))) }
            }
            Text(tr("将会：") + cronText(cron()), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            Button(
                onClick = {
                    busy = true
                    vm.saveAutomation(a?.id, name.ifBlank { prompt.take(20) }, cron(), prompt, "") { ok -> busy = false; if (ok) onClose() }
                },
                enabled = !busy && prompt.isNotBlank() && (mode != "weekly" || days.isNotEmpty()),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(tr("保存")) }
        }
    }

    if (pickTime) AlertDialog(
        onDismissRequest = { pickTime = false },
        confirmButton = { TextButton(onClick = { pickTime = false }) { Text(tr("好")) } },
        text = { TimePicker(time) },
    )
    if (confirmDelete && a != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text(tr("删除自动化？")) },
        text = { Text(tr("「{0}」会被删除，不会再自动运行。", a.name)) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.automationAction(a.id, "delete"); onClose() }) {
            Text(tr("删除"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("取消")) } },
    )
}
