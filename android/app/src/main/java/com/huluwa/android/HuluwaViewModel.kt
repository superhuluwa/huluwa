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
    val auth: AuthState = AuthState(), val tab: MainTab = MainTab.Functions,
    val sessions: LoadState<List<Session>> = LoadState(emptyList()), val chat: ChatState = ChatState(),
    val upgrade: AppUpgradeState = AppUpgradeState(),
    val downloads: LoadState<List<DownloadTask>> = LoadState(emptyList()), val subscriptions: LoadState<List<Subscription>> = LoadState(emptyList()),
    val tunnels: LoadState<List<Tunnel>> = LoadState(emptyList()), val runtime: FrpRuntime = FrpRuntime(),
    val containers: LoadState<List<DockerContainer>> = LoadState(emptyList()), val overview: DockerOverview = DockerOverview(),
    val dockerSection: DockerSection = DockerSection.Containers, val resources: Map<DockerSection, LoadState<List<DockerResource>>> = emptyMap(),
    val realtime: Boolean = false, val sheet: AppSheet? = null, val busy: String? = null, val actionError: String? = null,
    val androidBuild: AndroidBuildState = AndroidBuildState(), val clock: Long = System.currentTimeMillis(),
    val checkLogs: LoadState<List<CheckLog>> = LoadState(emptyList()),
    val library: LibraryState = LibraryState(),
    val browserVisible: Boolean = false,
)

/** 剧集管理：扫描结果 + 当前展开的剧集分组（UI 层可自行维护更细的状态） */
data class LibraryState(
    val scan: LoadState<LibraryScan> = LoadState(LibraryScan()),
    val deleting: Boolean = false,
)

class HuluwaViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as HuluwaApplication).repository
    private val session = repository.api.store
    private val appUpdates = (application as HuluwaApplication).appUpdates
    private val buildDownloads = (application as HuluwaApplication).buildDownloads
    private val mutable = MutableStateFlow(AppState(auth = AuthState(session.server, session.username, session.loggedIn)))
    private fun withAppSessions(value: AppState, upgrade: AppUpgradeState): AppState {
        val upgradeTime = upgrade.latest?.publishedAt.orEmpty().ifBlank {
            if (upgrade.checkedAt > 0) LocalDateTime.ofInstant(Instant.ofEpochMilli(upgrade.checkedAt), ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) else ""
        }
        val upgradeSession = Session(UPGRADE_SESSION_ID, "在线升级", "upgrade", if (upgrade.hasUpdate) 1 else 0,
            null, upgrade.sessionPreview, upgradeTime)
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
                        val enabled = value.subscriptions.data.count { it.enabled }
                        val suffix = if (value.subscriptions.error != null) " · 部分来源加载失败" else ""
                        "${value.subscriptions.data.size} 个订阅 · $enabled 个追更中$suffix"
                    }
                }
            }
            Session(service.sessionId, service.title, service.kind, 0, null, preview)
        }
        val localIds = managementSessions.map { it.id }.toSet() + UPGRADE_SESSION_ID
        val remoteSessions = value.sessions.data.filterNot { it.isLocal || it.id in localIds }
        // 管理会话无消息时间，按最旧处理；保留其他会话按更新时间排序的规则。
        val sortedSessions = (remoteSessions + managementSessions + upgradeSession).sortedWith(
            compareByDescending<Session> { it.updatedAt.ifBlank { "0000-00-00 00:00:00" } }.thenBy { it.id })
        return value.copy(upgrade = upgrade, sessions = value.sessions.copy(data = sortedSessions))
    }
    val state = combine(mutable, appUpdates.state, buildDownloads.state) { value, upgrade, download ->
        withAppSessions(value.copy(androidBuild = value.androidBuild.copy(download = download)), upgrade)
    }.stateIn(viewModelScope, SharingStarted.Eagerly,
        withAppSessions(mutable.value.copy(androidBuild = mutable.value.androidBuild.copy(download = buildDownloads.state.value)), appUpdates.state.value))

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
    private var socketJob: Job? = null
    private var pollingJob: Job? = null
    private var upgradeDownloadJob: Job? = null
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
        appUpdates.reset(); buildDownloads.reset(); session.clear(); repository.clearCaches()
        // 重新构造状态会一并关闭隐藏浏览器，重新登录一律回到正常首页。
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
                try { buildDownloads.poll() } catch (error: CancellationException) { throw error } catch (_: Throwable) {}
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
    fun selectTab(tab: MainTab) { closeChat(); dismissSheet(); mutable.update { it.copy(tab = tab) }; refreshCurrent(false) }

    /** 隐藏浏览器：在「我的」页连续点击标题 10 次后由界面层打开，计数与连击节奏放在界面层。 */
    fun openHiddenBrowser() { closeChat(); dismissSheet(); mutable.update { it.copy(browserVisible = true) } }
    fun closeHiddenBrowser() = mutable.update { it.copy(browserVisible = false) }
    fun refreshCurrent(silent: Boolean = false) {
        val current = mutable.value
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
                    // 巡检与剧集摘要同样在会话列表常驻刷新（安卓新增的两条系统会话）。
                    refreshCheckLogs(silent)
                    refreshLibrary(silent)
                    // 「我的订阅」摘要：订阅列表随系统会话行一起更新。
                    refreshSubscriptions(silent)
                }
            }
            MainTab.Downloads -> refreshDownloads(silent)
            // 「我的」页只读取已登录信息，按会话未读数刷新即可。
            MainTab.Mine -> refreshSessions(silent)
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
            ManagementService.CheckLogs -> refreshCheckLogs(silent)
            ManagementService.Library -> refreshLibrary(silent)
            ManagementService.Subscriptions -> refreshSubscriptions(silent)
        }
    }
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
    /** 巡检记录：列表 + 会话摘要共用同一份缓存（limit 200，与网页端一致） */
    fun refreshCheckLogs(silent: Boolean = false) = refresh("check-logs") {
        val epoch = generation
        if (!silent) mutable.update { it.copy(checkLogs = it.checkLogs.copy(loading = true, error = null)) }
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
        if (!silent) mutable.update { it.copy(library = it.library.copy(scan = it.library.scan.copy(loading = true, error = null))) }
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

    /** 构建管理轮询：状态 + 任务历史 + 选中任务的增量日志，一次刷新全部拉齐。 */
    fun refreshAndroidBuild(silent: Boolean = false) = refresh("android-build") {
        val epoch = generation
        val previous = mutable.value.androidBuild
        if (!silent) mutable.update { it.copy(androidBuild = it.androidBuild.copy(tasks = it.androidBuild.tasks.copy(loading = true))) }
        try {
            val status = AndroidBuildStatus.from(repository.androidStatus())
            var tasks = repository.androidTasks()
            if (epoch != generation) return@refresh
            var selectedId = previous.selectedId?.takeIf { id -> tasks.any { it.id == id } }
                ?: status.activeTask?.id ?: tasks.firstOrNull()?.id
            var logText = if (selectedId == previous.selectedId) previous.logs else ""
            var cursor = if (selectedId == previous.selectedId) previous.cursor else 0
            var complete = if (selectedId == previous.selectedId) previous.logComplete else false
            // 日志按字节游标增量拉取；任务结束后不再请求，避免无谓轮询。
            if (selectedId != null && !complete) {
                val logs = repository.androidLogs(selectedId, cursor)
                if (epoch != generation) return@refresh
                logText = (if (logs.reset) "" else logText) + logs.text
                if (logText.length > 200_000) logText = logText.takeLast(200_000)
                cursor = logs.cursor
                complete = !logs.hasMore && logs.task?.active != true
                // 日志接口会返回最新的任务行，先合并进列表，再让选中态指向它。
                logs.task?.let { task -> tasks = listOf(task) + tasks.filter { it.id != task.id } }
            }
            mutable.update {
                it.copy(clock = System.currentTimeMillis(), androidBuild = it.androidBuild.copy(
                    status = status, tasks = LoadState(tasks, loaded = true), connectionError = false,
                    selectedId = selectedId, logs = logText, cursor = cursor, logComplete = complete,
                ))
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Throwable) {
            if (epoch == generation) {
                mutable.update { it.copy(androidBuild = it.androidBuild.copy(
                    tasks = it.androidBuild.tasks.copy(loading = false, error = error.userMessage(), loaded = true),
                    connectionError = true)) }
                if (error is ApiException && error.code == 401) handleError(error)
            }
        }
    }
    fun selectAndroidTask(task: AndroidBuildTask) {
        if (mutable.value.androidBuild.selectedId == task.id) return
        mutable.update { it.copy(androidBuild = it.androidBuild.copy(selectedId = task.id, logs = "", cursor = 0, logComplete = false, follow = true)) }
        refreshAndroidBuild()
    }
    fun toggleAndroidFollow() = mutable.update { it.copy(androidBuild = it.androidBuild.copy(follow = !it.androidBuild.follow)) }
    fun submitAndroidBuild(type: String) {
        if (mutable.value.androidBuild.submitting != null) return
        val epoch = generation
        mutable.update { it.copy(androidBuild = it.androidBuild.copy(submitting = type)) }
        viewModelScope.launch {
            try {
                val task = if (type == "BUILD") repository.androidBuild() else repository.androidUpdate()
                if (epoch != generation) return@launch
                mutable.update { it.copy(clock = System.currentTimeMillis(), androidBuild = it.androidBuild.copy(
                    submitting = null, status = it.androidBuild.status.copy(activeTask = task),
                    selectedId = task.id, logs = "", cursor = 0, logComplete = false, follow = true,
                    tasks = LoadState(listOf(task) + it.androidBuild.tasks.data.filter { item -> item.id != task.id }, loaded = true),
                )) }
                notify(if (type == "BUILD") "已提交 APK 编译任务" else "已提交代码更新任务")
                refreshAndroidBuild()
            } catch (error: CancellationException) { throw error }
            catch (error: Throwable) {
                if (epoch == generation) { mutable.update { it.copy(androidBuild = it.androidBuild.copy(submitting = null)) }; handleError(error) }
            }
        }
    }
    fun cancelAndroidTask(task: AndroidBuildTask) = confirm("取消构建任务",
        "确定取消「${task.typeLabel} #${task.id}」？构建容器会停止当前进程，未完成的产物会被清理。", "取消任务") {
        perform("android-cancel", "已发送取消请求", after = { refreshAndroidBuild() }) { repository.androidCancel(task.id) }
    }
    fun downloadAndroidArtifact(task: AndroidBuildTask, logFile: Boolean = false) {
        viewModelScope.launch {
            try { buildDownloads.download(task, logFile) }
            catch (error: CancellationException) { throw error }
            catch (error: Throwable) { handleError(error) }
        }
    }
    fun cancelAndroidDownload() { viewModelScope.launch { buildDownloads.cancel() } }
    /** 构建页面可见时驱动下载进度；没有进行中的下载会直接返回。 */
    fun pollAndroidDownload() { viewModelScope.launch { buildDownloads.poll() } }
    fun androidBuildInstallerIntent() = buildDownloads.installIntent()
    fun canInstallAndroidBuild() = buildDownloads.canInstallPackages()
    fun androidBuildInstallError(message: String) = buildDownloads.installationError(message)

    private var chatGeneration = 0
    private var localId = -1L
    fun openSession(session: Session) {
        if (session.isLocal) {
            closeChat()
            dismissSheet()
            mutable.update { it.copy(chat = ChatState(session = session)) }
            val service = session.managementService
            if (service != null) refreshManagement(service) else checkAppUpdate()
            return
        }
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
    fun refreshAppUpdateAfterInstall() { appUpdates.refreshInstalledVersion(); checkAppUpdate() }

    private fun merge(old: List<ChatMessage>, incoming: List<ChatMessage>) = (old + incoming).associateBy { it.id }.values.sortedWith(compareBy<ChatMessage> { if (it.id < 0) Long.MAX_VALUE else it.id }.thenBy { it.createdAt })
    fun refreshChat() {
        val chat = mutable.value.chat; val id = chat.session?.id ?: return
        if (chat.loading || chat.session?.isLocal == true) return
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
        if (chat.session?.isLocal == true || chat.loadingEarlier || !chat.hasEarlier) return
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
        if (selected.kind == "system" || selected.isLocal || content.isBlank()) return
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
        if (selected.kind == "system" || selected.isLocal) return
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
