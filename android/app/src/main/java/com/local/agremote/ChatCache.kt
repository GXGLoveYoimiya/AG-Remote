package com.local.agremote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 手机端本地对话历史缓存（内存 + 磁盘 JSON，最多保留最近 30 个对话）：
 * - 打开已缓存的对话时 0ms 直接上屏；
 * - 若电脑端 `updated` 与 `totalSteps` 未变且为 idle，无需再通过电脑传输；
 * - 若对话有更新（新增消息/修改/运行中），自动增量同步并覆盖本地缓存。
 */
data class CachedChat(
    val id: String,
    val updated: String,
    val totalSteps: Int,
    val loadedFrom: Int,
    val model: String,
    val steps: List<Step>,
)

class ChatCache(ctx: Context) {
    private val dir = File(ctx.cacheDir, "chat_cache").apply { mkdirs() }
    private val mem = ConcurrentHashMap<String, CachedChat>()

    fun getMemory(id: String): CachedChat? = mem[id]

    suspend fun load(id: String, api: Api): CachedChat? {
        mem[id]?.let { return it }
        return withContext(Dispatchers.IO) {
            val f = fileOf(id)
            if (!f.isFile) return@withContext null
            runCatching {
                val o = JSONObject(f.readText())
                if (o.optInt("v", 1) < 2) return@withContext null
                val a = o.optJSONArray("steps") ?: JSONArray()
                val steps = (0 until a.length()).map { api.parseStep(a.getJSONObject(it)) }
                CachedChat(
                    id = id,
                    updated = o.optString("updated"),
                    totalSteps = o.optInt("totalSteps", steps.size),
                    loadedFrom = o.optInt("loadedFrom", 0),
                    model = o.optString("model"),
                    steps = steps,
                ).also { mem[id] = it }
            }.getOrNull()
        }
    }

    suspend fun save(entry: CachedChat) {
        mem[entry.id] = entry
        withContext(Dispatchers.IO) {
            runCatching {
                val arr = JSONArray()
                entry.steps.forEach { arr.put(stepToJson(it)) }
                val o = JSONObject()
                    .put("v", 2)
                    .put("updated", entry.updated)
                    .put("totalSteps", entry.totalSteps)
                    .put("loadedFrom", entry.loadedFrom)
                    .put("model", entry.model)
                    .put("steps", arr)
                val tmp = File(dir, "${entry.id}.tmp")
                tmp.writeText(o.toString())
                tmp.renameTo(fileOf(entry.id))
                pruneOld()
            }
        }
    }

    fun remove(id: String) {
        mem.remove(id)
        runCatching { fileOf(id).delete() }
    }

    private fun fileOf(id: String): File = File(dir, "${id.replace(Regex("[^a-zA-Z0-9_-]"), "_")}.json")

    private fun pruneOld() {
        val files = dir.listFiles()?.filter { it.name.endsWith(".json") } ?: return
        if (files.size <= 60) return
        files.sortedByDescending { it.lastModified() }.drop(60).forEach { runCatching { it.delete() } }
    }

    private fun stepToJson(s: Step): JSONObject {
        val o = JSONObject()
            .put("i", s.i)
            .put("type", s.type)
            .put("kind", s.kind)
            .put("status", s.status)
            .put("title", s.title)
            .put("text", s.text)
            .put("sub", s.sub)
            .put("detail", s.detail)
            .put("thinking", s.thinking)
            .put("time", s.time)
            .put("trajectoryId", s.trajectoryId)
        if (s.attachments.isNotEmpty()) {
            val aa = JSONArray()
            s.attachments.forEach { a ->
                aa.put(JSONObject().put("name", a.name).put("mime", a.mime).put("path", a.path).put("ref", a.ref))
            }
            o.put("attachments", aa)
        }
        if (s.mentions.isNotEmpty()) {
            val ma = JSONArray()
            s.mentions.forEach { m -> ma.put(JSONObject().put("name", m.name).put("logo", m.logo)) }
            o.put("mentions", ma)
        }
        return o
    }
}
