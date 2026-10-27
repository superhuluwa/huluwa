package com.huluwa.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huluwa.android.*
import com.huluwa.android.data.*
import com.huluwa.android.data.isServerUnreachable
import kotlinx.serialization.json.JsonObject

/**
 * 懂片帝（独立一套界面，与电影港 / 电影天堂完全分开）：
 *
 *  - [DpdResultCard]：聊天里的作品卡，只有「资源列表」与「网站直达」两个入口，
 *    没有订阅按钮（站点按集下发 token，没有稳定地址可比对更新）；
 *  - [DpdSourceDialog]：线路弹框，线路分页签 + 点选后懒解析各集，
 *    每集可播放 / 复制 m3u8 直链 / 离线下载。
 *
 * 后端载荷用 kind=dpd-title / dpd-detail / dpd-line 区分，字段也不与其它来源重合。
 */

// ---------------------------------------------------------------- 聊天卡片

@Composable fun DpdResultCard(card: DpdCard, ui: AppState, vm: HuluwaViewModel) {
    val websiteBusy = ui.busy == "dpd-website:${card.key()}"
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Poster(card.title, card.cover, size = DpSize(64.dp, 90.dp))
            Column(Modifier.weight(1f)) {
                Text(card.title, color = HuluwaColors.Ink, style = MaterialTheme.typography.titleMedium,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (card.meta.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(card.meta, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (card.remarks.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("状态：${card.remarks}", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(8.dp))
                DpdSourceTag()
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DpdActionButton("资源列表", Icons.Outlined.PlayCircleOutline, enabled = ui.busy == null) {
                vm.openDpdSources(card)
            }
            DpdActionButton(if (websiteBusy) "正在打开…" else "网站直达", Icons.Outlined.OpenInNew,
                busy = websiteBusy, enabled = ui.busy == null) {
                vm.openDpdWebsite(card)
            }
        }
    }
}

/** 来源标记：一眼区分这不是电影港 / 电影天堂的卡片 */
@Composable private fun DpdSourceTag() {
    Surface(color = HuluwaColors.SoftGreen, shape = RoundedCornerShape(5.dp)) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(Icons.Outlined.PlayCircleOutline, null, Modifier.size(12.dp), tint = HuluwaColors.Pine)
            Text("懂片帝 多线路", color = HuluwaColors.Pine, style = MaterialTheme.typography.labelMedium, maxLines = 1)
        }
    }
}

/** 卡片按钮：与电影港卡片的 FilmActionButton 同款视觉，但独立实现，避免互相牵连 */
@Composable private fun DpdActionButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                                       busy: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled && !busy, shape = RoundedCornerShape(10.dp),
        color = if (enabled && !busy) HuluwaColors.White else HuluwaColors.Paper,
        border = BorderStroke(1.dp, HuluwaColors.Line)) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(icon, null, Modifier.size(14.dp), tint = if (enabled && !busy) HuluwaColors.Pine else HuluwaColors.Muted)
            Text(label, color = if (enabled && !busy) HuluwaColors.Pine else HuluwaColors.Muted,
                style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- 线路弹框

/**
 * 懂片帝资源弹框：顶部线路分页签，选中线路后拉取该线路的全部剧集。
 *
 * <p>详情接口只给线路名与集数，各集地址由 `/api/dpd/line` 按线路懒解析，
 * 因此切换线路时会出现短暂的「正在解析」，解析完的线路再切回不再请求。
 */
@Composable fun DpdSourceDialog(sheet: AppSheet.Dpd, busy: Boolean, actionError: String?, vm: HuluwaViewModel, onPlay: (String) -> Unit) {
    val card = sheet.card
    val detail = sheet.detail
    val context = LocalContext.current
    fun copy(text: String, label: String) {
        if (text.isBlank()) return
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        vm.notify("${label}已复制")
    }
    val lines = remember(detail.data) { DpdLine.rows(detail.data) }
    var selected by rememberSaveable(card.key()) { mutableIntStateOf(0) }
    val current = lines.getOrNull(selected.coerceIn(0, (lines.size - 1).coerceAtLeast(0)))?.name.orEmpty()
    // 选中线路后按需解析；解析过的线路由 ViewModel 缓存，不会重复请求
    LaunchedEffect(current) { if (current.isNotBlank()) vm.loadDpdLine(card, current) }
    val lineState = sheet.lines[current]

    Dialog(onDismissRequest = vm::dismissDpdSources, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max = 620.dp).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .88f),
            shape = RoundedCornerShape(20.dp), color = HuluwaColors.White, border = BorderStroke(1.dp, HuluwaColors.Line)) {
            Column(Modifier.fillMaxWidth()) {
                // 标题
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Outlined.PlayCircleOutline, null, Modifier.size(16.dp), tint = HuluwaColors.Pine)
                    Column(Modifier.weight(1f)) {
                        Text(detail.data.text("title", fallback = card.title), style = MaterialTheme.typography.titleMedium,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(detail.data.text("year"), detail.data.text("area"), detail.data.text("remarks"))
                            .filter(String::isNotBlank).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, maxLines = 1)
                    }
                    if (lines.size > 1) Text("${lines.size} 条线路", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    IconButton({ vm.reloadDpdSources(card) }, enabled = !detail.loading) {
                        Icon(Icons.Outlined.Refresh, "重新抓取", Modifier.size(19.dp), tint = HuluwaColors.Pine)
                    }
                    IconButton(vm::dismissDpdSources) { Icon(Icons.Outlined.Close, "关闭", Modifier.size(20.dp), tint = HuluwaColors.Muted) }
                }
                HorizontalDivider(color = HuluwaColors.Line)
                Box(Modifier.weight(1f, fill = false)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        actionError?.takeUnless { it.isServerUnreachable() }?.let {
                            Text(it, Modifier.padding(bottom = 10.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
                        }
                        Feedback(detail.error, { vm.reloadDpdSources(card) })
                        if (!detail.loaded) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = HuluwaColors.Pine)
                            return@Column
                        }
                        if (detail.error != null && detail.data.isEmpty()) return@Column
                        if (lines.isEmpty()) {
                            Text("该作品暂无可直接播放的 m3u8 线路", Modifier.fillMaxWidth().padding(vertical = 28.dp),
                                style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                            return@Column
                        }
                        DpdLineTabs(lines, selected, { selected = it })
                        // 懂片帝不是订阅来源，没有订阅季号，固定按第 1 季归档
                        DpdEpisodeList(card, current, lineState, null, busy, vm, onPlay) { url -> copy(url, "资源地址") }
                    }
                }
                HorizontalDivider(color = HuluwaColors.Line)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (sheet.busyLine.isNotBlank()) "正在解析「${sheet.busyLine}」…" else "点选线路后可播放、复制 m3u8 直链；离线任务在「下载 → 离线剧集」查看",
                        Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    OutlinedButton(vm::dismissDpdSources, Modifier.height(36.dp), shape = RoundedCornerShape(11.dp),
                        border = BorderStroke(1.dp, HuluwaColors.Line), contentPadding = PaddingValues(horizontal = 14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = HuluwaColors.Pine)) {
                        Text("关闭", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

/** 线路分页签（横向滚动胶囊） */
@Composable private fun DpdLineTabs(lines: List<DpdLine>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        lines.forEachIndexed { index, line ->
            val active = index == selected.coerceIn(lines.indices)
            Surface(onClick = { onSelect(index) }, shape = RoundedCornerShape(999.dp),
                color = if (active) HuluwaColors.SoftGreen else HuluwaColors.White,
                border = BorderStroke(1.dp, if (active) HuluwaColors.Pine else HuluwaColors.Line)) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(line.name, style = MaterialTheme.typography.labelLarge,
                        color = if (active) HuluwaColors.Pine else HuluwaColors.Muted, maxLines = 1)
                    if (line.total > 0) Text("${line.total}", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                }
            }
        }
    }
}

/** 某条线路的分集列表：加载中 / 失败 / 空 / 各集 */
@Composable private fun DpdEpisodeList(card: DpdCard, lineName: String, state: LoadState<JsonObject>?, defaultSeason: Int?,
                                       busy: Boolean, vm: HuluwaViewModel, onPlay: (String) -> Unit, onCopy: (String) -> Unit) {
    when {
        state == null || state.loading -> Row(Modifier.fillMaxWidth().padding(vertical = 26.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CircularProgressIndicator(Modifier.size(16.dp), color = HuluwaColors.Pine, strokeWidth = 2.dp)
            Text("正在解析「$lineName」的各集地址…", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        }
        state.error != null -> Feedback(state.error, { vm.loadDpdLine(card, lineName) })
        else -> {
            val episodes = remember(state.data) { DpdEpisode.rows(state.data) }
            if (episodes.isEmpty()) {
                Text("该线路没有可播放的地址，换一条试试", Modifier.fillMaxWidth().padding(vertical = 28.dp),
                    style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
                return
            }
            // 新集在前，与电影天堂弹层的排序一致；识别不出集号的条目按原顺序排在最后
            episodes.withIndex()
                .sortedWith(compareByDescending<IndexedValue<DpdEpisode>> { episodeNumber(it.value.title) ?: -1 }.thenBy { it.index })
                .forEach { (_, episode) -> DpdEpisodeRow(card, lineName, episode, defaultSeason, busy, vm, onPlay, onCopy) }
        }
    }
}

@Composable private fun DpdEpisodeRow(card: DpdCard, lineName: String, episode: DpdEpisode, defaultSeason: Int?,
                                      busy: Boolean, vm: HuluwaViewModel, onPlay: (String) -> Unit, onCopy: (String) -> Unit) {
    // 官方线路受站点限速，只有用户点开的那一集才兑换，其余集地址为空、显示「待解析」
    val ready = episode.url.isNotBlank()
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)
        .background(Color(0x08243D32), RoundedCornerShape(8.dp))
        .border(1.dp, HuluwaColors.Line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(episode.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Ink,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (ready) {
                DpdTinyButton("复制", Icons.Outlined.ContentCopy, primary = true) { onCopy(episode.url) }
                DpdTinyButton("离线下载", Icons.Outlined.Download, primary = false, enabled = !busy) {
                    vm.enqueueDpdEpisode(card, lineName, episode, defaultSeason ?: 1, episodeNumber(episode.title) ?: 1)
                }
            } else {
                // 点一下才去兑换/解析这一集，省站点配额
                DpdTinyButton("解析", Icons.Outlined.PlayCircleOutline, primary = true, enabled = !busy) {
                    vm.resolveDpdEpisode(card, lineName, episode.title)
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        if (ready) {
            Text(episode.url, Modifier.fillMaxWidth().clickable { onPlay(episode.url) },
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Text("点「解析」获取这一集的地址（站点限速，逐集解析）",
                style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable private fun DpdTinyButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
                                      primary: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(onClick = onClick, enabled = enabled, shape = RoundedCornerShape(8.dp),
        color = if (primary) HuluwaColors.Pine else HuluwaColors.White,
        border = if (primary) null else BorderStroke(1.dp, HuluwaColors.Line)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(13.dp), tint = if (primary) HuluwaColors.White else HuluwaColors.Pine)
            Text(label, color = if (primary) HuluwaColors.White else HuluwaColors.Pine, fontSize = 12.sp,
                fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/** 从集标题里识别集号（「第3集」/「EP03」/「03」），识别不到返回 null 排在最后。
 *  懂片帝独立维护一份，避免与电影天堂弹层的排序逻辑互相牵连。 */
private fun episodeNumber(title: String): Int? {
    val value = title.trim()
    if (Regex("预告|花絮|番外|彩蛋|特辑|特别篇|片花|幕后|先导|解说|试看").containsMatchIn(value)) return null
    if (Regex("(?:S\\d+E\\d+(?:[ ._-]*E|[-~])\\d+)|(?:第\\s*\\d+\\s*[-~至]\\s*\\d+\\s*集)").containsMatchIn(value)) return null
    val patterns = listOf(
        Regex("第\\s*(\\d{1,4})\\s*[集话期]"),
        Regex("(?:S\\d{1,3}E|(?:^|[ ._-])EP?)(\\d{1,4})(?!\\d)", RegexOption.IGNORE_CASE),
        Regex("^(\\d{1,4})(?:集|话|期)?$"),
    )
    patterns.forEach { pattern ->
        val number = pattern.find(value)?.groupValues?.get(1)?.toIntOrNull()
        if (number != null && number in 1..9999) return number
    }
    return null
}
