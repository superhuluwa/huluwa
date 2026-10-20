package com.huluwa.android.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.BrowserAddress

/**
 * 隐藏的无痕浏览器：在「我的」页连续点击顶部标题 10 次进入，界面上不提供任何入口。
 *
 * 无痕做法：禁用文件访问与混合内容；进入和退出时清除 Cookie、WebStorage 与磁盘缓存；
 * 关闭页面时销毁 WebView，不留下本次浏览的会话数据。
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable fun HiddenBrowserScreen(vm: HuluwaViewModel) {
    val context = LocalContext.current
    val keyboard = LocalSoftwareKeyboardController.current
    var address by rememberSaveable { mutableStateOf("") }
    // 保存当前页地址，屏幕旋转重建 WebView 时可以恢复浏览位置。
    var loadedUrl by rememberSaveable { mutableStateOf("") }
    var editing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var failure by remember { mutableStateOf<String?>(null) }
    val webView = remember {
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
            }
            loadUrl(loadedUrl.ifBlank { "about:blank" })
        }
    }
    BackHandler { vm.closeHiddenBrowser() }
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
                        IconButton(vm::closeHiddenBrowser) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "关闭浏览器") }
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
