package com.huluwa.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.concurrent.ConcurrentHashMap

class HuluwaRepository(val api: ApiClient, private val chatCache: ChatCache) {
    private val previousCpu = ConcurrentHashMap<String, JsonObject>()
    // FRP 保存提交完整表单，必须保留 tcp、false 等默认值。
    private val tunnelRequestJson = Json(ApiJson) { encodeDefaults = true }
    suspend fun authStatus(server: String) = api.request("GET", listOf("auth", "status"), server = server, token = "").asObject()
    suspend fun login(server: String, username: String, password: String, setup: Boolean): JsonObject = api.request(
        "POST", listOf("auth", if (setup) "setup" else "login"), ApiJson.encodeToJsonElement(Credentials(username, password)), server = server, token = ""
    ).asObject()
    suspend fun me() = api.get("auth", "me").asObject()
    suspend fun changePassword(old: String, new: String) = api.post("auth", "password", body = jsonBody { put("oldPassword", old); put("newPassword", new) })
    fun clearCaches() { previousCpu.clear(); chatCache.clear() }
    suspend fun sessions() = api.get("chat", "sessions", query = mapOf("client" to "android"))
        .asArray().map { Session.from(it.asObject()) }

    /** 进入会话时先取本地缓存，命中即可立即渲染，不必等网络返回。 */
    suspend fun cachedMessages(id: String): List<ChatMessage> = withContext(Dispatchers.IO) { chatCache.read(id) }

    /** 同一个进程内反复进出会话时，直接同步拿到上次渲染过的一页，转场第一帧就有内容。 */
    fun warmMessages(id: String): List<ChatMessage>? = chatCache.peek(id)

    /** 消息落盘放 IO 线程，失败不影响正常收发（缓存只是锦上添花）。 */
    suspend fun cacheMessages(id: String, rows: List<ChatMessage>) {
        withContext(Dispatchers.IO) { runCatching { chatCache.write(id, rows) } }
    }

    /** 清空会话后同步删掉本地缓存，避免下次进入又短暂显示已清空的消息。 */
    suspend fun forgetMessages(id: String) = withContext(Dispatchers.IO) { chatCache.remove(id) }

    suspend fun messages(id: String, before: Long? = null) = api.get("chat", "sessions", id, "messages", query = buildMap {
        put("limit", "50"); if (before != null) put("beforeId", before.toString())
    }).asArray().map { ChatMessage.from(it.asObject()) }
    suspend fun send(id: String, content: String, requestId: String) = api.post("chat", "sessions", id, "messages", body = ApiJson.encodeToJsonElement(SendMessageRequest(content, requestId)))
        .asArray().map { ChatMessage.from(it.asObject()) }
    suspend fun markRead(id: String) { api.post("chat", "sessions", id, "read") }
    suspend fun clearChat(id: String) { api.delete("chat", "sessions", id, "messages") }

    suspend fun downloads(refresh: Boolean): MultiSourceResult<DownloadTask> = coroutineScope {
        val results = DownloadSource.entries.map { source -> async {
            source to sourceResult {
                val data = when (source) {
                    DownloadSource.Hls -> api.get("hls", "downloads")
                    DownloadSource.Magnet -> api.get("chat", "subscriptions", "downloads", query = mapOf("refresh" to refresh.toString()))
                    DownloadSource.XunleiWin -> api.get("xunlei-win", "downloads")
                }
                data.asArray().map { DownloadTask.from(it.asObject(), source) }
            }
        } }.awaitAll()
        val errors = results.mapNotNull { it.second.exceptionOrNull() }
        errors.filterIsInstance<ApiException>().firstOrNull { it.code == 401 }?.let { throw it }
        if (errors.size == results.size) throw errors.first()
        MultiSourceResult(results.flatMap { it.second.getOrDefault(emptyList()) }.sortedByDescending { it.createdAt },
            results.mapNotNull { (source, result) -> result.exceptionOrNull()?.let { "${source.title}：${it.userMessage()}" } },
            results.filter { it.second.isFailure }.map { it.first.name }.toSet())
    }
    suspend fun retry(task: DownloadTask) { require(task.retryable) { "当前任务不能重试" }; api.post("hls", "downloads", task.id, "retry").requireSuccess() }
    suspend fun cancel(task: DownloadTask) { require(task.cancellable) { "当前任务不能取消" }; api.post("hls", "downloads", task.id, "cancel").requireSuccess() }
    suspend fun deleteDownload(task: DownloadTask) { when (task.source) {
        DownloadSource.Hls -> api.delete("hls", "downloads", task.id)
        DownloadSource.Magnet -> api.delete("chat", "subscriptions", "downloads", task.id)
        DownloadSource.XunleiWin -> api.delete("xunlei-win", "downloads", task.id)
    }.requireSuccess() }
    suspend fun downloadLog(id: String) = api.get("hls", "downloads", id, "log").asObject().text("content")
    suspend fun clearDownloads(source: DownloadSource) { when (source) {
        DownloadSource.Hls -> api.delete("hls", "downloads")
        DownloadSource.Magnet -> api.delete("chat", "subscriptions", "downloads")
        DownloadSource.XunleiWin -> api.delete("xunlei-win", "downloads")
    }.requireSuccess() }
    suspend fun clearAllDownloads(): Unit = coroutineScope {
        // 各来源独立处理，某个来源失败时仍完成其他来源，并明确返回未清空的来源。
        val results = DownloadSource.entries.map { source -> async {
            source to sourceResult { clearDownloads(source) }
        } }.awaitAll()
        val errors = results.mapNotNull { it.second.exceptionOrNull() }
        errors.filterIsInstance<ApiException>().firstOrNull { it.code == 401 }?.let { throw it }
        if (errors.isNotEmpty()) {
            val details = results.mapNotNull { (source, result) ->
                result.exceptionOrNull()?.let { "${source.title}：${it.userMessage()}" }
            }.joinToString("\n")
            throw IllegalStateException("清空记录未全部完成：\n$details")
        }
    }
    suspend fun downloadMagnet(title: String, magnet: String) = api.post("chat", "subscriptions", "magnets", "download", body = jsonBody {
        put("title", title); put("magnet", magnet)
    }).requireSuccess()

    suspend fun subscriptions(): MultiSourceResult<Subscription> = coroutineScope {
        val results = SubscriptionSource.entries.map { source -> async {
            source to sourceResult {
                subscriptionRows(source)
            }
        } }.awaitAll()
        val errors = results.mapNotNull { it.second.exceptionOrNull() }
        errors.filterIsInstance<ApiException>().firstOrNull { it.code == 401 }?.let { throw it }
        if (errors.size == results.size) throw errors.first()
        MultiSourceResult(results.flatMap { it.second.getOrDefault(emptyList()) }.sortedByDescending { it.createdAt },
            results.mapNotNull { (source, result) -> result.exceptionOrNull()?.let { "${source.title}：${it.userMessage()}" } },
            results.filter { it.second.isFailure }.map { it.first.name }.toSet())
    }
    private suspend fun subscriptionRows(source: SubscriptionSource): List<Subscription> {
        val data = if (source == SubscriptionSource.Hls) api.get("hls", "subscriptions")
            else api.get("chat", "subscriptions", query = mapOf("includeDisabled" to "true"))
        return data.asArray().map { Subscription.from(it.asObject(), source) }
    }

    /** 返回操作后该来源的完整快照，卡片和「我的订阅」同步使用真实状态。 */
    suspend fun setFilmFollowing(card: FilmCard, following: Boolean): List<Subscription> {
        require(!card.isMovieHls) { "该来源暂不支持追更" }
        val rows = subscriptionRows(card.subscriptionSource)
        if (following) {
            val saved = subscribe(card)
            return rows.filterNot { it.key == saved.key } + saved
        }
        return rows.map { subscription ->
            if (!subscription.enabled || !card.matchesSubscription(subscription)) return@map subscription
            if (subscription.source == SubscriptionSource.Hls) {
                api.post("hls", "subscriptions", subscription.id, "enabled", body = jsonBody { put("enabled", false) }).requireSuccess()
            } else api.post("chat", "subscriptions", subscription.id, "unsubscribe").requireSuccess()
            subscription.copy(enabled = false, raw = jsonBody {
                subscription.raw.forEach { (key, value) -> put(key, value) }
                put("enabled", false)
            })
        }
    }

    suspend fun check(sub: Subscription): String {
        val response = if (sub.source == SubscriptionSource.Hls) api.post("hls", "subscriptions", sub.id, "check")
            else api.post("chat", "subscriptions", sub.id, "check")
        return response.requireSuccess().asObject().text("message", fallback = "订阅检查完成")
    }
    suspend fun deleteSubscription(sub: Subscription) {
        if (sub.source == SubscriptionSource.Hls) api.delete("hls", "subscriptions", sub.id).requireSuccess()
        else api.delete("chat", "subscriptions", sub.id).requireSuccess()
    }
    private suspend fun subscribe(card: FilmCard): Subscription {
        val response = if (card.isHls) api.post("hls", "subscriptions", "quick", body = jsonBody { put("apiUrl", card.apiUrl); put("resId", card.id) }).requireSuccess()
        else api.post("chat", "subscriptions", body = jsonBody {
            put("id", card.id); put("title", card.title); put("year", card.year); put("origin", card.origin)
            put("genre", card.genre); put("cover", card.cover); put("url", card.url)
        }).requireSuccess()
        return Subscription.from(response.asObject().child("subscription"), card.subscriptionSource)
    }
    /** 各来源先读缓存并后台刷新；轮询只读快照，老卡片缺少地址时由后端按片名匹配。 */
    suspend fun filmResources(card: FilmCard, online: Boolean = false, cachedOnly: Boolean = false): JsonObject {
        if (!card.isMovie) return api.get("hls", "detail", query = buildMap {
            put("resId", card.id); put("cachedOnly", cachedOnly.toString())
            if (card.apiUrl.isNotBlank()) put("apiUrl", card.apiUrl)
        }).asObject()
        val url = if (online) {
            if (card.isMovieHls) card.url else card.onlineUrl
        } else if (card.isMovieHls) "" else card.url
        return api.get("chat", "movie", "resources", query = mapOf(
            "type" to if (online) "online" else "magnet", "url" to url, "title" to card.title, "year" to card.year,
            "cachedOnly" to cachedOnly.toString(),
        )).asObject()
    }
    /** 首次请求返回目录缓存并启动刷新，轮询只读取缓存，避免反复触发抓站。 */
    suspend fun btbtlaDetail(id: String, cachedOnly: Boolean = false): JsonObject =
        api.get("btbtla", "detail", query = mapOf("id" to id, "cachedOnly" to cachedOnly.toString())).asObject()

    suspend fun btbtlaMagnet(id: String): String =
        api.get("btbtla", "magnet", query = mapOf("id" to id)).asObject().text("magnet").also {
            require(it.startsWith("magnet:?xt=urn:btih:")) { "该资源没有返回有效磁力链接" }
        }

    /**
     * 打开书籍：只准备目录并缓存正文入口，不把书加入书架。
     *
     * 返回服务端当前的书架状态；书籍本来就已收藏时保持收藏，
     * 首次阅读时由阅读页右上角的「加入书架」按钮决定是否收藏。
     */
    suspend fun openFanqieBook(bookId: String, title: String, author: String, cover: String): Boolean =
        api.post("fanqie", "books", bookId, "open", body = jsonBody {
            put("title", title); put("author", author); put("cover", cover); put("addToShelf", false)
        }).asObject().flag("inShelf")

    /** 阅读页加入书架，返回操作后的书架状态。 */
    suspend fun addFanqieToShelf(bookId: String, title: String, author: String, cover: String): Boolean =
        api.post("fanqie", "books", bookId, "shelf", body = jsonBody {
            put("title", title); put("author", author); put("cover", cover)
        }).asObject().flag("inShelf")

    /** 阅读页移出书架：目录和正文缓存保留，只从书架列表隐藏。 */
    suspend fun removeFanqieFromShelf(bookId: String): Boolean =
        api.delete("fanqie", "books", bookId, "shelf").asObject().flag("inShelf")

    suspend fun saveFanqieProgress(bookId: String, order: Int, paragraphIndex: Int, scrollOffset: Int) {
        api.put("fanqie", "books", bookId, "progress", body = jsonBody {
            put("order", order); put("paragraphIndex", paragraphIndex); put("scrollOffset", scrollOffset)
        })
    }

    /** 番茄小说：取一章正文（order<=0 时服务端按上次进度续读）。 */
    suspend fun readFanqieChapter(bookId: String, order: Int): FanqieChapter =
        FanqieChapter.from(api.get("fanqie", "books", bookId, "read",
            query = mapOf("order" to order.toString())).asObject())

    /** 番茄小说：目录（打开目录抽屉时拉取）。 */
    suspend fun fanqieCatalog(bookId: String): FanqieCatalog =
        FanqieCatalog.from(api.get("fanqie", "books", bookId, "catalog").asObject())

    /** 番茄小说：读过的书，按最近打开或阅读时间排序。 */
    suspend fun fanqieBooks(): List<FanqieBookShelf> =
        api.get("fanqie", "books").asArray().mapNotNull { FanqieBookShelf.from(it.asObject()) }

    suspend fun downloadFanqieBook(bookId: String): FanqieShelfUpdateStatus =
        FanqieShelfUpdateStatus.from(api.post("fanqie", "books", bookId, "download").asObject())

    suspend fun fanqieShelfUpdateStatus(): FanqieShelfUpdateStatus =
        FanqieShelfUpdateStatus.from(api.get("fanqie", "books", "update-status").asObject())

    suspend fun deleteFanqieBooks(bookIds: Set<String>): FanqieDeleteResult =
        FanqieDeleteResult.from(api.post("fanqie", "books", "delete", body = jsonBody {
            putJsonArray("bookIds") { bookIds.forEach { add(JsonPrimitive(it)) } }
        }).asObject())

    /** 复用电影港的手动磁力下载接口、默认下载器、保存目录、任务记录及通知。 */
    suspend fun enqueueBtbtlaMagnet(card: BtbtlaCard, item: BtbtlaResource, magnet: String, resolution: Int): JsonObject =
        api.post("chat", "subscriptions", "magnets", "download", body = jsonBody {
            put("url", card.pageUrl); put("title", card.title); put("magnet", magnet)
            put("quality", if (resolution > 0) "${resolution}P" else "OTHER")
            put("episode", item.episodeKey)
        }).requireSuccess().asObject()

    // ------------------------------------------------------------------ 懂片帝
    // 独立接口：不复用 hls/detail 或 hls/downloads，与电影港、电影天堂完全隔开。

    /** 懂片帝先返回目录缓存并后台刷新；轮询只读取服务端快照。 */
    suspend fun dpdDetail(id: String, cachedOnly: Boolean = false): JsonObject =
        api.get("dpd", "detail", query = mapOf("resId" to id, "cachedOnly" to cachedOnly.toString())).asObject()

    /** 线路先显示缓存，后台补齐的地址通过 cachedOnly 轮询同步。 */
    suspend fun dpdLine(id: String, line: String, cachedOnly: Boolean = false): JsonObject =
        api.get("dpd", "line", query = mapOf("resId" to id, "line" to line,
            "cachedOnly" to cachedOnly.toString())).asObject()

    /** 懂片帝单集按需解析（官方线路受站点限速，只有点开的那一集才兑换） */
    suspend fun dpdEpisode(id: String, line: String, title: String): JsonObject =
        api.get("dpd", "episode", query = mapOf("resId" to id, "line" to line, "title" to title)).asObject()

    /** 懂片帝下载只提交作品、线路和集数，真实链接在下载任务执行时获取。 */
    suspend fun enqueueDpdEpisode(card: DpdCard, line: String, episode: DpdEpisode, season: Int, episodeNo: Int) {
        require(episodeNo > 0 && season > 0) { "季号和集号必须大于零" }
        api.post("dpd", "downloads", body = jsonBody {
            put("resId", card.id); put("line", line); put("title", episode.title)
            put("season", season); put("episode", episodeNo)
        }).requireSuccess()
    }

    /** 已知详情页或已匹配的在线地址直接打开，其余磁力卡片按需匹配手机站。 */
    fun knownFilmWebsite(card: FilmCard): String? = when {
        card.isHls -> BrowserAddress.normalize(card.url)
        card.onlineUrl.isNotBlank() -> BrowserAddress.normalize(card.onlineUrl)
        else -> null
    }

    suspend fun filmWebsite(card: FilmCard): String {
        knownFilmWebsite(card)?.let { return it }
        val url = api.get("chat", "movie-hls", "website", query = mapOf(
            "url" to card.url, "title" to card.title,
        )).asObject().text("url")
        return BrowserAddress.normalize(url)
    }

    suspend fun movieHlsUrl(item: PlayItem): String = api.get("chat", "movie-hls", "resolve", query = mapOf("url" to item.url))
        .asObject().text("url").also { require(it.startsWith("https://") || it.startsWith("http://")) { "未解析到视频地址" } }

    suspend fun enqueueEpisode(card: FilmCard, source: String, item: PlayItem, season: Int, episode: Int) {
        require(episode > 0 && season > 0) { "季号和集号必须大于零" }
        val path = if (card.isMovieHls) arrayOf("chat", "movie-hls", "downloads") else arrayOf("hls", "downloads")
        api.post(*path, body = jsonBody {
            put("apiUrl", card.apiUrl); put("resId", card.id); put("title", card.title); put("year", card.year)
            put("season", season); put("episode", episode); put("episodeTitle", item.title); put("url", item.url); put("sourceName", source)
        }).requireSuccess()
    }
    suspend fun enqueueMagnet(card: FilmCard, item: JsonObject) = api.post("chat", "subscriptions", "magnets", "download", body = jsonBody {
        put("url", card.url); put("title", card.title); put("magnet", item.text("magnet")); put("quality", item.text("quality")); put("episode", item.text("episode"))
    }).requireSuccess()

    suspend fun serverMetrics() = ServerMetrics.from(api.get("dashboard", "server").asObject())
    suspend fun frpSummary() = FrpRuntime.from(api.get("frp", "runtime").asObject())
    suspend fun dockerSummary() = DockerSummary.from(api.get("docker", "summary").asObject())

    suspend fun frp(): Pair<List<Tunnel>, FrpRuntime> = coroutineScope {
        val tunnels = async { api.get("frp", "tunnels").asArray().map { Tunnel.from(it.asObject()) } }
        val runtime = async { FrpRuntime.from(api.get("frp", "runtime").asObject()) }
        tunnels.await() to runtime.await()
    }
    suspend fun saveTunnel(original: String?, draft: TunnelDraft) {
        val body = tunnelRequestJson.encodeToJsonElement(draft)
        if (original == null) {
            api.post("frp", "tunnels", body = body).requireSuccess()
            if (!draft.enabled) api.post("frp", "tunnels", draft.name, "toggle", query = mapOf("enabled" to "false")).requireSuccess()
        } else api.put("frp", "tunnels", original, body = body).requireSuccess()
    }
    suspend fun toggleTunnel(tunnel: Tunnel) { api.post("frp", "tunnels", tunnel.name, "toggle", query = mapOf("enabled" to (!tunnel.enabled).toString())).requireSuccess() }
    suspend fun deleteTunnel(tunnel: Tunnel) { api.delete("frp", "tunnels", tunnel.name).requireSuccess() }
    suspend fun frpConfig() = api.get("frp", "config").asObject().text("content")
    suspend fun saveFrpConfig(content: String) { api.put("frp", "config", body = jsonBody { put("content", content) }).requireSuccess() }
    suspend fun reloadFrp() { api.post("frp", "reload").requireSuccess() }
    suspend fun restartFrp() { api.post("frp", "restart").requireSuccess() }

    suspend fun docker(): Pair<List<DockerContainer>, DockerOverview> = coroutineScope {
        val containers = async { api.get("docker", "containers", query = mapOf("all" to "true")).asArray().map { DockerContainer.from(it.asObject()) } }
        val summary = async { api.get("docker", "summary").asObject() }
        val environment = async { api.get("docker", "environment").asObject() }
        val list = containers.await(); val counts = summary.await(); val host = environment.await()
        val ids = list.filter { it.running }.map { it.id }
        val statsResult = if (ids.isNotEmpty()) sourceResult {
            coroutineScope {
                val chunks = ids.chunked(200).map { chunk -> async { api.post("docker", "containers", "stats", body = JsonArray(chunk.map(::JsonPrimitive))).asObject() } }.awaitAll()
                JsonObject(chunks.flatMap { it.entries }.associate { it.toPair() })
            }
        } else Result.success(JsonObject(emptyMap()))
        (statsResult.exceptionOrNull() as? ApiException)?.takeIf { it.code == 401 }?.let { throw it }
        var totalCpu = 0.0; var totalMemory = 0.0; var cpuSamples = 0; var memorySamples = 0
        statsResult.getOrNull()?.forEach { (id, value) ->
            if (id !in ids) return@forEach
            val stats = value.asObject(); val cpu = stats.child("cpu_stats")
            val previous = previousCpu[id] ?: stats.child("precpu_stats")
            val systemNow = cpu.number("system_cpu_usage")
            val systemBefore = previous.number("system_cpu_usage")
            val usageNow = cpu.child("cpu_usage").number("total_usage")
            val usageBefore = previous.child("cpu_usage").number("total_usage")
            val systemDelta = if (systemNow != null && systemBefore != null) systemNow - systemBefore else null
            val cpuDelta = if (usageNow != null && usageBefore != null) usageNow - usageBefore else null
            val cores = cpu.number("online_cpus") ?: cpu.child("cpu_usage").rows("percpu_usage").size.coerceAtLeast(1).toDouble()
            if (systemDelta != null && cpuDelta != null && systemDelta > 0 && cpuDelta >= 0) { totalCpu += cpuDelta / systemDelta * cores * 100.0; cpuSamples++ }
            else stats.number("CPUPercRaw")?.takeIf { it >= 0 }?.let { totalCpu += it; cpuSamples++ }
            (stats.child("memory_stats").number("usage") ?: stats.number("MemoryUsageRaw"))?.takeIf { it >= 0 }?.let { totalMemory += it; memorySamples++ }
            if (cpu.isNotEmpty()) previousCpu[id] = cpu
        }
        previousCpu.keys.retainAll(ids.toSet())
        list to DockerOverview(host.flag("daemonOnline") && counts.flag("available", true), counts.number("total")?.toInt() ?: list.size,
            counts.number("running")?.toInt() ?: list.count { it.running }, counts.number("images")?.toInt() ?: 0, host.text("serverVersion"),
            host.text("message").ifBlank { counts.text("message") }, ResourceUsage(totalCpu.takeIf { cpuSamples > 0 }, totalMemory.takeIf { memorySamples > 0 }, cpuSamples, memorySamples, ids.size))
    }
    suspend fun dockerResources(section: DockerSection): List<DockerResource> {
        val data = when (section) {
            DockerSection.Images -> api.get("docker", "images", query = mapOf("all" to "true"))
            DockerSection.Networks -> api.get("docker", "networks")
            DockerSection.Volumes -> api.get("docker", "volumes")
            DockerSection.Containers -> return emptyList()
        }
        val rows = if (section == DockerSection.Volumes) data.asObject().rows("Volumes", "volumes") else data.asArray()
        return rows.map { it.asObject() }.map { raw -> when (section) {
            DockerSection.Images -> DockerResource(raw.text("Id", "id"), raw.rows("RepoTags", "repoTags").joinToString { it.jsonPrimitive.content }.ifBlank { "未标记镜像" }, formatBytes(raw.number("Size", "size")), raw)
            DockerSection.Networks -> DockerResource(raw.text("Id", "id"), raw.text("Name", "name"), raw.text("Driver", "driver"), raw)
            else -> DockerResource(raw.text("Name", "name"), raw.text("Name", "name"), raw.text("Driver", "driver"), raw)
        } }
    }
    suspend fun containerAction(container: DockerContainer, action: String) {
        require(action in setOf("start", "stop", "restart"))
        api.post("docker", "containers", container.id, action).requireSuccess()
    }
    suspend fun containerLogs(id: String) = (api.get("docker", "containers", id, "logs", query = mapOf("tail" to "200", "follow" to "false", "stdout" to "true", "stderr" to "true")) as? JsonPrimitive)?.contentOrNull.orEmpty()
    suspend fun inspectContainer(id: String) = api.get("docker", "containers", id).asObject()
    suspend fun deleteContainer(id: String) { api.delete("docker", "containers", id).requireSuccess() }
    suspend fun createResource(section: DockerSection, name: String) {
        when (section) {
            DockerSection.Images -> ImageReference.parse(name).let { api.post("docker", "images", "pull", query = mapOf("image" to it.name, "tag" to it.tag)).requireSuccess() }
            DockerSection.Networks -> api.post("docker", "networks", query = mapOf("name" to name, "driver" to "bridge")).requireSuccess()
            DockerSection.Volumes -> api.post("docker", "volumes", query = mapOf("name" to name, "driver" to "local")).requireSuccess()
            else -> throw IllegalArgumentException("请选择资源类型")
        }
    }
    suspend fun removeResource(section: DockerSection, id: String) {
        val type = when (section) { DockerSection.Images -> "images"; DockerSection.Networks -> "networks"; DockerSection.Volumes -> "volumes"; else -> throw IllegalArgumentException("请选择资源类型") }
        api.delete("docker", type, id).requireSuccess()
    }
    suspend fun settings(kind: String): JsonObject = when (kind) {
        "magnet" -> api.get("qbittorrent", "settings").asObject()
        "video" -> api.get("video", "settings").asObject()
        "xunlei" -> api.get("xunlei", "settings").asObject()
        "xunlei-win" -> api.get("xunlei-win", "settings").asObject()
        "movie", "btbtla" -> api.get("chat", "subscriptions", "settings").asObject()
        else -> api.get("hls", "settings").asObject()
    }
    suspend fun saveSettings(kind: String, body: JsonObject) {
        when (kind) {
            "magnet" -> api.post("qbittorrent", "settings", body = body)
            "video" -> api.post("video", "settings", body = body)
            "xunlei" -> api.post("xunlei", "settings", body = body)
            "xunlei-win" -> api.post("xunlei-win", "settings", body = body)
            "movie", "btbtla" -> api.post("chat", "subscriptions", "settings", body = body)
            else -> api.post("hls", "settings", body = body)
        }.requireSuccess()
    }

    // ---- 巡检记录（订阅巡检结果，与网页端「巡检记录」同一接口） ----
    suspend fun checkLogs(limit: Int = 200, source: String = ""): List<CheckLog> =
        api.get("chat", "check-logs", query = buildMap {
            put("limit", limit.toString()); if (source.isNotBlank()) put("source", source)
        }).asArray().map { CheckLog.from(it.asObject()) }
    suspend fun clearCheckLogs() { api.delete("chat", "check-logs").requireSuccess() }

    // ---- 剧集管理（扫描共享下载目录并批量删除） ----
    suspend fun libraryScan(): LibraryScan = LibraryScan.from(api.get("library", "episodes").asObject())
    suspend fun libraryVideoInfo(ids: List<String>): Map<String, LibraryVideoInfo> =
        api.post("library", "video-info", body = jsonBody { put("ids", JsonArray(ids.map(::JsonPrimitive))) })
            .asArray().map { it.asObject() }.associate { row ->
                row.text("id") to LibraryVideoInfo.from(row["videoInfo"].asObject())
            }
    suspend fun libraryDelete(ids: List<String>): LibraryDeleteResult {
        require(ids.isNotEmpty()) { "请选择要删除的剧集" }
        return LibraryDeleteResult.from(api.request("DELETE", listOf("library", "episodes"),
            jsonBody { put("ids", JsonArray(ids.map(::JsonPrimitive))) }).asObject())
    }

    // Both app projects share the build protocol and use isolated backend mailboxes.
    suspend fun androidStatus(serverId: String = "local", project: AndroidBuildProject = AndroidBuildProject.Huluwa): JsonObject =
        api.get(*project.path("status"), query = mapOf("serverId" to serverId)).asObject()
    suspend fun androidTasks(limit: Int = 30, project: AndroidBuildProject = AndroidBuildProject.Huluwa): List<AndroidBuildTask> =
        api.get(*project.path("tasks"), query = mapOf("limit" to limit.toString())).asArray().map { AndroidBuildTask.from(it.asObject()) }
    suspend fun androidUpdate(serverId: String = "local", project: AndroidBuildProject = AndroidBuildProject.Huluwa): AndroidBuildTask =
        AndroidBuildTask.from(api.post(*project.path("update"), query = mapOf("serverId" to serverId)).asObject())
    suspend fun androidBuild(serverId: String = "local", project: AndroidBuildProject = AndroidBuildProject.Huluwa): AndroidBuildTask =
        AndroidBuildTask.from(api.post(*project.path("build"), query = mapOf("serverId" to serverId)).asObject())
    suspend fun androidCancel(id: Long, project: AndroidBuildProject = AndroidBuildProject.Huluwa): AndroidBuildTask =
        AndroidBuildTask.from(api.post(*project.path("tasks", id.toString(), "cancel")).asObject())
    suspend fun androidLogs(id: Long, cursor: Long, project: AndroidBuildProject = AndroidBuildProject.Huluwa): AndroidLogChunk =
        AndroidLogChunk.from(api.get(*project.path("tasks", id.toString(), "logs"), query = mapOf("cursor" to cursor.toString())).asObject())
}
