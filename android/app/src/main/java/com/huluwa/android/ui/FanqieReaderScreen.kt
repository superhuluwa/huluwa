package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.FanqieReaderState
import com.huluwa.android.HuluwaViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

/** 原生滚动阅读：按需取章、目录跳转、恢复章节和滚动位置。 */
@Composable fun FanqieReaderScreen(state: FanqieReaderState, vm: HuluwaViewModel) {
    val chapter = state.chapter
    val listState = rememberLazyListState()
    LaunchedEffect(state.bookId, chapter, state.loading, state.error) {
        if (chapter == null || state.loading || state.error != null) return@LaunchedEffect
        listState.scrollToItem(chapter.paragraphIndex.coerceIn(0, chapter.paragraphs.size + 1),
            chapter.scrollOffset.coerceAtLeast(0))
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged().collect { (index, offset) ->
                vm.rememberReaderPosition(state.bookId, chapter.order, index, offset)
            }
    }
    BackHandler { if (state.tocVisible) vm.toggleReaderToc() else vm.closeReader() }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Column {
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
                    IconButton(vm::refreshReaderBook, enabled = !state.loading) {
                        Icon(Icons.Outlined.Refresh, "刷新章节")
                    }
                    IconButton(vm::toggleReaderToc, enabled = chapter != null && !state.loading) {
                        Icon(Icons.AutoMirrored.Outlined.List, "目录")
                    }
                }
                if (state.progressError != null) Text(state.progressError,
                    Modifier.fillMaxWidth().clickable { vm.flushReaderProgress() }
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    color = HuluwaColors.Danger, style = MaterialTheme.typography.labelSmall)
            }
        },
        bottomBar = {
            if (chapter != null) ReaderBottomBar(chapter.hasPrev && !state.loading, chapter.hasNext && !state.loading,
                !state.loading, { vm.readChapter(chapter.order - 1) }, vm::toggleReaderToc,
                { vm.readChapter(chapter.order + 1) })
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
                    Button(vm::retryReader) {
                        Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("重试")
                    }
                }
                chapter != null -> LazyColumn(state = listState,
                    modifier = Modifier.fillMaxSize().widthIn(max = 720.dp).align(Alignment.TopCenter),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(chapter.chapterTitle, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                            HorizontalDivider(color = HuluwaColors.Line)
                        }
                    }
                    items(chapter.paragraphs) { paragraph ->
                        Text("　　$paragraph", style = MaterialTheme.typography.bodyLarge, fontSize = 18.sp,
                            lineHeight = 32.sp, color = HuluwaColors.Ink)
                    }
                    item {
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
                    }
                }
            }
            if (state.tocVisible && chapter != null && !state.loading) ReaderToc(state,
                onDismiss = vm::toggleReaderToc, onSelect = vm::readChapter, onRetry = vm::retryReaderToc)
        }
    }
}

@Composable private fun ReaderBottomBar(
    hasPrev: Boolean, hasNext: Boolean, enabled: Boolean,
    onPrev: () -> Unit, onToc: () -> Unit, onNext: () -> Unit,
) {
    Surface(color = Color.White, shadowElevation = 8.dp) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(56.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceEvenly) {
            TextButton(onPrev, enabled = hasPrev) {
                Icon(Icons.Outlined.SkipPrevious, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp)); Text("上一章")
            }
            TextButton(onToc, enabled = enabled) {
                Icon(Icons.AutoMirrored.Outlined.List, null, Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp)); Text("目录")
            }
            TextButton(onNext, enabled = hasNext) {
                Text("下一章"); Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.SkipNext, null, Modifier.size(18.dp))
            }
        }
    }
}

@Composable private fun ReaderToc(state: FanqieReaderState, onDismiss: () -> Unit,
    onSelect: (Int) -> Unit, onRetry: () -> Unit) {
    val chapter = state.chapter ?: return
    val toc = state.toc
    val listState = rememberLazyListState()
    LaunchedEffect(toc, chapter.order) {
        val index = toc?.chapters?.indexOfFirst { it.order == chapter.order } ?: -1
        if (index >= 0) listState.scrollToItem((index - 2).coerceAtLeast(0))
    }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .3f)).clickable(onClick = onDismiss)) {
        Surface(Modifier.fillMaxWidth(.86f).fillMaxHeight().align(Alignment.CenterEnd)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
            color = Color.White, shape = RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)) {
            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("目录", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Text("共 ${chapter.chapterCount} 章", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
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
