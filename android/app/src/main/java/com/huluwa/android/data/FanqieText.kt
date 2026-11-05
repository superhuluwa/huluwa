package com.huluwa.android.data

import android.text.Html
import kotlinx.serialization.json.JsonObject

private val fanqieHtmlEntity = Regex("&(?:#([0-9]+)|#[xX]([0-9a-fA-F]+)|[A-Za-z][A-Za-z0-9]+);")

/** 兼容旧接口和缓存中的 HTML 实体，只处理实体，不把正文作为 HTML 重新排版。 */
internal fun JsonObject.fanqieText(key: String): String {
    val value = text(key)
    if ('&' !in value) return value
    return fanqieHtmlEntity.replace(value) { match ->
        val decimal = match.groupValues[1]
        val hex = match.groupValues[2]
        val decoded = if (decimal.isNotEmpty() || hex.isNotEmpty()) {
            val codePoint = if (decimal.isNotEmpty()) decimal.toIntOrNull() else hex.toIntOrNull(16)
            if (codePoint == null || codePoint == 0 || !Character.isValidCodePoint(codePoint) ||
                codePoint in 0xD800..0xDFFF) return@replace match.value
            String(Character.toChars(codePoint))
        } else {
            if (match.value == "&apos;") "'"
            else Html.fromHtml(match.value, Html.FROM_HTML_MODE_COMPACT).toString()
        }
        decoded.replace('\u00a0', ' ')
    }
}
