package com.huluwa.android.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.huluwa.android.DengtaPage
import com.huluwa.android.DengtaState
import com.huluwa.android.DengtaViewModel
import com.huluwa.android.data.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@Composable fun DengtaScreen(vm: DengtaViewModel, onClose: () -> Unit) {
    val ui by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val pages = rememberSaveableStateHolder()
    var record by remember { mutableStateOf(false) }
    var calendar by remember { mutableStateOf<String?>(null) }
    val back = { if (!vm.back()) onClose() }
    BackHandler(onBack = back)
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }
    Scaffold(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
        containerColor = HuluwaColors.Paper, contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            Surface(color = Color.White) {
                Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "返回") }
                    if (ui.page == DengtaPage.Ranking) RoundIcon(Icons.Outlined.Insights, modifier = Modifier.size(36.dp))
                    Column(Modifier.weight(1f).padding(start = 8.dp)) {
                        Text(when (ui.page) { DengtaPage.Ranking -> "灯塔"; DengtaPage.Detail -> "剧集详情"; DengtaPage.Journal -> "追剧日志" },
                            style = MaterialTheme.typography.titleMedium)
                        Text(if (ui.page == DengtaPage.Ranking) "影视榜单与追剧日历" else ui.selected?.name ?: "每一集，都有记录",
                            color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    val loading = when { ui.page == DengtaPage.Ranking -> ui.ranking.loading; ui.selected != null -> ui.detail.loading; else -> ui.journalLoading }
                    IconButton({ when { ui.page == DengtaPage.Ranking -> vm.refreshRanking(); ui.selected != null -> vm.refreshDetail(); else -> vm.loadJournal() } }, enabled = !loading) {
                        Icon(Icons.Outlined.Refresh, "刷新当前页面")
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
                    DengtaPage.Detail -> DengtaDetailPage(ui, vm)
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
    val rows = data.rows.filter { row ->
        ui.platform == "全部平台" || row.media.platforms.contains(ui.platform) || when (ui.platform) {
            "优酷" -> row.platformCode.contains("youku"); "爱奇艺" -> row.platformCode.contains("iqiyi")
            "腾讯视频" -> row.platformCode.contains("tencent"); "芒果TV" -> row.platformCode.contains("mango")
            "哔哩哔哩" -> row.platformCode.contains("bilibili"); else -> false
        }
    }
    LaunchedEffect(ui.type, ui.date, ui.platform) { list.scrollToItem(0) }
    val date = dengtaDate(data.date.ifBlank { ui.date }) ?: LocalDate.now(ZoneId.of("Asia/Shanghai"))
    LazyColumn(state = list, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(18.dp, 8.dp, 18.dp, 28.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("排行榜", Modifier.padding(horizontal = 12.dp, vertical = 12.dp), color = HuluwaColors.Pine, fontWeight = FontWeight.Bold)
                TextButton({ vm.openJournal() }) { Text("追剧日志", color = HuluwaColors.Muted) }
            }
            Row(Modifier.padding(top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("看见好故事", color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium)
                    Text("全网${when (ui.type) { "2" -> "综艺"; "3" -> "动漫"; else -> "剧集" }}榜", fontSize = 26.sp, fontWeight = FontWeight.Bold)
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
                Row(Modifier.padding(top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                    Text(data.market.value.ifBlank { "—" }, color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    Text(data.market.unit, Modifier.padding(start = 5.dp, bottom = 6.dp), color = HuluwaColors.Lime)
                }
                Text("日榜 · ${date.format(DateTimeFormatter.ISO_LOCAL_DATE)} · ${statusUpdatedAt(ui.ranking.updatedAt, ui.ranking.error != null)}",
                    color = Color(0xFFDCE9DA), style = MaterialTheme.typography.labelMedium)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                listOf("1" to "剧集", "2" to "综艺", "3" to "动漫").forEach { (id, name) ->
                    FilterChip(ui.type == id, { vm.selectType(id) }, label = { Text(name) })
                }
                if (ui.date.isNotBlank()) TextButton({ vm.selectDate("") }) { Text("今天", fontSize = 12.sp) }
            }
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton({ vm.selectMetric(false) }) { Text("播放量", color = if (!ui.shareMetric) HuluwaColors.Pine else HuluwaColors.Muted, fontWeight = FontWeight.SemiBold) }
                TextButton({ vm.selectMetric(true) }) { Text("播放市占", color = if (ui.shareMetric) HuluwaColors.Pine else HuluwaColors.Muted) }
                Spacer(Modifier.weight(1f))
                DengtaDropdown(ui.platform, listOf("全部平台", "优酷", "爱奇艺", "腾讯视频", "芒果TV", "哔哩哔哩"), vm::selectPlatform)
            }
            ui.ranking.error?.let { StatusNotice(it, if (data.rows.isNotEmpty()) "正在显示上次获取的数据" else null, onRetry = vm::refreshRanking) }
            if (ui.ranking.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = HuluwaColors.Pine)
            if (rows.isNotEmpty()) Row(Modifier.fillMaxWidth().padding(4.dp, 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("排名 / 作品", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                Text(if (ui.shareMetric) "播放市占率" else data.column, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
            }
        }
        if (rows.isEmpty() && !ui.ranking.loading && ui.ranking.error == null) item {
            DengtaEmpty("暂无匹配的榜单数据", "试试切换日期、作品类型或播放平台。")
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
                        Text(row.media.platforms.ifBlank { "平台待公布" }, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(row.openDay, color = HuluwaColors.Muted, fontSize = 10.sp, maxLines = 1)
                            if (row.playStateImage.isNotBlank()) AsyncImage(row.playStateImage, "播出状态", Modifier.size(36.dp, 16.dp), contentScale = ContentScale.Fit)
                        }
                    }
                    Column(Modifier.widthIn(min = 74.dp, max = 105.dp), horizontalAlignment = Alignment.End) {
                        Text(if (ui.shareMetric) row.share.ifBlank { "—" } else row.plays.label, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text(if (ui.shareMetric) row.plays.label else "市占 ${row.share.ifBlank { "—" }}", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
                        val max = data.rows.mapNotNull { it.plays.raw }.maxOrNull()
                        if (row.plays.raw != null && max != null && max > 0) {
                            val progress = (row.plays.raw / max).toFloat().coerceIn(0f, 1f)
                            Box(Modifier.padding(top = 8.dp).width(78.dp).height(3.dp).progressSemantics(progress)
                                .clip(RoundedCornerShape(3.dp)).background(HuluwaColors.SoftGreen)) {
                                Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(HuluwaColors.Pine, RoundedCornerShape(3.dp)))
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
