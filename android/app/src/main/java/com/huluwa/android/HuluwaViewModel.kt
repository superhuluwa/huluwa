package com.huluwa.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

sealed interface AppSheet {
    data object Account : AppSheet
    data object NewDownload : AppSheet
    data class TunnelEdit(val tunnel: Tunnel? = null) : AppSheet
    data class Settings(val kind: String, val data: LoadState<JsonObject>) : AppSheet
    /** defaultSeason 非空时使用订阅季号直接下载，省略参数配置。 */
    data class Films(val card: FilmCard, val data: LoadState<JsonObject>, val defaultSeason: Int? = null,
        val onlineData: LoadState<JsonObject> = LoadState(JsonObject(emptyMap()))) : AppSheet
    data class Episode(val card: FilmCard, val source: String, val item: PlayItem) : AppSheet
    /** 懂片帝资源弹框：独立于 Films（电影港 / 电影天堂），各线路按需解析 */
    data class Dpd(
        val card: DpdCard,
        val detail: LoadState<JsonObject>,
        /** 线路名 → 该线路各集；点选线路后才拉取 */
        val lines: Map<String, LoadState<JsonObject>> = emptyMap(),
        /** 正在解析的线路名（底部提示用） */
        val busyLine: String = "",
    ) : AppSheet
    data class Text(val title: String, val data: LoadState<String>, val onSave: ((String) -> Unit)? = null) : AppSheet
    data class ContainerDetail(val container: DockerContainer, val data: LoadState<JsonObject>) : AppSheet
    data class CreateResource(val section: DockerSection) : AppSheet
    data class Confirm(val title: String, val description: String, val positive: String, val action: () -> Unit) : AppSheet
}
data class AuthState(val server: String = "", val username: String = "admin", val loggedIn: Boolean = false, val working: Boolean = false, val setup: Boolean = false, val error: String? = null)
data class ChatState(val session: Session? = null, val messages: List<ChatMessage> = emptyList(), val loading: Boolean = false, val loadingEarlier: Boolean = false,
    val hasEarlier: Boolean = false, val error: String? = null, val processing: Boolean = false)
/** 消息分页大小：与接口 limit 和本地缓存的一页保持一致。 */
private const val CHAT_PAGE_SIZE = 50
data class AppState(
    val auth: AuthState = AuthState(), val tab: MainTab = MainTab.Functions,
    val sessions: LoadState<List<Session>> = LoadState(emptyList()), val chat: ChatState = ChatState(),
    val pinnedSessionIds: List<String> = emptyList(),
    val cachedLocalSessions: List<Session> = emptyList(), val sessionCacheReady: Boolean = false,
    val upgrade: AppUpgradeState = AppUpgradeState(),
    val downloads: LoadState<List<DownloadTask>> = LoadState(emptyList()), val subscriptions: LoadState<List<Subscription>> = LoadState(emptyList()),
    val subscriptionSourcesLoaded: Set<SubscriptionSource> = emptySet(),
    val tunnels: LoadState<List<Tunnel>> = LoadState(emptyList()), val runtime: FrpRuntime = FrpRuntime(),
    val containers: LoadState<List<DockerContainer>> = LoadState(emptyList()), val overview: DockerOverview = DockerOverview(),
    val dockerSection: DockerSection = DockerSection.Containers, val resources: Map<DockerSection, LoadState<List<DockerResource>>> = emptyMap(),
    val realtime: Boolean = false, val sheet: AppSheet? = null, val busy: String? = null, val actionError: String? = null,
    val terminalBuddyBuild: AndroidBuildState = AndroidBuildState(),
    val androidBuild: AndroidBuildState = AndroidBuildState(), val clock: Long = System.currentTimeMillis(),
    val checkLogs: LoadState<List<CheckLog>> = LoadState(emptyList()),
    val library: LibraryState = LibraryState(),
    val browserVisible: Boolean = false, val browserUrl: String = "",
    val mine: MineOverview = MineOverview(), val serverStatusVisible: Boolean = false,
) {
    fun build(project: AndroidBuildProject) = if (project == AndroidBuildProject.Huluwa) androidBuild else terminalBuddyBuild
    fun updateBuild(project: AndroidBuildProject, transform: (AndroidBuildState) -> AndroidBuildState): AppState =
        if (project == AndroidBuildProject.Huluwa) copy(androidBuild = transform(androidBuild))
        else copy(terminalBuddyBuild = transform(terminalBuddyBuild))
}

/** 剧集管理：扫描结果 + 当前展开的剧集分组（UI 层可自行维护更细的状态） */
data class LibraryState(
    val scan: LoadState<LibraryScan> = LoadState(LibraryScan()),
    val deleting: Boolean = false,
)

class HuluwaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as HuluwaApplication).repository
    private val session = repository.api.store
    private val sessionListCache = (application as HuluwaApplication).sessionListCache
    private val appUpdates = (application as HuluwaApplication).appUpdates
    private val terminalBuddyBuildDownloads = (application as HuluwaApplication).terminalBuddyBuildDownloads
    private fun buildDownloader(project: AndroidBuildProject) = if (project == AndroidBuildProject.Huluwa) buildDownloads else terminalBuddyBuildDownloads
    private val buildDownloads = (application as HuluwaApplication).buildDownloads
    private val buildPreferences = AndroidBuildPreferences(application)
    private val sessionListPreferences = SessionListPreferences(application)
    private val mutable = MutableStateFlow(initialState())
    private fun initialState(error: String? = null) = AppState(
        auth = AuthState(session.server, session.username, session.loggedIn, error = error),
        pinnedSessionIds = if (session.loggedIn) sessionListPreferences.pinnedSessions(session.server, session.username) else emptyList(),
        androidBuild = AndroidBuildState(serverId = buildPreferences.selectedServer(session.server)),
        terminalBuddyBuild = AndroidBuildState(serverId = buildPreferences.selectedServer(session.server, AndroidBuildProject.TerminalBuddy)),
    )
    private fun withAppSessions(value: AppState, upgrade: AppUpgradeState): AppState {
        val cached = value.cachedLocalSessions.associateBy { it.id }
        val upgradeTime = upgrade.latest?.publishedAt.orEmpty().ifBlank {
            if (upgrade.checkedAt > 0) LocalDateTime.ofInstant(Instant.ofEpochMilli(upgrade.checkedAt), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) else ""
        }
        val liveUpgradeSession = Session(UPGRADE_SESSION_ID, "在线升级", "upgrade", if (upgrade.hasUpdate) 1 else 0,
            null, upgrade.sessionPreview, upgradeTime)
        val upgradeSession = if (upgradePreviewReady(upgrade)) liveUpgradeSession
            else cached[UPGRADE_SESSION_ID] ?: liveUpgradeSession
        val managementSessions = ManagementService.entries.map { service ->
            val preview = when (service) {
                ManagementService.Frp -> when {
                    value.tunnels.error != null -> "暂时无法连接 Frp，点击查看详情"
                    !value.tunnels.loaded -> if (value.tunnels.loading) "正在同步隧道状态…" else "内网穿透 · 隧道、配置与日志"
                    !value.runtime.running -> "frpc 未运行 · 共 ${value.tunnels.data.size} 条隧道"
                    else -> "${value.tunnels.data.count { it.running }}/${value.tunnels.data.size} 条隧道运行中 · 点击管理"
                }
                ManagementService.Docker -> when {
                    value.containers.error != null -> "暂时无法连接 Docker，点击查看详情"
                    !value.containers.loaded -> if (value.containers.loading) "正在同步容器状态…" else "容器服务 · 容器、镜像、网络与存储卷"
                    !value.overview.online -> "Docker 连接异常，点击查看详情"
                    else -> "${value.overview.running}/${value.overview.total} 个容器运行中 · ${value.overview.images} 个镜像"
                }
                ManagementService.AppBuild -> androidBuildPreview(value.androidBuild)
                ManagementService.TerminalBuddyBuild -> androidBuildPreview(value.terminalBuddyBuild)
                ManagementService.CheckLogs -> when {
                    value.checkLogs.error != null -> "巡检记录加载失败，点击重试"
                    !value.checkLogs.loaded -> if (value.checkLogs.loading) "正在同步巡检记录…" else "电影港与电影天堂的订阅巡检历史"
                    value.checkLogs.data.isEmpty() -> "还没有巡检记录，订阅后每个周期都会留一条结果"
                    else -> value.checkLogs.data.firstOrNull()?.let { log ->
                        "${log.sourceLabel} · ${log.seriesTitle} · ${log.statusLabel}"
                    } ?: "订阅巡检历史"
                }
                ManagementService.Library -> when {
                    value.library.scan.error != null -> "下载目录扫描失败，点击重试"
                    !value.library.scan.loaded -> if (value.library.scan.loading) "正在扫描下载目录…" else "扫描共享下载目录，管理磁盘空间"
                    value.library.scan.data.entries.isEmpty() -> "下载目录里还没有可管理的剧集"
                    else -> "${value.library.scan.data.seriesCount} 部作品 · ${value.library.scan.data.entries.size} 个视频 · ${formatBytes(value.library.scan.data.totalBytes.toDouble())}"
                }
                // 我的订阅：电影港与电影天堂两个来源合并统计，失败只标注单个来源
                ManagementService.Subscriptions -> when {
                    value.subscriptions.loading -> "正在同步订阅…"
                    value.subscriptions.error != null && value.subscriptions.data.isEmpty() -> "订阅加载失败，点击重试"
                    value.subscriptions.data.isEmpty() -> "电影港与电影天堂的订阅，追更状态与资源列表"
                    else -> {
                        val suffix = if (value.subscriptions.error != null) " · 部分来源加载失败" else ""
                        "${value.subscriptions.data.size} 个订阅$suffix"
                    }
                }
            }
            val ready = managementPreviewReady(value, service)
            // 先沿用上次展示的摘要，拿到有效服务数据后再替换，避免缓存被加载提示覆盖。
            if (!ready && cached[service.sessionId] != null) cached.getValue(service.sessionId)
            else Session(service.sessionId, service.title, service.kind, 0, null, preview)
        }
        val dengtaSession = Session(DENGTA_SESSION_ID, "灯塔", DENGTA_SESSION_KIND, 0, null,
            "影视榜单 · 剧集详情 · 追剧日历")
        val localIds = managementSessions.map { it.id }.toSet() + UPGRADE_SESSION_ID + DENGTA_SESSION_ID
        val remoteSessions = value.sessions.data.filterNot { it.isLocal || it.id in localIds }
            .groupBy { it.id }.values.map { rows ->
                rows.maxBy { it.updatedAt }.copy(unread = rows.sumOf { it.unread })
            }
        // 最新置顶的会话排在最前，其余仍按更新时间排序；管理会话无消息时间，按最旧处理。
        val pinOrder = value.pinnedSessionIds.withIndex().associate { it.value to it.index }
        val sortedSessions = (remoteSessions + managementSessions + upgradeSession + dengtaSession).sortedWith(
            compareBy<Session> { pinOrder[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.updatedAt.ifBlank { "0000-00-00 00:00:00" } }.thenBy { it.id })
        return value.copy(upgrade = upgrade, sessions = value.sessions.copy(data = sortedSessions))
    }
    private fun managementPreviewReady(value: AppState, service: ManagementService) = when (service) {
        ManagementService.Frp -> value.tunnels.loaded && value.tunnels.error == null
        ManagementService.Docker -> value.containers.loaded && value.containers.error == null
        ManagementService.AppBuild -> value.androidBuild.tasks.loaded && !value.androidBuild.connectionError
        ManagementService.TerminalBuddyBuild -> value.terminalBuddyBuild.tasks.loaded && !value.terminalBuddyBuild.connectionError
        ManagementService.CheckLogs -> value.checkLogs.loaded && value.checkLogs.error == null
        ManagementService.Library -> value.library.scan.loaded && value.library.scan.error == null
        ManagementService.Subscriptions -> value.subscriptions.loaded
            && (value.subscriptions.error == null || value.subscriptions.data.isNotEmpty())
    }
    private fun upgradePreviewReady(upgrade: AppUpgradeState) = (upgrade.checked && upgrade.checkError == null)
        || upgrade.download.active || upgrade.download.phase == UpgradePhase.Ready

    val state = combine(mutable, appUpdates.state, buildDownloads.state, terminalBuddyBuildDownloads.state) { value, upgrade, download, terminalDownload ->
        withAppSessions(value.copy(androidBuild = value.androidBuild.copy(download = download),
            terminalBuddyBuild = value.terminalBuddyBuild.copy(download = terminalDownload)), upgrade)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        withAppSessions(mutable.value.copy(androidBuild = mutable.value.androidBuild.copy(download = buildDownloads.state.value),
            terminalBuddyBuild = mutable.value.terminalBuddyBuild.copy(download = terminalBuddyBuildDownloads.state.value)), appUpdates.state.value))

    /**
     * 服务器是否可达：由 ApiClient 在每次请求后更新（成功即在线，连接失败即离线）。
     * 后台轮询会自动重试，界面层只把它渲染成标题栏的「重连中」徽标，不再弹错误卡片。
     */
    val serverReachable: StateFlow<Boolean> = repository.api.reachable

    /** 构建会话预览：与网页端概览一致，优先展示进行中的任务。 */
    private fun androidBuildPreview(build: AndroidBuildState): String {
        val status = build.status
        val task = status.activeTask ?: build.selectedTask
        return when {
            !status.enabled && build.tasks.loaded -> "安卓构建服务尚未启用，点击查看详情"
            build.connectionError -> "暂时无法连接构建服务，点击重试"
            !build.tasks.loaded -> if (build.tasks.loading) "正在同步构建状态…" else "更新源码、编译安装包，查看执行日志"
            task != null && task.active -> "${task.typeLabel} #${task.id} · ${task.stageLabel}"
            status.latestApk != null -> "最近安装包 v${status.latestApk.versionName} · ${status.latestApk.versionCode}，点击管理"
            else -> "更新源码、编译安装包，查看执行日志"
        }
    }
    private val notices = Channel<String>(Channel.BUFFERED)
    val messages = notices.receiveAsFlow()
    private var foreground = false
    private val foregroundState = MutableStateFlow(false)
    private var socketJob: Job? = null
    private var pollingJob: Job? = null
    private var upgradeDownloadJob: Job? = null
    private var chatJob: Job? = null
    private var sessionCacheJob: Job? = null
    private var authJob: Job? = null
    private var mutationJob: Job? = null
    private var sheetJob: Job? = null
    private val refreshJobs = mutableMapOf<String, Job>()
    private var generation = 0
    private var sheetGeneration = 0
    private var processingStarted = 0L

    init {
        restoreSessionList()
        viewModelScope.launch {
            // 只观察实际展示的列表；轮询结果未变化时不重复落盘。
            state.map { ui ->
                if (!ui.auth.loggedIn || !ui.sessionCacheReady || !ui.sessions.loaded) null
                else sessionListCache.scope()?.let { scope ->
                    val cachedIds = ui.cachedLocalSessions.map { it.id }.toSet()
                    scope to ui.sessions.data.filter { row ->
                        // 首次同步前的加载提示不落盘，已有摘要则继续保留。
                        !row.isLocal || row.kind == DENGTA_SESSION_KIND || row.id in cachedIds || when (val service = row.managementService) {
                            null -> upgradePreviewReady(ui.upgrade)
                            else -> managementPreviewReady(ui, service)
                        }
                    }
                }
            }.distinctUntilChanged().collect { snapshot ->
                if (snapshot != null) {
                    val (scope, rows) = snapshot
                    mutable.update { it.copy(cachedLocalSessions = rows.filter { row -> row.isLocal }) }
                    withContext(Dispatchers.IO) { sessionListCache.write(scope, rows) }
                }
            }
        }
        // 资源轮询只跟随可见的「我的」或服务器详情；FRP / Docker 摘要采用较慢的周期。
        viewModelScope.launch {
            combine(mutable, foregroundState) { ui, active ->
                when {
                    !active || !ui.auth.loggedIn || ui.browserVisible || ui.chat.session != null -> 0
                    ui.serverStatusVisible -> 2
                    ui.tab == MainTab.Mine -> 1
                    else -> 0
                }
            }.distinctUntilChanged().collectLatest { mode ->
                if (mode == 0) return@collectLatest
                try {
                    var nextServicesAt = 0L
                    while (currentCoroutineContext().isActive) {
                        refreshServer(true)
                        val now = System.currentTimeMillis()
                        if (mode == 1 && now >= nextServicesAt) {
                            refreshFrpSummary()
                            refreshDockerSummary()
                            nextServicesAt = now + 30_000L
                        }
                        delay(if (mode == 2) 2_000L else 5_000L)
                    }
                } finally {
                    listOf("mine-server", "mine-frp", "mine-docker").forEach { refreshJobs.remove(it)?.cancel() }
                    mutable.update { it.copy(mine = it.mine.copy(
                        server = it.mine.server.copy(loading = false), frp = it.mine.frp.copy(loading = false),
                        docker = it.mine.docker.copy(loading = false),
                    )) }
                }
            }
        }
    }

    /** 冷启动先读磁盘；若网络已先返回，则只补充尚未同步的本地服务摘要。 */
    private fun restoreSessionList() {
        sessionCacheJob?.cancel()
        val scope = sessionListCache.scope() ?: return
        val epoch = generation
        sessionCacheJob = viewModelScope.launch {
            val rows = withContext(Dispatchers.IO) { sessionListCache.read(scope) }
            if (epoch != generation) return@launch
            mutable.update {
                it.copy(
                    sessions = if (rows != null && it.sessions.updatedAt == 0L) it.sessions.copy(
                        data = rows.filterNot { row -> row.isLocal }, loading = false, loaded = true,
                    ) else it.sessions,
                    cachedLocalSessions = rows.orEmpty().filter { row -> row.isLocal },
                    sessionCacheReady = true,
                )
            }
        }
    }

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
                repository.api.markReachable()
                mutable.value = initialState()
                restoreSessionList()
                if (foreground) startForegroundWork()
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { mutable.update { it.copy(auth = it.auth.copy(working = false, error = error.userMessage())) } }
        }
    }
    fun resetSetup() { mutable.update { it.copy(auth = it.auth.copy(setup = false, error = null)) } }
    fun logout(message: String? = null) {
        generation++; stopForegroundWork(); sessionCacheJob?.cancel(); chatJob?.cancel(); sheetJob?.cancel(); mutationJob?.cancel()
        refreshJobs.values.forEach(Job::cancel); refreshJobs.clear()
        appUpdates.reset(); buildDownloads.reset(); terminalBuddyBuildDownloads.reset(); session.clear(); repository.clearCaches(); sessionListCache.clear()
        // 重新构造状态会一并关闭隐藏浏览器，重新登录一律回到正常首页。
        mutable.value = initialState(error = message)
        if (message != null) notify(message)
    }
    private fun handleError(error: Throwable) {
        // 「连不上服务器」统一改为标题栏的「重连中」徽标提示（微信式），不再用 snackbar 反复弹；
        // 后台轮询一直在自动重试，连接恢复后徽标会自行消失。
        if (error.userMessage().isServerUnreachable()) return
        if (error is ApiException && error.code == 401) logout("登录已过期，请重新登录") else notify(error.userMessage())
    }
    fun setForeground(active: Boolean) {
        foreground = active
        foregroundState.value = active
        if (active && session.loggedIn) startForegroundWork() else stopForegroundWork()
    }
    private fun startForegroundWork() {
        appUpdates.refreshInstalledVersion()
        checkAppUpdate()
        if (upgradeDownloadJob?.isActive != true) upgradeDownloadJob = viewModelScope.launch {
            while (isActive && session.loggedIn) {
                try { appUpdates.pollDownload() }
                catch (error: CancellationException) { throw error }
                catch (error: Throwable) { handleError(error) }
                delay(if (appUpdates.state.value.download.active) 700 else 2000)
            }
        }
        if (socketJob?.isActive != true) socketJob = viewModelScope.launch {
            ChatRealtime(repository.api).events().catch { handleError(it) }.collect { event -> when (event) {
                RealtimeEvent.Connected -> { mutable.update { it.copy(realtime = true) }; refreshSessions(true); refreshChat() }
                RealtimeEvent.Disconnected -> mutable.update { it.copy(realtime = false) }
                is RealtimeEvent.Frame -> onFrame(event.body)
            } }
        }
        if (pollingJob?.isActive != true) pollingJob = viewModelScope.launch {
            while (isActive && session.loggedIn) {
                refreshCurrent(true); refreshSessions(true); refreshChat(); checkAppUpdate()
                try { buildDownloads.poll(); terminalBuddyBuildDownloads.poll() } catch (error: CancellationException) { throw error } catch (_: Throwable) {}
                if (mutable.value.chat.processing && System.currentTimeMillis() - processingStarted > 180_000) mutable.update { it.copy(chat = it.chat.copy(processing = false)) }
                delay(if (mutable.value.realtime) 15_000 else 8_000)
            }
        }
    }
    private fun stopForegroundWork() {
        socketJob?.cancel(); socketJob = null; pollingJob?.cancel(); pollingJob = null
        upgradeDownloadJob?.cancel(); upgradeDownloadJob = null
        mutable.update { it.copy(realtime = false) }
    }
    fun selectTab(tab: MainTab) { closeChat(); dismissSheet(); mutable.update { it.copy(tab = tab, serverStatusVisible = false) }; refreshCurrent(false) }

    /** 隐藏浏览器：在「我的」页连续点击标题 10 次后由界面层打开，计数与连击节奏放在界面层。 */
    fun openHiddenBrowser() { closeChat(); dismissSheet(); mutable.update { it.copy(browserVisible = true, browserUrl = "", serverStatusVisible = false) } }
    fun closeHiddenBrowser() = mutable.update { it.copy(browserVisible = false, browserUrl = "") }
    fun refreshCurrent(silent: Boolean = false) {
        val current = mutable.value
        if (current.serverStatusVisible) { refreshServer(silent); return }
        val service = current.chat.session?.managementService
        if (service != null) {
            refreshManagement(service, silent)
            return
        }
        when (current.tab) {
            MainTab.Functions, MainTab.System -> {
                refreshSessions(silent)
                if (current.chat.session == null) {
                    refreshFrp(silent)
                    refreshDocker(silent)
                    // 构建会话摘要与 Frp / Docker 一样在前台自动同步。
                    refreshAndroidBuild(silent)
                    refreshAndroidBuild(silent, AndroidBuildProject.TerminalBuddy)
                    // 巡检与剧集摘要同样在会话列表常驻刷新（安卓新增的两条系统会话）。
                    refreshCheckLogs(silent)
                    refreshLibrary(silent)
                    // 「我的订阅」摘要：订阅列表随系统会话行一起更新。
                    refreshSubscriptions(silent)
                }
            }
            MainTab.Ai -> Unit // AI 使用独立服务连接与生命周期。
            MainTab.Downloads -> refreshDownloads(silent)
            // 自动刷新由可见性轮询负责；手动刷新独立重取三个模块。
            MainTab.Mine -> if (!silent) refreshMine()
        }
    }
    private fun refreshManagement(service: ManagementService, silent: Boolean = false) {
        when (service) {
            ManagementService.Frp -> refreshFrp(silent)
            ManagementService.Docker -> {
                refreshDocker(silent)
                if (mutable.value.dockerSection != DockerSection.Containers) refreshResources(silent)
            }
            ManagementService.AppBuild -> refreshAndroidBuild(silent)
            ManagementService.TerminalBuddyBuild -> refreshAndroidBuild(silent, AndroidBuildProject.TerminalBuddy)
            ManagementService.CheckLogs -> refreshCheckLogs(silent)
            ManagementService.Library -> refreshLibrary(silent)
            ManagementService.Subscriptions -> refreshSubscriptions(silent)
        }
    }
    // 刷新通过任务表防止重复请求；已有数据时静默更新，不触发加载态或禁用页面操作。
    private fun refresh(key: String, block: suspend () -> Unit) {
        if (!session.loggedIn || refreshJobs[key]?.isActive == true) return
        refreshJobs[key] = viewModelScope.launch { block() }
    }
    fun openServerStatus() {
        closeChat(); dismissSheet()
        mutable.update { it.copy(serverStatusVisible = true) }
        refreshServer()
    }
    fun closeServerStatus() { mutable.update { it.copy(serverStatusVisible = false) } }
    fun openManagement(service: ManagementService) {
        val target = state.value.sessions.data.firstOrNull { it.managementService == service }
            ?: Session(service.sessionId, service.title, service.kind, 0, null)
        openSession(target)
    }
    fun refreshMine() {
        refreshServer()
        refreshFrpSummary()
        refreshDockerSummary()
    }
    fun refreshServer(silent: Boolean = false) = refresh("mine-server") {
        val epoch = generation
        if (!silent || !mutable.value.mine.server.loaded) mutable.update { it.copy(mine = it.mine.copy(server = it.mine.server.copy(loading = !it.mine.server.loaded))) }
        try {
            val data = repository.serverMetrics()
            if (!data.available) throw ApiException(503, data.message.ifBlank { "资源指标暂不可用" })
            if (epoch == generation) mutable.update { it.copy(mine = it.mine.copy(server = LoadState(
                data, loaded = true, updatedAt = System.currentTimeMillis(),
            ))) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(mine = it.mine.copy(server = it.mine.server.copy(loading = false, loaded = true, error = error.userMessage()))) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun refreshFrpSummary() = refresh("mine-frp") {
        val epoch = generation
        mutable.update { it.copy(mine = it.mine.copy(frp = it.mine.frp.copy(loading = !it.mine.frp.loaded))) }
        try {
            val data = repository.frpSummary()
            if (epoch == generation) mutable.update { it.copy(mine = it.mine.copy(frp = LoadState(
                data, loaded = true, updatedAt = System.currentTimeMillis(),
                error = if (data.configAvailable) null else data.message.ifBlank { "无法读取隧道配置" },
            ))) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(mine = it.mine.copy(frp = it.mine.frp.copy(loading = false, loaded = true, error = error.userMessage()))) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun refreshDockerSummary() = refresh("mine-docker") {
        val epoch = generation
        mutable.update { it.copy(mine = it.mine.copy(docker = it.mine.docker.copy(loading = !it.mine.docker.loaded))) }
        try {
            val data = repository.dockerSummary()
            if (!data.available) throw ApiException(503, data.message.ifBlank { "Docker 无法连接" })
            if (epoch == generation) mutable.update { it.copy(mine = it.mine.copy(docker = LoadState(data, loaded = true, updatedAt = System.currentTimeMillis()))) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(mine = it.mine.copy(docker = it.mine.docker.copy(loading = false, loaded = true, error = error.userMessage()))) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }

    fun toggleSessionPin(id: String) {
        val current = mutable.value
        if (!current.auth.loggedIn || state.value.sessions.data.none { it.id == id }) return
        val pins = if (id in current.pinnedSessionIds) current.pinnedSessionIds - id
            else listOf(id) + current.pinnedSessionIds
        sessionListPreferences.savePinnedSessions(current.auth.server, current.auth.username, pins)
        mutable.update { it.copy(pinnedSessionIds = pins) }
    }

    fun refreshSessions(silent: Boolean = false) = refresh("sessions") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(sessions = it.sessions.copy(loading = !it.sessions.loaded)) }
        try { val data = repository.sessions(); if (epoch == generation) mutable.update { it.copy(sessions = LoadState(data, loaded = true, updatedAt = System.currentTimeMillis())) } }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(sessions = it.sessions.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    /** 巡检记录：列表 + 会话摘要共用同一份缓存（limit 200，与网页端一致） */
    fun refreshCheckLogs(silent: Boolean = false) = refresh("check-logs") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(checkLogs = it.checkLogs.copy(loading = !it.checkLogs.loaded)) }
        try {
            val rows = repository.checkLogs()
            if (epoch == generation) mutable.update { it.copy(checkLogs = LoadState(rows, loaded = true)) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(checkLogs = it.checkLogs.copy(loading = false, error = error.userMessage(), loaded = true)) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun clearCheckLogs() = confirm("清空巡检记录", "将删除全部巡检历史，订阅本身与下载任务不受影响。", "清空") {
        perform("check-logs-clear", "已清空巡检记录", after = { refreshCheckLogs() }) { repository.clearCheckLogs() }
    }
    /** 剧集管理：扫描共享下载目录（后端只接受本次扫描返回的短期 ID 做删除） */
    fun refreshLibrary(silent: Boolean = false) = refresh("library") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(library = it.library.copy(scan = it.library.scan.copy(loading = !it.library.scan.loaded))) }
        try {
            val scan = repository.libraryScan()
            if (epoch == generation) mutable.update { it.copy(library = it.library.copy(scan = LoadState(scan, loaded = true))) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(library = it.library.copy(scan = it.library.scan.copy(loading = false, error = error.userMessage(), loaded = true))) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun deleteLibrary(entries: List<LibraryEntry>) {
        if (entries.isEmpty()) return
        val bytes = entries.sumOf { it.size }
        val preview = entries.take(6).joinToString("、") { "${it.seriesTitle} ${it.episodeLabel}" }
        val extra = if (entries.size > 6) "等 ${entries.size} 个文件" else ""
        confirm("删除 ${entries.size} 个剧集文件", "$preview$extra\n将释放约 ${formatBytes(bytes.toDouble())}，删除后无法恢复。", "确认删除") {
            perform("library-delete", "", close = false, after = { refreshLibrary() }) {
                val result = repository.libraryDelete(entries.map { it.id })
                val removed = result.deletedIds
                // 先在本地剔除已删除项，避免后台刷新期间列表仍然显示旧文件
                if (removed.isNotEmpty()) {
                    mutable.update { state ->
                        val scan = state.library.scan
                        state.copy(library = state.library.copy(scan = scan.copy(
                            data = scan.data.copy(entries = scan.data.entries.filterNot { it.id in removed }))))
                    }
                }
                notify(if (result.failed > 0) "${result.message}，释放 ${formatBytes(result.freedBytes.toDouble())}"
                    else "已删除 ${result.deleted} 个文件，释放 ${formatBytes(result.freedBytes.toDouble())}")
            }
        }
    }

    fun refreshDownloads(silent: Boolean = false) = refresh("downloads") {        val epoch = generation
        if (!silent) mutable.update { it.copy(downloads = it.downloads.copy(loading = !it.downloads.loaded)) }
        try {
            val result = repository.downloads(!silent)
            if (epoch == generation) mutable.update { old -> old.copy(downloads = LoadState((result.items + old.downloads.data.filter { it.source.name in result.failedSources }).sortedByDescending { it.createdAt }, error = result.warnings.joinToString("\n").ifBlank { null }, loaded = true)) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(downloads = it.downloads.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    fun refreshSubscriptions(silent: Boolean = false) = refresh("subscriptions") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(subscriptions = it.subscriptions.copy(loading = !it.subscriptions.loaded)) }
        try {
            val result = repository.subscriptions()
            if (epoch == generation) mutable.update { old -> old.copy(subscriptions = LoadState((result.items + old.subscriptions.data.filter { it.source.name in result.failedSources }).sortedByDescending { it.createdAt }, error = result.warnings.joinToString("\n").ifBlank { null }, loaded = true),
                subscriptionSourcesLoaded = old.subscriptionSourcesLoaded + SubscriptionSource.entries.filter { it.name !in result.failedSources }) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(subscriptions = it.subscriptions.copy(loading = false, error = error.userMessage(), loaded = true)) }; if (error is ApiException && error.code == 401) handleError(error) } }
    }
    fun refreshFrp(silent: Boolean = false) = refresh("frp") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(tunnels = it.tunnels.copy(loading = !it.tunnels.loaded)) }
        try {
            val (tunnels, runtime) = repository.frp()
            val now = System.currentTimeMillis()
            if (epoch == generation) mutable.update { it.copy(
                tunnels = LoadState(tunnels, loaded = true, updatedAt = now), runtime = runtime,
                mine = it.mine.copy(frp = LoadState(runtime, loaded = true, updatedAt = now,
                    error = if (runtime.configAvailable) null else runtime.message.ifBlank { "无法读取隧道配置" })),
            ) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(tunnels = it.tunnels.copy(loading = false, error = error.userMessage(), loaded = true),
                    mine = it.mine.copy(frp = it.mine.frp.copy(loading = false, loaded = true, error = error.userMessage()))) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun refreshDocker(silent: Boolean = false) = refresh("docker") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(containers = it.containers.copy(loading = !it.containers.loaded)) }
        try {
            val (containers, overview) = repository.docker()
            if (!overview.online) throw ApiException(503, overview.message.ifBlank { "Docker 无法连接" })
            val now = System.currentTimeMillis()
            if (epoch == generation) mutable.update { it.copy(
                containers = LoadState(containers, loaded = true, updatedAt = now), overview = overview,
                mine = it.mine.copy(docker = LoadState(DockerSummary(true, overview.total, overview.running), loaded = true, updatedAt = now)),
            ) }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(containers = it.containers.copy(loading = false, error = error.userMessage(), loaded = true),
                    mine = it.mine.copy(docker = it.mine.docker.copy(loading = false, loaded = true, error = error.userMessage()))) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun selectDockerSection(section: DockerSection) { mutable.update { it.copy(dockerSection = section) }; if (section != DockerSection.Containers) refreshResources() }
    fun refreshResources(silent: Boolean = false) {
        val section = mutable.value.dockerSection; if (section == DockerSection.Containers) return
        refresh("resources:$section") {
            val epoch = generation
            if (!silent) mutable.update {
                val state = it.resources[section] ?: LoadState(emptyList())
                it.copy(resources = it.resources + (section to state.copy(loading = !state.loaded)))
            }
            try { val result = repository.dockerResources(section); if (epoch == generation) mutable.update { it.copy(resources = it.resources + (section to LoadState(result, loaded = true))) } }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (epoch == generation) { mutable.update { it.copy(resources = it.resources + (section to (it.resources[section] ?: LoadState(emptyList())).copy(loading = false, error = error.userMessage(), loaded = true))) }; if (error is ApiException && error.code == 401) handleError(error) } }
        }
    }

    /** 构建管理轮询：状态 + 任务历史 + 选中任务的增量日志，一次刷新全部拉齐。 */
    fun refreshAndroidBuild(silent: Boolean = false, project: AndroidBuildProject = AndroidBuildProject.Huluwa) = refresh("android-build:${project.id}") {
        val epoch = generation
        val previous = mutable.value.build(project)
        if (!silent) mutable.update { it.updateBuild(project) { current -> current.copy(tasks = current.tasks.copy(loading = !current.tasks.loaded)) } }
        try {
            val status = AndroidBuildStatus.from(repository.androidStatus(previous.serverId, project))
            var tasks = repository.androidTasks(project = project)
            if (epoch != generation || mutable.value.build(project).serverId != previous.serverId) return@refresh
            val selectedId = status.activeTask?.id
                ?: previous.selectedId?.takeIf { id -> tasks.any { it.id == id } }
                ?: tasks.firstOrNull()?.id
            var logText = if (selectedId == previous.selectedId) previous.logs else ""
            var cursor = if (selectedId == previous.selectedId) previous.cursor else 0
            var complete = if (selectedId == previous.selectedId) previous.logComplete else false
            if (selectedId != null && !complete) {
                val logs = repository.androidLogs(selectedId, cursor, project)
                if (epoch != generation || mutable.value.build(project).serverId != previous.serverId) return@refresh
                logText = (if (logs.reset) "" else logText) + logs.text
                if (logText.length > 200_000) logText = logText.takeLast(200_000)
                cursor = logs.cursor
                complete = !logs.hasMore && logs.task?.active != true
                logs.task?.let { task -> tasks = listOf(task) + tasks.filter { it.id != task.id } }
            }
            buildPreferences.selectServer(session.server, status.serverId, project)
            mutable.update { it.copy(clock = System.currentTimeMillis()).updateBuild(project) { current -> current.copy(
                serverId = status.serverId, status = status, tasks = LoadState(tasks, loaded = true), connectionError = false,
                selectedId = selectedId, logs = logText, cursor = cursor, logComplete = complete,
            ) } }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation && mutable.value.build(project).serverId == previous.serverId) {
                mutable.update { it.updateBuild(project) { current -> current.copy(
                    tasks = current.tasks.copy(loading = false, error = error.userMessage(), loaded = true), connectionError = true) } }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun toggleAndroidFollow(project: AndroidBuildProject = AndroidBuildProject.Huluwa) =
        mutable.update { it.updateBuild(project) { current -> current.copy(follow = !current.follow) } }
    fun selectAndroidServer(id: String, project: AndroidBuildProject = AndroidBuildProject.Huluwa) {
        refreshJobs.remove("android-build:${project.id}")?.cancel()
        buildPreferences.selectServer(session.server, id, project)
        mutable.update { it.updateBuild(project) { current -> current.copy(serverId = id,
            status = current.status.copy(workerOnline = false, source = AndroidSource()),
            selectedId = null, logs = "", cursor = 0, logComplete = false) } }
        refreshAndroidBuild(project = project)
    }
    fun submitAndroidBuild(type: String, project: AndroidBuildProject = AndroidBuildProject.Huluwa) {
        if (mutable.value.build(project).submitting != null) return
        val epoch = generation
        val serverId = mutable.value.build(project).serverId
        mutable.update { it.updateBuild(project) { current -> current.copy(submitting = type) } }
        viewModelScope.launch {
            try {
                val task = if (type == "BUILD") repository.androidBuild(serverId, project) else repository.androidUpdate(serverId, project)
                if (epoch != generation) return@launch
                refreshJobs.remove("android-build:${project.id}")?.cancel()
                mutable.update { it.copy(clock = System.currentTimeMillis()).updateBuild(project) { current -> current.copy(
                    submitting = null, status = current.status.copy(activeTask = task),
                    selectedId = task.id, logs = "", cursor = 0, logComplete = false, follow = true,
                    tasks = LoadState(listOf(task) + current.tasks.data.filter { item -> item.id != task.id }, loaded = true),
                ) } }
                notify(if (type == "BUILD") "已提交 ${project.title} APK 编译任务" else "已提交 ${project.title}代码更新任务")
                refreshAndroidBuild(project = project)
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) {
                if (epoch == generation) {
                    mutable.update { it.updateBuild(project) { current -> current.copy(submitting = null) } }
                    handleError(error)
                }
            }
        }
    }
    fun cancelAndroidTask(task: AndroidBuildTask, project: AndroidBuildProject = AndroidBuildProject.Huluwa) = confirm("取消构建任务",
        "确定取消「${project.title} · ${task.typeLabel} #${task.id}」？构建服务会停止当前进程，未完成的产物会被清理。", "取消任务") {
        perform("android-cancel:${project.id}", "已发送取消请求", after = { refreshAndroidBuild(project = project) }) { repository.androidCancel(task.id, project) }
    }
    fun downloadAndroidArtifact(task: AndroidBuildTask, logFile: Boolean = false, project: AndroidBuildProject = AndroidBuildProject.Huluwa) {
        viewModelScope.launch {
            try { buildDownloader(project).download(task, logFile) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { handleError(error) }
        }
    }
    fun cancelAndroidDownload(project: AndroidBuildProject = AndroidBuildProject.Huluwa) { viewModelScope.launch { buildDownloader(project).cancel() } }
    fun pollAndroidDownload(project: AndroidBuildProject = AndroidBuildProject.Huluwa) { viewModelScope.launch { buildDownloader(project).poll() } }
    fun androidBuildInstallerIntent(project: AndroidBuildProject = AndroidBuildProject.Huluwa) = buildDownloader(project).installIntent()
    fun canInstallAndroidBuild(project: AndroidBuildProject = AndroidBuildProject.Huluwa) = buildDownloader(project).canInstallPackages()
    fun androidBuildInstallError(message: String, project: AndroidBuildProject = AndroidBuildProject.Huluwa) = buildDownloader(project).installationError(message)

    private var chatGeneration = 0
    private var localId = -1L
    fun openSession(session: Session) {
        if (session.isLocal) {
            closeChat()
            dismissSheet()
            mutable.update { it.copy(chat = ChatState(session = session), serverStatusVisible = false) }
            val service = session.managementService
            if (service != null) refreshManagement(service) else if (session.kind == "upgrade") checkAppUpdate()
            return
        }
        if (session.kind == "movie" || session.kind == "dytt") refreshSubscriptions(true)
        chatGeneration++; chatJob?.cancel()
        val current = chatGeneration
        // 先同步切到会话页（保证转场立即开始）；进程内已有这一页时直接同步渲染，第一帧就是完整内容。
        val warm = repository.warmMessages(session.id)
        mutable.update { it.copy(serverStatusVisible = false, chat = ChatState(session = session, messages = warm.orEmpty(), loading = warm == null,
            hasEarlier = warm != null && warm.size >= CHAT_PAGE_SIZE)) }
        chatJob = viewModelScope.launch {
            // 没有进程内缓存时再读磁盘，命中即关闭加载态；随后请求服务器在后台无感刷新。
            if (warm == null) {
                val cached = repository.cachedMessages(session.id)
                if (current != chatGeneration) return@launch
                if (cached.isNotEmpty()) mutable.update { it.copy(chat = it.chat.copy(messages = cached, loading = false,
                    hasEarlier = cached.size >= CHAT_PAGE_SIZE)) }
            }
            try {
                val rows = repository.messages(session.id)
                if (current != chatGeneration) return@launch
                // 服务器返回空页面说明消息已被清空，本地缓存一并作废；否则写入最新一页。
                if (rows.isEmpty()) repository.forgetMessages(session.id) else repository.cacheMessages(session.id, rows)
                mutable.update { old ->
                    val sent = old.chat.messages.filter { it.delivery == Delivery.Sent }
                    // 与既有刷新逻辑一致：最新一页和本地完全不重叠时（离线太久 / 记录被清空）以服务器为准重建。
                    val gap = rows.size >= 50 && sent.isNotEmpty() && rows.none { row -> sent.any { it.id == row.id } }
                    // 服务器返回空页面说明消息已被清空，此时不能用缓存兜底，否则会一直显示旧消息。
                    val base = if (gap || rows.isEmpty()) emptyList() else old.chat.messages
                    old.copy(chat = old.chat.copy(messages = merge(base, rows), loading = false, error = null,
                        hasEarlier = gap || rows.size >= 50))
                }
                sourceResult { repository.markRead(session.id) }.exceptionOrNull()?.let { if (it is ApiException && it.code == 401) handleError(it) }
                if (current == chatGeneration) refreshSessions(true)
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == chatGeneration) { mutable.update { it.copy(chat = it.chat.copy(loading = false, error = error.userMessage())) }; if (error is ApiException && error.code == 401) handleError(error) } }
        }
    }
    fun closeChat() { chatGeneration++; chatJob?.cancel(); mutable.update { it.copy(chat = ChatState()) } }

    fun checkAppUpdate() = refresh("app-upgrade") {
        try { appUpdates.checkLatest() }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { handleError(error) }
    }
    fun downloadAppUpdate() = refresh("app-upgrade-download") {
        try { appUpdates.downloadLatest() }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) { appUpdates.downloadError(error.userMessage()) }
    }
    fun cancelAppUpdate() = refresh("app-upgrade-cancel") { appUpdates.cancelDownload() }
    fun consumeUpdateInstallRequest() = appUpdates.consumeInstallRequest()
    fun canInstallAppUpdate() = appUpdates.canInstallPackages()
    fun appUpdateInstallationError(message: String) = appUpdates.installationError(message)
    suspend fun appUpdateInstallerIntent() = appUpdates.installerIntent()

    private fun merge(old: List<ChatMessage>, incoming: List<ChatMessage>) = (old + incoming).associateBy { it.id }.values.sortedWith(compareBy<ChatMessage> { if (it.id < 0) Long.MAX_VALUE else it.id }.thenBy { it.createdAt })
    fun refreshChat() {
        val chat = mutable.value.chat; val id = chat.session?.id ?: return
        // 正在进入会话（读缓存 / 首屏请求）时不重复请求：缓存命中后 loading 已为 false，
        // 但 openSession 的请求可能还没回来，这里靠 chatJob 拦住，避免两个请求并发。
        if (chat.loading || chatJob?.isActive == true || chat.session?.isLocal == true) return
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
                    // 轮询合并后的结果同步进缓存，保证下次进入时看到的与当前一致；
                    // 服务器返回空页说明会话已被清空，直接作废缓存，避免下次进入回显旧消息。
                    if (rows.isEmpty()) repository.forgetMessages(id) else {
                        val snapshot = mutable.value.chat.messages
                        viewModelScope.launch { repository.cacheMessages(id, snapshot) }
                    }
                    sourceResult { repository.markRead(id) }.exceptionOrNull()?.let { if (it is ApiException && it.code == 401) handleError(it) }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == chatGeneration) { mutable.update { it.copy(chat = it.chat.copy(error = error.userMessage())) }; if (error is ApiException && error.code == 401) handleError(error) } }
        }
    }
    fun loadEarlier() {
        val chat = mutable.value.chat; val id = chat.session?.id ?: return
        if (chat.session?.isLocal == true || chat.loadingEarlier || !chat.hasEarlier) return
        val before = chat.messages.filter { it.id > 0 }.minOfOrNull { it.id } ?: return
        val current = chatGeneration
        mutable.update { it.copy(chat = it.chat.copy(loadingEarlier = true)) }
        viewModelScope.launch {
            try {
                val rows = repository.messages(id, before)
                if (current == chatGeneration) {
                    // 只缓存最新一页，翻出来的更早消息不必落盘；下次进入仍会自动刷新补齐。
                    mutable.update { it.copy(chat = it.chat.copy(messages = merge(rows, it.chat.messages), loadingEarlier = false, hasEarlier = rows.size >= 50)) }
                }
            }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { if (current == chatGeneration) { mutable.update { it.copy(chat = it.chat.copy(loadingEarlier = false)) }; handleError(error) } }
        }
    }
    fun send(content: String, retry: ChatMessage? = null) {
        val chat = mutable.value.chat; val selected = chat.session ?: return
        if (selected.kind == "system" || selected.isLocal || content.isBlank()) return
        val current = chatGeneration; val nonce = retry?.requestId ?: UUID.randomUUID().toString()
        val outgoing = retry?.copy(delivery = Delivery.Sending) ?: ChatMessage(localId--, selected.id, "user", "text", content.trim(), content.trim(), JsonObject(emptyMap()), LocalDateTime.now().toString().replace('T', ' '), Delivery.Sending, nonce)
        processingStarted = System.currentTimeMillis()
        mutable.update { it.copy(chat = it.chat.copy(messages = merge(it.chat.messages.filterNot { item -> item.requestId == nonce }, listOf(outgoing)), processing = true)) }
        viewModelScope.launch {
            try {
                val accepted = repository.send(selected.id, content.trim(), nonce)
                if (current == chatGeneration) {
                    mutable.update { it.copy(chat = it.chat.copy(messages = merge(it.chat.messages.filterNot { item -> item.requestId == nonce }, accepted))) }
                    // 发送成功后立即写缓存，避免刚发完就退出、下次进入看不到这条消息。
                    val snapshot = mutable.value.chat.messages
                    viewModelScope.launch { repository.cacheMessages(selected.id, snapshot) }
                }
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
            "chat.sessions" -> mutable.update { it.copy(sessions = LoadState(frame.rows("sessions").map { row -> Session.from(row.asObject()) }, loaded = true, updatedAt = System.currentTimeMillis())) }
            "chat.messages" -> {
                val rows = frame.rows("messages").map { ChatMessage.from(it.asObject()) }
                if (mutable.value.chat.session?.id == id) {
                    mutable.update { it.copy(chat = it.chat.copy(messages = merge(it.chat.messages, rows), processing = false)) }
                    // 实时推送到达时同步更新缓存，下次进入看到的就是最新的（写盘放后台，不阻塞推送处理）。
                    val snapshot = mutable.value.chat.messages
                    viewModelScope.launch { repository.cacheMessages(id, snapshot) }
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
        if (selected.kind == "system" || selected.isLocal) return
        confirm("清空会话", "确定清空「${selected.title}」的全部消息吗？", "清空") {
            perform("clear-chat", "会话已清空", after = { refreshSessions() }) {
                repository.clearChat(selected.id); chatGeneration++
                // 先删本地缓存再清空界面，避免下次进入时缓存里的旧消息短暂回显。
                repository.forgetMessages(selected.id)
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
    fun deleteDownload(task: DownloadTask) {
        val name = listOf(task.title, task.episode).filter { it.isNotBlank() }.joinToString(" ")
        val detail = if (task.source == DownloadSource.Hls) {
            "删除「$name」的下载记录；若任务仍在排队或下载，将一并取消，已下载文件保留。"
        } else {
            "删除「$name」的下载记录，下载器中的任务继续运行，已下载文件保留。"
        }
        confirm("删除下载记录", detail, "删除记录") {
            perform("delete-download:${task.key}", "下载记录已删除", after = { reloadDownloadsAfterChange() }) {
                repository.deleteDownload(task)
                mutable.update { it.copy(downloads = it.downloads.copy(data = it.downloads.data.filterNot { row -> row.key == task.key })) }
            }
        }
    }
    fun clearAllDownloads() = confirm("清空全部下载记录",
        "清空所有来源的下载记录，不受搜索和筛选条件影响。进行中的离线任务和已下载文件保留；磁力及迅雷记录清空后，下载器中的任务继续运行。", "清空记录") {
        perform("clear-all-downloads", "记录已清空，进行中的离线任务已保留", after = { reloadDownloadsAfterChange() }) {
            repository.clearAllDownloads()
        }
    }
    private fun reloadDownloadsAfterChange() {
        // 丢弃操作前开始的同步，防止旧快照把刚删除的记录带回列表。
        refreshJobs.remove("downloads")?.cancel()
        refreshDownloads()
    }
    fun clearDownloads(source: DownloadSource) = confirm("清空下载记录", if (source == DownloadSource.Hls) "清空已结束的离线下载记录，保留进行中的任务和已下载文件。" else "清空${source.title}的记录，下载器中的任务会继续运行。", "清空记录") {
        perform("clear-downloads", "记录已清空", after = { reloadDownloadsAfterChange() }) { repository.clearDownloads(source) }
    }
    fun downloadLog(task: DownloadTask) = loadText("下载日志") { repository.downloadLog(task.id) }
    fun checkSubscription(sub: Subscription) = perform("check:${sub.key}", "", close = false, after = { refreshSubscriptions(); refreshDownloads(true) }) { notify(repository.check(sub)) }
    fun deleteSubscription(sub: Subscription) = confirm("删除订阅", if (sub.source == SubscriptionSource.Hls) "删除「${sub.title}」及其追更任务记录，已归档文件保留。" else "删除「${sub.title}」及其磁力基线、下载记录，已下载文件保留。", "删除订阅") {
        perform("delete:${sub.key}", "订阅已删除", after = { refreshSubscriptions(); refreshDownloads(true) }) { repository.deleteSubscription(sub) }
    }
    fun setFilmFollowing(card: FilmCard, following: Boolean) {
        if (card.isMovieHls) return
        val epoch = generation
        perform("film-follow:${card.key()}", if (following) "已开启追更" else "已取消追更", close = false,
            after = { refreshSubscriptions(); if (!following) refreshDownloads(true) }) {
            refreshJobs.remove("subscriptions")?.cancel()
            val rows = repository.setFilmFollowing(card, following)
            if (epoch == generation) {
                // 取消操作前开始的刷新，避免旧快照把卡片状态改回去。
                refreshJobs.remove("subscriptions")?.cancel()
                mutable.update { old -> old.copy(
                    subscriptions = old.subscriptions.copy(data = (rows + old.subscriptions.data.filter { it.source != card.subscriptionSource })
                        .sortedByDescending { it.createdAt }),
                    subscriptionSourcesLoaded = old.subscriptionSourcesLoaded + card.subscriptionSource,
                ) }
            }
        }
    }
    fun openFilmWebsite(card: FilmCard) {
        if (mutable.value.busy != null || !session.loggedIn) return
        val knownUrl = try { repository.knownFilmWebsite(card) }
        catch (error: IllegalArgumentException) {
            mutable.update { it.copy(actionError = error.userMessage()) }
            return
        }
        if (knownUrl != null) {
            dismissSheet()
            mutable.update { it.copy(browserVisible = true, browserUrl = knownUrl, serverStatusVisible = false, actionError = null) }
            return
        }
        val epoch = generation
        val sessionId = mutable.value.chat.session?.id
        perform("film-website:${card.key()}", "", close = false) {
            val url = repository.filmWebsite(card)
            if (epoch == generation && mutable.value.chat.session?.id == sessionId) {
                dismissSheet()
                mutable.update { it.copy(browserVisible = true, browserUrl = url, serverStatusVisible = false) }
            }
        }
    }
    // ------------------------------------------------------------------ 懂片帝
    // 独立的一套入口与状态：不经过 filmResources / Films，避免与电影港、电影天堂互相牵连。

    /** 打开懂片帝资源弹框并拉取作品详情（线路清单） */
    fun openDpdSources(card: DpdCard) {
        AppLog.d("懂片帝", "打开资源列表：${card.title} (${card.id.take(16)}…)")
        showSheet(AppSheet.Dpd(card, LoadState(JsonObject(emptyMap()))))
        reloadDpdSources(card)
    }

    fun dismissDpdSources() {
        if (mutable.value.sheet is AppSheet.Dpd) dismissSheet()
    }

    /** 重新抓取作品详情；已解析的各线路一并清空，避免集数变化后仍显示旧地址 */
    fun reloadDpdSources(card: DpdCard) {
        val sheet = mutable.value.sheet as? AppSheet.Dpd ?: return
        if (sheet.card.key() != card.key()) return
        val current = sheetGeneration
        mutable.update { old ->
            val active = old.sheet as? AppSheet.Dpd ?: return@update old
            old.copy(sheet = active.copy(detail = active.detail.copy(loading = true, error = null), lines = emptyMap(), busyLine = ""))
        }
        viewModelScope.launch {
            try {
                val result = repository.dpdDetail(card.id)
                AppLog.d("懂片帝", "详情返回：${result.rows("dpdLines").size} 条线路")
                if (current != sheetGeneration) return@launch
                mutable.update { old ->
                    val active = old.sheet as? AppSheet.Dpd ?: return@update old
                    old.copy(sheet = active.copy(detail = LoadState(result, loaded = true)))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.e("懂片帝", "详情加载失败", error)
                if (current != sheetGeneration) return@launch
                mutable.update { old ->
                    val active = old.sheet as? AppSheet.Dpd ?: return@update old
                    old.copy(sheet = active.copy(detail = LoadState(JsonObject(emptyMap()), loaded = true, error = error.userMessage())))
                }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }

    /**
     * 解析某条线路的全部剧集。
     * 每条线路的每集都要单独请求站点，很慢，所以只在用户点选这条线路时才拉；结果缓存到弹框状态里。
     */
    fun loadDpdLine(card: DpdCard, line: String) {
        val sheet = mutable.value.sheet as? AppSheet.Dpd ?: return
        if (sheet.card.key() != card.key() || line.isBlank()) return
        val existing = sheet.lines[line]
        if (existing != null && (existing.loading || (existing.loaded && existing.error == null))) return
        val current = sheetGeneration
        mutable.update { old ->
            val active = old.sheet as? AppSheet.Dpd ?: return@update old
            old.copy(sheet = active.copy(
                busyLine = line,
                lines = active.lines + (line to LoadState(JsonObject(emptyMap()), loading = true))))
        }
        AppLog.d("懂片帝", "开始解析线路：$line")
        val startedAt = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                val result = repository.dpdLine(card.id, line)
                val items = result.rows("episodes")
                AppLog.d("懂片帝", "线路「$line」返回 ${items.size} 集，其中 ${items.count { e -> e.asObject().text("url").isNotBlank() }} 集有地址（${System.currentTimeMillis() - startedAt}ms）")
                if (current != sheetGeneration) return@launch
                mutable.update { old ->
                    val active = old.sheet as? AppSheet.Dpd ?: return@update old
                    old.copy(sheet = active.copy(
                        busyLine = if (active.busyLine == line) "" else active.busyLine,
                        lines = active.lines + (line to LoadState(result, loaded = true))))
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                AppLog.e("懂片帝", "线路「$line」解析失败（${System.currentTimeMillis() - startedAt}ms）", error)
                if (current != sheetGeneration) return@launch
                mutable.update { old ->
                    val active = old.sheet as? AppSheet.Dpd ?: return@update old
                    old.copy(sheet = active.copy(
                        busyLine = if (active.busyLine == line) "" else active.busyLine,
                        lines = active.lines + (line to LoadState(
                            JsonObject(emptyMap()), loaded = true, error = error.userMessage()))))
                }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }

    /** 懂片帝作品页（站点 /player/{作品ID}）用内置浏览器打开 */
    fun openDpdWebsite(card: DpdCard) {
        val url = card.pageUrl.takeIf { it.startsWith("http://") || it.startsWith("https://") } ?: return
        dismissSheet()
        mutable.update { it.copy(browserVisible = true, browserUrl = url, serverStatusVisible = false, actionError = null) }
    }

    /**
     * 懂片帝单集按需解析：官方线路受站点限速，只有用户点开的那一集才去兑换。
     * 成功后把这一集的地址回填到弹层里，界面立即变成可播放状态。
     */
    fun resolveDpdEpisode(card: DpdCard, line: String, title: String) {
        val sheet = mutable.value.sheet as? AppSheet.Dpd ?: return
        if (sheet.card.key() != card.key()) return
        val current = sheetGeneration
        AppLog.d("懂片帝", "解析单集：$line / $title")
        val startedAt = System.currentTimeMillis()
        perform("dpd-episode-resolve", "", close = false) {
            val result = repository.dpdEpisode(card.id, line, title)
            AppLog.d("懂片帝", "单集「$title」解析完成（${System.currentTimeMillis() - startedAt}ms）")
            if (current != sheetGeneration) return@perform
            val episodes = DpdEpisode.rows(result)
            if (episodes.isEmpty()) return@perform
            val resolved = episodes.first()
            mutable.update { old ->
                val active = old.sheet as? AppSheet.Dpd ?: return@update old
                val state = active.lines[line] ?: return@update old
                // 只替换这一集的地址，其它集保持原样
                val updated = state.data.rows("episodes").map { element ->
                    val item = element.asObject()
                    if (item.text("title") == resolved.title) {
                        jsonBody {
                            item.forEach { (key, value) -> put(key, value) }
                            put("url", resolved.url)
                        }
                    } else element
                }
                val data = jsonBody {
                    state.data.forEach { (key, value) -> put(key, value) }
                    put("episodes", JsonArray(updated))
                }
                old.copy(sheet = active.copy(lines = active.lines +
                    (line to state.copy(data = data))))
            }
        }
    }

    /** 懂片帝先按集入队，下载任务执行时才获取该线路的真实链接。 */
    fun enqueueDpdEpisode(card: DpdCard, line: String, episode: DpdEpisode, season: Int, episodeNo: Int) =
        perform("dpd-episode", "已加入离线下载", close = false, after = { refreshDownloads() }) {
            repository.enqueueDpdEpisode(card, line, episode, season, episodeNo)
        }

    fun filmResources(card: FilmCard, defaultSeason: Int? = null) {
        showSheet(AppSheet.Films(card, LoadState(JsonObject(emptyMap())), defaultSeason))
        reloadFilmResources(card)
    }
    /** 两个来源并行抓取，各自保留数据、加载和错误状态。 */
    fun reloadFilmResources(card: FilmCard) {
        val sheet = mutable.value.sheet as? AppSheet.Films ?: return
        if (sheet.card.key() != card.key() || sheetJob?.isActive == true) return
        val current = sheetGeneration
        mutable.update { it.copy(sheet = sheet.copy(
            data = sheet.data.copy(loading = true, error = null),
            onlineData = if (card.isMovie) sheet.onlineData.copy(loading = true, error = null) else sheet.onlineData,
        )) }
        sheetJob = viewModelScope.launch {
            val sources = if (card.isMovie) listOf(false, true) else listOf(false)
            sources.forEach { online -> launch {
                try {
                    val result = repository.filmResources(card, online)
                    if (current == sheetGeneration) mutable.update { old ->
                        val active = old.sheet as? AppSheet.Films ?: return@update old
                        val loaded = LoadState(result, loaded = true)
                        old.copy(sheet = if (online) active.copy(onlineData = loaded) else active.copy(data = loaded))
                    }
                } catch (error: CancellationException) { throw error }
                catch (error: Throwable) {
                    if (current == sheetGeneration) {
                        mutable.update { old ->
                            val active = old.sheet as? AppSheet.Films ?: return@update old
                            val failed = (if (online) active.onlineData else active.data).copy(
                                loading = false, error = error.userMessage(), loaded = true)
                            old.copy(sheet = if (online) active.copy(onlineData = failed) else active.copy(data = failed))
                        }
                        if (error is ApiException && error.code == 401) handleError(error)
                    }
                }
            } }
        }
    }
    fun resolveMovieHls(item: PlayItem, onResolved: (String) -> Unit) {
        val current = sheetGeneration
        perform("movie-hls-resolve", "", close = false) {
            val url = repository.movieHlsUrl(item)
            if (current == sheetGeneration) onResolved(url)
        }
    }
    /** 浏览器解析任务由页面持有，离开当前网页时可取消，不依赖资源弹框。 */
    fun resolveBrowserVideo(url: String, onResolved: (String) -> Unit, onFailure: (String) -> Unit): Job = viewModelScope.launch {
        try { onResolved(repository.movieHlsUrl(PlayItem("", url))) }
        catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            onFailure(error.userMessage())
            if (error is ApiException && error.code == 401) handleError(error)
        }
    }
    fun enqueueEpisode(card: FilmCard, source: String, item: PlayItem, season: Int, episode: Int) = perform("episode", "已加入离线下载", close = mutable.value.sheet !is AppSheet.Films, after = { refreshDownloads() }) { repository.enqueueEpisode(card, source, item, season, episode) }
    fun enqueueMagnet(card: FilmCard, item: JsonObject) = perform("magnet", "下载任务已提交", after = { markMagnetDownloaded(card, item); refreshDownloads() }) { repository.enqueueMagnet(card, item) }
    /** 提交成功后把弹框里的这一行标成「已下载」，与网页端弹框一致，不必等重新抓取 */
    private fun markMagnetDownloaded(card: FilmCard, item: JsonObject) {
        val sheet = mutable.value.sheet as? AppSheet.Films ?: return
        if (sheet.card.key() != card.key() && sheet.data.data.text("url") != card.url) return
        val marked = sheet.data.data.markDownloaded(item.text("hash").ifBlank { item.text("magnet") })
        if (marked !== sheet.data.data) mutable.update { it.copy(sheet = sheet.copy(data = sheet.data.copy(data = marked))) }
    }
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
    fun tunnelDetail(tunnel: Tunnel) = loadText(tunnel.name) {
        buildString {
            appendLine("代理类型：${tunnel.draft.proxyType.uppercase()}")
            appendLine("隧道状态：${if (!tunnel.enabled) "已停用" else tunnel.statusText.ifBlank { "状态待查询" }}")
            appendLine("本地服务：${tunnel.localAddress.ifBlank { "—" }}")
            appendLine("远程地址 / 端口：${tunnel.remoteAddress.ifBlank { "—" }}")
            appendLine("本地探测：${tunnel.probeText.ifBlank { "未探测" }}")
            if (tunnel.error.isNotBlank()) appendLine("错误信息：${tunnel.error}")
            if (tunnel.draft.remark.isNotBlank()) appendLine("备注：${tunnel.draft.remark}")
        }
    }
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
