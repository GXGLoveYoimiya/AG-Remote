package com.local.agremote

import java.util.concurrent.ConcurrentHashMap

/** 谷歌翻译：请求交给电脑端服务去发（走电脑那边的网络），手机按目标语言分别缓存结果。 */
object Translator {
    var api: Api? = null
    private val cache = ConcurrentHashMap<String, String>()

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

    suspend fun toChinese(text: String): String = translate(text, "zh-CN")
}
