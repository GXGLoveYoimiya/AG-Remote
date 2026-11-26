package com.local.agremote.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CallEnd
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Hearing
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MicOff
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.local.agremote.AppViewModel
import com.local.agremote.VoiceCall
import kotlinx.coroutines.delay

private fun speedLabel(s: Double) = (if (s % 1.0 == 0.0) s.toInt().toString() else s.toString()) + "×"

/** 全屏通话界面：随声音起伏的光球、状态、实时字幕、静音/外放/语速/音色/挂断。 */
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
    val startedAt by call.startedAt.collectAsState()
    val cfg by vm.voiceCfg.collectAsState()
    var settingsOpen by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase) { while (phase == VoiceCall.Phase.Listening || phase == VoiceCall.Phase.Connecting) { now = System.currentTimeMillis(); delay(500) } }

    // 通话中屏幕常亮
    val view = LocalView.current
    DisposableEffect(Unit) { view.keepScreenOn = true; onDispose { view.keepScreenOn = false } }

    val aiSpeaking = ai > 0.02f
    val ended = phase == VoiceCall.Phase.Ended || phase == VoiceCall.Phase.Failed
    val status = when {
        phase == VoiceCall.Phase.Failed -> err?.let { tr(it) } ?: tr("语音通话失败")
        phase == VoiceCall.Phase.Ended -> tr("通话已结束")
        phase == VoiceCall.Phase.Connecting -> tr("正在连接…")
        phase == VoiceCall.Phase.Retrying -> tr("网络不稳，正在重连…")
        aiSpeaking -> tr("正在说")
        muted -> tr("已静音")
        else -> tr("在听")
    }
    val secs = ((now - startedAt) / 1000).coerceAtLeast(0)

    Dialog(onDismissRequest = { vm.endVoiceCall() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(12.dp))
                Text(tr("实时语音"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(listOfNotNull(cfg.settings.name.ifBlank { null }, speedLabel(cfg.settings.speed),
                    if (!ended && phase != VoiceCall.Phase.Connecting) "%d:%02d".format(secs / 60, secs % 60) else null).joinToString(" · "),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // 光球：AI 说话时跟着 AI 的音量，否则跟着你的音量
                Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
                    Orb(level = if (aiSpeaking) ai else mic, active = !ended && phase != VoiceCall.Phase.Connecting, aiSpeaking = aiSpeaking)
                }
                Text(status, style = MaterialTheme.typography.titleMedium,
                    color = if (phase == VoiceCall.Phase.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))

                // 实时字幕
                val listState = rememberLazyListState()
                LaunchedEffect(lines.size, lines.lastOrNull()?.text?.length) { if (lines.isNotEmpty()) listState.animateScrollToItem(lines.size - 1) }
                LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (lines.isEmpty() && !ended) item {
                        Text(tr("开始说话吧，说完稍停一下 AI 就会回答；AI 说话时你可以直接打断。"),
                            Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                    }
                    itemsIndexed(lines, key = { i, l -> "${l.user}-${l.seq}-$i" }) { _, l ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (l.user) Arrangement.End else Arrangement.Start) {
                            Surface(
                                color = if (l.user) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = RoundedCornerShape(18.dp), modifier = Modifier.widthIn(max = 300.dp),
                            ) {
                                Text(l.text, Modifier.padding(horizontal = 14.dp, vertical = 9.dp), style = MaterialTheme.typography.bodyLarge,
                                    color = if (l.user) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = if (l.interim) 0.6f else 1f)
                                    else MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // 控制按钮
                Row(Modifier.fillMaxWidth().padding(vertical = 18.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically) {
                    RoundAction(if (muted) Icons.Rounded.MicOff else Icons.Rounded.Mic, if (muted) tr("取消静音") else tr("静音"),
                        on = muted, enabled = !ended) { call.setMuted(!muted) }
                    RoundAction(if (speaker) Icons.AutoMirrored.Rounded.VolumeUp else Icons.Rounded.Hearing, if (speaker) tr("扬声器") else tr("听筒"),
                        on = !speaker, enabled = !ended) { call.setSpeaker(!speaker) }
                    RoundAction(Icons.Rounded.RecordVoiceOver, tr("语音设置"), on = false, enabled = true) { settingsOpen = true }
                    FilledIconButton(
                        onClick = { vm.endVoiceCall() }, modifier = Modifier.size(64.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFFE5484D), contentColor = Color.White),
                    ) { Icon(Icons.Rounded.CallEnd, tr("结束通话"), Modifier.size(30.dp)) }
                }
            }
        }
        if (settingsOpen) {
            ModalBottomSheet(onDismissRequest = { settingsOpen = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
                Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                    Text(tr("语音设置"), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    VoiceSettingsContent(vm, inCall = true)
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, on: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(56.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = if (on) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurface)) {
            Icon(icon, label)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Orb(level: Float, active: Boolean, aiSpeaking: Boolean) {
    val target = if (active) 0.55f + level * 0.45f else 0.5f
    val scale by animateFloatAsState(target, tween(120), label = "orb")
    var breathe by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) { var t = 0f; while (true) { t += 0.05f; breathe = (kotlin.math.sin(t) + 1f) / 2f; delay(40) } }
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val inner = if (aiSpeaking) tertiary else primary
    Canvas(Modifier.size(220.dp)) {
        val r = size.minDimension / 2f * (scale + (if (active) 0.03f * breathe else 0f))
        drawCircle(Brush.radialGradient(listOf(inner.copy(alpha = 0.25f), Color.Transparent), center, r * 1.25f), r * 1.25f)
        drawCircle(Brush.radialGradient(listOf(inner, primary.copy(alpha = 0.85f), primary.copy(alpha = 0.55f)),
            center + Offset(-r * 0.25f, -r * 0.3f), r * 1.4f), r * 0.82f)
    }
}

/** 音色、语速、音量：设置页和通话中都用这一块；改动会写回电脑上的 Antigravity。 */
@Composable
fun VoiceSettingsContent(vm: AppViewModel, inCall: Boolean = false) {
    val cfg by vm.voiceCfg.collectAsState()
    val previewing by vm.previewing.collectAsState()
    val s = cfg.settings
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(tr("音色") + if (inCall) "（" + tr("下次通话生效") + "）" else "", style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(18.dp)) {
            Column {
                cfg.voices.forEach { v ->
                    val selected = v.name == s.name || (s.name.isBlank() && v == cfg.voices.first())
                    Row(Modifier.fillMaxWidth().clickable { vm.saveVoice(name = v.name) }.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { vm.previewVoice(v.name) }, modifier = Modifier.size(36.dp)) {
                            Icon(if (previewing == v.name) Icons.Rounded.Stop else Icons.Rounded.PlayArrow, tr("试听"),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(v.name, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                            if (v.tone.isNotBlank()) Text(v.tone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
        Text(tr("语速"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            cfg.speeds.forEach { sp ->
                FilterChip(selected = sp == s.speed, onClick = { vm.saveVoice(speed = sp) }, label = { Text(speedLabel(sp)) })
            }
        }
        Text(tr("音量") + "  ${s.volume}%", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        var vol by remember(s.volume) { mutableFloatStateOf(s.volume.toFloat()) }
        Slider(value = vol, onValueChange = { vol = it }, onValueChangeFinished = { vm.saveVoice(volume = vol.toInt()) }, valueRange = 0f..100f)
    }
}

/** 设置页里的一块：电脑不支持时给出原因。 */
@Composable
fun VoiceSettingsCard(vm: AppViewModel) {
    val cfg by vm.voiceCfg.collectAsState()
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            if (!cfg.available) {
                Text(tr("电脑上的 Antigravity 不支持实时语音（需要 2.35 或更新版本）"), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (cfg.voices.isEmpty()) return@Column
                Spacer(Modifier.height(10.dp))
            }
            VoiceSettingsContent(vm)
        }
    }
}
