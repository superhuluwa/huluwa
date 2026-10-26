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
@Serializable data class TunnelDraft(
    val name: String = "", val proxyType: String = "tcp", val localIp: String = "127.0.0.1",
    val localPort: Int? = null, val remotePort: Int? = null, val customDomains: String = "",
    val subdomain: String = "", val remark: String = "", val encryption: Boolean = false,
    val compression: Boolean = false, val enabled: Boolean = true,
)

const val MOVIE_SESSION_TITLE = "电影港 ★ 磁力"

enum class MainTab(val title: String, val isSessionTab: Boolean = false) {
    Functions("功能", true), System("系统", true), Ai("Ai"), Downloads("下载"), Mine("我的")
}
enum class AndroidBuildProject(val id: String, val title: String, val apiPath: List<String>) {
    Huluwa("huluwa", "葫芦娃", listOf("android")),
    TerminalBuddy("terminalbuddy", "TerminalBuddy", listOf("terminalbuddy", "android"));
    fun path(vararg tail: String): Array<String> = (apiPath + tail).toTypedArray()
}
enum class ManagementService(val sessionId: String, val kind: String, val title: String, val subtitle: String) {
    Frp("local-frp-management", "frp", "Frp 管理", "内网穿透与隧道管理"),
    Docker("local-docker-management", "docker", "Docker 管理", "容器与资源管理"),
    AppBuild("local-android-build", "android-build", "App 构建管理", "更新源码 · 编译安装包 · 执行日志"),
    TerminalBuddyBuild("local-terminalbuddy-build", "terminalbuddy-build", "TerminalBuddy App 打包", "更新源码 · 编译安装包 · 执行日志"),
    CheckLogs("local-check-logs", "check-logs", "巡检记录", "订阅巡检结果与新增明细"),
    Library("local-library", "library", "剧集管理", "扫描下载目录 · 管理磁盘空间"),
    Subscriptions("local-subscriptions", "subscriptions", "我的订阅", "电影港与电影天堂订阅 · 追更状态与资源列表"),
}
enum class DownloadSource(val title: String) { Hls("离线剧集"), Magnet("磁力下载"), XunleiWin("迅雷【Win】") }
enum class SubscriptionSource(val title: String) { Hls("电影天堂"), Movie("电影港") }
enum class Delivery { Sent, Sending, Failed }

data class Session(
    val id: String, val title: String, val kind: String, val unread: Int, val last: ChatMessage?,
    val previewOverride: String? = null, val updatedAt: String = last?.createdAt.orEmpty(),
) {
    val managementService: ManagementService? get() = ManagementService.entries.firstOrNull { it.kind == kind }
    val isLocal: Boolean get() = kind == "upgrade" || managementService != null
    /** 只有本地服务会话归入系统会话，其余内置会话（含系统通知）都属于功能会话。 */
    val isSystemSession: Boolean get() = isLocal
    val preview: String get() = previewOverride ?: last?.let { it.contentText.ifBlank { it.content } }?.replace(Regex("<[^>]+>"), "")?.take(160).orEmpty().ifBlank { "发条消息，开始使用这个服务" }
    companion object {
        fun from(raw: JsonObject): Session {
            val id = raw.text("id")
            val title = raw.text("title").let { if (id == "movie" && it == "电影港") MOVIE_SESSION_TITLE else it }
            return Session(id, title, raw.text("kind"), raw.number("unread")?.toInt() ?: 0,
                (raw["last"] as? JsonObject)?.let(ChatMessage::from), updatedAt = raw.text("updatedAt"))
        }
    }
}
data class ParsedImage(val url: String, val livePhotoUrl: String = "")

data class ChatMessage(
    val id: Long, val sessionId: String, val role: String, val type: String, val content: String,
    val contentText: String, val payload: JsonObject, val createdAt: String,
    val delivery: Delivery = Delivery.Sent, val requestId: String? = null,
) {
    val mine get() = role == "user"
    val notify: NotifyMessage? get() = if (type == "notify") NotifyMessage.from(this) else null
    val videoImages: List<ParsedImage> get() = if (payload.text("kind") == "video") payload.rows("images").mapNotNull { image ->
        val raw = image.asObject()
        val url = (image as? JsonPrimitive)?.contentOrNull ?: raw.text("url")
        url?.takeIf { it.startsWith("http://") || it.startsWith("https://") }?.let {
            ParsedImage(it, raw.text("livePhotoUrl", "live_photo_url"))
        }
    } else emptyList()
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
    /** 同一部作品在两个来源里的唯一键，用于判断弹框是否还停留在同一张卡片上 */
    fun key(): String = "$source:$id"
    companion object {
        fun from(raw: JsonObject) = FilmCard(raw.text("id", "resId"), raw.text("title"), raw.text("cover"), raw.text("url"), raw.text("source"),
            raw.text("year"), raw.text("genre"), raw.text("origin"), raw.text("apiUrl"), raw.rows("playSources").map { PlaySource.from(it.asObject()) }, raw.flag("subscribed"))
    }
}

/** 资源列表里把某条磁力标成「已下载」：命中 hash（无 hash 时退回 magnet 原文），未命中返回原对象 */
fun JsonObject.markDownloaded(key: String): JsonObject {
    if (key.isBlank()) return this
    val magnets = rows("magnets")
    if (magnets.none { it is JsonObject && (it.text("hash").ifBlank { it.text("magnet") }) == key }) return this
    val updated = magnets.map { element ->
        if (element is JsonObject && (element.text("hash").ifBlank { element.text("magnet") }) == key)
            buildJsonObject { element.forEach { (name, value) -> put(name, value) }; put("downloaded", JsonPrimitive(true)) }
        else element
    }
    return buildJsonObject { forEach { (name, value) -> put(name, if (name == "magnets") JsonArray(updated) else value) } }
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
data class Tunnel(
    val draft: TunnelDraft, val status: String, val statusText: String, val failed: Boolean,
    val localAddress: String, val remoteAddress: String,
    val probe: String = "", val probeText: String = "", val error: String = "",
) {
    val name get() = draft.name
    val enabled get() = draft.enabled
    val running get() = enabled && status == "running"
    companion object {
        fun from(raw: JsonObject) = Tunnel(TunnelDraft(raw.text("name"), raw.text("proxyType", fallback = "tcp"), raw.text("localIp", fallback = "127.0.0.1"),
            raw.number("localPort")?.toInt(), raw.number("remotePort")?.toInt(), raw.text("customDomains"), raw.text("subdomain"), raw.text("remark"),
            raw.flag("encryption"), raw.flag("compression"), raw.flag("enabled")), raw.text("status"), raw.text("statusText"), raw.flag("failed"), raw.text("localAddress"), raw.text("remoteAddress"),
            raw.text("probe"), raw.text("probeText"), raw.text("error"))
    }
}
data class FrpRuntime(
    val running: Boolean = false, val status: String = "未知", val message: String = "", val container: String = "frpc",
    val sharedConfig: Boolean? = null, val configAvailable: Boolean = false,
    val total: Int? = null, val runningTunnels: Int? = null, val failedTunnels: Int? = null, val disabledTunnels: Int? = null,
) {
    companion object {
        fun from(raw: JsonObject): FrpRuntime {
            val configured = raw.flag("configAvailable")
            return FrpRuntime(
                raw.flag("containerRunning"), raw.text("containerStatus", fallback = "未知"), raw.text("message"),
                raw.text("container", fallback = "frpc"), (raw["configSharedWithFrpc"] as? JsonPrimitive)?.booleanOrNull,
                configured, raw.number("total")?.toInt()?.takeIf { configured && it >= 0 },
                raw.number("running")?.toInt()?.takeIf { configured && it >= 0 },
                raw.number("error")?.toInt()?.takeIf { configured && it >= 0 },
                raw.number("disabled")?.toInt()?.takeIf { configured && it >= 0 },
            )
        }
    }
}

/** 服务器整机指标与 Docker 容器采样分开建模；缺失的数值保持未知。 */
data class ServerMetrics(
    val available: Boolean = false, val cpuPercent: Double? = null, val cores: Int? = null,
    val load1: Double? = null, val load5: Double? = null, val load15: Double? = null,
    val memoryPercent: Double? = null, val memoryUsed: Double? = null, val memoryTotal: Double? = null,
    val memoryAvailable: Double? = null, val sampledAt: Long? = null, val stale: Boolean = false, val message: String = "",
) {
    companion object {
        fun from(raw: JsonObject): ServerMetrics {
            val cpu = raw.child("cpu")
            val memory = raw.child("memory")
            return ServerMetrics(
                available = raw.flag("available"), cpuPercent = cpu.number("percent")?.takeIf { it in 0.0..100.0 },
                cores = cpu.number("cores")?.toInt()?.takeIf { it > 0 },
                load1 = cpu.number("load1")?.takeIf { it >= 0 }, load5 = cpu.number("load5")?.takeIf { it >= 0 },
                load15 = cpu.number("load15")?.takeIf { it >= 0 },
                memoryPercent = memory.number("percent")?.takeIf { it in 0.0..100.0 },
                memoryUsed = memory.number("used")?.takeIf { it >= 0 }, memoryTotal = memory.number("total")?.takeIf { it > 0 },
                memoryAvailable = memory.number("available")?.takeIf { it >= 0 },
                sampledAt = raw.number("sampledAt")?.toLong()?.takeIf { it > 0 }, stale = raw.flag("stale"), message = raw.text("message"),
            )
        }
    }
}

data class DockerSummary(
    val available: Boolean = false, val total: Int? = null, val running: Int? = null, val message: String = "",
) {
    val notRunning: Int? get() = total?.let { count -> running?.let { (count - it).coerceAtLeast(0) } }
    companion object {
        fun from(raw: JsonObject): DockerSummary {
            val available = raw.flag("available")
            return DockerSummary(available, raw.number("total")?.toInt()?.takeIf { available && it >= 0 },
                raw.number("running")?.toInt()?.takeIf { available && it >= 0 }, raw.text("message"))
        }
    }
}

data class MineOverview(
    val server: LoadState<ServerMetrics> = LoadState(ServerMetrics()),
    val frp: LoadState<FrpRuntime> = LoadState(FrpRuntime()),
    val docker: LoadState<DockerSummary> = LoadState(DockerSummary()),
) {
    val loading: Boolean get() = server.loading || frp.loading || docker.loading
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
data class ResourceUsage(
    val cpuPercent: Double? = null, val memoryBytes: Double? = null,
    val cpuSamples: Int = 0, val memorySamples: Int = 0, val expectedSamples: Int = 0,
)
data class DockerOverview(val online: Boolean = false, val total: Int = 0, val running: Int = 0, val images: Int = 0, val version: String = "", val message: String = "", val usage: ResourceUsage = ResourceUsage())

/** 安卓构建任务：字段与后端 android_build_task 表一一对应。 */
data class AndroidBuildTask(
    val id: Long, val type: String, val status: String, val stage: String,
    val commit: String, val commitSubject: String, val versionCode: Long?, val versionName: String,
    val artifactName: String, val apkSize: Long, val error: String,
    val requestedBy: String, val createdAt: String, val startedAt: String, val completedAt: String,
    val serverId: String = "local",
) {
    val building get() = type == "BUILD"
    val active get() = status in setOf("QUEUED", "RUNNING", "CANCELLING")
    val downloadable get() = building && status == "SUCCESS" && artifactName.isNotBlank() && apkSize > 0
    val typeLabel get() = if (building) "编译 APK" else "更新代码"
    val statusLabel get() = when (status) {
        "QUEUED" -> "等待执行"
        "RUNNING" -> "执行中"
        "CANCELLING" -> "正在取消"
        "SUCCESS" -> "成功"
        "FAILED" -> "失败"
        "CANCELLED" -> "已取消"
        "TIMED_OUT" -> "超时"
        "INTERRUPTED" -> "意外中断"
        else -> status
    }
    val stageLabel get() = when (stage) {
        "QUEUED" -> "等待执行"
        "FETCHING" -> "更新源码"
        "PREPARING" -> "准备编译"
        "COMPILING" -> "正在编译"
        "VERIFYING" -> "校验安装包"
        "ARCHIVING" -> "归档安装包"
        "DONE" -> "完成"
        else -> stage.ifBlank { statusLabel }
    }
    /** 任务耗时：未结束的任务以当前时钟兜底。 */
    fun elapsedLabel(clock: Long): String {
        val start = parseLocalTime(startedAt.ifBlank { createdAt }) ?: return "—"
        val end = parseLocalTime(completedAt) ?: clock
        val seconds = (end - start).coerceAtLeast(0) / 1000
        return if (seconds < 60) "${seconds} 秒" else "${seconds / 60} 分 ${seconds % 60} 秒"
    }
    /** 开始时间：兼容 ISO-8601（带时区）和本地时间两种写法，统一显示为本地时间。 */
    val startedLabel: String get() = formatLocalTime(startedAt.ifBlank { createdAt })
    companion object {
        fun from(raw: JsonObject) = AndroidBuildTask(
            raw.number("id")?.toLong() ?: 0, raw.text("type", fallback = "UPDATE"),
            raw.text("status", fallback = "QUEUED"), raw.text("stage"),
            raw.text("commit"), raw.text("commitSubject"), raw.number("versionCode")?.toLong(), raw.text("versionName"),
            raw.text("artifactName"), raw.number("apkSize")?.toLong() ?: 0, raw.text("error"),
            raw.text("requestedBy"), raw.text("createdAt"), raw.text("startedAt"), raw.text("completedAt"),
            raw.text("serverId", fallback = "local"),
        )
    }
}

/** 源码快照：构建容器成功执行「更新代码」后写入 source.json。 */
data class AndroidSource(val commit: String = "", val commitSubject: String = "", val branch: String = "", val updatedAt: String = "") {
    companion object {
        fun from(raw: JsonObject) = AndroidSource(raw.text("commit"), raw.text("commitSubject"), raw.text("branch"), raw.text("updatedAt"))
    }
}

data class AndroidBuildServer(val id: String, val name: String, val online: Boolean) {
    companion object {
        fun from(raw: JsonObject) = AndroidBuildServer(raw.text("id"), raw.text("name"), raw.flag("online"))
    }
}

data class AndroidBuildStatus(
    val enabled: Boolean = false, val workerOnline: Boolean = false, val signingReady: Boolean = false,
    val branch: String = "master", val source: AndroidSource = AndroidSource(),
    val activeTask: AndroidBuildTask? = null, val latestApk: AndroidBuildTask? = null,
    val servers: List<AndroidBuildServer> = emptyList(),
    val serverId: String = "local",
) {
    /** 编译任务会先更新源码，首次构建也可直接提交。 */
    val canBuild get() = enabled && workerOnline && activeTask == null && signingReady
    val canDownload get() = latestApk?.downloadable == true
    companion object {
        fun from(raw: JsonObject) = AndroidBuildStatus(
            raw.flag("enabled"), raw.flag("workerOnline"), raw.flag("signingReady"),
            raw.text("branch", fallback = "master"), AndroidSource.from(raw.child("source")),
            (raw["activeTask"] as? JsonObject)?.let(AndroidBuildTask::from),
            (raw["latestApk"] as? JsonObject)?.let(AndroidBuildTask::from),
            (raw["servers"] as? JsonArray)?.mapNotNull { (it as? JsonObject)?.let(AndroidBuildServer::from) } ?: emptyList(),
            raw.text("serverId", fallback = "local"),
        )
    }
}

/** 增量日志分片：cursor 是下一次请求的字节位置，reset 表示服务端已从头返回。 */
data class AndroidLogChunk(val task: AndroidBuildTask?, val text: String, val cursor: Long, val hasMore: Boolean, val reset: Boolean) {
    companion object {
        fun from(raw: JsonObject) = AndroidLogChunk(
            (raw["task"] as? JsonObject)?.let(AndroidBuildTask::from), raw.text("text"),
            raw.number("cursor")?.toLong() ?: 0, raw.flag("hasMore"), raw.flag("reset"),
        )
    }
}

data class AndroidBuildState(
    val serverId: String = "local",
    val status: AndroidBuildStatus = AndroidBuildStatus(),
    val tasks: LoadState<List<AndroidBuildTask>> = LoadState(emptyList()),
    val selectedId: Long? = null, val logs: String = "", val cursor: Long = 0,
    val logComplete: Boolean = false, val follow: Boolean = true, val submitting: String? = null,
    val connectionError: Boolean = false, val download: AndroidDownload = AndroidDownload(),
) {
    val selectedTask: AndroidBuildTask? get() = selectedId?.let { id -> tasks.data.firstOrNull { it.id == id } }
}

/** 安装包下载状态：服务端只下发短时票据，App 用系统 DownloadManager 拉取。 */
data class AndroidDownload(
    val taskId: Long = 0, val phase: UpgradePhase = UpgradePhase.Idle, val logFile: Boolean = false,
    val filename: String = "", val received: Long = 0, val total: Long = 0,
    val message: String = "", val error: String? = null,
) {
    val active get() = phase == UpgradePhase.Downloading
    val progress get() = if (total > 0) (received.toFloat() / total).coerceIn(0f, 1f) else null
}

/** 订阅巡检记录：电影港（磁力）与电影天堂（离线剧集）共用一条时间线。 */
data class CheckLog(
    val id: Long, val source: String, val seriesTitle: String, val trigger: String, val status: String,
    val foundCount: Int, val submittedCount: Int, val message: String, val details: List<String>,
    val durationMs: Long, val createdAt: String,
) {
    val sourceLabel get() = if (source == "dytt") "电影天堂" else "电影港"
    val triggerLabel get() = if (trigger == "manual") "立即检查" else "定时巡检"
    val statusLabel get() = when (status) {
        "updated" -> "有更新"
        "ok" -> "无更新"
        "baseline" -> "补建基线"
        "failed" -> "巡检失败"
        else -> status
    }
    /** 只有真正发现新条目时才展示「新增 / 提交」，无更新与失败都不展示 0 */
    val hasUpdate get() = foundCount > 0 || submittedCount > 0
    /** 耗时展示：小于 1 秒显示毫秒，超过 1 分钟显示分秒 */
    val durationText get() = when {
        durationMs <= 0 -> "—"
        durationMs < 1000 -> "$durationMs ms"
        durationMs < 60_000 -> String.format(Locale.ROOT, "%.1f s", durationMs / 1000.0)
        else -> "${durationMs / 60_000} 分 ${Math.round((durationMs % 60_000) / 1000.0)} 秒"
    }
    companion object {
        fun from(raw: JsonObject) = CheckLog(
            raw.number("id")?.toLong() ?: 0, raw.text("source"), raw.text("seriesTitle"), raw.text("trigger"),
            raw.text("status"), raw.number("foundCount")?.toInt() ?: 0, raw.number("submittedCount")?.toInt() ?: 0,
            raw.text("message"), raw.rows("details").mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
            raw.number("durationMs")?.toLong() ?: 0, raw.text("createdAt"),
        )
    }
}

/** 本地下载目录中的一个视频文件（后端只接受本次扫描返回的短期 ID 做删除）。 */
data class LibraryEntry(
    val id: String, val seriesTitle: String, val seriesKey: String, val season: Int, val episode: Int,
    val fileName: String, val relativePath: String, val size: Long, val modifiedAt: String,
    val episodeRecognized: Boolean,
) {
    val episodeLabel get() = if (episodeRecognized && episode > 0) "第 $episode 集" else fileName.substringBeforeLast('.').take(64)
    companion object {
        fun from(raw: JsonObject) = LibraryEntry(
            raw.text("id"), raw.text("seriesTitle"), raw.text("seriesKey"),
            raw.number("season")?.toInt() ?: 1, raw.number("episode")?.toInt() ?: 0,
            raw.text("fileName"), raw.text("relativePath"), raw.number("size")?.toLong() ?: 0,
            raw.text("modifiedAt"), raw.flag("episodeRecognized"),
        )
    }
}

/** 扫描结果：下载根目录、可写状态、文件清单与统计。 */
data class LibraryScan(
    val mediaRoot: String = "", val writable: Boolean = false, val entries: List<LibraryEntry> = emptyList(),
    val totalBytes: Long = 0, val scannedFiles: Int = 0, val seriesCount: Int = 0,
    val scannedAt: String = "", val warning: String = "",
) {
    companion object {
        fun from(raw: JsonObject) = LibraryScan(
            raw.text("mediaRoot"), raw.flag("writable"), raw.rows("entries").map { LibraryEntry.from(it.asObject()) },
            raw.number("totalBytes")?.toLong() ?: 0, raw.number("scannedFiles")?.toInt() ?: 0,
            raw.number("seriesCount")?.toInt() ?: 0, raw.text("scannedAt"), raw.text("warning"),
        )
    }
}

/** 删除结果：后端只报告实际删掉的 ID，失败项逐条给出原因。 */
data class LibraryDeleteResult(
    val deleted: Int = 0, val failed: Int = 0, val freedBytes: Long = 0,
    val deletedIds: Set<String> = emptySet(), val message: String = "",
) {
    companion object {
        fun from(raw: JsonObject) = LibraryDeleteResult(
            raw.number("deleted")?.toInt() ?: 0, raw.number("failed")?.toInt() ?: 0,
            raw.number("freedBytes")?.toLong() ?: 0,
            raw.rows("deletedIds").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.toSet(),
            raw.text("message"),
        )
    }
}

data class LoadState<T>(
    val data: T, val loading: Boolean = false, val error: String? = null, val loaded: Boolean = false,
    val updatedAt: Long = 0L,
)
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


/** 解析后端时间：兼容 ISO-8601（带时区）和本地时间字符串，返回本地毫秒时间戳。 */
internal fun parseLocalTime(raw: String): Long? {
    if (raw.isBlank()) return null
    runCatching { return java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli() }
    runCatching { return java.time.LocalDateTime.parse(raw.replace(' ', 'T').take(19))
        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() }
    return null
}
/** 把后端时间格式化为「yyyy-MM-dd HH:mm」。 */
internal fun formatLocalTime(raw: String): String {
    val millis = parseLocalTime(raw) ?: return raw.take(16).replace('T', ' ')
    return java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
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
