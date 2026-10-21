package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huluwa.android.*
import com.huluwa.android.data.*

/** 隐藏入口的点击计数：相邻两次点击间隔超过 2 秒就重新计数，累计 10 次后触发。 */
@Composable private fun rememberHiddenBrowserTap(vm: HuluwaViewModel): () -> Unit {
    var taps by remember { mutableIntStateOf(0) }
    var lastTapAt by remember { mutableLongStateOf(0L) }
    return {
        val now = System.currentTimeMillis()
        taps = if (now - lastTapAt > 2_000L) 1 else taps + 1
        lastTapAt = now
        if (taps >= 10) { taps = 0; vm.openHiddenBrowser() }
    }
}
private fun tabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.Functions -> Icons.Outlined.GridView
    MainTab.System -> Icons.Outlined.VerifiedUser
    MainTab.Downloads -> Icons.Outlined.FileDownload
    MainTab.Mine -> Icons.Outlined.PersonOutline
}
@Composable fun HuluwaRoot(vm: HuluwaViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val reconnecting = rememberReconnecting(vm)
    val snackbar = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()
    val onMineTitleTap = rememberHiddenBrowserTap(vm)
    LifecycleStartEffect(ui.auth.loggedIn) { vm.setForeground(true); onStopOrDispose { vm.setForeground(false) } }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(ui.chat.session != null && ui.sheet == null) { vm.closeChat() }
    if (!ui.auth.loggedIn) {
        AuthScreen(ui.auth, vm::authenticate, vm::resetSetup)
    } else if (ui.browserVisible) {
        HiddenBrowserScreen(vm)
    } else {
        val activeSession = ui.chat.session
        // 退场动画期间 closeChat() 已经清空 ui.chat，直接读 ui 会让正在滑出的二级页先变成空白。
        // 这里保留最后一次有效会话的快照，专门供退场那一层绘制。
        val chatSnapshot = remember { mutableStateOf(ui.chat) }
        SideEffect { if (ui.chat.session != null) chatSnapshot.value = ui.chat }
        val pageUi = if (activeSession != null) ui else ui.copy(chat = chatSnapshot.value)
        AnimatedContent(
            // true = 二级页（会话 / 管理会话 / 在线升级），false = 标签页外壳（列表）
            targetState = activeSession != null,
            transitionSpec = { pageSlideTransition() },
            label = "页面转场",
        ) { detail ->
            if (!detail) {
                // 列表层始终在二级页下方：返回时二级页从列表上方滑出，与微信一致
                Box(Modifier.fillMaxSize().zIndex(0f)) {
                    Scaffold(
                        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        containerColor = if (ui.tab.isSessionTab) SessionsBackground else HuluwaColors.Paper,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            // 隐藏入口：在「我的」页连续点击标题 10 次进入无痕浏览器。
                            // 连不上服务器时统一在标题右侧显示「重连中」，连接恢复后自动消失。
                            PageHeader(ui.tab.title, if (ui.tab.isSessionTab) SessionsBackground else HuluwaColors.Paper,
                                onTap = if (ui.tab == MainTab.Mine) onMineTitleTap else null,
                                reconnecting = reconnecting) {
                                if (ui.tab.isSessionTab) {
                                    Box(Modifier.size(20.dp)) {
                                        if (!ui.sessions.loaded || ui.sessions.loading) HeaderLoadingIndicator()
                                    }
                                }
                            }
                        },
                        bottomBar = {
                            NavigationBar(Modifier.navigationBarsPadding(), containerColor = Color.White, tonalElevation = 0.dp) {
                                MainTab.entries.forEach { tab -> NavigationBarItem(ui.tab == tab, { vm.selectTab(tab) },
                                    icon = { BadgedBox(badge = {
                                        val unread = when (tab) {
                                            MainTab.Functions -> ui.sessions.data.filterNot { it.isSystemSession }.sumOf { it.unread }
                                            MainTab.System -> ui.sessions.data.filter { it.isSystemSession }.sumOf { it.unread }
                                            else -> 0
                                        }
                                        if (unread > 0) Badge(containerColor = HuluwaColors.Pine, contentColor = Color.White) { Text(if (unread > 99) "99+" else unread.toString()) }
                                    }) { Icon(tabIcon(tab), null) } },
                                    label = { Text(tab.title, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    colors = NavigationBarItemDefaults.colors(selectedIconColor = HuluwaColors.Pine, selectedTextColor = HuluwaColors.Pine,
                                        indicatorColor = HuluwaColors.SoftGreen, unselectedIconColor = HuluwaColors.Muted, unselectedTextColor = HuluwaColors.Muted)) }
                            }
                        }, snackbarHost = { SnackbarHost(snackbar) },
                    ) { padding ->
                        BoxWithConstraints(Modifier.padding(padding).fillMaxSize()) {
                            Box(Modifier.widthIn(max = 660.dp).fillMaxSize().align(androidx.compose.ui.Alignment.TopCenter)) {
                                holder.SaveableStateProvider(ui.tab.name) { when (ui.tab) {
                                    MainTab.Functions -> SessionsScreen(ui, vm)
                                    MainTab.System -> SessionsScreen(ui, vm, system = true)
                                    MainTab.Downloads -> DownloadsScreen(ui, vm)
                                    MainTab.Mine -> MineScreen(ui, vm)
                                } }
                                if (ui.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.TopCenter))
                            }
                        }
                    }
                }
            } else Box(Modifier.fillMaxSize().zIndex(1f)) {
                when {
                    pageUi.chat.session?.kind == "upgrade" -> AppUpgradeScreen(pageUi, vm, snackbar)
                    pageUi.chat.session?.managementService != null ->
                        holder.SaveableStateProvider("session:${pageUi.chat.session.id}") { ManagementSessionScreen(pageUi, vm, snackbar) }
                    else -> ChatScreen(pageUi, vm, snackbar)
                }
            }
        }
        AppSheets(ui, vm)
    }
}

/**
 * 微信式页面转场：前进时新页从右滑入、旧页向左让出 1/4 宽度；返回时反向。
 * 时长 320ms，缓动曲线对齐网页端 app-shell.vue 的 cubic-bezier(.32,.72,0,1)。
 */
private fun AnimatedContentTransitionScope<Boolean>.pageSlideTransition(): ContentTransform {
    val forward = targetState
    val spec = tween<IntOffset>(durationMillis = 320, easing = CubicBezierEasing(.32f, .72f, 0f, 1f))
    val enter = slideInHorizontally(spec) { width -> if (forward) width else -width / 4 }
    val exit = slideOutHorizontally(spec) { width -> if (forward) -width / 4 else width }
    return enter togetherWith exit
}
