package com.huluwa.android.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.huluwa.android.*
import com.huluwa.android.data.*
import kotlinx.coroutines.flow.first

@Composable fun ChatScreen(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val chat = ui.chat; val session = chat.session ?: return
    val displayMessages = remember(chat.messages) { mergeVideoImageMessages(chat.messages) }
    val context = LocalContext.current
    var pendingSaveImage by rememberSaveable { mutableStateOf<String?>(null) }
    val storagePermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val url = pendingSaveImage
        pendingSaveImage = null
        if (granted && url != null) vm.saveImageToGallery(url)
        else if (url != null) vm.notify("保存原图需要存储权限，请授权后重试")
    }
    fun saveOriginalImage(url: String) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            if (pendingSaveImage != null) return
            pendingSaveImage = url
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else vm.saveImageToGallery(url)
    }
    val reconnecting = rememberReconnecting(vm)
    val list = rememberChatListState(session.id, displayMessages, chat.processing)
    var content by rememberSaveable(session.id) { mutableStateOf("") }
    // 系统分享（番茄 App「分享 → 葫芦娃」）预填到输入框，等待用户确认后再发送。
    val shared = vm.pendingShare.collectAsStateWithLifecycle().value
    LaunchedEffect(session.id, shared) {
        if (shared != null && session.kind == "fanqie") {
            content = shared
            vm.consumeShare()
        }
    }
    var menu by remember { mutableStateOf(false) }
    var media by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var activeVideoId by remember(session.id) { mutableStateOf<Long?>(null) }
    var initialized by rememberSaveable(session.id) { mutableStateOf(false) }
    val latestMessage = chat.messages.lastOrNull()
    // 返回阅读页或书架前的会话时，以当前快照开始观察，避免把页面重建当成新消息。
    var observedMessageId by remember(session.id) { mutableStateOf(latestMessage?.id) }
    var observedProcessing by remember(session.id) { mutableStateOf(chat.processing) }
    val nearBottom by remember(list) { derivedStateOf { list.firstVisibleItemIndex <= 2 } }
    LaunchedEffect(session.id, latestMessage?.id, chat.processing) {
        val changed = latestMessage?.id != observedMessageId || chat.processing != observedProcessing
        observedMessageId = latestMessage?.id
        observedProcessing = chat.processing
        if (latestMessage != null && (!initialized || (changed && (nearBottom || latestMessage.mine)))) {
            list.scrollToItem(0)
            initialized = true
        }
    }
    // 列表仅保留当前点击的视频；完全滑出可见区域后恢复封面，避免滚回时自动播放。
    LaunchedEffect(session.id, activeVideoId) {
        val messageId = activeVideoId ?: return@LaunchedEffect
        snapshotFlow { list.layoutInfo.visibleItemsInfo.any { it.key == messageId } }.first { visible -> !visible }
        activeVideoId = null
    }
    // 整个聊天布局统一避让键盘，消息区与输入栏随系统键盘动画一起调整高度。
    Scaffold(modifier = Modifier.fillMaxSize().skinBackground().imePadding(), containerColor = Color.Transparent, contentWindowInsets = WindowInsets(0, 0, 0, 0), snackbarHost = { SnackbarHost(snackbar) },
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
                    if (session.kind == "fanqie") IconButton(vm::showFanqieShelf) {
                        Icon(Icons.Outlined.MenuBook, "书架管理")
                    }
                    IconButton({ vm.refreshChat() }) { Icon(Icons.Outlined.Refresh, "刷新会话") }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Outlined.MoreHoriz, "会话操作") }
                        DropdownMenu(menu, { menu = false }) {
                            if (session.kind == "fanqie") DropdownMenuItem(text = { Text("书架管理") },
                                onClick = { menu = false; vm.showFanqieShelf() })
                            if (session.kind != "system" && session.kind != "fanqie") DropdownMenuItem(text = { Text("服务设置") }, onClick = { menu = false; vm.showSettings(session.kind) })
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
                        placeholder = { Text(when (session.kind) { "magnet", "xunlei", "xunlei-win" -> "粘贴下载链接…"; "video" -> "粘贴分享口令…"; "movie-hls" -> "输入片名或新版详情链接…"; "dpd" -> "输入片名，按线路搜索…"; "btbtla" -> "输入片名，搜索高清磁力…"; "fanqie" -> "输入书名、作者或粘贴分享链接…"; else -> "输入片名，试试搜点什么…" }, style = MaterialTheme.typography.bodySmall) },
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
                    onSaveImage = ::saveOriginalImage,
                    onMedia = { url, video -> activeVideoId = null; media = url to video })
            }
            item("history") {
                // 加载状态统一显示在标题栏菊花上，消息区只保留错误反馈，避免列表被进度条挤动。
                Feedback(error = chat.error.takeUnless { it.isServerUnreachable() }, onRetry = { if (chat.messages.isEmpty()) vm.openSession(session) else vm.refreshChat() })
                if (chat.hasEarlier) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(vm::loadEarlier, enabled = !chat.loadingEarlier) { Text(if (chat.loadingEarlier) "正在加载…" else "查看更早消息") }
                }
                if (!chat.loading && chat.messages.isEmpty() && chat.error == null) {
                    if (session.kind == "fanqie") EmptyContent("搜索想看的小说", "发送书名、作者或番茄分享链接即可；读过的书可从右上角书架管理继续", sessionIcon(session.kind))
                    else EmptyContent("发条消息，开始吧", "搜索影视、提交下载，都可以从这里开始", sessionIcon(session.kind))
                }
            }
        }
    }
    media?.let { (url, video) -> MediaViewer(url, video) { media = null } }
}

/** 保存消息锚点与卡片内偏移，离开期间有新消息插入时也恢复到原来的内容。 */
@Composable private fun rememberChatListState(sessionId: String, messages: List<ChatMessage>, processing: Boolean): LazyListState {
    val currentMessages by rememberUpdatedState(messages)
    val currentProcessing by rememberUpdatedState(processing)
    val saver = remember(sessionId) {
        listSaver<LazyListState, Any>(
            save = { list ->
                val anchor = list.layoutInfo.visibleItemsInfo.firstOrNull { it.index == list.firstVisibleItemIndex }?.key
                listOf(anchor ?: "", list.firstVisibleItemIndex, list.firstVisibleItemScrollOffset)
            },
            restore = { saved ->
                val prefix = if (currentProcessing) 1 else 0
                val index = when (val anchor = saved[0]) {
                    is Long -> {
                        val messageIndex = currentMessages.indexOfLast { it.id == anchor }
                        if (messageIndex >= 0) currentMessages.lastIndex - messageIndex + prefix else null
                    }
                    "processing" -> 0
                    "history" -> currentMessages.size + prefix
                    else -> null
                }
                LazyListState(firstVisibleItemIndex = index ?: (saved[1] as Int),
                    firstVisibleItemScrollOffset = saved[2] as Int)
            },
        )
    }
    return rememberSaveable(sessionId, saver = saver) { LazyListState() }
}

@Composable private fun MessageBubble(
    message: ChatMessage, kind: String, ui: AppState, vm: HuluwaViewModel,
    playingVideo: Boolean, onVideoPlay: () -> Unit, onVideoStop: () -> Unit,
    onSaveImage: (String) -> Unit, onMedia: (String, Boolean) -> Unit,
) {
    val notify = message.notify
    val film = message.film
    // 懂片帝作品卡走独立渲染，不复用电影港 / 电影天堂的 FilmResultCard
    val dpd = message.dpd
    val btbtla = message.btbtla
    val fanqie = message.fanqie
    val fanqieReader = message.fanqieReader
    val card = film != null || dpd != null || btbtla != null || fanqie != null || fanqieReader != null
    val galleryImages = remember(message.payload) { message.videoImages }
    if (notify != null) { NotifyMessageCard(notify, kind, timeLabel(message.createdAt)); return }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.mine) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Top) {
        if (!message.mine) { SessionIcon(kind, Modifier.size(32.dp)); Spacer(Modifier.width(9.dp)) }
        Column(Modifier.widthIn(max = 300.dp), horizontalAlignment = if (message.mine) Alignment.End else Alignment.Start) {
            Surface(color = if (message.mine) HuluwaColors.ChatBubble else Color.White,
                shape = if (card) RoundedCornerShape(18.dp) else RoundedCornerShape(
                    topStart = if (message.mine) 17.dp else 4.dp, topEnd = if (message.mine) 4.dp else 17.dp,
                    bottomStart = 17.dp, bottomEnd = 17.dp),
                border = BorderStroke(1.dp, HuluwaColors.Line)) {
                Column(Modifier.padding(if (card) 12.dp else 13.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when {
                        btbtla != null -> BtbtlaResultCard(btbtla, vm)
                        fanqie != null -> FanqieResultCard(fanqie) { vm.requestFanqieRead(fanqie) }
                        fanqieReader != null -> FanqieReaderResultCard(fanqieReader) {
                            vm.openReader(fanqieReader.bookId, fanqieReader.title, fanqieReader.author, fanqieReader.cover)
                        }
                        dpd != null -> DpdResultCard(dpd, ui, vm)
                        film != null -> FilmResultCard(film, ui, vm)
                        message.type == "image" && galleryImages.size > 1 -> {
                            VideoImageGallery(galleryImages, playingVideo, onVideoPlay, onVideoStop, ui.savingImageUrls, onSaveImage) { onMedia(it, false) }
                            val caption = plainText(message.contentText)
                            if (caption.isNotBlank()) SelectionContainer { Text(caption, color = HuluwaColors.Ink, style = MaterialTheme.typography.bodyMedium) }
                        }
                        message.type == "image" && message.content.isNotBlank() -> {
                            AsyncImage(message.content, "消息图片",
                                Modifier.width(240.dp).heightIn(min = 150.dp, max = 300.dp).clickable { onMedia(message.content, false) }, contentScale = ContentScale.Fit)
                            if (kind == "video" && !message.mine) SaveOriginalImageButton(message.content in ui.savingImageUrls, { onSaveImage(message.content) })
                        }
                        message.type == "video" && message.content.isNotBlank() -> {
                            InlineVideoBubble(message.content, message.payload.text("poster", "cover"), playingVideo, onVideoPlay, onVideoStop)
                            // 单张实况照片以视频消息下发，保存对应的静态原图。
                            val originalImage = message.payload.text("poster", "cover")
                            if (kind == "video" && !message.mine && message.payload.text("video").isBlank() &&
                                (message.payload.number("imageCount") ?: 0.0) > 0 && originalImage.isNotBlank()) {
                                SaveOriginalImageButton(originalImage in ui.savingImageUrls, { onSaveImage(originalImage) })
                            }
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
                            if (galleryImages.isNotEmpty()) VideoImageGallery(galleryImages, playingVideo, onVideoPlay, onVideoStop, ui.savingImageUrls, onSaveImage) { onMedia(it, false) }
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
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Poster(card.title, card.cover, size = DpSize(64.dp, 90.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(card.title, Modifier.weight(1f), color = HuluwaColors.Ink,
                        style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (!card.isMovieHls) {
                        FilmFollowButton(subscribed, followBusy, enabled = ui.busy == null) {
                            vm.setFilmFollowing(card, !subscribed)
                        }
                    }
                }
                val metadata = listOf(card.year, card.genre).filter(String::isNotBlank).joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(metadata, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (card.origin.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(card.origin, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                FilmSourceTag(card)
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilmActionButton("资源列表", Icons.Outlined.FormatListBulleted) {
                vm.filmResources(card)
            }
            FilmActionButton(if (websiteBusy) "正在打开…" else "网站直达", Icons.Outlined.OpenInNew,
                busy = websiteBusy, enabled = ui.busy == null) {
                vm.openFilmWebsite(card)
            }
        }
    }
}

@Composable private fun FilmSourceTag(card: FilmCard) {
    val sourceTitle = when {
        card.isMovieHls -> "电影港 在线"
        card.isHls -> "电影天堂"
        else -> "电影港 磁力"
    }
    val sourceIcon = when {
        card.isMovieHls -> Icons.Outlined.PlayCircleOutline
        card.isHls -> Icons.Outlined.Movie
        else -> Icons.Outlined.Link
    }
    Surface(color = HuluwaColors.SoftGreen, shape = RoundedCornerShape(5.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(sourceIcon, null, Modifier.size(12.dp), tint = HuluwaColors.Pine)
            Text(sourceTitle, color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** 小号图文操作按内容宽度排列，避免大面积实色按钮占满卡片底部。 */
@Composable private fun FilmActionButton(
    text: String, icon: ImageVector, busy: Boolean = false,
    enabled: Boolean = true, onClick: () -> Unit,
) {
    val contentColor = if (enabled) HuluwaColors.Pine else HuluwaColors.Muted
    val shape = RoundedCornerShape(8.dp)
    Row(Modifier.heightIn(min = 28.dp).clip(shape)
        .clickable(enabled = enabled && !busy, role = Role.Button, onClickLabel = text, onClick = onClick)
        .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(14.dp), color = contentColor, strokeWidth = 1.5.dp)
        else Icon(icon, null, Modifier.size(14.dp), tint = contentColor)
        Text(text, color = contentColor, style = MaterialTheme.typography.labelMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** 紧凑订阅胶囊不引入 Material 按钮的最小布局高度，避免把标题与简介撑开。 */
@Composable private fun FilmFollowButton(subscribed: Boolean, busy: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val contentColor = if (enabled) HuluwaColors.Pine else HuluwaColors.Muted
    Row(Modifier.widthIn(min = 56.dp).heightIn(min = 24.dp)
        .clip(CircleShape)
        .background(if (subscribed) HuluwaColors.SoftGreen else Color.White)
        .border(1.dp, if (subscribed) HuluwaColors.Pine else HuluwaColors.Line, CircleShape)
        .clickable(enabled = enabled && !busy, role = Role.Button,
            onClickLabel = if (subscribed) "取消订阅" else "订阅", onClick = onClick)
        .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        if (busy) CircularProgressIndicator(Modifier.size(12.dp), color = contentColor, strokeWidth = 1.5.dp)
        else Icon(if (subscribed) Icons.Outlined.NotificationsActive else Icons.Outlined.AddAlert,
            null, Modifier.size(12.dp), tint = contentColor)
        Spacer(Modifier.width(3.dp))
        Text(if (subscribed) "取消订阅" else "订阅", color = contentColor,
            style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}
