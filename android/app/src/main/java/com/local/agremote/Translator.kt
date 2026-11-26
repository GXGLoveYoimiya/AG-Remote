package com.local.agremote

import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object Translator {
    var api: Api? = null
    private val cache = ConcurrentHashMap<String, String>()
    // 插件简介、技能说明这类基本不变的译文存在手机上，下次打开直接用（思考过程这类不存）
    private val kept = ConcurrentHashMap<String, String>()
    private var file: File? = null

    /** 读回上次存下的译文；重复调用没关系。 */
    fun init(dir: File) {
        if (file != null) return
        file = File(dir, "translations.json").also { f ->
            runCatching {
                val o = JSONObject(f.readText())
                o.keys().forEach { k -> o.optString(k).takeIf { it.isNotBlank() }?.let { kept[k] = it; cache[k] = it } }
            }
        }
    }

    private fun save() {
        val f = file ?: return
        runCatching {
            if (kept.size > 20000) kept.keys.take(kept.size - 20000).forEach { kept.remove(it) }
            f.writeText(JSONObject(kept as Map<*, *>).toString())
        }
    }

    private fun googleTarget(lang: String): String = when (lang) {
        "zh-HK", "zh-TW" -> "zh-TW"
        "zh-CN", "" -> "zh-CN"
        else -> lang
    }

    private fun key(text: String, targetLang: String) = "${googleTarget(targetLang)}\u0000$text"

    fun cached(text: String, targetLang: String = "zh-CN"): String? =
        if (text.isBlank()) text else cache[key(text, targetLang)]

    suspend fun translate(text: String, targetLang: String = "zh-CN"): String {
        if (text.isBlank()) return text
        val k = key(text, targetLang)
        cache[k]?.let { return it }
        val a = api ?: throw IllegalStateException("还没连上电脑")
        val out = a.translate(text, googleTarget(targetLang))
        cache[k] = out
        return out
    }

    /** 提前批量翻译不常变的内容并存下来；已经有译文的跳过。 */
    suspend fun prefetch(texts: Collection<String>, targetLang: String) {
        val a = api ?: return
        val need = texts.filter { it.isNotBlank() && cache[key(it, targetLang)] == null }.distinct()
        if (need.isEmpty()) return
        for (chunk in need.chunked(150)) {
            val map = a.translateMany(chunk, googleTarget(targetLang))
            map.forEach { (src, out) -> if (out.isNotBlank()) { val k = key(src, targetLang); cache[k] = out; kept[k] = out } }
        }
        save()
    }

    suspend fun toChinese(text: String): String = translate(text, "zh-CN")
}
