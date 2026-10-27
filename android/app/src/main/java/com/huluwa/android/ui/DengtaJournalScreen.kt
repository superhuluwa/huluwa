package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.huluwa.android.DengtaState
import com.huluwa.android.DengtaViewModel
import com.huluwa.android.data.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable internal fun DengtaJournalPage(ui: DengtaState, vm: DengtaViewModel, onRecord: () -> Unit, onCalendar: (String) -> Unit) {
    val media = ui.selected
    val detail = ui.detail.data?.takeIf { it.media.id == media?.id }
    var tab by rememberSaveable(media?.id) { mutableIntStateOf(0) }
    val entries = ui.journal.media.filter { it.following || ui.journal.logs.any { log -> log.mediaId == it.media.id } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 18.dp, 18.dp, 28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            if (ui.journalLoading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = HuluwaColors.Pine)
            ui.journalError?.let { StatusNotice(it, onRetry = vm::loadJournal) }
        }
        if (media == null) {
            item {
                Text("我的追剧", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text("${ui.journal.media.count { it.following }}部正在追 · ${ui.journal.logs.size}条观看记录", Modifier.padding(top = 5.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
            }
            if (entries.isEmpty() && !ui.journalLoading && ui.journalError == null) item {
                Panel(Modifier.fillMaxWidth()) {
                    DengtaEmpty("还没有追剧记录", "从排行榜进入作品详情，加入追剧后就能在这里找到。")
                    TextButton({ vm.back() }, Modifier.align(Alignment.CenterHorizontally)) { Text("去排行榜发现好剧") }
                }
            }
            items(entries, key = { it.media.id }) { entry ->
                Surface(onClick = { vm.openJournal(entry.media) }, color = Color.White, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        DengtaPoster(entry.media.poster, entry.media.name, Modifier.size(49.dp, 73.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(entry.media.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("已看${entry.watched}集${entry.media.episodeCount?.let { " / 共${it}集" }.orEmpty()}", color = HuluwaColors.Pine, style = MaterialTheme.typography.bodySmall)
                            Text(if (entry.following) "追剧中 · ${entry.media.platforms}" else "已取消追剧 · 观看记录已保留", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                        Icon(Icons.Outlined.ChevronRight, "查看追剧记录", Modifier.size(18.dp), tint = HuluwaColors.Muted)
                    }
                }
            }
            item { Text("观看记录保存在本机，按当前服务器和账号分别保存。", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium) }
        } else {
            item {
                val watched = ui.selectedFollow?.watched ?: 0
                val pending = media.updatedEpisodes?.let { (it - watched).coerceAtLeast(0) }
                Panel(Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(13.dp), verticalAlignment = Alignment.CenterVertically) {
                        DengtaPoster(media.poster, media.name, Modifier.size(52.dp, 76.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(media.name, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text(media.updateText.ifBlank { "更新进度暂未提供" }, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                            if (pending != null) Text(if (pending > 0) "还有${pending}集待看" else "已追平当前更新", color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (media.episodeCount != null && media.episodeCount > 0) LinearProgressIndicator(
                        progress = { (watched.toFloat() / media.episodeCount).coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 17.dp).height(6.dp), color = HuluwaColors.Pine, trackColor = HuluwaColors.SoftGreen)
                    Text("已看${watched}集${media.episodeCount?.let { " / 共${it}集" }.orEmpty()}", Modifier.padding(top = 9.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                    Button(onRecord, enabled = !ui.journalBusy && !ui.journalLoading, modifier = Modifier.fillMaxWidth().padding(top = 12.dp), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Outlined.EditNote, null, Modifier.size(19.dp)); Spacer(Modifier.width(7.dp)); Text("记录观看进度")
                    }
                }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(15.dp)) {
                    TextButton({ tab = 0 }) { Text(detail?.calendarTitle ?: "追剧日历", color = if (tab == 0) HuluwaColors.Pine else HuluwaColors.Muted, fontWeight = FontWeight.SemiBold) }
                    TextButton({ tab = 1 }) { Text("我的记录 ${ui.selectedLogs.size}", color = if (tab == 1) HuluwaColors.Pine else HuluwaColors.Muted) }
                }
                if (ui.detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = HuluwaColors.Pine)
                ui.detail.error?.let { StatusNotice(it, onRetry = vm::refreshDetail) }
            }
            if (tab == 0) {
                item {
                    if (detail?.calendarUrl?.isNotBlank() == true) {
                        Panel(Modifier.fillMaxWidth(), contentPadding = 12.dp) {
                            Text(detail.calendarTitle, Modifier.padding(4.dp, 4.dp, 4.dp, 12.dp), style = MaterialTheme.typography.titleMedium)
                            DengtaCalendarImage(detail.calendarUrl, detail.calendarTitle) { onCalendar(detail.calendarUrl) }
                            Text("点击海报可放大查看。日历可能晚于实时数据更新，以平台实际播出为准。", Modifier.padding(4.dp, 12.dp, 4.dp, 3.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                        }
                    } else if (!ui.detail.loading && ui.detail.error == null) Panel(Modifier.fillMaxWidth()) {
                        DengtaEmpty("暂未提供${detail?.calendarTitle ?: "追剧日历"}", "仍可记录观看进度；排期公布后刷新即可查看。")
                    }
                }
                item { DengtaRules(ui, vm) }
                if (ui.selectedLogs.isNotEmpty()) item { Text("最近的观看记录", style = MaterialTheme.typography.titleMedium) }
            }
            val logs = if (tab == 0) ui.selectedLogs.take(2) else ui.selectedLogs
            items(logs, key = { it.id }) { log -> DengtaLogCard(log) }
            if (tab == 1 && logs.isEmpty()) item {
                Panel(Modifier.fillMaxWidth()) { DengtaEmpty("还没有观看记录", "记下看到哪一集，也可以留下一句观剧感受。") }
            }
            item { DengtaSourceNote(ui.detail.updatedAt) }
        }
    }
}

@Composable private fun DengtaLogCard(log: DengtaWatchLog) {
    val time = remember(log.recordedAt) { Instant.ofEpochMilli(log.recordedAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm")) }
    Panel(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(7.dp).background(HuluwaColors.Pine, CircleShape))
            Text(time, Modifier.weight(1f), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
        }
        Text("看到第${log.episode}集", Modifier.padding(top = 10.dp), color = HuluwaColors.Pine, fontWeight = FontWeight.SemiBold)
        if (log.note.isNotBlank()) Text(log.note, Modifier.padding(top = 7.dp), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable internal fun DengtaRecordDialog(ui: DengtaState, vm: DengtaViewModel, onDismiss: () -> Unit) {
    val media = ui.selected ?: return
    var episode by rememberSaveable(media.id) { mutableStateOf((media.updatedEpisodes ?: ui.selectedFollow?.watched?.takeIf { it > 0 } ?: 1).toString()) }
    var note by rememberSaveable(media.id) { mutableStateOf("") }
    val number = episode.toIntOrNull()
    val valid = number != null && number > 0 && (media.episodeCount == null || number <= media.episodeCount)
    AlertDialog(onDismissRequest = { if (!ui.journalBusy) onDismiss() }, title = { Text("记录这一次追剧") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text(media.name, color = HuluwaColors.Muted)
                OutlinedTextField(episode, { episode = it.filter(Char::isDigit).take(9) }, label = { Text("看到第几集") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), isError = episode.isNotBlank() && !valid,
                    supportingText = { Text(media.episodeCount?.let { "共${it}集，请填写实际观看进度" } ?: "请填写正整数集数") })
                OutlinedTextField(note, { note = it.take(160) }, label = { Text("观剧感受（选填）") }, minLines = 3,
                    supportingText = { Text("${note.length}/160") })
                ui.journalError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }, confirmButton = {
            TextButton({ number?.let { vm.record(it, note, onDismiss) } }, enabled = valid && !ui.journalBusy && !ui.journalLoading) {
                Text(if (ui.journalBusy) "保存中…" else "保存记录")
            }
        }, dismissButton = { TextButton(onDismiss, enabled = !ui.journalBusy) { Text("取消") } })
}

@Composable private fun DengtaCalendarImage(url: String, title: String, onOpen: () -> Unit) {
    var ratio by remember(url) { mutableFloatStateOf(.6f) }
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    var retry by remember(url) { mutableIntStateOf(0) }
    if (failed) StatusNotice("日历图片加载失败", onRetry = { retry++; failed = false; loading = true })
    else Box(Modifier.fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(HuluwaColors.SoftGreen)
        .clickable(onClickLabel = "放大查看$title", onClick = onOpen), contentAlignment = Alignment.Center) {
        key(retry) {
            AsyncImage(url, title, Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                onSuccess = {
                    val size = it.painter.intrinsicSize
                    if (size.width.isFinite() && size.height.isFinite() && size.width > 0 && size.height > 0) ratio = (size.width / size.height).coerceIn(.1f, 3f)
                    loading = false
                }, onError = { failed = true; loading = false })
        }
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = HuluwaColors.Pine)
    }
}

@Composable internal fun DengtaCalendarViewer(url: String, title: String, onDismiss: () -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var image by remember(url) { mutableStateOf(Size.Zero) }
    var failed by remember(url) { mutableStateOf(false) }
    var retry by remember(url) { mutableIntStateOf(0) }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭日历", tint = Color.White) }
                Text(title, Modifier.weight(1f), color = Color.White, style = MaterialTheme.typography.titleMedium)
                IconButton({ scale = 1f; offset = Offset.Zero }) { Icon(Icons.Outlined.ZoomOutMap, "重置图片缩放", tint = Color.White) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().clipToBounds().onSizeChanged { viewport = it }
                .pointerInput(url, viewport, image) {
                    detectTransformGestures { centroid, pan, zoom, _ ->
                        val next = (scale * zoom).coerceIn(1f, 5f)
                        val center = Offset(viewport.width / 2f, viewport.height / 2f)
                        val focus = centroid - center
                        val translated = (offset - focus) * (next / scale) + focus + pan
                        val fit = if (image.width > 0 && image.height > 0) minOf(viewport.width / image.width, viewport.height / image.height) else 1f
                        val maxX = ((image.width * fit * next - viewport.width) / 2).coerceAtLeast(0f)
                        val maxY = ((image.height * fit * next - viewport.height) / 2).coerceAtLeast(0f)
                        scale = next; offset = Offset(translated.x.coerceIn(-maxX, maxX), translated.y.coerceIn(-maxY, maxY))
                    }
                }, contentAlignment = Alignment.Center) {
                key(retry) {
                    AsyncImage(url, title, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                        contentScale = ContentScale.Fit, onSuccess = {
                            val intrinsic = it.painter.intrinsicSize
                            if (intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.width > 0 && intrinsic.height > 0) image = intrinsic
                            failed = false
                        }, onError = { failed = true })
                }
                if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("图片暂时无法加载", color = Color.White)
                    TextButton({ retry++; failed = false }) { Text("重新加载", color = HuluwaColors.Lime) }
                }
            }
            Text("双指缩放，拖动查看排期", Modifier.align(Alignment.CenterHorizontally).padding(16.dp), color = Color.LightGray, fontSize = 12.sp)
        }
    }
}
