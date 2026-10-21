package com.huluwa.android.data

import kotlinx.coroutines.CancellationException
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
fun Throwable.userMessage(): String = when (this) {
    is ApiException -> message
    is SocketTimeoutException -> "请求超时，请稍后重试"
    is IOException -> "无法连接服务器，请检查服务器地址和网络"
    is IllegalArgumentException -> message ?: "输入内容不正确"
    else -> message?.take(240) ?: "操作失败，请重试"
}
suspend fun <T> sourceResult(block: suspend () -> T): Result<T> = try { Result.success(block()) }
catch (error: CancellationException) { throw error }
catch (error: Throwable) { Result.failure(error) }

class ApiClient(val store: SessionStore) {
    val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(90, TimeUnit.SECONDS)
        .callTimeout(100, TimeUnit.SECONDS).retryOnConnectionFailure(false).build()

    suspend fun request(
        method: String, segments: List<String>, body: JsonElement? = null, query: Map<String, String> = emptyMap(),
        server: String = store.server, token: String = store.token,
    ): JsonElement {
        val target = ServerAddress.endpoint(server, segments, query)
        val request = Request.Builder().url(target).header("Accept", "application/json")
        if (token.isNotBlank()) request.header("Authorization", "Bearer $token")
        // OkHttp 要求 POST / PUT / PATCH 必须带请求体，没传 body 时补一个空 JSON（如 frp 热加载、容器重启）；
        // DELETE 可以没有正文，只有批量删除这类场景才按 body 判断是否携带。
        val payload = when {
            method in setOf("POST", "PUT", "PATCH") ->
                (body?.toString() ?: "{}").toRequestBody("application/json; charset=utf-8".toMediaType())
            method == "DELETE" && body != null ->
                body.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            else -> null
        }
        request.method(method, payload)
        val client = if (segments.takeLast(2) == listOf("images", "pull")) http.newBuilder()
            .readTimeout(16, TimeUnit.MINUTES).callTimeout(17, TimeUnit.MINUTES).build() else http
        return execute(request.build(), client)
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
fun JsonElement.requireSuccess(): JsonElement {
    val value = this as? JsonObject ?: return this
    if ((value["ok"] as? JsonPrimitive)?.booleanOrNull == false || (value["success"] as? JsonPrimitive)?.booleanOrNull == false)
        throw ApiException(400, value.text("message", "error", fallback = "服务未完成该操作"))
    return this
}
