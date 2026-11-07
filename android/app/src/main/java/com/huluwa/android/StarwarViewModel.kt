package com.huluwa.android

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.huluwa.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonArray

/** 星球大战的页面阶段，对应原版的「大厅 → 房间 → 等待聊天室 → 对战 → 结算」 */
enum class StarwarStage { Lobby, Rooms, Room, Battle, Result }

data class StarwarState(
    val stage: StarwarStage = StarwarStage.Lobby,
    val loading: Boolean = false,
    val error: String? = null,
    val toast: String? = null,

    // 大厅
    val profile: StarwarProfile = StarwarProfile(),
    val zones: List<StarwarZone> = emptyList(),
    val planets: List<StarwarPlanet> = emptyList(),
    val ranks: List<StarwarRankTitle> = emptyList(),
    val texts: StarwarTexts = StarwarTexts(),
    val help: StarwarHelp = StarwarHelp(),
    val chatNotice: String = "",
    val online: Int = 0,

    // 房间列表
    val zone: String = "multi",
    val zoneTitle: String = "",
    val roomPage: StarwarRoomPage? = null,
    val page: Int = 0,

    // 房间
    val room: StarwarRoom? = null,
    val chat: StarwarChatState = StarwarChatState(),
    val chatInput: String = "",
    val chatOpen: Boolean = false,
    val phraseSheetOpen: Boolean = false,
    val nextChatAt: Long = 0,

    // 对战
    val world: StarwarWorld? = null,
    val standings: List<StarwarStanding> = emptyList(),
    val connected: Boolean = false,
    val result: List<StarwarStanding> = emptyList(),
    val matchId: String = "",

    // 弹窗
    val createRoomOpen: Boolean = false,
    val passwordPromptRoom: String? = null,
    val helpOpen: Boolean = false,
    val shop: StarwarShop? = null,
    val shopOpen: Boolean = false,
    val hangarOpen: Boolean = false,
    val hangar: List<Map<String, String>> = emptyList(),
    val rankingOpen: Boolean = false,
    val ranking: List<StarwarRankItem> = emptyList(),
    val opponent: Map<String, String>? = null,
    val statusOpen: Boolean = false,
) {
    val roomId: String get() = room?.id.orEmpty()
    val isOwner: Boolean get() = room?.owner == profile.user
    val canQuickStart: Boolean get() = room?.memberList?.size ?: 0 >= 2
    val mySurvivedMs: Long get() = world?.players?.firstOrNull { it.user == profile.user }?.survived ?: 0
}

class StarwarViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as HuluwaApplication
    private val repository = StarwarRepository(app.repository.api)
    private val realtime = StarwarRealtime(app.repository.api)
    private val mutable = MutableStateFlow(StarwarState())
    val state = mutable.asStateFlow()

    private var realtimeJob: Job? = null
    private var roomPollJob: Job? = null

    init {
        loadLobby()
    }

    fun dismissToast() = mutable.update { it.copy(toast = null) }
    fun dismissError() = mutable.update { it.copy(error = null) }

    /** 轻提示（提示语多为原版原文，如「即将推出,敬请期待!」） */
    fun showToast(text: String) = mutable.update { it.copy(toast = text) }

    // ---------------------------------------------------------------- 大厅

    fun loadLobby() {
        if (mutable.value.loading && mutable.value.zones.isNotEmpty()) return
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val raw = repository.lobby()
                val profileRaw = raw["profile"] as? kotlinx.serialization.json.JsonObject
                mutable.update { state ->
                    state.copy(
                        loading = false,
                        profile = profileRaw?.let(::parseStarwarProfile) ?: state.profile,
                        zones = raw["zones"]?.jsonArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map {
                            StarwarZone(
                                key = it.str("key"), title = it.str("title"), capacity = it.int("capacity"),
                                costCoin = it.int("costCoin"), costCrystal = it.int("costCrystal"))
                        } ?: emptyList(),
                        planets = raw["planets"]?.jsonArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map {
                            StarwarPlanet(it.str("id"), it.str("name"), it.str("icon"),
                                it.str("unlockText"), it.int("unlockLevel"), it.boolean("locked"))
                        } ?: emptyList(),
                        ranks = raw["ranks"]?.jsonArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map {
                            StarwarRankTitle(it.str("name"), it.int("minLevel"))
                        } ?: emptyList(),
                        texts = StarwarTexts(raw["texts"] as? kotlinx.serialization.json.JsonObject
                            ?: kotlinx.serialization.json.JsonObject(emptyMap())),
                        help = parseHelp(raw["help"] as? kotlinx.serialization.json.JsonObject),
                        chatNotice = raw.str("chatNotice"),
                        online = raw.int("online"),
                    )
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    private fun parseHelp(raw: kotlinx.serialization.json.JsonObject?): StarwarHelp {
        if (raw == null) return StarwarHelp()
        return StarwarHelp(
            helpTitle = raw.str("helpTitle"),
            helpLines = raw["helpLines"]?.jsonArray?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } ?: emptyList(),
            introTitle = raw.str("introTitle"), intro = raw.str("intro"),
            howToTitle = raw.str("howToTitle"), howTo = raw.str("howTo"),
        )
    }

    fun showHelp() = mutable.update { it.copy(helpOpen = true) }
    fun hideHelp() = mutable.update { it.copy(helpOpen = false) }

    // ---------------------------------------------------------------- 房间列表

    fun openZone(zone: String, title: String) {
        mutable.update { it.copy(stage = StarwarStage.Rooms, zone = zone, zoneTitle = title, page = 0) }
        loadRooms(zone, 0)
    }

    fun loadRooms(zone: String = mutable.value.zone, page: Int = mutable.value.page) {
        mutable.update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            try {
                val raw = repository.rooms(zone, page)
                mutable.update {
                    it.copy(loading = false, zone = zone, page = page,
                        zoneTitle = raw.str("zoneTitle"),
                        roomPage = parseRoomPage(raw))
                }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(loading = false, error = error.userMessage()) }
            }
        }
    }

    private fun parseRoomPage(raw: kotlinx.serialization.json.JsonObject): StarwarRoomPage = StarwarRoomPage(
        zone = raw.str("zone"), zoneTitle = raw.str("zoneTitle"), page = raw.int("page"),
        pages = raw.int("pages", 1), total = raw.int("total"), capacity = raw.int("capacity", 6),
        rooms = raw["rooms"]?.jsonArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
            ?.map(::parseStarwarRoom) ?: emptyList(),
        pager = raw["pager"]?.jsonArray?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } ?: emptyList(),
        notice = raw.str("notice").ifBlank { null },
    )

    fun page(delta: Int) {
        val current = mutable.value.roomPage ?: return
        val target = (mutable.value.page + delta).coerceIn(0, maxOf(0, current.pages - 1))
        if (target == mutable.value.page) {
            val text = if (delta < 0) mutable.value.texts.text("ui", "alreadyFirst", "已经是第一页!")
                       else mutable.value.texts.text("ui", "alreadyLast", "已经是最后一页!")
            mutable.update { it.copy(toast = text) }
            return
        }
        loadRooms(mutable.value.zone, target)
    }

    fun refreshRooms() = loadRooms(mutable.value.zone, mutable.value.page)

    /** 从专区房间列表回到大厅 */
    fun backToLobby() = mutable.update {
        it.copy(stage = StarwarStage.Lobby, roomPage = null, page = 0)
    }

    /** 「首页」：跳到第 0 页 */
    fun firstPage() {
        if (mutable.value.page == 0) {
            mutable.update { it.copy(toast = "已经是第一页!") }
            return
        }
        loadRooms(mutable.value.zone, 0)
    }

    /** 「末页」：跳到最后一页 */
    fun lastPage() {
        val pages = mutable.value.roomPage?.pages ?: 1
        val last = maxOf(0, pages - 1)
        if (mutable.value.page >= last) {
            mutable.update { it.copy(toast = "已经是最后一页!") }
            return
        }
        loadRooms(mutable.value.zone, last)
    }

    // ---------------------------------------------------------------- 创建 / 进入房间

    fun showCreateRoom() = mutable.update { it.copy(createRoomOpen = true) }
    fun hideCreateRoom() = mutable.update { it.copy(createRoomOpen = false) }

    fun createRoom(name: String, mapId: Int, items: Boolean, costMode: Int, password: String) {
        viewModelScope.launch {
            try {
                val raw = repository.createRoom(mutable.value.zone, name, mapId, items, costMode, password)
                val room = parseStarwarRoom(raw)
                mutable.update { it.copy(createRoomOpen = false, room = room, stage = StarwarStage.Room) }
                connectRoom(room.id)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = error.userMessage()) } }
        }
    }

    /** 点击房间：有密码则弹输入框，否则直接进 */
    fun tapRoom(room: StarwarRoom) {
        if (room.hasPassword) {
            mutable.update { it.copy(passwordPromptRoom = room.id) }
        } else {
            enterRoom(room.id, null)
        }
    }

    fun dismissPasswordPrompt() = mutable.update { it.copy(passwordPromptRoom = null) }

    fun enterRoomWithPassword(password: String) {
        val roomId = mutable.value.passwordPromptRoom ?: return
        mutable.update { it.copy(passwordPromptRoom = null) }
        enterRoom(roomId, password)
    }

    fun enterRoom(roomId: String, password: String?) {
        viewModelScope.launch {
            try {
                val raw = repository.enter(roomId, password)
                val room = parseStarwarRoom(raw)
                mutable.update { it.copy(room = room, stage = StarwarStage.Room) }
                connectRoom(roomId)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                mutable.update { it.copy(error = error.userMessage(), passwordPromptRoom = null) }
            }
        }
    }

    fun leaveRoom() {
        val roomId = mutable.value.roomId
        disconnectRoom()
        if (roomId.isBlank()) {
            backToRooms()
            return
        }
        viewModelScope.launch {
            runCatching { repository.leave(roomId) }
            backToRooms()
        }
    }

    private fun backToRooms() {
        mutable.update { it.copy(stage = StarwarStage.Rooms, room = null, world = null, standings = emptyList()) }
        loadRooms()
    }

    fun kick(target: String) {
        val roomId = mutable.value.roomId
        viewModelScope.launch {
            try {
                mutable.update { it.copy(room = parseStarwarRoom(repository.kick(roomId, target))) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun toggleReady() {
        val room = mutable.value.room ?: return
        val me = room.memberList.firstOrNull { it.user == mutable.value.profile.user }
        viewModelScope.launch {
            try {
                mutable.update { it.copy(room = parseStarwarRoom(repository.ready(room.id, me?.ready != true))) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun quickStart() {
        val roomId = mutable.value.roomId
        viewModelScope.launch {
            try {
                mutable.update { it.copy(room = parseStarwarRoom(repository.quickStart(roomId))) }
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { mutable.update { it.copy(error = error.userMessage()) } }
        }
    }

    // ---------------------------------------------------------------- 实时连接

    private fun connectRoom(roomId: String) {
        disconnectRoom()
        loadChat(roomId, 0)
        realtimeJob = viewModelScope.launch {
            realtime.events(roomId).collect { event ->
                when (event) {
                    is StarwarEvent.Connected -> mutable.update { it.copy(connected = true) }
                    is StarwarEvent.Disconnected -> mutable.update { it.copy(connected = false) }
                    is StarwarEvent.World -> {
                        val current = mutable.value
                        // 房间等待阶段收到世界帧 = 已经开局
                        mutable.update {
                            it.copy(
                                connected = true,
                                world = event.world,
                                standings = event.standings,
                                stage = if (event.world.settled) StarwarStage.Result else StarwarStage.Battle,
                                result = if (event.world.settled) event.standings else it.result,
                                matchId = event.world.matchId.ifBlank { it.matchId },
                            )
                        }
                        if (current.stage == StarwarStage.Room) {
                            // 刚开局：补一次房间详情拿结算信息
                            loadRoom(roomId)
                        }
                    }
                    is StarwarEvent.Settled -> {
                        mutable.update {
                            it.copy(stage = StarwarStage.Result, result = event.standings,
                                standings = event.standings, matchId = event.matchId)
                        }
                    }
                    is StarwarEvent.Chat -> {
                        mutable.update { state ->
                            val merged = (state.chat.messages + event.message).distinctBy { it.id }
                                .sortedBy { it.id }
                            state.copy(chat = state.chat.copy(messages = merged))
                        }
                    }
                    is StarwarEvent.Room -> {
                        val room = event.room
                        val stage = when (room.status) {
                            "playing" -> StarwarStage.Battle
                            "settled" -> StarwarStage.Result
                            else -> StarwarStage.Room
                        }
                        mutable.update { it.copy(room = room, stage = if (it.stage == StarwarStage.Battle) it.stage else stage) }
                    }
                }
            }
        }
    }

    private fun disconnectRoom() {
        realtimeJob?.cancel()
        realtimeJob = null
        roomPollJob?.cancel()
        roomPollJob = null
    }

    fun loadRoom(roomId: String) {
        viewModelScope.launch {
            runCatching { repository.room(roomId) }.onSuccess { raw ->
                val room = parseStarwarRoom(raw)
                mutable.update { it.copy(room = room) }
            }
        }
    }

    // ---------------------------------------------------------------- 等待聊天室

    fun loadChat(roomId: String, afterId: Long) {
        viewModelScope.launch {
            try {
                val raw = repository.chat(roomId, afterId)
                val messages = raw["messages"]?.jsonArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }
                    ?.map(::parseStarwarChatMessage) ?: emptyList()
                mutable.update { state ->
                    val merged = (if (afterId == 0L) messages else (state.chat.messages + messages))
                        .distinctBy { it.id }.sortedBy { it.id }
                    state.copy(
                        chat = state.chat.copy(
                            messages = merged,
                            phrases = raw["phrases"]?.jsonArray?.mapNotNull { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull } ?: state.chat.phrases,
                            notice = raw.str("notice"),
                            intervalMs = raw["intervalMs"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 10_000,
                            silencedFor = raw["silencedFor"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0,
                            nextAllowedAt = raw["nextAllowedAt"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0,
                        )
                    )
                }
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) { /* 聊天历史加载失败不打断房间流程 */ }
        }
    }

    fun setChatInput(value: String) = mutable.update { it.copy(chatInput = value.take(120)) }
    fun openChat() = mutable.update { it.copy(chatOpen = true) }
    fun closeChat() = mutable.update { it.copy(chatOpen = false) }
    fun togglePhraseSheet() = mutable.update { it.copy(phraseSheetOpen = !it.phraseSheetOpen) }

    fun sendChat(content: String = mutable.value.chatInput, kind: String = "text") {
        val roomId = mutable.value.roomId
        val text = content.trim()
        if (roomId.isBlank() || text.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now < mutable.value.chat.nextAllowedAt) {
            // 原文「聊天间隔10秒,请勿刷屏!」
            mutable.update { it.copy(toast = mutable.value.texts.text("chat", "interval", "聊天间隔10秒,请勿刷屏!")) }
            return
        }
        mutable.update { it.copy(chatInput = "") }
        viewModelScope.launch {
            try {
                val raw = repository.say(roomId, text, kind)
                val next = raw["nextAllowedAt"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toLongOrNull() } ?: 0
                mutable.update { it.copy(chat = it.chat.copy(nextAllowedAt = next)) }
                loadChat(roomId, mutable.value.chat.messages.lastOrNull()?.id ?: 0)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                val message = error.userMessage()
                mutable.update { it.copy(toast = if (message.contains("10秒")) message
                        else mutable.value.texts.text("chat", "sendFailed", "发送聊天消息失败")) }
            }
        }
    }

    // ---------------------------------------------------------------- 对战

    /** 进入对战：确保世界已开 */
    fun ensureMatch() {
        val roomId = mutable.value.roomId
        if (roomId.isBlank() || mutable.value.world != null) return
        viewModelScope.launch {
            runCatching { repository.startMatch(roomId) }
        }
    }

    /** 方向键输入（按住即持续发送，由 UI 的 pointerInput 驱动） */
    fun input(dx: Int, dy: Int, item: Boolean = false) {
        val roomId = mutable.value.roomId
        if (roomId.isBlank()) return
        viewModelScope.launch { runCatching { realtime.input(roomId, dx, dy, item) } }
    }

    fun useItem() = input(0, 0, item = true)

    /** 再来一局：回到房间等待状态并让房主重新开始 */
    fun playAgain() {
        val roomId = mutable.value.roomId
        mutable.update { it.copy(stage = StarwarStage.Room, world = null, standings = emptyList(), result = emptyList()) }
        if (roomId.isNotBlank()) {
            loadRoom(roomId)
            viewModelScope.launch { runCatching { repository.ready(roomId, false) } }
        }
    }

    /** 退出结算回到大厅 */
    fun exitBattle() = leaveRoom()

    /** 结算页「返回」：只回房间等待区，不退出房间 */
    fun backToRoomsFromResult() {
        mutable.update {
            it.copy(stage = StarwarStage.Room, world = null, standings = emptyList(),
                result = emptyList(), matchId = "")
        }
        if (mutable.value.roomId.isNotBlank()) loadRoom(mutable.value.roomId)
    }

    // ---------------------------------------------------------------- 商店 / 机库 / 排行 / 资料

    fun openShop() {
        viewModelScope.launch {
            runCatching { repository.shop() }.onSuccess { raw ->
                mutable.update { it.copy(shop = parseShop(raw), shopOpen = true) }
            }
        }
    }
    fun closeShop() = mutable.update { it.copy(shopOpen = false) }

    fun buy(type: String, id: String) {
        viewModelScope.launch {
            runCatching { repository.buy(type, id) }.onSuccess { raw ->
                mutable.update { it.copy(shop = parseShop(raw)) }
                loadLobby()
            }.onFailure { error -> mutable.update { it.copy(error = error.userMessage()) } }
        }
    }

    fun openHangar() {
        viewModelScope.launch {
            runCatching { repository.hangar() }.onSuccess { raw ->
                val rows = raw.jsonArray.mapNotNull { item ->
                    (item as? kotlinx.serialization.json.JsonObject)?.let {
                        mapOf("type" to it.str("type"), "id" to it.str("id"), "source" to it.str("source"))
                    }
                }
                mutable.update { it.copy(hangar = rows, hangarOpen = true) }
            }
        }
    }
    fun closeHangar() = mutable.update { it.copy(hangarOpen = false) }

    fun equip(type: String, id: String) {
        viewModelScope.launch {
            runCatching { repository.equip(type, id) }
                .onSuccess { raw -> mutable.update { it.copy(profile = parseStarwarProfile(raw)) } }
                .onFailure { error -> mutable.update { it.copy(error = error.userMessage()) } }
            loadLobby()
        }
    }

    fun openRanking() {
        viewModelScope.launch {
            runCatching { repository.ranking("score", 0) }.onSuccess { raw ->
                val items = raw["items"]?.jsonArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map {
                    StarwarRankItem(it.int("ranking"), it.str("user"), it.str("nick"),
                        it.int("totalScore"), it.int("bestScore"), it.int("matches"), it.int("wins"))
                } ?: emptyList()
                mutable.update { it.copy(ranking = items, rankingOpen = true) }
            }
        }
    }
    fun closeRanking() = mutable.update { it.copy(rankingOpen = false) }

    fun openOpponent(name: String) {
        viewModelScope.launch {
            runCatching { repository.opponent(name) }.onSuccess { raw ->
                mutable.update {
                    it.copy(opponent = mapOf(
                        "user" to raw.str("user"), "nick" to raw.str("nick"),
                        "level" to raw.str("level"), "rank" to raw.str("rank"),
                        "wins" to raw.str("wins"), "losses" to raw.str("losses"),
                        "winRate" to raw.str("winRate"), "plane" to raw.str("plane")))
                }
            }.onFailure { mutable.update { it.copy(toast = mutable.value.texts.text("social", "opInfoFail", "查看对方资料失败")) } }
        }
    }
    fun closeOpponent() = mutable.update { it.copy(opponent = null) }

    /** 「我的状态」：等级 / 胜率 / 飞机（原文三行标签） */
    fun openStatus() = mutable.update { it.copy(statusOpen = true) }
    fun closeStatus() = mutable.update { it.copy(statusOpen = false) }

    fun addFriend(name: String) {
        viewModelScope.launch {
            runCatching { repository.addFriend(name) }
                .onSuccess { mutable.update { it.copy(toast = it.texts.text("social", "addOk", "添加好友成功")) } }
                .onFailure { mutable.update { it.copy(toast = it.texts.text("social", "addFail", "添加好友失败")) } }
        }
    }

    override fun onCleared() {
        disconnectRoom()
        super.onCleared()
    }
}

// ---------------------------------------------------------------- JSON 小工具

private fun kotlinx.serialization.json.JsonObject.str(key: String, fallback: String = ""): String =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull ?: fallback
private fun kotlinx.serialization.json.JsonObject.int(key: String, fallback: Int = 0): Int =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: fallback
private fun kotlinx.serialization.json.JsonObject.boolean(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.booleanOrNull ?: fallback

private fun parseShop(raw: kotlinx.serialization.json.JsonObject): StarwarShop = StarwarShop(
    title = raw.str("title"),
    items = raw["items"]?.jsonArray?.mapNotNull { it as? kotlinx.serialization.json.JsonObject }?.map {
        StarwarShopItem(it.str("type"), it.str("id"), it.str("name"), it.int("gold"),
            it.int("crystal"), it.str("desc"), it.boolean("owned"))
    } ?: emptyList(),
    gold = raw.int("gold"), crystal = raw.int("crystal"),
)
