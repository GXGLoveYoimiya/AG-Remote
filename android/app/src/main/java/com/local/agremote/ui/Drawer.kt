package com.local.agremote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Archive
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.MarkChatRead
import androidx.compose.material.icons.rounded.MarkChatUnread
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.KeyboardCommandKey
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.IconButton
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import com.local.agremote.AppViewModel
import com.local.agremote.Conversation

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DrawerContent(vm: AppViewModel, onClose: () -> Unit, onOpen: (String) -> Unit, onCommandPalette: () -> Unit) {
    val list by vm.list.collectAsState()
    val link by vm.link.collectAsState()
    val chat by vm.chat.collectAsState()
    val projects by vm.projects.collectAsState()
    val active = list.filter { !it.archived }
    // 自动化/插件自带的项目只在里面有对话时才显示
    val shownProjects = projects.filter { p -> !p.sidecar || active.any { it.id in p.conversations } }
    val inProject = shownProjects.flatMap { it.conversations }.toSet()
    val loose = active.filter { it.id !in inProject }
    val expanded = remember { androidx.compose.runtime.mutableStateMapOf<String, Boolean>() }
    var newProject by remember { mutableStateOf(false) }

    val hosts by vm.hosts.collectAsState()
    val activeHostId by vm.activeHostId.collectAsState()
    val activeHost = hosts.find { it.id == activeHostId } ?: hosts.firstOrNull()
    var pickHostMenu by remember { mutableStateOf(false) }

    ModalDrawerSheet(Modifier.fillMaxHeight().width(316.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        // 顶部：标题 + 命令面板；下面一行是连接状态和当前电脑
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Antigravity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onClose(); onCommandPalette() }) { Icon(Icons.Rounded.KeyboardCommandKey, tr("命令面板")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { LinkLine(link) }
                if (activeHost != null) Box(Modifier.padding(end = 8.dp)) {
                    Surface(
                        onClick = { if (hosts.size > 1) pickHostMenu = true else { onOpen("settings"); onClose() } },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Computer, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(6.dp))
                            Text(activeHost.name.ifBlank { activeHost.host }, style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 110.dp))
                            if (hosts.size > 1) Icon(Icons.Rounded.ExpandMore, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    DropdownMenu(pickHostMenu, { pickHostMenu = false }, shape = RoundedCornerShape(16.dp)) {
                        hosts.forEach { h ->
                            DropdownMenuItem(
                                text = { Text(h.name.ifBlank { h.host }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    if (h.id == activeHostId) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                                },
                                onClick = { pickHostMenu = false; vm.switchHost(h.id); onClose() },
                            )
                        }
                    }
                }
            }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
            item {
                DrawerRow(Icons.Rounded.Schedule, tr("自动化")) { onOpen("automations"); onClose() }
                DrawerRow(Icons.Rounded.Extension, tr("自定义扩展")) { onOpen("customizations"); onClose() }
            }
            // 项目
            item {
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr("项目"), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    IconButton(onClick = { newProject = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.CreateNewFolder, tr("新建项目"), Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (shownProjects.isEmpty()) item {
                Text(tr("还没有项目"), Modifier.padding(start = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline)
            }
            shownProjects.forEach { p ->
                val open = expanded[p.id] ?: (chat.id != null && chat.id in p.conversations)
                val convs = active.filter { it.id in p.conversations }
                item(key = "p_" + p.id) {
                    ProjectRow(vm, p, open, convs.size, onToggle = { expanded[p.id] = !open },
                        onNewChat = { vm.newChatInProject(p.id); onClose() })
                }
                if (open) {
                    items(convs, key = { "pc_" + it.id }) { c ->
                        Box(Modifier.padding(start = 20.dp)) {
                            ConvItem(vm, c, selected = c.id == chat.id) { vm.openChat(c.id); onClose() }
                        }
                    }
                    if (convs.isEmpty()) item(key = "pe_" + p.id) {
                        Text(tr("还没有对话"), Modifier.padding(start = 44.dp, bottom = 6.dp), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
            // 历史会话（不在任何项目里的）
            item {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(tr("历史会话"), Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(loose, key = { it.id }) { c ->
                ConvItem(vm, c, selected = c.id == chat.id) { vm.openChat(c.id); onClose() }
            }
            if (loose.isEmpty()) item {
                Text(tr("还没有对话"), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 底部：设置 + 新建对话
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = { onOpen("settings"); onClose() }, modifier = Modifier.size(48.dp),
                colors = IconButtonDefaults.filledTonalIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
                Icon(Icons.Rounded.Settings, tr("设置"))
            }
            Spacer(Modifier.width(10.dp))
            Button(onClick = { vm.newChat(); onClose() }, modifier = Modifier.weight(1f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.surface)) {
                Icon(Icons.Rounded.EditNote, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(tr("新建对话"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }

    if (newProject) ProjectDialog(title = "新建项目", initName = "", showFolder = true, onDismiss = { newProject = false }) { n, f ->
        newProject = false; vm.createProject(n, f)
    }
}

/** 侧边栏里的一行：图标 + 文字，选中时灰色圆角底。 */
@Composable
private fun DrawerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp))
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(vm: AppViewModel, p: com.local.agremote.Project, open: Boolean, count: Int, onToggle: () -> Unit, onNewChat: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onToggle, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
            .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (open) Icons.Rounded.FolderOpen else Icons.Rounded.Folder, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                p.folders.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (count > 0) Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            IconButton(onClick = onNewChat, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Rounded.Add, "在这个项目里新建对话", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(text = { Text("在项目中新建对话") }, leadingIcon = { Icon(Icons.Rounded.Add, null) }, onClick = { menu = false; onNewChat() })
            DropdownMenuItem(text = { Text("重命名") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
            DropdownMenuItem(text = { Text("删除项目", color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
        }
    }
    if (renaming) ProjectDialog("重命名项目", p.name, showFolder = false, onDismiss = { renaming = false }) { n, _ ->
        renaming = false; vm.renameProject(p.id, n)
    }
    if (deleting) AlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text("删除项目？") },
        text = { Text("「${p.name}」这个项目会被删除。项目里的对话不会删，会回到历史会话里；电脑上的文件夹也不会动。") },
        confirmButton = { TextButton(onClick = { deleting = false; vm.deleteProject(p.id) }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text("取消") } },
    )
}

@Composable
private fun ProjectDialog(title: String, initName: String, showFolder: Boolean, onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initName) }
    var folder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("名称") })
                if (showFolder) OutlinedTextField(folder, { folder = it }, singleLine = true, label = { Text("电脑上的文件夹（可不填）") },
                    placeholder = { Text("D:/Projects/xxx") })
            }
        },
        confirmButton = { TextButton(onClick = { onOk(name.trim(), folder.trim()) }, enabled = name.isNotBlank()) { Text("好") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConvItem(vm: AppViewModel, c: Conversation, selected: Boolean, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    val running = isRunning(c.status)

    Box {
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true })
            .padding(start = 12.dp, end = 12.dp, top = 11.dp, bottom = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal)
            Spacer(Modifier.width(8.dp))
            when {
                c.waiting -> Icon(Icons.Rounded.TouchApp, "等待确认", Modifier.size(16.dp), tint = Amber)
                running -> StatusDot(true)
                c.unread -> Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                c.pinned -> Icon(Icons.Rounded.PushPin, "已置顶", Modifier.size(14.dp), tint = MaterialTheme.colorScheme.outline)
            }
        }
        ConvActionMenus(vm, c, expanded = menu, onDismiss = { menu = false })
    }
}

/** 对话操作菜单（侧边栏长按和聊天页右上角「⋮」共用）：重命名、置顶、已读/未读、复制、移到项目、归档、删除。 */
@Composable
fun ConvActionMenus(
    vm: AppViewModel,
    c: Conversation,
    expanded: Boolean,
    onDismiss: () -> Unit,
    showNewChat: Boolean = false,
) {
    var copyMenu by remember(c.id) { mutableStateOf(false) }
    var moveMenu by remember(c.id) { mutableStateOf(false) }
    var renaming by remember(c.id) { mutableStateOf(false) }
    var deleting by remember(c.id) { mutableStateOf(false) }
    val projects by vm.projects.collectAsState()

    DropdownMenu(expanded, onDismiss, shape = RoundedCornerShape(20.dp)) {
        if (showNewChat) {
            DropdownMenuItem(text = { Text(tr("新建对话")) }, leadingIcon = { Icon(Icons.Rounded.EditNote, null) },
                onClick = { onDismiss(); vm.newChat() })
            HorizontalDivider()
        }
        DropdownMenuItem(text = { Text(tr("重命名")) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
            onClick = { onDismiss(); renaming = true })
        DropdownMenuItem(text = { Text(if (c.pinned) tr("取消置顶") else tr("置顶")) },
            leadingIcon = { Icon(if (c.pinned) Icons.Outlined.PushPin else Icons.Rounded.PushPin, null) },
            onClick = { onDismiss(); vm.setPinned(c.id, !c.pinned) })
        DropdownMenuItem(text = { Text(if (c.unread) tr("标为已读") else tr("标为未读")) },
            leadingIcon = { Icon(if (c.unread) Icons.Rounded.MarkChatRead else Icons.Rounded.MarkChatUnread, null) },
            onClick = { onDismiss(); vm.setUnread(c.id, !c.unread) })
        HorizontalDivider()
        DropdownMenuItem(text = { Text(tr("复制")) }, leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
            trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
            onClick = { onDismiss(); copyMenu = true })
        DropdownMenuItem(text = { Text(tr("移动到项目")) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.DriveFileMove, null) },
            trailingIcon = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null) },
            onClick = { onDismiss(); moveMenu = true })
        HorizontalDivider()
        DropdownMenuItem(text = { Text(tr("归档")) }, leadingIcon = { Icon(Icons.Rounded.Archive, null) },
            onClick = { onDismiss(); vm.setArchived(c.id, true) })
        DropdownMenuItem(text = { Text(tr("删除"), color = MaterialTheme.colorScheme.error) },
            leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
            onClick = { onDismiss(); deleting = true })
    }
    DropdownMenu(moveMenu, { moveMenu = false }, shape = RoundedCornerShape(20.dp)) {
        val cur = projects.find { c.id in it.conversations }
        projects.filter { !it.sidecar }.forEach { p ->
            DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                trailingIcon = { if (p.id == cur?.id) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                onClick = { moveMenu = false; if (p.id != cur?.id) vm.moveConversation(c.id, p.id) })
        }
        if (projects.none { !it.sidecar }) DropdownMenuItem(text = { Text(tr("还没有项目")) }, enabled = false, onClick = {})
        if (cur != null) DropdownMenuItem(text = { Text(tr("移出项目")) }, onClick = { moveMenu = false; vm.moveConversation(c.id, "") })
    }
    DropdownMenu(copyMenu, { copyMenu = false }, shape = RoundedCornerShape(20.dp)) {
        DropdownMenuItem(text = { Text(tr("复制")) }, onClick = { copyMenu = false; vm.copyConversation(c.id) })
        DropdownMenuItem(text = { Text(tr("复制") + " · Title") }, onClick = { copyMenu = false; vm.copy(c.title) })
        DropdownMenuItem(text = { Text(tr("复制") + " · ID") }, onClick = { copyMenu = false; vm.copy(c.id) })
    }

    if (renaming) {
        var t by remember { mutableStateOf(c.title) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text(tr("重命名")) },
            text = { OutlinedTextField(t, { t = it }, singleLine = true) },
            confirmButton = { TextButton(onClick = { renaming = false; vm.rename(c.id, t) }, enabled = t.isNotBlank()) { Text(tr("保存")) } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text(tr("取消")) } },
        )
    }
    if (deleting) DeleteDialog(c.title, onDismiss = { deleting = false }) { deleting = false; vm.delete(c.id) }
}

@Composable
fun DeleteDialog(title: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("删除对话")) },
        text = { Text("「$title」") },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("删除"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } },
    )
}
