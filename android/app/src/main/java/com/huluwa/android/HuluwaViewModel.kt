package com.huluwa.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.LocalDateTime
import java.util.UUID

sealed interface AppSheet {
    data object Account : AppSheet
    data object NewDownload : AppSheet
    data class TunnelEdit(val tunnel: Tunnel? = null) : AppSheet
    data class Settings(val kind: String, val data: LoadState<JsonObject>) : AppSheet
    data class Films(val card: FilmCard, val data: LoadState<JsonObject>) : AppSheet
    data class Episode(val card: FilmCard, val source: String, val item: PlayItem) : AppSheet
    data class Text(val title: String, val data: LoadState<String>, val onSave: ((String) -> Unit)? = null) : AppSheet
    data class ContainerDetail(val container: DockerContainer, val data: LoadState<JsonObject>) : AppSheet
    data class CreateResource(val section: DockerSection) : AppSheet
    data class Confirm(val title: String, val description: String, val positive: String, val action: () -> Unit) : AppSheet
}
data class AuthState(val server: String = "", val username: String = "admin", val loggedIn: Boolean = false, val working: Boolean = false, val setup: Boolean = false, val error: String? = null)
data class ChatState(val session: Session? = null, val messages: List<ChatMessage> = emptyList(), val loading: Boolean = false, val loadingEarlier: Boolean = false,
    val hasEarlier: Boolean = false, val error: String? = null, val processing: Boolean = false)
data class AppState(
    val auth: AuthState = AuthState(), val tab: MainTab = MainTab.Sessions,
    val sessions: LoadState<List<Session>> = LoadState(emptyList()), val chat: ChatState = ChatState(),
    val downloads: LoadState<List<DownloadTask>> = LoadState(emptyList()), val subscriptions: LoadState<List<Subscription>> = LoadState(emptyList()),
    val tunnels: LoadState<List<Tunnel>> = LoadState(emptyList()), val runtime: FrpRuntime = FrpRuntime(),
    val containers: LoadState<List<DockerContainer>> = LoadState(emptyList()), val overview: DockerOverview = DockerOverview(),
    val dockerSection: DockerSection = DockerSection.Containers, val resources: Map<DockerSection, LoadState<List<DockerResource>>> = emptyMap(),
    val realtime: Boolean = false, val sheet: AppSheet? = null, val busy: String? = null, val actionError: String? = null,
)

class HuluwaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as HuluwaApplication).repository
    private val session = repository.api.store
    private val mutable = MutableStateFlow(AppState(auth = AuthState(session.server, session.username, session.loggedIn)))
    val state = mutable.asStateFlow()
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    private var foreground = false
    private var socketJob: Job? = null
    private var pollingJob: Job? = null
    private var chatJob: Job? = null
    private var authJob: Job? = null
    private var mutationJob: Job? = null
    private var sheetJob: Job? = null
    private val refreshJobs = mutableMapOf<String, Job>()
    private var generation = 0
    private var sheetGeneration = 0
    private var processingStarted = 0L

    fun notify(text: String) { viewModelScope.launch { notices.send(text) } }
    fun authenticate(server: String, username: String, password: String, confirmation: String) {
        if (authJob?.isActive == true) return
        val requestedSetup = mutable.value.auth.setup
        authJob = viewModelScope.launch {
            mutable.update { it.copy(auth = it.auth.copy(working = true, error = null)) }
            try {
                val address = ServerAddress.normalize(server)
                require(username.trim().isNotEmpty() && username.trim().length <= 32) { "请输入不超过 32 位的账号" }
                require(password.isNotEmpty()) { "请输入密码" }
                val initialized = repository.authStatus(address).flag("initialized")
                if (!initialized && !requestedSetup) {
                    mutable.update { it.copy(auth = it.auth.copy(server = address, username = username.trim(), working = false, setup = true)) }
                    return@launch
                }
                if (!initialized) {
                    require(password.length in 6..64) { "管理员密码长度应为 6–64 位" }
                    require(password == confirmation) { "两次输入的密码不一致" }
                }
                val result = repository.login(address, username.trim(), password, setup = !initialized)
                withContext(Dispatchers.IO) { session.save(address, result.text("username", fallback = username.trim()), result.text("token")) }
                mutable.value = AppState(auth = AuthState(session.server, session.username, true))
                if (foreground) startForegroundWork()
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { mutable.update { it.copy(auth = it.auth.copy(working = false, error = error.userMessage())) } }
        }
    }
    fun resetSetup() { mutable.update { it.copy(auth = it.auth.copy(setup = false, error = null)) } }
    fun logout(message: String? = null) {
        generation++; stopForegroundWork(); chatJob?.cancel(); sheetJob?.cancel(); mutationJob?.cancel()
        refreshJobs.values.forEach(Job::cancel); refreshJobs.clear()
        session.clear(); repository.clearCaches()
        mutable.value = AppState(auth = AuthState(session.server, session.username, error = message))
        if (message != null) notify(message)
    }
    private fun handleError(error: Throwable) {
        if (error is ApiException && error.code == 401) logout("登录已过期，请重新登录") else notify(error.userMessage())
    }
    fun setForeground(active: Boolean) {
        foreground = active
        if (active && session.loggedIn) startForegroundWork() else stopForegroundWork()
    }
    private fun startForegroundWork() {
        if (socketJob?.isActive != true) socketJob = viewModelScope.launch {
            ChatRealtime(repository.api).events().catch { handleError(it) }.collect { event -> when (event) {
                RealtimeEvent.Connected -> { mutable.update { it.copy(realtime = true) }; refreshSessions(true); refreshChat() }
                RealtimeEvent.Disconnected -> mutable.update { it.copy(realtime = false) }
                is RealtimeEvent.Frame -> onFrame(event.body)
            } }
        }
        if (pollingJob?.isActive != true) pollingJob = viewModelScope.launch {
            while (isActive && session.loggedIn) {
                refreshCurrent(true); refreshSessions(true); refreshChat()
                if (mutable.value.chat.processing && System.currentTimeMillis() - processingStarted > 180_000) mutable.update { it.copy(chat = it.chat.copy(processing = false)) }
                delay(if (mutable.value.realtime) 15_000 else 8_000)
            }
        }
    }
    private fun stopForegroundWork() {
        socketJob?.cancel(); socketJob = null; pollingJob?.cancel(); pollingJob = null
        mutable.update { it.copy(realtime = false) }
    }
    fun selectTab(tab: MainTab) { closeChat(); dismissSheet(); mutable.update { it.copy(tab = tab) }; refreshCurrent(false) }
    fun refreshCurrent(silent: Boolean = false) { when (mutable.value.tab) {
        MainTab.Sessions -> refreshSessions(silent)
        MainTab.Downloads -> refreshDownloads(silent)
        MainTab.Subscriptions -> refreshSubscriptions(silent)
        MainTab.Frp -> refreshFrp(silent)
        MainTab.Docker -> { refreshDocker(silent); if (mutable.value.dockerSection != DockerSection.Containers) refreshResources(silent) }
    } }
    private fun refresh(key: String, block: suspend () -> Unit) {
        if (!session.loggedIn || refreshJobs[key]?.isActive == true) return
        refreshJobs[key] = viewModelScope.launch { block() }
    }
    fun refreshSessions(silent: Boolean = false) = refresh("sessions") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(sessions = it.sessions.copy(loading = true, error = null)) }
        try { val data = repository.sessions(); if (epoch == generation) mutable.update { it.copy(sessions = LoadState(data, loaded = true)) } }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(sessions = it.sessions.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    fun refreshDownloads(silent: Boolean = false) = refresh("downloads") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(downloads = it.downloads.copy(loading = true, error = null)) }
        try {
            val result = repository.downloads(!silent)
            if (epoch == generation) mutable.update { old -> old.copy(downloads = LoadState((result.items + old.downloads.data.filter { it.source.name in result.failedSources }).sortedByDescending { it.createdAt }, error = result.warnings.joinToString("\n").ifBlank { null }, loaded = true)) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(downloads = it.downloads.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    fun refreshSubscriptions(silent: Boolean = false) = refresh("subscriptions") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(subscriptions = it.subscriptions.copy(loading = true, error = null)) }
        try {
            val result = repository.subscriptions()
            if (epoch == generation) mutable.update { old -> old.copy(subscriptions = LoadState((result.items + old.subscriptions.data.filter { it.source.name in result.failedSources }).sortedByDescending { it.createdAt }, error = result.warnings.joinToString("\n").ifBlank { null }, loaded = true)) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(subscriptions = it.subscriptions.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    fun refreshFrp(silent: Boolean = false) = refresh("frp") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(tunnels = it.tunnels.copy(loading = true, error = null)) }
        try { val (tunnels, runtime) = repository.frp(); if (epoch == generation) mutable.update { it.copy(tunnels = LoadState(tunnels, loaded = true), runtime = runtime) } }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(tunnels = it.tunnels.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    fun refreshDocker(silent: Boolean = false) = refresh("docker") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(containers = it.containers.copy(loading = true, error = null)) }
        try { val (containers, overview) = repository.docker(); if (epoch == generation) mutable.update { it.copy(containers = LoadState(containers, loaded = true), overview = overview) } }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(containers = it.containers.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    fun selectDockerSection(section: DockerSection) { mutable.update { it.copy(dockerSection = section) }; if (section != DockerSection.Containers) refreshResources() }
    fun refreshResources(silent: Boolean = false) {
        val section = mutable.value.dockerSection; if (section == DockerSection.Containers) return
        refresh("resources:$section") {
            val epoch = generation
            if (!silent) mutable.update { it.copy(resources = it.resources + (section to (it.resources[section] ?: LoadState(emptyList())).copy(loading = true, error = null))) }
            try { val result = repository.dockerResources(section); if (epoch == generation) mutable.update { it.copy(resources = it.resources + (section to LoadState(result, loaded = true))) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(resources = it.resources + (section to (it.resources[section] ?: LoadState(emptyList())).copy(loading = false, error = error.userMessage(), loaded = true))) }; if (error is ApiException && error.code == 401) handleError(error) } }
        }
    }

    private var chatGeneration = 0
    private var localId = -1L
    fun openSession(session: Session) {
        chatGeneration++; chatJob?.cancel()
        val current = chatGeneration
        mutable.update { it.copy(chat = ChatState(session = session, loading = true)) }
        chatJob = viewModelScope.launch {
            try {
                val rows = repository.messages(session.id)
                if (current != chatGeneration) return@launch
                mutable.update { it.copy(chat = it.chat.copy(messages = rows, loading = false, hasEarlier = rows.size >= 50)) }
                sourceResult { repository.markRead(session.id) }.exceptionOrNull()?.let { if (it is ApiException && it.code == 401) handleError(it) }
                if (current == chatGeneration) refreshSessions(true)
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == chatGeneration) { mutable.update { it.copy(chat = it.chat.copy(loading = false, error = error.userMessage())) }; if (error is ApiException && error.code == 401) handleError(error) } }
        }
    }
    fun closeChat() { chatGeneration++; chatJob?.cancel(); mutable.update { it.copy(chat = ChatState()) } }
    private fun merge(old: List<ChatMessage>, incoming: List<ChatMessage>) = (old + incoming).associateBy { it.id }.values.sortedWith(compareBy<ChatMessage> { if (it.id < 0) Long.MAX_VALUE else it.id }.thenBy { it.createdAt })
    fun refreshChat() {
        val chat = mutable.value.chat; val id = chat.session?.id ?: return
        if (chat.loading) return
        val current = chatGeneration
        refresh("chat:$id") {
            try {
                val rows = repository.messages(id)
                if (current == chatGeneration && mutable.value.chat.session?.id == id) {
                    mutable.update { old ->
                        val sent = old.chat.messages.filter { it.delivery == Delivery.Sent }
                        // 长时间离线后若最新一页与旧缓存完全不重叠，以新一页重建分页边界，避免中间消息永久漏读。
                        val gap = rows.size >= 50 && sent.isNotEmpty() && rows.none { row -> sent.any { it.id == row.id } }
                        val base = if (gap) old.chat.messages.filter { it.delivery != Delivery.Sent } else old.chat.messages
                        old.copy(chat = old.chat.copy(messages = merge(base, rows), error = null,
                            hasEarlier = if (gap) true else old.chat.hasEarlier,
                            processing = old.chat.processing && rows.none { row -> row.role == "bot" && old.chat.messages.none { it.id == row.id } }))
                    }
                    sourceResult { repository.markRead(id) }.exceptionOrNull()?.let { if (it is ApiException && it.code == 401) handleError(it) }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == chatGeneration) { mutable.update { it.copy(chat = it.chat.copy(error = error.userMessage())) }; if (error is ApiException && error.code == 401) handleError(error) } }
        }
    }
    fun loadEarlier() {
        val chat = mutable.value.chat; val id = chat.session?.id ?: return
        if (chat.loadingEarlier || !chat.hasEarlier) return
        val before = chat.messages.filter { it.id > 0 }.minOfOrNull { it.id } ?: return
        val current = chatGeneration
        mutable.update { it.copy(chat = it.chat.copy(loadingEarlier = true)) }
        viewModelScope.launch {
            try { val rows = repository.messages(id, before); if (current == chatGeneration) mutable.update { it.copy(chat = it.chat.copy(messages = merge(rows, it.chat.messages), loadingEarlier = false, hasEarlier = rows.size >= 50)) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == chatGeneration) { mutable.update { it.copy(chat = it.chat.copy(loadingEarlier = false)) }; handleError(error) } }
        }
    }
    fun send(content: String, retry: ChatMessage? = null) {
        val chat = mutable.value.chat; val selected = chat.session ?: return
        if (selected.kind == "system" || content.isBlank()) return
        val current = chatGeneration; val nonce = retry?.requestId ?: UUID.randomUUID().toString()
        val outgoing = retry?.copy(delivery = Delivery.Sending) ?: ChatMessage(localId--, selected.id, "user", "text", content.trim(), content.trim(), JsonObject(emptyMap()), LocalDateTime.now().toString().replace('T', ' '), Delivery.Sending, nonce)
        processingStarted = System.currentTimeMillis()
        mutable.update { it.copy(chat = it.chat.copy(messages = merge(it.chat.messages.filterNot { item -> item.requestId == nonce }, listOf(outgoing)), processing = true)) }
        viewModelScope.launch {
            try {
                val accepted = repository.send(selected.id, content.trim(), nonce)
                if (current == chatGeneration) mutable.update { it.copy(chat = it.chat.copy(messages = merge(it.chat.messages.filterNot { item -> item.requestId == nonce }, accepted))) }
                refreshSessions(true)
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) {
                if (current == chatGeneration) mutable.update { it.copy(chat = it.chat.copy(processing = false, messages = it.chat.messages.map { item -> if (item.requestId == nonce) item.copy(delivery = Delivery.Failed) else item })) }
                handleError(error)
            }
        }
    }
    private fun onFrame(frame: JsonObject) {
        val id = frame.text("sessionId")
        when (frame.text("type")) {
            "chat.sessions" -> mutable.update { it.copy(sessions = LoadState(frame.rows("sessions").map { row -> Session.from(row.asObject()) }, loaded = true)) }
            "chat.messages" -> {
                val rows = frame.rows("messages").map { ChatMessage.from(it.asObject()) }
                if (mutable.value.chat.session?.id == id) {
                    mutable.update { it.copy(chat = it.chat.copy(messages = merge(it.chat.messages, rows), processing = false)) }
                    if (foreground) viewModelScope.launch { sourceResult { repository.markRead(id) }.exceptionOrNull()?.let { if (it is ApiException && it.code == 401) handleError(it) } }
                }
                refreshSessions(true)
            }
            "chat.processing" -> if (mutable.value.chat.session?.id == id) { processingStarted = System.currentTimeMillis(); mutable.update { it.copy(chat = it.chat.copy(processing = true)) } }
            "chat.done" -> if (mutable.value.chat.session?.id == id) mutable.update { it.copy(chat = it.chat.copy(processing = false)) }
            "error" -> if (frame.number("code")?.toInt() == 401) logout("登录已过期，请重新登录")
        }
    }
    fun showSheet(sheet: AppSheet) { sheetGeneration++; sheetJob?.cancel(); mutable.update { it.copy(sheet = sheet, actionError = null) } }
    fun dismissSheet() { sheetGeneration++; sheetJob?.cancel(); mutable.update { it.copy(sheet = null) } }
    fun confirm(title: String, text: String, positive: String = "确认", action: () -> Unit) { showSheet(AppSheet.Confirm(title, text, positive, action)) }
    fun confirmSheet() { val action = (mutable.value.sheet as? AppSheet.Confirm)?.action ?: return; dismissSheet(); action() }
    private fun perform(key: String, success: String, close: Boolean = true, after: () -> Unit = {}, block: suspend () -> Unit) {
        if (mutable.value.busy != null || !session.loggedIn) return
        val epoch = generation; val sheetEpoch = sheetGeneration
        mutable.update { it.copy(busy = key, actionError = null) }
        mutationJob = viewModelScope.launch {
            try {
                block()
                if (epoch == generation) {
                    if (close && sheetEpoch == sheetGeneration) dismissSheet()
                    if (success.isNotBlank()) notify(success)
                    after()
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(actionError = error.userMessage()) }; handleError(error); if (epoch == generation) after() } }
            finally { if (epoch == generation) mutable.update { it.copy(busy = null) } }
        }
    }
    fun clearChat() {
        val selected = mutable.value.chat.session ?: return
        if (selected.kind == "system") return
        confirm("清空会话", "确定清空「${selected.title}」的全部消息吗？", "清空") {
            perform("clear-chat", "会话已清空", after = { refreshSessions() }) {
                repository.clearChat(selected.id); chatGeneration++
                mutable.update { it.copy(chat = it.chat.copy(messages = emptyList(), processing = false, hasEarlier = false, loadingEarlier = false)) }
            }
        }
    }
    fun newDownload(title: String, magnet: String) = perform("new-download", "下载任务已提交", after = { refreshDownloads() }) {
        require(magnet.startsWith("magnet:?", true)) { "请输入有效的磁力链接" }; repository.downloadMagnet(title.trim().ifBlank { "手动下载" }, magnet.trim())
    }
    fun cancelDownload(task: DownloadTask) = confirm("取消下载", "取消「${task.title} ${task.episode}」后，临时分片会被清理，已归档文件保留。", "取消下载") {
        perform("cancel:${task.key}", "任务已取消", after = { refreshDownloads() }) { repository.cancel(task) }
    }
    fun retryDownload(task: DownloadTask) = perform("retry:${task.key}", "任务已重新加入队列", close = false, after = { refreshDownloads() }) { repository.retry(task) }
    fun clearDownloads(source: DownloadSource) = confirm("清空下载记录", if (source == DownloadSource.Hls) "清空已结束的离线下载记录，保留进行中的任务和已下载文件。" else "清空${source.title}的记录，下载器中的任务会继续运行。", "清空记录") {
        perform("clear-downloads", "记录已清空", after = { refreshDownloads() }) { repository.clearDownloads(source) }
    }
    fun downloadLog(task: DownloadTask) = loadText("下载日志") { repository.downloadLog(task.id) }
    fun checkSubscription(sub: Subscription) = perform("check:${sub.key}", "", close = false, after = { refreshSubscriptions(); refreshDownloads(true) }) { notify(repository.check(sub)) }
    fun toggleSubscription(sub: Subscription) = perform("toggle:${sub.key}", if (sub.enabled) "已暂停追更" else "已恢复追更", after = { refreshSubscriptions(); refreshDownloads(true) }) { repository.toggle(sub) }
    fun deleteSubscription(sub: Subscription) = confirm("删除订阅", if (sub.source == SubscriptionSource.Hls) "删除「${sub.title}」及其追更任务记录，已归档文件保留。" else "删除「${sub.title}」及其磁力基线、下载记录，已下载文件保留。", "删除订阅") {
        perform("delete:${sub.key}", "订阅已删除", after = { refreshSubscriptions(); refreshDownloads(true) }) { repository.deleteSubscription(sub) }
    }
    fun subscribe(card: FilmCard) = perform("subscribe:${card.source}:${card.id}", "已加入我的订阅", close = false, after = { refreshSubscriptions() }) { repository.subscribe(card) }
    fun filmResources(card: FilmCard) {
        showSheet(AppSheet.Films(card, LoadState(JsonObject(emptyMap()), loading = true))); val current = sheetGeneration
        sheetJob = viewModelScope.launch { try { val result = repository.filmResources(card); if (current == sheetGeneration) mutable.update { it.copy(sheet = AppSheet.Films(card, LoadState(result, loaded = true))) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == sheetGeneration) { mutable.update { it.copy(sheet = AppSheet.Films(card, LoadState(JsonObject(emptyMap()), error = error.userMessage(), loaded = true))) }; if (error is ApiException && error.code == 401) handleError(error) } } }
    }
    fun enqueueEpisode(card: FilmCard, source: String, item: PlayItem, season: Int, episode: Int) = perform("episode", "已加入离线下载", after = { refreshDownloads() }) { repository.enqueueEpisode(card, source, item, season, episode) }
    fun enqueueMagnet(card: FilmCard, item: JsonObject) = perform("magnet", "下载任务已提交", after = { refreshDownloads() }) { repository.enqueueMagnet(card, item) }
    fun saveTunnel(original: String?, draft: TunnelDraft) = perform("tunnel-save", "隧道已保存并应用", after = { refreshFrp() }) { require(draft.validationError() == null) { draft.validationError().orEmpty() }; repository.saveTunnel(original, draft) }
    fun toggleTunnel(tunnel: Tunnel) = confirm(if (tunnel.enabled) "停用隧道" else "启用隧道", "确定${if (tunnel.enabled) "停用" else "启用"}「${tunnel.name}」？保存后会热加载配置。") {
        perform("tunnel:${tunnel.name}", "隧道状态已更新", after = { refreshFrp() }) { repository.toggleTunnel(tunnel) }
    }
    fun deleteTunnel(tunnel: Tunnel) = confirm("删除隧道", "确定删除「${tunnel.name}」？配置会立即热加载。", "删除") {
        perform("tunnel-delete", "隧道已删除", after = { refreshFrp() }) { repository.deleteTunnel(tunnel) }
    }
    fun showFrpConfig() = loadText("Frp 配置文件", onSave = { text -> confirm("保存并热加载", "新配置会覆盖当前 frpc.ini，并立即应用。", "保存") { perform("frp-config", "配置已保存并热加载", after = { refreshFrp() }) { repository.saveFrpConfig(text) } } }) { repository.frpConfig() }
    fun reloadFrp() = confirm("热加载配置", "重新加载当前 frpc 配置，继续保留现有连接。", "热加载") { perform("frp-reload", "配置已热加载", after = { refreshFrp() }) { repository.reloadFrp() } }
    fun restartFrp() = confirm("重启 frpc", "重启期间，所有隧道会暂时断开。", "重启") { perform("frp-restart", "已重启 frpc", after = { refreshFrp() }) { repository.restartFrp() } }
    fun containerAction(container: DockerContainer, action: String) {
        val title = when (action) { "start" -> "启动"; "stop" -> "停止"; else -> "重启" }
        confirm("${title}容器", "确定${title}「${container.name}」？${if (action == "start") "" else "服务可能暂时不可用。"}", title) {
            perform("container:${container.id}", "容器已${title}", after = { refreshDocker() }) { repository.containerAction(container, action) }
        }
    }
    fun deleteContainer(container: DockerContainer) = confirm("删除容器", "确定删除「${container.name}」？请先停止容器。此操作不会请求强制删除或删除关联卷。", "删除") {
        perform("container-delete", "容器已删除", after = { refreshDocker() }) { repository.deleteContainer(container.id) }
    }
    fun containerLogs(container: DockerContainer) = loadText("${container.name} · 运行日志") { repository.containerLogs(container.id) }
    fun frpLogs() = loadText("frpc · 运行日志") {
        val container = repository.api.get("docker", "containers", query = mapOf("all" to "true")).asArray().map { DockerContainer.from(it.asObject()) }
            .firstOrNull { it.name == mutable.value.runtime.container } ?: throw IllegalArgumentException("没有找到 frpc 容器")
        repository.containerLogs(container.id)
    }
    fun containerDetail(container: DockerContainer) {
        showSheet(AppSheet.ContainerDetail(container, LoadState(JsonObject(emptyMap()), loading = true))); val current = sheetGeneration
        sheetJob = viewModelScope.launch { try { val result = repository.inspectContainer(container.id); if (current == sheetGeneration) mutable.update { it.copy(sheet = AppSheet.ContainerDetail(container, LoadState(result, loaded = true))) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == sheetGeneration) { mutable.update { it.copy(sheet = AppSheet.ContainerDetail(container, LoadState(JsonObject(emptyMap()), error = error.userMessage(), loaded = true))) }; if (error is ApiException && error.code == 401) handleError(error) } } }
    }
    fun createResource(section: DockerSection, name: String) = perform("resource-create", "操作已完成", after = { refreshResources(); refreshDocker(true) }) {
        require(name.isNotBlank()) { "请输入名称" }; repository.createResource(section, name.trim())
    }
    fun removeResource(section: DockerSection, item: DockerResource) = confirm("删除${section.title}", "确定删除「${item.name}」？${if (section == DockerSection.Volumes) "存储卷中的数据可能永久删除。" else "正在使用的资源会由服务端拒绝删除。"}", "删除") {
        perform("resource-delete", "资源已删除", after = { refreshResources(); refreshDocker(true) }) { repository.removeResource(section, item.id) }
    }
    fun showSettings(kind: String) {
        showSheet(AppSheet.Settings(kind, LoadState(JsonObject(emptyMap()), loading = true))); val current = sheetGeneration
        sheetJob = viewModelScope.launch { try { val result = repository.settings(kind); if (current == sheetGeneration) mutable.update { it.copy(sheet = AppSheet.Settings(kind, LoadState(result, loaded = true))) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == sheetGeneration) { mutable.update { it.copy(sheet = AppSheet.Settings(kind, LoadState(JsonObject(emptyMap()), error = error.userMessage(), loaded = true))) }; if (error is ApiException && error.code == 401) handleError(error) } } }
    }
    fun saveSettings(kind: String, body: JsonObject) = perform("settings-save", "设置已保存") { repository.saveSettings(kind, body) }
    fun loadText(title: String, onSave: ((String) -> Unit)? = null, load: suspend () -> String) {
        showSheet(AppSheet.Text(title, LoadState("", loading = true), onSave)); val current = sheetGeneration
        sheetJob = viewModelScope.launch { try { val result = load(); if (current == sheetGeneration) mutable.update { it.copy(sheet = AppSheet.Text(title, LoadState(result, loaded = true), onSave)) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == sheetGeneration) { mutable.update { it.copy(sheet = AppSheet.Text(title, LoadState("", error = error.userMessage(), loaded = true), onSave)) }; if (error is ApiException && error.code == 401) handleError(error) } } }
    }
    fun changePassword(old: String, new: String, confirmation: String) = perform("password", "密码已修改，请重新登录", after = { logout() }) {
        require(new.length in 6..64) { "新密码长度应为 6–64 位" }; require(new == confirmation) { "两次输入的新密码不一致" }; repository.changePassword(old, new)
    }
}
