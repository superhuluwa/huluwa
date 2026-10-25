package com.huluwa.android.data

import android.content.Context
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import java.security.MessageDigest

/** 按服务器和账号保存置顶顺序，独立于会被刷新、退出登录清理的展示缓存。 */
class SessionListPreferences(context: Context) {
    private val preferences = context.getSharedPreferences("huluwa_session_list", Context.MODE_PRIVATE)

    fun pinnedSessions(server: String, username: String): List<String> = runCatching {
        val content = preferences.getString(key(server, username), null) ?: return@runCatching emptyList()
        val rows = ApiJson.parseToJsonElement(content) as? JsonArray ?: return@runCatching emptyList()
        rows.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.takeIf(String::isNotBlank) }.distinct()
    }.getOrDefault(emptyList())

    fun savePinnedSessions(server: String, username: String, ids: List<String>) {
        val content = JsonArray(ids.distinct().map(::JsonPrimitive)).toString()
        preferences.edit().putString(key(server, username), content).apply()
    }

    private fun key(server: String, username: String): String = MessageDigest.getInstance("SHA-256")
        .digest("$server\u0000$username".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }
}
