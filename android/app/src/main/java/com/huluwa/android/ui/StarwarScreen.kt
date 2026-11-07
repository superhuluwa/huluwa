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
    val back = {
        when (ui.stage) {
            StarwarStage.Battle, StarwarStage.Result -> vm.leaveRoom()
            StarwarStage.Room -> vm.leaveRoom()
            StarwarStage.Rooms -> vm.loadRooms()   // 回到大厅
            StarwarStage.Lobby -> onClose()
        }
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
    // 等待聊天室轮询：WebSocket 只推新消息，进入房间时补一次历史
    LaunchedEffect(ui.stage, ui.roomId) {
        if (ui.stage == StarwarStage.Room && ui.roomId.isNotBlank()) {
            while (true) {
                delay(5_000)
                vm.loadRoom(ui.roomId)
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

    if (ui.createRoomOpen) StarwarCreateRoomDialog(ui, vm)
    if (ui.passwordPromptRoom != null) StarwarPasswordDialog(ui, vm)
    if (ui.helpOpen) StarwarHelpDialog(ui, vm)
    if (ui.shopOpen) StarwarShopDialog(ui, vm)
    if (ui.hangarOpen) StarwarHangarDialog(ui, vm)
    if (ui.rankingOpen) StarwarRankingDialog(ui, vm)
    if (ui.opponent != null) StarwarOpponentDialog(ui, vm)
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
        item { StarwarPlanetsCard(ui) }
        item { StarwarHelpCard(ui, vm) }
    }
}


@Composable private fun StarwarProfileCard(ui: StarwarState, vm: StarwarViewModel) {
    Panel {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(HuluwaColors.SoftGreen, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center) {
                Text("✈", style = MaterialTheme.typography.headlineMedium)
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
                contentAlignment = Alignment.Center) { Text("🚀") }
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
        Text("九大星球", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ui.planets.forEach { planet ->
                Column(horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(64.dp).alphaIf(planet.locked)) {
                    Box(Modifier.size(40.dp).background(
                        if (planet.locked) Color(0xFFE0E0E0) else HuluwaColors.SoftGreen),
                        contentAlignment = Alignment.Center) {
                        Text(if (planet.locked) "🔒" else "🪐")
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
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                TextButton(onClick = { vm.page(-1) }) { Text("上页") }
                Text("第 ${ui.page + 1} / ${page.pages} 页", style = MaterialTheme.typography.bodySmall,
                    color = HuluwaColors.Muted, modifier = Modifier.align(Alignment.CenterVertically))
                TextButton(onClick = { vm.page(1) }) { Text("下页") }
            }
        }
    }
}

@Composable private fun StarwarRoomRow(room: StarwarRoom, onClick: () -> Unit) {
    Panel(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
            Text(statusText, style = MaterialTheme.typography.labelMedium,
                color = if (room.status == "waiting") HuluwaColors.Pine else HuluwaColors.Muted)
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
                        if (room.countdownEnd > 0 && room.status == "countdown") {
                            CountdownBadge(room.countdownEnd)
                        }
                    }
                }
            }
            item { Text("玩家列表", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold) }
            items(room.memberList, key = { it.user }) { member ->
                StarwarMemberRow(member, ui, vm)
            }
            item {
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.openChat() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.Chat, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("聊天室")
                    }
                    OutlinedButton(onClick = { vm.togglePhraseSheet() }, modifier = Modifier.weight(1f)) {
                        Text("短语")
                    }
                }
            }
        }
        StarwarRoomActions(ui, vm, room, me)
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
    Box(Modifier.background(HuluwaColors.Pine, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
        Text("${(left + 999) / 1000} 秒后开始", color = Color.White,
            style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun StarwarMemberRow(member: StarwarRoomMember, ui: StarwarState, vm: StarwarViewModel) {
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).background(
                if (member.team == 1) Color(0xFFFFE0E0) else HuluwaColors.SoftGreen, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center) { Text("✈") }
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

@Composable private fun StarwarCanvas(ui: StarwarState, vm: StarwarViewModel) {
    val world = ui.world ?: return
    val scale = remember(world.width, world.height) { 1f }
    BoxWithConstraints(Modifier.fillMaxSize().padding(8.dp)) {
        val canvasWidth = maxWidth
        val canvasHeight = maxHeight
        Canvas(Modifier.fillMaxSize().background(Color(0xFF101828), RoundedCornerShape(8.dp))
            .pointerInput(world.matchId) {
                detectDragGestures(onDragEnd = { vm.input(0, 0) }) { change, drag ->
                    change.consume()
                    // 拖拽即移动：把位移换算成方向输入
                    val dx = if (drag.x > 1f) 1 else if (drag.x < -1f) -1 else 0
                    val dy = if (drag.y > 1f) 1 else if (drag.y < -1f) -1 else 0
                    vm.input(dx, dy)
                }
            }) {
            val sx = size.width / world.width
            val sy = size.height / world.height
            // 子弹
            world.bullets.forEach { bullet ->
                drawCircle(Color(0xFFFF5A5A), radius = 3f * minOf(sx, sy),
                    center = Offset(bullet.x * sx, bullet.y * sy))
            }
            // 道具
            world.props.forEach { prop ->
                val color = when (prop.kind) {
                    "heal" -> Color(0xFF5AD55A)
                    "shield" -> Color(0xFF5AA7FF)
                    "slow" -> Color(0xFFFFD45A)
                    else -> Color(0xFFC55AFF)
                }
                drawCircle(color, radius = 6f * minOf(sx, sy), center = Offset(prop.x * sx, prop.y * sy))
            }
            // 玩家
            world.players.forEach { player ->
                val center = Offset(player.x * sx, player.y * sy)
                if (player.shield) {
                    drawCircle(Color(0x665AA7FF), radius = 12f * minOf(sx, sy), center = center)
                }
                drawPlayer(center, minOf(sx, sy), player.alive,
                    if (player.user == ui.profile.user) Color(0xFF7CE8B0) else Color(0xFFFFC15A))
            }
        }
        Text("${(world.elapsed / 1000.0)} 秒", color = Color(0x88FFFFFF),
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
        Text("生命 ${world.players.firstOrNull { it.user == ui.profile.user }?.hp ?: 0}",
            color = Color(0x88FFFFFF), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
    }
}

private fun DrawScope.drawPlayer(center: Offset, unit: Float, alive: Boolean, color: Color) {
    if (!alive) {
        drawCircle(Color(0x55FF5A5A), radius = 8f * unit, center = center)
        return
    }
    val size = Size(14f * unit, 14f * unit)
    drawRect(color, topLeft = Offset(center.x - size.width / 2, center.y - size.height / 2), size = size)
    drawRect(Color.White, topLeft = Offset(center.x - 1f * unit, center.y - size.height / 2 - 3f * unit),
        size = Size(2f * unit, 4f * unit))
}

/** 原版软键盘：2/8/4/6 方向、5 确定、* 使用道具、# 聊天 */
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
        Text("游戏排名结果", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        if (rows.isEmpty()) {
            Text("游戏结束！", style = MaterialTheme.typography.bodyMedium,
                color = HuluwaColors.Muted, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        }
        Panel {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { standing ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${standing.ranking}", style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = when (standing.ranking) {
                                1 -> Color(0xFFD9A300)
                                2 -> Color(0xFF9AA4B2)
                                3 -> Color(0xFFB87333)
                                else -> HuluwaColors.Muted
                            }, modifier = Modifier.width(32.dp))
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
            OutlinedButton(onClick = { vm.exitBattle() }, modifier = Modifier.weight(1f)) { Text("返回") }
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
                item { Text("游戏帮助", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
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
                    TextButton({ vm.togglePhraseSheet() }) { Text("返回") }
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
                Text("${shop.gold} K币 · ${shop.crystal} 水晶", style = MaterialTheme.typography.labelMedium,
                    color = HuluwaColors.Muted)
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(shop.items, key = { it.type + it.id }) { item ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.bodyMedium)
                                Text(item.desc, style = MaterialTheme.typography.labelSmall,
                                    color = HuluwaColors.Muted)
                                Text(if (item.gold > 0) "${item.gold} K币" else "${item.crystal} 水晶",
                                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFD9A300))
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
                            Column(Modifier.weight(1f)) {
                                Text(item["id"].orEmpty(), style = MaterialTheme.typography.bodyMedium)
                                Text(if (item["type"] == "plane") "飞机" else "引擎",
                                    style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                            }
                            TextButton({ vm.equip(item["type"].orEmpty(), item["id"].orEmpty()) }) { Text("装备") }
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
                Text("游戏排行榜", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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

