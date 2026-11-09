package com.huluwa.android

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.*
import com.huluwa.android.hongguo.HongguoEpisodePreload
import com.huluwa.android.hongguo.HongguoOfflineDownloader
import com.huluwa.android.hongguo.HongguoOfflineStatus
import com.huluwa.android.hongguo.HongguoPrefetchCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

enum class HongguoFeedTab { Recommend, Ranking, Search, Favorites }

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
    val episodePreloads: List<HongguoEpisodePreload> = emptyList(),
    val playback: HongguoPlayback? = null, val streamIndex: Int = 0, val playRequest: Long = 0,
    val startPositionMs: Long = 0, val playLoading: Boolean = false, val playError: String? = null,
    val settings: HongguoSettings? = null, val settingsVisible: Boolean = false,
    val settingsLoading: Boolean = false, val settingsSaving: Boolean = false, val settingsError: String? = null,
    val favorites: List<HongguoFavorite> = emptyList(),
    val offline: Map<String, HongguoOfflineStatus> = emptyMap(),
    val offlineEpisodes: Map<String, Set<String>> = emptyMap(),
    val playingOffline: Boolean = false,
) {
    val stream get() = playback?.streams?.getOrNull(streamIndex)
    val currentRankContent get() = rankContents.firstOrNull { it.selected || it.id == rankContent }
    val currentRankBoards get() = currentRankContent?.boards.orEmpty()
    val favoriteSelected get() = detail?.series?.id?.let { id -> favorites.any { it.id == id } } ?: false
}

class HongguoViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HuluwaApplication
    private val repository = HongguoRepository(app.repository.api)
    private val playbackCache = HongguoPrefetchCache(viewModelScope, repository::playback)
    private val history = HongguoHistoryStore(application, app.session.server, app.session.username)
    private val favoriteStore = HongguoFavoriteStore(application, app.session.server, app.session.username)
    private val feedCache = HongguoFeedCacheStore(application, app.session.server, app.session.username)
    private val offlineDownloader = app.hongguoOffline
    private val mutable = MutableStateFlow(HongguoState())
    val state = mutable.asStateFlow()
    private var searchJob: Job? = null
    private var discoverJob: Job? = null
    private var rankJob: Job? = null
    private var detailJob: Job? = null
    private var playbackJob: Job? = null
    private var prefetchJob: Job? = null
    private val positions = mutableMapOf<String, HongguoWatchProgress>()
    private val lastSavedAt = mutableMapOf<String, Long>()
    private var preferredDefinition = "1080p"

    init {
        val cachedDiscover = feedCache.readDiscover()
        val cachedRanking = feedCache.readDefaultRanking()
        mutable.update {
            it.copy(
                favorites = favoriteStore.read(), discover = cachedDiscover,
                discoverLoaded = cachedDiscover.isNotEmpty(),
                rankContent = cachedRanking?.content.orEmpty(), rankBoard = cachedRanking?.board.orEmpty(),
                rankCursor = cachedRanking?.cursor.orEmpty(), rankItems = cachedRanking?.items.orEmpty(),
                rankContents = cachedRanking?.contents.orEmpty(), rankHasMore = cachedRanking?.hasMore == true,
                rankLoaded = cachedRanking != null,
            )
        }
        // 离线下载器在应用级存活，ViewModel 只观察状态流并按剧集归档分集标记。
        viewModelScope.launch {
            offlineDownloader.observable.collect { statuses ->
                mutable.update { it.copy(offline = statuses, offlineEpisodes = statuses.mapValues { s -> s.value.episodeIds }) }
            }
        }
        loadSettings()
    }
    fun setQuery(value: String) { mutable.update { it.copy(query = value.take(100)) } }
    fun selectTab(tab: HongguoFeedTab) {
        mutable.update { it.copy(tab = tab) }
        when (tab) {
            HongguoFeedTab.Recommend, HongguoFeedTab.Ranking -> Unit
            HongguoFeedTab.Search, HongguoFeedTab.Favorites -> Unit
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
                feedCache.writeDiscover(page.items)
                mutable.update { it.copy(discover = page.items, discoverLoading = false, discoverLoaded = true) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(discoverLoading = false,
                discoverError = error.userMessage()) } }
        }
    }
    fun loadRanking(content: String = mutable.value.rankContent, board: String = mutable.value.rankBoard,
                    more: Boolean = false) {
        val current = mutable.value
        if (more && (current.rankLoading || current.rankMore || !current.rankHasMore)) return
        val cursor = if (more) current.rankCursor else ""
        val cached = if (more) null else feedCache.readRanking(content, board)
        mutable.update { it.copy(rankContent = content, rankBoard = board, rankCursor = if (more) it.rankCursor else "",
            rankLoading = !more, rankMore = more, rankError = null,
            rankItems = if (more) it.rankItems else cached?.items.orEmpty(),
            rankContents = cached?.contents ?: it.rankContents,
            rankHasMore = if (more) it.rankHasMore else cached?.hasMore == true) }
        rankJob?.cancel(); rankJob = viewModelScope.launch {
            try {
                val ranking = repository.ranking(content, board, cursor)
                if (!more) {
                    feedCache.writeRanking(HongguoCachedRanking(ranking.content, ranking.board,
                        ranking.contents, ranking.items, ranking.hasMore, ranking.cursor), content, board,
                        default = content.isBlank() && board.isBlank())
                }
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
    fun selectRankContent(id: String) {
        showCachedRanking(feedCache.readRanking(id, ""))
        loadRanking(content = id, board = "")
    }
    fun selectRankBoard(id: String) {
        val content = mutable.value.rankContent
        showCachedRanking(feedCache.readRanking(content, id))
        loadRanking(content = content, board = id)
    }
    private fun showCachedRanking(cached: HongguoCachedRanking?) {
        if (cached == null) return
        mutable.update {
            it.copy(rankContent = cached.content, rankBoard = cached.board, rankCursor = cached.cursor,
                rankItems = cached.items, rankContents = cached.contents, rankHasMore = cached.hasMore,
                rankLoaded = true)
        }
    }
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
        detailJob?.cancel(); playbackJob?.cancel(); prefetchJob?.cancel()
        playbackCache.clear()
        mutable.update { it.copy(selectedSeries = series, detail = null, detailLoading = true, detailError = null,
            episode = null, playback = null, episodePreloads = emptyList(), playLoading = false, playError = null,
            playingOffline = false) }
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
        playbackJob?.cancel(); prefetchJob?.cancel()
        val pages = detail.episodes.filterNot { it.locked }
        val page = pages.indexOfFirst { it.id == episode.id }
        playbackCache.retain(pages.subList((page - 1).coerceAtLeast(0), (page + 2).coerceAtMost(pages.size))
            .mapTo(mutableSetOf()) { it.id })
        val resume = resumePosition(detail.series.id, episode.id)
        val local = offlinePlayback(detail.series.id, episode.id)
        val cached = local ?: playbackCache.peek(episode.id)
        val request = mutable.value.playRequest + 1
        mutable.update { it.copy(episode = episode, playback = cached,
            streamIndex = cached?.let(::preferredStreamIndex) ?: 0,
            episodePreloads = cachedNeighbors(detail, episode), playLoading = cached == null, playError = null,
            startPositionMs = resume, playRequest = request, playingOffline = local != null) }
        playbackJob = viewModelScope.launch {
            try {
                val playback = cached ?: playbackCache.get(episode.id)
                if (mutable.value.playRequest != request) return@launch
                mutable.update { it.copy(playback = playback, streamIndex = preferredStreamIndex(playback),
                    playLoading = false) }
                prefetchNextEpisode(request)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update {
                if (it.playRequest == request) it.copy(playLoading = false, playError = error.userMessage()) else it
            } }
        }
    }
    fun retryPlayback() {
        mutable.value.episode?.let { episode ->
            playbackCache.invalidate(episode.id)
            playEpisode(episode)
        }
    }
    private fun preferredStreamIndex(playback: HongguoPlayback): Int =
        playback.streams.indexOfFirst { it.definition == preferredDefinition }.coerceAtLeast(0)

    private fun resumePosition(seriesId: String, episodeId: String): Long {
        val progress = positions[episodeId] ?: history.read(seriesId)?.takeIf { it.episodeId == episodeId }
        return progress?.takeUnless { it.completed }?.positionMs ?: 0L
    }

    private fun neighboringEpisodes(detail: HongguoDetail, episode: HongguoEpisode): List<HongguoEpisode> {
        val pages = detail.episodes.filterNot { it.locked }
        val index = pages.indexOfFirst { it.id == episode.id }
        return listOfNotNull(pages.getOrNull(index + 1), pages.getOrNull(index - 1))
    }

    private fun cachedNeighbors(detail: HongguoDetail, episode: HongguoEpisode): List<HongguoEpisodePreload> =
        neighboringEpisodes(detail, episode).mapNotNull { neighbor ->
            offlinePreload(detail.series.id, neighbor)
                ?: playbackCache.peek(neighbor.id)?.let { playback ->
                    HongguoEpisodePreload(neighbor.id, playback, playback.streams[preferredStreamIndex(playback)],
                        resumePosition(detail.series.id, neighbor.id))
                }
        }

    /** 离线分集的本地播放模型：已解密明文 MP4，直连文件，无需向服务取播放地址。 */
    private fun offlinePlayback(seriesId: String, episodeId: String): HongguoPlayback? {
        val (file, definition) = offlineDownloader.lookup(seriesId, episodeId) ?: return null
        val stream = HongguoStream(Uri.fromFile(file).toString(), "", definition, "bytevc1",
            0, 1080, file.length(), false, "", "", "")
        return HongguoPlayback(episodeId, listOf(stream), 0, "")
    }

    private fun offlinePreload(seriesId: String, episode: HongguoEpisode): HongguoEpisodePreload? =
        offlinePlayback(seriesId, episode.id)?.let { playback ->
            HongguoEpisodePreload(episode.id, playback, playback.streams.first(),
                resumePosition(seriesId, episode.id))
        }

    /** 先准备下一集，再准备上一集；切集不取消仍可复用的在途播放地址请求。 */
    fun prefetchNextEpisode(currentRequest: Long? = null) {
        val current = mutable.value
        if (currentRequest != null && current.playRequest != currentRequest) return
        val detail = current.detail ?: return
        val episode = current.episode ?: return
        if (prefetchJob?.isActive == true) return
        prefetchJob = viewModelScope.launch {
            for (neighbor in neighboringEpisodes(detail, episode)) {
                try {
                    // 离线分集直接本地成源，只有在线分集才需要预取播放地址。
                    if (offlinePlayback(detail.series.id, neighbor.id) == null) playbackCache.get(neighbor.id)
                    if (mutable.value.playRequest != current.playRequest) return@launch
                    mutable.update { it.copy(episodePreloads = cachedNeighbors(detail, episode)) }
                } catch (error: CancellationException) { throw error }
                catch (_: Exception) { /* 相邻集预加载失败不影响当前播放，切集时仍可重试。 */ }
            }
        }
    }
    fun toggleFavorite() {
        val series = mutable.value.detail?.series ?: mutable.value.selectedSeries ?: return
        mutable.update { it.copy(favorites = favoriteStore.toggle(series)) }
    }

    /** 开始（或补齐）整剧离线；标题与封面取当前详情或收藏里的最新展示信息。 */
    fun startOfflineDownload(seriesId: String) {
        val series = mutable.value.detail?.series?.takeIf { it.id == seriesId }
            ?: mutable.value.selectedSeries?.takeIf { it.id == seriesId }
            ?: mutable.value.favorites.firstOrNull { it.id == seriesId }?.series()
            ?: return
        offlineDownloader.start(seriesId, series.title, series.cover)
    }
    fun cancelOfflineDownload(seriesId: String) = offlineDownloader.cancel(seriesId)
    fun removeOffline(seriesId: String) = offlineDownloader.remove(seriesId)
    fun selectStream(index: Int) {
        val current = mutable.value
        val stream = current.playback?.streams?.getOrNull(index) ?: return
        if (index == current.streamIndex && current.playError == null) return
        preferredDefinition = stream.definition
        val position = current.episode?.let { positions[it.id]?.positionMs } ?: current.startPositionMs
        prefetchJob?.cancel()
        mutable.update { it.copy(streamIndex = index, startPositionMs = position, playError = null,
            episodePreloads = current.detail?.let { detail -> current.episode?.let { cachedNeighbors(detail, it) } }.orEmpty(),
            playRequest = it.playRequest + 1) }
        prefetchNextEpisode(mutable.value.playRequest)
    }
    fun nextEpisode(request: Long? = null) {
        val current = mutable.value
        if (request != null && current.playRequest != request) return
        val episodes = current.detail?.episodes ?: return
        val index = episodes.indexOfFirst { it.id == current.episode?.id }
        val next = episodes.asSequence().drop(index + 1).firstOrNull { !it.locked } ?: return
        playEpisode(next)
    }
    fun previousEpisode() {
        val current = mutable.value
        val episodes = current.detail?.episodes ?: return
        val index = episodes.indexOfFirst { it.id == current.episode?.id }
        val previous = episodes.asSequence().take(index.coerceAtLeast(0)).lastOrNull { !it.locked } ?: return
        playEpisode(previous)
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
        detailJob?.cancel(); playbackJob?.cancel(); prefetchJob?.cancel()
        playbackCache.clear()
        mutable.update { it.copy(selectedSeries = null, detail = null, detailLoading = false, detailError = null,
            episode = null, playback = null, episodePreloads = emptyList(), playLoading = false, playError = null,
            playingOffline = false) }
        return true
    }
    override fun onCleared() {
        prefetchJob?.cancel()
        playbackCache.clear()
        super.onCleared()
    }
}
