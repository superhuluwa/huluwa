package com.huluwa.android.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class ApiException(val code: Int, override val message: String) : IOException(message)

/** 服务器不可达时的统一文案；界面层据此把提示收敛到标题栏的「重连中」徽标。 */
const val ServerUnreachableMessage = "无法连接服务器，请检查服务器地址和网络"
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    is SocketTimeoutException -> "请求超时，请稍后重试"
    is IOException -> ServerUnreachableMessage
    is IllegalArgumentException -> message ?: "输入内容不正确"
    else -> message?.take(240) ?: "操作失败，请重试"
}
/** 是否属于「服务器连不上」：与 [userMessage] 中网络层的分支保持一致（超时单独提示，不算断线）。 */
fun String?.isServerUnreachable(): Boolean = this == ServerUnreachableMessage
suspend fun <T> sourceResult(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (error: CancellationException) { throw error }
catch (error: Throwable) { Result.failure(error) }

class ApiClient(val store: SessionStore) {
    val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()

    // 服务器可达性：任一请求成功即视为在线，连接失败即可达为 false。
    // 后台轮询本身就在不断重试，界面层只观察这个状态，不再要求用户手动点「重试」。
    private val mutableReachable = MutableStateFlow(true)
    val reachable: StateFlow<Boolean> = mutableReachable.asStateFlow()
    /** 登录成功、重新配置服务器时重置，避免上一台服务器的离线状态带到新会话。 */
    fun markReachable() { mutableReachable.value = true }

    suspend fun request(
        method: String, segments: List<String>, body: JsonElement? = null, query: Map<String, String> = emptyMap(),
        server: String = store.server, token: String = store.token,
    ): JsonElement {
        val target = ServerAddress.endpoint(server, segments, query)
        val request = Request.Builder().url(target).header("Accept", "application/json")
        if (token.isNotBlank()) request.header("Authorization", "Bearer $token")
        request.method(method, payloadFor(method, body))
        val client = if (segments.takeLast(2) == listOf("images", "pull")) http.newBuilder()
            .readTimeout(16, TimeUnit.MINUTES).callTimeout(17, TimeUnit.MINUTES).build() else http
        return try {
            execute(request.build(), client).also { mutableReachable.value = true }
        } catch (error: IOException) {
            // HTTP 层有响应（含 4xx / 5xx）说明服务器在线，只有连接失败才算不可达；
            // 超时单独提示，不作为断线，与 userMessage() 的分支保持一致。
            if (error !is ApiException && error !is SocketTimeoutException) mutableReachable.value = false
            throw error
        }
    }
    suspend fun get(vararg path: String, query: Map<String, String> = emptyMap()) = request("GET", path.toList(), query = query)
    suspend fun post(vararg path: String, body: JsonElement? = null, query: Map<String, String> = emptyMap()) = request("POST", path.toList(), body, query)
    suspend fun put(vararg path: String, body: JsonElement) = request("PUT", path.toList(), body)
    suspend fun delete(vararg path: String) = request("DELETE", path.toList())

    private suspend fun execute(request: Request, client: OkHttpClient): JsonElement = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, error: IOException) { if (continuation.isActive) continuation.resumeWithException(error) }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    val result = runCatching {
                        val raw = response.body?.string().orEmpty()
                        val envelope = runCatching { ApiJson.parseToJsonElement(raw) }.getOrNull() as? JsonObject
                        if (!response.isSuccessful) throw ApiException(response.code, envelope?.text("message")?.ifBlank { null } ?: "服务器请求失败（${response.code}）")
                        if (envelope == null || !envelope.containsKey("code")) throw ApiException(502, "服务器返回的数据格式不正确，请确认地址指向 huluwa 服务")
                        val code = envelope.number("code")?.toInt() ?: 502
                        if (code != 200) throw ApiException(code, envelope.text("message", fallback = "操作失败"))
                        envelope["data"] ?: JsonNull
                    }
                    if (continuation.isActive) result.fold({ continuation.resume(it) }, { continuation.resumeWithException(it) })
                }
            }
        })
    }
}

/**
 * 按方法与是否传了 body 决定请求体。单独提取成顶层函数，便于单测锁住 OkHttp 的硬约束：
 * POST / PUT / PATCH 必须携带请求体，没传时补空 JSON（标记已读、frp 热加载 / 重启、容器启停、
 * 订阅检查等都是无 body 的 POST，补 null 会在 Request.Builder.method() 直接抛
 * IllegalArgumentException: method POST must have a request body，请求根本发不出去）；
 * DELETE 可以没有正文，只有批量删除这类场景才按 body 判断是否携带。
 */
internal fun payloadFor(method: String, body: JsonElement?): RequestBody? = when {
    method in setOf("POST", "PUT", "PATCH") ->
        (body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType())
    method == "DELETE" && body != null ->
        body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
    else -> null
}

fun JsonElement.requireSuccess(): JsonElement {
    val value = this as? JsonObject ?: return this
    if ((value["ok"] as? JsonPrimitive)?.booleanOrNull == false || (value["success"] as? JsonPrimitive)?.booleanOrNull == false)
        throw ApiException(400, value.text("message", "error", fallback = "服务未完成该操作"))
    return this
}
