package com.huluwa.android.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import java.util.concurrent.atomic.AtomicLong

/** 星球大战会话（是男人就撑20秒 网络对战版）的会话类型常量。 */
const val STARWAR_SESSION_KIND = "starwar"

// ---------------------------------------------------------------- 模型

data class StarwarProfile(
    val user: String = "", val nick: String = "", val level: Int = 1, val rank: String = "",
    val exp: Int = 0, val wins: Int = 0, val losses: Int = 0, val matches: Int = 0,
    val winRate: Double = 0.0, val gold: Int = 0, val crystal: Int = 0,
    val plane: String = "plane", val engine: String = "engine", val silencedFor: Long = 0,
)

data class StarwarZone(val key: String, val title: String, val capacity: Int,
                       val costCoin: Int, val costCrystal: Int)

data class StarwarPlanet(val id: Int, val name: String, val icon: String,
                         val unlockText: String, val unlockLevel: Int, val locked: Boolean)

data class StarwarRankTitle(val name: String, val minLevel: Int)

data class StarwarWorldPlayer(
    val user: String = "", val nick: String = "", val plane: String = "plane",
    val x: Float = 0f, val y: Float = 0f, val hp: Int = 100, val score: Int = 0,
    val alive: Boolean = true, val shield: Boolean = false, val survived: Long = 0,
    val reachedTarget: Boolean = false, val shells: Int = 3, val surround: Boolean = false,
)

data class StarwarBullet(val x: Float, val y: Float)
data class StarwarProp(val x: Float, val y: Float, val kind: String)
data class StarwarStone(val x: Float, val y: Float, val kind: Int)
data class StarwarMissile(val x: Float, val y: Float)

data class StarwarWorld(
    val matchId: String = "", val tick: Int = 0, val elapsed: Long = 0,
    val width: Int = 240, val height: Int = 320,
    val players: List<StarwarWorldPlayer> = emptyList(),
    val bullets: List<StarwarBullet> = emptyList(),
    val props: List<StarwarProp> = emptyList(),
    val stones: List<StarwarStone> = emptyList(),
    val missiles: List<StarwarMissile> = emptyList(),
    val settled: Boolean = false,
)

data class StarwarRoomMember(
    val user: String = "", val nick: String = "", val seat: Int = 0, val team: Int = 0,
    val ready: Boolean = false, val alive: Boolean = true, val hp: Int = 100,
    val score: Int = 0, val plane: String = "plane", val level: Int = 1,
    val rank: String = "", val winRate: Double = 0.0, val isOwner: Boolean = false,
)

data class StarwarRoom(
    val id: String = "", val zone: String = "", val name: String = "", val owner: String = "",
    val mapId: Int = 2, val mapName: String = "", val itemMode: Int = 0,
    // 收费方式：0 免费 / 1 K币 / 2 水晶，由服务端 roomView 的 costMode 下发
    val costMode: Int = 0,
    val hasPassword: Boolean = false, val status: String = "waiting",
    val members: Int = 0, val capacity: Int = 6, val round: Int = 1,
    val memberList: List<StarwarRoomMember> = emptyList(),
    val countdownEnd: Long = 0, val passwordHint: String = "", val isOwner: Boolean = false,
    val world: StarwarWorld? = null,
)

data class StarwarChatMessage(
    val id: Long = 0, val user: String = "", val nick: String = "", val content: String = "",
    val kind: String = "text", val at: Long = 0, val mine: Boolean = false,
)

data class StarwarRoomPage(
    val zone: String = "", val zoneTitle: String = "", val page: Int = 0, val pages: Int = 1,
    val total: Int = 0, val capacity: Int = 6, val rooms: List<StarwarRoom> = emptyList(),
    val pager: List<String> = emptyList(), val notice: String? = null,
)

data class StarwarRankItem(
    val ranking: Int = 0, val user: String = "", val nick: String = "",
    val totalScore: Int = 0, val bestScore: Int = 0, val matches: Int = 0, val wins: Int = 0,
)

data class StarwarRanking(
    val title: String = "", val page: Int = 0, val pages: Int = 1,
    val total: Int = 0, val items: List<StarwarRankItem> = emptyList(),
)

data class StarwarStanding(
    val ranking: Int = 0, val username: String = "", val nickname: String = "",
    val score: Int = 0, val survivedMs: Long = 0, val hp: Int = 0,
    val survivedTarget: Boolean = false,
)

data class StarwarChatState(
    val messages: List<StarwarChatMessage> = emptyList(), val phrases: List<String> = emptyList(),
    val notice: String = "", val intervalMs: Long = 10_000, val silencedFor: Long = 0,
    val nextAllowedAt: Long = 0,
)

data class StarwarHelp(
    val helpTitle: String = "", val helpLines: List<String> = emptyList(),
    val introTitle: String = "", val intro: String = "",
    val howToTitle: String = "", val howTo: String = "",
)

data class StarwarShopItem(
    val type: String = "", val id: String = "", val name: String = "",
    val gold: Int = 0, val crystal: Int = 0, val desc: String = "", val owned: Boolean = false,
)

data class StarwarShop(
    val title: String = "", val items: List<StarwarShopItem> = emptyList(),
    val gold: Int = 0, val crystal: Int = 0,
)

/** 服务端下发的全部原文文案（1:1 还原当年措辞）。 */
data class StarwarTexts(
    val raw: JsonObject = JsonObject(emptyMap()),
) {
    fun group(name: String): JsonObject = raw[name]?.jsonObject ?: JsonObject(emptyMap())
    fun text(group: String, key: String, fallback: String = ""): String {
        val value = this.group(group)[key]
        return if (value is JsonPrimitive && value.isString) value.content else fallback
    }
    val pager: List<String> get() = raw["pager"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
    val defaultPhrases: List<String> get() = raw["phrases"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull } ?: emptyList()
}

// ---------------------------------------------------------------- 解析

private fun JsonObject.int(key: String, fallback: Int = 0): Int =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: fallback
private fun JsonObject.long(key: String, fallback: Long = 0L): Long =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: fallback
private fun JsonObject.double(key: String, fallback: Double = 0.0): Double =
    (this[key] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull() ?: fallback
private fun JsonObject.str(key: String, fallback: String = ""): String =
    (this[key] as? JsonPrimitive)?.contentOrNull ?: fallback
private fun JsonObject.bool(key: String, fallback: Boolean = false): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: fallback

fun parseStarwarProfile(raw: JsonObject): StarwarProfile = StarwarProfile(
    user = raw.str("username"), nick = raw.str("nickname"), level = raw.int("level", 1),
    rank = raw.str("rank"), exp = raw.int("exp"), wins = raw.int("wins"), losses = raw.int("losses"),
    matches = raw.int("matches"), winRate = raw.double("winRate"), gold = raw.int("gold"),
    crystal = raw.int("crystal"), plane = raw.str("plane", "plane"),
    engine = raw.str("equip_engine", "engine"), silencedFor = raw.long("silencedFor"),
)

fun parseStarwarWorld(raw: JsonObject): StarwarWorld = StarwarWorld(
    matchId = raw.str("matchId"), tick = raw.int("tick"), elapsed = raw.long("elapsed"),
    width = raw.int("width", 240), height = raw.int("height", 320),
    players = raw["players"]?.jsonArray?.mapNotNull { it as? JsonObject }?.map { p ->
        StarwarWorldPlayer(
            user = p.str("user"), nick = p.str("nick"), plane = p.str("plane", "plane"),
            x = p.double("x").toFloat(), y = p.double("y").toFloat(), hp = p.int("hp"),
            score = p.int("score"), alive = p.bool("alive", true), shield = p.bool("shield"),
            survived = p.long("survived"), reachedTarget = p.bool("reachedTarget"),
            shells = p.int("shells", 3), surround = p.bool("surround"),
        )
    } ?: emptyList(),
    bullets = raw["bullets"]?.jsonArray?.mapNotNull { it as? JsonObject }?.map {
        StarwarBullet(it.double("x").toFloat(), it.double("y").toFloat())
    } ?: emptyList(),
    props = raw["props"]?.jsonArray?.mapNotNull { it as? JsonObject }?.map {
        StarwarProp(it.double("x").toFloat(), it.double("y").toFloat(), it.str("kind"))
    } ?: emptyList(),
    stones = raw["stones"]?.jsonArray?.mapNotNull { it as? JsonObject }?.map {
        StarwarStone(it.double("x").toFloat(), it.double("y").toFloat(), it.int("kind", 1))
    } ?: emptyList(),
    missiles = raw["missiles"]?.jsonArray?.mapNotNull { it as? JsonObject }?.map {
        StarwarMissile(it.double("x").toFloat(), it.double("y").toFloat())
    } ?: emptyList(),
    settled = raw.bool("settled"),
)

private fun parseStarwarRoomMember(raw: JsonObject) = StarwarRoomMember(
    user = raw.str("user"), nick = raw.str("nick"), seat = raw.int("seat"), team = raw.int("team"),
    ready = raw.bool("ready"), alive = raw.bool("alive", true), hp = raw.int("hp"),
    score = raw.int("score"), plane = raw.str("plane", "plane"), level = raw.int("level", 1),
    rank = raw.str("rank"), winRate = raw.double("winRate"), isOwner = raw.bool("isOwner"),
)

fun parseStarwarRoom(raw: JsonObject): StarwarRoom = StarwarRoom(
    id = raw.str("id"), zone = raw.str("zone"), name = raw.str("name"), owner = raw.str("owner"),
    mapId = raw.int("mapId", 2), mapName = raw.str("mapName"), itemMode = raw.int("itemMode"),
    costMode = raw.int("costMode"),
    hasPassword = raw.bool("hasPassword"), status = raw.str("status", "waiting"),
    members = raw.int("members"), capacity = raw.int("capacity", 6), round = raw.int("round", 1),
    memberList = raw["memberList"]?.jsonArray?.mapNotNull { it as? JsonObject }
        ?.map(::parseStarwarRoomMember) ?: emptyList(),
    countdownEnd = raw.long("countdownEnd"), passwordHint = raw.str("passwordHint"),
    isOwner = raw.bool("isOwner"),
    world = (raw["world"] as? JsonObject)?.takeIf { it.isNotEmpty() }?.let(::parseStarwarWorld),
)

fun parseStarwarChatMessage(raw: JsonObject) = StarwarChatMessage(
    id = raw.long("id"), user = raw.str("user"), nick = raw.str("nick"),
    content = raw.str("content"), kind = raw.str("kind", "text"),
    at = raw.long("at"), mine = raw.bool("mine"),
)

fun parseStarwarStanding(raw: JsonObject) = StarwarStanding(
    ranking = raw.int("ranking"), username = raw.str("username"), nickname = raw.str("nickname"),
    score = raw.int("score"), survivedMs = raw.long("survivedMs"), hp = raw.int("hp"),
    survivedTarget = raw.bool("survivedTarget"),
)

// ---------------------------------------------------------------- 仓库

/** 星球大战接口封装。实时帧走 /ws/starwar，见 [StarwarRealtime]。 */
class StarwarRepository(private val api: ApiClient) {

    suspend fun lobby() = api.get("starwar", "lobby").asObject()
    suspend fun texts() = api.get("starwar", "texts").asObject()
    suspend fun help() = api.get("starwar", "help").asObject()
    suspend fun profile() = api.get("starwar", "profile").asObject()
    suspend fun status() = api.get("starwar", "status").asObject()

    suspend fun saveNickname(nickname: String) =
        api.post("starwar", "profile", "nickname", body = jsonBody { put("nickname", nickname) }).asObject()

    suspend fun rooms(zone: String, page: Int) =
        api.get("starwar", "rooms", query = mapOf("zone" to zone, "page" to "$page")).asObject()

    suspend fun createRoom(zone: String, name: String, mapId: Int, items: Boolean,
                           costMode: Int, password: String) =
        api.post("starwar", "rooms", body = jsonBody {
            put("zone", zone); put("name", name); put("mapId", mapId); put("items", items)
            put("costMode", costMode); put("password", password)
        }).asObject()

    suspend fun room(id: String) = api.get("starwar", "rooms", id).asObject()

    suspend fun enter(id: String, password: String?) =
        api.post("starwar", "rooms", id, "enter", body = jsonBody { put("password", password ?: "") }).asObject()

    suspend fun leave(id: String) = api.post("starwar", "rooms", id, "leave").asObject()
    suspend fun kick(id: String, target: String) =
        api.post("starwar", "rooms", id, "kick", body = jsonBody { put("target", target) }).asObject()
    suspend fun ready(id: String, ready: Boolean) =
        api.post("starwar", "rooms", id, "ready", body = jsonBody { put("ready", ready) }).asObject()
    suspend fun quickStart(id: String) = api.post("starwar", "rooms", id, "quick-start").asObject()

    /** 房主修改房间设置：mapId / items / costMode / password 任选其一 */
    suspend fun updateSettings(id: String, mapId: Int?, items: Boolean?, costMode: Int?, password: String?): JsonObject =
        api.post("starwar", "rooms", id, "settings", body = jsonBody {
            mapId?.let { put("mapId", it) }
            items?.let { put("items", it) }
            costMode?.let { put("costMode", it) }
            password?.let { put("password", it) }
        }).asObject()
    suspend fun startMatch(id: String) = api.post("starwar", "rooms", id, "start").asObject()
    suspend fun world(id: String) = api.get("starwar", "rooms", id, "world").asObject()

    suspend fun chat(id: String, afterId: Long) =
        api.get("starwar", "rooms", id, "chat", query = mapOf("afterId" to "$afterId")).asObject()
    suspend fun say(id: String, content: String, kind: String = "text") =
        api.post("starwar", "rooms", id, "chat", body = jsonBody { put("content", content); put("kind", kind) }).asObject()

    suspend fun phrases() = api.get("starwar", "phrases")
    suspend fun savePhrases(list: List<String>) =
        api.post("starwar", "phrases", body = JsonArray(list.map { JsonPrimitive(it) }))

    suspend fun friends() = api.get("starwar", "friends")
    suspend fun addFriend(target: String) =
        api.post("starwar", "friends", body = jsonBody { put("target", target) }).asObject()
    suspend fun opponent(name: String) = api.get("starwar", "players", name).asObject()

    suspend fun shop() = api.get("starwar", "shop").asObject()
    suspend fun buy(type: String, id: String) =
        api.post("starwar", "shop", "buy", body = jsonBody { put("type", type); put("id", id) }).asObject()
    suspend fun hangar() = api.get("starwar", "hangar")
    suspend fun equip(type: String, id: String) =
        api.post("starwar", "hangar", "equip", body = jsonBody { put("type", type); put("id", id) }).asObject()
    suspend fun market() = api.get("starwar", "market").asObject()

    suspend fun ranking(scope: String, page: Int) =
        api.get("starwar", "ranking", query = mapOf("scope" to scope, "page" to "$page")).asObject()

    suspend fun matchResult(matchId: String) = api.get("starwar", "matches", matchId).asObject()
}

/** 对战实时通道。与聊天通道分开：20Hz 世界快照的高频帧不该挤占聊天连接。 */
sealed interface StarwarEvent {
    data object Connected : StarwarEvent
    data object Disconnected : StarwarEvent
    data class World(val world: StarwarWorld, val standings: List<StarwarStanding>) : StarwarEvent
    data class Settled(val roomId: String, val matchId: String, val standings: List<StarwarStanding>) : StarwarEvent
    data class Chat(val roomId: String, val message: StarwarChatMessage) : StarwarEvent
    data class Room(val roomId: String, val room: StarwarRoom) : StarwarEvent
}

class StarwarRealtime(private val api: ApiClient) {

    private val socketRef = AtomicLong(0)

    fun events(roomId: String): Flow<StarwarEvent> =
        callbackFlow<StarwarEvent> {
            if (!api.store.loggedIn) { close(); return@callbackFlow }
            val url = ServerAddress.starwarSocket(api.store.server, api.store.token)
            val socket = api.http.newWebSocket(okhttp3.Request.Builder().url(url).build(),
                object : okhttp3.WebSocketListener() {
                    override fun onOpen(webSocket: okhttp3.WebSocket, response: okhttp3.Response) {
                        socketRef.set(System.currentTimeMillis())
                        trySend(StarwarEvent.Connected)
                        webSocket.send(joinFrame(roomId))
                    }

                    override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
                        socketRef.set(System.currentTimeMillis())
                        val frame = runCatching { ApiJson.parseToJsonElement(text).asObject() }.getOrNull() ?: return
                        when ((frame["type"] as? JsonPrimitive)?.contentOrNull) {
                            "starwar.world" -> {
                                val world = (frame["world"] as? JsonObject)?.let(::parseStarwarWorld) ?: return
                                val standings = frame["standings"]?.jsonArray?.mapNotNull { it as? JsonObject }
                                    ?.map(::parseStarwarStanding) ?: emptyList()
                                trySend(StarwarEvent.World(world, standings))
                            }
                            "starwar.settled" -> trySend(StarwarEvent.Settled(
                                roomId = (frame["roomId"] as? JsonPrimitive)?.contentOrNull ?: roomId,
                                matchId = (frame["matchId"] as? JsonPrimitive)?.contentOrNull ?: "",
                                standings = frame["standings"]?.jsonArray?.mapNotNull { it as? JsonObject }
                                    ?.map(::parseStarwarStanding) ?: emptyList()))
                            "starwar.chat" -> {
                                val message = (frame["message"] as? JsonObject)?.let(::parseStarwarChatMessage) ?: return
                                trySend(StarwarEvent.Chat(roomId, message))
                            }
                            "starwar.room" -> {
                                val room = (frame["room"] as? JsonObject)?.let(::parseStarwarRoom) ?: return
                                trySend(StarwarEvent.Room(roomId, room))
                            }
                        }
                    }

                    override fun onFailure(webSocket: okhttp3.WebSocket, error: Throwable, response: okhttp3.Response?) {
                        trySend(StarwarEvent.Disconnected)
                        close(java.io.IOException("对战连接暂不可用", error))
                    }

                    override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
                        trySend(StarwarEvent.Disconnected)
                        close()
                    }
                })
            val heartbeat = launch {
                while (true) {
                    kotlinx.coroutines.delay(15_000)
                    socket.send("{\"type\":\"ping\"}")
                }
            }
            awaitClose { heartbeat.cancel(); socket.close(1000, "离开房间"); socket.cancel() }
        }

    /** 方向键与使用道具；高频调用，走 WebSocket 不阻塞 UI */
    suspend fun input(roomId: String, dx: Int, dy: Int, item: Boolean = false) {
        api.post("starwar", "rooms", roomId, "input", body = jsonBody {
            put("dx", dx); put("dy", dy); put("item", item)
        })
    }
}

private fun joinFrame(roomId: String): String =
    buildJsonObject { put("type", "join"); put("roomId", roomId) }.toString()
