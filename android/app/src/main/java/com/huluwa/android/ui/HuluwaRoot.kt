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
import androidx.lifecycle.viewmodel.compose.viewModel
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
private enum class RootPage { Home, Chat, ServerStatus }

@Composable fun HuluwaRoot(vm: HuluwaViewModel, onSkinSelected: (AppSkin) -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val reconnecting = rememberReconnecting(vm)
    val snackbar = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()
    val onMineTitleTap = rememberHiddenBrowserTap(vm)
    LifecycleStartEffect(ui.auth.loggedIn) { vm.setForeground(true); onStopOrDispose { vm.setForeground(false) } }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(ui.chat.session != null && ui.sheet == null && !ui.browserVisible) { vm.closeChat() }
    BackHandler(ui.serverStatusVisible && ui.sheet == null) { vm.closeServerStatus() }
    if (!ui.auth.loggedIn) {
        AuthScreen(ui.auth, vm::authenticate, vm::resetSetup)
    } else if (ui.browserVisible) {
        HiddenBrowserScreen(vm, initialUrl = ui.browserUrl, bookmarksEnabled = ui.browserBookmarksEnabled)
    } else {
        val activeSession = ui.chat.session
        val page = when {
            ui.serverStatusVisible -> RootPage.ServerStatus
            activeSession != null -> RootPage.Chat
            else -> RootPage.Home
        }
        // 退场动画期间 closeChat() 已经清空 ui.chat，直接读 ui 会让正在滑出的二级页先变成空白。
        // 这里保留最后一次有效会话的快照，专门供退场那一层绘制。
        val chatSnapshot = remember { mutableStateOf(ui.chat) }
        SideEffect { if (ui.chat.session != null) chatSnapshot.value = ui.chat }
        val pageUi = if (activeSession != null) ui else ui.copy(chat = chatSnapshot.value)
        AnimatedContent(
            // 所有二级会话页共用根导航与横向转场。
            targetState = page,
            transitionSpec = { pageSlideTransition() },
            label = "页面转场",
        ) { destination ->
            if (destination == RootPage.Home) {
                // 列表层始终在二级页下方：返回时二级页从列表上方滑出，与微信一致
                Box(Modifier.fillMaxSize().zIndex(0f)) {
                    val homeBackground = if (ui.tab.isSessionTab) SessionsBackground else HuluwaColors.Paper
                    Scaffold(
                        modifier = Modifier.fillMaxSize().skinBackground(homeBackground).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                        containerColor = Color.Transparent,
                        contentWindowInsets = WindowInsets(0, 0, 0, 0),
                        topBar = {
                            // 隐藏入口：在「我的」页连续点击标题 10 次进入无痕浏览器。
                            // 连不上服务器时统一在标题右侧显示「重连中」，连接恢复后自动消失。
                            key(ui.tab) {
                                PageHeader(ui.tab.title,
                                    onTap = if (ui.tab == MainTab.Mine) onMineTitleTap else null,
                                    reconnecting = reconnecting) {
                                    if (ui.tab == MainTab.Downloads) DownloadsHeaderActions(ui, vm)
                                    if (ui.tab == MainTab.Mine) {
                                        HeaderAction(Icons.Outlined.Settings, "连接与账户", { vm.showSheet(AppSheet.Account) })
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
                                    MainTab.Mine -> MineScreen(ui, vm, onSkinSelected)
                                } }
                            }
                        }
                    }
                }
            } else Box(Modifier.fillMaxSize().zIndex(1f)) {
                when {
                    destination == RootPage.ServerStatus ->
                        holder.SaveableStateProvider("server-status") { ServerStatusScreen(ui, vm, snackbar) }
                    pageUi.chat.session?.kind == "upgrade" -> AppUpgradeScreen(pageUi, vm, snackbar)
                    pageUi.chat.session?.kind == DENGTA_SESSION_KIND ->
                        holder.SaveableStateProvider("dengta:${pageUi.auth.server}:${pageUi.auth.username}") {
                            val dengtaVm: DengtaViewModel = viewModel(key = "dengta:${pageUi.auth.server}:${pageUi.auth.username}")
                            DengtaScreen(dengtaVm, pageUi.chat.session?.title.orEmpty(), vm::closeChat)
                        }
                    pageUi.chat.session?.managementService != null ->
                        holder.SaveableStateProvider("session:${pageUi.chat.session.id}") { ManagementSessionScreen(pageUi, vm, snackbar) }
                    else -> holder.SaveableStateProvider("session:${pageUi.chat.session?.id}") { ChatScreen(pageUi, vm, snackbar) }
                }
            }
        }
        AppSheets(ui, vm, snackbar)
    }
}

/**
 * 微信式页面转场：前进时新页从右滑入、旧页向左让出 1/4 宽度；返回时反向。
 * 时长 320ms，缓动曲线对齐网页端 app-shell.vue 的 cubic-bezier(.32,.72,0,1)。
 */
private fun AnimatedContentTransitionScope<RootPage>.pageSlideTransition(): ContentTransform {
    val forward = targetState != RootPage.Home
    val spec = tween<IntOffset>(durationMillis = 320, easing = CubicBezierEasing(.32f, .72f, 0f, 1f))
    val enter = slideInHorizontally(spec) { width -> if (forward) width else -width / 4 }
    val exit = slideOutHorizontally(spec) { width -> if (forward) -width / 4 else width }
    return enter togetherWith exit
}
