package com.local.agremote.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.launch
import com.local.agremote.Link

val Green = Color(0xFF2E9E5B)
val Amber = Color(0xFFE08A00)

@Composable
fun LinkLine(link: Link) {
    val (color, text) = when (link) {
        Link.Online -> Green to tr("已连接")
        Link.Connecting -> MaterialTheme.colorScheme.outline to tr("连接中…")
        Link.NoAntigravity -> Amber to tr("电脑在线，但 Antigravity 没开")
        Link.Offline -> MaterialTheme.colorScheme.error to tr("无法连接")
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun StatusDot(running: Boolean, color: Color = MaterialTheme.colorScheme.primary) {
    if (running) {
        val t = rememberInfiniteTransition(label = "pulse")
        val a by t.animateFloat(0.3f, 1f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "a")
        Box(Modifier.size(8.dp).alpha(a).background(color, CircleShape))
    } else {
        Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.outlineVariant, CircleShape))
    }
}

fun isRunning(status: String?) = status == "running" || status == "busy" || status == "canceling"

fun relTime(iso: String): String {
    val t = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val d = (System.currentTimeMillis() - t) / 1000
    val lang = I18n.currentLang
    val sp = if (lang.startsWith("zh") || lang == "ja") "" else " "
    return when {
        d < 60 -> I18n.t(lang, "刚刚")
        d < 3600 -> "${d / 60}$sp${I18n.t(lang, "分钟前")}"
        d < 86400 -> "${d / 3600}$sp${I18n.t(lang, "小时前")}"
        d < 86400 * 7 -> "${d / 86400}$sp${I18n.t(lang, "天前")}"
        else -> {
            val fmt = if (lang.startsWith("zh") || lang == "ja") "M月d日" else "MMM d"
            java.text.SimpleDateFormat(fmt, java.util.Locale.forLanguageTag(lang)).format(java.util.Date(t))
        }
    }
}

/** 回复气泡底部的发送时间，如“19:48”“昨天 19:48”“11月23日 19:48”。 */
fun msgTime(iso: String): String {
    val instant = runCatching { java.time.Instant.parse(iso) }.getOrNull() ?: return ""
    val zone = java.time.ZoneId.systemDefault()
    val zdt = instant.atZone(zone)
    val today = java.time.LocalDate.now(zone)
    val hm = java.time.format.DateTimeFormatter.ofPattern("HH:mm").format(zdt)
    val lang = I18n.currentLang
    val cjk = lang.startsWith("zh") || lang == "ja"
    return when {
        zdt.toLocalDate() == today -> hm
        zdt.toLocalDate() == today.minusDays(1) -> "${I18n.t(lang, "昨天")} $hm"
        zdt.year == today.year -> if (cjk) "${zdt.monthValue}月${zdt.dayOfMonth}日 $hm" else "${zdt.monthValue}/${zdt.dayOfMonth} $hm"
        else -> if (cjk) "${zdt.year}年${zdt.monthValue}月${zdt.dayOfMonth}日 $hm" else "${zdt.year}-${zdt.monthValue}-${zdt.dayOfMonth} $hm"
    }
}

/** 距离某个时间点还有多久，比如“3小时20分钟后”。 */
fun untilText(iso: String): String {
    val t = runCatching { java.time.Instant.parse(iso).toEpochMilli() }.getOrNull() ?: return ""
    val m = ((t - System.currentTimeMillis()) / 60000).coerceAtLeast(0)
    val lang = I18n.currentLang
    val sp = if (lang.startsWith("zh") || lang == "ja") "" else " "
    return when {
        m < 1 -> I18n.t(lang, "马上")
        m < 60 -> "$m$sp${I18n.t(lang, "分钟后")}"
        m < 1440 -> "${m / 60}$sp${I18n.t(lang, "小时")}$sp${m % 60}$sp${I18n.t(lang, "分钟后")}"
        else -> "${m / 1440}$sp${I18n.t(lang, "天后")}"
    }
}

/**
 * 底部弹窗里的可滚动内容用：滚到头以后继续拉，内容自己带阻尼地跟着手指走、松手弹回（回弹手感保留），
 * 但多出来的滑动不再传给弹窗——否则弹窗会被拉上去又弹回，来回“抽搐”。拖标题栏关闭弹窗不受影响。
 */
@Composable
fun Modifier.noSheetOverscroll(): Modifier {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val offset = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val bounce = androidx.compose.animation.core.spring<Float>(dampingRatio = 0.72f,
        stiffness = androidx.compose.animation.core.Spring.StiffnessMediumLow)
    val conn = androidx.compose.runtime.remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset,
                                     source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                // 已经拉出了一段，又往回拉：先把这段收回来
                val o = offset.value
                if (o != 0f && available.y != 0f && (o > 0) != (available.y > 0)) {
                    val n = if (o > 0) maxOf(0f, o + available.y) else minOf(0f, o + available.y)
                    scope.launch { offset.snapTo(n) }
                    return androidx.compose.ui.geometry.Offset(0f, n - o)
                }
                return androidx.compose.ui.geometry.Offset.Zero
            }

            override fun onPostScroll(consumed: androidx.compose.ui.geometry.Offset, available: androidx.compose.ui.geometry.Offset,
                                      source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y != 0f && source == androidx.compose.ui.input.nestedscroll.NestedScrollSource.UserInput) {
                    val o = offset.value
                    val damp = 0.5f * (1f - (kotlin.math.abs(o) / 360f).coerceIn(0f, 0.92f)) // 越拉越沉
                    scope.launch { offset.snapTo((o + available.y * damp).coerceIn(-360f, 360f)) }
                }
                return available // 不交给弹窗
            }

            override suspend fun onPreFling(available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (offset.value != 0f) { offset.animateTo(0f, bounce); return available }
                return androidx.compose.ui.unit.Velocity.Zero
            }

            override suspend fun onPostFling(consumed: androidx.compose.ui.unit.Velocity,
                                             available: androidx.compose.ui.unit.Velocity): androidx.compose.ui.unit.Velocity {
                if (offset.value != 0f) offset.animateTo(0f, bounce)
                return available
            }
        }
    }
    return this.nestedScroll(conn).graphicsLayer { translationY = offset.value }
}
