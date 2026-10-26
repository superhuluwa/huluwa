package com.huluwa.android.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.BrowserAddress

/**
 * 应用内无痕浏览器：支持隐藏入口与影视卡片的「网站直达」，可传入初始页面。
 *
 * 无痕做法：禁用文件访问与混合内容；进入和退出时清除 Cookie、WebStorage 与磁盘缓存；
 * 关闭页面时销毁 WebView，不留下本次浏览的会话数据。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun HiddenBrowserScreen(vm: HuluwaViewModel, initialUrl: String = "") {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val keyboard = LocalSoftwareKeyboardController.current
    var address by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    // 保存当前页地址，屏幕旋转重建 WebView 时可以恢复浏览位置。
    var loadedUrl by rememberSaveable(initialUrl) { mutableStateOf(initialUrl) }
    var editing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
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
            webViewClient = object : WebViewClient() {
                /** 只允许在浏览器内打开网页，禁止网页通过自定义 scheme 唤起外部应用。 */
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                    request.url.scheme?.lowercase() !in setOf("http", "https")
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    failure = null
                    progress = 1
                    if (!url.isNullOrBlank() && url != "about:blank") loadedUrl = url
                }
                override fun onPageFinished(view: WebView, url: String?) {
                    progress = 0
                    val actual = url.orEmpty()
                    if (actual.isNotBlank() && actual != "about:blank") {
                        loadedUrl = actual
                        if (!editing) address = actual
                    }
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
                // 网页视频点全屏时由宿主接管：把视频视图铺满窗口并切到横屏沉浸式。
                // 不覆写这两个回调时，WebView 会直接告诉网页「不支持全屏」，点击就毫无反应。
                override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                    fullscreenHost.show(view, callback)
                }
                override fun onHideCustomView() {
                    fullscreenHost.hide(callPage = false)
                }
            }
            // 屏幕旋转等重建时恢复当前页；网站直达首次进入即加载影片页面。
            if (loadedUrl.isNotBlank()) loadUrl(loadedUrl)
        }
    }
    /** 返回统一走网页历史；没有可回退的网页时返回原入口。 */
    fun goBackOrExit() {
        if (webView.canGoBack()) webView.goBack() else vm.closeHiddenBrowser()
    }
    // 系统返回键：全屏播放时先退出全屏；否则与标题栏返回一致，先网页回退，退无可退再返回原入口。
    BackHandler { if (fullscreenHost.isActive) fullscreenHost.hide(callPage = true) else goBackOrExit() }
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
            // 全屏中直接退出浏览页时也要先收起全屏视图，否则窗口会停留在横屏。
            fullscreenHost.hide(callPage = false)
            clearPrivateData()
            webView.stopLoading()
            webView.loadUrl("about:blank")
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
            webView.removeAllViews()
            webView.destroy()
        }
    }
    fun navigate() {
        val url = runCatching { BrowserAddress.normalize(address) }.getOrElse {
            failure = it.message
            return
        }
        address = url
        failure = null
        keyboard?.hide()
        webView.loadUrl(url)
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
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
                            Modifier.weight(1f).onFocusChanged { editing = it.isFocused },
                            singleLine = true, shape = RoundedCornerShape(14.dp),
                            leadingIcon = { Icon(Icons.Outlined.Shield, null, Modifier.size(17.dp), tint = HuluwaColors.Pine) },
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
                    }
                    if (progress in 1..99) Box(Modifier.fillMaxWidth().height(2.dp).background(HuluwaColors.SoftGreen)) {
                        Box(Modifier.fillMaxWidth(progress / 100f).fillMaxHeight().background(HuluwaColors.Pine))
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().navigationBarsPadding().imePadding()) {
            AndroidView({ webView }, Modifier.fillMaxSize())
            if (loadedUrl.isBlank()) {
                Column(
                    Modifier.align(Alignment.Center).padding(horizontal = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    RoundIcon(Icons.Outlined.Shield, modifier = Modifier.size(60.dp))
                    Text("无痕浏览", style = MaterialTheme.typography.titleMedium)
                    Text("输入网址开始浏览，关闭页面后清除本次记录", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
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
 * 的 android.R.id.content 上盖住 Compose 界面，并切换到横屏沉浸式；退出时移除并恢复系统栏。
 */
private class WebFullscreenHost(private val activity: Activity?) {
    private var container: FrameLayout? = null
    private var callback: WebChromeClient.CustomViewCallback? = null

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
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        (view.parent as? ViewGroup)?.removeView(view)
        box.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        root.addView(box)
        container = box
        this.callback = callback
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
        // 恢复成跟随传感器，回到浏览器页面后仍可正常旋转。
        target.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}
