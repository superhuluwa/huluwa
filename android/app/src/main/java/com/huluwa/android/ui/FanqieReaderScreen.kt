package com.huluwa.android.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.ViewTreeObserver
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.TextFormat
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.huluwa.android.FanqieReaderState
import com.huluwa.android.HuluwaViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/** 原生阅读：目录从左侧滑出，字号跨书籍保存，全屏时轻点唤出工具，章末继续上滑自动翻章。 */
@Composable fun FanqieReaderScreen(state: FanqieReaderState, vm: HuluwaViewModel) {
    val chapter = state.chapter
    val listState = rememberLazyListState()
    val currentReaderState by rememberUpdatedState(state)
    val nextChapterDragThreshold = with(LocalDensity.current) { 48.dp.toPx() }
    val chapterScrollConnection = remember(listState, vm, nextChapterDragThreshold) {
        ReaderChapterScrollConnection(listState, nextChapterDragThreshold) {
            val reader = currentReaderState
            val currentChapter = reader.chapter
            if (reader.fullScreen && !reader.loading && reader.error == null &&
                !reader.tocVisible && !reader.fontSettingsVisible && currentChapter?.hasNext == true) {
                vm.rememberReaderPosition(reader.bookId, currentChapter.order,
                    listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
                vm.readChapter(currentChapter.order + 1)
            }
        }
    }
    LaunchedEffect(chapterScrollConnection) {
        listState.interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is DragInteraction.Start -> chapterScrollConnection.startDrag()
                is DragInteraction.Stop, is DragInteraction.Cancel -> chapterScrollConnection.stopDrag()
            }
        }
    }
    val showControls = !state.fullScreen || state.controlsVisible
    ReaderSystemBars(state.fullScreen)
    LaunchedEffect(state.bookId, chapter, state.loading, state.error) {
        if (chapter == null || state.loading || state.error != null) return@LaunchedEffect
        listState.scrollToItem(chapter.paragraphIndex.coerceIn(0, chapter.paragraphs.size + 1),
            chapter.scrollOffset.coerceAtLeast(0))
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) ->
                vm.rememberReaderPosition(state.bookId, chapter.order, index, offset)
            }
    }
    BackHandler {
        when {
            state.tocVisible -> vm.closeReaderToc()
            state.fontSettingsVisible -> vm.toggleReaderFontSettings()
            state.fullScreen -> vm.toggleReaderFullscreen()
            else -> vm.closeReader()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .then(if (state.fullScreen) Modifier.windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Top)) else Modifier),
            containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                if (showControls) Column {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        IconButton(vm::closeReader) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                        Column(Modifier.weight(1f)) {
                            Text(chapter?.title.orEmpty().ifBlank { state.title },
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(when {
                                state.loading -> if (state.prepared) "正在加载章节…" else "正在打开小说…"
                                chapter != null -> (if (chapter.author.isNotBlank()) chapter.author + " · " else "") +
                                        "第 ${chapter.order}/${chapter.chapterCount} 章"
                                else -> "番茄小说"
                            }, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                        }
                        IconButton(vm::refreshReaderBook, enabled = !state.loading) { Icon(Icons.Outlined.Refresh, "刷新章节") }
                        IconButton(vm::toggleReaderToc, enabled = chapter != null && !state.loading) {
                            Icon(Icons.AutoMirrored.Outlined.List, "目录")
                        }
                    }
                    state.progressError?.let { error ->
                        Text(error, Modifier.fillMaxWidth().clickable { vm.flushReaderProgress() }
                            .padding(horizontal = 20.dp, vertical = 6.dp),
                            color = HuluwaColors.Danger, style = MaterialTheme.typography.labelSmall)
                    }
                }
            },
            bottomBar = {
                if (showControls && chapter != null) Surface(color = Color.White, shadowElevation = 8.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        AnimatedVisibility(state.fontSettingsVisible) { ReaderFontSettings(state.fontSizeSp, vm) }
                        Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically) {
                            ReaderTool("上一章", Icons.Outlined.SkipPrevious, chapter.hasPrev && !state.loading) { vm.readChapter(chapter.order - 1) }
                            ReaderTool("目录", Icons.AutoMirrored.Outlined.List, !state.loading, vm::toggleReaderToc)
                            ReaderTool("字号", Icons.Outlined.TextFormat, true, vm::toggleReaderFontSettings)
                            ReaderTool(if (state.fullScreen) "退出全屏" else "全屏",
                                if (state.fullScreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                true, vm::toggleReaderFullscreen)
                            ReaderTool("下一章", Icons.Outlined.SkipNext, chapter.hasNext && !state.loading) { vm.readChapter(chapter.order + 1) }
                        }
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when {
                    state.loading -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        CircularProgressIndicator()
                        Text(if (state.prepared) "正在加载章节…" else "正在打开小说…",
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.bodyMedium)
                    }
                    state.error != null -> Column(Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.error, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        Button(vm::retryReader) { Text("重试") }
                    }
                    chapter != null -> LazyColumn(state = listState,
                        modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).align(Alignment.TopCenter)
                            .nestedScroll(chapterScrollConnection)
                            .pointerInput(state.fullScreen) {
                                if (state.fullScreen) detectTapGestures { vm.toggleReaderControls() }
                            }.semantics {
                                if (state.fullScreen) onClick("显示或隐藏阅读工具") { vm.toggleReaderControls(); true }
                            },
                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(chapter.chapterTitle, fontSize = (state.fontSizeSp + 4).sp,
                                    lineHeight = (state.fontSizeSp * 1.6f + 4).sp, fontWeight = FontWeight.SemiBold)
                                HorizontalDivider(color = HuluwaColors.Line)
                            }
                        }
                        items(chapter.paragraphs) { paragraph ->
                            Text("　　$paragraph", fontSize = state.fontSizeSp.sp,
                                lineHeight = (state.fontSizeSp * 1.8f).sp, color = HuluwaColors.Ink)
                        }
                        item {
                            if (showControls) {
                                Spacer(Modifier.height(20.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    if (chapter.hasPrev) OutlinedButton({ vm.readChapter(chapter.order - 1) }, Modifier.weight(1f)) {
                                        Text("上一章", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                    if (chapter.hasNext) Button({ vm.readChapter(chapter.order + 1) }, Modifier.weight(1f)) {
                                        Text("下一章", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    } else TextButton(vm::refreshReaderBook, Modifier.weight(1f)) {
                                        Text("已读到最新，刷新章节", maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            } else {
                                Text(if (chapter.hasNext) "本章结束，继续上滑阅读下一章" else "已读到最新",
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                                    color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }
        AnimatedVisibility(visible = state.tocVisible && chapter != null && !state.loading,
            enter = fadeIn(tween(220)), exit = fadeOut(tween(220))) {
            ReaderToc(state, onDismiss = vm::closeReaderToc, onSelect = vm::readChapter, onRetry = vm::retryReaderToc)
        }
    }
}

/** 从章末开始的新手势才触发翻章，让最后一屏正文留给用户读完。 */
private class ReaderChapterScrollConnection(
    private val listState: LazyListState,
    private val dragThreshold: Float,
    private val onNextChapter: () -> Unit,
) : NestedScrollConnection {
    private var startedAtEnd = false
    private var upwardDrag = 0f

    fun startDrag() {
        startedAtEnd = !listState.canScrollForward
        upwardDrag = 0f
    }

    fun stopDrag() {
        startedAtEnd = false
        upwardDrag = 0f
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (!startedAtEnd || source != NestedScrollSource.UserInput) return Offset.Zero
        if (available.y >= 0f || listState.canScrollForward) {
            upwardDrag = 0f
            return Offset.Zero
        }
        upwardDrag -= available.y
        if (upwardDrag >= dragThreshold) {
            stopDrag()
            onNextChapter()
        }
        return Offset.Zero
    }
}

@Composable private fun ReaderTool(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean, onClick: () -> Unit) {
    TextButton(onClick, enabled = enabled, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp)) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Icon(icon, null, Modifier.size(21.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable private fun ReaderFontSettings(size: Int, vm: HuluwaViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("字号", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Text("$size 号", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Pine)
            IconButton(vm::toggleReaderFontSettings, Modifier.size(32.dp)) { Icon(Icons.Outlined.Close, "关闭字号设置", Modifier.size(18.dp)) }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton({ vm.setReaderFontSize(size - 1) }, enabled = size > 14) { Text("A−") }
            Slider(value = size.toFloat(), onValueChange = { vm.setReaderFontSize(it.toInt()) },
                modifier = Modifier.weight(1f), valueRange = 14f..32f, steps = 17)
            TextButton({ vm.setReaderFontSize(size + 1) }, enabled = size < 32) { Text("A+") }
        }
    }
}

@Composable private fun AnimatedVisibilityScope.ReaderToc(state: FanqieReaderState, onDismiss: () -> Unit,
    onSelect: (Int) -> Unit, onRetry: () -> Unit) {
    val chapter = state.chapter ?: return
    val toc = state.toc
    val listState = rememberLazyListState()
    LaunchedEffect(toc, chapter.order) {
        val index = toc?.chapters?.indexOfFirst { it.order == chapter.order } ?: -1
        if (index >= 0) listState.scrollToItem((index - 2).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)).clickable(onClick = onDismiss)) {
        Surface(Modifier.fillMaxWidth(.86f).fillMaxHeight().align(Alignment.CenterStart)
            .animateEnterExit(enter = slideInHorizontally(tween(220)) { -it }, exit = slideOutHorizontally(tween(220)) { -it })
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            color = Color.White, shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("目录", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("共 ${chapter.chapterCount} 章", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭目录") }
                }
                HorizontalDivider(color = HuluwaColors.Line)
                when {
                    state.tocError != null -> Column(Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(state.tocError, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(10.dp)); Button(onRetry) { Text("重试") }
                    }
                    toc == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
                    else -> LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                        items(toc.chapters, key = { it.order }) { item ->
                            val current = item.order == chapter.order
                            Row(Modifier.fillMaxWidth().background(if (current) HuluwaColors.SoftGreen else Color.Transparent)
                                .clickable { onSelect(item.order) }.padding(horizontal = 18.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (current) HuluwaColors.Pine else HuluwaColors.Ink)
                                if (current) Text("读到这里", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Pine)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 隐藏系统栏，并在退出阅读或取消全屏时恢复，避免影响书架与会话页。 */
@Composable private fun ReaderSystemBars(fullScreen: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = remember(context) { context.readerActivity()?.window }
    DisposableEffect(window, view, fullScreen) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previousBehavior = controller?.systemBarsBehavior
        fun applyBars() {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (fullScreen) controller?.hide(WindowInsetsCompat.Type.systemBars())
            else controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        applyBars()
        val observer = view.viewTreeObserver
        val focus = ViewTreeObserver.OnWindowFocusChangeListener { focused -> if (focused) applyBars() }
        observer.addOnWindowFocusChangeListener(focus)
        onDispose {
            if (observer.isAlive) observer.removeOnWindowFocusChangeListener(focus)
            controller?.show(WindowInsetsCompat.Type.systemBars())
            if (previousBehavior != null) controller?.systemBarsBehavior = previousBehavior
        }
    }
}

private fun Context.readerActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return current as? Activity
}
