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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huluwa.android.*
import com.huluwa.android.data.*
import com.huluwa.android.data.isServerUnreachable
import kotlinx.serialization.json.*

/**
 * 资源列表弹框（对齐网页端 MagnetListDialog / PlaySourceDialog）：
 *  - 居中卡片：图标 + 剧名 + 条数标题，正文区内滚动，底部固定「关闭」；
 *  - 电影港：磁力与播放线路合并为同一组选项，磁力优先，点击直接切换；
 *  - 电影天堂：按线路分页签，每行「复制 / 离线下载」。
 */
@Composable fun FilmResources(sheet: AppSheet.Films, busy: Boolean, actionError: String?, vm: HuluwaViewModel, onPlay: (String) -> Unit) {
    val card = sheet.card
    val movieSources = if (card.isMovie) sheet.onlineData.data.rows("playSources").map { PlaySource.from(it.asObject()) }
        .sortedBy { if (it.name.trim().equals("dyttm3u8", ignoreCase = true)) 0 else 1 } else emptyList()
    var selectedSource by rememberSaveable(card.key()) { mutableIntStateOf(0) }
    val selectedMovieSource = selectedSource.coerceIn(0, movieSources.size)
    val online = card.isMovie && selectedMovieSource > 0
    val magnetScroll = rememberScrollState()
    val onlineScroll = rememberScrollState()
    val scroll = if (card.isMovie && online) onlineScroll else magnetScroll
    val resources = if (card.isMovie && online) sheet.onlineData else sheet.data
    val isHls = if (card.isMovie) online else card.isHls
    val resourceCard = if (card.isMovie) card.copy(
        id = resources.data.text("id", fallback = card.id),
        url = resources.data.text("url", fallback = card.url),
        source = if (online) "movie-hls" else "movie",
    ) else card
    val context = LocalContext.current
    // 复制反馈沿用全局 snackbar（vm.notify → 根界面 SnackbarHost）
    fun copy(text: String, label: String) {
        if (text.isBlank()) return
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText(label, text))
        vm.notify("${label}已复制")
    }
    // 与网页端 n-modal preset="card" 对齐：居中卡片、20dp 圆角、雾绿描边
    Dialog(onDismissRequest = vm::dismissSheet, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max = 620.dp).heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .88f),
            shape = RoundedCornerShape(20.dp), color = HuluwaColors.White, border = BorderStroke(1.dp, HuluwaColors.Line)) {
            Column(Modifier.fillMaxWidth()) {
                FilmDialogHeader(card, resources.data, sheet.data.loading || sheet.onlineData.loading, isHls, vm)
                HorizontalDivider(color = HuluwaColors.Line)
                Box(Modifier.weight(1f, fill = false)) {
                    Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 14.dp)) {
                        // 下载 / 提交失败在弹框内就地提示（共享的 actionError 会被底部弹层的错误区一起显示）
                        actionError?.takeUnless { it.isServerUnreachable() }?.let {
                            Text(it, Modifier.padding(bottom = 10.dp), color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall)
                        }
                        if (card.isMovie) {
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilmSourceChip("磁力", sheet.data.data.rows("magnets").size, selectedMovieSource == 0) { selectedSource = 0 }
                                movieSources.forEachIndexed { index, source ->
                                    FilmSourceChip(source.name, source.items.size, selectedMovieSource == index + 1) { selectedSource = index + 1 }
                                }
                            }
                            if (!online) {
                                Feedback(sheet.onlineData.error, { vm.reloadFilmResources(card) })
                                if (sheet.data.loaded && !sheet.onlineData.loaded) {
                                    LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 12.dp), color = HuluwaColors.Pine)
                                }
                            }
                        }
                        Feedback(resources.error, { vm.reloadFilmResources(card) })
                        if (!resources.loaded) {
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = HuluwaColors.Pine)
                            return@Column
                        }
                        if (resources.error != null && resources.data.isEmpty()) return@Column
                        if (card.isMovie && online) HlsSourceItems(resourceCard, movieSources[selectedMovieSource - 1], sheet.defaultSeason, busy, vm, onPlay) { url -> copy(url, "资源地址") }
                        else if (isHls) HlsSourceList(resourceCard, resources.data, sheet.defaultSeason, busy, vm, onPlay) { url -> copy(url, "资源地址") }
                        else MagnetSourceList(resourceCard, resources.data, busy, vm) { magnet -> copy(magnet, "磁力链接") }
                    }
                }
                HorizontalDivider(color = HuluwaColors.Line)
                FilmDialogFooter(!resources.loaded, card.isMovie && online, vm)
            }
        }
    }
}

/** 弹框标题：图标 + 剧名 + 条数，右侧「重新抓取」与关闭 */
@Composable private fun FilmDialogHeader(card: FilmCard, data: JsonObject, loading: Boolean, isHls: Boolean, vm: HuluwaViewModel) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 14.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(if (isHls) Icons.Outlined.PlayCircleOutline else Icons.Outlined.CloudDownload, null, Modifier.size(16.dp), tint = HuluwaColors.Pine)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(data.text("title", fallback = card.title), Modifier.weight(1f, fill = false),
                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TitleReconnectBadge(rememberReconnecting(vm))
        }
        // 电影港按磁力条数、电影天堂按各线路可复制条目合计（与网页端「可复制 N 条」一致）
        val count = if (isHls) data.rows("playSources").sumOf { source -> source.asObject().rows("items").size }
        else data.rows("magnets").size
        if (count > 0) Text(if (isHls) "可复制 $count 条" else "$count 条", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
        IconButton(onClick = { vm.reloadFilmResources(card) }, enabled = !loading) { Icon(Icons.Outlined.Refresh, "重新抓取", Modifier.size(19.dp), tint = HuluwaColors.Pine) }
        IconButton(vm::dismissSheet) { Icon(Icons.Outlined.Close, "关闭", Modifier.size(20.dp), tint = HuluwaColors.Muted) }
    }
}

/** 底部：说明文字 + 「关闭」，与网页端弹框 footer 一致 */
@Composable private fun FilmDialogFooter(loading: Boolean, movieHls: Boolean, vm: HuluwaViewModel) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (loading) "正在抓取资源…" else if (movieHls) "播放和复制时解析直链；离线任务在「下载 → 离线剧集」查看" else "下载结果会推送到对应下载工具会话；同名剧归入同一文件夹",
            Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        OutlinedButton(vm::dismissSheet, Modifier.height(36.dp), shape = RoundedCornerShape(11.dp), border = BorderStroke(1.dp, HuluwaColors.Line),
            contentPadding = PaddingValues(horizontal = 14.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = HuluwaColors.Pine)) { Text("关闭", style = MaterialTheme.typography.labelLarge) }
    }
}

// ---------------------------------------------------------------- 电影港：磁力

@Composable private fun MagnetSourceList(card: FilmCard, data: JsonObject, busy: Boolean, vm: HuluwaViewModel, onCopy: (String) -> Unit) {
    val items = data.rows("magnets").map { it.asObject() }
    if (items.isEmpty()) { EmptyHint("该作品暂无可用磁力资源"); return }
    items.forEach { item ->
        val downloaded = item.flag("downloaded")
        ResourceRow(downloaded = downloaded) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(episodeLabel(item.text("episode")), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                    color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TinyButton("复制", Icons.Outlined.ContentCopy, primary = true, enabled = item.text("magnet").isNotBlank()) { onCopy(item.text("magnet")) }
                if (downloaded) TinyButton("已下载", Icons.Outlined.Check, primary = true, enabled = false) {}
                else TinyButton("下载", Icons.Outlined.Download, primary = true,
                    enabled = !busy && item.text("magnet").isNotBlank()) { vm.enqueueMagnet(card, item) }
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QualityTag(item.text("quality"))
                Text(item.text("title").ifBlank { card.title }, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                    color = HuluwaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- 电影天堂：播放线路

@Composable private fun HlsSourceList(card: FilmCard, data: JsonObject, defaultSeason: Int?, busy: Boolean, vm: HuluwaViewModel, onPlay: (String) -> Unit, onCopy: (String) -> Unit) {
    val sources = data.rows("playSources").map { PlaySource.from(it.asObject()) }
        .sortedBy { if (it.name.trim().equals("dyttm3u8", ignoreCase = true)) 0 else 1 }
    if (sources.isEmpty()) { EmptyHint("该资源没有解析到可用地址"); return }
    var selected by rememberSaveable(card.key()) { mutableIntStateOf(0) }
    val current = sources[selected.coerceIn(sources.indices)]
    Text("点击「复制」拿到 m3u8 直链，粘贴到播放器即可观看", Modifier.padding(bottom = 12.dp),
        style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
    // 安卓端优先排列 dyttm3u8 并默认选中第一条，其余线路保持原有顺序。
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        sources.forEachIndexed { index, source ->
            FilmSourceChip(source.name, source.items.size, index == selected.coerceIn(sources.indices)) { selected = index }
        }
    }
    HlsSourceItems(card, current, defaultSeason, busy, vm, onPlay, onCopy)
}

@Composable private fun HlsSourceItems(card: FilmCard, source: PlaySource, defaultSeason: Int?, busy: Boolean, vm: HuluwaViewModel, onPlay: (String) -> Unit, onCopy: (String) -> Unit) {
    // 剧集按集号倒序（新集在前）；识别不出集号的条目按原顺序排在最后。
    val items = source.items.withIndex()
        .sortedWith(compareByDescending<IndexedValue<PlayItem>> { episodeNumber(it.value.title) ?: -1 }.thenBy { it.index })
        .map { it.value }
    items.forEach { item ->
        if (card.isMovieHls) {
            ResourceRow {
                Text(item.title, style = MaterialTheme.typography.bodyMedium, color = HuluwaColors.Ink)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TinyButton("播放", Icons.Outlined.PlayCircleOutline, primary = true, enabled = !busy) {
                        vm.resolveMovieHls(item, onPlay)
                    }
                    TinyButton("复制直链", Icons.Outlined.ContentCopy, primary = false, enabled = !busy) {
                        vm.resolveMovieHls(item, onCopy)
                    }
                    TinyButton("离线下载", Icons.Outlined.Download, primary = false, enabled = !busy) {
                        vm.showSheet(AppSheet.Episode(card, source.name, item))
                    }
                }
            }
            return@forEach
        }
        ResourceRow {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(item.title, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                TinyButton("复制", Icons.Outlined.ContentCopy, primary = true, enabled = item.url.isNotBlank()) { onCopy(item.url) }
                if (item.url.startsWith("http")) TinyButton("离线下载", Icons.Outlined.Download, primary = false, enabled = !busy) {
                    if (defaultSeason != null) {
                        // 沿用订阅季号并识别资源集号；电影正片等无集号条目默认第 1 集。
                        vm.enqueueEpisode(card, source.name, item, defaultSeason, episodeNumber(item.title) ?: 1)
                    } else vm.showSheet(AppSheet.Episode(card, source.name, item))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(item.url, Modifier.fillMaxWidth().clickable { onPlay(item.url) }, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                color = HuluwaColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

// ---------------------------------------------------------------- 共用小组件

/** 磁力与播放线路共用的圆角选项。 */
@Composable private fun FilmSourceChip(title: String, count: Int, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) HuluwaColors.SoftGreen else HuluwaColors.White,
        border = BorderStroke(1.dp, if (selected) HuluwaColors.Pine else HuluwaColors.Line),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = if (selected) HuluwaColors.Pine else HuluwaColors.Muted, maxLines = 1)
            Text("$count", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
        }
    }
}

/** 行容器：浅底 + 细边框 + 8dp 圆角，对应网页端 mm-row / ps-row；已下载的行整体压暗 */
@Composable private fun ResourceRow(downloaded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp).alpha(if (downloaded) .6f else 1f)
        .background(Color(0x08243D32), RoundedCornerShape(8.dp))
        .border(1.dp, HuluwaColors.Line, RoundedCornerShape(8.dp)).padding(horizontal = 10.dp, vertical = 8.dp), content = content)
}

/** 清晰度色标：2160P / 4K 绿、1080P 蓝、720P 琥珀、其余灰 */
@Composable private fun QualityTag(quality: String) {
    val value = quality.uppercase()
    val (color, background) = when (value) {
        "2160P", "4K" -> HuluwaColors.Pine to Color(0x2434D399)
        "1080P" -> HuluwaColors.Blue to Color(0x2496A5FA)
        "720P", "480P", "360P" -> HuluwaColors.Amber to Color(0x24FBBF24)
        else -> HuluwaColors.Muted to Color(0x17243D32)
    }
    Surface(shape = RoundedCornerShape(999.dp), color = background) {
        Text(value.ifBlank { "OTHER" }, Modifier.widthIn(min = 58.dp).padding(vertical = 2.dp), textAlign = TextAlign.Center,
            color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 小号行内按钮：主操作用松柏绿实心，次要操作用白底细边框 */
@Composable private fun TinyButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick, enabled = enabled,
        shape = RoundedCornerShape(8.dp), color = if (primary) HuluwaColors.Pine else HuluwaColors.White,
        border = if (primary) null else BorderStroke(1.dp, HuluwaColors.Line),
    ) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, null, Modifier.size(13.dp), tint = if (primary) HuluwaColors.White else HuluwaColors.Pine)
            Text(label, color = if (primary) HuluwaColors.White else HuluwaColors.Pine, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

@Composable private fun EmptyHint(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(vertical = 28.dp), textAlign = TextAlign.Center,
        style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted)
}

/** 集号展示：1-14 → 第1集-第14集，空值 → 未识别集数（与网页端 episodeLabel 一致） */
private fun episodeLabel(episode: String): String =
    if (episode.isBlank()) "未识别集数" else episode.split('-').joinToString("-") { "第${it}集" }

/**
 * 播放条目标题里的集号：第12集 / S02E12 / EP06 / 「12集」；
 * 识别不到返回 null（正片、预告等），排序时排到最后，与网页端 episodeNumber 对齐。
 */
private fun episodeNumber(title: String): Int? {
    val value = title.trim()
    if (Regex("预告|花絮|番外|彩蛋|特辑|特别篇|片花|幕后|先导|解说|试看").containsMatchIn(value)) return null
    // 合并集号（01-14）不参与排序，按原顺序排在后面
    if (Regex("(?:S\\d+E\\d+(?:[ ._-]*E|[-~])\\d+)|(?:第\\s*\\d+\\s*[-~至]\\s*\\d+\\s*集)", RegexOption.IGNORE_CASE).containsMatchIn(value)) return null
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

@Composable fun EpisodeForm(sheet: AppSheet.Episode, busy: Boolean, vm: HuluwaViewModel) {
    var season by rememberSaveable(sheet.card.id, sheet.item.url) { mutableStateOf("1") }
    var episode by rememberSaveable(sheet.card.id, sheet.item.url) { mutableStateOf(
        Regex("(?:第\\s*|S\\d+E|EP?)(\\d+)", RegexOption.IGNORE_CASE).find(sheet.item.title)?.groupValues?.get(1)
            ?: sheet.item.title.trim().toIntOrNull()?.toString() ?: if (sheet.card.isMovieHls) "1" else "") }
    var error by remember { mutableStateOf<String?>(null) }
    LabelValue("剧集", sheet.card.title)
    LabelValue("当前资源", "${sheet.source} · ${sheet.item.title}")
    OutlinedTextField(season, { season = it }, Modifier.fillMaxWidth(), label = { Text("季号") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    OutlinedTextField(episode, { episode = it }, Modifier.fillMaxWidth(), label = { Text("集号") }, singleLine = true, enabled = !busy, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    Text("确认季号和集号，下载后可按剧集归档到家庭媒体库。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
    error?.let { Text(it, color = HuluwaColors.Danger) }
    Button({
        val s = season.toIntOrNull(); val e = episode.toIntOrNull()
        if (s == null || e == null || s < 1 || e < 1) error = "请输入大于零的季号和集号"
        else vm.enqueueEpisode(sheet.card, sheet.source, sheet.item, s, e)
    }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("加入离线下载") }
}
@Composable fun ContainerDetail(sheet: AppSheet.ContainerDetail, busy: Boolean, vm: HuluwaViewModel) {
    Feedback(sheet.data.error, { vm.containerDetail(sheet.container) })
    if (!sheet.data.loaded || sheet.data.error != null) return
    val raw = sheet.data.data
    LabelValue("容器名称", sheet.container.name)
    LabelValue("容器编号", sheet.container.id)
    LabelValue("使用镜像", sheet.container.image)
    LabelValue("状态", raw.child("State").text("Status").ifBlank { sheet.container.status })
    raw.child("State").child("Health").text("Status").takeIf { it.isNotBlank() }?.let { health ->
        LabelValue("健康检查", when (health) { "healthy" -> "健康"; "unhealthy" -> "不健康"; "starting" -> "启动中"; else -> health })
    }
    LabelValue("创建时间", raw.text("Created", "created"))
    LabelValue("重启策略", raw.child("HostConfig").child("RestartPolicy").text("Name", "name"))
    raw.rows("Mounts", "mounts").forEach { mount -> mount.asObject().let { LabelValue("存储挂载", "${it.text("Source", "source")}\n→ ${it.text("Destination", "destination")}") } }
    Button({ vm.containerLogs(sheet.container) }, Modifier.fillMaxWidth(), enabled = !busy) { Text("查看运行日志") }
    val containerState = raw.child("State").text("Status").ifBlank { sheet.container.state }.lowercase()
    val canStop = containerState in setOf("running", "paused", "restarting")
    val canStart = containerState in setOf("created", "exited")
    OutlinedButton({ vm.containerAction(sheet.container, "restart") }, Modifier.fillMaxWidth(), enabled = !busy && containerState != "removing") { Text("重启容器") }
    OutlinedButton({ vm.containerAction(sheet.container, if (canStop) "stop" else "start") }, Modifier.fillMaxWidth(),
        enabled = !busy && (canStop || canStart)) { Text(if (canStop) "停止容器" else "启动容器") }
    TextButton({ vm.deleteContainer(sheet.container) }, Modifier.fillMaxWidth(), enabled = !busy && containerState in setOf("created", "exited", "dead")) { Text("删除容器", color = HuluwaColors.Danger) }
}
