package com.huluwa.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.MotionEvent
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.HuluwaApplication
import com.huluwa.android.VideoCastViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * 应用内无痕浏览器：支持隐藏入口与影视卡片的「网站直达」，可传入初始页面。
 * 收藏相关菜单（收藏当前网页 / 我的收藏）只在隐藏入口进入时显示。
 *
 * 无痕做法：禁用文件访问与混合内容；进入和退出时清除 Cookie、WebStorage 与磁盘缓存；
 * 关闭页面时销毁 WebView；主动收藏的网址单独保存在 App 本地，关闭后仍保留。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun HiddenBrowserScreen(vm: HuluwaViewModel, initialUrl: String = "", bookmarksEnabled: Boolean = false) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val addressFocus = remember { FocusRequester() }
    val bookmarkStore = remember(context) { (context.applicationContext as HuluwaApplication).browserBookmarks }
    val bookmarks by bookmarkStore.bookmarks.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var browserMenu by remember { mutableStateOf(false) }
    var showBookmarks by rememberSaveable { mutableStateOf(false) }
    var savingBookmark by remember { mutableStateOf(false) }
    var bookmarkError by remember { mutableStateOf<String?>(null) }

    var address by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    // 保存当前页地址，屏幕旋转重建 WebView 时可以恢复浏览位置。
    var loadedUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var editing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    var backPending by remember(initialUrl) { mutableStateOf(false) }
    var restoreFramesOnTouch by remember(initialUrl) { mutableStateOf(false) }
    val castVm: VideoCastViewModel = viewModel()
    val cast by castVm.state.collectAsStateWithLifecycle()
    var detectedVideo by remember(initialUrl) { mutableStateOf<BrowserVideo?>(null) }
    var videoTouch by remember(initialUrl) { mutableStateOf<BrowserVideoTouch?>(null) }
    var videoFullscreenRequest by remember(initialUrl) { mutableIntStateOf(0) }
    var castVideo by remember(initialUrl) { mutableStateOf<BrowserVideo?>(null) }
    var showCast by remember { mutableStateOf(false) }
    var pageTitle by remember(initialUrl) { mutableStateOf("") }
    val currentPage = remember(initialUrl) { AtomicReference(initialUrl) }
    val pageSequence = remember(initialUrl) { AtomicLong(0) }
    val castActive by rememberUpdatedState(cast.selected != null && cast.playing)
    val bookmarkUrl = remember(loadedUrl) { runCatching { BrowserAddress.normalize(loadedUrl) }.getOrNull() }
    val bookmarked = bookmarks.any { it.url == bookmarkUrl }

    fun bookmarkNotice(message: String) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(message)
        }
    }
    fun changeBookmark(remove: BrowserBookmark? = null) {
        if (savingBookmark) return
        val url = remove?.url ?: bookmarkUrl ?: return
        val title = pageTitle.ifBlank { url }
        savingBookmark = true
        bookmarkError = null
        scope.launch {
            try {
                if (remove != null) {
                    bookmarkStore.remove(url)
                    bookmarkNotice("收藏已删除")
                } else bookmarkNotice(if (bookmarkStore.toggle(url, title)) "已收藏当前网页" else "已取消收藏")
            } catch (error: CancellationException) { throw error }
            catch (_: Exception) {
                bookmarkError = "收藏保存失败，请重试"
                if (!showBookmarks) bookmarkNotice("收藏保存失败，请重试")
            }
            finally { savingBookmark = false }
        }
    }


    fun capture(value: String, page: String, userAgent: String, positionMs: Long = 0, referer: String = page) {
        if (!MovieWebVideo.isM3u8(value)) return
        val title = pageTitle.ifBlank { if (MovieWebVideo.isMoviePage(page)) "电影港在线视频" else "网页视频" }
        detectedVideo = BrowserVideo(value, title, referer, userAgent,
            CookieManager.getInstance().getCookie(value).orEmpty(), positionMs.coerceAtLeast(0))
    }
    fun captureDom(result: String?, page: String, userAgent: String, referer: String = page, requestedUrl: String? = null) {
        runCatching {
            val encoded = ApiJson.parseToJsonElement(result.orEmpty()) as? JsonPrimitive ?: return@runCatching
            val raw = ApiJson.parseToJsonElement(encoded.content).asObject()
            val source = raw.text("url")
            val positionMs = raw.number("positionMs")?.toLong()?.coerceAtLeast(0)
            val previous = detectedVideo
            if (MovieWebVideo.isM3u8(source)) {
                // 只有命中当前资源的请求头才能复用，避免把广告请求的 Referer 带给正片。
                val sourceReferer = if (requestedUrl == null || requestedUrl == source) referer else page
                capture(source, page, userAgent, positionMs ?: 0,
                    previous?.takeIf { it.url == source }?.referer ?: sourceReferer)
            } else if (previous != null && positionMs != null && !MovieWebVideo.isMfcnPage(page)) {
                // 电影港的 blob 视频仍沿用请求捕获的地址，只补充可读取的网页进度。
                detectedVideo = previous.copy(positionMs = positionMs)
            }
        }
    }
    // 网页视频全屏：WebView 不自带全屏能力，需由宿主把网页提供的视频视图挂到窗口上。
    val fullscreenHost = remember(activity) { WebFullscreenHost(activity) }
    val webView = remember(initialUrl) {
        clearPrivateData()
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.setGeolocationEnabled(false)
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.cacheMode = WebSettings.LOAD_NO_CACHE
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
            var touchDownX = 0f
            var touchDownY = 0f
            // 原播放区域的点击也能接管跨域 iframe；拖动网页不当作播放请求。
            setOnTouchListener { _, event ->
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    touchDownX = event.x
                    touchDownY = event.y
                    if (restoreFramesOnTouch && !backPending && !castActive) {
                        restoreFramesOnTouch = false
                        evaluateJavascript(MovieWebVideo.resumeScript, null)
                    }
                } else if (event.actionMasked == MotionEvent.ACTION_UP &&
                    event.eventTime - event.downTime < 500 &&
                    kotlin.math.abs(event.x - touchDownX) < 12 * resources.displayMetrics.density &&
                    kotlin.math.abs(event.y - touchDownY) < 12 * resources.displayMetrics.density) {
                    videoTouch = BrowserVideoTouch(event.x, event.y, SystemClock.uptimeMillis())
                }
                false
            }
            webViewClient = object : WebViewClient() {
                private fun capturePage(view: WebView, page: String, referer: String = page, requestedUrl: String? = null) {
                    if (!MovieWebVideo.isSupportedPage(page) || page != currentPage.get()) return
                    val sequence = pageSequence.get()
                    view.evaluateJavascript(MovieWebVideo.captureScript) { result ->
                        if (sequence == pageSequence.get()) captureDom(result, page, view.settings.userAgentString, referer, requestedUrl)
                    }
                }
                /** 只允许在浏览器内打开网页，禁止网页通过自定义 scheme 唤起外部应用。 */
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme?.lowercase() !in setOf("http", "https")
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    failure = null
                    progress = 1
                    pageTitle = ""
                    pageSequence.incrementAndGet()
                    backPending = false
                    restoreFramesOnTouch = false
                    detectedVideo = null
                    videoTouch = null
                    videoFullscreenRequest = 0
                    if (!url.isNullOrBlank() && url != "about:blank") {
                        loadedUrl = url
                        currentPage.set(url)
                    }
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    progress = 0
                    val actual = url.orEmpty()
                    if (actual.isNotBlank() && actual != "about:blank" && actual == currentPage.get()) {
                        loadedUrl = actual
                        if (!editing) address = actual
                        capturePage(view, actual)
                    }
                }
                override fun doUpdateVisitedHistory(view: WebView, url: String?, isReload: Boolean) {
                    val actual = url.orEmpty()
                    if (actual.isBlank() || actual == "about:blank" || actual == currentPage.get()) return
                    // Vue 的站内切换不会重新加载文档，仍需清掉上一条视频及其异步回调。
                    pageSequence.incrementAndGet()
                    currentPage.set(actual)
                    loadedUrl = actual
                    if (!editing) address = actual
                    pageTitle = view.title.orEmpty()
                    failure = null
                    backPending = false
                    restoreFramesOnTouch = false
                    detectedVideo = null
                    videoTouch = null
                    videoFullscreenRequest = 0
                    view.post { capturePage(view, actual) }
                }
                override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                    val page = currentPage.get()
                    val sequence = pageSequence.get()
                    val url = request.url.toString()
                    if (MovieWebVideo.isSupportedPage(page) && MovieWebVideo.isM3u8(url)) {
                        val referer = request.requestHeaders.entries.firstOrNull { it.key.equals("Referer", true) }?.value ?: page
                        view.post {
                            if (sequence == pageSequence.get() && detectedVideo == null) {
                                if (MovieWebVideo.isMfcnPage(page)) {
                                    // 该站同页含广告，并且旧 HLS 请求可能尚未结束，以当前组件的配置为准。
                                    capturePage(view, page, referer, url)
                                } else capture(url, page, view.settings.userAgentString, referer = referer)
                            }
                        }
                    }
                    return null
                }
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        progress = 0
                        failure = "网页无法打开，请检查网址或网络连接"
                    }
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }
                override fun onReceivedTitle(view: WebView, title: String?) {
                    if (!title.isNullOrBlank()) pageTitle = title
                }
                // 网页请求全屏时先保留原生网页能力，受支持的正片随后切换到共用播放实例。
                // 不覆写这两个回调时，WebView 会直接告诉网页「不支持全屏」，点击就毫无反应。
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    fullscreenHost.show(view, callback)
                    if (MovieWebVideo.isPlaybackPage(currentPage.get())) videoFullscreenRequest++
                }
                override fun onHideCustomView() {
                    fullscreenHost.hide(callPage = false)
                }
            }
            // 屏幕旋转等重建时恢复当前页；网站直达首次进入即加载影片页面。
            if (loadedUrl.isNotBlank()) loadUrl(loadedUrl)
        }
    }
    /** 回退前先停止旧页面媒体，兼容不会卸载文档的网页路由。 */
    fun goBackOrExit() {
        if (backPending) return
        restoreFramesOnTouch = false
        if (!webView.canGoBack()) {
            webView.onPause()
            vm.closeHiddenBrowser()
            return
        }
        backPending = true
        val sequence = pageSequence.incrementAndGet()
        detectedVideo = null
        videoTouch = null
        videoFullscreenRequest = 0
        webView.stopLoading()
        webView.evaluateJavascript(MovieWebVideo.stopScript) {
            // 清理期间可能已跳转或关闭，不能再操作旧页面或已销毁的 WebView。
            if (sequence == pageSequence.get()) {
                backPending = false
                if (webView.canGoBack()) webView.goBack() else vm.closeHiddenBrowser()
            }
        }
    }
    // 系统返回退出全屏时也先暂停；网页自己的全屏切换仍由播放器处理。
    BackHandler {
        if (!backPending) {
            if (fullscreenHost.isActive) {
                backPending = true
                val sequence = pageSequence.get()
                webView.evaluateJavascript(MovieWebVideo.pauseScript) {
                    if (sequence == pageSequence.get()) {
                        backPending = false
                        restoreFramesOnTouch = true
                        fullscreenHost.hide(callPage = true)
                    }
                }
            } else goBackOrExit()
        }
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle, webView) {
        val observer = LifecycleEventObserver { _, event -> when (event) {
            Lifecycle.Event.ON_STOP -> webView.onPause()
            Lifecycle.Event.ON_START -> webView.onResume()
            else -> Unit
        } }
        lifecycle.lifecycle.addObserver(observer)
        onDispose {
            lifecycle.lifecycle.removeObserver(observer)
            pageSequence.incrementAndGet()
            castVm.cancelScan()
            // 全屏中直接退出浏览页时也要先收起全屏视图，否则窗口会停留在横屏。
            fullscreenHost.hide(callPage = false)
            clearPrivateData()
            webView.onPause()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
            webView.removeAllViews()
            webView.destroy()
        }
    }
    fun navigateTo(value: String) {
        val url = runCatching { BrowserAddress.normalize(value) }.getOrElse {
            failure = it.message
            return
        }
        address = url
        failure = null
        keyboard?.hide()
        focus.clearFocus()
        webView.loadUrl(url)
    }
    fun navigate() = navigateTo(address)
    LaunchedEffect(cast.selected) {
        if (cast.selected == null) webView.onResume()
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(::goBackOrExit) {
                            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回")
                        }
                        OutlinedTextField(
                            address, { address = it },
                            Modifier.weight(1f).focusRequester(addressFocus).onFocusChanged { editing = it.isFocused },
                            singleLine = true, shape = RoundedCornerShape(14.dp),
                            leadingIcon = { Icon(Icons.Outlined.Shield, null, Modifier.size(17.dp), tint = HuluwaColors.Pine) },
                            trailingIcon = {
                                if (address.isNotEmpty()) IconButton({
                                    address = ""
                                    failure = null
                                    addressFocus.requestFocus()
                                    keyboard?.show()
                                }) { Icon(Icons.Outlined.Close, "清空网址", Modifier.size(18.dp)) }
                            },
                            placeholder = { Text("输入网址", style = MaterialTheme.typography.bodySmall) },
                            textStyle = MaterialTheme.typography.bodyMedium,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { navigate() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = HuluwaColors.Paper, unfocusedContainerColor = HuluwaColors.Paper,
                                focusedBorderColor = HuluwaColors.Pine, unfocusedBorderColor = HuluwaColors.Line,
                            ),
                        )
                        IconButton({ webView.reload() }, enabled = loadedUrl.isNotBlank()) { Icon(Icons.Outlined.Refresh, "刷新网页") }
                        // 菜单里只有收藏相关两项，非隐藏入口时整组隐藏。
                        if (bookmarksEnabled) Box {
                            IconButton({ browserMenu = true }) { Icon(Icons.Outlined.MoreVert, "浏览器菜单") }
                            DropdownMenu(browserMenu, { browserMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (bookmarked) "取消当前收藏" else "收藏当前网页") },
                                    leadingIcon = { Icon(if (bookmarked) Icons.Outlined.Star else Icons.Outlined.StarBorder, null) },
                                    enabled = bookmarkUrl != null && !savingBookmark,
                                    onClick = { browserMenu = false; changeBookmark() },
                                )
                                DropdownMenuItem(
                                    text = { Text("我的收藏（${bookmarks.size}）") },
                                    leadingIcon = { Icon(Icons.Outlined.Bookmarks, null) },
                                    onClick = { browserMenu = false; keyboard?.hide(); showBookmarks = true },
                                )
                            }
                        }
                    }
                    cast.selected?.let { device ->
                        TextButton({ castVideo = cast.media; showCast = true; castVm.scan() }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.Cast, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("${if (cast.playing) "投屏中" else "已暂停"} · ${device.name}", maxLines = 1)
                        }
                    }
                    if (progress in 1..99) Box(Modifier.fillMaxWidth().height(2.dp).background(HuluwaColors.SoftGreen)) {
                        Box(Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(HuluwaColors.Pine))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().navigationBarsPadding().imePadding().clipToBounds()) {
            AndroidView({ webView }, Modifier.fillMaxSize())
            BrowserWebVideoOverlay(
                vm = vm,
                webView = webView,
                page = loadedUrl,
                pageToken = pageSequence.get(),
                detectedVideo = detectedVideo,
                touch = videoTouch,
                fullscreenRequest = videoFullscreenRequest,
                castActive = cast.selected != null && cast.playing,
                onTakeover = { fullscreenHost.hide(callPage = true) },
                onCast = { media -> castVideo = media; showCast = true; castVm.scan() },
            )
            if (loadedUrl.isBlank()) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RoundIcon(Icons.Outlined.Shield, modifier = Modifier.size(60.dp))
                    Text("无痕浏览", style = MaterialTheme.typography.titleMedium)
                    Text("输入网址开始浏览，关闭后清除浏览记录，收藏会保留", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                    if (bookmarksEnabled) TextButton({ keyboard?.hide(); showBookmarks = true }) {
                        Icon(Icons.Outlined.Bookmarks, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("我的收藏（${bookmarks.size}）")
                    }
                }
            }
            failure?.let { message ->
                Surface(Modifier.align(Alignment.TopCenter).padding(16.dp), shape = RoundedCornerShape(14.dp), color = Color(0xFFF8F0E4)) {
                    Row(
                        Modifier.padding(start = 14.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = HuluwaColors.Amber)
                        Text(message, Modifier.weight(1f), color = HuluwaColors.Amber, style = MaterialTheme.typography.bodySmall)
                        TextButton({ navigate() }) { Text("重试") }
                    }
                }
            }
        }
    }
    if (showBookmarks) BrowserBookmarksDialog(bookmarks, savingBookmark, bookmarkError,
        onOpen = { bookmark -> showBookmarks = false; navigateTo(bookmark.url) },
        onRemove = { changeBookmark(it) }, dismiss = { showBookmarks = false })
    if (showCast) VideoCastDialog(castVideo, cast, castVm, onStarted = {
        webView.evaluateJavascript(MovieWebVideo.pauseScript, null)
    }, dismiss = { showCast = false; castVm.cancelScan() })
}

/** 清除 WebView 全局的会话数据；应用内没有其他 WebView，可以直接整库清理。 */
private fun clearPrivateData() {
    CookieManager.getInstance().apply { removeAllCookies(null); flush() }
    WebStorage.getInstance().deleteAllData()
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * 网页视频全屏的宿主容器。
 *
 * WebView 本身不具备全屏能力：网页调用 video.requestFullscreen() 时，会把播放器视图通过
 * WebChromeClient.onShowCustomView 交给宿主，由宿主铺满窗口。这里把播放器视图挂到 Activity
 * 的 android.R.id.content 上盖住 Compose 界面并进入横屏沉浸播放；退出时恢复原方向和系统栏。
 */
private class WebFullscreenHost(private val activity: Activity?) {
    private var container: FrameLayout? = null
    private var callback: WebChromeClient.CustomViewCallback? = null
    private var originalOrientation: Int? = null

    /** 是否处于全屏播放中。 */
    val isActive: Boolean get() = container != null

    fun show(view: View, callback: WebChromeClient.CustomViewCallback) {
        // 有些网页切换清晰度时不会先发 hide 就再次请求全屏，先收掉旧的。
        if (isActive) hide(callPage = false)
        val target = activity
        val root = target?.findViewById<ViewGroup>(android.R.id.content)
        if (target == null || root == null) {
            // 挂不上窗口就直接让网页退出全屏，避免 WebView 停在全屏状态不再渲染。
            runCatching { callback.onCustomViewHidden() }
            return
        }
        val box = FrameLayout(target).apply {
            setBackgroundColor(android.graphics.Color.BLACK)
            keepScreenOn = true
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        (view.parent as? ViewGroup)?.removeView(view)
        box.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(box)
        container = box
        this.callback = callback
        originalOrientation = target.requestedOrientation
        enterImmersive(target, box)
    }

    /**
     * 退出全屏。callPage=true 时会通知网页退出，网页侧随后触发的 onHideCustomView 因已清理而成为空操作。
     */
    fun hide(callPage: Boolean) {
        val target = activity ?: return
        val box = container ?: return
        val pending = callback
        container = null
        callback = null
        // 移除宿主容器后，网页播放器视图回到 WebView 自己的层级，页面控件状态由网页同步恢复。
        box.removeAllViews()
        (box.parent as? ViewGroup)?.removeView(box)
        if (callPage) runCatching { pending?.onCustomViewHidden() }
        exitImmersive(target)
    }

    private fun enterImmersive(target: Activity, view: View) {
        target.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = WindowCompat.getInsetsController(target.window, view)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun exitImmersive(target: Activity) {
        val controller = WindowCompat.getInsetsController(target.window, target.window.decorView)
        controller.show(WindowInsetsCompat.Type.systemBars())
        target.requestedOrientation = originalOrientation ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        originalOrientation = null
    }
}
