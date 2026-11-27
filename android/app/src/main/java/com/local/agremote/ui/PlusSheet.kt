package com.local.agremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.local.agremote.AppViewModel

/** 权限预设（Antigravity 的 AgentPermissionPreset），名字优先用 Antigravity 自带的中文。 */
private val PRESETS = listOf(
    "AGENT_PERMISSION_PRESET_DEFAULT" to ("Default" to "默认"),
    "AGENT_PERMISSION_PRESET_REQUEST_REVIEW" to ("Request Review" to "请求审核"),
    "AGENT_PERMISSION_PRESET_AUTO" to ("Auto" to "自动"),
    "AGENT_PERMISSION_PRESET_TURBO" to ("Turbo Mode" to "极速模式"),
)

fun presetLabel(vm: AppViewModel, p: String): String {
    val (_, fallback) = PRESETS.find { it.first == p }?.second ?: return I18n.t("默认")
    return I18n.t(fallback)
}

/** 「+」菜单：拍照 / 照片 / 文件，以及权限、插件、技能与规则、MCP服务。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlusSheet(vm: AppViewModel, onCamera: () -> Unit, onPhotos: () -> Unit, onFiles: () -> Unit,
              onInsert: (String) -> Unit, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val preset by vm.preset.collectAsState()
    val skills by vm.skills.collectAsState()
    val mcp by vm.mcp.collectAsState()
    val connectors by vm.connectors.collectAsState()
    val customizations by vm.customizations.collectAsState()
    val rules = remember(customizations) {
        (customizations ?: emptyList()).filter { it.type == "RULE" }
    }
    var page by remember { mutableStateOf("main") }
    var skillsTr by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.loadExtras(); vm.loadCustomizations() }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                IconButton(onClick = { if (page == "main") onClose() else page = "main" }, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(if (page == "main") Icons.Rounded.Close else Icons.AutoMirrored.Rounded.ArrowBack, "关闭")
                }
                Text(
                    when (page) {
                        "preset" -> tr("权限（和电脑同步）")
                        "plugins" -> tr("插件")
                        "skills" -> tr("技能与规则")
                        "mcp_servers" -> tr("MCP服务")
                        else -> tr("添加到会话")
                    },
                    Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                )
                if (page == "skills") {
                    androidx.compose.material3.TextButton(
                        onClick = { skillsTr = !skillsTr },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    ) {
                        Icon(Icons.Rounded.Translate, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(if (skillsTr) tr("原文") else tr("翻译"))
                    }
                }
            }
            Column(Modifier.noSheetOverscroll().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                when (page) {
                    "main" -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            BigButton(Icons.Rounded.PhotoCamera, tr("相机"), Modifier.weight(1f), onCamera)
                            BigButton(Icons.Rounded.Image, tr("照片"), Modifier.weight(1f), onPhotos)
                            BigButton(Icons.Rounded.UploadFile, tr("文件"), Modifier.weight(1f), onFiles)
                        }
                        Group {
                            NavItem(Icons.Rounded.FastForward, tr("权限"), presetLabel(vm, preset)) { page = "preset" }
                        }
                        Group {
                            NavItem(
                                Icons.Rounded.Extension,
                                tr("插件"),
                                connectors?.let { l -> if (l.isEmpty()) tr("未安装") else l.joinToString("、") { it.name } } ?: "",
                            ) { page = "plugins" }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            val srCount = skills.size + rules.size
                            NavItem(
                                Icons.Rounded.AutoStories,
                                tr("技能与规则"),
                                if (srCount == 0) "" else "${skills.size} ${tr("技能")} · ${rules.size} ${tr("规则")}",
                            ) { page = "skills" }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            NavItem(
                                Icons.Rounded.Dns,
                                tr("MCP服务"),
                                mcp?.let { l -> if (l.isEmpty()) tr("未配置") else "${l.count { it.enabled }} / ${l.size} ${tr("已启用")}" } ?: "",
                            ) { page = "mcp_servers" }
                        }
                    }
                    "preset" -> Group {
                        PRESETS.forEachIndexed { k, (id, _) ->
                            if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                            val on = preset == id || (preset.isBlank() && id.endsWith("DEFAULT"))
                            Row(Modifier.fillMaxWidth().clickable { vm.setPreset(id); page = "main" }.padding(horizontal = 20.dp, vertical = 16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(presetLabel(vm, id), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                                if (on) Icon(Icons.Rounded.Check, "已选", tint = iconInk())
                            }
                        }
                    }
                    "plugins" -> {
                        val list = connectors
                        when {
                            list == null -> Text(tr("读取中…"), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            list.isEmpty() -> Text(tr("未安装"),
                                Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Group {
                                list.forEachIndexed { k, c ->
                                    if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                    Row(Modifier.fillMaxWidth().clickable { vm.addMention(c); onClose() }
                                        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        ConnectorLogo(vm, c.logo)
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                                            val status = when {
                                                c.needsAuth -> tr("未配置")
                                                c.connected == true && c.account.isNotBlank() -> "${tr("已连接")} · ${c.account}"
                                                c.connected == true -> tr("已连接")
                                                c.connected == false -> tr("未配置")
                                                c.error.isNotBlank() -> c.error
                                                else -> c.description
                                            }
                                            Text(status, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                                color = if (c.needsAuth || c.connected == false || c.error.isNotBlank()) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        Text(tr("点一个插件，它会作为 @ 标签挂在输入框上方，发送时一起带给 agent（和电脑上 @ 插件一样）。"), Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    "skills" -> {
                        Text(tr("技能与规则由 agent 按需自动使用；点一项可插入到输入框中明确指定。"),
                            Modifier.padding(horizontal = 4.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (skills.isNotEmpty()) {
                            Text("${tr("技能")}（${skills.size}）", Modifier.padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelLarge, color = accentText(MaterialTheme.colorScheme.primary))
                            Group {
                                skills.forEachIndexed { k, s ->
                                    if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                    val (desc, _) = rememberGoogleTranslated(vm.zh(s.description), skillsTr)
                                    Column(Modifier.fillMaxWidth().clickable { onInsert("请使用 ${s.name} skill："); onClose() }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)) {
                                        Text(s.name, style = MaterialTheme.typography.titleSmall)
                                        Text(desc, style = MaterialTheme.typography.bodySmall, maxLines = 3,
                                            overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        if (rules.isNotEmpty()) {
                            Text("${tr("规则")}（${rules.size}）", Modifier.padding(horizontal = 4.dp),
                                style = MaterialTheme.typography.labelLarge, color = accentText(MaterialTheme.colorScheme.primary))
                            Group {
                                rules.forEachIndexed { k, r ->
                                    if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                    Column(Modifier.fillMaxWidth().clickable { onInsert("请遵循 ${r.name} 规则："); onClose() }
                                        .padding(horizontal = 20.dp, vertical = 12.dp)) {
                                        Text(r.name, style = MaterialTheme.typography.titleSmall)
                                        val sub = listOfNotNull(
                                            r.scope.takeIf { it.isNotBlank() },
                                            r.status.takeIf { it.isNotBlank() },
                                            r.error.takeIf { it.isNotBlank() },
                                        ).joinToString(" · ")
                                        if (sub.isNotBlank()) {
                                            Text(sub, style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        if (skills.isEmpty() && rules.isEmpty()) {
                            Text(tr("暂无技能或规则。"), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    "mcp_servers" -> {
                        val list = mcp
                        when {
                            list == null -> Text(tr("读取中…"), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            list.isEmpty() -> Text(tr("电脑端暂未配置 MCP 服务。"), Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            else -> Group {
                                list.forEachIndexed { k, srv ->
                                    if (k > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                                    Row(
                                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                                            Icon(Icons.Rounded.Dns, null, Modifier.padding(8.dp).size(20.dp))
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(srv.name, style = MaterialTheme.typography.titleMedium)
                                            val stText = when {
                                                srv.error.isNotBlank() -> srv.error
                                                !srv.enabled -> tr("已停用")
                                                srv.tools > 0 -> "${srv.tools} tools · ${tr("已启用")}"
                                                else -> srv.status.ifBlank { tr("已启用") }
                                            }
                                            Text(
                                                stText,
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                color = if (srv.error.isNotBlank()) MaterialTheme.colorScheme.error
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        AppSwitch(checked = srv.enabled, onCheckedChange = { vm.toggleMcp(srv.name, it) })
                                    }
                                }
                            }
                        }
                        Text(tr("这里的 MCP 服务开关与电脑端 Antigravity 实时同步。"), Modifier.padding(horizontal = 4.dp),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun BigButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, color = sheetCardColor(), shape = RoundedCornerShape(24.dp), modifier = modifier) {
        Column(Modifier.padding(vertical = 18.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
                Icon(icon, null, Modifier.padding(14.dp).size(26.dp))
            }
            Spacer(Modifier.size(10.dp))
            Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Surface(color = sheetCardColor(), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column { content() }
    }
}

@Composable
private fun NavItem(icon: ImageVector, title: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
            Icon(icon, null, Modifier.padding(10.dp).size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (value.isNotBlank()) Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


@Composable
private fun ConnectorLogo(vm: AppViewModel, url: String) {
    val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, url) { value = vm.logoBitmap(url) }
    val b = bmp
    if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)))
    else Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = CircleShape) {
        Icon(Icons.Rounded.Extension, null, Modifier.padding(8.dp).size(20.dp))
    }
}


/** @ 的连接器小标签：图标 + 名字，可选关闭按钮。输入框上方和已发消息里都用它。 */
@Composable
fun MentionChip(vm: AppViewModel, name: String, logo: String, onRemove: (() -> Unit)? = null) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, shape = RoundedCornerShape(50)) {
        Row(Modifier.padding(start = 8.dp, end = if (onRemove == null) 12.dp else 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            val bmp by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, logo) { value = if (logo.isBlank()) null else vm.logoBitmap(logo) }
            val b = bmp
            if (b != null) androidx.compose.foundation.Image(b.asImageBitmap(), null, Modifier.size(18.dp))
            else Icon(Icons.Rounded.Extension, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(name, style = MaterialTheme.typography.labelLarge, maxLines = 1)
            if (onRemove != null) IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Rounded.Close, "移除", Modifier.size(16.dp))
            }
        }
    }
}
