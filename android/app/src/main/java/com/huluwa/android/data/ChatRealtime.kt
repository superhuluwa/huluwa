package com.huluwa.android.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import okhttp3.*
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min

sealed interface RealtimeEvent {
    data object Connected : RealtimeEvent
    data object Disconnected : RealtimeEvent
    data class Frame(val body: JsonObject) : RealtimeEvent
}
/** 只在前台收集；取消收集时同步关闭连接和心跳。REST 发送保留 requestId 幂等。 */
class ChatRealtime(private val api: ApiClient) {
    fun events(): Flow<RealtimeEvent> = callbackFlow<RealtimeEvent> {
        if (!api.store.loggedIn) { close(); return@callbackFlow }
        val lastFrame = AtomicLong(System.currentTimeMillis())
        val request = Request.Builder().url(ServerAddress.socket(api.store.server, api.store.token)).build()
        val socket = api.http.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { lastFrame.set(System.currentTimeMillis()); trySend(RealtimeEvent.Connected) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                lastFrame.set(System.currentTimeMillis())
                val frame = runCatching { ApiJson.parseToJsonElement(text).asObject() }.getOrNull() ?: return
                trySend(RealtimeEvent.Frame(frame))
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, null) }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { trySend(RealtimeEvent.Disconnected); close(IOException("实时连接已断开")) }
            override fun onFailure(webSocket: WebSocket, error: Throwable, response: Response?) {
                trySend(RealtimeEvent.Disconnected)
                close(if (response?.code == 401) ApiException(401, "登录已过期，请重新登录") else IOException("实时连接暂不可用", error))
            }
        })
        val heartbeat = launch { while (true) { delay(25_000); if (System.currentTimeMillis() - lastFrame.get() > 60_000) { socket.cancel(); break }; socket.send("{\"type\":\"ping\"}") } }
        awaitClose { heartbeat.cancel(); socket.close(1000, "客户端离开前台"); socket.cancel() }
    }.retryWhen { cause, attempt ->
        if (cause is ApiException && cause.code == 401) false
        else if (!api.store.loggedIn) false
        else { delay(min(30_000L, 1_000L shl attempt.coerceAtMost(5).toInt())); true }
    }
}
