package com.local.agremote.ui

import kotlinx.coroutines.launch
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilledTonalButton
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.local.agremote.ThemeConfig

val PRESET_COLORS = listOf(
    0xFFDBF1FF.toInt() to "晴空蓝", // 默认
    0xFFD97757.toInt() to "陶土橙",
    0xFF3482FF.toInt() to "小米蓝",
    0xFF0EA5E9.toInt() to "天空蓝",
    0xFF10B981.toInt() to "翡翠绿",
    0xFF22C55E.toInt() to "青草绿",
    0xFFEAB308.toInt() to "琥珀金",
    0xFFF97316.toInt() to "活力橙",
    0xFFEF4444.toInt() to "绯红",
    0xFFEC4899.toInt() to "樱花粉",
    0xFF8B5CF6.toInt() to "罗兰紫",
    0xFF6366F1.toInt() to "靛蓝",
    0xFF64748B.toInt() to "高级灰",
    0xFF1F1E1D.toInt() to "墨黑",
)

private fun bestOnColor(bg: Color): Color = if (bg.luminance() > 0.45f) Color(0xFF111111) else Color.White

private fun hueShift(color: Color, degrees: Float): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[0] = (hsv[0] + degrees + 360f) % 360f
    return Color(android.graphics.Color.HSVToColor(hsv))
}

private fun materialSchemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val tertSeed = hueShift(seed, 42f)
    return if (!dark) {
        val primary = lerp(seed, Color.Black, if (seed.luminance() > 0.55f) 0.22f else 0.06f)
        val primaryContainer = lerp(Color.White, seed, 0.22f)
        val onPrimaryContainer = lerp(seed, Color.Black, 0.55f)
        val secondary = lerp(seed, Color(0xFF606068), 0.45f)
        val secondaryContainer = lerp(Color.White, seed, 0.14f)
        val tertiary = lerp(tertSeed, Color.Black, if (tertSeed.luminance() > 0.55f) 0.2f else 0.05f)
        val tertiaryContainer = lerp(Color.White, tertSeed, 0.20f)
        val surface = lerp(Color(0xFFFDFCFF), seed, 0.025f)
        lightColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = bestOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = lerp(secondary, Color.Black, 0.5f),
            tertiary = tertiary,
            onTertiary = bestOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = lerp(tertSeed, Color.Black, 0.55f),
            background = surface,
            onBackground = Color(0xFF1A1B1E),
            surface = surface,
            onSurface = Color(0xFF1A1B1E),
            surfaceVariant = lerp(Color(0xFFEAECEF), seed, 0.08f),
            onSurfaceVariant = Color(0xFF494A50),
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = lerp(Color(0xFFF7F7FA), seed, 0.04f),
            surfaceContainer = lerp(Color(0xFFF2F2F6), seed, 0.06f),
            surfaceContainerHigh = lerp(Color(0xFFECECF1), seed, 0.085f),
            surfaceContainerHighest = lerp(Color(0xFFE5E6EC), seed, 0.11f),
            outline = Color(0xFF76777E),
            outlineVariant = lerp(Color(0xFFD2D3DA), seed, 0.08f),
        )
    } else {
        val primary = lerp(seed, Color.White, if (seed.luminance() < 0.25f) 0.45f else 0.26f)
        val primaryContainer = lerp(Color(0xFF1C1D22), seed, 0.34f)
        val onPrimaryContainer = lerp(seed, Color.White, 0.78f)
        val secondary = lerp(primary, Color(0xFFB4B6BE), 0.35f)
        val secondaryContainer = lerp(Color(0xFF202127), seed, 0.24f)
        val tertiary = lerp(tertSeed, Color.White, 0.32f)
        val tertiaryContainer = lerp(Color(0xFF222026), tertSeed, 0.32f)
        val surface = lerp(Color(0xFF121316), seed, 0.03f)
        darkColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = bestOnColor(secondary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = lerp(secondary, Color.White, 0.75f),
            tertiary = tertiary,
            onTertiary = bestOnColor(tertiary),
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = lerp(tertSeed, Color.White, 0.78f),
            background = surface,
            onBackground = Color(0xFFE6E6EC),
            surface = surface,
            onSurface = Color(0xFFE6E6EC),
            surfaceVariant = lerp(Color(0xFF2B2C32), seed, 0.08f),
            onSurfaceVariant = Color(0xFFBABBC4),
            surfaceContainerLowest = lerp(Color(0xFF0D0E11), seed, 0.02f),
            surfaceContainerLow = lerp(Color(0xFF191A1F), seed, 0.04f),
            surfaceContainer = lerp(Color(0xFF1E1F25), seed, 0.055f),
            surfaceContainerHigh = lerp(Color(0xFF272830), seed, 0.075f),
            surfaceContainerHighest = lerp(Color(0xFF31333B), seed, 0.10f),
            outline = Color(0xFF8E9099),
            outlineVariant = lerp(Color(0xFF42434C), seed, 0.08f),
        )
    }
}

/**
 * 默认（仿 Claude 移动端）：暖色纸感的中性底色，气泡和标签用米灰而不是强调色，
 * 强调色只用在图标、链接、开关和发送按钮上；深色模式是暖调深灰。强调色可调；字体统一用系统字体。
 */
private fun claudeScheme(accent: Color, dark: Boolean): ColorScheme {
    val tert = hueShift(accent, -150f)
    return if (!dark) {
        val primary = accent // 用你选的颜色本身（浅色不再自动加深）；按钮上的图标会自动选深色或浅色
        lightColorScheme(
            primary = primary, onPrimary = bestOnColor(primary),
            // 取自 Claude 移动端：偏暖的浅灰底、纯白卡片、浅灰按钮和标签、近黑文字
            primaryContainer = Color(0xFFEFEEEA), onPrimaryContainer = Color(0xFF141413),
            secondary = Color(0xFF6B6A65), onSecondary = Color.White,
            secondaryContainer = Color(0xFFEFEEEA), onSecondaryContainer = Color(0xFF141413),
            tertiary = tert, onTertiary = bestOnColor(tert),
            tertiaryContainer = lerp(Color(0xFFF9F9F7), tert, 0.16f), onTertiaryContainer = Color(0xFF141413),
            background = Color(0xFFF9F9F7), onBackground = Color(0xFF141413),
            surface = Color(0xFFF9F9F7), onSurface = Color(0xFF141413),
            surfaceVariant = Color(0xFFEFEEEA), onSurfaceVariant = Color(0xFF6B6A65),
            surfaceContainerLowest = Color(0xFFFFFFFF), surfaceContainerLow = Color(0xFFF4F4F0),
            surfaceContainer = Color(0xFFF0EFEB), surfaceContainerHigh = Color(0xFFEFEEEA),
            surfaceContainerHighest = Color(0xFFE9E8E4),
            outline = Color(0xFFB9B8B3), outlineVariant = Color(0xFFE4E3DE),
        )
    } else {
        val primary = if (accent.luminance() < 0.2f) lerp(accent, Color.White, 0.4f) else accent
        darkColorScheme(
            primary = primary, onPrimary = bestOnColor(primary),
            primaryContainer = Color(0xFF393937), onPrimaryContainer = Color(0xFFF0EEE6),
            secondary = Color(0xFFC2C0B6), onSecondary = Color(0xFF1F1E1D),
            secondaryContainer = Color(0xFF3E3E3A), onSecondaryContainer = Color(0xFFF0EEE6),
            tertiary = lerp(tert, Color.White, 0.25f), onTertiary = Color(0xFF1F1E1D),
            tertiaryContainer = lerp(Color(0xFF262624), tert, 0.3f), onTertiaryContainer = Color(0xFFF0EEE6),
            background = Color(0xFF262624), onBackground = Color(0xFFF0EEE6),
            surface = Color(0xFF262624), onSurface = Color(0xFFF0EEE6),
            surfaceVariant = Color(0xFF3A3A37), onSurfaceVariant = Color(0xFFA6A39A),
            surfaceContainerLowest = Color(0xFF1F1E1D), surfaceContainerLow = Color(0xFF2B2B29),
            surfaceContainer = Color(0xFF30302E), surfaceContainerHigh = Color(0xFF393937),
            surfaceContainerHighest = Color(0xFF44443F),
            outline = Color(0xFF7A7870), outlineVariant = Color(0xFF3F3F3B),
        )
    }
}

/** Miuix（HyperOS 风格）：纯净中性黑白层次 + 高对比纯色卡片 + 清晰主题强调色。 */
private fun miuixScheme(accent: Color, dark: Boolean): ColorScheme {
    val tert = hueShift(accent, 30f)
    return if (!dark) {
        val primary = accent
        val primaryContainer = lerp(Color.White, accent, 0.14f)
        val onPrimaryContainer = lerp(accent, Color.Black, 0.35f)
        val secondaryContainer = lerp(Color.White, accent, 0.10f)
        lightColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = primary,
            onSecondary = bestOnColor(primary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color(0xFF111111),
            tertiary = tert,
            onTertiary = bestOnColor(tert),
            tertiaryContainer = lerp(Color.White, tert, 0.14f),
            onTertiaryContainer = Color(0xFF1A1A1A),
            background = Color(0xFFF7F7F7),
            onBackground = Color(0xFF000000),
            surface = Color(0xFFF7F7F7),
            onSurface = Color(0xFF000000),
            surfaceVariant = Color(0xFFEFEFF2),
            onSurfaceVariant = Color(0xFF666666),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFFFFFFF),
            surfaceContainer = Color(0xFFFFFFFF),
            surfaceContainerHigh = Color(0xFFEDEDEF),
            surfaceContainerHighest = Color(0xFFE4E4E7),
            outline = Color(0xFF8E8E93),
            outlineVariant = Color(0xFFE5E5EA),
        )
    } else {
        val primary = if (accent.luminance() < 0.18f) lerp(accent, Color.White, 0.35f) else accent
        val primaryContainer = lerp(Color(0xFF1C1C1E), primary, 0.28f)
        val onPrimaryContainer = lerp(primary, Color.White, 0.72f)
        val secondaryContainer = lerp(Color(0xFF1C1C1E), primary, 0.20f)
        darkColorScheme(
            primary = primary,
            onPrimary = bestOnColor(primary),
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = primary,
            onSecondary = bestOnColor(primary),
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = Color(0xFFF2F2F7),
            tertiary = tert,
            onTertiary = bestOnColor(tert),
            tertiaryContainer = lerp(Color(0xFF1C1C1E), tert, 0.25f),
            onTertiaryContainer = Color(0xFFF2F2F7),
            background = Color(0xFF000000),
            onBackground = Color(0xFFF2F2F7),
            surface = Color(0xFF000000),
            onSurface = Color(0xFFF2F2F7),
            surfaceVariant = Color(0xFF242426),
            onSurfaceVariant = Color(0xFF8E8E93),
            surfaceContainerLowest = Color(0xFF0A0A0A),
            surfaceContainerLow = Color(0xFF141414),
            surfaceContainer = Color(0xFF1C1C1E),
            surfaceContainerHigh = Color(0xFF242426),
            surfaceContainerHighest = Color(0xFF2C2C2E),
            outline = Color(0xFF636366),
            outlineVariant = Color(0xFF2C2C2E),
        )
    }
}

@Composable
fun AgAppTheme(cfg: ThemeConfig, content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val sysDark = isSystemInDarkTheme()
    val dark = when (cfg.darkMode) {
        "light" -> false
        "dark" -> true
        else -> sysDark
    }
    val hasDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    var accentColor = Color.Unspecified
    val scheme = when (cfg.style) {
        "default" -> {
            val accent = if (cfg.colorMode == "system" && hasDynamic) {
                (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)).primary
            } else Color(cfg.seedColor)
            // 默认风格：大部分地方用黑色（深色模式下是白色），强调色只留给发送按钮、思考中等少数地方（见 [accent]）
            val s = claudeScheme(accent, dark)
            accentColor = s.primary
            s.copy(primary = s.onSurface, onPrimary = s.surface)
        }
        "miuix" -> {
            val accent = if (cfg.colorMode == "system" && hasDynamic) {
                (if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)).primary
            } else Color(cfg.seedColor)
            miuixScheme(accent, dark)
        }
        else -> {
            if (cfg.colorMode == "system" && hasDynamic) {
                if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
            } else {
                materialSchemeFromSeed(Color(cfg.seedColor), dark)
            }
        }
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val ctrl = WindowCompat.getInsetsController(window, view)
            ctrl.isAppearanceLightStatusBars = !dark
            ctrl.isAppearanceLightNavigationBars = !dark
        }
    }

    // 字体：用导入的字体时，所有文字样式都换成它；聊天正文是原生 TextView，通过 LocalAppTypeface 给它
    val fontPath = cfg.fontFile.takeIf { cfg.useCustomFont && it.isNotBlank() && java.io.File(it).exists() }
    val typeface = remember(fontPath) { fontPath?.let { runCatching { android.graphics.Typeface.createFromFile(it) }.getOrNull() } }
    val typography = remember(fontPath, typeface) {
        val base = androidx.compose.material3.Typography()
        if (fontPath == null || typeface == null) base else {
            val fam = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(java.io.File(fontPath)))
            base.copy(
                displayLarge = base.displayLarge.copy(fontFamily = fam), displayMedium = base.displayMedium.copy(fontFamily = fam),
                displaySmall = base.displaySmall.copy(fontFamily = fam), headlineLarge = base.headlineLarge.copy(fontFamily = fam),
                headlineMedium = base.headlineMedium.copy(fontFamily = fam), headlineSmall = base.headlineSmall.copy(fontFamily = fam),
                titleLarge = base.titleLarge.copy(fontFamily = fam), titleMedium = base.titleMedium.copy(fontFamily = fam),
                titleSmall = base.titleSmall.copy(fontFamily = fam), bodyLarge = base.bodyLarge.copy(fontFamily = fam),
                bodyMedium = base.bodyMedium.copy(fontFamily = fam), bodySmall = base.bodySmall.copy(fontFamily = fam),
                labelLarge = base.labelLarge.copy(fontFamily = fam), labelMedium = base.labelMedium.copy(fontFamily = fam),
                labelSmall = base.labelSmall.copy(fontFamily = fam),
            )
        }
    }
    CompositionLocalProvider(LocalAccent provides accentColor, LocalAppTypeface provides typeface) {
        MaterialTheme(colorScheme = scheme, content = content, typography = typography, shapes = androidx.compose.material3.Shapes(
            extraSmall = RoundedCornerShape(14.dp), // 输入框、菜单、提示条
            small = RoundedCornerShape(50),         // 选项按钮（FilterChip / AssistChip）做成胶囊
        ))
    }
}

private val LocalAccent = staticCompositionLocalOf { Color.Unspecified }

/** 导入的字体（null = 系统字体），给聊天正文这类原生 TextView 用。 */
val LocalAppTypeface = staticCompositionLocalOf<android.graphics.Typeface?> { null }

/** 强调色：默认风格下 primary 是黑色，真正的强调色从这里取；其他风格就是 primary。 */
@Composable
fun accent(): Color = LocalAccent.current.takeOrElse { MaterialTheme.colorScheme.primary }

@Composable
fun onAccent(): Color = bestOnColor(accent())

/** 设置页里的主题风格 + 配色（深色模式在设置首页的“颜色模式”里）。 */
@Composable
fun ThemeSettingsCard(cfg: ThemeConfig, onChange: (ThemeConfig) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. 主题风格：默认（仿 Claude）/ Material You / Miuix —— 布局都一样，只是配色不同
            SectionLabel(tr("主题风格"))
            CardGroup {
                StyleOptionCard(
                    title = tr("默认"),
                    desc = tr("温暖纸感 · 简洁克制"),
                    selected = cfg.style == "default",
                    previewBg = Color(0xFFFAF9F5),
                    previewCard = Color(0xFFF0EEE6),
                    previewAccent = Color(0xFFDBF1FF),
                    modifier = Modifier.fillMaxWidth(),
                ) { onChange(cfg.copy(style = "default")) }
                CardDivider(indent = false)
                StyleOptionCard(
                    title = "Material You",
                    desc = tr("动态色调 · 柔和圆润"),
                    selected = cfg.style == "material",
                    previewBg = Color(0xFFF4EFF8),
                    previewCard = Color(0xFFE7DFF0),
                    previewAccent = Color(0xFF6750A4),
                    modifier = Modifier.fillMaxWidth(),
                ) { onChange(cfg.copy(style = "material")) }
                CardDivider(indent = false)
                StyleOptionCard(
                    title = "Miuix",
                    desc = tr("纯净极简 · HyperOS"),
                    selected = cfg.style == "miuix",
                    previewBg = Color(0xFFF7F7F7),
                    previewCard = Color(0xFFFFFFFF),
                    previewAccent = Color(0xFF3482FF),
                    modifier = Modifier.fillMaxWidth(),
                ) { onChange(cfg.copy(style = "miuix")) }
            }

            // 2. 主题颜色：跟随系统 / 自由调色 + 两行整齐的预设色圆点
            Row(Modifier.padding(end = 6.dp), verticalAlignment = Alignment.Bottom) {
                SectionLabel(tr("配色方案"), Modifier.weight(1f))
                Text(
                    if (cfg.colorMode == "system") tr("跟随系统") else String.format("#%06X", cfg.seedColor and 0xFFFFFF),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val isPreset = cfg.colorMode == "custom" && (PRESET_COLORS.any { it.first == cfg.seedColor } || cfg.seedColor in cfg.savedColors)
            val isSys = cfg.colorMode == "system" && !showPicker
            val isFreeActive = showPicker || (cfg.colorMode == "custom" && !isPreset)
            CardBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ColorModePill(Icons.Rounded.AutoAwesome, tr("系统"), isSys, Modifier.weight(1f)) {
                    showPicker = false; onChange(cfg.copy(colorMode = "system"))
                }
                ColorModePill(Icons.Rounded.Palette, if (showPicker) tr("收回调色") else tr("自由调色"), isFreeActive, Modifier.weight(1f)) {
                    showPicker = !showPicker
                }
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PRESET_COLORS.chunked(7).forEach { rowColors ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        rowColors.forEach { (argb, name) ->
                            val sel = !showPicker && cfg.colorMode == "custom" && cfg.seedColor == argb
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(Color(argb))
                                    .then(if (sel) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                    .clickable { showPicker = false; onChange(cfg.copy(colorMode = "custom", seedColor = argb)) },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (sel) Icon(Icons.Rounded.Check, name, Modifier.size(18.dp), tint = bestOnColor(Color(argb)))
                            }
                        }
                        repeat(7 - rowColors.size) { Spacer(Modifier.size(38.dp)) } // 不满一行时占位，圆点不会被拉开
                    }
                }
            }
            // 我的配色：点选使用，长按删除
            if (cfg.savedColors.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(tr("我的配色") + " · " + tr("长按删除"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    cfg.savedColors.take(7).forEach { argb ->
                        val sel = !showPicker && cfg.colorMode == "custom" && cfg.seedColor == argb
                        Box(
                            Modifier.size(38.dp).clip(CircleShape).background(Color(argb))
                                .then(if (sel) Modifier.border(2.5.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier)
                                .pointerInput(argb, cfg) {
                                    detectTapGestures(
                                        onTap = { showPicker = false; onChange(cfg.copy(colorMode = "custom", seedColor = argb)) },
                                        onLongPress = { onChange(cfg.copy(savedColors = cfg.savedColors - argb)) },
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) { if (sel) Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = bestOnColor(Color(argb))) }
                    }
                    repeat(7 - cfg.savedColors.take(7).size) { Spacer(Modifier.size(38.dp)) }
                }
            }

            if (showPicker) {
                Spacer(Modifier.height(10.dp))
                CustomColorPicker(
                    initial = cfg.customColor, // 用单独保存的自定义色，不跟着预设色变
                    onCancel = { showPicker = false },
                    onSave = { argb ->
                        // 保存到“我的配色”并使用
                        showPicker = false
                        onChange(cfg.copy(colorMode = "custom", seedColor = argb, customColor = argb,
                            savedColors = (listOf(argb) + (cfg.savedColors - argb)).take(7)))
                    },
                    onConfirm = { argb ->
                        showPicker = false
                        onChange(cfg.copy(colorMode = "custom", seedColor = argb, customColor = argb))
                    },
                )
            }
            }
    }
}

@Composable
private fun StyleOptionCard(
    title: String,
    desc: String,
    selected: Boolean,
    previewBg: Color,
    previewCard: Color,
    previewAccent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    run {
        Row(modifier.clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            // 迷你风格示意图
            Box(Modifier.width(76.dp).height(40.dp).clip(RoundedCornerShape(10.dp)).background(previewBg)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)).padding(6.dp)) {
                Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(22.dp).clip(RoundedCornerShape(7.dp)).background(previewCard))
                    Box(Modifier.size(20.dp).clip(CircleShape).background(previewAccent))
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Rounded.Check, tr("已选"), Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun CustomColorPicker(initial: Int, onCancel: () -> Unit, onSave: (Int) -> Unit, onConfirm: (Int) -> Unit) {
    val initHsv = remember(initial) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(initial, it) }
    }
    var h by remember(initial) { mutableFloatStateOf(initHsv[0]) }
    var s by remember(initial) { mutableFloatStateOf(initHsv[1].coerceAtLeast(0.15f)) }
    var v by remember(initial) { mutableFloatStateOf(initHsv[2].coerceAtLeast(0.25f)) }
    var hex by remember(initial) { mutableStateOf(String.format("%06X", initial and 0xFFFFFF)) } // 不含 #

    fun syncHex() {
        val argb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
        hex = String.format("%06X", argb and 0xFFFFFF)
    }

    val previewArgb = android.graphics.Color.HSVToColor(floatArrayOf(h, s, v))
    val preview = Color(previewArgb)
    val hueBrush = remember {
        Brush.horizontalGradient(
            listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red)
        )
    }

    val bottomPart = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
        // 色相：彩虹条，圆形把手
        Text(tr("色相"), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box(
            Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(14.dp)).background(hueBrush)
                .pointerInput(Unit) {
                    detectTapGestures { off -> h = (off.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * 360f; syncHex() }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { ch, _ ->
                        ch.consume(); h = (ch.position.x / size.width.coerceAtLeast(1)).coerceIn(0f, 1f) * 360f; syncHex()
                    }
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.height / 2f
                val cx = ((h / 360f).coerceIn(0f, 1f) * size.width).coerceIn(r, size.width - r)
                drawCircle(Color.White, radius = r - 2.dp.toPx(), center = Offset(cx, r))
                drawCircle(preview, radius = r - 5.dp.toPx(), center = Offset(cx, r))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("饱和度"), Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppSlider(value = s, onValueChange = { s = it; syncHex() }, valueRange = 0f..1f, modifier = Modifier.weight(1f), color = preview)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("亮度"), Modifier.width(56.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            AppSlider(value = v, onValueChange = { v = it; syncHex() }, valueRange = 0.15f..1f, modifier = Modifier.weight(1f), color = preview)
        }
        Column(Modifier.bringIntoViewRequester(bottomPart), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // 预览色块 + HEX（# 固定）
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(preview)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp)))
                OutlinedTextField(
                    value = hex,
                    onValueChange = { raw ->
                        val digits = raw.uppercase().filter { it in '0'..'9' || it in 'A'..'F' }.take(6)
                        hex = digits
                        if (digits.length == 6) {
                            runCatching { (0xFF000000.toInt() or digits.toInt(16)) }.onSuccess { argb ->
                                val out = FloatArray(3)
                                android.graphics.Color.colorToHSV(argb, out)
                                h = out[0]; s = out[1]; v = out[2]
                            }
                        }
                    },
                    singleLine = true,
                    prefix = { Text("#") },
                    label = { Text(tr("自定义色值 (HEX)")) },
                    modifier = Modifier.weight(1f).onFocusChanged { f ->
                        // 键盘弹起后，把输入框和下面的按钮一起滚到键盘上方，留出空隙
                        if (f.isFocused) scope.launch { repeat(8) { kotlinx.coroutines.delay(120); bottomPart.bringIntoView() } } // 键盘升起过程中持续跟上
                    },
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onCancel, modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface)) { Text(tr("取消")) }
                Button(onClick = { onConfirm(previewArgb) }, modifier = Modifier.weight(1f).height(44.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = preview, contentColor = bestOnColor(preview))) { Text(tr("使用")) }
            }
            FilledTonalButton(onClick = { onSave(previewArgb) }, modifier = Modifier.fillMaxWidth().height(44.dp),
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface)) {
                Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(tr("保存到我的配色"))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 配色方式的胶囊按钮：选中浅灰底，没选中白底细边。 */
@Composable
private fun ColorModePill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else sheetCardColor(),
        border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.height(44.dp),
    ) {
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1, softWrap = false,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
    }
}
