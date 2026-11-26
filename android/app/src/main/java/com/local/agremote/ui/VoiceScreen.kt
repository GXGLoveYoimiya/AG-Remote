package com.local.agremote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Description
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.local.agremote.AppViewModel
import com.local.agremote.R
import com.local.agremote.VoiceCall
import kotlinx.coroutines.flow.drop


private fun speedLabel(s: Double) = (if (s % 1.0 == 0.0) s.toInt().toString() else s.toString()) + "×"

/**
 * 实时语音（布局参考 Claude 移动端的语音模式）：
 * 开场是图标 + 问候；说起来后像聊天一样一问一答；没说完的话以斜体浮在麦克风上方；
 * 底部一层柔光随声音起伏（你说话时主题主色、AI 说话时第三色）；中间大麦克风（点按静音），
 * 底栏：模型、外放、挂断；右上角齿轮打开语音设置。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceCallScreen(vm: AppViewModel, call: VoiceCall) {
    val phase by call.phase.collectAsState()
    val lines by call.lines.collectAsState()
    val err by call.error.collectAsState()
    val mic by call.micLevel.collectAsState()
    val ai by call.aiLevel.collectAsState()
    val muted by call.muted.collectAsState()
    val speaker by call.speaker.collectAsState()
    val account by vm.account.collectAsState()
    val models by vm.models.collectAsState()
    val selected by vm.selected.collectAsState()
    var sheet by remember { mutableStateOf<String?>(null) } // settings / add / model

    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    val aiSpeaking = ai > 0.02f
    val connecting = phase == VoiceCall.Phase.Connecting
    val ended = phase == VoiceCall.Phase.Ended || phase == VoiceCall.Phase.Failed
    val finals = lines.filter { !it.interim }
    val interim = lines.lastOrNull { it.user && it.interim }?.text
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

    Dialog(onDismissRequest = { vm.endVoiceCall() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            BottomGlow(level = if (aiSpeaking) ai else mic, warm = aiSpeaking, active = !ended && !connecting, dark = dark)

            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                // 右上角：语音设置
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
                    CircleButton(Icons.Rounded.Settings, tr("语音设置"), size = 44) { sheet = "settings" }
                }

                Box(Modifier.weight(1f).fillMaxWidth()) {
                    if (finals.isEmpty()) {
                        // 开场：图标 + 问候；连接中略带模糊
                        Column(Modifier.align(Alignment.Center).padding(bottom = 60.dp).then(if (connecting) Modifier.blur(3.dp) else Modifier),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(painterResource(R.drawable.ic_fg), null, Modifier.size(96.dp))
                            val name = account?.name?.takeIf { it.isNotBlank() }
                            Text(when {
                                phase == VoiceCall.Phase.Failed -> err?.let { tr(it) } ?: tr("语音通话失败")
                                phase == VoiceCall.Phase.Ended -> tr("通话已结束")
                                name != null -> tr("来聊聊，{0}", name)
                                else -> tr("来聊聊")
                            }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center,
                                color = if (phase == VoiceCall.Phase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 32.dp))
                        }
                    } else {
                        val listState = rememberLazyListState()
                        LaunchedEffect(finals.size, finals.lastOrNull()?.text?.length) { listState.animateScrollToItem(finals.size - 1) }
                        LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            itemsIndexed(finals, key = { i, l -> "${l.user}-${l.seq}-$i" }) { _, l ->
                                if (l.user) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                    Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(20.dp),
                                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.widthIn(max = 300.dp)) {
                                        Text(l.text, Modifier.padding(horizontal = 14.dp, vertical = 10.dp), style = MaterialTheme.typography.bodyLarge)
                                    }
                                } else Column {
                                    Row {
                                        Icon(Icons.Rounded.GraphicEq, null, Modifier.padding(top = 3.dp).size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Spacer(Modifier.width(8.dp))
                                        Text(l.text, style = MaterialTheme.typography.bodyLarge)
                                    }
                                    IconButton(onClick = { vm.copy(l.text) }, modifier = Modifier.padding(start = 14.dp).size(32.dp)) {
                                        Icon(Icons.Rounded.ContentCopy, tr("复制"), Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }

                // 还没说完的话：斜体浮在麦克风上方
                Box(Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                    if (interim != null) Text(interim, style = MaterialTheme.typography.bodyLarge, fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1, modifier = Modifier.padding(horizontal = 32.dp))
                }

                // 大麦克风：点按静音 / 取消静音
                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                    val micOn = !muted
                    val scale by animateFloatAsState(1f + mic * 0.12f, tween(100), label = "mic")
                    Surface(
                        shape = CircleShape,
                        color = if (micOn) MaterialTheme.colorScheme.surfaceContainerLowest else MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        shadowElevation = if (micOn) 8.dp else 0.dp,
                        modifier = Modifier.size((64 * scale).dp)
                            .clickable(enabled = !ended) { call.setMuted(!muted) },
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(if (micOn) Icons.Rounded.Mic else Icons.Rounded.MicOff,
                                if (muted) tr("取消静音") else tr("静音"), Modifier.size(26.dp))
                        }
                    }
                }

                // 底栏：附件、模型、外放、挂断
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    CircleButton(Icons.Rounded.Add, tr("添加到会话"), size = 44, enabled = !ended) { sheet = "add" }
                    Spacer(Modifier.width(10.dp))
                    Surface(onClick = { sheet = "model" }, shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceContainerLowest,
                        shadowElevation = 3.dp, modifier = Modifier.height(44.dp)) {
                        Row(Modifier.padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(vm.modelChipLabel(selected), style = MaterialTheme.typography.labelLarge, maxLines = 1)
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(16.dp))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    CircleButton(if (speaker) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Hearing,
                        if (speaker) tr("扬声器") else tr("听筒"), size = 44, enabled = !ended) { call.setSpeaker(!speaker) }
                    Spacer(Modifier.width(10.dp))
                    Surface(onClick = { vm.endVoiceCall() }, shape = CircleShape,
                        color = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface, modifier = Modifier.size(48.dp)) {
                        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Close, tr("结束通话")) }
                    }
                }
            }
        }

        when (sheet) {
            "settings" -> SheetFrame(tr("语音设置"), onClose = { sheet = null }) { VoiceSettingsContent(vm, inCall = true) }
            "add" -> VoiceAddSheet(vm, onClose = { sheet = null })
            "model" -> ModelSheet(models, selected, onPick = { vm.pickVoiceModel(it) }, onClose = { sheet = null })
        }
    }
}

/** 底部柔光：颜色跟随 App 当前主题——你说话时用主色，AI 说话时用第三色；随音量起伏。 */
@Composable
private fun BottomGlow(level: Float, warm: Boolean, active: Boolean, dark: Boolean) {
    val strength by animateFloatAsState(if (active) 0.45f + level * 0.55f else 0.25f, tween(160), label = "glow")
    val listen = accent()
    val speak = MaterialTheme.colorScheme.tertiary
    val tint by animateFloatAsState(if (warm) 1f else 0f, tween(400), label = "tint")
    val c = androidx.compose.ui.graphics.lerp(listen, speak, tint)
    val a = strength * (if (dark) 0.5f else 0.6f)
    Canvas(Modifier.fillMaxSize()) {
        val h = size.height
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, c.copy(alpha = a * 0.55f), c.copy(alpha = a)),
            startY = h * 0.5f, endY = h))
        // AI 说话时两侧边缘也泛起暖光
        if (tint > 0.01f) {
            val r = size.width * 0.75f
            drawCircle(Brush.radialGradient(listOf(speak.copy(alpha = a * tint * 0.6f), Color.Transparent), Offset(0f, h), r), r, Offset(0f, h))
            drawCircle(Brush.radialGradient(listOf(speak.copy(alpha = a * tint * 0.6f), Color.Transparent), Offset(size.width, h), r), r, Offset(size.width, h))
        }
    }
}

@Composable
private fun VoiceAddSheet(vm: AppViewModel, onClose: () -> Unit) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    fun done(uris: List<android.net.Uri>) { if (uris.isNotEmpty()) { vm.sendVoiceAttachments(uris); onClose() } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { done(listOf(android.net.Uri.parse(it))) } }
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { done(it) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { done(it) }
    SheetFrame(tr("添加到会话"), onClose) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AddTile(Icons.Rounded.PhotoCamera, tr("相机"), Modifier.weight(1f)) {
                val u = com.local.agremote.Attachments.cameraUri(ctx); cameraUri = u.toString(); camera.launch(u)
            }
            AddTile(Icons.Rounded.PhotoLibrary, tr("照片"), Modifier.weight(1f)) {
                photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
            }
            AddTile(Icons.Rounded.Description, tr("文件"), Modifier.weight(1f)) { files.launch(arrayOf("*/*")) }
        }
    }
}

@Composable
private fun AddTile(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = sheetCardColor(), modifier = modifier.height(100.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(44.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(icon, null, Modifier.size(22.dp)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun CircleButton(icon: ImageVector, label: String, size: Int, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shadowElevation = 3.dp, modifier = Modifier.size(size.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, label, Modifier.size(22.dp)) }
    }
}

/** 底部面板的统一外框：左上角关闭、标题居中。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetFrame(title: String, onClose: () -> Unit, content: @Composable () -> Unit) {
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
            Box(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) { Icon(Icons.Rounded.Close, tr("关闭")) }
                Text(title, Modifier.align(Alignment.Center), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

/**
 * 语音设置（都是 Antigravity 自带的设置项，和电脑同步）：音色（左右滑动的卡片，滑到即试听并选用）、语速、音量。
 * 设置页和通话中都用这一块。
 */
@Composable
fun VoiceSettingsContent(vm: AppViewModel, inCall: Boolean = false) {
    val cfg by vm.voiceCfg.collectAsState()
    val s = cfg.settings
    val voices = cfg.voices
    // 离开语音设置（返回或关掉面板）时立刻停止试听，不等它说完
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { vm.stopPreview() } }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (voices.isNotEmpty()) {
            SectionLabel(tr("音色") + if (inCall) "（" + tr("下次通话生效") + "）" else "")
            val start = voices.indexOfFirst { it.name == s.name }.coerceAtLeast(0)
            val pager = rememberPagerState(initialPage = start) { voices.size }
            // 用户滑到一个音色：选用并试听（初次显示不算）
            LaunchedEffect(pager, voices) {
                snapshotFlow { pager.settledPage }.drop(1).collect { i ->
                    voices.getOrNull(i)?.let { v -> vm.saveVoice(name = v.name); vm.previewVoice(v.name) }
                }
            }
            HorizontalPager(pager, contentPadding = PaddingValues(horizontal = 80.dp), pageSpacing = 14.dp,
                modifier = Modifier.fillMaxWidth().height(170.dp)) { i ->
                val v = voices[i]
                val cur = i == pager.currentPage
                Surface(onClick = { if (cur) vm.previewVoice(v.name) }, shape = RoundedCornerShape(28.dp),
                    color = sheetCardColor(),
                    border = androidx.compose.foundation.BorderStroke(if (cur) 1.5.dp else 1.dp,
                        if (cur) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxSize().padding(vertical = if (cur) 0.dp else 18.dp)) {
                    Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        Text(v.name, style = MaterialTheme.typography.titleLarge, fontWeight = if (cur) FontWeight.SemiBold else FontWeight.Normal)
                        if (v.tone.isNotBlank()) Text(v.tone, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                voices.indices.forEach { i ->
                    Box(Modifier.padding(3.dp).size(6.dp).background(
                        if (i == pager.currentPage) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                }
            }
        }

        SectionLabel(tr("播放"))
        CardGroup {
            var speedMenu by remember { mutableStateOf(false) }
            ListRow(tr("语速"), value = speedLabel(s.speed), end = RowEnd.None, trailing = {
                Box {
                    Icon(Icons.Rounded.UnfoldMore, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        cfg.speeds.forEach { sp ->
                            DropdownMenuItem(text = { Text(speedLabel(sp)) }, onClick = { speedMenu = false; vm.saveVoice(speed = sp) },
                                trailingIcon = { if (sp == s.speed) Icon(Icons.Rounded.Check, null) })
                        }
                    }
                }
            }) { speedMenu = true }
            CardDivider(indent = false)
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Row {
                    Text(tr("音量"), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("${s.volume}%", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                var vol by remember(s.volume) { mutableFloatStateOf(s.volume.toFloat()) }
                AppSlider(value = vol, onValueChange = { vol = it }, onValueChangeFinished = { vm.saveVoice(volume = vol.toInt()) }, valueRange = 0f..100f)
            }
        }
    }
}

/** 设置页里的实时语音：电脑不支持时给出原因。 */
@Composable
fun VoiceSettingsCard(vm: AppViewModel) {
    val cfg by vm.voiceCfg.collectAsState()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (!cfg.available) CardBox {
            Text(tr("电脑上的 Antigravity 不支持实时语音（需要 2.35 或更新版本）"),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (cfg.voices.isNotEmpty()) VoiceSettingsContent(vm)
    }
}
