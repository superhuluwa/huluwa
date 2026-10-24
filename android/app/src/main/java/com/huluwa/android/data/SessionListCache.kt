package com.huluwa.android.data

import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

/** 功能 / 系统列表的展示快照：按服务器和账号隔离，只保存列表所需字段。 */
class SessionListCache(context: Context, private val store: SessionStore) {
    data class Scope internal constructor(val server: String, val username: String, internal val version: Long)

    private val directory = File(context.filesDir, "session-list-cache")
    private val memory = mutableMapOf<String, List<Session>>()
    private val lastWritten = mutableMapOf<String, String>()
    @Volatile private var version = 0L

    fun scope(): Scope? = if (store.loggedIn) Scope(store.server, store.username, version) else null

    @Synchronized fun read(scope: Scope): List<Session>? {
        if (!isCurrent(scope)) return null
        val key = key(scope)
        memory[key]?.let { return it }
        val file = AtomicFile(File(directory, "$key.json"))
        return runCatching {
            val content = file.openRead().bufferedReader(Charsets.UTF_8).use { it.readText() }
            val array = ApiJson.parseToJsonElement(content) as? JsonArray ?: error("列表缓存格式错误")
            val rows = array.map { element ->
                val row = element as? JsonObject ?: error("列表缓存条目错误")
                require(row.text("id").isNotBlank())
                Session(row.text("id"), row.text("title"), row.text("kind"), row.number("unread")?.toInt() ?: 0,
                    null, row.text("preview"), row.text("updatedAt"))
            }
            memory[key] = rows
            lastWritten[key] = encode(rows)
            rows
        }.getOrElse {
            runCatching { file.delete() }
            null
        }
    }

    /** 原子写入，内容未变时跳过写盘；退出登录后排队中的旧快照会自动失效。 */
    @Synchronized fun write(scope: Scope, sessions: List<Session>) {
        if (!isCurrent(scope)) return
        val key = key(scope)
        val rows = sessions.map { it.copy(last = null, previewOverride = it.preview) }
        memory[key] = rows
        val content = encode(rows)
        if (lastWritten[key] == content) return
        val file = AtomicFile(File(directory, "$key.json"))
        var output: FileOutputStream? = null
        runCatching {
            check(directory.isDirectory || directory.mkdirs())
            val stream = file.startWrite()
            output = stream
            stream.write(content.toByteArray(Charsets.UTF_8))
            file.finishWrite(stream)
        }.onSuccess { lastWritten[key] = content }
            .onFailure { output?.let { stream -> runCatching { file.failWrite(stream) } } }
    }

    @Synchronized fun clear() {
        version++
        memory.clear()
        lastWritten.clear()
        runCatching { directory.deleteRecursively() }
    }

    private fun isCurrent(scope: Scope) = scope.version == version && store.loggedIn
        && scope.server == store.server && scope.username == store.username

    private fun key(scope: Scope): String = MessageDigest.getInstance("SHA-256")
        .digest("${scope.server}\u0000${scope.username}".toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 0xFF) }

    private fun encode(rows: List<Session>) = JsonArray(rows.map { row ->
        buildJsonObject {
            put("id", row.id); put("title", row.title); put("kind", row.kind); put("unread", row.unread)
            put("preview", row.preview); put("updatedAt", row.updatedAt)
        }
    }).toString()
}
