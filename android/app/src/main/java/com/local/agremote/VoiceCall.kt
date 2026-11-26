package com.local.agremote

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Antigravity 2.35+ 的实时语音通话（电脑上 language_server 的 /voice/ws，经电脑端服务原样转发）。
 * 上行：VoiceClientMessage.mic_audio（16 kHz 单声道 s16le），每 40 ms 一包；
 * 下行：VoiceServerMessage —— voice_audio（24 kHz s16le）、status、user_speech（你说的话）、
 *       voice_transcript（AI 说的话）、clear_audio（被你打断，立刻停播）。播放速度由服务端处理。
 */
class VoiceCall(private val ctx: Context, private val api: Api) {
    enum class Phase { Connecting, Listening, Retrying, Ended, Failed }
    data class Line(val user: Boolean, val seq: Int, val text: String, val interim: Boolean = false)

    private val _phase = MutableStateFlow(Phase.Connecting); val phase = _phase.asStateFlow()
    private val _lines = MutableStateFlow<List<Line>>(emptyList()); val lines = _lines.asStateFlow()
    private val _convId = MutableStateFlow<String?>(null); val convId = _convId.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    private val _micLevel = MutableStateFlow(0f); val micLevel = _micLevel.asStateFlow()
    private val _aiLevel = MutableStateFlow(0f); val aiLevel = _aiLevel.asStateFlow()
    private val _muted = MutableStateFlow(false); val muted = _muted.asStateFlow()
    private val _speaker = MutableStateFlow(true); val speaker = _speaker.asStateFlow()
    private val _startedAt = MutableStateFlow(0L); val startedAt = _startedAt.asStateFlow()

    @Volatile private var ws: WebSocket? = null
    @Volatile private var running = false
    private var recThread: Thread? = null
    private var playThread: Thread? = null
    private var track: AudioTrack? = null
    private val playQueue = LinkedBlockingQueue<ByteArray>()
    @Volatile private var volume = 1f
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var prevMode = AudioManager.MODE_NORMAL

    /** 开始通话。convId 为空时电脑端会先建一个新对话，status 里会带回对话 ID。 */
    fun start(convId: String?, model: String, voice: String, speed: Double, volumePercent: Int) {
        if (running) return
        running = true
        volume = (volumePercent / 100f).coerceIn(0f, 1f)
        _convId.value = convId
        _phase.value = Phase.Connecting
        _startedAt.value = System.currentTimeMillis()
        setupAudioRoute()
        startPlayer()
        ws = api.voiceSocket(convId.orEmpty(), model, voice, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (speed != 1.0) sendSettings(speed)
                startRecorder()
            }
            override fun onMessage(webSocket: WebSocket, bytes: ByteString) = handle(bytes.toByteArray())
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { if (running) finish(null) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                if (!running) return
                finish(when (response?.code) {
                    401 -> "未配对"
                    503 -> "找不到 Antigravity，请确认它正在运行"
                    404 -> "电脑端服务版本太旧，请更新"
                    else -> t.message ?: "连接断开"
                })
            }
        })
    }

    fun stop() { if (running) finish(null) }

    fun setMuted(m: Boolean) { _muted.value = m }

    fun setVolume(percent: Int) { volume = (percent / 100f).coerceIn(0f, 1f); track?.setVolume(volume) }

    /** 播放速度：服务端按这个速度生成/调整 AI 的语音。 */
    fun setSpeed(speed: Double) = sendSettings(speed)

    fun setSpeaker(on: Boolean) { _speaker.value = on; routeSpeaker(on) }

    // ---------------- 音频 ----------------

    private fun setupAudioRoute() {
        prevMode = am.mode
        am.mode = AudioManager.MODE_IN_COMMUNICATION // 让系统的回声消除生效（外放时不会把 AI 的声音录回去）
        routeSpeaker(_speaker.value)
    }

    @Suppress("DEPRECATION")
    private fun routeSpeaker(on: Boolean) {
        if (Build.VERSION.SDK_INT >= 31) {
            if (on) am.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
                ?.let { am.setCommunicationDevice(it) }
            else am.clearCommunicationDevice()
        } else am.isSpeakerphoneOn = on
    }

    private fun startPlayer() {
        val rate = 24000
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(maxOf(min, rate / 5 * 2)) // 约 200 ms，够平滑又不拖延迟
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        t.setVolume(volume)
        t.play()
        track = t
        playThread = Thread({
            while (running) {
                val buf = playQueue.poll(200, TimeUnit.MILLISECONDS)
                if (buf == null) { _aiLevel.value = 0f; continue }
                _aiLevel.value = level(buf, buf.size)
                t.write(buf, 0, buf.size)
            }
        }, "voice-play").apply { start() }
    }

    @SuppressLint("MissingPermission")
    private fun startRecorder() {
        recThread = Thread({
            val rate = 16000
            val frame = ByteArray(rate / 25 * 2) // 40 ms
            val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            val rec = runCatching {
                AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frame.size * 4))
            }.getOrNull()
            if (rec == null || rec.state != AudioRecord.STATE_INITIALIZED) { finish("无法打开麦克风"); return@Thread }
            val aec = if (AcousticEchoCanceler.isAvailable()) runCatching { AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
            val ns = if (NoiseSuppressor.isAvailable()) runCatching { NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } }.getOrNull() else null
            try {
                rec.startRecording()
                val silence = ByteArray(frame.size)
                while (running) {
                    val n = rec.read(frame, 0, frame.size)
                    if (n <= 0) break
                    val muted = _muted.value
                    _micLevel.value = if (muted) 0f else level(frame, n)
                    ws?.send(micAudio(if (muted) silence else frame, n))
                }
            } catch (_: Exception) {
            } finally {
                runCatching { rec.stop() }; rec.release(); aec?.release(); ns?.release()
            }
        }, "voice-rec").apply { start() }
    }

    private fun level(buf: ByteArray, n: Int): Float {
        var sum = 0.0; var i = 0
        while (i + 1 < n) { val v = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xff)).toShort().toDouble(); sum += v * v; i += 2 }
        val rms = kotlin.math.sqrt(sum / (n / 2).coerceAtLeast(1))
        val db = 20 * kotlin.math.log10(rms.coerceAtLeast(1.0))
        return ((db - 35) / 40).toFloat().coerceIn(0f, 1f)
    }

    private fun finish(err: String?) {
        if (!running) return
        running = false
        _error.value = err
        _phase.value = if (err == null) Phase.Ended else Phase.Failed
        runCatching { ws?.close(1000, "bye") }
        ws = null
        playQueue.clear()
        runCatching { track?.pause(); track?.flush(); track?.stop(); track?.release() }
        track = null
        _micLevel.value = 0f; _aiLevel.value = 0f
        runCatching {
            if (Build.VERSION.SDK_INT >= 31) am.clearCommunicationDevice() else @Suppress("DEPRECATION") { am.isSpeakerphoneOn = false }
            am.mode = prevMode
        }
    }

    // ---------------- 协议 ----------------

    private fun handle(b: ByteArray) {
        for (f in Pb.parse(b)) when (f.no) {
            1 -> if (running) playQueue.offer(f.bytes)                          // voice_audio
            2 -> {                                                              // status
                val s = Pb.parse(f.bytes)
                Pb.str(s, 6).takeIf { it.isNotBlank() }?.let { _convId.value = it }
                when (Pb.int(s, 1)) {
                    1 -> _phase.value = Phase.Connecting
                    2 -> _phase.value = Phase.Listening
                    3 -> _phase.value = Phase.Retrying
                    5 -> finish(Pb.str(s, 3).ifBlank { failureText(Pb.int(s, 2)) })
                }
            }
            7 -> {                                                              // voice_transcript：AI 说的话（分段到达）
                val s = Pb.parse(f.bytes)
                appendAi(Pb.int(s, 2), Pb.str(s, 1))
            }
            8 -> {                                                              // user_speech：识别出的你说的话
                val s = Pb.parse(f.bytes)
                upsertUser(Pb.int(s, 2), Pb.str(s, 1).trim(), Pb.int(s, 3) != 0)
            }
            9 -> { playQueue.clear(); track?.let { runCatching { it.pause(); it.flush(); it.play() } } } // clear_audio：被打断
        }
    }

    private fun failureText(reason: Int) = when (reason) {
        1 -> "语音服务暂时不可用"
        2 -> "请求太频繁，请稍后再试"
        5 -> "没有使用实时语音的权限"
        6 -> "连接超时"
        9 -> "对话已不存在"
        else -> "语音通话失败"
    }

    private fun upsertUser(seq: Int, text: String, interim: Boolean) {
        if (text.isEmpty()) return
        val cur = _lines.value
        val i = cur.indexOfLast { it.user && it.seq == seq }
        _lines.value = if (i >= 0) cur.toMutableList().also { it[i] = Line(true, seq, text, interim) } else cur + Line(true, seq, text, interim)
    }

    private fun appendAi(seq: Int, text: String) {
        if (text.isEmpty()) return
        val cur = _lines.value
        val last = cur.lastOrNull()
        _lines.value = if (last != null && !last.user && last.seq == seq) cur.dropLast(1) + last.copy(text = last.text + text)
        else cur + Line(false, seq, text.trimStart())
    }

    private fun micAudio(pcm: ByteArray, n: Int): ByteString {
        val o = ByteArrayOutputStream(n + 8)
        Pb.writeTag(o, 2, 2); Pb.writeVarint(o, n.toLong()); o.write(pcm, 0, n)
        return o.toByteArray().toByteString()
    }

    private fun sendSettings(speed: Double) {
        val inner = ByteArrayOutputStream()
        Pb.writeTag(inner, 2, 1); val bits = java.lang.Double.doubleToLongBits(speed)
        for (k in 0 until 8) inner.write(((bits ushr (8 * k)) and 0xff).toInt())
        val o = ByteArrayOutputStream()
        Pb.writeTag(o, 3, 2); Pb.writeVarint(o, inner.size().toLong()); o.write(inner.toByteArray())
        ws?.send(o.toByteArray().toByteString())
    }
}

/** 够用的 protobuf 读写（语音协议只用到 varint / 长度前缀 / 64 位定长）。 */
object Pb {
    class Field(val no: Int, val wire: Int, val num: Long, val bytes: ByteArray)

    fun parse(b: ByteArray): List<Field> {
        val out = ArrayList<Field>(); var i = 0
        fun varint(): Long { var r = 0L; var s = 0; while (i < b.size) { val c = b[i++].toInt() and 0xff; r = r or ((c and 0x7f).toLong() shl s); if (c < 0x80) break; s += 7 }; return r }
        while (i < b.size) {
            val tag = varint(); val no = (tag ushr 3).toInt(); val wt = (tag and 7).toInt()
            when (wt) {
                0 -> out += Field(no, wt, varint(), ByteArray(0))
                2 -> { val n = varint().toInt(); if (n < 0 || i + n > b.size) return out; out += Field(no, wt, 0, b.copyOfRange(i, i + n)); i += n }
                1 -> { if (i + 8 > b.size) return out; out += Field(no, wt, 0, b.copyOfRange(i, i + 8)); i += 8 }
                5 -> { if (i + 4 > b.size) return out; out += Field(no, wt, 0, b.copyOfRange(i, i + 4)); i += 4 }
                else -> return out
            }
        }
        return out
    }
    fun str(fs: List<Field>, no: Int) = fs.firstOrNull { it.no == no && it.wire == 2 }?.bytes?.toString(Charsets.UTF_8).orEmpty()
    fun int(fs: List<Field>, no: Int) = fs.firstOrNull { it.no == no && it.wire == 0 }?.num?.toInt() ?: 0
    fun writeVarint(o: ByteArrayOutputStream, v: Long) { var x = v; while (x and 0x7fL.inv() != 0L) { o.write(((x and 0x7f) or 0x80).toInt()); x = x ushr 7 }; o.write(x.toInt()) }
    fun writeTag(o: ByteArrayOutputStream, no: Int, wire: Int) = writeVarint(o, ((no shl 3) or wire).toLong())
}
