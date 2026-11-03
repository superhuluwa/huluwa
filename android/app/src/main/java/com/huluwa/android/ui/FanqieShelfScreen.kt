package com.huluwa.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.huluwa.android.FanqieShelfState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.FanqieBookShelf

/** 独立书架管理页：每行三本，服务端下载进度、一键更新和批量管理。 */
@Composable fun FanqieShelfScreen(state: FanqieShelfState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val grid = rememberLazyGridState()
    val books = state.books.data
    val updates = state.updates
    val bookUpdates = updates?.books.orEmpty().associateBy { it.bookId }
    fun back() {
        if (state.selecting && !state.deleting) vm.toggleShelfSelection() else vm.closeFanqieShelf()
    }
    BackHandler(onBack = ::back)
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    IconButton(::back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    Column(Modifier.weight(1f)) {
                        Text("书架管理", style = MaterialTheme.typography.titleMedium)
                        Text(if (state.selecting) "已选择 ${state.selectedIds.size} 本" else "共 ${books.size} 本小说",
                            style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                    IconButton(vm::refreshFanqieShelf, enabled = !state.books.loading && !state.deleting) {
                        Icon(Icons.Outlined.Refresh, "刷新书架")
                    }
                    TextButton(vm::toggleShelfSelection,
                        enabled = books.isNotEmpty() && !state.deleting && (!state.updating || state.selecting)) {
                        Text(if (state.selecting) "取消" else "批量管理")
                    }
                }
            }
        },
        bottomBar = {
            if (state.selecting || state.deleting) Surface(color = Color.White, shadowElevation = 8.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(vm::selectAllShelfBooks, enabled = !state.deleting && !state.updating && books.isNotEmpty()) {
                        Text(if (books.isNotEmpty() && state.selectedIds.size == books.size) "取消全选" else "全选")
                    }
                    Spacer(Modifier.weight(1f))
                    Button({ vm.deleteShelfBooks(state.selectedIds) }, enabled = state.selectedIds.isNotEmpty() && !state.deleting && !state.updating,
                        colors = ButtonDefaults.buttonColors(containerColor = HuluwaColors.Danger)) {
                        if (state.deleting) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp,
                            color = LocalContentColor.current)
                        else Icon(Icons.Outlined.DeleteOutline, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.deleting) "正在删除…" else "删除（${state.selectedIds.size}）")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(vm::checkFanqieShelfUpdates, Modifier.fillMaxWidth(),
                    enabled = books.isNotEmpty() && !state.books.loading && !state.selecting && !state.deleting && !state.updating) {
                    if (state.updating) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Outlined.Refresh, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.updating) "正在检查并下载…" else "一键检查更新")
                }
                val summary = if (state.updateStarting) "正在启动更新…" else updates?.summary.orEmpty()
                if (summary.isNotBlank()) Text(summary, Modifier.fillMaxWidth(),
                    color = if (updates?.error != null) HuluwaColors.Danger else HuluwaColors.Muted,
                    style = MaterialTheme.typography.bodySmall)
                state.updateError?.let { error ->
                    Text(error, Modifier.fillMaxWidth(), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
                }
            }
            if (state.books.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.books.error?.let { error ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, Modifier.weight(1f), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
                    TextButton(vm::refreshFanqieShelf) { Text("重试") }
                }
            }
            state.deleteError?.let { error ->
                Text(error, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
            }
            if (books.isEmpty() && !state.books.loading && state.books.error == null && !state.deleting) {
                Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                    Text("书架里还没有小说\n返回番茄小说会话，搜索并打开书目即可加入书架",
                        color = HuluwaColors.Muted, style = MaterialTheme.typography.bodyMedium)
                }
            } else LazyVerticalGrid(columns = GridCells.Fixed(3), state = grid,
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp,
                    bottom = if (state.selecting) 16.dp else 16.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()),
                horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                items(books, key = { it.bookId }) { book ->
                    ShelfBook(book, state.selecting, book.bookId in state.selectedIds, state.deleting,
                        updating = state.updating, updateError = bookUpdates[book.bookId]?.error,
                        onOpen = { vm.openReader(book.bookId, book.title, book.author, book.cover) },
                        onSelect = { vm.selectShelfBook(book.bookId) },
                        onDelete = { vm.deleteShelfBooks(setOf(book.bookId)) })
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable private fun ShelfBook(book: FanqieBookShelf, selecting: Boolean, selected: Boolean, busy: Boolean,
    updating: Boolean, updateError: String?, onOpen: () -> Unit, onSelect: () -> Unit, onDelete: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
        .combinedClickable(enabled = !busy, onClick = { if (selecting) onSelect() else onOpen() },
            onLongClickLabel = "选择小说", onLongClick = if (updating) null else onSelect), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(shape = RoundedCornerShape(10.dp),
            border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) HuluwaColors.Pine else HuluwaColors.Line)) {
            Box(Modifier.fillMaxWidth().aspectRatio(.7f).background(HuluwaColors.SoftGreen)) {
                Text(book.title, Modifier.align(Alignment.Center).padding(12.dp),
                    style = MaterialTheme.typography.titleSmall, color = HuluwaColors.Pine,
                    maxLines = 4, overflow = TextOverflow.Ellipsis)
                if (book.cover.isNotBlank()) AsyncImage(book.cover, book.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                if (selecting) Surface(Modifier.align(Alignment.TopEnd).padding(5.dp), shape = RoundedCornerShape(20.dp), color = Color.White) {
                    Icon(if (selected) Icons.Outlined.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                        if (selected) "已选中" else "未选中", Modifier.padding(3.dp).size(22.dp), tint = HuluwaColors.Pine)
                }
            }
        }
        Text(book.title, Modifier.fillMaxWidth(), style = MaterialTheme.typography.titleSmall,
            minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(book.author.ifBlank { "未知作者" }, Modifier.fillMaxWidth(), color = HuluwaColors.Muted,
            style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("服务端已下载 ${book.downloadedChapterCount} / ${book.chapterCount} 章", Modifier.fillMaxWidth(),
            color = HuluwaColors.Pine, style = MaterialTheme.typography.labelSmall, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        if (updateError != null) Text(updateError, Modifier.fillMaxWidth(), color = HuluwaColors.Danger,
            style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (book.progressChapter > 0) "第 ${book.progressChapter} 章" else "尚未阅读",
                Modifier.weight(1f), color = HuluwaColors.Pine, style = MaterialTheme.typography.labelSmall,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!selecting) IconButton(onDelete, enabled = !busy && !updating, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.DeleteOutline, "删除${book.title}", Modifier.size(18.dp), tint = HuluwaColors.Danger)
            }
        }
    }
}
