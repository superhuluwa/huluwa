package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huluwa.android.StarwarStage
import com.huluwa.android.StarwarState
import com.huluwa.android.StarwarViewModel
import kotlinx.coroutines.delay

/**
 * 原版 240×320 像素界面外壳。
 *
 * <p>原版游戏是 240×320 的 MTK 功能机程序，所有界面都是硬像素布局。
 * 这里把整块屏幕当成一张 240×320 的画布，等比缩放到可用区域，四周留黑边，
 * 保证任何机型上看到的版式都与当年一致（非 4:3 屏幕上原版也留黑边）。</p>
 */
@Composable
fun StarwarPixelFrame(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val aspect = StarwarPx.WIDTH / StarwarPx.HEIGHT
        val w = minOf(maxWidth, maxHeight * aspect)
        val h = w / aspect
        Box(
            Modifier.align(Alignment.Center).size(w, h),
            content = content,
        )
    }
}

/**
 * 星球大战（是男人就撑20秒 网络对战版）—— 原版像素界面。
 *
 * <p>整套界面不再使用 Material 卡片，而是复刻当年 240×320 功能机上的版式：
 * 顶部青蓝标题栏、中部列表/内容区、底部左右软键条。所有文字用原版 14 点阵字库
 * （`font/gb14.sky` + `font/asc14.sky`）逐像素绘制，贴图按 `starwar.ext`
 * 资源表里的原始尺寸绘制，整体等比放大铺满屏幕。</p>
 */
@Composable
fun StarwarScreen(vm: StarwarViewModel, onClose: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // remember 的 lambda 不是 @Composable 上下文，LocalContext.current 必须先取到局部变量
    val context = LocalContext.current
    val font = remember(context) { StarwarFont.of(context) }

    // 本地 UI 态：退出确认、菜单/弹窗、列表光标
    var exitConfirm by remember { mutableStateOf(false) }
    var localMenu by remember { mutableStateOf<StarwarLocal>(StarwarLocal.None) }
    // 各列表的软键光标（原版用 2/4/6/8 移动，这里点击/长按也能操作）
    var cursor by remember { mutableIntStateOf(0) }

    val doBack: () -> Unit = {
        when (ui.stage) {
            StarwarStage.Battle, StarwarStage.Room -> vm.leaveRoom()
            StarwarStage.Rooms -> vm.backToLobby()
            StarwarStage.Lobby -> onClose()
            StarwarStage.Result -> vm.exitBattle()
        }
    }
    val back: () -> Unit = when {
        localMenu != StarwarLocal.None -> { { closeLocal(ui, vm, localMenu) ; localMenu = StarwarLocal.None } }
        ui.stage == StarwarStage.Battle -> { { exitConfirm = true } }
        else -> doBack
    }

    if (exitConfirm) {
        StarwarPixelConfirm("退出", "正在游戏中,确定要退出吗?",
            onOk = { exitConfirm = false; doBack() }, onCancel = { exitConfirm = false })
    }
    BackHandler(onBack = back)
    LaunchedEffect(ui.toast) {
        val text = ui.toast
        if (!text.isNullOrBlank()) { snackbar.showSnackbar(text); vm.dismissToast() }
    }
    LaunchedEffect(ui.error) {
        val text = ui.error
        if (!text.isNullOrBlank()) { snackbar.showSnackbar(text); vm.dismissError() }
    }
    LaunchedEffect(ui.stage, ui.roomId) {
        if (ui.stage == StarwarStage.Room && ui.roomId.isNotBlank()) {
            while (true) {
                delay(3_000)
                vm.loadRoom(ui.roomId)
                vm.loadChat(ui.roomId, ui.chat.messages.lastOrNull()?.id ?: 0)
            }
        }
    }

    // 服务端/ViewModel 侧的弹窗开关 → 本地弹窗。
    // 只在开关「真」时单向同步；开关变回 false 不回写 localMenu，
    // 否则本地主动打开（如大厅点商店）会被立刻重置成 None。
    LaunchedEffect(ui.createRoomOpen, ui.passwordPromptRoom, ui.helpOpen, ui.shopOpen,
        ui.hangarOpen, ui.rankingOpen, ui.statusOpen, ui.settingsOpen, ui.chatOpen,
        ui.phraseSheetOpen, ui.phraseEditOpen, ui.systemMenuOpen, ui.opponent) {
        when {
            ui.createRoomOpen -> localMenu = StarwarLocal.CreateRoom
            ui.passwordPromptRoom != null -> localMenu = StarwarLocal.Password
            ui.helpOpen -> localMenu = StarwarLocal.Help
            ui.shopOpen -> localMenu = StarwarLocal.Shop
            ui.hangarOpen -> localMenu = StarwarLocal.Hangar
            ui.rankingOpen -> localMenu = StarwarLocal.Ranking
            ui.statusOpen -> localMenu = StarwarLocal.Status
            ui.settingsOpen -> localMenu = StarwarLocal.Settings
            ui.chatOpen -> localMenu = StarwarLocal.Chat
            ui.phraseSheetOpen -> localMenu = StarwarLocal.Phrase
            ui.phraseEditOpen -> localMenu = StarwarLocal.PhraseEdit
            ui.systemMenuOpen -> localMenu = StarwarLocal.SystemMenu
            ui.opponent != null -> localMenu = StarwarLocal.Opponent
        }
    }

    /** 打开本地弹窗：同时打开 ViewModel 开关，保持两边一致 */
    val openLocal: (StarwarLocal) -> Unit = { target ->
        localMenu = target
        when (target) {
            StarwarLocal.CreateRoom -> vm.showCreateRoom()
            StarwarLocal.Help -> vm.showHelp()
            StarwarLocal.Shop -> vm.openShop()
            StarwarLocal.Hangar -> vm.openHangar()
            StarwarLocal.Ranking -> vm.openRanking()
            StarwarLocal.Status -> vm.openStatus()
            StarwarLocal.Settings -> vm.openSettings()
            StarwarLocal.Chat -> vm.openChat()
            StarwarLocal.Phrase -> if (!ui.phraseSheetOpen) vm.togglePhraseSheet()
            StarwarLocal.PhraseEdit -> vm.openPhraseEdit()
            StarwarLocal.SystemMenu -> vm.openSystemMenu()
            // 密码框与对手资料由 ViewModel 内的动作直接置位，这里不必额外操作
            StarwarLocal.Password, StarwarLocal.Opponent -> Unit
            StarwarLocal.None -> Unit
        }
    }

    StarwarPixelFrame {
        Box(Modifier.fillMaxSize()) {
            when (ui.stage) {
                StarwarStage.Lobby -> StarwarLobbyPixel(ui, vm, font, openLocal)
                StarwarStage.Rooms -> StarwarRoomsPixel(ui, vm, font, cursor) { cursor = it }
                StarwarStage.Room -> StarwarRoomPixel(ui, vm, font, openLocal)
                StarwarStage.Battle -> StarwarBattlePixel(ui, vm, font)
                StarwarStage.Result -> StarwarResultPixel(ui, vm, font)
            }
            if (ui.loading) {
                StarwarLoadingPixel(font, ui.texts.text("ui", "fetchingData", "正在获取数据,请稍候…"))
            }
        }
    }

    StarwarMusic(
        enabled = ui.stage == StarwarStage.Lobby || ui.stage == StarwarStage.Rooms ||
                ui.stage == StarwarStage.Room,
        volume = ui.volume,
    )

    // ---------------------------------------------------------------- 弹窗
    when (localMenu) {
        StarwarLocal.None -> Unit
        StarwarLocal.CreateRoom -> StarwarCreateRoomPixel(ui, vm, font) {
            localMenu = StarwarLocal.None; vm.hideCreateRoom()
        }
        StarwarLocal.Password -> StarwarPasswordPixel(ui, vm, font) {
            localMenu = StarwarLocal.None; vm.dismissPasswordPrompt()
        }
        StarwarLocal.Help -> StarwarHelpPixel(ui, font) { localMenu = StarwarLocal.None; vm.hideHelp() }
        StarwarLocal.Shop -> StarwarShopPixel(ui, vm, font) { localMenu = StarwarLocal.None; vm.closeShop() }
        StarwarLocal.Hangar -> StarwarHangarPixel(ui, vm, font) { localMenu = StarwarLocal.None; vm.closeHangar() }
        StarwarLocal.Ranking -> StarwarRankingPixel(ui, font) { localMenu = StarwarLocal.None; vm.closeRanking() }
        StarwarLocal.Status -> StarwarStatusPixel(ui, font) { localMenu = StarwarLocal.None; vm.closeStatus() }
        StarwarLocal.Settings -> StarwarSettingsPixel(ui, vm, font) {
            localMenu = StarwarLocal.None; vm.closeSettings()
        }
        StarwarLocal.Chat -> StarwarChatPixel(ui, vm, font) { localMenu = StarwarLocal.None; vm.closeChat() }
        StarwarLocal.Phrase -> StarwarPhrasePixel(ui, vm, font) {
            localMenu = StarwarLocal.None; vm.togglePhraseSheet()
        }
        StarwarLocal.PhraseEdit -> StarwarPhraseEditPixel(ui, vm, font) {
            localMenu = StarwarLocal.None; vm.closePhraseEdit()
        }
        StarwarLocal.SystemMenu -> StarwarSystemMenuPixel(ui, vm, font, onClose) {
            localMenu = StarwarLocal.None; vm.closeSystemMenu()
        }
        StarwarLocal.Opponent -> StarwarOpponentPixel(ui, vm, font) {
            localMenu = StarwarLocal.None; vm.closeOpponent()
        }
    }
}

/** 本地弹窗类型（对应 ViewModel 里的一组 boolean 字段） */
enum class StarwarLocal {
    None, CreateRoom, Password, Help, Shop, Hangar, Ranking, Status,
    Settings, Chat, Phrase, PhraseEdit, SystemMenu, Opponent,
}

/** 关闭弹窗时把 ViewModel 里对应的开关也复位 */
private fun closeLocal(ui: StarwarState, vm: StarwarViewModel, menu: StarwarLocal) {
    when (menu) {
        StarwarLocal.None -> Unit
        StarwarLocal.CreateRoom -> vm.hideCreateRoom()
        StarwarLocal.Password -> vm.dismissPasswordPrompt()
        StarwarLocal.Help -> vm.hideHelp()
        StarwarLocal.Shop -> vm.closeShop()
        StarwarLocal.Hangar -> vm.closeHangar()
        StarwarLocal.Ranking -> vm.closeRanking()
        StarwarLocal.Status -> vm.closeStatus()
        StarwarLocal.Settings -> vm.closeSettings()
        StarwarLocal.Chat -> vm.closeChat()
        StarwarLocal.Phrase -> vm.togglePhraseSheet()
        StarwarLocal.PhraseEdit -> vm.closePhraseEdit()
        StarwarLocal.SystemMenu -> vm.closeSystemMenu()
        StarwarLocal.Opponent -> vm.closeOpponent()
    }
}
