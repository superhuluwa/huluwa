package com.huluwa.android.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 灯塔公开 H5 接口。独立客户端不携带葫芦娃登录信息，也不更改 TLS 校验。 */
class DengtaRepository {
    private val cookies = DengtaCookieJar()
    private val requestMutex = Mutex()
    private val http = OkHttpClient.Builder().cookieJar(cookies).connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun ranking(type: String, date: String, loop: Boolean = false): DengtaRanking {
        require(type in setOf("1", "2", "3")) { "不支持的榜单类型" }
        require(date.isEmpty() || date.matches(Regex("\\d{8}"))) { "榜单日期格式不正确" }
        val payload = buildJsonObject {
            put("configCode", "1"); put("typeCode", type); put("platformCode", "beaconmarketshare_play_vv")
            put("showDate", date); put("dateType", "0"); put("marketShareType", "2"); put("isLoop", loop)
        }
        return DengtaRanking.from(request("mtop.alipictures.gravitywave.media.rank.list", "3.0", payload).child("data"))
    }

    suspend fun detail(id: String): DengtaDetail {
        require(id.matches(Regex("\\d+"))) { "作品编号无效" }
        val payload = buildJsonObject {
            put("mediaId", id); put("dr", "[]"); put("patternName", "BEACON_MEDIA_DETAIL_FILM"); put("patternVersion", "2.0")
        }
        return DengtaDetail.from(request("mtop.alipictures.gravitywave.media.get", "1.0", payload), id)
    }

    suspend fun playRules(id: String): List<DengtaPlayRule> {
        require(id.matches(Regex("\\d+"))) { "作品编号无效" }
        // platfom 是接口的实际拼写，不要改为 platform。
        return request("mtop.alipictures.gravitywave.media.platfom.playrule.list", "1.0", buildJsonObject { put("mediaId", id) })
            .rows("dataList").map { value -> value.asObject().let { DengtaPlayRule(it.text("platformName"), it.text("updateEpisode"), it.text("playRule")) } }
    }

    suspend fun marketing(id: String, eventType: String = "all"): DengtaMarketingContent {
        require(id.matches(Regex("\\d+"))) { "作品编号无效" }
        return DengtaMarketingContent.from(request("mtop.alipictures.gravitywave.media.detail.marketingEvent.list", "1.0",
            buildJsonObject { put("mediaId", id); put("eventType", eventType) }).child("data"))
    }

    suspend fun materials(id: String, tab: String = "", menuCode: String = "all"): DengtaMaterialContent {
        require(id.matches(Regex("\\d+"))) { "作品编号无效" }
        return DengtaMaterialContent.from(request("mtop.alipictures.gravitywave.media.detail.material.list", "1.0",
            buildJsonObject { put("mediaId", id); put("menuCode", menuCode); if (tab.isNotBlank()) put("selectTab", tab) }).child("data"))
    }

    suspend fun highlights(id: String, dataType: String, lastTime: String = ""): DengtaHighlightContent {
        require(id.matches(Regex("\\d+"))) { "作品编号无效" }
        return DengtaHighlightContent.from(request("mtop.alipictures.gravitywave.highlight.timeline", "1.0",
            buildJsonObject { put("dataId", id); put("dataTypeStr", dataType); if (lastTime.isNotBlank()) put("lastTime", lastTime) }).child("data"))
    }

    private suspend fun request(api: String, version: String, payload: JsonObject): JsonObject = withContext(Dispatchers.IO) {
        requestMutex.withLock {
            if (cookies.token().isBlank()) bootstrap(api, version)
            repeat(2) { attempt ->
                val data = payload.toString()
                val timestamp = System.currentTimeMillis().toString()
                val signature = MessageDigest.getInstance("MD5").digest("${cookies.token()}&$timestamp&$APP_KEY&$data".toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it.toInt() and 0xff) }
                val result = execute(api, version, data, timestamp, signature)
                val ret = result.rows("ret").mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                if (ret.any { it.startsWith("SUCCESS") }) {
                    require(result["data"] is JsonObject) { "灯塔接口没有返回有效数据" }
                    return@withLock result.child("data")
                }
                val tokenFailure = ret.any { it.startsWith("FAIL_SYS_TOKEN_") }
                if (attempt == 0 && tokenFailure) {
                    cookies.clearToken()
                    bootstrap(api, version)
                } else {
                    val reason = ret.firstOrNull()?.substringAfter("::", "")?.take(180).orEmpty()
                    throw IllegalStateException("灯塔数据获取失败${if (reason.isBlank()) "，请稍后重试" else "：$reason"}")
                }
            }
            error("灯塔令牌更新失败，请稍后重试")
        }
    }

    private suspend fun bootstrap(api: String, version: String) {
        // H5 匿名会话由首次请求的 Set-Cookie 下发 _m_h5_tk。
        execute(api, version, "{}", System.currentTimeMillis().toString(), "x")
        check(cookies.token().isNotBlank()) { "灯塔暂未建立数据会话，请稍后重试" }
    }

    private suspend fun execute(api: String, version: String, data: String, timestamp: String, sign: String): JsonObject {
        val url = "https://acs.m.taobao.com/h5/$api/$version/".toHttpUrl().newBuilder()
            .addQueryParameter("jsv", "2.7.5").addQueryParameter("appKey", APP_KEY)
            .addQueryParameter("t", timestamp).addQueryParameter("sign", sign).addQueryParameter("api", api)
            .addQueryParameter("v", version).addQueryParameter("data", data).addQueryParameter("type", "originaljson").build()
        val request = Request.Builder().url(url).header("Accept", "application/json")
            .header("Referer", "https://dengtapro.taopiaopiao.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36")
            .get().build()
        return suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val parsed = response.use {
                            check(it.isSuccessful) { "灯塔服务暂不可用（HTTP ${it.code}）" }
                            val body = it.body?.string() ?: error("灯塔返回了空响应")
                            ApiJson.parseToJsonElement(body) as? JsonObject ?: error("灯塔响应格式不正确")
                        }
                        if (continuation.isActive) continuation.resume(parsed)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }
    }

    private class DengtaCookieJar : CookieJar {
        private val values = mutableListOf<Cookie>()
        @Synchronized override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            for (cookie in cookies) {
                values.removeAll { it.name == cookie.name && it.domain == cookie.domain && it.path == cookie.path }
                if (cookie.expiresAt > System.currentTimeMillis()) values.add(cookie)
            }
        }
        @Synchronized override fun loadForRequest(url: HttpUrl): List<Cookie> {
            values.removeAll { it.expiresAt <= System.currentTimeMillis() }
            return values.filter { it.matches(url) }
        }
        @Synchronized fun token(): String = loadForRequest("https://acs.m.taobao.com/h5/".toHttpUrl())
            .firstOrNull { it.name == "_m_h5_tk" }?.value?.substringBefore('_').orEmpty()
        @Synchronized fun clearToken() { values.removeAll { it.name == "_m_h5_tk" || it.name == "_m_h5_tk_enc" } }
    }

    private companion object { const val APP_KEY = "12574478" }
}
