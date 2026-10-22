package com.huluwa.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
import java.util.UUID

data class AiUiState(
    val server: String = "", val username: String = "admin", val authenticated: Boolean = false,
    val authenticating: Boolean = false, val loading: Boolean = false, val loadingSession: Boolean = false,
    val sessions: List<AiSession> = emptyList(), val projects: List<AiProject> = emptyList(),
    val providers: List<AiProvider> = emptyList(), val projectPath: String = "", val providerId: String = "", val modelId: String = "",
    val selected: AiSession? = null, val draft: String = "", val sending: Boolean = false,
    val connected: Boolean = false, val working: Boolean = false, val error: String? = null, val connectionRevision: Long = 0,
) {
    val launchProject get() = projects.find { it.path == projectPath }
    val availableProviders get() = providers.filter { provider ->
        launchProject?.let { if (provider.agent == "codex") it.enabledAgents.codex else it.enabledAgents.claude } == true
    }
    val launchProvider get() = availableProviders.find { it.id == providerId }
    val launchModel get() = launchProvider?.models?.find { it.id == modelId }
    val canCreate get() = launchProject != null && launchProvider != null && launchModel != null && !loading && !working
}

class AiViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = (application as HuluwaApplication).aiRepository
    private val store = repository.store
    private val mutable = MutableStateFlow(AiUiState(store.server, store.username, store.loggedIn,
        projectPath = repository.selectedProject, providerId = repository.selectedProvider, modelId = repository.selectedModel))
    val state = mutable.asStateFlow()
    private var visible = false
    private var generation = 0
    private var authJob: Job? = null
    private var refreshJob: Job? = null
    private var streamJob: Job? = null
    private var pollingJob: Job? = null
    private var selectJob: Job? = null
    private var sendJob: Job? = null
    private var actionJob: Job? = null
    private val drafts = mutableMapOf<String, String>()
    private val requests = mutableMapOf<String, Pair<String, String>>()

    fun setVisible(active: Boolean) {
        visible = active
        if (active && store.loggedIn) { refresh(); startRealtime() } else stopRealtime()
    }
    fun login(server: String, username: String, password: String) {
        if (authJob?.isActive == true) return
        authJob = viewModelScope.launch {
            mutable.update { it.copy(authenticating = true, error = null) }
            try {
                require(username.isNotBlank()) { "请输入 AI 服务账号" }
                require(password.isNotEmpty()) { "请输入 AI 服务密码" }
                withContext(Dispatchers.IO) { repository.login(server, username.trim(), password) }
                generation++; stopRealtime(); refreshJob?.cancel(); refreshJob = null; selectJob?.cancel(); sendJob?.cancel(); actionJob?.cancel(); drafts.clear(); requests.clear()
                mutable.value = AiUiState(store.server, store.username, true, projectPath = repository.selectedProject,
                    providerId = repository.selectedProvider, modelId = repository.selectedModel, connectionRevision = System.currentTimeMillis())
                refresh()
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { mutable.update { it.copy(authenticating = false, error = e.userMessage()) } }
        }
    }
    fun logout() {
        generation++; stopRealtime(); authJob?.cancel(); refreshJob?.cancel(); selectJob?.cancel(); sendJob?.cancel(); actionJob?.cancel()
        store.clear(); repository.lastSession = ""; drafts.clear(); requests.clear()
        mutable.value = AiUiState(store.server, store.username)
    }
    private fun failure(error: Throwable) {
        if (error is ApiException && error.code == 401) {
            logout(); mutable.update { it.copy(error = "AI 服务登录已过期，请重新登录") }
        } else mutable.update { it.copy(error = error.userMessage()) }
    }
    fun clearError() { mutable.update { it.copy(error = null) } }
    fun refresh() {
        if (!store.loggedIn || refreshJob?.isActive == true) return
        val epoch = generation
        refreshJob = viewModelScope.launch {
            mutable.update { it.copy(loading = true) }
            try {
                val (sessions, projects, catalog) = coroutineScope {
                    val sessions = async { repository.sessions() }; val projects = async { repository.projects() }
                    val catalog = async { repository.catalog() }
                    Triple(sessions.await(), projects.await(), catalog.await())
                }
                if (epoch != generation) return@launch
                mutable.update { reconcile(it.copy(sessions = sessions, projects = projects, providers = catalog.providers, loading = false, error = null)) }
                saveSelection()
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (epoch == generation) { mutable.update { it.copy(loading = false) }; failure(e) } }
        }
    }
    private fun reconcile(ui: AiUiState): AiUiState {
        val project = ui.launchProject ?: ui.projects.firstOrNull()
        val projectUi = ui.copy(projectPath = project?.path.orEmpty())
        val choices = projectUi.availableProviders
        val provider = choices.find { it.id == ui.providerId }
            ?: choices.firstOrNull { it.isDefault && it.models.isNotEmpty() }
            ?: choices.firstOrNull { it.models.isNotEmpty() } ?: choices.firstOrNull()
        val model = provider?.models?.find { it.id == ui.modelId } ?: provider?.models?.firstOrNull()
        return projectUi.copy(providerId = provider?.id.orEmpty(), modelId = model?.id.orEmpty())
    }
    private fun saveSelection() {
        val ui = mutable.value
        repository.saveSelection(ui.projectPath, ui.providerId, ui.modelId)
    }
    fun selectProject(path: String) { mutable.update { reconcile(it.copy(projectPath = path)) }; saveSelection() }
    fun selectProvider(id: String) { mutable.update { reconcile(it.copy(providerId = id, modelId = "")) }; saveSelection() }
    fun selectModel(id: String) { mutable.update { reconcile(it.copy(modelId = id)) }; saveSelection() }
    fun open(id: String) {
        refreshJob?.cancel(); selectJob?.cancel(); sendJob?.cancel(); sendJob = null; stopRealtime()
        val epoch = ++generation
        val summary = mutable.value.sessions.find { it.id == id }
        repository.lastSession = id
        mutable.update { it.copy(selected = summary, loading = false, loadingSession = true, draft = drafts[id].orEmpty(), sending = false, working = false, error = null) }
        selectJob = viewModelScope.launch {
            try {
                val session = repository.session(id)
                if (epoch != generation) return@launch
                mutable.update { it.copy(selected = session, loadingSession = false) }
                startRealtime()
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (epoch == generation) { mutable.update { it.copy(loadingSession = false) }; failure(e) } }
        }
    }
    fun closeSession() {
        generation++; selectJob?.cancel(); sendJob?.cancel(); sendJob = null; stopRealtime(); repository.lastSession = ""
        mutable.update { it.copy(selected = null, draft = "", loadingSession = false, sending = false, working = false, error = null) }
        refresh()
    }
    fun create() {
        val ui = mutable.value
        val project = ui.launchProject ?: return
        val provider = ui.launchProvider ?: return
        val model = ui.launchModel ?: return
        if (!ui.canCreate || actionJob?.isActive == true) return
        val epoch = generation
        actionJob = viewModelScope.launch {
            mutable.update { it.copy(working = true, error = null) }
            try {
                val session = repository.create(project, provider, model.id)
                if (epoch != generation) return@launch
                mutable.update { it.copy(sessions = listOf(session) + it.sessions, working = false) }
                open(session.id)
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (epoch == generation) { mutable.update { it.copy(working = false) }; failure(e) } }
        }
    }
    fun draft(text: String) {
        mutable.value.selected?.id?.let { drafts[it] = text }
        mutable.update { it.copy(draft = text) }
    }
    fun send() {
        val session = mutable.value.selected ?: return
        val text = mutable.value.draft.trim()
        if (text.isEmpty() || session.running || sendJob?.isActive == true) return
        val request = requests[session.id]?.takeIf { it.first == text } ?: (text to UUID.randomUUID().toString()).also { requests[session.id] = it }
        val epoch = generation
        sendJob = viewModelScope.launch {
            mutable.update { it.copy(sending = true, error = null) }
            try {
                val result = repository.send(session.id, text, request.second)
                requests.remove(session.id)
                if (epoch != generation || mutable.value.selected?.id != session.id) return@launch
                // 发送成功才清空；失败或未知结果保留文本和 requestId，可安全重试。
                if (mutable.value.draft.trim() == text) { drafts[session.id] = ""; mutable.update { it.copy(draft = "") } }
                receive(result)
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (epoch == generation) failure(e) }
            finally { if (epoch == generation) mutable.update { it.copy(sending = false) } }
        }
    }
    fun stop() = action { repository.stop(it.id) }
    fun answer(approval: AiApproval, decision: String, answers: JsonObject = JsonObject(emptyMap())) = action {
        repository.answer(it.id, approval, decision, answers)
    }
    fun delete(session: AiSession) {
        if (actionJob?.isActive == true) return
        val epoch = generation
        actionJob = viewModelScope.launch {
            mutable.update { it.copy(working = true, error = null) }
            try {
                repository.delete(session.id)
                if (epoch == generation) {
                    if (mutable.value.selected?.id == session.id) closeSession()
                    mutable.update { it.copy(sessions = it.sessions.filterNot { item -> item.id == session.id }, working = false) }
                    drafts.remove(session.id); requests.remove(session.id)
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (epoch == generation) { mutable.update { it.copy(working = false) }; failure(e) } }
        }
    }
    private fun action(block: suspend (AiSession) -> Unit) {
        val session = mutable.value.selected ?: return
        if (actionJob?.isActive == true) return
        val epoch = generation
        actionJob = viewModelScope.launch {
            mutable.update { it.copy(working = true, error = null) }
            try { block(session); if (epoch == generation) receive(repository.session(session.id)) }
            catch (e: CancellationException) { throw e }
            catch (e: Throwable) { if (epoch == generation) failure(e) }
            finally { if (epoch == generation) mutable.update { it.copy(working = false) } }
        }
    }
    private fun receive(session: AiSession) {
        mutable.update { current ->
            val selected = current.selected
            if (selected == null || selected.id != session.id || session.revision < selected.revision) current
            else current.copy(selected = session, sessions = (listOf(session.copy(messages = emptyList())) + current.sessions.filterNot { it.id == session.id }).sortedByDescending { it.updatedAt })
        }
    }
    private fun startRealtime() {
        val id = mutable.value.selected?.id ?: return
        if (!visible || !store.loggedIn || streamJob?.isActive == true) return
        val epoch = generation
        streamJob = viewModelScope.launch {
            var retries = 0
            while (isActive && epoch == generation) {
                try {
                    repository.observe(id).collect { snapshot ->
                        if (epoch == generation) { retries = 0; mutable.update { it.copy(connected = true) }; receive(snapshot) }
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: Throwable) { if (e is ApiException && e.code == 401) { failure(e); return@launch } }
                if (epoch != generation) break
                mutable.update { it.copy(connected = false) }
                delay((1_000L shl retries.coerceAtMost(4))); retries++
            }
        }
        // WS 受代理/切网影响时仍可读取消息；也能及时识别认证失效。
        pollingJob = viewModelScope.launch {
            while (isActive && epoch == generation) {
                delay(if (mutable.value.connected) 15_000 else 3_000)
                try { val snapshot = repository.session(id); if (epoch == generation) { receive(snapshot); mutable.update { it.copy(error = null) } } }
                catch (e: CancellationException) { throw e }
                catch (e: Throwable) { if (epoch == generation) failure(e) }
            }
        }
    }
    private fun stopRealtime() {
        streamJob?.cancel(); streamJob = null; pollingJob?.cancel(); pollingJob = null
        mutable.update { it.copy(connected = false) }
    }
}
