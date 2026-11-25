package com.local.agremote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

/** 把相机/相册/文件选出来的东西读成附件。照片会缩到长边 2048 再转 JPEG，免得传得太慢。 */
object Attachments {
    private const val MAX_FILE = 40 * 1024 * 1024

    fun cameraUri(ctx: Context): Uri {
        val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "photo_${System.currentTimeMillis()}.jpg")
        return androidx.core.content.FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
    }

    suspend fun load(ctx: Context, uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val cr = ctx.contentResolver
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
        runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
            }
        }
        val mime = cr.getType(uri) ?: guessMime(name)
        val bytes = cr.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("读不到这个文件")
        loadBytes(name, mime, bytes, ctx, uri)
    }

    suspend fun loadBytes(name: String, mime: String, bytes: ByteArray, ctx: Context? = null, uri: Uri? = null): Attachment =
        withContext(Dispatchers.IO) {
            val safeName = name.ifBlank { "pasted_${System.currentTimeMillis()}.png" }
            val safeMime = mime.ifBlank { guessMime(safeName) }
            if (safeMime.startsWith("image/") && safeMime != "image/gif") {
                val full = decodeScaled(bytes, 2048) ?: throw IllegalStateException("图片打不开")
                val out = ByteArrayOutputStream()
                full.compress(Bitmap.CompressFormat.JPEG, 88, out)
                val jpg = out.toByteArray()
                val thumb = Bitmap.createScaledBitmap(full, 160, (160f * full.height / full.width).toInt().coerceAtLeast(1), true)
                val n = if (safeName.contains('.')) safeName.substringBeforeLast('.') + ".jpg" else "$safeName.jpg"
                Attachment(n, "image/jpeg", Base64.encodeToString(jpg, Base64.NO_WRAP), jpg.size, thumb)
            } else {
                if (bytes.size > MAX_FILE) throw IllegalStateException("文件太大（超过 40MB）")
                val thumb = if (safeMime.startsWith("video/") && ctx != null && uri != null) videoThumbFromUri(ctx, uri) else null
                Attachment(safeName, safeMime, Base64.encodeToString(bytes, Base64.NO_WRAP), bytes.size, thumb)
            }
        }

    private fun videoThumbFromUri(ctx: Context, uri: Uri): Bitmap? = runCatching {
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(ctx, uri)
            val frame = mmr.getFrameAtTime(0) ?: mmr.frameAtTime ?: return null
            scaleThumb(frame, 240)
        } finally { runCatching { mmr.release() } }
    }.getOrNull()

    fun videoThumbFromFile(f: File): Bitmap? = runCatching {
        val mmr = android.media.MediaMetadataRetriever()
        try {
            mmr.setDataSource(f.absolutePath)
            val frame = mmr.getFrameAtTime(0) ?: mmr.frameAtTime ?: return null
            scaleThumb(frame, 240)
        } finally { runCatching { mmr.release() } }
    }.getOrNull()

    private fun scaleThumb(bmp: Bitmap, maxSide: Int): Bitmap {
        val side = maxOf(bmp.width, bmp.height).coerceAtLeast(1)
        if (side <= maxSide) return bmp
        val k = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt().coerceAtLeast(1), (bmp.height * k).toInt().coerceAtLeast(1), true)
    }

    private fun decodeScaled(bytes: ByteArray, maxSide: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: return null
        val side = maxOf(bmp.width, bmp.height)
        if (side <= maxSide) return bmp
        val k = maxSide.toFloat() / side
        return Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true)
    }

    fun mimeOf(name: String) = guessMime(name)

    private fun guessMime(name: String) = when (name.substringAfterLast('.', "").lowercase()) {
        "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "webp" -> "image/webp"; "gif" -> "image/gif"
        "pdf" -> "application/pdf"; "txt", "log" -> "text/plain"; "md" -> "text/markdown"; "json" -> "application/json"
        "csv" -> "text/csv"; "html", "htm" -> "text/html"; "zip" -> "application/zip"; "apk" -> "application/vnd.android.package-archive"
        "markdown" -> "text/markdown"; "bmp" -> "image/bmp"
        "mp4", "m4v" -> "video/mp4"; "mov" -> "video/quicktime"; "webm" -> "video/webm"; "mkv" -> "video/x-matroska"; "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"; "wav" -> "audio/wav"
        "kt", "kts", "java", "py", "js", "mjs", "ts", "c", "cpp", "h", "go", "rs", "sh", "ps1", "bat", "cmd", "css", "ini",
        "toml", "yaml", "yml", "xml", "gradle" -> "text/plain"
        else -> "application/octet-stream"
    }
}
