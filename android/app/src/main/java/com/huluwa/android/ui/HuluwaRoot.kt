package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
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

private fun tabIcon(tab: MainTab): ImageVector = when (tab) {
    MainTab.Sessions -> Icons.Outlined.Forum
    MainTab.Downloads -> Icons.Outlined.FileDownload
    MainTab.Subscriptions -> Icons.Outlined.BookmarkBorder
    MainTab.Mine -> Icons.Outlined.PersonOutline
}
@Composable fun HuluwaRoot(vm: HuluwaViewModel) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val holder = rememberSaveableStateHolder()
    LifecycleStartEffect(ui.auth.loggedIn) { vm.setForeground(true); onStopOrDispose { vm.setForeground(false) } }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    BackHandler(ui.chat.session != null && ui.sheet == null) { vm.closeChat() }
    if (!ui.auth.loggedIn) {
        AuthScreen(ui.auth, vm::authenticate, vm::resetSetup)
    } else {
        val activeSession = ui.chat.session
        if (activeSession?.kind == "upgrade") AppUpgradeScreen(ui, vm, snackbar)
        else if (activeSession?.managementService != null) {
            holder.SaveableStateProvider("session:${activeSession.id}") { ManagementSessionScreen(ui, vm, snackbar) }
        }
        else if (activeSession != null) ChatScreen(ui, vm, snackbar)
        else Scaffold(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            containerColor = if (ui.tab == MainTab.Sessions) SessionsBackground else HuluwaColors.Paper,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                PageHeader(ui.tab.title, if (ui.tab == MainTab.Sessions) SessionsBackground else HuluwaColors.Paper)
            },
            bottomBar = {
                NavigationBar(Modifier.navigationBarsPadding(), containerColor = Color.White, tonalElevation = 0.dp) {
                    MainTab.entries.forEach { tab -> NavigationBarItem(ui.tab == tab, { vm.selectTab(tab) },
                        icon = { BadgedBox(badge = {
                            val unread = ui.sessions.data.sumOf { it.unread }
                            if (tab == MainTab.Sessions && unread > 0) Badge(containerColor = HuluwaColors.Pine, contentColor = Color.White) { Text(if (unread > 99) "99+" else unread.toString()) }
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
                        MainTab.Sessions -> SessionsScreen(ui, vm)
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
