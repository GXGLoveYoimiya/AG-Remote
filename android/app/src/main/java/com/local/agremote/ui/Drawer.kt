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
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material.icons.rounded.SpaceDashboard
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.graphics.asImageBitmap
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

    val hosts by vm.hosts.collectAsState()
    val activeHostId by vm.activeHostId.collectAsState()
    val activeHost = hosts.find { it.id == activeHostId } ?: hosts.firstOrNull()
    var pickHostMenu by remember { mutableStateOf(false) }
    val avatar by vm.avatar.collectAsState()
    androidx.compose.runtime.LaunchedEffect(link) { if (avatar == null && link == com.local.agremote.Link.Online) vm.loadAvatar() }

    ModalDrawerSheet(Modifier.fillMaxHeight().width(316.dp), drawerContainerColor = MaterialTheme.colorScheme.surface) {
        // 顶部：标题 + 命令面板；下面一行是连接状态和当前电脑
        Column(Modifier.padding(start = 20.dp, end = 8.dp, top = 14.dp, bottom = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Antigravity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                IconButton(onClick = { onClose(); onCommandPalette() }) { Icon(Icons.Rounded.KeyboardCommandKey, tr("命令面板")) }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 左边是当前电脑，右边紧跟连接状态（绿点）
                if (activeHost != null) Box(Modifier.padding(end = 10.dp)) {
                    Surface(
                        onClick = { if (hosts.size > 1) pickHostMenu = true else onOpen("settings") },
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
                    DropdownMenu(pickHostMenu, { pickHostMenu = false }, shape = RoundedCornerShape(16.dp), containerColor = sheetCardColor()) {
                        hosts.forEach { h ->
                            DropdownMenuItem(
                                text = { Text(h.name.ifBlank { h.host }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingIcon = {
                                    if (h.id == activeHostId) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = iconInk())
                                },
                                onClick = { pickHostMenu = false; vm.switchHost(h.id); onClose() },
                            )
                        }
                    }
                }
                Box(Modifier.weight(1f)) { LinkLine(link) }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
        val listLoading by vm.listLoading.collectAsState()
        val listHasMore by vm.listHasMore.collectAsState()
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 10.dp, end = 10.dp, top = 4.dp, bottom = 80.dp)) {
            item {
                DrawerRow(Icons.Outlined.Inventory2, tr("项目")) { onOpen("projects") }
                DrawerRow(Icons.Rounded.Extension, tr("自定义")) { onOpen("customizations") }
                DrawerRow(Icons.Rounded.Schedule, tr("自动化")) { onOpen("automations") }
            }
            // 历史会话（全部对话，项目里的也在；按项目看在“项目”页）
            item {
                HorizontalDivider(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                Text(tr("历史会话"), Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(active, key = { it.id }) { c ->
                ConvItem(vm, c, selected = c.id == chat.id) { vm.openChat(c.id); onClose() }
            }
            if (active.isEmpty() && listLoading) {
                item(key = "drawer_loading") {
                    Box(Modifier.fillMaxWidth().padding(vertical = 20.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.2.dp)
                    }
                }
            } else if (active.isEmpty()) {
                item {
                    Text(tr("还没有对话"), Modifier.padding(12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (listHasMore) {
                item(key = "drawer_more") {
                    androidx.compose.runtime.LaunchedEffect(active.size) { vm.loadMoreConversations() }
                    Box(Modifier.fillMaxWidth().padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        androidx.compose.material3.CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.2.dp)
                    }
                }
            }
        }
        // 底部：头像（设置）+ 新建对话，压在一层渐变上，列表滑到下面时慢慢淡出（照 Claude）
        val bg = MaterialTheme.colorScheme.surface
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(96.dp)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(
                0f to bg.copy(alpha = 0f), 0.45f to bg.copy(alpha = 0.92f), 1f to bg)))
        Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Surface(onClick = { onOpen("settings") }, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(46.dp)) {
                val av = avatar
                if (av != null) androidx.compose.foundation.Image(av.asImageBitmap(), tr("设置"),
                    Modifier.fillMaxSize().clip(CircleShape), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
                else Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Settings, tr("设置")) }
            }
            Spacer(Modifier.weight(1f))
            // 新建对话：右下角一颗小胶囊（Material You 下绑定中间色 primaryContainer，其余主题保持 primary）
            val isM3 = LocalIsMaterialYou.current
            Button(onClick = { vm.newChat(); onClose() }, modifier = Modifier.height(46.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isM3) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.primary,
                    contentColor = if (isM3) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onPrimary,
                )) {
                Icon(Icons.Rounded.Add, null, Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text(tr("新建对话"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        }
    }
}

/** 侧边栏里的一行：图标 + 文字，选中时灰色圆角底。 */
@Composable
private fun DrawerRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean = false, onClick: () -> Unit) {
    val s = MaterialTheme.colorScheme.surface
    val isLight = (s.red * 0.299f + s.green * 0.587f + s.blue * 0.114f) > 0.5f
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
        .background(if (selected) MaterialTheme.colorScheme.surfaceContainerHighest else androidx.compose.ui.graphics.Color.Transparent)
        .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(22.dp), tint = if (isLight) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ProjectDialog(title: String, initName: String, showFolder: Boolean, onDismiss: () -> Unit, onOk: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initName) }
    var folder by remember { mutableStateOf("") }
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text(tr("名称")) })
                if (showFolder) OutlinedTextField(folder, { folder = it }, singleLine = true, label = { Text(tr("电脑上的文件夹（可不填）")) },
                    placeholder = { Text("D:/Projects/xxx") })
            }
        },
        confirmButton = { TextButton(onClick = { onOk(name.trim(), folder.trim()) }, enabled = name.isNotBlank()) { Text(tr("好")) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } },
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
            Text(c.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (c.unread) FontWeight.SemiBold else FontWeight.Normal)
            Spacer(Modifier.width(8.dp))
            // 右侧：距上次聊天多久（刚刚 / 5 分钟前 / 2 小时前…），后面跟状态标记
            Text(relTime(c.updated), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            if (c.waiting || isRunning(c.status) || c.unread || c.pinned) Spacer(Modifier.width(8.dp))
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
    onOverview: (() -> Unit)? = null,
) {
    var copyMenu by remember(c.id) { mutableStateOf(false) }
    var moveMenu by remember(c.id) { mutableStateOf(false) }
    var renaming by remember(c.id) { mutableStateOf(false) }
    var deleting by remember(c.id) { mutableStateOf(false) }
    val projects by vm.projects.collectAsState()

    DropdownMenu(expanded, onDismiss, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
        if (showNewChat) {
            DropdownMenuItem(text = { Text(tr("新建对话")) }, leadingIcon = { Icon(Icons.Rounded.EditNote, null) },
                onClick = { onDismiss(); vm.newChat() })
            if (onOverview != null) DropdownMenuItem(text = { Text(tr("概览")) }, leadingIcon = { Icon(Icons.Rounded.SpaceDashboard, null) },
                onClick = { onDismiss(); onOverview() })
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
    DropdownMenu(moveMenu, { moveMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
        val cur = projects.find { c.id in it.conversations }
        projects.filter { !it.sidecar }.forEach { p ->
            DropdownMenuItem(text = { Text(p.name) }, leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                trailingIcon = { if (p.id == cur?.id) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) },
                onClick = { moveMenu = false; if (p.id != cur?.id) vm.moveConversation(c.id, p.id) })
        }
        if (projects.none { !it.sidecar }) DropdownMenuItem(text = { Text(tr("还没有项目")) }, enabled = false, onClick = {})
        if (cur != null) DropdownMenuItem(text = { Text(tr("移出项目")) }, onClick = { moveMenu = false; vm.moveConversation(c.id, "") })
    }
    DropdownMenu(copyMenu, { copyMenu = false }, shape = RoundedCornerShape(20.dp), containerColor = sheetCardColor()) {
        DropdownMenuItem(text = { Text(tr("复制")) }, onClick = { copyMenu = false; vm.copyConversation(c.id) })
        DropdownMenuItem(text = { Text(tr("复制") + " · Title") }, onClick = { copyMenu = false; vm.copy(c.title) })
        DropdownMenuItem(text = { Text(tr("复制") + " · ID") }, onClick = { copyMenu = false; vm.copy(c.id) })
    }

    if (renaming) {
        var t by remember { mutableStateOf(c.title) }
        AppAlertDialog(
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
    AppAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("删除对话")) },
        text = { Text("「$title」") },
        confirmButton = { TextButton(onClick = onConfirm) { Text(tr("删除"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消")) } },
    )
}
