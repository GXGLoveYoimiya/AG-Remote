package com.local.agremote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.agremote.AppViewModel
import com.local.agremote.Project

/**
 * 项目页（侧边栏“项目”按钮进来，照 Claude 移动端）：搜索框 + 项目列表，右下角新建项目。
 * 点一个项目直接进入这个项目里的新对话；长按可以重命名、删除。
 */
@Composable
fun ProjectsScreen(vm: AppViewModel, onBack: () -> Unit, onOpenChat: () -> Unit) {
    val projects by vm.projects.collectAsState()
    val list by vm.list.collectAsState()
    val active = list.filter { !it.archived }
    // 自动化/插件自带的项目只在里面有对话时才显示（和电脑上一样）
    val shown = projects.filter { p -> !p.sidecar || active.any { it.id in p.conversations } }
    var q by rememberSaveable { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }
    val hits = shown.filter { q.isBlank() || it.name.contains(q, true) || it.folders.any { f -> f.contains(q, true) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = { AppTopBar(tr("项目"), onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { creating = true },
                containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(50), icon = { Icon(Icons.Rounded.Add, null) }, text = { Text(tr("新建项目")) })
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(tr("搜索项目")) }, shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest))
            if (hits.isEmpty()) {
                // 空状态：图标 + 一句说明，居中
                Column(Modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center) {
                    Icon(Icons.Outlined.Inventory2, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    Text(if (shown.isEmpty()) tr("创建项目，按主题或电脑上的文件夹整理你和 Antigravity 的对话。") else tr("没有匹配的项目"),
                        style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(80.dp))
                }
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp)) {
                item {
                    CardGroup {
                        hits.forEachIndexed { i, p ->
                            if (i > 0) CardDivider()
                            ProjectItem(vm, p, active.count { it.id in p.conversations }) { vm.newChatInProject(p.id); onOpenChat() }
                        }
                    }
                }
            }
        }
    }

    if (creating) ProjectDialog(title = tr("新建项目"), initName = "", showFolder = true, onDismiss = { creating = false }) { n, f ->
        creating = false; vm.createProject(n, f)
    }
}

/** 一个项目：图标 + 名字（+ 电脑上的文件夹）+ 对话数；长按重命名/删除。 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectItem(vm: AppViewModel, p: Project, count: Int, onClick: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current
    Box {
        Row(Modifier.fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = {
                if (!p.sidecar) { haptic.performHapticFeedback(HapticFeedbackType.LongPress); menu = true }
            })
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Inventory2, null, Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                p.folders.firstOrNull()?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (count > 0) Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(menu, { menu = false }, shape = RoundedCornerShape(20.dp)) {
            DropdownMenuItem(text = { Text(tr("重命名")) }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                onClick = { menu = false; renaming = true })
            DropdownMenuItem(text = { Text(tr("删除项目"), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                onClick = { menu = false; deleting = true })
        }
    }
    if (renaming) ProjectDialog(tr("重命名项目"), p.name, showFolder = false, onDismiss = { renaming = false }) { n, _ ->
        renaming = false; vm.renameProject(p.id, n)
    }
    if (deleting) AppAlertDialog(
        onDismissRequest = { deleting = false },
        title = { Text(tr("删除项目？")) },
        text = { Text("「${p.name}」" + tr("这个项目会被删除。项目里的对话不会删，会回到历史会话里；电脑上的文件夹也不会动。")) },
        confirmButton = { TextButton(onClick = { deleting = false; vm.deleteProject(p.id) }) {
            Text(tr("删除"), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { deleting = false }) { Text(tr("取消")) } },
    )
}
