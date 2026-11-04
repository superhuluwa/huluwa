package com.huluwa.android.ui

import android.os.SystemClock
import android.webkit.CookieManager
import android.webkit.WebView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.coroutines.resume
import kotlin.math.roundToInt

internal data class BrowserVideoTouch(val x: Float, val y: Float, val atMs: Long)

private data class WebVideoBounds(
    val left: Double, val top: Double, val width: Double, val height: Double, val viewportWidth: Double,
) {
    fun contains(touch: BrowserVideoTouch, webViewWidth: Int): Boolean {
        val scale = webViewWidth / viewportWidth
        return touch.x >= left * scale && touch.x <= (left + width) * scale &&
            touch.y >= top * scale && touch.y <= (top + height) * scale
    }
    companion object {
        fun from(raw: JsonObject): WebVideoBounds? {
            val bounds = raw.child("bounds")
            val left = bounds.number("left") ?: return null
            val top = bounds.number("top") ?: return null
            val width = bounds.number("width")?.takeIf { it > 0 } ?: return null
            val height = bounds.number("height")?.takeIf { it > 0 } ?: return null
            val viewportWidth = bounds.number("viewportWidth")?.takeIf { it > 0 } ?: return null
            return WebVideoBounds(left, top, width, height, viewportWidth)
        }
    }
}

/** 只读取受支持播放页的状态；原位覆盖网页视频，不增加浏览器工具栏，也不暴露原生 JS 接口。 */
@Composable internal fun BrowserWebVideoOverlay(
    vm: HuluwaViewModel,
    webView: WebView,
    page: String,
    pageToken: Long,
    detectedVideo: BrowserVideo?,
    touch: BrowserVideoTouch?,
    fullscreenRequest: Int,
    castActive: Boolean,
    onTakeover: () -> Unit,
    onCast: (BrowserVideo) -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current
    val latestDetected by rememberUpdatedState(detectedVideo)
    val latestTouch by rememberUpdatedState(touch)
    val latestFullscreenRequest by rememberUpdatedState(fullscreenRequest)
    val latestCastActive by rememberUpdatedState(castActive)
    val latestOnTakeover by rememberUpdatedState(onTakeover)
    var media by remember(page, pageToken) { mutableStateOf<BrowserVideo?>(null) }
    var bounds by remember(page, pageToken) { mutableStateOf<WebVideoBounds?>(null) }
    var failedUrl by remember(page, pageToken) { mutableStateOf<String?>(null) }
    var acceptedFullscreenRequest by remember(page, pageToken) { mutableIntStateOf(0) }
    LaunchedEffect(webView, page, pageToken) {
        if (!MovieWebVideo.isPlaybackPage(page)) return@LaunchedEffect
        var lastTouchAt = 0L
        var lastFullscreenRequest = 0
        var pendingUntil = 0L
        var resolveAttempted = false
        while (isActive) {
            if (!lifecycle.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                delay(250)
                continue
            }
            val result = webView.videoScript(MovieWebVideo.captureScript)
            val raw = runCatching {
                val encoded = ApiJson.parseToJsonElement(result.orEmpty()) as? JsonPrimitive
                encoded?.content?.let { ApiJson.parseToJsonElement(it).asObject() }
            }.getOrNull()
            // 回调可能晚于导航，不能把旧文档的位置或播放请求带到新页。
            if (raw == null || raw.text("pageUrl") != page) {
                delay(250)
                continue
            }
            bounds = WebVideoBounds.from(raw)
            val now = SystemClock.uptimeMillis()
            val tap = latestTouch
            if (tap != null && tap.atMs > lastTouchAt) {
                lastTouchAt = tap.atMs
                if (now - tap.atMs in 0L..1500L && bounds?.contains(tap, webView.width) == true) {
                    pendingUntil = now + 5000
                    resolveAttempted = false
                }
            }
            if (latestFullscreenRequest > lastFullscreenRequest) {
                lastFullscreenRequest = latestFullscreenRequest
                // 只响应正片的全屏操作，同页广告的全屏请求继续交给网页。
                if (raw.flag("requested") || (tap != null && now - tap.atMs in 0L..1500L &&
                        bounds?.contains(tap, webView.width) == true)) {
                    acceptedFullscreenRequest = lastFullscreenRequest
                    pendingUntil = now + 5000
                    resolveAttempted = false
                }
            }
            if (raw.flag("requested")) pendingUntil = now + 5000
            if (media == null && bounds != null && pendingUntil > now && !latestCastActive) {
                val source = raw.text("url")
                val observed = latestDetected
                val direct = source.takeIf(MovieWebVideo::isM3u8)
                // 电影港优先使用当前页公开配置或本站解析，跨域播放才沿用请求捕获结果。
                val url = direct ?: if (MovieWebVideo.canResolve(page)) {
                    if (!resolveAttempted) {
                        resolveAttempted = true
                        vm.resolveInlineVideo(page)
                    } else null
                } else observed?.url
                if (url != null && MovieWebVideo.isM3u8(url) && url != failedUrl) {
                    val position = raw.number("positionMs")?.toLong()?.coerceAtLeast(0) ?: 0
                    val resolved = observed?.takeIf { it.url == url }?.copy(positionMs = position) ?: BrowserVideo(
                        url = url,
                        title = raw.text("title").ifBlank { "网页视频" },
                        referer = page,
                        userAgent = webView.settings.userAgentString,
                        cookie = CookieManager.getInstance().getCookie(url).orEmpty(),
                        positionMs = position,
                    )
                    latestOnTakeover()
                    // 等网页暂停完成再启动原生解码，避免切换期间出现两路声音。
                    if (webView.videoScript(MovieWebVideo.takeoverScript(page)) == "true" && isActive) {
                        media = resolved
                        pendingUntil = 0L
                    }
                }
            }
            // 播放中及时跟随滚动、缩放和旋转；后台停止读取页面。
            delay(if (media == null) 200 else 32)
        }
    }
    media?.let { current ->
        Layout(
            content = {
                BrowserInlineVideoPlayer(
                    media = current,
                    fullscreenRequest = acceptedFullscreenRequest,
                    suspended = castActive || bounds == null,
                    onCast = onCast,
                    onFailure = { position ->
                        if (webView.url == page && media?.url == current.url) {
                            failedUrl = current.url
                            media = null
                            // 此资源本次不再重复接管，让用户沿用网页原有的播放能力。
                            webView.evaluateJavascript(MovieWebVideo.resumeScript, null)
                            webView.evaluateJavascript(MovieWebVideo.releaseTakeoverScript(position), null)
                        }
                    },
                )
            },
            modifier = Modifier.fillMaxSize().clipToBounds(),
        ) { measurables, constraints ->
            val rect = bounds
            val scale = if (rect != null) webView.width / rect.viewportWidth else 1.0
            val width = ((rect?.width ?: 1.0) * scale).roundToInt().coerceIn(1, 16384)
            val height = ((rect?.height ?: 1.0) * scale).roundToInt().coerceIn(1, 16384)
            val child = measurables.single().measure(Constraints.fixed(width, height))
            val x = ((rect?.left ?: -20000.0) * scale).roundToInt()
            val y = ((rect?.top ?: -20000.0) * scale).roundToInt()
            layout(constraints.maxWidth, constraints.maxHeight) { child.place(x, y) }
        }
    }
}

private suspend fun WebView.videoScript(script: String): String? = suspendCancellableCoroutine { continuation ->
    evaluateJavascript(script) { result -> if (continuation.isActive) continuation.resume(result) }
}

private suspend fun HuluwaViewModel.resolveInlineVideo(page: String): String? = suspendCancellableCoroutine { continuation ->
    val job = resolveBrowserVideo(page,
        onResolved = { if (continuation.isActive) continuation.resume(it) },
        onFailure = { if (continuation.isActive) continuation.resume(null) },
    )
    continuation.invokeOnCancellation { job.cancel() }
}
