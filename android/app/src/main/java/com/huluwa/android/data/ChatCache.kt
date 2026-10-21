package com.huluwa.android.data

import android.content.Context
import kotlinx.serialization.json.*
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * 会话消息的本地缓存。
 *
 * 进入聊天页时先用这里最近一页的数据立即渲染，再在后台请求服务器无感刷新，
 * 避免每次进入都要先看一遍加载菊花。
 *
 * 约定：
 * - 只缓存已落库（id > 0）且发送成功的消息，避免「发送中 / 发送失败」的气泡在下次进入时复活；
 * - 每个会话只保留最新一页（[PAGE_SIZE] 条），文件体积可控；
 * - 文件放在应用私有目录，退出登录时随 [clear] 一并删除，切换服务器不会读到上一台的数据。
 */
class ChatCache(context: Context) {
    private val directory = File(context.filesDir, CACHE_DIRECTORY)
    // 进程内缓存：同一次启动里反复进出会话时可以直接同步取到，连磁盘读都省掉。
    private val memory = ConcurrentHashMap<String, List<ChatMessage>>()
    // 内容指纹：轮询刷新时消息通常没变，据此跳过重复写盘。
    private val lastSignature = ConcurrentHashMap<String, String>()

    /** 内存里已有的一页（同步、不读盘）；没有则返回 null，界面层据此决定是否先显示加载态。 */
    fun peek(sessionId: String): List<ChatMessage>? = memory[sessionId]

    /** 读取缓存的最近一页；文件不存在或内容损坏时返回空列表（损坏的文件顺带删除）。 */
    fun read(sessionId: String): List<ChatMessage> {
        memory[sessionId]?.let { return it }
        val file = fileFor(sessionId)
        if (!file.isFile) return emptyList()
        val rows = runCatching { decode(file.readText()) }.getOrElse {
            runCatching { file.delete() }
            emptyList()
        }
        if (rows.isNotEmpty()) memory[sessionId] = rows
        return rows
    }

    /**
     * 写入最近一页缓存。
     *
     * 先写临时文件再改名，避免进程在写入途中被杀导致缓存只写了一半；
     * 消息内容没有变化时直接跳过，轮询不会反复写盘。
     */
    fun write(sessionId: String, messages: List<ChatMessage>) {
        val rows = latestPage(messages)
        if (rows.isEmpty()) return
        memory[sessionId] = rows
        val signature = signature(rows)
        if (lastSignature[sessionId] == signature) return
        runCatching {
            if (!directory.isDirectory) directory.mkdirs()
            val target = fileFor(sessionId)
            val temp = File(directory, target.name + TEMP_SUFFIX)
            val content = encode(rows)
            temp.writeText(content)
            if (!temp.renameTo(target)) {
                target.writeText(content)
                temp.delete()
            }
        }.onSuccess { lastSignature[sessionId] = signature }
    }

    /** 删除单个会话的缓存（清空会话时调用）。 */
    fun remove(sessionId: String) {
        memory.remove(sessionId)
        lastSignature.remove(sessionId)
        runCatching { fileFor(sessionId).delete() }
    }

    /** 清空全部缓存（退出登录 / 切换服务器时调用）。 */
    fun clear() {
        memory.clear()
        lastSignature.clear()
        runCatching { directory.deleteRecursively() }
    }

    /** 会话 ID 可能包含任意字符，文件名统一用摘要，避免路径注入与超长文件名。 */
    private fun fileFor(sessionId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(sessionId.toByteArray(Charsets.UTF_8))
        // Byte 为负数时 %02x 会输出 ffffffxx，必须先按无符号取低 8 位。
        val name = digest.take(16).joinToString("") { "%02x".format(it.toInt() and 0xFF) }
        return File(directory, "$name.json")
    }

    private fun signature(rows: List<ChatMessage>) = "${rows.size}:${rows.first().id}:${rows.last().id}"

    companion object {
        /** 与接口默认分页一致：缓存一页就够「秒开」，更多历史仍按需从服务器拉取。 */
        const val PAGE_SIZE = 50
        private const val CACHE_DIRECTORY = "chat-cache"
        private const val TEMP_SUFFIX = ".tmp"

        /** 只保留已落库且发送成功的消息，按 id 去重排序后取最新一页。 */
        internal fun latestPage(messages: List<ChatMessage>, limit: Int = PAGE_SIZE): List<ChatMessage> =
            messages.filter { it.id > 0 && it.delivery == Delivery.Sent }
                .distinctBy { it.id }
                .sortedBy { it.id }
                .takeLast(limit)

        /** 缓存字段与接口保持一致，读回时直接复用 [ChatMessage.from]。 */
        internal fun encode(messages: List<ChatMessage>): String = JsonArray(messages.map { message ->
            buildJsonObject {
                put("id", message.id); put("sessionId", message.sessionId); put("role", message.role)
                put("type", message.type); put("content", message.content); put("contentText", message.contentText)
                put("payload", message.payload); put("createdAt", message.createdAt)
            }
        }).toString()

        internal fun decode(raw: String): List<ChatMessage> =
            ApiJson.parseToJsonElement(raw).asArray().map { ChatMessage.from(it.asObject()) }
    }
}
