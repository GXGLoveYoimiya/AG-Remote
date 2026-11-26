package com.local.agremote.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Spanned
import android.text.method.LinkMovementMethod
import android.text.style.AbsoluteSizeSpan
import android.text.style.BackgroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.ReplacementSpan
import android.text.style.TypefaceSpan
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.delay
import org.commonmark.node.Code
import org.commonmark.node.Link
import org.commonmark.node.Node
import kotlin.math.ceil

private sealed interface MdSeg {
    data class Prose(val text: String) : MdSeg
    data class Code(val lang: String, val code: String) : MdSeg
}

/** 把围栏代码块拆出来单独渲染（带右上角复制按钮），其余正文仍交给 Markwon。 */
private fun splitMarkdown(text: String): List<MdSeg> {
    if (!text.contains("```") && !text.contains("~~~")) return listOf(MdSeg.Prose(text))
    val out = ArrayList<MdSeg>()
    val lines = text.split('\n')
    val buf = StringBuilder()
    var fenceChar = ' '
    var fenceLen = 0
    var lang = ""
    fun flushProse() {
        val s = buf.toString().trim('\n')
        if (s.isNotBlank()) out.add(MdSeg.Prose(s))
        buf.setLength(0)
    }
    for (line in lines) {
        val trimmed = line.trimStart()
        if (fenceLen == 0) {
            val ch = trimmed.firstOrNull()
            if (ch == '`' || ch == '~') {
                var n = 0
                while (n < trimmed.length && trimmed[n] == ch) n++
                if (n >= 3) {
                    val info = trimmed.substring(n).trim()
                    if (ch != '`' || !info.contains('`')) {
                        flushProse()
                        fenceChar = ch
                        fenceLen = n
                        lang = info.substringBefore(' ').trim()
                        continue
                    }
                }
            }
            if (buf.isNotEmpty()) buf.append('\n')
            buf.append(line)
        } else {
            val isClose = trimmed.length >= fenceLen &&
                trimmed.takeWhile { it == fenceChar }.length >= fenceLen &&
                trimmed.dropWhile { it == fenceChar }.isBlank()
            if (isClose) {
                out.add(MdSeg.Code(lang, buf.toString()))
                buf.setLength(0)
                fenceLen = 0
                lang = ""
            } else {
                if (buf.isNotEmpty()) buf.append('\n')
                buf.append(line)
            }
        }
    }
    if (fenceLen > 0) {
        out.add(MdSeg.Code(lang, buf.toString()))
    } else {
        flushProse()
    }
    return out
}

/** 用原生 TextView 渲染 Markdown（Markwon），代码块单独渲染并带右上角复制按钮。 */
@Composable
fun Markdown(text: String, modifier: Modifier = Modifier, sizeSp: Float = 15.5f, onLink: ((String) -> Unit)? = null) {
    val segs = remember(text) { splitMarkdown(text) }
    if (segs.size == 1 && segs[0] is MdSeg.Prose) {
        MarkwonText((segs[0] as MdSeg.Prose).text, modifier, sizeSp, onLink)
        return
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        segs.forEach { seg ->
            when (seg) {
                is MdSeg.Prose -> MarkwonText(seg.text, Modifier.fillMaxWidth(), sizeSp, onLink)
                is MdSeg.Code -> CodeBlockCard(seg.lang, seg.code)
            }
        }
    }
}

@Composable
private fun CodeBlockCard(lang: String, code: String) {
    val ctx = LocalContext.current
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    lang.ifBlank { "代码" },
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("code", code))
                            copied = true
                            if (Build.VERSION.SDK_INT < 33) Toast.makeText(ctx, "已复制", Toast.LENGTH_SHORT).show()
                        }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        "复制代码",
                        Modifier.size(15.dp),
                        tint = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (copied) "已复制" else "复制",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (copied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
            Box(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp)) {
                SelectionContainer {
                    Text(
                        code,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * 解决 CommonMark 在“正文+**标点内容**正文”（如 `显示**“你好”**时`、`改为**`AG`**吧`）时
 * 因左右 flanking 规则导致星号原样露出的问题：只在配对的 `**...**` 内侧是标点且外侧紧贴文字时，
 * 给外侧补一个极细空格（U+200A）。
 */
private fun fixCjkBold(s: String): String {
    if (!s.contains("**")) return s
    val re = Regex("""(`+[^`\n]+`+)|(\*\*(?!\s)(?:[^*\n]|`+[^`\n]+`+)+?(?<!\s)\*\*)""")
    val sb = StringBuilder(s.length + 12)
    var last = 0
    for (m in re.findAll(s)) {
        sb.append(s, last, m.range.first)
        if (m.groups[1] != null) {
            sb.append(m.value)
        } else {
            val full = m.value
            val inner = full.substring(2, full.length - 2)
            val prev = s.getOrNull(m.range.first - 1)
            val next = s.getOrNull(m.range.last + 1)
            val firstIn = inner.firstOrNull()
            val lastIn = inner.lastOrNull()
            val needLeft = prev != null && prev.isLetterOrDigit() && firstIn != null && !firstIn.isLetterOrDigit() && !firstIn.isWhitespace()
            val needRight = next != null && next.isLetterOrDigit() && lastIn != null && !lastIn.isLetterOrDigit() && !lastIn.isWhitespace()
            if (needLeft) sb.append('\u200A')
            sb.append(full)
            if (needRight) sb.append('\u200A')
        }
        last = m.range.last + 1
    }
    sb.append(s, last, s.length)
    return sb.toString()
}

/** 自动把裸露的 http(s):// 链接转为 Markdown 链接（跳过行内代码和已有 [text](url)）。 */
private fun linkifyBareUrls(s: String): String {
    if (!s.contains("http://") && !s.contains("https://")) return s
    val re = Regex("""(`+[^`\n]+`+)|(\[[^\]]*\]\([^)]+\))|(<https?://[^>\s]+>)|(https?://[^\s<>)\]"'，。；：！？、】）》]+)""")
    return re.replace(s) { m ->
        when {
            m.groups[1] != null || m.groups[2] != null || m.groups[3] != null -> m.value
            else -> {
                var u = m.value
                var trail = ""
                while (u.isNotEmpty() && u.last() in charArrayOf('.', ',', ';', ':', '!', '?', ')', ']')) {
                    trail = u.last() + trail
                    u = u.dropLast(1)
                }
                if (u.startsWith("http://") || u.startsWith("https://")) "[$u]($u)$trail" else m.value
            }
        }
    }
}

/** Antigravity 风格圆角链接胶囊（柔和主色底 + 主色文字，不画生硬下划线）。 */
private class RoundedLinkSpan(
    private val bgColor: Int,
    private val textColor: Int,
    private val radius: Float,
    private val padH: Float,
    private val marginH: Float,
    private val padV: Float,
    private val mono: Boolean = false,
) : ReplacementSpan() {
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val oldTf = paint.typeface
        if (mono) paint.typeface = Typeface.MONOSPACE
        val w = paint.measureText(text, start, end)
        paint.typeface = oldTf
        return ceil(w + padH * 2f + marginH * 2f).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val oldColor = paint.color
        val oldTf = paint.typeface
        val oldUl = paint.isUnderlineText
        if (mono) paint.typeface = Typeface.MONOSPACE
        paint.isUnderlineText = false
        val w = paint.measureText(text, start, end)
        val fm = paint.fontMetrics
        val rTop = (y + fm.ascent - padV).coerceAtLeast(top.toFloat())
        val rBottom = (y + fm.descent + padV).coerceAtMost(bottom.toFloat())
        rect.set(x + marginH, rTop, x + marginH + w + padH * 2f, rBottom)
        paint.color = bgColor
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x + marginH + padH, y.toFloat(), paint)
        paint.color = oldColor
        paint.typeface = oldTf
        paint.isUnderlineText = oldUl
    }
}

/** 圆角行内代码胶囊：紧贴文字高度绘制，避免与上下行碰撞，且短词不会中间折断。 */
private class RoundedCodeSpan(
    private val bgColor: Int,
    private val textColor: Int,
    private val textSizePx: Float,
    private val radius: Float,
    private val padH: Float,
    private val marginH: Float,
    private val padV: Float,
) : ReplacementSpan() {
    private val rect = RectF()

    override fun getSize(paint: Paint, text: CharSequence, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        val oldTf = paint.typeface
        val oldSz = paint.textSize
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = textSizePx
        val w = paint.measureText(text, start, end)
        paint.typeface = oldTf
        paint.textSize = oldSz
        return ceil(w + padH * 2f + marginH * 2f).toInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val oldColor = paint.color
        val oldTf = paint.typeface
        val oldSz = paint.textSize
        paint.typeface = Typeface.MONOSPACE
        paint.textSize = textSizePx
        val w = paint.measureText(text, start, end)
        val fm = paint.fontMetrics
        val rTop = (y + fm.ascent - padV).coerceAtLeast(top.toFloat())
        val rBottom = (y + fm.descent + padV).coerceAtMost(bottom.toFloat())
        rect.set(x + marginH, rTop, x + marginH + w + padH * 2f, rBottom)
        paint.color = bgColor
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.color = textColor
        canvas.drawText(text, start, end, x + marginH + padH, y.toFloat(), paint)
        paint.color = oldColor
        paint.typeface = oldTf
        paint.textSize = oldSz
    }
}

@Composable
private fun MarkwonText(text: String, modifier: Modifier = Modifier, sizeSp: Float = 15.5f, onLink: ((String) -> Unit)? = null) {
    val ctx = LocalContext.current
    val appTypeface = LocalAppTypeface.current
    val cs = MaterialTheme.colorScheme
    val fg = cs.onSurface.toArgb()
    val link = cs.primary.toArgb()
    val linkBg = cs.primaryContainer.copy(alpha = 0.52f).toArgb()
    val codeBg = cs.surfaceContainerHighest.copy(alpha = 0.72f).toArgb()
    val dm = ctx.resources.displayMetrics
    val codeSizePx = android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp * 0.90f, dm)
    val density = dm.density
    val linkHandler = androidx.compose.runtime.rememberUpdatedState(onLink)
    val processed = remember(text) { linkifyBareUrls(fixCjkBold(normalizeMath(text))) }
    val markwon = remember(fg, link, linkBg, codeBg, sizeSp) {
        Markwon.builder(ctx)
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TablePlugin.create(ctx))
            // 数学公式：$$…$$ 独占几行是公式块，行内的 $$…$$ 是行内公式（其他写法先由 normalizeMath 统一）
            .usePlugin(io.noties.markwon.inlineparser.MarkwonInlineParserPlugin.create())
            .usePlugin(io.noties.markwon.ext.latex.JLatexMathPlugin.create(
                android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP, sizeSp, dm)) { b ->
                b.inlinesEnabled(true)
                b.theme().textColor(fg)
                b.errorHandler { latex, _ -> null.also { android.util.Log.w("AGRemote", "公式渲染失败: $latex") } }
            })
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureConfiguration(builder: io.noties.markwon.MarkwonConfiguration.Builder) {
                    builder.linkResolver { view, url ->
                        val h = linkHandler.value
                        if (h != null) h(url) else openWebLink(view.context, url)
                    }
                }

                override fun configureVisitor(builder: MarkwonVisitor.Builder) {
                    // 收紧段落之间的空白高度，避免出现大段留白
                    builder.blockHandler(object : MarkwonVisitor.BlockHandler {
                        override fun blockStart(visitor: MarkwonVisitor, node: Node) {
                            visitor.ensureNewLine()
                        }

                        override fun blockEnd(visitor: MarkwonVisitor, node: Node) {
                            if (visitor.hasNext(node)) {
                                visitor.ensureNewLine()
                                val start = visitor.length()
                                visitor.forceNewLine()
                                visitor.builder().setSpan(
                                    RelativeSizeSpan(0.42f),
                                    start, visitor.length(),
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                                )
                            }
                        }
                    })
                    // 行内代码：短内容用圆角胶囊，长内容正常折行；若位于链接内则用主色高亮
                    builder.on(Code::class.java) { visitor, code ->
                        val start = visitor.length()
                        val literal = code.literal
                        val inLink = code.parent is Link
                        visitor.builder().append(literal)
                        if (literal.length <= 26 && !literal.contains('\n')) {
                            visitor.builder().setSpan(
                                RoundedCodeSpan(
                                    bgColor = if (inLink) linkBg else codeBg,
                                    textColor = if (inLink) link else fg,
                                    textSizePx = codeSizePx,
                                    radius = 6f * density,
                                    padH = 5f * density,
                                    marginH = 2f * density,
                                    padV = 1.5f * density,
                                ),
                                start, visitor.length(),
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        } else {
                            visitor.builder().setSpan(BackgroundColorSpan(if (inLink) linkBg else codeBg), start, visitor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            visitor.builder().setSpan(TypefaceSpan("monospace"), start, visitor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                            visitor.builder().setSpan(AbsoluteSizeSpan(codeSizePx.toInt()), start, visitor.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        }
                    }
                    // 链接：短标签用 Antigravity 风格圆角主色胶囊，长链接正常折行高亮
                    builder.on(Link::class.java) { visitor, linkNode ->
                        val start = visitor.length()
                        visitor.visitChildren(linkNode)
                        val end = visitor.length()
                        val labelLen = end - start
                        val hasCodeChild = linkNode.firstChild is Code && linkNode.firstChild == linkNode.lastChild
                        if (labelLen in 1..34 && !hasCodeChild) {
                            visitor.builder().setSpan(
                                RoundedLinkSpan(
                                    bgColor = linkBg,
                                    textColor = link,
                                    radius = 7f * density,
                                    padH = 6f * density,
                                    marginH = 2f * density,
                                    padV = 1.5f * density,
                                ),
                                start, end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                            )
                        }
                        io.noties.markwon.core.CoreProps.LINK_DESTINATION.set(visitor.renderProps(), linkNode.destination)
                        visitor.setSpansForNodeOptional(linkNode, start)
                    }
                }

                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(link)
                        .codeBackgroundColor(codeBg)
                        .codeBlockBackgroundColor(codeBg)
                        .codeTypeface(Typeface.MONOSPACE)
                        .codeBlockTypeface(Typeface.MONOSPACE)
                        .codeTextSize(codeSizePx.toInt())
                        .codeBlockMargin((10 * density).toInt())
                        .headingBreakHeight(0)
                        .bulletWidth((6 * density).toInt())
                }
            })
            .build()
    }
    AndroidView(
        modifier = modifier,
        factory = { c ->
            TextView(c).apply {
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                setLineSpacing(4f * density, 1.22f)
            }
        },
        update = { tv ->
            tv.setTextColor(fg)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            val tf = appTypeface ?: android.graphics.Typeface.DEFAULT
            if (tv.typeface != tf) tv.typeface = tf
            if (tv.tag != processed) {
                markwon.setParsedMarkdown(tv, alignInlineMath(markwon, markwon.toMarkdown(processed)))
                tv.tag = processed
            }
        },
    )
}


/**
 * 行内公式对齐：公式插件把公式的中心对在文字基线上（看起来往下掉半格），还会改这一行的行高。
 * 换成 [InlineMathSpan]：公式中心对准这一行文字的中线，只有公式比字高时才撑开行距。
 */
private fun alignInlineMath(markwon: Markwon, src: android.text.Spanned): android.text.Spanned {
    val spans = src.getSpans(0, src.length, io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan::class.java)
        .filter { it.javaClass != io.noties.markwon.ext.latex.JLatexAsyncDrawableSpan::class.java } // 子类 = 行内公式；本类 = 公式块
    if (spans.isEmpty()) return src
    val out = android.text.SpannableStringBuilder(src)
    val theme = markwon.configuration().theme()
    for (s in spans) {
        val st = out.getSpanStart(s); val en = out.getSpanEnd(s); val fl = out.getSpanFlags(s)
        out.removeSpan(s)
        out.setSpan(InlineMathSpan(theme, s.drawable), st, en, fl)
    }
    return out
}

private class InlineMathSpan(theme: io.noties.markwon.core.MarkwonTheme, private val d: io.noties.markwon.image.AsyncDrawable) :
    io.noties.markwon.image.AsyncDrawableSpan(theme, d, ALIGN_CENTER, false) {

    override fun getSize(paint: android.graphics.Paint, text: CharSequence?, start: Int, end: Int, fm: android.graphics.Paint.FontMetricsInt?): Int {
        if (!d.hasResult()) return (paint.measureText(text, start, end) + 0.5f).toInt()
        val b = d.bounds
        if (fm != null) {
            val p = paint.fontMetricsInt
            val center = (p.ascent + p.descent) / 2
            val top = center - b.height() / 2
            val bottom = top + b.height()
            fm.ascent = minOf(p.ascent, top); fm.top = minOf(p.top, top)
            fm.descent = maxOf(p.descent, bottom); fm.bottom = maxOf(p.bottom, bottom)
        }
        return b.right
    }

    override fun draw(canvas: android.graphics.Canvas, text: CharSequence?, start: Int, end: Int, x: Float,
                      top: Int, y: Int, bottom: Int, paint: android.graphics.Paint) {
        if (!d.hasResult()) return
        val p = paint.fontMetricsInt
        val center = y + (p.ascent + p.descent) / 2f
        val save = canvas.save()
        canvas.translate(x, center - d.bounds.height() / 2f)
        d.draw(canvas)
        canvas.restoreToCount(save)
    }
}

/**
 * 把模型常用的几种公式写法统一成 Markwon 公式插件认识的格式：
 * \[…\] 和 $$…$$ → 独占几行的 $$ 公式块；\(…\) 和 $…$ → 行内 $$…$$。行内代码里的不动；
 * 像 “$5 和 $10” 这样的金额不会被当成公式（开头结尾不能是空格，后面不能紧跟数字字母）。
 */
internal fun normalizeMath(src: String): String {
    if (!src.contains('$') && !src.contains("\\(") && !src.contains("\\[")) return src
    // 1) 公式块（可以跨行）
    var t = Regex("""\\\[([\s\S]+?)\\\]""").replace(src) { "\n$$\n" + it.groupValues[1].trim() + "\n$$\n" }
    t = Regex("""\$\$([\s\S]+?)\$\$""").replace(t) { m ->
        val body = m.groupValues[1]
        val lineStart = m.range.first == 0 || t[m.range.first - 1] == '\n'
        val lineEnd = m.range.last + 1 >= t.length || t[m.range.last + 1] == '\n'
        // 独占一行（或本来就跨行）的按公式块显示，句子中间的按行内公式
        if (body.contains('\n') || (lineStart && lineEnd)) "\n$$\n" + body.trim() + "\n$$\n"
        else "\u0000" + body.trim() + "\u0001"
    }
    // 2) 行内公式：跳过 `行内代码`
    val out = StringBuilder()
    var last = 0
    val inlineDollar = Regex("""(?<![\\$\w])\$(?![\s$])([^$\n]+?)(?<![\s\\])\$(?![\w$])""")
    val inlineParen = Regex("""\\\((.+?)\\\)""")
    fun conv(s: String) = inlineDollar.replace(inlineParen.replace(s) { "\u0000" + it.groupValues[1].trim() + "\u0001" }) {
        "\u0000" + it.groupValues[1] + "\u0001"
    }
    for (m in Regex("`+[^`]*`+").findAll(t)) {
        out.append(conv(t.substring(last, m.range.first))).append(m.value)
        last = m.range.last + 1
    }
    out.append(conv(t.substring(last)))
    return out.toString().replace("\u0000", "$$").replace("\u0001", "$$").replace(Regex("\n{3,}"), "\n\n")
}

/** 只打开网页类链接；对 http(s) 优先直接指定系统默认浏览器包名，避免被 B 站/知乎/淘宝等 App Links 劫持。 */
fun openWebLink(ctx: android.content.Context, url: String) {
    if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("mailto:") && !url.startsWith("tel:")) {
        android.widget.Toast.makeText(ctx, I18n.t("无法打开这个链接"), android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    if (url.startsWith("http://") || url.startsWith("https://")) {
        intent.addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        val probe = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://example.invalid"))
            .addCategory(android.content.Intent.CATEGORY_BROWSABLE)
        val browserPkg = runCatching {
            ctx.packageManager.resolveActivity(probe, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        }.getOrNull()
        if (!browserPkg.isNullOrBlank() && browserPkg != "android" && browserPkg != ctx.packageName) {
            intent.setPackage(browserPkg)
        }
    }
    runCatching {
        ctx.startActivity(intent)
    }.onFailure {
        // 指定默认浏览器失败时回退普通 ACTION_VIEW
        runCatching {
            intent.setPackage(null)
            ctx.startActivity(intent)
        }.onFailure {
            android.widget.Toast.makeText(ctx, I18n.t("没有能打开这个链接的应用"), android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}

/** 弹出系统应用选择器，让用户自由选择用哪个浏览器或对应 App（如 B 站/知乎等）打开链接。 */
fun openWebLinkChooser(ctx: android.content.Context, url: String) {
    if (!url.startsWith("http://") && !url.startsWith("https://") && !url.startsWith("mailto:") && !url.startsWith("tel:")) {
        android.widget.Toast.makeText(ctx, I18n.t("无法打开这个链接"), android.widget.Toast.LENGTH_SHORT).show()
        return
    }
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
    val chooser = android.content.Intent.createChooser(intent, I18n.t("选择打开应用"))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching {
        ctx.startActivity(chooser)
    }.onFailure {
        android.widget.Toast.makeText(ctx, I18n.t("没有能打开这个链接的应用"), android.widget.Toast.LENGTH_SHORT).show()
    }
}
