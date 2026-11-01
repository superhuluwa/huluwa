package com.huluwa.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.progressSemantics
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LifecycleStartEffect
import coil3.compose.AsyncImage
import com.huluwa.android.DengtaPage
import com.huluwa.android.DengtaState
import com.huluwa.android.DengtaViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable fun DengtaScreen(vm: DengtaViewModel, title: String, onClose: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val pages = rememberSaveableStateHolder()
    var record by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf<String?>(null) }
    val back = { if (!vm.back()) onClose() }
    BackHandler(onBack = back)
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    LifecycleStartEffect(vm to ui.page) {
        vm.setRankingVisible(ui.page == DengtaPage.Ranking)
        onStopOrDispose { vm.setRankingVisible(false) }
    }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    if (ui.page == DengtaPage.Ranking) SessionIcon(DENGTA_SESSION_KIND, Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(when (ui.page) { DengtaPage.Ranking -> title; DengtaPage.Detail -> "剧集详情"; DengtaPage.Journal -> "追剧日志" },
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (ui.page == DengtaPage.Ranking) "影视榜单与追剧日历" else ui.selected?.name ?: "每一集，都有记录",
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().navigationBarsPadding()) {
            val pageKey = if (ui.page == DengtaPage.Ranking) "ranking" else "${ui.page}:${ui.selected?.id.orEmpty()}"
            pages.SaveableStateProvider(pageKey) {
                when (ui.page) {
                    DengtaPage.Ranking -> DengtaRankingPage(ui, vm)
                    DengtaPage.Detail -> DengtaDetailPage(ui, vm) { calendar = it }
                    DengtaPage.Journal -> DengtaJournalPage(ui, vm, { record = true }, { calendar = it })
                }
            }
        }
    }
    if (record && ui.selected != null) DengtaRecordDialog(ui, vm) { record = false }
    calendar?.let { url -> DengtaCalendarViewer(url, "${ui.selected?.name.orEmpty()}${ui.detail.data?.calendarTitle ?: "追剧日历"}") { calendar = null } }
}

@Composable private fun DengtaRankingPage(ui: DengtaState, vm: DengtaViewModel) {
    val context = LocalContext.current
    val list = rememberLazyListState()
    val data = ui.ranking.data
    val rows = data.rows
    LaunchedEffect(ui.category, ui.typeCode, ui.metricCode, ui.date) { list.scrollToItem(0) }
    val date = dengtaDate(data.date.ifBlank { ui.date }) ?: LocalDate.now(ZoneId.of("Asia/Shanghai"))
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 28.dp)) {
        item {
            Row(Modifier.padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("看见好故事", color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium)
                    Text("全网${dengtaCategoryName(ui.category)}榜", fontSize = 26.sp, fontWeight = FontWeight.Bold)
                }
                TextButton({
                    val last = dengtaDate(data.lastDate) ?: LocalDate.now(ZoneId.of("Asia/Shanghai"))
                    val first = dengtaDate(data.firstDate)?.takeIf { !it.isAfter(last) } ?: last.minusYears(2)
                    val selected = date.coerceIn(first, last)
                    DatePickerDialog(context, { _, year, month, day -> vm.selectDate(LocalDate.of(year, month + 1, day).format(DateTimeFormatter.BASIC_ISO_DATE)) },
                        selected.year, selected.monthValue - 1, selected.dayOfMonth).apply {
                        datePicker.minDate = first.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        datePicker.maxDate = last.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    }.show()
                }) {
                    Text(date.format(DateTimeFormatter.ofPattern("MM月dd日", Locale.CHINA)), fontSize = 12.sp)
                    Icon(Icons.Outlined.ExpandMore, null, Modifier.size(16.dp))
                }
            }
            Panel(Modifier.fillMaxWidth(), HuluwaColors.PineDark, 20.dp) {
                Text(data.title, color = HuluwaColors.Lime, style = MaterialTheme.typography.bodySmall)
                // 大盘总量只有正片播放榜提供；集均、市占和平台热度榜不显示，避免给出误导性的「—」。
                if (data.market.value.isNotBlank()) Row(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(data.market.value, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text(data.market.unit, Modifier.padding(start = 5.dp, bottom = 6.dp), color = HuluwaColors.Lime)
                    DengtaIncrement(ui.marketIncrement, ui.rankingUpdateId, HuluwaColors.Lime,
                        Modifier.weight(1f).padding(start = 8.dp).align(Alignment.Top))
                } else Spacer(Modifier.height(10.dp))
                val updated = if (ui.ranking.updatedAt > 0L) "获取于 ${statusTime(ui.ranking.updatedAt)}" else "等待获取"
                Text("日榜 · ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)} · $updated${if (ui.ranking.error != null) " · 上次数据" else ""}",
                    color = Color(0xFFDCE9DA), style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("1" to "剧集", "2" to "综艺", "3" to "动漫").forEach { (id, name) ->
                    FilterChip(ui.category == id, { vm.selectCategory(id) }, label = { Text(name) })
                }
                if (ui.date.isNotBlank()) TextButton({ vm.selectDate("") }) { Text("今天", fontSize = 12.sp) }
            }
            // 子类与指标都来自灯塔配置，和网页版 tab 一一对应：剧集下有全部／电视剧／网剧，综艺下是播放市占。
            val type = ui.config.data?.type(ui.typeCode)
            if (!type?.metrics.isNullOrEmpty()) Row(Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                type?.let { current -> current.metrics.forEach { metric ->
                    FilterChip(metric.code == ui.metricCode, { vm.selectMetric(metric.code) }, label = { Text(metric.title, fontSize = 12.sp) })
                } }
            }
            if (type != null && (ui.config.data?.types?.count { it.visible } ?: 0) > 1) Row(
                Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ui.config.data?.types?.filter { it.visible }?.forEach { item ->
                    TextButton({ vm.selectType(item.code) }, contentPadding = PaddingValues(horizontal = 6.dp)) {
                        Text(item.title, color = if (item.code == ui.typeCode) HuluwaColors.Pine else HuluwaColors.Muted,
                            fontWeight = if (item.code == ui.typeCode) FontWeight.SemiBold else FontWeight.Normal, fontSize = 13.sp)
                    }
                }
            }
            ui.config.error?.let { StatusNotice(it, "榜单筛选项加载失败", onRetry = vm::refreshRanking) }
            if (ui.config.loading && type == null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = HuluwaColors.Pine)
            ui.ranking.error?.let { StatusNotice(it, if (data.rows.isNotEmpty()) "正在显示上次获取的数据" else null, onRetry = vm::refreshRanking) }
            if (ui.ranking.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = HuluwaColors.Pine)
            if (rows.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("排名 / 作品", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(data.rightTitle, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (rows.isEmpty() && !ui.ranking.loading && ui.ranking.error == null && !ui.config.loading) item {
            DengtaEmpty("暂无匹配的榜单数据", "试试切换日期、作品类型或播放指标。")
        }
        items(rows, key = { it.media.id }) { row ->
            Column(Modifier.fillMaxWidth().background(Color.White).clickable { vm.openDetail(row.media) }.padding(horizontal = 12.dp)) {
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.widthIn(min = 32.dp).width(IntrinsicSize.Min), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(row.rank.toString().padStart(2, '0'), color = if (row.rank <= 3) HuluwaColors.Amber else HuluwaColors.Muted,
                            fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        Text(if (row.rankChange == 0) "—" else "${if (row.rankChange > 0) "↑" else "↓"}${abs(row.rankChange.toLong())}",
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false)
                    }
                    DengtaPoster(row.media.poster, row.media.name, Modifier.size(42.dp, 62.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(row.media.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                        DengtaPlatformLogos(row.platformCode, row.media.platforms)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.openDay, color = HuluwaColors.Muted, fontSize = 10.sp, maxLines = 1)
                            if (row.playStateImage.isNotBlank()) AsyncImage(row.playStateImage, "播出状态", Modifier.size(36.dp, 16.dp), contentScale = ContentScale.Fit)
                        }
                    }
                    Column(Modifier.widthIn(min = 74.dp, max = 105.dp), horizontalAlignment = Alignment.End) {
                        // 只有正片播放榜给播放次数增量；集均、市占和平台热度不累计播放量。
                        DengtaIncrement(if (data.metricCode == DENGTA_METRIC_PLAY) ui.rowIncrements[row.media.id] ?: 0L else 0L,
                            ui.rankingUpdateId, HuluwaColors.Danger, Modifier.width(78.dp))
                        Text(row.value.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        if (row.note.isNotBlank()) Text(row.note, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val progress = row.percent
                        if (progress != null) {
                            Box(Modifier.padding(top = 8.dp).width(78.dp).height(3.dp).progressSemantics(progress.toFloat().coerceIn(0f, 1f))
                                .clip(RoundedCornerShape(3.dp)).background(HuluwaColors.SoftGreen)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(progress.toFloat().coerceIn(0f, 1f)).background(HuluwaColors.Pine, RoundedCornerShape(3.dp)))
                            }
                        }
                    }
                }
                HorizontalDivider(color = HuluwaColors.Line)
            }
        }
        item { DengtaSourceNote(ui.ranking.updatedAt) }
    }
}

/** 为增量预留固定文字高度，出现和淡出时不挤动数值、海报或列表行。 */
@Composable private fun DengtaIncrement(value: Long, updateId: Long, color: Color, modifier: Modifier = Modifier) {
    var visible by remember { mutableStateOf(false) }
    var displayedValue by remember { mutableLongStateOf(0L) }
    LaunchedEffect(value, updateId) {
        if (value != 0L) displayedValue = value
        visible = value != 0L
        if (visible) {
            delay(2_050L)
            visible = false
        }
    }
    val height = with(LocalDensity.current) { 18.sp.toDp() }
    Box(modifier.height(height), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(visible,
            enter = fadeIn(tween(150)) + slideInVertically(tween(180)) { it / 3 },
            exit = fadeOut(tween(250)) + slideOutVertically(tween(250)) { -it / 2 }) {
            Text(if (displayedValue > 0L) "+$displayedValue" else displayedValue.toString(), color = color, fontSize = 11.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable internal fun DengtaDropdown(value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(value, fontSize = 11.sp, maxLines = 1)
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(14.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { expanded = false; onSelect(option) }) }
        }
    }
}

@Composable internal fun DengtaPoster(url: String, title: String, modifier: Modifier) {
    var failed by remember(url) { mutableStateOf(false) }
    Box(modifier.clip(RoundedCornerShape(9.dp)).background(HuluwaColors.SoftGreen), contentAlignment = Alignment.Center) {
        if (url.isNotBlank() && !failed) AsyncImage(url, "$title 海报", Modifier.fillMaxSize(), contentScale = ContentScale.Crop, onError = { failed = true })
        else Icon(Icons.Outlined.LocalMovies, "$title 暂无可用海报", tint = HuluwaColors.Muted)
    }
}
@Composable internal fun DengtaEmpty(title: String, description: String = "") {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Icon(Icons.Outlined.Movie, null, Modifier.size(32.dp), tint = HuluwaColors.Muted)
        Text(title, style = MaterialTheme.typography.titleSmall)
        if (description.isNotBlank()) Text(description, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
    }
}
@Composable internal fun DengtaSourceNote(time: Long) {
    Text("数据来源：灯塔专业版 · ${statusUpdatedAt(time)}\n播放量与平台热度按各自口径展示", Modifier.fillMaxWidth().padding(vertical = 24.dp),
        style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
}
