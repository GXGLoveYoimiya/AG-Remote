package com.local.agremote.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 要预览的东西：名字、类型，以及怎么拿到内容。 */
class PreviewItem(val name: String, val mime: String, val load: suspend () -> ByteArray?)

private fun isText(mime: String, name: String): Boolean {
    if (mime.startsWith("text/") || mime == "application/json" || mime == "text/xml") return true
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("md", "markdown", "txt", "log", "json", "csv", "xml", "yaml", "yml", "js", "mjs", "ts", "py", "kt", "java",
        "c", "cpp", "h", "cs", "go", "rs", "sh", "ps1", "bat", "cmd", "css", "ini", "toml", "gradle", "kts", "html", "htm")
}

private fun isVideo(mime: String, name: String): Boolean {
    if (mime.startsWith("video/")) return true
    val ext = name.substringAfterLast('.', "").lowercase()
    return ext in setOf("mp4", "mov", "webm", "mkv", "m4v", "3gp")
}

/** 全屏预览：图片可双指缩放、双击放大；视频内嵌播放；文本在 App 里显示；其他文件交给手机上的应用打开。 */
@Composable
fun PreviewDialog(item: PreviewItem, onClose: () -> Unit) {
    val ctx = LocalContext.current
    val bytes by produceState<ByteArray?>(null, item) { value = runCatching { item.load() }.getOrNull() ?: ByteArray(0) }
    val image = item.mime.startsWith("image/")
    val video = !image && isVideo(item.mime, item.name)
    val darkStage = image || video
    val text = !darkStage && isText(item.mime, item.name)

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(if (darkStage) Color.Black else MaterialTheme.colorScheme.surface)) {
            val b = bytes
            when {
                b == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                b.isEmpty() -> Text(tr("读取失败"), Modifier.align(Alignment.Center), color = if (darkStage) Color.White else MaterialTheme.colorScheme.error)
                image -> ZoomImage(b)
                video -> VideoPlayer(b, item.name)
                text -> {
                    val s = remember(b) { b.toString(Charsets.UTF_8) }
                    Column(Modifier.fillMaxSize().statusBarsPadding().padding(top = 56.dp).verticalScroll(rememberScrollState()).padding(16.dp)) {
                        if (item.name.endsWith(".md", true) || item.mime == "text/markdown") Markdown(s, Modifier.fillMaxWidth())
                        else Box(Modifier.horizontalScroll(rememberScrollState())) {
                            SelectionContainer {
                                Text(s, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp)
                            }
                        }
                    }
                }
                else -> {
                    // 其他类型：交给手机上的应用打开
                    remember(b) { openExternally(ctx, b, item.name, item.mime); onClose(); 0 }
                }
            }
            // 顶部栏：文件名、用其他应用打开、关闭
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "关闭", tint = if (darkStage) Color.White else MaterialTheme.colorScheme.onSurface) }
                Text(item.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = if (darkStage) Color.White else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium)
                if (b != null && b.isNotEmpty()) IconButton(onClick = { openExternally(ctx, b, item.name, item.mime) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, "用其他应用打开", tint = if (darkStage) Color.White else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
private fun VideoPlayer(bytes: ByteArray, name: String) {
    val ctx = LocalContext.current
    val file = remember(bytes, name) {
        val dir = File(ctx.cacheDir, "preview").apply { mkdirs() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "video.mp4" }
        File(dir, "${bytes.size}_$safe").apply { writeBytes(bytes) }
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { c ->
                VideoView(c).apply {
                    val mc = MediaController(c)
                    mc.setAnchorView(this)
                    setMediaController(mc)
                    setVideoURI(Uri.fromFile(file))
                    setOnPreparedListener { mp ->
                        mp.isLooping = false
                        start()
                    }
                }
            },
            onRelease = { it.stopPlayback() },
        )
    }
}

@Composable
private fun ZoomImage(bytes: ByteArray) {
    val bmp = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
    if (bmp == null) { Box(Modifier.fillMaxSize()) { Text(tr("图片打不开"), Modifier.align(Alignment.Center), color = Color.White) }; return }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Image(
        bmp.asImageBitmap(), null,
        Modifier.fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(1f, 6f)
                    offset = if (scale == 1f) Offset.Zero else offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f })
            }
            .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offset.x, translationY = offset.y),
        contentScale = ContentScale.Fit,
    )
}

/** 存到缓存目录，再用系统“打开方式”打开。 */
fun openExternally(ctx: Context, bytes: ByteArray, name: String, mime: String) {
    runCatching {
        val dir = File(ctx.cacheDir, "preview").apply { mkdirs() }
        val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "file" }
        val f = File(dir, safe).apply { writeBytes(bytes) }
        val uri = androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime.ifBlank { "*/*" })
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        val launch = if (mime == "application/vnd.android.package-archive") i else Intent.createChooser(i, I18n.t("选择打开应用")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(launch)
    }.onFailure { Toast.makeText(ctx, I18n.t("没有能打开这个文件的应用"), Toast.LENGTH_SHORT).show() }
}

/** 预览本地还没发出去的附件。 */
fun localPreview(a: com.local.agremote.Attachment) = PreviewItem(a.name, a.mime) {
    withContext(Dispatchers.Default) { android.util.Base64.decode(a.base64, android.util.Base64.NO_WRAP) }
}
