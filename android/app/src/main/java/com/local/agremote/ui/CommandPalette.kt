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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.local.agremote.AppViewModel
import com.local.agremote.Conversation
import kotlinx.coroutines.delay

private data class SlashDef(val cmd: String, val desc: String, val keywords: String)

private val SLASH_COMMANDS = listOf(
    SlashDef("/plan", "制定分步执行计划再动手", "plan 计划 规划 步骤"),
    SlashDef("/boost", "深度思考、多维方案与严格验证", "boost 深度 思考 复杂 加强"),
    SlashDef("/goal", "持续自动执行直到目标完全达成", "goal 目标 长时间 持续"),
    SlashDef("/grill-me", "通过多轮提问对齐设计细节", "grill 访谈 细节 方案"),
    SlashDef("/teamwork", "启动多个子 Agent 协同完成大任务", "teamwork 团队 多代理 子agent"),
    SlashDef("/browser", "调用浏览器浏览网页或操作 Web 应用", "browser 浏览器 网页 搜索"),
    SlashDef("/schedule", "设置定时任务或单次提醒", "schedule 定时 提醒 自动化 cron"),
    SlashDef("/learn", "把本次经验与偏好保存给后续任务", "learn 学习 记住 经验 规则"),
    SlashDef("/compact", "压缩整理当前长对话的上下文", "compact 压缩 总结 上下文"),
)

private val PALETTE_PRESETS = listOf(
    "AGENT_PERMISSION_PRESET_DEFAULT" to ("Default" to "默认"),
    "AGENT_PERMISSION_PRESET_REQUEST_REVIEW" to ("Request Review" to "请求审核"),
    "AGENT_PERMISSION_PRESET_AUTO" to ("Auto" to "自动"),
    "AGENT_PERMISSION_PRESET_TURBO" to ("Turbo Mode" to "极速模式"),
)

private sealed interface PaletteRow {
    val key: String
    data class Section(override val key: String, val title: String) : PaletteRow
    data class Entry(
        override val key: String,
        val icon: ImageVector,
        val title: String,
        val subtitle: String = "",
        val badge: String = "",
        val mono: Boolean = false,
        val checked: Boolean = false,
        val tint: Color? = null,
        val conv: Conversation? = null,
        val onClick: () -> Unit,
    ) : PaletteRow
}

/**
 * Antigravity 同款命令面板：快速检索全部对话（含已归档）、快捷操作、斜杠命令、项目、模型、权限预设、Skills 和连接器。
 */
@Composable
fun CommandPaletteDialog(
    vm: AppViewModel,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    onOpenOverview: () -> Unit,
) {
    val list by vm.list.collectAsState()
    val chat by vm.chat.collectAsState()
    val projects by vm.projects.collectAsState()
    val models by vm.models.collectAsState()
    val selectedModel by vm.selected.collectAsState()
    val preset by vm.preset.collectAsState()
    val skills by vm.skills.collectAsState()
    val connectors by vm.connectors.collectAsState()
    val theme by vm.theme.collectAsState()

    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf("all") } // all / conv / action / slash / model / ext
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        vm.loadExtras()
        delay(80)
        runCatching { focus.requestFocus() }
    }

    val q = query.trim().lowercase()
    fun matches(vararg fields: String): Boolean {
        if (q.isBlank()) return true
        val hay = fields.joinToString(" ").lowercase()
        return q.split(Regex("\\s+")).all { tok -> hay.contains(tok) }
    }

    val convProjectName = remember(projects) {
        buildMap<String, String> {
            projects.filter { !it.sidecar }.forEach { p -> p.conversations.forEach { cid -> put(cid, p.name) } }
        }
    }

    val lang = LocalAppLang.current
    val rows = remember(q, tab, lang, list, chat.id, chat.conv, projects, models, selectedModel, preset, skills, connectors, theme) {
        val t = { k: String -> I18n.t(lang, k) }
        val out = mutableListOf<PaletteRow>()
        val curConv = chat.conv
        val hasChat = chat.id != null
        val curRunning = isRunning(curConv?.status)

        // 1. 快捷操作
        if (tab == "all" || tab == "action") {
            val actions = buildList {
                add(PaletteRow.Entry("act_new", Icons.Rounded.EditNote, t("新建对话"), t("开始一个全新的会话")) {
                    vm.newChat(); onNavigate("chat"); onDismiss()
                })
                add(PaletteRow.Entry("act_browser", Icons.Rounded.Language, t("打开内置浏览器"), t("在应用内浏览网页或本地服务")) {
                    onDismiss(); vm.openBrowser()
                })
                if (hasChat) {
                    add(PaletteRow.Entry("act_overview", Icons.Rounded.SpaceDashboard, t("打开当前对话概览"), t("查看产物、文件改动、终端和链接")) {
                        onNavigate("chat"); onDismiss(); onOpenOverview()
                    })
                }
                if (hasChat && curRunning) {
                    add(PaletteRow.Entry("act_stop", Icons.Rounded.Stop, t("停止当前运行"), t("中断正在执行的 Agent 任务")) {
                        vm.cancel(); onDismiss()
                    })
                }
                if (curConv != null) {
                    add(PaletteRow.Entry("act_pin", Icons.Rounded.PushPin,
                        if (curConv.pinned) t("取消置顶当前对话") else t("置顶当前对话"), curConv.title) {
                        vm.setPinned(curConv.id, !curConv.pinned); onDismiss()
                    })
                    add(PaletteRow.Entry("act_copy", Icons.Rounded.ContentCopy, t("复制当前对话全部内容"), curConv.title) {
                        vm.copyConversation(curConv.id); onDismiss()
                    })
                    add(PaletteRow.Entry("act_archive", Icons.Rounded.Archive, t("归档当前对话"), curConv.title) {
                        vm.setArchived(curConv.id, true); onDismiss()
                    })
                }
                add(PaletteRow.Entry("act_auto", Icons.Rounded.Schedule, t("打开自动化"), t("管理定时运行的后台任务")) {
                    onNavigate("automations"); onDismiss()
                })
                add(PaletteRow.Entry("act_cust", Icons.Rounded.Extension, t("打开自定义扩展"), t("插件市场、连接器与扩展状态")) {
                    onNavigate("customizations"); onDismiss()
                })
                add(PaletteRow.Entry("act_settings", Icons.Rounded.Settings, t("打开设置与额度"), t("额度、外观主题、推送和连接")) {
                    onNavigate("settings"); onDismiss()
                })
                val nextDark = when (theme.darkMode) { "light" -> "dark"; "dark" -> "system"; else -> "light" }
                val nextDarkLabel = when (nextDark) { "dark" -> t("深色"); "light" -> t("浅色"); else -> t("系统") }
                val curDarkLabel = when (theme.darkMode) { "dark" -> t("深色"); "light" -> t("浅色"); else -> t("系统") }
                add(PaletteRow.Entry("act_dark",
                    if (nextDark == "dark") Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                    "${t("切换外观模式")} → $nextDarkLabel", "${t("当前")}：$curDarkLabel") {
                    vm.updateTheme(theme.copy(darkMode = nextDark))
                    vm.showToast(nextDarkLabel)
                    onDismiss()
                })
                // 三种风格依次切换：默认 → Material You → Miuix
                fun styleLabel(s: String) = when (s) { "material" -> "Material You"; "miuix" -> "Miuix"; else -> t("默认") }
                val nextStyle = when (theme.style) { "default" -> "material"; "material" -> "miuix"; else -> "default" }
                val nextStyleLabel = styleLabel(nextStyle)
                add(PaletteRow.Entry("act_style", Icons.Rounded.Palette, "${t("切换主题风格")} → $nextStyleLabel",
                    "${t("当前")}：${styleLabel(theme.style)}") {
                    vm.updateTheme(theme.copy(style = nextStyle))
                    vm.showToast(nextStyleLabel)
                    onDismiss()
                })
                add(PaletteRow.Entry("act_refresh", Icons.Rounded.Refresh, t("刷新列表与同步状态"), t("重新拉取电脑端对话、项目与模型")) {
                    vm.refreshList(); vm.refreshProjects(); vm.loadModels(); vm.loadSync()
                    vm.showToast("已刷新")
                    onDismiss()
                })
            }.filter { matches(it.title, it.subtitle, it.key) }

            val shown = if (tab == "all" && q.isBlank()) actions.take(5) else actions
            if (shown.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_act", t("快捷操作")))
                out.addAll(shown)
            }
        }

        // 2. 对话（搜索时包含已归档对话）
        if (tab == "all" || tab == "conv") {
            val pool = if (q.isBlank() && tab == "all") list.filter { !it.archived } else list
            val matched = pool.filter { c ->
                matches(c.title, c.workspace, convProjectName[c.id] ?: "", if (c.archived) "归档 archived" else "")
            }
            val shown = if (tab == "all" && q.isBlank()) matched.take(5) else matched.take(30)
            if (shown.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_conv", if (q.isBlank()) t("最近对话") else "${t("对话")}（${matched.size}）"))
                shown.forEach { c ->
                    val proj = convProjectName[c.id]
                    val sub = listOfNotNull(proj, relTime(c.updated).takeIf { it.isNotBlank() }).joinToString(" · ")
                    out.add(PaletteRow.Entry(
                        key = "conv_${c.id}",
                        icon = Icons.Rounded.ChatBubbleOutline,
                        title = c.title,
                        subtitle = sub,
                        badge = if (c.archived) t("已归档") else "",
                        checked = c.id == chat.id,
                        conv = c,
                        onClick = { vm.openChat(c.id); onNavigate("chat"); onDismiss() },
                    ))
                }
            }
        }

        // 3. 斜杠命令
        if (tab == "all" || tab == "slash") {
            val matched = SLASH_COMMANDS.filter { matches(it.cmd, it.desc, it.keywords) }
            val shown = if (tab == "all" && q.isBlank()) matched.take(4) else matched
            if (shown.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_slash", t("斜杠命令")))
                shown.forEach { s ->
                    out.add(PaletteRow.Entry(
                        key = "slash_${s.cmd}",
                        icon = Icons.Rounded.Terminal,
                        title = s.cmd,
                        subtitle = s.desc,
                        mono = true,
                        onClick = {
                            vm.insertDraft(s.cmd + " ")
                            onNavigate("chat")
                            onDismiss()
                        },
                    ))
                }
            }
        }

        // 4. 项目
        if (tab == "all" || tab == "action") {
            val userProjects = projects.filter { !it.sidecar }.filter { p ->
                matches(p.name, p.folders.joinToString(" "), "项目 project")
            }
            val shown = if (tab == "all" && q.isBlank()) emptyList() else userProjects
            if (shown.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_proj", t("在项目中新建对话")))
                shown.forEach { p ->
                    out.add(PaletteRow.Entry(
                        key = "proj_${p.id}",
                        icon = Icons.Rounded.Folder,
                        title = p.name,
                        subtitle = p.folders.firstOrNull() ?: "${p.conversations.size}",
                        onClick = { vm.newChatInProject(p.id); onNavigate("chat"); onDismiss() },
                    ))
                }
            }
        }

        // 5. 模型与权限预设
        if (tab == "model" || (tab == "all" && q.isNotBlank())) {
            val matchedModels = models.filter { m -> matches(m.label, m.family, m.levelZh, m.desc, "模型 model") }
            if (matchedModels.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_model", t("切换模型")))
                matchedModels.forEach { m ->
                    val label = if (m.levelZh.isNotBlank()) "${m.family} · ${m.levelZh}" else m.family
                    out.add(PaletteRow.Entry(
                        key = "model_${m.model}",
                        icon = Icons.Rounded.AutoAwesome,
                        title = label,
                        subtitle = m.desc,
                        checked = m.model == selectedModel,
                        onClick = { vm.pickModel(m.model); vm.showToast(label); onDismiss() },
                    ))
                }
            }
            val matchedPresets = PALETTE_PRESETS.filter { (id, pair) ->
                matches(pair.first, pair.second, presetLabel(vm, id), "权限 permission preset")
            }
            if (matchedPresets.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_preset", t("权限预设（同步电脑）")))
                matchedPresets.forEach { (id, _) ->
                    val lbl = presetLabel(vm, id)
                    val on = preset == id || (preset.isBlank() && id.endsWith("DEFAULT"))
                    out.add(PaletteRow.Entry(
                        key = "preset_$id",
                        icon = Icons.Rounded.FastForward,
                        title = lbl,
                        checked = on,
                        onClick = { vm.setPreset(id); vm.showToast(lbl); onDismiss() },
                    ))
                }
            }
        }

        // 6. 连接器 & Skills
        if (tab == "ext" || (tab == "all" && q.isNotBlank())) {
            val conns = (connectors ?: emptyList()).filter { c -> matches(c.name, c.description, "插件 plugin 连接器 connector") }
            if (conns.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_conn", "@ ${t("插件")}"))
                conns.forEach { c ->
                    out.add(PaletteRow.Entry(
                        key = "conn_${c.kind}_${c.key}",
                        icon = Icons.Rounded.Extension,
                        title = c.name,
                        subtitle = c.description.ifBlank { if (c.connected == true) t("已连接") else "" },
                        onClick = {
                            vm.addMention(c)
                            vm.showToast("@${c.name}")
                            onNavigate("chat")
                            onDismiss()
                        },
                    ))
                }
            }
            val sks = skills.filter { s -> matches(s.name, s.description, vm.zh(s.description), "skill 技能 规则") }
            if (sks.isNotEmpty()) {
                out.add(PaletteRow.Section("sec_skill", t("技能与规则")))
                sks.forEach { s ->
                    out.add(PaletteRow.Entry(
                        key = "skill_${s.name}",
                        icon = Icons.Rounded.AutoStories,
                        title = s.name,
                        subtitle = vm.zh(s.description),
                        onClick = {
                            vm.insertDraft("请使用 ${s.name} skill：")
                            onNavigate("chat")
                            onDismiss()
                        },
                    ))
                }
            }
        }

        out
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss)
                .statusBarsPadding()
                .imePadding()
                .padding(horizontal = 14.dp, vertical = 18.dp),
            contentAlignment = Alignment.TopCenter,
        ) {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = sheetCardColor(),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 580.dp)
                    .clickable(enabled = false) {},
            ) {
                Column(Modifier.fillMaxWidth()) {
                    // 顶部搜索输入行
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Search, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            if (query.isEmpty()) {
                                Text(tr("搜索对话、命令、模型、插件…"),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            BasicTextField(
                                value = query,
                                onValueChange = { query = it },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                                textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                                cursorBrush = SolidColor(accentText(MaterialTheme.colorScheme.primary)),
                            )
                        }
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, "清空", Modifier.size(18.dp))
                            }
                        } else {
                            IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Close, "关闭", Modifier.size(20.dp))
                            }
                        }
                    }

                    // 分类筛选条
                    Row(
                        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        listOf(
                            "all" to tr("全部"),
                            "conv" to tr("对话"),
                            "action" to tr("操作"),
                            "slash" to tr("斜杠"),
                            "model" to tr("模型/权限"),
                            "ext" to tr("扩展"),
                        ).forEach { (id, lbl) ->
                                FilterChip(
                                    selected = tab == id,
                                    onClick = { tab = id },
                                    label = { Text(lbl, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false) },
                                )
                            }
                    }

                    HorizontalDivider(Modifier.padding(top = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    if (rows.isEmpty()) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                            Text(tr("没有匹配项"), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            Modifier.fillMaxWidth().weight(1f, fill = false),
                            contentPadding = PaddingValues(vertical = 6.dp, horizontal = 8.dp),
                        ) {
                            items(rows, key = { it.key }) { row ->
                                when (row) {
                                    is PaletteRow.Section -> {
                                        Text(
                                            row.title,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = accentText(MaterialTheme.colorScheme.primary),
                                            fontWeight = FontWeight.Medium,
                                            modifier = Modifier.padding(start = 12.dp, top = 10.dp, bottom = 4.dp),
                                        )
                                    }
                                    is PaletteRow.Entry -> {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable(onClick = row.onClick)
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                row.icon, null, Modifier.size(20.dp),
                                                tint = row.tint ?: if (row.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        row.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        fontFamily = if (row.mono) FontFamily.Monospace else FontFamily.Default,
                                                        fontWeight = if (row.checked || row.mono) FontWeight.SemiBold else FontWeight.Normal,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.weight(1f, fill = false),
                                                    )
                                                    if (row.badge.isNotBlank()) {
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                                        ) {
                                                            Text(row.badge, style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                                                        }
                                                    }
                                                }
                                                if (row.subtitle.isNotBlank()) {
                                                    Text(
                                                        row.subtitle,
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                            }
                                            val c = row.conv
                                            if (c != null) {
                                                Spacer(Modifier.width(8.dp))
                                                when {
                                                    c.waiting -> Icon(Icons.Rounded.TouchApp, "等待确认", Modifier.size(16.dp), tint = Amber)
                                                    isRunning(c.status) -> StatusDot(true)
                                                    c.unread -> Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                                    row.checked -> Icon(Icons.Rounded.Check, "当前", Modifier.size(18.dp), tint = iconInk())
                                                }
                                            } else if (row.checked) {
                                                Spacer(Modifier.width(8.dp))
                                                Icon(Icons.Rounded.Check, "当前", Modifier.size(18.dp), tint = iconInk())
                                            }
                                        }
                                    }
                                }
                            }
                            item(key = "pad_bottom") { Spacer(Modifier.height(4.dp)) }
                        }
                    }
                }
            }
        }
    }
}
