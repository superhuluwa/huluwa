package com.huluwa.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class HongguoFeedTab { Recommend, Ranking, Search }

data class HongguoState(
    val tab: HongguoFeedTab = HongguoFeedTab.Recommend,
    val query: String = "", val submittedQuery: String = "", val results: List<HongguoSeries> = emptyList(),
    val searched: Boolean = false, val searching: Boolean = false, val loadingMore: Boolean = false,
    val nextOffset: Int = 0, val hasMore: Boolean = false, val searchError: String? = null,
    val discover: List<HongguoSeries> = emptyList(), val discoverLoading: Boolean = false,
    val discoverLoaded: Boolean = false, val discoverError: String? = null,
    val rankContent: String = "", val rankBoard: String = "", val rankCursor: String = "",
    val rankItems: List<HongguoRankItem> = emptyList(), val rankContents: List<HongguoRankContent> = emptyList(),
    val rankLoading: Boolean = false, val rankMore: Boolean = false, val rankLoaded: Boolean = false,
    val rankHasMore: Boolean = false, val rankError: String? = null,
    val detail: HongguoDetail? = null, val detailLoading: Boolean = false, val detailError: String? = null,
    val selectedSeries: HongguoSeries? = null, val episode: HongguoEpisode? = null,
    val playback: HongguoPlayback? = null, val streamIndex: Int = 0, val playRequest: Long = 0,
    val startPositionMs: Long = 0, val playLoading: Boolean = false, val playError: String? = null,
    val settings: HongguoSettings? = null, val settingsVisible: Boolean = false,
    val settingsLoading: Boolean = false, val settingsSaving: Boolean = false, val settingsError: String? = null,
) {
    val stream get() = playback?.streams?.getOrNull(streamIndex)
    val currentRankContent get() = rankContents.firstOrNull { it.selected || it.id == rankContent }
    val currentRankBoards get() = currentRankContent?.boards.orEmpty()
}

class HongguoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HuluwaApplication
    private val repository = HongguoRepository(app.repository.api)
    private val history = HongguoHistoryStore(application, app.session.server, app.session.username)
    private val mutable = MutableStateFlow(HongguoState())
    val state = mutable.asStateFlow()
    private var searchJob: Job? = null
    private var discoverJob: Job? = null
    private var rankJob: Job? = null
    private var detailJob: Job? = null
    private var playbackJob: Job? = null
    private val positions = mutableMapOf<String, HongguoWatchProgress>()
    private val lastSavedAt = mutableMapOf<String, Long>()
    private var preferredDefinition = "720p"

    init { loadSettings() }
    fun setQuery(value: String) { mutable.update { it.copy(query = value.take(100)) } }
    fun selectTab(tab: HongguoFeedTab) {
        mutable.update { it.copy(tab = tab) }
        when (tab) {
            HongguoFeedTab.Recommend -> if (!mutable.value.discoverLoading) loadDiscover()
            HongguoFeedTab.Ranking -> if (!mutable.value.rankLoaded) loadRanking()
            HongguoFeedTab.Search -> Unit
        }
    }
    fun loadDiscover() {
        val current = mutable.value
        if (current.discoverLoading) return
        mutable.update { it.copy(discoverLoading = true, discoverError = null) }
        discoverJob?.cancel()
        discoverJob = viewModelScope.launch {
            try {
                val page = repository.discover()
                mutable.update { it.copy(discover = page.items, discoverLoading = false, discoverLoaded = true) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(discoverLoading = false,
                discoverError = error.userMessage()) } }
        }
    }
    fun loadRanking(content: String = mutable.value.rankContent, board: String = mutable.value.rankBoard,
                    more: Boolean = false) {
        val current = mutable.value
        if (current.rankLoading || current.rankMore || (more && !current.rankHasMore)) return
        val cursor = if (more) current.rankCursor else ""
        mutable.update { it.copy(rankContent = content, rankBoard = board, rankCursor = if (more) it.rankCursor else "",
            rankLoading = !more, rankMore = more, rankError = null, rankItems = if (more) it.rankItems else emptyList()) }
        rankJob?.cancel(); rankJob = viewModelScope.launch {
            try {
                val ranking = repository.ranking(content, board, cursor)
                mutable.update { old ->
                    val rows = if (more) (old.rankItems + ranking.items).distinctBy { it.kind + it.id } else ranking.items
                    old.copy(rankItems = rows, rankContent = ranking.content, rankBoard = ranking.board,
                        rankContents = ranking.contents, rankCursor = ranking.cursor, rankHasMore = ranking.hasMore,
                        rankLoading = false, rankMore = false, rankLoaded = true)
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(rankLoading = false, rankMore = false,
                rankError = error.userMessage()) } }
        }
    }
    fun selectRankContent(id: String) { loadRanking(content = id, board = "") }
    fun selectRankBoard(id: String) { loadRanking(content = mutable.value.rankContent, board = id) }
    fun showSettings() { mutable.update { it.copy(settingsVisible = true, settingsError = null) }; loadSettings() }
    fun hideSettings() { if (!mutable.value.settingsSaving) mutable.update { it.copy(settingsVisible = false) } }
    private fun loadSettings() {
        if (mutable.value.settingsLoading) return
        mutable.update { it.copy(settingsLoading = true) }
        viewModelScope.launch {
            try {
                val settings = repository.settings()
                mutable.update { it.copy(settings = settings, settingsLoading = false, settingsError = null) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(settingsLoading = false, settingsError = error.userMessage()) } }
        }
    }
    fun saveSettings(url: String) {
        if (mutable.value.settingsSaving) return
        mutable.update { it.copy(settingsSaving = true, settingsError = null) }
        viewModelScope.launch {
            try {
                val settings = repository.saveSettings(url)
                mutable.update { it.copy(settings = settings, settingsSaving = false, settingsVisible = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(settingsSaving = false, settingsError = error.userMessage()) } }
        }
    }
    fun syncSettings() {
        if (mutable.value.settingsSaving) return
        mutable.update { it.copy(settingsSaving = true, settingsError = null) }
        viewModelScope.launch {
            try {
                val settings = repository.syncSettings()
                mutable.update { it.copy(settings = settings, settingsSaving = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(settingsSaving = false, settingsError = error.userMessage()) } }
        }
    }
    fun search(more: Boolean = false) {
        val current = mutable.value
        if (!more) mutable.update { it.copy(tab = HongguoFeedTab.Search) }
        if (more && (current.searching || current.loadingMore || !current.hasMore)) return
        val query = (if (more) current.submittedQuery else current.query).trim()
        if (query.isBlank()) return
        searchJob?.cancel()
        val offset = if (more) current.nextOffset else 0
        mutable.update { it.copy(submittedQuery = query, searched = true, searching = !more, loadingMore = more,
            searchError = null, results = if (more) it.results else emptyList(), hasMore = if (more) it.hasMore else false) }
        searchJob = viewModelScope.launch {
            try {
                val page = repository.search(query, offset)
                mutable.update { old ->
                    val rows = if (more) (old.results + page.items).distinctBy { it.id } else page.items
                    old.copy(results = rows, searching = false, loadingMore = false, nextOffset = page.nextOffset,
                        hasMore = page.hasMore && (!more || rows.size > old.results.size))
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(searching = false, loadingMore = false, searchError = error.userMessage()) } }
        }
    }
    fun openSeries(series: HongguoSeries) {
        detailJob?.cancel(); playbackJob?.cancel()
        mutable.update { it.copy(selectedSeries = series, detail = null, detailLoading = true, detailError = null,
            episode = null, playback = null, playLoading = false, playError = null) }
        detailJob = viewModelScope.launch {
            try {
                val detail = repository.detail(series.id)
                mutable.update { it.copy(detail = detail, detailLoading = false) }
                val progress = history.read(series.id)
                val index = detail.episodes.indexOfFirst { it.id == progress?.episodeId }
                val next = if (index >= 0 && progress?.completed == true) detail.episodes.getOrNull(index + 1) else null
                val episode = next?.takeUnless { it.locked } ?: detail.episodes.getOrNull(index)?.takeUnless { it.locked }
                    ?: detail.episodes.firstOrNull { !it.locked }
                if (episode != null) playEpisode(episode)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(detailLoading = false, detailError = error.userMessage()) } }
        }
    }
    fun playEpisode(episode: HongguoEpisode) {
        if (episode.locked) return
        val detail = mutable.value.detail ?: return
        if (detail.episodes.none { it.id == episode.id }) return
        playbackJob?.cancel()
        val progress = positions[episode.id] ?: history.read(detail.series.id)?.takeIf { it.episodeId == episode.id }
        val resume = progress?.takeUnless { it.completed }?.positionMs ?: 0L
        mutable.update { it.copy(episode = episode, playback = null, playLoading = true, playError = null,
            startPositionMs = resume, playRequest = it.playRequest + 1) }
        playbackJob = viewModelScope.launch {
            try {
                val playback = repository.playback(episode.id)
                val available = playback.streams.filter { it.supported }.sortedByDescending { it.height }
                check(available.isNotEmpty()) { "该集暂未提供手机支持的视频编码，请稍后重试" }
                val selected = available.indexOfFirst { it.definition == preferredDefinition }.coerceAtLeast(0)
                mutable.update { it.copy(playback = playback.copy(streams = available), streamIndex = selected, playLoading = false) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(playLoading = false, playError = error.userMessage()) } }
        }
    }
    fun retryPlayback() { mutable.value.episode?.let(::playEpisode) }
    fun selectStream(index: Int) {
        val current = mutable.value
        val stream = current.playback?.streams?.getOrNull(index) ?: return
        if (index == current.streamIndex && current.playError == null) return
        preferredDefinition = stream.definition
        val position = current.episode?.let { positions[it.id]?.positionMs } ?: current.startPositionMs
        mutable.update { it.copy(streamIndex = index, startPositionMs = position, playError = null, playRequest = it.playRequest + 1) }
    }
    fun nextEpisode(request: Long? = null) {
        val current = mutable.value
        if (request != null && current.playRequest != request) return
        val episodes = current.detail?.episodes ?: return
        val index = episodes.indexOfFirst { it.id == current.episode?.id }
        episodes.getOrNull(index + 1)?.takeUnless { it.locked }?.let(::playEpisode)
    }
    fun previousEpisode() {
        val current = mutable.value
        val episodes = current.detail?.episodes ?: return
        episodes.getOrNull(episodes.indexOfFirst { it.id == current.episode?.id } - 1)?.takeUnless { it.locked }?.let(::playEpisode)
    }
    fun playerError(request: Long, message: String) {
        mutable.update { if (it.playRequest == request) it.copy(playError = message) else it }
    }
    fun progress(seriesId: String, episode: HongguoEpisode, position: Long, duration: Long, force: Boolean) {
        val progress = HongguoWatchProgress(episode.id, position.coerceAtLeast(0), duration.coerceAtLeast(0), episode.number)
        // 播放器刚创建还没有时长时，不用 0 覆盖断点；onDispose 仍保留已知时长。
        if (duration <= 0 && position <= 0) return
        val saved = if (duration > 0) progress else progress.copy(durationMs = positions[episode.id]?.durationMs ?: 0)
        positions[episode.id] = saved
        val now = android.os.SystemClock.elapsedRealtime()
        if (force || now - (lastSavedAt[episode.id] ?: 0) >= 5000) {
            history.write(seriesId, saved); lastSavedAt[episode.id] = now
        }
    }
    fun back(): Boolean {
        if (mutable.value.settingsVisible) { hideSettings(); return true }
        if (mutable.value.selectedSeries == null && mutable.value.tab != HongguoFeedTab.Recommend) {
            mutable.update { it.copy(tab = HongguoFeedTab.Recommend) }; return true
        }
        if (mutable.value.selectedSeries == null) return false
        detailJob?.cancel(); playbackJob?.cancel()
        mutable.update { it.copy(selectedSeries = null, detail = null, detailLoading = false, detailError = null,
            episode = null, playback = null, playLoading = false, playError = null) }
        return true
    }
}
