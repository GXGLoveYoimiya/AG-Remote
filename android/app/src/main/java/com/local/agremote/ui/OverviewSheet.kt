package com.local.agremote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Difference
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.local.agremote.AppViewModel
import com.local.agremote.Attachments
import com.local.agremote.BackgroundTaskItem
import com.local.agremote.Step
import com.local.agremote.StepAttachment
import com.local.agremote.SubagentItem
import kotlinx.coroutines.delay

private val URL_EXTRACT_RE = Regex("""https?://[^\s<>"'`\])]+""")

fun extractConversationUrls(steps: List<Step>): List<String> {
    val seen = LinkedHashSet<String>()
    for (s in steps) {
        for (src in listOf(s.text, s.detail)) {
            if (src.isBlank()) continue
            for (m in URL_EXTRACT_RE.findAll(src)) {
                val u = m.value.trimEnd('.', ',', ';', ')', ']', '。', '，', '、', '！', '？')
                if (u.length > 10) seen.add(u)
            }
        }
    }
    return seen.toList().reversed()
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0 -> ""
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
    else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

private sealed interface OverviewDetail {
    data object Review : OverviewDetail
    data object Files : OverviewDetail
    data object Terminals : OverviewDetail
    data object AllTasks : OverviewDetail
    data class Subagent(val item: SubagentItem) : OverviewDetail
    data class TaskLog(val item: BackgroundTaskItem) : OverviewDetail
}

/**
 * 与电脑 Antigravity 同步的「概览 / 子代理 / 后台任务」统一弹出菜单：
 * - 概览：Review、Files、终端、插件扩展 >
 * - 子代理（带计数）：列出本对话挂接的所有子代理，点击可查看子代理步骤
 * - 后台任务（带计数）：实时同步运行中与历史后台任务，点击看日志，底部「查看全部」
 */
@Composable
fun OverviewSheet(
    vm: AppViewModel,
    onOpenCustomizations: () -> Unit,
    onClose: () -> Unit,
) {
    val chat by vm.chat.collectAsState()
    val ov by vm.overview.collectAsState()
    val loading by vm.overviewLoading.collectAsState()
    val chatId = chat.id ?: return

    var detail by remember { mutableStateOf<OverviewDetail?>(null) }

    // 打开时立刻拉取最新同步数据；如果当前对话或后台任务正在运行，每 3 秒自动同步
    LaunchedEffect(chatId) {
        vm.loadOverview(chatId)
        while (true) {
            delay(3000)
            if (isRunning(chat.conv?.status) || ov.backgroundTasks.any { it.running } || ov.subagents.any { isRunning(it.status) }) {
                vm.loadOverview(chatId)
            }
        }
    }

    val commands = remember(chat.steps) {
        chat.steps.filter { it.type == "run_command" && it.text.isNotBlank() }.reversed()
    }
    val reviewCount = remember(ov.fileDiffs, chat.steps) {
        if (ov.fileDiffs.isNotEmpty()) ov.fileDiffs.size
        else chat.steps.filter { it.kind == "file" && it.text.isNotBlank() }.map { it.text.replace('\\', '/').lowercase() }.distinct().size
    }

    val d = detail
    if (d == null) {
        // 与电脑 Antigravity 同款的右上角悬浮弹出菜单
        Dialog(
            onDismissRequest = onClose,
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
                    .clickable(onClick = onClose)
                    .statusBarsPadding()
                    .padding(top = 54.dp, end = 12.dp, start = 12.dp, bottom = 24.dp),
                contentAlignment = Alignment.TopEnd,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .widthIn(min = 272.dp, max = 320.dp)
                        .heightIn(max = 580.dp)
                        .clickable(enabled = false) {},
                ) {
                    Column(Modifier.verticalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                        // 1. 概览区
                        Row(
                            Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(tr("概览"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (loading) {
                                CircularProgressIndicator(Modifier.size(16.dp).padding(end = 2.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(8.dp))
                            }
                            IconButton(onClick = { vm.loadOverview(chatId) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Rounded.Refresh, tr("刷新"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        PopupMenuItem(Icons.Rounded.Difference, "Review", count = reviewCount) { detail = OverviewDetail.Review }
                        PopupMenuItem(Icons.Rounded.Description, "Files", count = ov.artifacts.size) { detail = OverviewDetail.Files }
                        PopupMenuItem(Icons.Rounded.Terminal, tr("终端"), count = commands.size) { detail = OverviewDetail.Terminals }
                        PopupMenuItem(Icons.Rounded.Extension, tr("插件扩展"), chevron = true) {
                            onClose(); onOpenCustomizations()
                        }

                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                        // 2. 子代理区
                        PopupSectionHeader(tr("子代理"), ov.subagents.size)
                        if (ov.subagents.isEmpty()) {
                            Text("暂无子代理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                        } else {
                            ov.subagents.take(5).forEach { sa ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { detail = OverviewDetail.Subagent(sa) }
                                        .padding(horizontal = 18.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.SmartToy, null, Modifier.size(18.dp),
                                        tint = if (isRunning(sa.status)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(sa.role, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        val sub = listOfNotNull(
                                            sa.typeName.takeIf { it.isNotBlank() && it != sa.role },
                                            "${sa.steps} 步",
                                            if (sa.killed) "已中止" else if (isRunning(sa.status)) "运行中" else "已完成",
                                        ).joinToString(" · ")
                                        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (isRunning(sa.status)) {
                                        Spacer(Modifier.width(6.dp))
                                        StatusDot(true)
                                    }
                                }
                            }
                        }

                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))

                        // 3. 后台任务区（同步电脑 Antigravity，最多先展示 3 条 + See all）
                        PopupSectionHeader(tr("后台任务"), ov.backgroundTasks.size)
                        if (ov.backgroundTasks.isEmpty()) {
                            Text("暂无后台任务", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp))
                        } else {
                            val runningFirst = ov.backgroundTasks.filter { it.running }
                            val previewList = if (runningFirst.isNotEmpty()) runningFirst.take(3) else ov.backgroundTasks.take(3)
                            previewList.forEach { task ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { detail = OverviewDetail.TaskLog(task) }
                                        .padding(horizontal = 18.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Rounded.Terminal, null, Modifier.size(18.dp),
                                        tint = if (task.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(12.dp))
                                    Text(task.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (task.running) {
                                        Spacer(Modifier.width(8.dp))
                                        StatusDot(true)
                                    }
                                }
                            }
                            if (ov.backgroundTasks.size > previewList.size || ov.backgroundTasks.isNotEmpty()) {
                                Row(
                                    Modifier.fillMaxWidth().clickable { detail = OverviewDetail.AllTasks }
                                        .padding(horizontal = 18.dp, vertical = 9.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(tr("查看全部"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    } else {
        // 点击弹出菜单某一项后展开的二级详情弹窗
        OverviewDetailSheet(
            vm = vm,
            chatId = chatId,
            detail = d,
            onBack = { detail = null },
            onOpenTask = { detail = OverviewDetail.TaskLog(it) },
            onClose = onClose,
        )
    }
}

@Composable
private fun PopupMenuItem(
    icon: ImageVector,
    label: String,
    count: Int = 0,
    chevron: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        if (count > 0) {
            CountPill(count)
        }
        if (chevron) {
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PopupSectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        CountPill(count)
    }
}

@Composable
private fun CountPill(count: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
        Text(
            "$count",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OverviewDetailSheet(
    vm: AppViewModel,
    chatId: String,
    detail: OverviewDetail,
    onBack: () -> Unit,
    onOpenTask: (BackgroundTaskItem) -> Unit,
    onClose: () -> Unit,
) {
    val chat by vm.chat.collectAsState()
    val ov by vm.overview.collectAsState()
    val expandedFiles = remember { mutableStateMapOf<String, Boolean>() }
    val expandedCmds = remember { mutableStateMapOf<Int, Boolean>() }

    val title = when (detail) {
        OverviewDetail.Review -> "Review · 文件变更"
        OverviewDetail.Files -> "Files · 产物与文件"
        OverviewDetail.Terminals -> "终端命令"
        OverviewDetail.AllTasks -> "全部后台任务 (${ov.backgroundTasks.size})"
        is OverviewDetail.Subagent -> "子代理 · ${detail.item.role}"
        is OverviewDetail.TaskLog -> detail.item.title
    }

    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.84f).navigationBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "返回") }
                Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭") }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))

            when (detail) {
                OverviewDetail.Review -> {
                    val stepDiffs = remember(chat.steps) {
                        val map = HashMap<String, Pair<String, String>>() // pathLower -> (sub, diff)
                        chat.steps.filter { it.kind == "file" && it.text.isNotBlank() }.forEach { s ->
                            val k = s.text.replace('\\', '/').lowercase()
                            val prev = map[k]
                            map[k] = (s.sub.ifBlank { prev?.first ?: "" }) to (s.detail.ifBlank { prev?.second ?: "" })
                        }
                        map
                    }
                    val list = remember(ov.fileDiffs, chat.steps) {
                        if (ov.fileDiffs.isNotEmpty()) ov.fileDiffs
                        else chat.steps.filter { it.kind == "file" && it.text.isNotBlank() }
                            .map { it.text.replace('\\', '/') }.distinct().reversed()
                            .map { p -> com.local.agremote.FileDiffItem(p, p.substringAfterLast('/'), p.substringBeforeLast('/', ""), false, "") }
                    }
                    if (list.isEmpty()) {
                        EmptyBox("本次对话还没有修改文件")
                    } else {
                        LazyColumn(
                            Modifier.weight(1f).noSheetOverscroll(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(list, key = { it.path }) { f ->
                                val open = expandedFiles[f.path] == true
                                val (sub, diff) = stepDiffs[f.path.lowercase()] ?: ("" to "")
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth().clickable { expandedFiles[f.path] = !open }.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Code, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(f.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                Text(
                                                    listOfNotNull(sub.takeIf { it.isNotBlank() }, f.dir.takeIf { it.isNotBlank() }).joinToString(" · "),
                                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (open) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = {
                                                    vm.openPreview(vm.previewOf(StepAttachment(f.name, Attachments.mimeOf(f.name), f.path)))
                                                }) { Text("查看完整文件") }
                                            }
                                            if (diff.isNotBlank()) {
                                                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                                    Column {
                                                        diff.lines().take(100).forEach { line ->
                                                            val c = when {
                                                                line.startsWith("+") -> Green
                                                                line.startsWith("-") -> MaterialTheme.colorScheme.error
                                                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                                                            }
                                                            Text(line, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp, color = c)
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
                }

                OverviewDetail.Files -> {
                    if (ov.artifacts.isEmpty()) {
                        EmptyBox("本次对话还没有产物或文件")
                    } else {
                        LazyColumn(
                            Modifier.weight(1f).noSheetOverscroll(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(ov.artifacts, key = { it.path }) { a ->
                                val icon = when {
                                    a.mime.startsWith("image/") -> Icons.Rounded.Image
                                    a.mime.startsWith("video/") -> Icons.Rounded.Videocam
                                    else -> Icons.Rounded.Description
                                }
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable {
                                        vm.openPreview(vm.previewOf(StepAttachment(a.name, a.mime, a.path)))
                                    },
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(icon, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(a.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val meta = listOfNotNull(formatSize(a.size).takeIf { it.isNotBlank() }, relTime(a.updated).takeIf { it.isNotBlank() }).joinToString(" · ")
                                            if (meta.isNotBlank()) Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            if (a.summary.isNotBlank()) Text(a.summary, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OverviewDetail.Terminals -> {
                    val commands = remember(chat.steps) { chat.steps.filter { it.type == "run_command" && it.text.isNotBlank() }.reversed() }
                    if (commands.isEmpty()) {
                        EmptyBox("本次对话还没有执行终端命令")
                    } else {
                        LazyColumn(
                            Modifier.weight(1f).noSheetOverscroll(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(commands, key = { it.i }) { cmd ->
                                val open = expandedCmds[cmd.i] == true
                                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                    Column {
                                        Row(
                                            Modifier.fillMaxWidth().clickable { expandedCmds[cmd.i] = !open }.padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Rounded.Terminal, null, Modifier.size(20.dp),
                                                tint = if (cmd.status == "error") MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(cmd.text, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                                                    maxLines = if (open) 6 else 1, overflow = TextOverflow.Ellipsis)
                                                if (cmd.sub.isNotBlank()) Text(cmd.sub, style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                            Icon(if (open) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (open) {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.End) {
                                                TextButton(onClick = { vm.copy(cmd.text, "已复制命令") }) { Text("复制命令") }
                                                if (cmd.detail.isNotBlank()) TextButton(onClick = { vm.copy(cmd.detail, "已复制输出") }) { Text("复制输出") }
                                            }
                                            if (cmd.detail.isNotBlank()) {
                                                Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(start = 12.dp, end = 12.dp, bottom = 12.dp)) {
                                                    Text(cmd.detail, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                OverviewDetail.AllTasks -> {
                    if (ov.backgroundTasks.isEmpty()) {
                        EmptyBox("暂无后台任务")
                    } else {
                        LazyColumn(
                            Modifier.weight(1f).noSheetOverscroll(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            items(ov.backgroundTasks, key = { it.id }) { task ->
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable { onOpenTask(task) },
                                ) {
                                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Terminal, null, Modifier.size(20.dp),
                                            tint = if (task.running) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(task.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                                                maxLines = 2, overflow = TextOverflow.Ellipsis)
                                            if (task.command.isNotBlank()) {
                                                Text(task.command, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            }
                                        }
                                        if (task.running) {
                                            Spacer(Modifier.width(8.dp))
                                            StatusDot(true)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                is OverviewDetail.TaskLog -> {
                    val task = detail.item
                    val logText by produceState<String?>(null, task.id) {
                        value = vm.loadTaskLog(chatId, task.id)
                    }
                    Column(Modifier.weight(1f).noSheetOverscroll().verticalScroll(rememberScrollState()).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        if (task.command.isNotBlank()) {
                            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("命令", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        TextButton(onClick = { vm.copy(task.command, "已复制命令") }) {
                                            Icon(Icons.Rounded.ContentCopy, null, Modifier.size(14.dp))
                                            Spacer(Modifier.width(4.dp))
                                            Text("复制")
                                        }
                                    }
                                    Text(task.command, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 16.sp)
                                }
                            }
                        }
                        Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(if (task.running) "实时日志（运行中）" else "任务日志", Modifier.weight(1f),
                                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    val body = logText ?: ""
                                    if (body.isNotBlank()) {
                                        TextButton(onClick = { vm.copy(body, "已复制日志") }) { Text("复制日志") }
                                    }
                                }
                                when (val body = logText) {
                                    null -> Box(Modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                                    }
                                    "" -> Text(task.progress.ifBlank { "暂无输出日志" }, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    else -> Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
                                        Text(body, fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                is OverviewDetail.Subagent -> {
                    val sa = detail.item
                    val steps by produceState<List<Step>?>(null, sa.id) {
                        value = vm.loadSubagentSteps(sa.id).filter { it.kind != "hidden" }
                    }
                    when (val list = steps) {
                        null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 2.dp)
                        }
                        emptyList<Step>() -> EmptyBox("子代理暂无可显示的步骤")
                        else -> LazyColumn(
                            Modifier.weight(1f).noSheetOverscroll(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(list, key = { it.i }) { st ->
                                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(
                                            when (st.kind) { "user" -> "任务指令"; "assistant" -> "子代理回复"; else -> st.title },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        if (st.text.isNotBlank()) {
                                            Text(st.text, style = MaterialTheme.typography.bodySmall, maxLines = 16, overflow = TextOverflow.Ellipsis)
                                        }
                                        if (st.sub.isNotBlank()) {
                                            Text(st.sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
}

@Composable
private fun EmptyBox(msg: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 48.dp), contentAlignment = Alignment.Center) {
        Text(msg, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
