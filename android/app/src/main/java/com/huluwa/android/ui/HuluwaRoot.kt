package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    MainTab.Subscriptions -> Icons.Outlined.BookmarkBorder
    MainTab.Mine -> Icons.Outlined.PersonOutline
}
@Composable fun HuluwaRoot(vm: HuluwaViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
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
        if (activeSession?.kind == "upgrade") AppUpgradeScreen(ui, vm, snackbar)
        else if (activeSession?.managementService != null) {
            holder.SaveableStateProvider("session:${activeSession.id}") { ManagementSessionScreen(ui, vm, snackbar) }
        }
        else if (activeSession != null) ChatScreen(ui, vm, snackbar)
        else Scaffold(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            containerColor = if (ui.tab.isSessionTab) SessionsBackground else HuluwaColors.Paper,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                // 隐藏入口：在「我的」页连续点击标题 10 次进入无痕浏览器。
                PageHeader(ui.tab.title, if (ui.tab.isSessionTab) SessionsBackground else HuluwaColors.Paper,
                    onTap = if (ui.tab == MainTab.Mine) onMineTitleTap else null)
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
                        MainTab.Subscriptions -> SubscriptionsScreen(ui, vm)
                        MainTab.Mine -> MineScreen(ui, vm)
                    } }
                    if (ui.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().align(androidx.compose.ui.Alignment.TopCenter))
                }
            }
        }
        AppSheets(ui, vm)
    }
}
