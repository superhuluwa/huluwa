package com.huluwa.android.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.huluwa.android.*
import com.huluwa.android.data.*
import kotlinx.coroutines.flow.first

@Composable fun ChatScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val chat = ui.chat; val session = chat.session ?: return
    val displayMessages = remember(chat.messages) { mergeVideoImageMessages(chat.messages) }
    val reconnecting = rememberReconnecting(vm)
    val list = rememberLazyListState()
    var content by rememberSaveable(session.id) { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var activeVideoId by remember(session.id) { mutableStateOf<Long?>(null) }
    var initialized by remember(session.id) { mutableStateOf(false) }
    val nearBottom by remember { derivedStateOf { list.firstVisibleItemIndex <= 2 } }
    LaunchedEffect(session.id, chat.messages.lastOrNull()?.id, chat.processing) {
        if (chat.messages.isNotEmpty() && (!initialized || nearBottom || chat.messages.last().mine)) {
            list.scrollToItem(0); initialized = true
        }
    }
    // 列表仅保留当前点击的视频；完全滑出可见区域后恢复封面，避免滚回时自动播放。
    LaunchedEffect(session.id, activeVideoId) {
        val messageId = activeVideoId ?: return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == messageId } }.first { visible -> !visible }
        activeVideoId = null
    }
    // 整个聊天布局统一避让键盘，消息区与输入栏随系统键盘动画一起调整高度。
    Scaffold(modifier = Modifier.fillMaxSize().imePadding(), containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(vm::closeChat) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回会话") }
                    SessionIcon(session.kind, Modifier.size(38.dp))
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // 标题按剩余宽度省略，确保右侧的重连徽标始终可见。
                            Text(session.title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            TitleReconnectBadge(reconnecting)
                        }
                        Text(if (ui.realtime) "实时消息已连接" else "消息自动轮询同步", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    }
                    IconButton({ vm.refreshChat() }) { Icon(Icons.Outlined.Refresh, "刷新会话") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "会话操作") }
                        DropdownMenu(menu, { menu = false }) {
                            if (session.kind != "system") DropdownMenuItem(text = { Text("服务设置") }, onClick = { menu = false; vm.showSettings(session.kind) })
                            if (session.kind != "system") DropdownMenuItem(text = { Text("清空会话") }, onClick = { menu = false; vm.clearChat() })
                        }
                    }
                }
            }
        }, bottomBar = {
            if (session.kind != "system") Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(content, { content = it }, Modifier.weight(1f), maxLines = 4,
                        placeholder = { Text(when (session.kind) { "magnet", "xunlei", "xunlei-win" -> "粘贴下载链接…"; "video" -> "粘贴分享口令…"; "movie-hls" -> "输入片名或新版详情链接…"; else -> "输入片名，试试搜点什么…" }, style = MaterialTheme.typography.bodySmall) },
                        textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(16.dp),
                        colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = HuluwaColors.Line, unfocusedContainerColor = HuluwaColors.Paper))
                    FilledIconButton({ val text = content.trim(); if (text.isNotEmpty()) { vm.send(text); content = "" } }, enabled = content.isNotBlank() && !chat.loading,
                        modifier = Modifier.size(48.dp), shape = RoundedCornerShape(15.dp)) { Icon(Icons.AutoMirrored.Outlined.Send, "发送消息") }
                }
            } else Text("系统通知会自动同步到这里", Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), color = HuluwaColors.Muted,
                style = MaterialTheme.typography.bodySmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }) { padding ->
        // 从最新消息处锚定：键盘改变可用高度时，消息随底部上移；短会话仍从顶部排列。
        LazyColumn(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), state = list, reverseLayout = true,
            contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(17.dp, Alignment.Top)) {
            if (chat.processing) item("processing") { Text("正在处理，请稍候…", style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted, modifier = Modifier.padding(start = 43.dp)) }
            items(displayMessages.asReversed(), key = { it.id }) { message ->
                MessageBubble(message, session.kind, ui, vm,
                    playingVideo = activeVideoId == message.id,
                    onVideoPlay = { activeVideoId = message.id },
                    onVideoStop = { if (activeVideoId == message.id) activeVideoId = null },
                    onMedia = { url, video -> activeVideoId = null; media = url to video })
            }
            item("history") {
                // 加载状态统一显示在标题栏菊花上，消息区只保留错误反馈，避免列表被进度条挤动。
                Feedback(error = chat.error.takeUnless { it.isServerUnreachable() }, onRetry = { if (chat.messages.isEmpty()) vm.openSession(session) else vm.refreshChat() })
                if (chat.hasEarlier) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(vm::loadEarlier, enabled = !chat.loadingEarlier) { Text(if (chat.loadingEarlier) "正在加载…" else "查看更早消息") }
                }
                if (!chat.loading && chat.messages.isEmpty() && chat.error == null) EmptyContent("发条消息，开始吧", "搜索影视、提交下载，都可以从这里开始", sessionIcon(session.kind))
            }
        }
    }
    media?.let { (url, video) -> MediaViewer(url, video) { media = null } }
}

@Composable private fun MessageBubble(
    message: ChatMessage, kind: String, ui: AppState, vm: HuluwaViewModel,
    playingVideo: Boolean, onVideoPlay: () -> Unit, onVideoStop: () -> Unit, onMedia: (String, Boolean) -> Unit,
) {
    val notify = message.notify
    val galleryImages = remember(message.payload) { message.videoImages }
    if (notify != null) { NotifyMessageCard(notify, kind, timeLabel(message.createdAt)); return }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!message.mine) { SessionIcon(kind, Modifier.size(32.dp)); Spacer(Modifier.width(9.dp)) }
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
            Surface(color = if (message.mine) Color(0xFFDFEBD4) else Color.White,
                shape = RoundedCornerShape(topStart = if (message.mine) 17.dp else 4.dp, topEnd = if (message.mine) 4.dp else 17.dp, bottomStart = 17.dp, bottomEnd = 17.dp),
                border = BorderStroke(1.dp, HuluwaColors.Line)) {
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val film = message.film
                    when {
                        film != null -> FilmResultCard(film, ui, vm)
                        message.type == "image" && galleryImages.size > 1 -> {
                            VideoImageGallery(galleryImages, playingVideo, onVideoPlay, onVideoStop) { onMedia(it, false) }
                            val caption = plainText(message.contentText)
                            if (caption.isNotBlank()) SelectionContainer { Text(caption, color = HuluwaColors.Ink, style = MaterialTheme.typography.bodyMedium) }
                        }
                        message.type == "image" && message.content.isNotBlank() -> AsyncImage(message.content, "消息图片",
                            Modifier.width(240.dp).heightIn(min = 150.dp, max = 300.dp).clickable { onMedia(message.content, false) }, contentScale = ContentScale.Fit)
                        message.type == "video" && message.content.isNotBlank() -> {
                            InlineVideoBubble(message.content, message.payload.text("poster", "cover"), playingVideo, onVideoPlay, onVideoStop)
                            val caption = plainText(message.contentText)
                            if (caption.isNotBlank()) SelectionContainer { Text(caption, color = HuluwaColors.Ink, style = MaterialTheme.typography.bodyMedium) }
                        }
                        else -> {
                            val text = plainText(message.contentText.ifBlank { message.content })
                            if (text.isNotBlank()) SelectionContainer { Text(text, color = if (message.type == "error") HuluwaColors.Danger else HuluwaColors.Ink, style = MaterialTheme.typography.bodyMedium) }
                            val video = message.payload.text("videoUrl", "video", "url")
                            if (message.payload.text("kind") == "video" && video.startsWith("http")) {
                                InlineVideoBubble(video, message.payload.text("poster", "cover"), playingVideo, onVideoPlay, onVideoStop)
                            }
                            if (galleryImages.isNotEmpty()) VideoImageGallery(galleryImages, playingVideo, onVideoPlay, onVideoStop) { onMedia(it, false) }
                            if (text.isBlank() && video.isBlank() && galleryImages.isEmpty()) Text("收到一条服务消息", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            if (message.delivery == Delivery.Sending) Text("发送中…", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 5.dp))
            if (message.delivery == Delivery.Failed) TextButton({ vm.send(message.content, message) }) { Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("发送失败，点击重试", color = HuluwaColors.Danger) }
            Text(timeLabel(message.createdAt), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable private fun NotifyMessageCard(message: NotifyMessage, kind: String, time: String) {
    val accent = when (message.level) {
        "success" -> HuluwaColors.Pine
        "warning" -> HuluwaColors.Amber
        "error" -> HuluwaColors.Danger
        else -> HuluwaColors.Blue
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        SessionIcon(kind, Modifier.size(32.dp)); Spacer(Modifier.width(9.dp))
        Surface(Modifier.weight(1f), color = Color.White, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, HuluwaColors.Line)) {
            Column(Modifier.padding(horizontal = 13.dp, vertical = 11.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                    Text(message.levelLabel, color = accent, style = MaterialTheme.typography.labelMedium)
                    if (message.source.isNotBlank()) Text(message.source, Modifier.weight(1f), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Spacer(Modifier.weight(1f))
                    Text(time, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
                if (message.title.isNotBlank()) Text(message.title, style = MaterialTheme.typography.titleMedium, color = HuluwaColors.Ink)
                val body = plainText(if (message.body.isNotBlank()) message.body else message.title)
                if (body.isNotBlank()) SelectionContainer { Text(body, style = MaterialTheme.typography.bodyMedium, color = HuluwaColors.Ink) }
            }
        }
    }
}
@Composable private fun FilmResultCard(card: FilmCard, ui: AppState, vm: HuluwaViewModel) {
    val subscribed = if (card.subscriptionSource in ui.subscriptionSourcesLoaded)
        ui.subscriptions.data.any { it.enabled && card.matchesSubscription(it) } else card.subscribed
    val followBusy = ui.busy == "film-follow:${card.key()}"
    val websiteBusy = ui.busy == "film-website:${card.key()}"
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(card.title, Modifier.weight(1f).padding(top = 4.dp), style = MaterialTheme.typography.titleMedium,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
        if (!card.isMovieHls) FilmFollowButton(subscribed, followBusy, enabled = ui.busy == null) {
            vm.setFilmFollowing(card, !subscribed)
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(11.dp)) {
        Poster(card.title, card.cover, Modifier.width(65.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val metadata = listOf(card.year, card.genre, card.origin).filter(String::isNotBlank).joinToString(" · ")
            if (metadata.isNotBlank()) Text(metadata, style = MaterialTheme.typography.bodySmall,
                color = HuluwaColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Tag(card.sourceTitle)
        }
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedButton({ vm.filmResources(card) }, Modifier.fillMaxWidth().heightIn(min = 44.dp),
            shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, HuluwaColors.Line)) {
            Icon(Icons.Outlined.FormatListBulleted, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text("资源列表")
        }
        TextButton({ vm.openFilmWebsite(card) }, Modifier.fillMaxWidth().heightIn(min = 44.dp),
            enabled = ui.busy == null, shape = RoundedCornerShape(12.dp)) {
            if (websiteBusy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
            else Icon(Icons.Outlined.Language, null, Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (websiteBusy) "正在打开…" else "网站直达")
        }
    }
}

/** 书签与加减徽标表示开始 / 停止追更，两种来源共用同一个右上角操作。 */
@Composable private fun FilmFollowButton(subscribed: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick, Modifier.widthIn(min = 96.dp).heightIn(min = 44.dp), enabled = enabled,
        shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 9.dp, vertical = 7.dp),
        border = BorderStroke(1.dp, if (subscribed) HuluwaColors.Pine.copy(alpha = .22f) else HuluwaColors.Line),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = if (subscribed) HuluwaColors.SoftGreen else HuluwaColors.Paper,
            contentColor = HuluwaColors.Pine,
            disabledContainerColor = if (subscribed) HuluwaColors.SoftGreen else HuluwaColors.Paper,
            disabledContentColor = HuluwaColors.Muted,
        )) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 1.5.dp)
        else Icon(painterResource(if (subscribed) R.drawable.ic_film_follow_remove else R.drawable.ic_film_follow_add),
            null, Modifier.size(18.dp))
        Spacer(Modifier.width(4.dp))
        Text(if (subscribed) "取消追更" else "追更", style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
