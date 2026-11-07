package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.asImageBitmap

import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.huluwa.android.StarwarStage
import com.huluwa.android.StarwarState
import com.huluwa.android.StarwarViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.delay

/**
 * 星球大战（是男人就撑20秒 网络对战版）原生界面。
 *
 * 阶段完全对齐原版：大厅 → 专区房间列表 → 房间（等待聊天室）→ 对战 → 结算。
 * 文案来自服务端下发的 [StarwarTexts]（逐字取自 starwar.mrp 的字符串表）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StarwarScreen(vm: StarwarViewModel, onClose: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    // 对局中点返回先按原文提示「正在游戏中,确定要退出吗?」
    var exitConfirm by remember { mutableStateOf(false) }
    val doBack: () -> Unit = {
        when (ui.stage) {
            StarwarStage.Battle, StarwarStage.Result -> vm.leaveRoom()
            StarwarStage.Room -> vm.leaveRoom()
            StarwarStage.Rooms -> vm.backToLobby()
            StarwarStage.Lobby -> onClose()
        }
    }
    val back: () -> Unit = {
        if (ui.stage == StarwarStage.Battle) exitConfirm = true else doBack()
    }
    if (exitConfirm) {
        AlertDialog(
            onDismissRequest = { exitConfirm = false },
            title = { Text("退出") },
            text = { Text("正在游戏中,确定要退出吗?") },
            confirmButton = { TextButton({ exitConfirm = false; doBack() }) { Text("确定") } },
            dismissButton = { TextButton({ exitConfirm = false }) { Text("返回") } },
        )
    }
    BackHandler(onBack = back)
    LaunchedEffect(ui.toast) {
        val text = ui.toast
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            vm.dismissToast()
        }
    }
    LaunchedEffect(ui.error) {
        val text = ui.error
        if (!text.isNullOrBlank()) {
            snackbar.showSnackbar(text)
            vm.dismissError()
        }
    }
    // 等待聊天室与房间状态轮询：WebSocket 只推新消息，这里负责补历史和成员变化
    LaunchedEffect(ui.stage, ui.roomId) {
        if (ui.stage == StarwarStage.Room && ui.roomId.isNotBlank()) {
            while (true) {
                delay(3_000)
                vm.loadRoom(ui.roomId)
                vm.loadChat(ui.roomId, ui.chat.messages.lastOrNull()?.id ?: 0)
            }
        }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            StarwarTopBar(ui, back)
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when (ui.stage) {
                StarwarStage.Lobby -> StarwarLobby(ui, vm)
                StarwarStage.Rooms -> StarwarRooms(ui, vm)
                StarwarStage.Room -> StarwarRoomScreen(ui, vm)
                StarwarStage.Battle -> StarwarBattle(ui, vm)
                StarwarStage.Result -> StarwarResult(ui, vm)
            }
            if (ui.loading) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
        }
    }

    // 原版背景音乐：大厅与房间循环播放，对局中降低音量，结算后停止
    StarwarMusic(enabled = ui.stage == StarwarStage.Lobby || ui.stage == StarwarStage.Rooms
            || ui.stage == StarwarStage.Room)

    if (ui.createRoomOpen) StarwarCreateRoomDialog(ui, vm)
    if (ui.passwordPromptRoom != null) StarwarPasswordDialog(ui, vm)
    if (ui.helpOpen) StarwarHelpDialog(ui, vm)
    if (ui.shopOpen) StarwarShopDialog(ui, vm)
    if (ui.hangarOpen) StarwarHangarDialog(ui, vm)
    if (ui.rankingOpen) StarwarRankingDialog(ui, vm)
    if (ui.opponent != null) StarwarOpponentDialog(ui, vm)
    if (ui.statusOpen) StarwarStatusDialog(ui, vm)
    if (ui.settingsOpen) StarwarSettingsDialog(ui, vm)
    if (ui.phraseEditOpen) StarwarPhraseEditDialog(ui, vm)
    if (ui.chatOpen) StarwarChatDialog(ui, vm)
    if (ui.phraseSheetOpen) StarwarPhraseDialog(ui, vm)
}

@Composable private fun StarwarTopBar(ui: StarwarState, back: () -> Unit) {
    Surface(color = Color.White, shadowElevation = 2.dp) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
            Column(Modifier.weight(1f)) {
                Text(titleOf(ui), style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitleOf(ui), style = MaterialTheme.typography.labelMedium,
                    color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (ui.stage == StarwarStage.Battle || ui.stage == StarwarStage.Room) {
                IconButton(onClick = { /* 打开聊天：原文 # 键 */ }) {
                    Icon(Icons.Outlined.Chat, "聊天")
                }
            }
        }
    }
}

private fun titleOf(ui: StarwarState): String = when (ui.stage) {
    StarwarStage.Lobby -> "星球大战"
    StarwarStage.Rooms -> ui.zoneTitle.ifBlank { "专区" }
    StarwarStage.Room -> ui.room?.name.orEmpty().ifBlank { "房间" }
    StarwarStage.Battle -> "星球大战"
    StarwarStage.Result -> "游戏排名结果"
}

private fun subtitleOf(ui: StarwarState): String = when (ui.stage) {
    StarwarStage.Lobby -> "${ui.profile.rank} · Lv.${ui.profile.level} · 胜率 ${ui.profile.winRate}%"
    StarwarStage.Rooms -> "第 ${ui.page + 1} / ${ui.roomPage?.pages ?: 1} 页 · 共 ${ui.roomPage?.total ?: 0} 个房间"
    StarwarStage.Room -> "房间 ${ui.room?.id} · ${ui.room?.mapName ?: ""}"
    StarwarStage.Battle -> "剩 ${ui.world?.players?.firstOrNull { it.user == ui.profile.user }?.hp ?: 0} 血"
    StarwarStage.Result -> "本局结束"
}

// ==================================================================== 大厅

@Composable private fun StarwarLobby(ui: StarwarState, vm: StarwarViewModel) {
    val texts = ui.texts
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { StarwarProfileCard(ui, vm) }
        item {
            Text("对战专区", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        items(ui.zones, key = { it.key }) { zone ->
            StarwarZoneCard(zone) { vm.openZone(zone.key, zone.title) }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StarwarMenuButton(Icons.Outlined.ShoppingCart, texts.group("menu")["planeShop"]?.toString()?.trim('"') ?: "飞机商店") { vm.openShop() }
                StarwarMenuButton(Icons.Outlined.FlightTakeoff, "我的机库") { vm.openHangar() }
                StarwarMenuButton(Icons.Outlined.Storefront, "交易市场") { vm.showToast("即将推出,敬请期待!") }
                StarwarMenuButton(Icons.Outlined.EmojiEvents, "排行榜") { vm.openRanking() }
            }
        }
        if (ui.activityNotice.isNotBlank()) item {
            // 活动专区提示：每日 20:00-22:00 免创建费、奖励翻倍
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarwarImage(StarwarArt.LOTTERY, 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(if (ui.activityFree) "活动进行中" else "活动专区",
                            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                            color = if (ui.activityFree) Color(0xFFD9A300) else HuluwaColors.Muted)
                        Text(ui.activityNotice, style = MaterialTheme.typography.labelSmall,
                            color = HuluwaColors.Muted)
                    }
                }
            }
        }
        item { StarwarPlanetsCard(ui) }
        item { StarwarHelpCard(ui, vm) }
    }
}


@Composable private fun StarwarProfileCard(ui: StarwarState, vm: StarwarViewModel) {
    Panel(onClick = { vm.openStatus() }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(HuluwaColors.SoftGreen, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                StarwarImage(StarwarArt.plane(ui.profile.plane), 44.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ui.profile.nick.ifBlank { ui.profile.user },
                    style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("${ui.profile.rank} · 等级 ${ui.profile.level} · 胜率 ${ui.profile.winRate}%",
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                Text("${ui.profile.wins} 胜 ${ui.profile.losses} 负 · 在线 ${ui.online} 人",
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${ui.profile.gold} K币", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFD9A300), fontWeight = FontWeight.SemiBold)
                Text("${ui.profile.crystal} 水晶", style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF4E8BD9), fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun StarwarZoneCard(zone: StarwarZone, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).background(HuluwaColors.SoftGreen, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center) {
                StarwarImage(StarwarArt.PLANE, 32.dp)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(zone.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("最多 ${zone.capacity} 人 · 创建房间 ${zone.costCoin} K币 / ${zone.costCrystal} 水晶",
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = HuluwaColors.Muted)
        }
    }
}

@Composable private fun StarwarMenuButton(icon: androidx.compose.ui.graphics.vector.ImageVector,
                                          label: String, onClick: () -> Unit) {
    Column(Modifier.weight(1f).clickable(onClick = onClick)
        .background(Color.White, RoundedCornerShape(12.dp)).padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(22.dp), tint = HuluwaColors.Pine)
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 1)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StarwarPlanetsCard(ui: StarwarState) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StarwarImage(StarwarArt.SUN, 20.dp)
            Spacer(Modifier.width(6.dp))
            Text("九大星球", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.planets.forEach { planet ->
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).alphaIf(planet.locked)) {
                    Box(Modifier.size(40.dp).background(
                        if (planet.locked) Color(0xFFE0E0E0) else HuluwaColors.SoftGreen),
                        contentAlignment = Alignment.Center) {
                        if (planet.locked) StarwarImage(StarwarArt.LOCK, 30.dp)
                        else StarwarImage(StarwarArt.planet(planet.icon), 34.dp)
                    }
                    Text(planet.name, style = MaterialTheme.typography.labelSmall)
                    Text(planet.unlockText, style = MaterialTheme.typography.labelSmall,
                        color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable private fun StarwarHelpCard(ui: StarwarState, vm: StarwarViewModel) {
    Panel(onClick = { vm.showHelp() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = HuluwaColors.Pine)
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("游戏帮助", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(ui.help.intro.ifBlank { "是男人就撑20秒游戏的网络对战版" },
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = HuluwaColors.Muted)
        }
    }
}

// ==================================================================== 房间列表

@Composable private fun StarwarRooms(ui: StarwarState, vm: StarwarViewModel) {
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.showCreateRoom() }, modifier = Modifier.weight(1f)) { Text("创建房间") }
            OutlinedButton(onClick = { vm.refreshRooms() }, modifier = Modifier.weight(1f)) { Text("刷新") }
        }
        val page = ui.roomPage
        if (page == null || page.rooms.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("还没有房间，点「创建房间」开一个", color = HuluwaColors.Muted)
            }
        } else {
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(page.rooms, key = { it.id }) { room ->
                    StarwarRoomRow(room) { vm.tapRoom(room) }
                }
            }
            // 原版分页栏：首页 / 上页 / 下页 / 末页 / 刷新（顺序与原文一致）
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    TextButton(onClick = { vm.firstPage() }) { Text("首页", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = { vm.page(-1) }) { Text("上页", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = { vm.page(1) }) { Text("下页", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = { vm.lastPage() }) { Text("末页", style = MaterialTheme.typography.labelMedium) }
                    TextButton(onClick = { vm.refreshRooms() }) { Text("刷新", style = MaterialTheme.typography.labelMedium) }
                }
                Text("第 ${ui.page + 1} / ${page.pages} 页 · 共 ${page.total} 个房间",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable private fun StarwarRoomRow(room: StarwarRoom, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarwarImage(StarwarArt.RANK, 16.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(room.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (room.hasPassword) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.Lock, "有密码", Modifier.size(14.dp), tint = HuluwaColors.Muted)
                    }
                }
                Text("房主 ${room.owner} · ${room.mapName} · ${room.members}/${room.capacity} 人",
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
            }
            val statusText = when (room.status) {
                "waiting" -> "等待中"
                "countdown" -> "倒计时"
                "playing" -> "游戏中"
                "settled" -> "已结束"
                else -> room.status
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(statusText, style = MaterialTheme.typography.labelMedium,
                    color = if (room.status == "waiting") HuluwaColors.Pine else HuluwaColors.Muted)
                if (room.status == "waiting") {
                    // 原版房间列表右侧的「进入」按钮
                    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("进入", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

// ==================================================================== 房间（等待聊天室）

@Composable private fun StarwarRoomScreen(ui: StarwarState, vm: StarwarViewModel) {
    val room = ui.room ?: return
    val me = room.memberList.firstOrNull { it.user == ui.profile.user }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Panel {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(room.name, style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold)
                            Text("房间 ${room.id} · ${room.mapName} · ${room.members}/${room.capacity}",
                                style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                            if (room.passwordHint.isNotBlank()) {
                                Text(room.passwordHint, style = MaterialTheme.typography.labelSmall,
                                    color = HuluwaColors.Muted)
                            }
                        }
                        if (ui.isOwner) {
                            // 房主可改：地X图 / 选X项 / 费X用 / 密X码
                            IconButton({ vm.openSettings() }, Modifier.size(36.dp)) {
                                Icon(Icons.Outlined.Settings, "房间设置", Modifier.size(20.dp),
                                    tint = HuluwaColors.Pine)
                            }
                        }
                        if (room.countdownEnd > 0 && room.status == "countdown") {
                            CountdownBadge(room.countdownEnd)
                        }
                    }
                }
            }
            item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StarwarImage(StarwarArt.LIST_ICON, 18.dp)
                Spacer(Modifier.width(6.dp))
                Text("玩家列表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
            items(room.memberList, key = { it.user }) { member ->
                StarwarMemberRow(member, ui, vm)
            }
            // 等待聊天室：原版进入房间即可看到聊天室，不用另外点开
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("聊天室", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = { vm.togglePhraseSheet() }) {
                        Text("短语", style = MaterialTheme.typography.labelSmall)
                    }
                    TextButton(onClick = { vm.openChat() }) {
                        Text("聊天窗口", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            item {
                // 聊天室公告（原文「聊天室公告：」+ 规则）
                if (ui.chat.notice.isNotBlank()) {
                    Text(ui.chat.notice, style = MaterialTheme.typography.labelSmall,
                        color = HuluwaColors.Muted,
                        modifier = Modifier.background(Color(0xFFFFF6E0), RoundedCornerShape(6.dp))
                            .padding(8.dp).fillMaxWidth())
                }
            }
            item {
                Column(Modifier.fillMaxWidth().heightIn(max = 180.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (ui.chat.messages.isEmpty()) {
                        Text("聊天室公告：请爱护聊天环境，发言间隔 10 秒",
                            style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    } else {
                        ui.chat.messages.takeLast(5).forEach { StarwarChatBubble(it) }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        ui.chatInput, vm::setChatInput,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("请输入聊天内容", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.width(6.dp))
                    Button({ vm.sendChat() }, enabled = ui.chatInput.isNotBlank()) {
                        Text("发送", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (ui.chat.nextAllowedAt > System.currentTimeMillis()) {
                    Text("聊天间隔10秒,请勿刷屏!", style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFD9A300), modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        StarwarRoomActions(ui, vm, room, me)
    }
}

/** 聊天气泡：系统消息居中灰字，玩家发言左右分列 */
@Composable
internal fun StarwarChatBubble(message: StarwarChatMessage) {
    if (message.kind == "system") {
        Text(message.content, style = MaterialTheme.typography.labelSmall,
            color = HuluwaColors.Muted, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
            textAlign = TextAlign.Center)
        return
    }
    Row(Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.background(
                if (message.mine) HuluwaColors.SoftGreen else Color(0xFFF1F3F4),
                RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp)
        ) {
            if (!message.mine) {
                Text(message.nick, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            }
            Text(message.content, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable private fun CountdownBadge(endEpochMs: Long) {
    var left by remember(endEpochMs) { mutableStateOf(maxOf(0L, endEpochMs - System.currentTimeMillis())) }
    LaunchedEffect(endEpochMs) {
        while (left > 0) {
            delay(200)
            left = maxOf(0L, endEpochMs - System.currentTimeMillis())
        }
    }
    Row(Modifier.background(HuluwaColors.Pine, RoundedCornerShape(8.dp))
        .padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        // 倒计时进度用原版 prgs1/2/3 三态贴图
        val frame = when ((left + 999) / 1000) {
            1L -> StarwarArt.PRGS3
            2L -> StarwarArt.PRGS2
            else -> StarwarArt.PRGS1
        }
        StarwarImage(frame, 14.dp)
        Spacer(Modifier.width(4.dp))
        Text("${(left + 999) / 1000} 秒后开始", color = Color.White,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun StarwarMemberRow(member: StarwarRoomMember, ui: StarwarState, vm: StarwarViewModel) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(
                if (member.team == 1) Color(0xFFFFE0E0) else HuluwaColors.SoftGreen, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) { StarwarImage(StarwarArt.plane(member.plane), 28.dp) }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(member.nick, style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (member.isOwner) {
                        Spacer(Modifier.width(4.dp))
                        Text("房主", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Pine)
                    }
                }
                Text("等级:${member.level} · 胜率:${member.winRate}% · 飞机:${member.plane}",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
            }
            if (member.user != ui.profile.user) {
                // 对方标：原版 oppos.bmp（组队战用 oppouse.bmp 表示「对面使用中」）
                StarwarImage(if (member.team == 0) StarwarArt.OPPOS else StarwarArt.OPPO_USE, 20.dp)
                IconButton({ vm.openOpponent(member.user) }, Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Info, "对手资料", Modifier.size(18.dp), tint = HuluwaColors.Muted)
                }
                if (ui.isOwner) {
                    IconButton({ vm.kick(member.user) }, Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.PersonRemove, "踢掉对手", Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                Text(if (member.ready) "已准备" else "未准备",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (member.ready) HuluwaColors.Pine else HuluwaColors.Muted)
            }
        }
    }
}

@Composable private fun StarwarRoomActions(ui: StarwarState, vm: StarwarViewModel,
                                           room: StarwarRoom, me: StarwarRoomMember?) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.leaveRoom() }, modifier = Modifier.weight(1f)) { Text("退出") }
            if (ui.isOwner) {
                Button(onClick = { vm.quickStart() }, modifier = Modifier.weight(1.4f),
                    enabled = room.memberList.size >= 2) { Text("快速开始") }
            } else {
                Button(onClick = { vm.toggleReady() }, modifier = Modifier.weight(1.4f)) {
                    Text(if (me?.ready == true) "取消准备" else "准备")
                }
            }
        }
    }
}

// ==================================================================== 对战

@Composable private fun StarwarBattle(ui: StarwarState, vm: StarwarViewModel) {
    val world = ui.world
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0F1A))) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween) {
            world?.players?.forEach { player ->
                Column {
                    Text(player.nick, color = if (player.alive) Color.White else Color.Gray,
                        style = MaterialTheme.typography.labelMedium)
                    Text("${player.score} 分", color = Color(0xFF8FE3C0),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            StarwarCanvas(ui, vm)
        }
        StarwarKeypad(ui, vm)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StarwarCanvas(ui: StarwarState, vm: StarwarViewModel) {
    val world = ui.world ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    // 原始位图转 ImageBitmap 并缓存：每帧不重复解码
    val bitmaps = remember {
        mapOf(
            "plane" to painterOf(context, StarwarArt.PLANE),
            "bullet" to painterOf(context, StarwarArt.BULLET),
            "bullet1" to painterOf(context, StarwarArt.BULLET1),
            "bullet2" to painterOf(context, StarwarArt.BULLET2),
            "prop" to painterOf(context, StarwarArt.PROP),
            "explode" to painterOf(context, StarwarArt.EXPLODE2),
            "explode1" to painterOf(context, StarwarArt.EXPLODE1),
            "explode3" to painterOf(context, StarwarArt.EXPLODE3),
            "damage1" to painterOf(context, StarwarArt.DAMAGE1),
            "damage2" to painterOf(context, StarwarArt.DAMAGE2),
            "damage3" to painterOf(context, StarwarArt.DAMAGE3),
            "shell" to painterOf(context, StarwarArt.SHELL),
            "flash" to painterOf(context, StarwarArt.FLASH),
            "surround" to painterOf(context, StarwarArt.SURROUND),
            "launch" to painterOf(context, StarwarArt.LAUNCH),
            "stone1" to painterOf(context, StarwarArt.STONE1),
            "stone2" to painterOf(context, StarwarArt.STONE2),
            "prop_heal" to painterOf(context, StarwarArt.LIVE),
            "prop_shield" to painterOf(context, StarwarArt.CRYSTAL),
            "prop_slow" to painterOf(context, StarwarArt.TIMEBAR),
            "prop_score" to painterOf(context, StarwarArt.LOTTERY),
        )
    }
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val scaleX = (maxWidth.value * 3f) / world.width
        val scaleY = (maxHeight.value * 3f) / world.height
        Box(
            Modifier.fillMaxSize()
                .background(Color(0xFF05070C), RoundedCornerShape(8.dp))
                .pointerInput(world.matchId) {
                    detectDragGestures(onDragEnd = { vm.input(0, 0) }) { change, drag ->
                        change.consume()
                        val dx = if (drag.x > 1f) 1 else if (drag.x < -1f) -1 else 0
                        val dy = if (drag.y > 1f) 1 else if (drag.y < -1f) -1 else 0
                        vm.input(dx, dy)
                    }
                }
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val sx = size.width / world.width
                val sy = size.height / world.height
                // 子弹：原始 12×12 同心圆贴图
                world.bullets.forEach { bullet ->
                    // 原版三种子弹：按局内时间轮换，视觉上不单调
                    val bulletKey = when ((world.elapsed / 5000) % 3) {
                        0L -> "bullet"
                        1L -> "bullet1"
                        else -> "bullet2"
                    }
                    bitmaps[bulletKey]?.let {
                        drawImage(
                            it, dstOffset = IntOffset(
                                ((bullet.x - 6) * sx).toInt(), ((bullet.y - 6) * sy).toInt()),
                            dstSize = IntSize((12 * sx).toInt().coerceAtLeast(2),
                                (12 * sy).toInt().coerceAtLeast(2)))
                    }
                }
                // 道具：原始道具贴图
                world.props.forEach { prop ->
                    val propKey = "prop_" + prop.kind
                    (bitmaps[propKey] ?: bitmaps["prop"])?.let {
                        drawImage(
                            it, dstOffset = IntOffset(
                                ((prop.x - 10) * sx).toInt(), ((prop.y - 10) * sy).toInt()),
                            dstSize = IntSize((20 * sx).toInt().coerceAtLeast(2),
                                (20 * sy).toInt().coerceAtLeast(2)))
                    }
                }
                // 陨石：原版 stone1 / stone2
                world.stones.forEach { stone ->
                    val key = if (stone.kind == 2) "stone2" else "stone1"
                    val size = if (stone.kind == 2) 18 else 12
                    bitmaps[key]?.let {
                        drawImage(
                            it, dstOffset = IntOffset(
                                ((stone.x - size / 2f) * sx).toInt(), ((stone.y - size / 2f) * sy).toInt()),
                            dstSize = IntSize((size * sx).toInt().coerceAtLeast(2),
                                (size * sy).toInt().coerceAtLeast(2)))
                    }
                }
                // 开局发射特效：原版 lauch.bmp，前 1 秒显示在自机下方
                if (world.elapsed < 1000) {
                    val mine = world.players.firstOrNull { it.user == ui.profile.user }
                    if (mine != null) {
                        bitmaps["launch"]?.let {
                            drawImage(
                                it, dstOffset = IntOffset(
                                    ((mine.x - 10) * sx).toInt(), ((mine.y + 8) * sy).toInt()),
                                dstSize = IntSize((20 * sx).toInt().coerceAtLeast(2),
                                    (40 * sy).toInt().coerceAtLeast(2)))
                        }
                    }
                }
                // 玩家：原始飞机贴图（阵亡改用爆炸贴图）
                world.players.forEach { player ->
                    // 存活时按剩余血量选受损贴图；阵亡后按时间轮换三帧爆炸
                    val key = when {
                        !player.alive -> when ((world.tick / 4) % 3) {
                            0 -> "explode1"
                            1 -> "explode"
                            else -> "explode3"
                        }
                        player.hp <= 20 -> "damage3"
                        player.hp <= 40 -> "damage2"
                        player.hp <= 60 -> "damage1"
                        else -> "plane"
                    }
                    (bitmaps[key] ?: bitmaps["plane"])?.let {
                        val w = if (player.alive) 20 else 28
                        val h = if (player.alive) 26 else 28
                        drawImage(
                            it, dstOffset = IntOffset(
                                ((player.x - w / 2f) * sx).toInt(), ((player.y - h / 2f) * sy).toInt()),
                            dstSize = IntSize((w * sx).toInt().coerceAtLeast(2),
                                (h * sy).toInt().coerceAtLeast(2)))
                    }
                    if (player.shield) {
                        drawCircle(Color(0x445AA7FF), radius = 14f * minOf(sx, sy),
                            center = Offset(player.x * sx, player.y * sy))
                    }
                }
            }
            // 生命条：原始 health 贴图做底
            Row(Modifier.align(Alignment.TopStart).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StarwarImage(StarwarArt.HEALTH, 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("${world.players.firstOrNull { it.user == ui.profile.user }?.hp ?: 0}",
                    color = Color(0xFF9FE8B0), style = MaterialTheme.typography.labelLarge)
            }
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                StarwarImage(StarwarArt.TIMEBAR, 16.dp)
                Spacer(Modifier.width(4.dp))
                Text("${(world.elapsed / 1000.0)} 秒", color = Color(0xFFCCD6E8),
                    style = MaterialTheme.typography.labelLarge)
            }
            // 20 秒目标进度条：原文「是男人就撑20秒」
            val me = world.players.firstOrNull { it.user == ui.profile.user }
            val progress = ((world.elapsed.toFloat() / 20_000f).coerceIn(0f, 1f))
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 60.dp)) {
                Text(if (me?.reachedTarget == true) "撑住20秒！" else "撑住 20 秒",
                    color = if (me?.reachedTarget == true) Color(0xFF7CE8B0) else Color(0xAAFFFFFF),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                Spacer(Modifier.height(3.dp))
                Box(Modifier.fillMaxWidth().height(5.dp)
                    .background(Color(0x44FFFFFF), RoundedCornerShape(3.dp))) {
                    Box(Modifier.fillMaxWidth(progress).fillMaxHeight()
                        .background(if (me?.reachedTarget == true) Color(0xFF7CE8B0) else Color(0xFFFFC15A),
                            RoundedCornerShape(3.dp)))
                }
            }
            // 组队战队伍标识：原版 team.bmp
            if ("team" == ui.room?.zone) {
                Row(Modifier.align(Alignment.CenterStart).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    StarwarImage(StarwarArt.TEAM, 22.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("组队战", color = Color(0xFFCCD6E8),
                        style = MaterialTheme.typography.labelSmall)
                }
            }
            // 道具栏：原版 pslot.bmp 槽位 + prop 道具贴着
            val myPlayer = world.players.firstOrNull { it.user == ui.profile.user }
            if (myPlayer != null) {
                Row(Modifier.align(Alignment.BottomEnd).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    StarwarImage(StarwarArt.PSLOT, 26.dp)
                    Spacer(Modifier.width(4.dp))
                    StarwarImage(StarwarArt.PROP, 20.dp)
                    Spacer(Modifier.width(4.dp))
                    Text("×${if (myPlayer.shield) 1 else 0}",
                        color = Color(0xFFCCD6E8), style = MaterialTheme.typography.labelSmall)
                }
            }
            // 玩家列表：AI 头像 + 昵称 + 分数
            Column(Modifier.align(Alignment.BottomStart).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)) {
                world.players.forEach { player ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 己方用 selfs，对方用 oppoself（原版「对面的己方」标记）
                        val mineTag = player.user == ui.profile.user
                        StarwarImage(
                            if (mineTag) StarwarArt.SELFS else StarwarArt.OPPO_SELF, 14.dp)
                        Spacer(Modifier.width(3.dp))
                        StarwarImage(StarwarArt.AI, 14.dp)
                        Spacer(Modifier.width(3.dp))
                        Text(player.nick, color = if (player.alive) Color.White else Color.Gray,
                            style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.width(4.dp))
                        StarwarImage(StarwarArt.NUMBER, 10.dp)
                        Spacer(Modifier.width(2.dp))
                        Text("${player.score}", color = Color(0xFF8FE3C0),
                            style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/** 资源 id → ImageBitmap（Compose 绘制用） */
private fun painterOf(context: android.content.Context, resId: Int): androidx.compose.ui.graphics.ImageBitmap {
    val d = androidx.core.content.ContextCompat.getDrawable(context, resId)
        ?: return androidx.compose.ui.graphics.ImageBitmap(1, 1)
    val bmp = android.graphics.Bitmap.createBitmap(
        d.intrinsicWidth.coerceAtLeast(1), d.intrinsicHeight.coerceAtLeast(1),
        android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    d.setBounds(0, 0, canvas.width, canvas.height)
    d.draw(canvas)
    return bmp.asImageBitmap()
}

@Composable private fun StarwarKeypad(ui: StarwarState, vm: StarwarViewModel) {
    Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f))
            KeyButton("▲\n2") { vm.input(0, -1) }
            Spacer(Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("◀\n4", Modifier.weight(1f)) { vm.input(-1, 0) }
            KeyButton("5", Modifier.weight(1f)) { vm.input(0, 0) }
            KeyButton("▶\n6", Modifier.weight(1f)) { vm.input(1, 0) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            KeyButton("*\n道具", Modifier.weight(1f)) { vm.useItem() }
            KeyButton("▼\n8", Modifier.weight(1f)) { vm.input(0, 1) }
            KeyButton("#\n聊天", Modifier.weight(1f)) { vm.openChat() }
        }
    }
}

@Composable private fun KeyButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier.height(48.dp).background(Color(0xFF1E293B), RoundedCornerShape(8.dp))
        .clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center)
    }
}

// ==================================================================== 结算

@Composable private fun StarwarResult(ui: StarwarState, vm: StarwarViewModel) {
    val rows = if (ui.result.isNotEmpty()) ui.result else ui.standings
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically) {
            StarwarImage(StarwarArt.SCORES, 26.dp)
            Spacer(Modifier.width(8.dp))
            Text("游戏排名结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (rows.isEmpty()) {
            Text("游戏结束！", style = MaterialTheme.typography.bodyMedium,
                color = HuluwaColors.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { standing ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
                            if (standing.ranking == 1) {
                                // 冠军用原始 crown.bmp 王冠贴图
                                StarwarImage(StarwarArt.CROWN, 20.dp)
                            } else {
                                Text("${standing.ranking}", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (standing.ranking == 2) Color(0xFF9AA4B2)
                                            else if (standing.ranking == 3) Color(0xFFB87333)
                                            else HuluwaColors.Muted)
                            }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(standing.nickname.ifBlank { standing.username },
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${standing.score} 分 · 撑住 ${standing.survivedMs / 1000.0} 秒",
                                style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                        }
                        if (standing.survivedTarget) {
                            Text("撑住20秒", style = MaterialTheme.typography.labelSmall,
                                color = HuluwaColors.Pine)
                        }
                    }
                }
            }
        }
        Text("请注意：每局游戏结束后奖励的水晶，在本局游戏所有玩家都结束游戏并公布最终排名信息后才会增加。",
            style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
        Spacer(Modifier.weight(1f))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.exitBattle() }, modifier = Modifier.weight(1f)) { Text("继续") }
            OutlinedButton(onClick = { vm.backToRoomsFromResult() }, modifier = Modifier.weight(1f)) { Text("返回") }
            Button(onClick = { vm.playAgain() }, modifier = Modifier.weight(1f)) { Text("再来一局!") }
        }
    }
}

// ==================================================================== 弹窗

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StarwarCreateRoomDialog(ui: StarwarState, vm: StarwarViewModel) {
    var name by remember { mutableStateOf(ui.profile.nick + "的房间") }
    var mapId by remember { mutableIntStateOf(ui.planets.firstOrNull { !it.locked }?.id ?: 2) }
    var items by remember { mutableStateOf(true) }
    var costMode by remember { mutableIntStateOf(0) }
    var password by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { vm.hideCreateRoom() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("创建房间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it.take(16) }, label = { Text("房间名") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("选择星球", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ui.planets.forEach { planet ->
                        FilterChip(selected = mapId == planet.id, enabled = !planet.locked,
                            onClick = { mapId = planet.id },
                            label = { Text(planet.name, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = items, onClick = { items = !items }, label = { Text("开启道具") })
                }
                Text("费用", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "免费", 1 to "K币", 2 to "水晶").forEach { (mode, label) ->
                        FilterChip(selected = costMode == mode, onClick = { costMode = mode },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                OutlinedTextField(password, { password = it.take(8) }, label = { Text("房间密码（可空）") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("房间密码默认为空,如非特别需要,请不要设定密码。",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.hideCreateRoom() }) { Text("返回") }
                    Button({ vm.createRoom(name, mapId, items, costMode, password) }) { Text("确定") }
                }
            }
        }
    }
}

@Composable private fun StarwarPasswordDialog(ui: StarwarState, vm: StarwarViewModel) {
    var password by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { vm.dismissPasswordPrompt() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("请输入房间密码", style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(password, { password = it.take(8) }, label = { Text("密 码") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.dismissPasswordPrompt() }) { Text("返回") }
                    Button({ vm.enterRoomWithPassword(password) }) { Text("确定") }
                }
            }
        }
    }
}

@Composable private fun StarwarHelpDialog(ui: StarwarState, vm: StarwarViewModel) {
    Dialog(onDismissRequest = { vm.hideHelp() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            LazyColumn(Modifier.padding(20.dp).heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StarwarImage(StarwarArt.OPERATE, 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("游戏帮助", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    }
                }
                item { Text(ui.help.introTitle, fontWeight = FontWeight.SemiBold) }
                item { Text(ui.help.intro, style = MaterialTheme.typography.bodySmall) }
                item { Text(ui.help.howToTitle, fontWeight = FontWeight.SemiBold) }
                item { Text(ui.help.howTo, style = MaterialTheme.typography.bodySmall) }
                item { Text(ui.help.helpTitle, fontWeight = FontWeight.SemiBold) }
                items(ui.help.helpLines) { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton({ vm.hideHelp() }) { Text("确定") }
                    }
                }
            }
        }
    }
}

@Composable private fun StarwarChatDialog(ui: StarwarState, vm: StarwarViewModel) {
    val listState = rememberLazyListState()
    LaunchedEffect(ui.chat.messages.size) {
        if (ui.chat.messages.isNotEmpty()) listState.animateScrollToItem(ui.chat.messages.size - 1)
    }
    Dialog(onDismissRequest = { vm.closeChat() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 520.dp).padding(16.dp)) {
                Text("聊天室", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (ui.chat.notice.isNotBlank()) {
                    Text(ui.chat.notice, style = MaterialTheme.typography.labelSmall,
                        color = HuluwaColors.Muted, modifier = Modifier.padding(vertical = 6.dp))
                }
                LazyColumn(Modifier.weight(1f), state = listState,
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ui.chat.messages, key = { it.id }) { message ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start) {
                            if (message.kind == "system") {
                                Text(message.content, style = MaterialTheme.typography.labelSmall,
                                    color = HuluwaColors.Muted, modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Center)
                            } else {
                                Column(Modifier.background(
                                    if (message.mine) HuluwaColors.SoftGreen else Color(0xFFF1F3F4),
                                    RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                                    Text(message.nick, style = MaterialTheme.typography.labelSmall,
                                        color = HuluwaColors.Muted)
                                    Text(message.content, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(ui.chatInput, vm::setChatInput, modifier = Modifier.weight(1f),
                        placeholder = { Text("请输入聊天内容", style = MaterialTheme.typography.bodySmall) },
                        singleLine = true)
                    Spacer(Modifier.width(8.dp))
                    Button({ vm.sendChat() }) { Text("发送") }
                }
            }
        }
    }
}

@Composable private fun StarwarPhraseDialog(ui: StarwarState, vm: StarwarViewModel) {
    Dialog(onDismissRequest = { vm.togglePhraseSheet() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("短语", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                ui.chat.phrases.forEach { phrase ->
                    Text(phrase, modifier = Modifier.fillMaxWidth()
                        .clickable { vm.sendChat(phrase, kind = "phrase"); vm.togglePhraseSheet() }
                        .padding(vertical = 10.dp))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.openPhraseEdit() }) { Text("短语设置") }
                    TextButton({ vm.togglePhraseSheet() }) { Text("返回") }
                }
            }
        }
    }
}

@Composable private fun StarwarPhraseEditDialog(ui: StarwarState, vm: StarwarViewModel) {
    Dialog(onDismissRequest = { vm.closePhraseEdit() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("短语设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("原版 6 条快捷短语，聊天时一键发送", style = MaterialTheme.typography.labelSmall,
                    color = HuluwaColors.Muted)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    itemsIndexed(ui.phraseDraft) { index, value ->
                        OutlinedTextField(
                            value, { vm.setPhraseDraft(index, it) },
                            label = { Text("短语 ${index + 1}", style = MaterialTheme.typography.labelSmall) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                            textStyle = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.closePhraseEdit() }) { Text("返回") }
                    Button({ vm.savePhrases() }) { Text("确定") }
                }
            }
        }
    }
}

@Composable private fun StarwarShopDialog(ui: StarwarState, vm: StarwarViewModel) {
    val shop = ui.shop ?: return
    Dialog(onDismissRequest = { vm.closeShop() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp).heightIn(max = 520.dp)) {
                Text(shop.title.ifBlank { "飞机商店" }, style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarwarImage(StarwarArt.GOLD, 16.dp)
                    Text(" ${shop.gold}", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(12.dp))
                    StarwarImage(StarwarArt.CRYSTAL, 16.dp)
                    Text(" ${shop.crystal}", style = MaterialTheme.typography.labelMedium)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shop.items, key = { it.type + it.id }) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            // 飞机用原始 plane 贴图预览；引擎用 engine 贴图
                            StarwarImage(
                                if (item.type == "plane") StarwarArt.plane(item.id) else StarwarArt.ENGINE,
                                36.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                Text(item.desc, style = MaterialTheme.typography.labelSmall,
                                    color = HuluwaColors.Muted)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (item.gold > 0) {
                                        StarwarImage(StarwarArt.GOLD, 14.dp)
                                        Text(" ${item.gold}", style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFFD9A300))
                                    } else {
                                        StarwarImage(StarwarArt.CRYSTAL, 14.dp)
                                        Text(" ${item.crystal}", style = MaterialTheme.typography.labelSmall,
                                            color = Color(0xFF4E8BD9))
                                    }
                                }
                            }
                            if (item.owned) {
                                Text("已拥有", style = MaterialTheme.typography.labelSmall,
                                    color = HuluwaColors.Muted)
                            } else {
                                Button({ vm.buy(item.type, item.id) }) { Text("购买") }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.closeShop() }) { Text("返回") }
                }
            }
        }
    }
}

@Composable private fun StarwarHangarDialog(ui: StarwarState, vm: StarwarViewModel) {
    Dialog(onDismissRequest = { vm.closeHangar() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp).heightIn(max = 480.dp)) {
                Text("我的机库", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ui.hangar) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val isPlane = item["type"] == "plane"
                            StarwarImage(
                                if (isPlane) StarwarArt.plane(item["id"].orEmpty()) else StarwarArt.ENGINE,
                                32.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item["id"].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                Text(if (isPlane) "飞机" else "引擎",
                                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                            }
                            val equipped = (isPlane && item["id"] == ui.profile.plane) ||
                                    (!isPlane && item["id"] == ui.profile.engine)
                            if (equipped) {
                                // 原版 youuse.bmp：表示「你正在使用」
                                StarwarImage(StarwarArt.YOU_USE, 18.dp)
                                Spacer(Modifier.width(4.dp))
                                Text("使用中", style = MaterialTheme.typography.labelSmall,
                                    color = HuluwaColors.Pine)
                            } else {
                                TextButton({ vm.equip(item["type"].orEmpty(), item["id"].orEmpty()) }) {
                                    Text("装备")
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.closeHangar() }) { Text("返回") }
                }
            }
        }
    }
}

@Composable private fun StarwarRankingDialog(ui: StarwarState, vm: StarwarViewModel) {
    Dialog(onDismissRequest = { vm.closeRanking() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp).heightIn(max = 520.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarwarImage(StarwarArt.SCORES, 22.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("游戏排行榜", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ui.ranking) { row ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("${row.ranking}", Modifier.width(32.dp),
                                fontWeight = FontWeight.Bold, color = HuluwaColors.Muted)
                            Text(row.nick.ifBlank { row.user }, Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium)
                            Text("${row.totalScore} 分", style = MaterialTheme.typography.labelMedium,
                                color = HuluwaColors.Pine)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.closeRanking() }) { Text("返回") }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun StarwarSettingsDialog(ui: StarwarState, vm: StarwarViewModel) {
    val room = ui.room ?: return
    var password by remember { mutableStateOf("") }
    Dialog(onDismissRequest = { vm.closeSettings() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp).heightIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Settings, null, tint = HuluwaColors.Pine)
                    Spacer(Modifier.width(8.dp))
                    Text("房间设置", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Text("地1图", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ui.planets.forEach { planet ->
                        FilterChip(selected = room.mapId == planet.id, enabled = !planet.locked,
                            onClick = { vm.updateSettings(mapId = planet.id) },
                            label = { Text(planet.name, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Text("选2项", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = room.itemMode == 1, onClick = { vm.updateSettings(items = true) },
                        label = { Text("开启道具", style = MaterialTheme.typography.labelSmall) })
                    FilterChip(selected = room.itemMode == 0, onClick = { vm.updateSettings(items = false) },
                        label = { Text("关闭道具", style = MaterialTheme.typography.labelSmall) })
                }
                Text("费3用", style = MaterialTheme.typography.labelMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0 to "免费", 1 to "K币", 2 to "水晶").forEach { (mode, label) ->
                        FilterChip(selected = room.costMode == mode,
                            onClick = { vm.updateSettings(costMode = mode) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) })
                    }
                }
                Text("密4码", style = MaterialTheme.typography.labelMedium)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(password, { password = it.take(8) },
                        label = { Text("房间密码（留空即清除）", style = MaterialTheme.typography.labelSmall) },
                        singleLine = true, modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Button({ vm.updateSettings(password = password); password = "" }) { Text("应用") }
                }
                Text("房间密码默认为空,如非特别需要,请不要设定密码。",
                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.closeSettings() }) { Text("返回") }
                }
            }
        }
    }
}

@Composable private fun StarwarStatusDialog(ui: StarwarState, vm: StarwarViewModel) {
    Dialog(onDismissRequest = { vm.closeStatus() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("我的状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarwarImage(StarwarArt.plane(ui.profile.plane), 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(ui.profile.nick.ifBlank { ui.profile.user },
                            style = MaterialTheme.typography.titleSmall)
                        Text("等级:${ui.profile.level} · ${ui.profile.rank}",
                            style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                        Text("胜率:${ui.profile.winRate}% · 飞机:${ui.profile.plane}",
                            style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StarwarImage(StarwarArt.GOLD, 28.dp)
                        Text("${ui.profile.gold}", style = MaterialTheme.typography.labelMedium)
                        Text("K币", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StarwarImage(StarwarArt.CRYSTAL, 28.dp)
                        Text("${ui.profile.crystal}", style = MaterialTheme.typography.labelMedium)
                        Text("水晶", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StarwarImage(StarwarArt.CROWN, 28.dp)
                        Text("${ui.profile.wins}", style = MaterialTheme.typography.labelMedium)
                        Text("胜场", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        StarwarImage(StarwarArt.AI, 28.dp)
                        Text("${ui.profile.losses}", style = MaterialTheme.typography.labelMedium)
                        Text("负场", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                }
                HorizontalDivider()
                Text("我的状态", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                Text("${ui.profile.wins} 胜 ${ui.profile.losses} 负 · 累计 ${ui.profile.matches} 场",
                    style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.closeStatus() }) { Text("确定") }
                }
            }
        }
    }
}

@Composable private fun StarwarOpponentDialog(ui: StarwarState, vm: StarwarViewModel) {
    val data = ui.opponent ?: return
    Dialog(onDismissRequest = { vm.closeOpponent() }) {
        Surface(RoundedCornerShape(16.dp), color = Color.White) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("对手资料", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(data["nick"].orEmpty().ifBlank { data["user"].orEmpty() },
                    style = MaterialTheme.typography.titleSmall)
                Text("等级:${data["level"]} · ${data["rank"]}", style = MaterialTheme.typography.bodySmall)
                Text("胜率:${data["winRate"]}% · ${data["wins"]} 胜 ${data["losses"]} 负",
                    style = MaterialTheme.typography.bodySmall)
                Text("飞机:${data["plane"]}", style = MaterialTheme.typography.bodySmall)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton({ vm.addFriend(data["user"].orEmpty()) }) { Text("加为好友") }
                    TextButton({ vm.closeOpponent() }) { Text("返回") }
                }
            }
        }
    }
}

// ==================================================================== 小工具

private fun Modifier.alphaIf(dimmed: Boolean): Modifier =
    if (dimmed) this.then(Modifier.alpha(0.45f)) else this

