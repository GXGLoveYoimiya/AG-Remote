package com.local.agremote.ui

import androidx.compose.foundation.layout.navigationBarsPadding
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// 全 App 共用的一套设计组件（参照 Claude 移动端）：居中标题的顶栏、灰色小节标题、
// 卡片里的分组列表（图标 + 标题 + 说明 + 右侧值/箭头/开关）。所有主题风格共用，颜色来自当前主题。

/** 顶栏：标题居中、左侧返回，底色和页面一致。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(title: String, onBack: (() -> Unit)?, actions: @Composable RowScope.() -> Unit = {}) {
    CenterAlignedTopAppBar(
        title = { Text(title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("返回")) } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surface),
    )
}

/** 小节标题：灰色小字。 */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier.padding(start = 6.dp, top = 10.dp, bottom = 2.dp), style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 一组列表行：卡片底色 + 圆角，里面的行用 [CardDivider] 分开。 */
@Composable
fun CardGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = sheetCardColor(), shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(content = content)
    }
}

/** 自由内容的卡片（带内边距）。 */
@Composable
fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = sheetCardColor(), shape = RoundedCornerShape(20.dp), modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp), content = content)
    }
}

@Composable
fun CardDivider(indent: Boolean = true) {
    HorizontalDivider(Modifier.padding(start = if (indent) 52.dp else 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
}

/** 行右侧：箭头（进二级页）、开关、勾选，或什么都不放。 */
sealed interface RowEnd {
    data object Chevron : RowEnd
    data object None : RowEnd
    data class Toggle(val on: Boolean, val onChange: (Boolean) -> Unit) : RowEnd
    data class Checked(val on: Boolean) : RowEnd
}

/** 列表行：图标 + 标题（+ 灰色说明）+ 右侧当前值 + 箭头/开关/勾选。 */
@Composable
fun ListRow(
    title: String,
    icon: ImageVector? = null,
    desc: String? = null,
    value: String? = null,
    end: RowEnd = RowEnd.Chevron,
    titleColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
) {
    val click = onClick ?: (end as? RowEnd.Toggle)?.let { t -> { t.onChange(!t.on) } }
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
    val valueMax = maxWidth * 0.4f // 右边的值最多占这一行四成宽，放不下就省略，标题不会被挤
    Row(Modifier.fillMaxWidth().then(if (click != null) Modifier.clickable(onClick = click) else Modifier)
        .padding(horizontal = 16.dp, vertical = if (desc.isNullOrBlank()) 15.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            val s = MaterialTheme.colorScheme.surface
            val isLight = (s.red * 0.299f + s.green * 0.587f + s.blue * 0.114f) > 0.5f
            val iconTint = if (titleColor == MaterialTheme.colorScheme.onSurface) {
                if (isLight) androidx.compose.ui.graphics.Color.Black else MaterialTheme.colorScheme.onSurface
            } else titleColor
            Icon(icon, null, Modifier.size(22.dp), tint = iconTint)
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            if (!desc.isNullOrBlank()) Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!value.isNullOrBlank()) Text(value, Modifier.padding(start = 8.dp).widthIn(max = valueMax), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        trailing?.invoke()
        when (end) {
            RowEnd.Chevron -> Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            is RowEnd.Toggle -> { Spacer(Modifier.width(8.dp)); AppSwitch(checked = end.on, onCheckedChange = end.onChange) }
            is RowEnd.Checked -> if (end.on) Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = iconInk())
            RowEnd.None -> {}
        }
    }
    }
}

/** 用量卡片：名称 + “已使用 xx%” + 进度条 + 重置时间（快用完时变红）。 */
@Composable
fun UsageCard(name: String, usedFraction: Double, resetText: String, desc: String = "") {
    val pct = (usedFraction * 100).toInt().coerceIn(0, 100)
    val color = if (pct >= 90) MaterialTheme.colorScheme.error else accent()
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Text(tr("已使用 {0}%", pct), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().height(8.dp),
            color = color, trackColor = color.copy(alpha = 0.18f), strokeCap = StrokeCap.Round, gapSize = 0.dp, drawStopIndicator = {})
        if (resetText.isNotBlank()) Text(resetText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (desc.isNotBlank()) Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 底部的撤销提示：深色圆角条 + “撤销”按钮，几秒后自动消失。 */
@Composable
fun UndoBar(vm: com.local.agremote.AppViewModel, modifier: Modifier = Modifier) {
    val host = androidx.compose.runtime.remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val undoLabel = tr("撤销")
    // 普通提示也用同一个提示条（不用系统自带的 Toast）
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.toast.collect { msg ->
            host.currentSnackbarData?.dismiss()
            scope.launch { host.showSnackbar(I18n.translateToast(msg), duration = androidx.compose.material3.SnackbarDuration.Short) }
        }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        vm.undo.collect { u ->
            host.currentSnackbarData?.dismiss() // 新的顶掉旧的
            scope.launch {
                val r = host.showSnackbar(I18n.t(I18n.currentLang, u.message), actionLabel = undoLabel, duration = androidx.compose.material3.SnackbarDuration.Short)
                if (r == androidx.compose.material3.SnackbarResult.ActionPerformed) u.undo()
            }
        }
    }
    androidx.compose.material3.SnackbarHost(host, modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 96.dp)) { d ->
        androidx.compose.material3.Snackbar(d, shape = RoundedCornerShape(16.dp),
            containerColor = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            actionColor = MaterialTheme.colorScheme.inversePrimary)
    }
}

/** 统一的对话框：白色卡片底、大圆角，所有语言统一左对齐标题/内容、单行右对齐操作栏（避免德语/俄语等长文案换行错位）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    properties: androidx.compose.ui.window.DialogProperties = androidx.compose.ui.window.DialogProperties(),
) {
    androidx.compose.material3.BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        properties = properties,
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = sheetCardColor(),
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 360.dp),
        ) {
            Column(Modifier.fillMaxWidth().padding(24.dp)) {
                if (icon != null) {
                    androidx.compose.foundation.layout.Box(
                        Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) { icon() }
                }
                if (title != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurface,
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.headlineSmall.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        ),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().padding(bottom = 16.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { title() }
                    }
                }
                if (text != null) {
                    androidx.compose.runtime.CompositionLocalProvider(
                        androidx.compose.material3.LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant,
                        androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.bodyMedium.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Start,
                        ),
                    ) {
                        androidx.compose.foundation.layout.Box(
                            Modifier.fillMaxWidth().weight(1f, fill = false).padding(bottom = 24.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) { text() }
                    }
                }
                androidx.compose.runtime.CompositionLocalProvider(
                    androidx.compose.material3.LocalContentColor provides accentText(MaterialTheme.colorScheme.primary),
                    androidx.compose.material3.LocalTextStyle provides MaterialTheme.typography.labelLarge,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** 统一的开关：滑块圆点始终为纯白色（避免浅色强调色下 onPrimary 为黑色导致滑块变成黑圆）。 */
@Composable
fun AppSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        colors = androidx.compose.material3.SwitchDefaults.colors(
            checkedThumbColor = androidx.compose.ui.graphics.Color.White,
            checkedTrackColor = MaterialTheme.colorScheme.primary,
            uncheckedThumbColor = androidx.compose.ui.graphics.Color.White,
            uncheckedBorderColor = MaterialTheme.colorScheme.outlineVariant,
        ),
    )
}

/** 统一的滑块：圆形白色滑块头 + 圆头轨道（不用新版的竖条滑块头）。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    onValueChangeFinished: (() -> Unit)? = null,
    color: androidx.compose.ui.graphics.Color = accent(),
) {
    val colors = androidx.compose.material3.SliderDefaults.colors(thumbColor = androidx.compose.ui.graphics.Color.White,
        activeTrackColor = color, inactiveTrackColor = color.copy(alpha = 0.18f))
    val outline = MaterialTheme.colorScheme.outlineVariant
    androidx.compose.material3.Slider(
        value = value, onValueChange = onValueChange, modifier = modifier, valueRange = valueRange,
        onValueChangeFinished = onValueChangeFinished, colors = colors,
        thumb = {
            androidx.compose.foundation.layout.Box(Modifier.size(22.dp)
                .shadow(2.dp, androidx.compose.foundation.shape.CircleShape)
                .background(androidx.compose.ui.graphics.Color.White, androidx.compose.foundation.shape.CircleShape)
                .border(1.dp, outline, androidx.compose.foundation.shape.CircleShape))
        },
        track = { st ->
            androidx.compose.material3.SliderDefaults.Track(sliderState = st, modifier = Modifier.height(6.dp), colors = colors,
                drawStopIndicator = null, thumbTrackGapSize = 0.dp)
        },
    )
}
