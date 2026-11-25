package com.local.agremote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * 后台常驻：保持和电脑端服务的 SSE 连接，agent 完成或需要你确认时发通知。
 * 用前台服务（remoteMessaging 类型）保活，通知栏会有一条低优先级的常驻通知。
 */
class WatchService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var loop: Job? = null
    private lateinit var prefs: Prefs
    private lateinit var api: Api

    /** 每个对话上一次看到的状态，用来判断“刚完成”“刚开始等确认”。 */
    private data class Seen(val status: String, val waiting: Boolean, val steps: Int)
    private val seen = HashMap<String, Seen>()
    private var primed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        api = Api(prefs)
        ensureChannels(this)
        val n = NotificationCompat.Builder(this, CH_SERVICE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("正在后台接收消息")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setContentIntent(openApp(this, null, 0))
            .build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID_SERVICE, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_REMOTE_MESSAGING)
        else startForeground(ID_SERVICE, n)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (prefs.token.isBlank() || !prefs.notify) { stopSelf(); return START_NOT_STICKY }
        if (loop?.isActive != true) loop = scope.launch { run() }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun run() {
        var backoff = 2000L
        while (scope.isActive) {
            try {
                check() // 先拿一次当前状态做基准
                api.streamClient.newCall(api.eventsRequest()).execute().use { resp ->
                    if (!resp.isSuccessful) throw ApiException(resp.code, "HTTP ${resp.code}")
                    backoff = 2000L
                    val src = resp.body.source()
                    while (scope.isActive) {
                        val line = src.readUtf8Line() ?: break
                        if (!line.startsWith("data: ")) continue
                        if (JSONObject(line.removePrefix("data: ")).optString("type") == "changed") check()
                    }
                }
            } catch (_: Exception) {
            }
            delay(backoff)
            backoff = (backoff * 2).coerceAtMost(60_000L)
        }
    }

    /** 拉一次对话列表，和上次比，决定要不要通知。 */
    private suspend fun check() {
        val list = runCatching { api.conversations() }.getOrNull() ?: return
        for (c in list) {
            val now = Seen(c.status, c.waiting, c.steps)
            val before = seen[c.id]
            seen[c.id] = now
            if (!primed || before == null) continue
            val wasRunning = before.status == "running" || before.status == "busy"
            when {
                c.waiting && !before.waiting -> notifyConv(c, "需要你确认，点开处理", important = true)
                wasRunning && c.status == "idle" -> notifyConv(c, lastReply(c), important = false)
            }
        }
        primed = true
    }

    private suspend fun lastReply(c: Conversation): String = runCatching {
        api.conversation(c.id, (c.steps - 4).coerceAtLeast(0)).steps
            .lastOrNull { (it.kind == "assistant" || it.kind == "error") && it.text.isNotBlank() }?.text
            ?.replace(Regex("[#*`>_]+"), "")?.trim()?.take(300)
    }.getOrNull() ?: "点开查看结果"

    private fun notifyConv(c: Conversation, text: String, important: Boolean) {
        if (AppState.foreground) return // App 在前台就不打扰
        val id = c.id.hashCode()
        val appName = getString(R.string.app_name)
        val title = if (c.title.isNotBlank()) "$appName · ${c.title}" else appName
        val n = NotificationCompat.Builder(this, if (important) CH_WAITING else CH_DONE)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(if (important) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (important) NotificationCompat.CATEGORY_REMINDER else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(openApp(this, c.id, id))
            .build()
        runCatching { NotificationManagerCompat.from(this).notify(id, n) }
    }

    companion object {
        const val CH_SERVICE = "watch"
        const val CH_DONE = "done"
        const val CH_WAITING = "waiting"
        const val ID_SERVICE = 1
        const val EXTRA_CONV = "conv"

        fun ensureChannels(ctx: Context) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel(CH_WAITING, "需要你确认", NotificationManager.IMPORTANCE_HIGH)
                .apply { description = "agent 停下来等你批准时提醒" })
            nm.createNotificationChannel(NotificationChannel(CH_DONE, "新消息回复", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "对话有新回复时提醒" })
            nm.createNotificationChannel(NotificationChannel(CH_SERVICE, "后台连接", NotificationManager.IMPORTANCE_MIN)
                .apply { description = "保持和电脑的连接（可以在系统设置里把这条通知隐藏）"; setShowBadge(false) })
        }

        fun openApp(ctx: Context, convId: String?, req: Int): PendingIntent {
            val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (convId != null) i.putExtra(EXTRA_CONV, convId)
            return PendingIntent.getActivity(ctx, req, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        fun start(ctx: Context) {
            val p = Prefs(ctx)
            if (p.token.isBlank() || !p.notify) return
            runCatching { ctx.startForegroundService(Intent(ctx, WatchService::class.java)) }
        }

        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, WatchService::class.java)) }
    }
}

/** App 是否在前台（前台时不弹通知）。 */
object AppState { @Volatile var foreground = false }

/** 开机后自动连上。 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) WatchService.start(ctx)
    }
}
