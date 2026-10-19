package com.huluwa.android.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.Locale

val ApiJson = Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }

fun JsonElement?.asObject(): JsonObject = this as? JsonObject ?: JsonObject(emptyMap())
fun JsonElement?.asArray(): List<JsonElement> = (this as? JsonArray)?.toList().orEmpty()
fun JsonObject.text(vararg keys: String, fallback: String = ""): String = keys.firstNotNullOfOrNull {
    (this[it] as? JsonPrimitive)?.contentOrNull?.takeIf { value -> value != "null" }
} ?: fallback
fun JsonObject.number(vararg keys: String): Double? = keys.firstNotNullOfOrNull {
    (this[it] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()?.takeIf(Double::isFinite)
}
fun JsonObject.flag(key: String, fallback: Boolean = false): Boolean = when (text(key).lowercase(Locale.ROOT)) {
    "true", "1" -> true
    "false", "0" -> false
    else -> fallback
}
fun JsonObject.child(key: String): JsonObject = this[key].asObject()
fun JsonObject.rows(vararg keys: String): List<JsonElement> = keys.firstNotNullOfOrNull { this[it] as? JsonArray }?.toList().orEmpty()
fun jsonBody(block: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject(block)

@Serializable data class Credentials(val username: String, val password: String)
@Serializable data class SendMessageRequest(val content: String, val requestId: String)
@Serializable data class EnabledRequest(val enabled: Boolean)
@Serializable data class TunnelDraft(
    val name: String = "", val proxyType: String = "tcp", val localIp: String = "127.0.0.1",
    val localPort: Int? = null, val remotePort: Int? = null, val customDomains: String = "",
    val subdomain: String = "", val remark: String = "", val encryption: Boolean = false,
    val compression: Boolean = false, val enabled: Boolean = true,
)

enum class MainTab(val title: String) {
    Sessions("我的会话"), Downloads("我的下载"), Subscriptions("我的订阅"), Frp("Frp 管理"), Docker("Docker 管理")
}
enum class DownloadSource(val title: String) { Hls("离线剧集"), Magnet("磁力下载"), XunleiWin("迅雷【Win】") }
enum class SubscriptionSource(val title: String) { Hls("电影天堂"), Movie("电影港") }
enum class Delivery { Sent, Sending, Failed }

data class Session(val id: String, val title: String, val kind: String, val unread: Int, val last: ChatMessage?) {
    val preview: String get() = last?.let { it.contentText.ifBlank { it.content } }?.replace(Regex("<[^>]+>"), "")?.take(160).orEmpty().ifBlank { "发条消息，开始使用这个服务" }
    companion object {
        fun from(raw: JsonObject) = Session(raw.text("id"), raw.text("title"), raw.text("kind"), raw.number("unread")?.toInt() ?: 0,
            (raw["last"] as? JsonObject)?.let(ChatMessage::from))
    }
}
data class ChatMessage(
    val id: Long, val sessionId: String, val role: String, val type: String, val content: String,
    val contentText: String, val payload: JsonObject, val createdAt: String,
    val delivery: Delivery = Delivery.Sent, val requestId: String? = null,
) {
    val mine get() = role == "user"
    val notify: NotifyMessage? get() = if (type == "notify") NotifyMessage.from(this) else null
    val film: FilmCard? get() = payload.takeIf { it.text("title").isNotBlank() && it.containsKey("cover") && it.containsKey("url") && it.text("kind") != "video" }?.let(FilmCard::from)
    companion object {
        fun from(raw: JsonObject): ChatMessage {
            val payload = when (val value = raw["payload"]) {
                is JsonObject -> value
                is JsonPrimitive -> runCatching { ApiJson.parseToJsonElement(value.content).asObject() }.getOrDefault(JsonObject(emptyMap()))
                else -> JsonObject(emptyMap())
            }
            return ChatMessage(raw.number("id")?.toLong() ?: 0L, raw.text("sessionId"), raw.text("role"), raw.text("type"),
                raw.text("content"), raw.text("contentText"), payload, raw.text("createdAt"))
        }
    }
}
data class NotifyMessage(
    val level: String, val source: String, val title: String, val body: String,
) {
    val levelLabel: String get() = when (level) {
        "success" -> "成功"
        "warning" -> "警告"
        "error" -> "错误"
        else -> "信息"
    }
    companion object {
        fun from(message: ChatMessage): NotifyMessage {
            val meta = message.payload
            val title = meta.text("title").ifBlank { message.contentText }
            val body = message.content.ifBlank { if (message.contentText != title) message.contentText else "" }
            return NotifyMessage(meta.text("level", fallback = "info"), meta.text("source"), title, body)
        }
    }
}
data class FilmCard(
    val id: String, val title: String, val cover: String, val url: String, val source: String,
    val year: String, val genre: String, val origin: String, val apiUrl: String,
    val sources: List<PlaySource>, val subscribed: Boolean,
) {
    val isHls get() = source == "dytt"
    companion object {
        fun from(raw: JsonObject) = FilmCard(raw.text("id", "resId"), raw.text("title"), raw.text("cover"), raw.text("url"), raw.text("source"),
            raw.text("year"), raw.text("genre"), raw.text("origin"), raw.text("apiUrl"), raw.rows("playSources").map { PlaySource.from(it.asObject()) }, raw.flag("subscribed"))
    }
}
data class PlaySource(val name: String, val items: List<PlayItem>) {
    companion object { fun from(raw: JsonObject) = PlaySource(raw.text("name"), raw.rows("items").map { PlayItem(it.asObject().text("title"), it.asObject().text("url")) }) }
}
data class PlayItem(val title: String, val url: String)

data class DownloadTask(
    val id: String, val source: DownloadSource, val title: String, val episode: String, val status: String,
    val progress: Float?, val speed: String, val downloaded: Double?, val total: Double?,
    val savePath: String, val message: String, val createdAt: String, val paused: Boolean,
) {
    val key get() = "${source.name}:$id"
    val active get() = status in setOf("pending", "added", "submitted", "queued", "downloading", "verifying", "running", "paused")
    val completed get() = status in setOf("completed", "finished", "complete", "success", "seeding", "uploading")
    val retryable get() = source == DownloadSource.Hls && status in setOf("failed", "cancelled", "skipped") && !paused
    val cancellable get() = source == DownloadSource.Hls && active
    companion object {
        fun from(raw: JsonObject, source: DownloadSource): DownloadTask {
            val status = raw.text("status", "state", fallback = "unknown").lowercase(Locale.ROOT)
            val episode = raw.text("episodeTitle").ifBlank {
                val value = raw.text("episode")
                if (value.isBlank()) "" else if (source == DownloadSource.Hls) "第 ${value} 集" else value
            }
            var progress = raw.number("progressPercent")
            if (progress == null) raw.number("progress")?.let { progress = it }
            if (status in setOf("completed", "finished", "success", "seeding")) progress = 100.0
            return DownloadTask(raw.text("id", "taskId"), source, raw.text("seriesTitle", "name", "title", fallback = "下载任务"), episode,
                status, progress?.toFloat()?.coerceIn(0f, 100f), raw.text("downloadSpeed", "speedText", "speed").let { if (it.toDoubleOrNull() != null) formatBytes(it.toDouble()) + "/s" else it },
                raw.number("receivedBytes", "downloadedBytes", "downloadedSize", "downloaded"), raw.number("totalBytes", "totalSize", "size"),
                raw.text("savePath", "path"), raw.text("message", "queryError", "error"), raw.text("createdAt"), raw.flag("paused"))
        }
    }
}
data class Subscription(
    val id: String, val source: SubscriptionSource, val title: String, val cover: String, val year: String,
    val genre: String, val enabled: Boolean, val count: Int, val lastCheck: String, val nextCheck: String,
    val lastError: String, val createdAt: String, val raw: JsonObject,
) {
    val key get() = "${source.name}:$id"
    companion object {
        fun from(raw: JsonObject, source: SubscriptionSource) = Subscription(raw.text("id"), source, raw.text("title"), raw.text("cover"),
            raw.text("year"), raw.text("genre"), raw.flag("enabled"), raw.number(if (source == SubscriptionSource.Hls) "episodeCount" else "magnetCount")?.toInt() ?: 0,
            raw.text("lastCheckAt"), raw.text("nextCheckAt"), raw.text("lastError"), raw.text("createdAt"), raw)
    }
}
data class Tunnel(val draft: TunnelDraft, val status: String, val statusText: String, val failed: Boolean, val localAddress: String, val remoteAddress: String) {
    val name get() = draft.name
    val enabled get() = draft.enabled
    val running get() = enabled && status == "running"
    companion object {
        fun from(raw: JsonObject) = Tunnel(TunnelDraft(raw.text("name"), raw.text("proxyType", fallback = "tcp"), raw.text("localIp", fallback = "127.0.0.1"),
            raw.number("localPort")?.toInt(), raw.number("remotePort")?.toInt(), raw.text("customDomains"), raw.text("subdomain"), raw.text("remark"),
            raw.flag("encryption"), raw.flag("compression"), raw.flag("enabled")), raw.text("status"), raw.text("statusText"), raw.flag("failed"), raw.text("localAddress"), raw.text("remoteAddress"))
    }
}
data class FrpRuntime(val running: Boolean = false, val status: String = "未知", val message: String = "", val container: String = "frpc", val sharedConfig: Boolean? = null) {
    companion object { fun from(raw: JsonObject) = FrpRuntime(raw.flag("containerRunning"), raw.text("containerStatus", fallback = "未知"), raw.text("message"), raw.text("container", fallback = "frpc"), (raw["configSharedWithFrpc"] as? JsonPrimitive)?.booleanOrNull) }
}
data class DockerContainer(val id: String, val name: String, val image: String, val state: String, val status: String, val ports: String, val raw: JsonObject) {
    val running get() = state.equals("running", true)
    companion object {
        fun from(raw: JsonObject): DockerContainer {
            val name = raw.rows("Names", "names").firstOrNull()?.jsonPrimitive?.contentOrNull?.trimStart('/') ?: raw.text("Name", "name", "Id", "id").take(16)
            val ports = raw.rows("Ports", "ports").map { it.asObject() }.mapNotNull { port ->
                val local = port.number("PrivatePort", "privatePort")?.toInt() ?: return@mapNotNull null
                val public = port.number("PublicPort", "publicPort")?.toInt()
                if (public != null) "$public → $local" else "$local/${port.text("Type", "type", fallback = "tcp")}"
            }.distinct().joinToString(" · ")
            val state = raw.text("State", "state").ifBlank { raw.child("State").text("Status", "status") }
            return DockerContainer(raw.text("Id", "id"), name, raw.text("Image", "image"), state, raw.text("Status", "status"), ports, raw)
        }
    }
}
data class DockerResource(val id: String, val name: String, val subtitle: String, val raw: JsonObject)
enum class DockerSection(val title: String) { Containers("容器"), Images("镜像"), Networks("网络"), Volumes("存储卷") }
data class ResourceUsage(val cpuPercent: Double? = null, val memoryBytes: Double? = null)
data class DockerOverview(val online: Boolean = false, val total: Int = 0, val running: Int = 0, val images: Int = 0, val version: String = "", val message: String = "", val usage: ResourceUsage = ResourceUsage())

data class LoadState<T>(val data: T, val loading: Boolean = false, val error: String? = null, val loaded: Boolean = false)
data class MultiSourceResult<T>(val items: List<T>, val warnings: List<String> = emptyList(), val failedSources: Set<String> = emptySet())

fun statusLabel(value: String): String = when (value.lowercase(Locale.ROOT)) {
    "pending", "added", "queued", "submitted" -> "等待开始"
    "downloading", "running" -> "下载中"
    "verifying" -> "校验中"
    "completed", "complete", "finished", "success", "seeding", "uploading" -> "已完成"
    "failed", "error" -> "失败"
    "cancelled", "canceled" -> "已取消"
    "skipped" -> "已跳过"
    "paused", "stopped" -> "已暂停"
    else -> "等待同步"
}
fun formatBytes(value: Double?): String {
    if (value == null || !value.isFinite()) return "—"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = value.coerceAtLeast(0.0); var index = 0
    while (size >= 1024 && index < units.lastIndex) { size /= 1024; index++ }
    return String.format(Locale.ROOT, if (index == 0) "%.0f %s" else "%.1f %s", size, units[index])
}


fun TunnelDraft.validationError(): String? = when {
    name.isBlank() || name.length > 128 || name.any { it == '[' || it == ']' || it.isISOControl() } -> "请填写有效的隧道名称（不超过 128 位）"
    proxyType !in setOf("tcp", "udp", "http", "https") -> "请选择协议类型"
    localIp.isBlank() -> "请填写本地服务地址"
    localPort == null || localPort !in 1..65535 -> "本地端口应在 1–65535 之间"
    proxyType in setOf("tcp", "udp") && (remotePort == null || remotePort !in 1..65535) -> "云端端口应在 1–65535 之间"
    proxyType in setOf("http", "https") && customDomains.isBlank() && subdomain.isBlank() -> "请填写自定义域名或子域名"
    else -> null
}
