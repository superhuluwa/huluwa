package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.*
import kotlinx.serialization.json.*

@Composable fun FilmResources(sheet: AppSheet.Films, busy: Boolean, vm: HuluwaViewModel, onPlay: (String) -> Unit) {
    val card = sheet.card
    Feedback(sheet.data.loading, sheet.data.error, { vm.filmResources(card) })
    if (!sheet.data.loaded || sheet.data.error != null) return
    if (card.isHls) {
        val sources = sheet.data.data.rows("playSources").map { PlaySource.from(it.asObject()) }
        var selected by rememberSaveable(card.id) { mutableIntStateOf(0) }
        val source = sources.getOrNull(selected) ?: sources.firstOrNull()
        var limit by remember(source?.name) { mutableIntStateOf(30) }
        if (sources.isEmpty()) { Text("暂时没有可用的播放线路", color = HuluwaColors.Muted); return }
        val currentSource = source ?: return
        FilterRow(sources.map { it.name }, selected.coerceIn(sources.indices), { selected = it })
        Text("选择剧集播放，或下载到家庭服务器。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
        currentSource.items.asReversed().take(limit).forEach { item ->
            Panel(Modifier.fillMaxWidth()) {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ onPlay(item.url) }, enabled = item.url.startsWith("http://") || item.url.startsWith("https://")) { Icon(Icons.Outlined.PlayCircleOutline, null, Modifier.size(17.dp)); Spacer(Modifier.width(4.dp)); Text("播放") }
                    TextButton({ vm.showSheet(AppSheet.Episode(card, currentSource.name, item)) }, enabled = !busy) { Text("离线下载") }
                }
            }
        }
        if (currentSource.items.size > limit) OutlinedButton({ limit += 30 }, Modifier.fillMaxWidth()) { Text("显示更多剧集") }
    } else {
        val magnets = sheet.data.data.rows("magnets").map { it.asObject() }
        var limit by remember(card.url) { mutableIntStateOf(30) }
        if (magnets.isEmpty()) Text("暂时没有采集到磁力资源", color = HuluwaColors.Muted)
        magnets.take(limit).forEach { item ->
            Panel(Modifier.fillMaxWidth()) {
                Text(item.text("title", fallback = card.title), style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(7.dp))
                Text(listOf(item.text("episode"), item.text("quality")).filter(String::isNotBlank).joinToString(" · "), color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                TextButton({ vm.confirm("下载资源", "将「${card.title} ${item.text("episode")}" + "」提交到服务器默认下载器。", "下载") { vm.enqueueMagnet(card, item) } },
                    modifier = Modifier.align(Alignment.End), enabled = !busy && !item.flag("downloaded") && item.text("magnet").isNotBlank()) { Text(if (item.flag("downloaded")) "已提交下载" else "下载到 NAS") }
            }
        }
        if (magnets.size > limit) OutlinedButton({ limit += 30 }, Modifier.fillMaxWidth()) { Text("显示更多资源") }
    }
}
@Composable fun EpisodeForm(sheet: AppSheet.Episode, busy: Boolean, vm: HuluwaViewModel) {
    var season by rememberSaveable(sheet.card.id, sheet.item.url) { mutableStateOf("1") }
    var episode by rememberSaveable(sheet.card.id, sheet.item.url) { mutableStateOf(
        Regex("(?:第\\s*|S\\d+E|EP?)(\\d+)", RegexOption.IGNORE_CASE).find(sheet.item.title)?.groupValues?.get(1)
            ?: sheet.item.title.trim().toIntOrNull()?.toString().orEmpty()) }
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
    Feedback(sheet.data.loading, sheet.data.error, { vm.containerDetail(sheet.container) })
    if (!sheet.data.loaded || sheet.data.error != null) return
    val raw = sheet.data.data
    LabelValue("容器名称", sheet.container.name)
    LabelValue("容器编号", sheet.container.id)
    LabelValue("使用镜像", sheet.container.image)
    LabelValue("状态", sheet.container.status)
    LabelValue("创建时间", raw.text("Created", "created"))
    LabelValue("重启策略", raw.child("HostConfig").child("RestartPolicy").text("Name", "name"))
    raw.rows("Mounts", "mounts").forEach { mount -> mount.asObject().let { LabelValue("存储挂载", "${it.text("Source", "source")}\n→ ${it.text("Destination", "destination")}") } }
    Button({ vm.containerLogs(sheet.container) }, Modifier.fillMaxWidth(), enabled = !busy) { Text("查看运行日志") }
    OutlinedButton({ vm.containerAction(sheet.container, if (sheet.container.running) "stop" else "start") }, Modifier.fillMaxWidth(), enabled = !busy) { Text(if (sheet.container.running) "停止容器" else "启动容器") }
    TextButton({ vm.deleteContainer(sheet.container) }, Modifier.fillMaxWidth(), enabled = !busy && !sheet.container.running) { Text("删除容器", color = HuluwaColors.Danger) }
}
