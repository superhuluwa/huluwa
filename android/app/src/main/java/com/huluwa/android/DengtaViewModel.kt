package com.huluwa.android

import android.app.Application
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.net.SocketTimeoutException
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToLong

enum class DengtaPage { Ranking, Detail, Journal }
data class DengtaState(
    val page: DengtaPage = DengtaPage.Ranking, val category: String = "1", val typeCode: String = "",
    val date: String = "", val metricCode: String = "",
    val config: LoadState<DengtaRankConfig?> = LoadState(null),
    val ranking: LoadState<DengtaRanking> = LoadState(DengtaRanking()),
    val marketIncrement: Long = 0L, val rowIncrements: Map<String, Long> = emptyMap(), val rankingUpdateId: Long = 0L,
    val selected: DengtaMedia? = null, val detail: LoadState<DengtaDetail?> = LoadState(null),
    val rules: LoadState<List<DengtaPlayRule>> = LoadState(emptyList()),
    val marketing: LoadState<DengtaMarketingContent> = LoadState(DengtaMarketingContent()), val marketingType: String = "all",
    val materials: LoadState<DengtaMaterialContent> = LoadState(DengtaMaterialContent()),
    val materialTab: String = "", val materialCategory: String = "all",
    val highlights: LoadState<DengtaHighlightContent> = LoadState(DengtaHighlightContent()),
    val journal: DengtaJournalData = DengtaJournalData(), val journalLoading: Boolean = true,
    val journalBusy: Boolean = false, val journalError: String? = null,
    val journalFromDetail: Boolean = false,
) {
    val selectedFollow: DengtaFollow? get() = journal.media.firstOrNull { it.media.id == selected?.id }
    val selectedLogs: List<DengtaWatchLog> get() = journal.logs.filter { it.mediaId == selected?.id }.sortedByDescending { it.recordedAt }
}

/** 网络快照留在会话内存中，收藏和个人日志独立持久化；不持有第三方账号。 */
class DengtaViewModel(application: Application) : AndroidViewModel(application) {
    private val session = (application as HuluwaApplication).session
    private val store = DengtaJournalStore(application, session.server, session.username)
    private val repository = DengtaRepository()
    private val mutable = MutableStateFlow(DengtaState())
    val state = mutable.asStateFlow()
    private val events = Channel<String>(Channel.BUFFERED)
    val messages = events.receiveAsFlow()
    private val journalMutex = Mutex()
    private val details = mutableMapOf<String, DengtaDetail>()
    private var rankJob: Job? = null
    private var detailJob: Job? = null
    private var marketingJob: Job? = null
    private var materialJob: Job? = null
    private var highlightJob: Job? = null
    private var contentMediaId: String? = null
    private var rankingVisible = false

    init { loadJournal() }

    fun setRankingVisible(visible: Boolean) {
        if (rankingVisible == visible) return
        rankingVisible = visible
        if (visible) refreshRanking() else {
            rankJob?.cancel()
            mutable.update { it.copy(ranking = it.ranking.copy(loading = false), marketIncrement = 0L, rowIncrements = emptyMap()) }
        }
    }

    fun selectType(typeCode: String) {
        if (typeCode == mutable.value.typeCode) return
        mutable.update { ui ->
            // 子类不同时指标 tab 可能不通用，切不过去就回落到该子类的默认指标。
            val metrics = ui.config.data?.type(typeCode)?.metrics.orEmpty()
            ui.copy(typeCode = typeCode, metricCode = metrics.firstOrNull { it.code == ui.metricCode }?.code
                ?: metrics.firstOrNull { it.default }?.code ?: metrics.firstOrNull()?.code.orEmpty(),
                ranking = LoadState(DengtaRanking()), marketIncrement = 0L, rowIncrements = emptyMap())
        }
        refreshRanking()
    }
    fun selectMetric(metricCode: String) {
        if (metricCode == mutable.value.metricCode) return
        mutable.update { it.copy(metricCode = metricCode, ranking = LoadState(DengtaRanking()), marketIncrement = 0L, rowIncrements = emptyMap()) }
        refreshRanking()
    }
    fun selectDate(date: String) {
        if (date == mutable.value.date) return
        mutable.update { it.copy(date = date, ranking = LoadState(DengtaRanking()), marketIncrement = 0L, rowIncrements = emptyMap()) }
        refreshRanking()
    }
    /** 分类切换后重新拉配置，因为三个分类的子类与指标 tab 完全不同。 */
    fun selectCategory(category: String) {
        if (category == mutable.value.category || category !in setOf("1", "2", "3")) return
        mutable.update { it.copy(category = category, typeCode = "", metricCode = "", ranking = LoadState(DengtaRanking()),
            marketIncrement = 0L, rowIncrements = emptyMap(), config = LoadState(null)) }
        refreshRanking()
    }

    /** 首次进入或分类变化时读取配置，返回可用状态；已缓存的配置不重复请求。 */
    private suspend fun ensureConfig(): DengtaRankConfig? {
        val current = mutable.value
        current.config.data?.takeIf { it.code == current.category }?.let { return it }
        if (!current.config.loading) mutable.update { it.copy(config = it.config.copy(loading = true, error = null)) }
        return try {
            val config = repository.rankConfig(current.category)
            mutable.update { if (it.category == config.code) {
                val type = config.type(it.typeCode) ?: config.defaultType
                it.copy(config = LoadState(config, loaded = true), typeCode = type?.code.orEmpty(),
                    metricCode = it.metricCode.takeIf { code -> type?.metrics?.any { m -> m.code == code } == true }
                        ?: type?.metrics?.firstOrNull { m -> m.default }?.code ?: type?.metrics?.firstOrNull()?.code.orEmpty())
            } else it }
            config
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            mutable.update { if (it.category == current.category) it.copy(config = it.config.copy(loading = false, error = error.dengtaMessage())) else it }
            null
        }
    }

    fun refreshRanking() {
        rankJob?.cancel()
        if (!rankingVisible) return
        rankJob = viewModelScope.launch {
            var loop = false
            while (currentCoroutineContext().isActive && rankingVisible && mutable.value.page == DengtaPage.Ranking) {
                val startedAt = SystemClock.elapsedRealtime()
                fetchRanking(loop = loop && isTodayRanking(mutable.value.date))
                // 配置读取失败时停止自动轮询，等用户点重试再恢复。
                if (mutable.value.config.error != null) break
                if (!isTodayRanking(mutable.value.date)) break
                loop = true
                // 请求串行执行，耗时超过周期时也不叠加请求。
                delay((3_000L - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(0L))
            }
        }
    }

    private fun isTodayRanking(date: String): Boolean = date.isBlank() || dengtaDate(date) == LocalDate.now(ZoneId.of("Asia/Shanghai"))

    private suspend fun fetchRanking(loop: Boolean) {
        // 配置失败时不进入轮询循环，避免每 3 秒重复打配置接口。
        if (ensureConfig() == null) return
        val category = mutable.value.category
        val typeCode = mutable.value.typeCode
        val metricCode = mutable.value.metricCode
        if (typeCode.isBlank() || metricCode.isBlank()) return
        val date = mutable.value.date
        if (!mutable.value.ranking.loaded) mutable.update { it.copy(ranking = it.ranking.copy(loading = true, error = null)) }
        try {
            val data = repository.ranking(category, typeCode, metricCode, date, loop)
            mutable.update { ui ->
                if (ui.category != category || ui.typeCode != typeCode || ui.metricCode != metricCode || ui.date != date ||
                    !rankingVisible || ui.page != DengtaPage.Ranking) return@update ui
                val previous = ui.ranking.data
                val comparable = ui.ranking.loaded && dengtaDate(previous.date) != null &&
                    dengtaDate(previous.date) == dengtaDate(data.date) && isTodayRanking(data.date)
                val oldRows = if (comparable) previous.rows.associateBy { it.media.id } else emptyMap()
                // 只有正片播放榜的 vvCnt 是持续累加的播放次数，其它指标做差不具备可比性。
                val increments = if (data.metricCode != DENGTA_METRIC_PLAY) emptyMap() else data.rows.mapNotNull { row ->
                    val old = oldRows[row.media.id]?.plays?.raw
                    val now = row.plays.raw
                    if (old == null || now == null) null else (now - old).roundToLong().takeIf { it != 0L }?.let { row.media.id to it }
                }.toMap()
                val oldMarket = previous.market.raw
                val newMarket = data.market.raw
                val marketIncrement = if (comparable && oldMarket != null && newMarket != null) (newMarket - oldMarket).roundToLong() else 0L
                ui.copy(ranking = LoadState(data, loaded = true, updatedAt = System.currentTimeMillis()),
                    marketIncrement = marketIncrement, rowIncrements = increments, rankingUpdateId = ui.rankingUpdateId + 1L)
            }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            mutable.update { if (it.category == category && it.typeCode == typeCode && it.metricCode == metricCode && it.date == date &&
                rankingVisible && it.page == DengtaPage.Ranking)
                it.copy(ranking = it.ranking.copy(loading = false, error = error.dengtaMessage()), marketIncrement = 0L, rowIncrements = emptyMap()) else it }
        }
    }

    fun openDetail(media: DengtaMedia) {
        contentMediaId = null
        mutable.update { it.copy(page = DengtaPage.Detail, selected = media) }
        refreshDetail()
    }
    fun openJournal(media: DengtaMedia? = null) {
        val fromDetail = mutable.value.page == DengtaPage.Detail && media != null
        mutable.update { it.copy(page = DengtaPage.Journal, selected = media, journalFromDetail = fromDetail) }
        if (media != null) refreshDetail() else detailJob?.cancel()
    }
    /** 返回 false 表示已到会话首页，由根导航退出灯塔。 */
    fun back(): Boolean {
        val ui = mutable.value
        return when {
            ui.page == DengtaPage.Ranking -> false
            ui.page == DengtaPage.Journal && ui.selected != null && ui.journalFromDetail -> {
                mutable.update { it.copy(page = DengtaPage.Detail, journalFromDetail = false) }; true
            }
            ui.page == DengtaPage.Journal && ui.selected != null -> {
                detailJob?.cancel(); mutable.update { it.copy(selected = null) }; true
            }
            else -> {
                detailJob?.cancel(); mutable.update { it.copy(page = DengtaPage.Ranking, selected = null) }; true
            }
        }
    }

    fun refreshDetail() {
        val media = mutable.value.selected ?: return
        detailJob?.cancel()
        if (contentMediaId != media.id) {
            marketingJob?.cancel(); materialJob?.cancel(); highlightJob?.cancel()
            contentMediaId = media.id
            mutable.update { it.copy(marketing = LoadState(DengtaMarketingContent()), marketingType = "all",
                materials = LoadState(DengtaMaterialContent()), materialTab = "", materialCategory = "all",
                highlights = LoadState(DengtaHighlightContent())) }
        }
        val cached = details[media.id]
        val previous = mutable.value.detail.takeIf { it.data?.media?.id == media.id }
        mutable.update { it.copy(detail = (previous ?: LoadState(cached, loaded = cached != null)).copy(loading = true, error = null), rules = LoadState(emptyList())) }
        detailJob = viewModelScope.launch {
            try {
                val detail = repository.detail(media.id)
                details[media.id] = detail
                if (mutable.value.selected?.id != media.id) return@launch
                mutable.update { it.copy(selected = detail.media, detail = LoadState(detail, loaded = true, updatedAt = System.currentTimeMillis())) }
                if (detail.hasPlayRule) loadRules(media.id)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { if (it.selected?.id == media.id) it.copy(detail = it.detail.copy(loading = false, error = error.dengtaMessage())) else it }
            }
        }
    }
    fun loadMarketing(type: String = mutable.value.marketingType, force: Boolean = false) {
        val current = mutable.value
        val id = current.selected?.id ?: return
        if (!force && current.marketingType == type && (current.marketing.loading || current.marketing.loaded)) return
        marketingJob?.cancel()
        val previous = if (current.marketingType == type) current.marketing.data else DengtaMarketingContent(options = current.marketing.data.options)
        mutable.update { it.copy(marketingType = type, marketing = LoadState(previous, loading = true)) }
        marketingJob = viewModelScope.launch {
            try {
                val data = repository.marketing(id, type)
                mutable.update { if (it.selected?.id == id && it.marketingType == type)
                    it.copy(marketing = LoadState(data, loaded = true)) else it }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { if (it.selected?.id == id && it.marketingType == type)
                    it.copy(marketing = it.marketing.copy(loading = false, error = error.dengtaMessage())) else it }
            }
        }
    }

    fun loadMaterials(tab: String = mutable.value.materialTab, category: String = mutable.value.materialCategory, force: Boolean = false) {
        val current = mutable.value
        val id = current.selected?.id ?: return
        if (!force && current.materialTab == tab && current.materialCategory == category && (current.materials.loading || current.materials.loaded)) return
        materialJob?.cancel()
        val previous = if (current.materialTab == tab && current.materialCategory == category) current.materials.data
            else DengtaMaterialContent(options = if (current.materialTab == tab) current.materials.data.options else emptyList(),
                tabs = current.materials.data.tabs, selectedTab = tab)
        mutable.update { it.copy(materialTab = tab, materialCategory = category, materials = LoadState(previous, loading = true)) }
        materialJob = viewModelScope.launch {
            try {
                val data = repository.materials(id, tab, category)
                mutable.update { if (it.selected?.id == id && it.materialTab == tab && it.materialCategory == category)
                    it.copy(materialTab = data.selectedTab, materials = LoadState(data, loaded = true)) else it }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { if (it.selected?.id == id && it.materialTab == tab && it.materialCategory == category)
                    it.copy(materials = it.materials.copy(loading = false, error = error.dengtaMessage())) else it }
            }
        }
    }

    fun loadHighlights(more: Boolean = false) {
        val current = mutable.value
        val detail = current.detail.data ?: return
        if (current.highlights.loading || (!more && current.highlights.loaded)) return
        val cursor = if (more) current.highlights.data.nextTime else ""
        if (more && cursor.isBlank()) return
        highlightJob?.cancel()
        mutable.update { it.copy(highlights = it.highlights.copy(loading = true, error = null)) }
        highlightJob = viewModelScope.launch {
            try {
                val data = repository.highlights(detail.media.id, detail.highlightType, cursor)
                mutable.update {
                    if (it.selected?.id != detail.media.id) it else {
                        val items = (if (more) it.highlights.data.items + data.items else data.items).distinctBy { item -> item.timestamp to item.html }
                        val next = data.nextTime.takeIf { data.items.isNotEmpty() && it != cursor }.orEmpty()
                        it.copy(highlights = LoadState(DengtaHighlightContent(items, next), loaded = true))
                    }
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { if (it.selected?.id == detail.media.id)
                    it.copy(highlights = it.highlights.copy(loading = false, error = error.dengtaMessage())) else it }
            }
        }
    }

    fun retryRules() {
        val id = mutable.value.selected?.id ?: return
        viewModelScope.launch { loadRules(id) }
    }
    private suspend fun loadRules(id: String) {
        if (mutable.value.selected?.id != id) return
        mutable.update { it.copy(rules = it.rules.copy(loading = true, error = null)) }
        try {
            val rules = repository.playRules(id)
            mutable.update { if (it.selected?.id == id) it.copy(rules = LoadState(rules, loaded = true)) else it }
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) {
            mutable.update { if (it.selected?.id == id) it.copy(rules = it.rules.copy(loading = false, error = error.dengtaMessage())) else it }
        }
    }

    fun loadJournal() {
        viewModelScope.launch {
            journalMutex.withLock {
                mutable.update { it.copy(journalLoading = true, journalError = null) }
                try { val data = store.read(); mutable.update { it.copy(journal = data, journalLoading = false) } }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { mutable.update { it.copy(journalLoading = false, journalError = error.dengtaMessage()) } }
            }
        }
    }
    fun toggleFollow(media: DengtaMedia) = updateJournal("追剧状态已更新") { data ->
        val existing = data.media.firstOrNull { it.media.id == media.id }
        val next = existing?.copy(media = media, following = !existing.following) ?: DengtaFollow(media)
        data.copy(media = listOf(next) + data.media.filterNot { it.media.id == media.id })
    }
    fun record(episode: Int, note: String, onSaved: () -> Unit) {
        val media = mutable.value.selected ?: return
        if (episode <= 0 || (media.episodeCount != null && episode > media.episodeCount)) {
            events.trySend("请填写有效的观看集数"); return
        }
        updateJournal("观看记录已保存", onSaved) { data ->
            val existing = data.media.firstOrNull { it.media.id == media.id }
            val next = existing?.copy(media = media, watched = episode) ?: DengtaFollow(media, watched = episode)
            data.copy(media = listOf(next) + data.media.filterNot { it.media.id == media.id },
                logs = listOf(DengtaWatchLog(UUID.randomUUID().toString(), media.id, episode, note.trim().take(160), System.currentTimeMillis())) + data.logs)
        }
    }
    private fun updateJournal(message: String, onSaved: () -> Unit = {}, transform: (DengtaJournalData) -> DengtaJournalData) {
        if (mutable.value.journalBusy || mutable.value.journalLoading) return
        mutable.update { it.copy(journalBusy = true) }
        viewModelScope.launch {
            try {
                journalMutex.withLock {
                    val next = store.update(transform)
                    mutable.update { it.copy(journal = next, journalError = null) }
                }
                onSaved(); events.send(message)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(journalError = error.dengtaMessage()) }; events.send(error.dengtaMessage()) }
            finally { mutable.update { it.copy(journalBusy = false) } }
        }
    }
}

private fun Exception.dengtaMessage(): String = when (this) {
    is SocketTimeoutException -> "灯塔请求超时，请稍后重试"
    is IOException -> "无法连接灯塔，请检查网络后重试"
    else -> message?.take(240) ?: "灯塔数据加载失败，请重试"
}
