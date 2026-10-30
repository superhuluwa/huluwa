package com.huluwa.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huluwa.android.AppSheet
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel
import com.huluwa.android.data.*

@Composable fun BtbtlaResultCard(card: BtbtlaCard, vm: HuluwaViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Poster(card.title, card.cover, size = DpSize(64.dp, 90.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(card.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(card.meta, style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("BT影视 · 高清磁力", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Pine)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton({ vm.openBtbtlaResources(card) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Outlined.FormatListBulleted, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp)); Text("资源列表", style = MaterialTheme.typography.labelLarge)
            }
            OutlinedButton({ vm.openBtbtlaWebsite(card) }, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Icon(Icons.Outlined.OpenInNew, null, Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp)); Text("网站直达", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** 分辨率优先；最新单集在前，分包和全集靠后；复制与下载共用按需获取的磁力。 */
@Composable fun BtbtlaResources(sheet: AppSheet.Btbtla, ui: AppState, vm: HuluwaViewModel) {
    val context = LocalContext.current
    val groups = remember(sheet.detail.data) { BtbtlaQuality.rows(sheet.detail.data) }
    var selectedResolution by rememberSaveable(sheet.card.id) { mutableStateOf<Int?>(null) }
    val selected = groups.firstOrNull { it.resolution == selectedResolution } ?: groups.firstOrNull()
    val list = rememberLazyListState()
    LaunchedEffect(selected?.resolution) { list.scrollToItem(0) }
    Dialog(onDismissRequest = vm::dismissSheet, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth(.94f).widthIn(max = 620.dp)
            .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .88f),
            color = Color.White, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, HuluwaColors.Line)) {
            Column {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(sheet.card.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val total = sheet.detail.data.number("totalEpisodes")?.toInt() ?: 0
                        Text("BT影视 · ${groups.sumOf { it.items.size }} 个版本" + if (total > 0) " · 全剧 $total 集" else "",
                            style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                    }
                    IconButton({ vm.reloadBtbtlaResources(sheet.card) }, enabled = !sheet.detail.loading) { Icon(Icons.Outlined.Refresh, "刷新资源") }
                    IconButton(vm::dismissSheet) { Icon(Icons.Outlined.Close, "关闭") }
                }
                HorizontalDivider(color = HuluwaColors.Line)
                if (sheet.detail.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Feedback(sheet.detail.error, { vm.reloadBtbtlaResources(sheet.card) })
                    ui.actionError?.let { Text(it, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
                    if (groups.isNotEmpty()) {
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            groups.forEach { group ->
                                FilterChip(selected = group.resolution == selected?.resolution,
                                    onClick = { selectedResolution = group.resolution },
                                    label = { Text("${group.quality} · ${group.items.size}") })
                            }
                        }
                        Text(selected?.coverage.orEmpty(), style = MaterialTheme.typography.labelMedium,
                            color = HuluwaColors.Pine, modifier = Modifier.padding(bottom = 8.dp))
                    }
                }
                if (sheet.detail.loaded && !sheet.detail.loading && groups.isEmpty() && sheet.detail.error == null) {
                    Text("该作品暂无可用磁力资源", Modifier.padding(20.dp), color = HuluwaColors.Muted)
                }
                if (selected != null) {
                    LazyColumn(Modifier.weight(1f, fill = false), state = list,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selected.items.groupBy { it.section }.forEach { (episode, resources) ->
                            item(key = "section:$episode") {
                                Text(episode, style = MaterialTheme.typography.titleSmall, color = HuluwaColors.Ink,
                                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp))
                            }
                            items(resources, key = { it.id }) { resource ->
                                val copying = ui.busy == "btbtla-magnet:${resource.id}"
                                val downloading = ui.busy == "btbtla-download:${resource.id}"
                                val downloaded = resource.id in sheet.downloaded
                                Surface(shape = RoundedCornerShape(12.dp), color = if (downloaded) HuluwaColors.SoftGreen else HuluwaColors.Paper,
                                    border = BorderStroke(1.dp, HuluwaColors.Line)) {
                                    Column(Modifier.padding(11.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Text((resource.tags + resource.size).filter(String::isNotBlank).joinToString(" · ").ifBlank { "普通版" },
                                            style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Pine)
                                        SelectionContainer { Text(resource.title, style = MaterialTheme.typography.bodySmall, color = HuluwaColors.Muted) }
                                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)) {
                                            TextButton({
                                                vm.copyBtbtlaMagnet(sheet.card, resource) { magnet ->
                                                    context.getSystemService(ClipboardManager::class.java)
                                                        ?.setPrimaryClip(ClipData.newPlainText("磁力链接", magnet))
                                                }
                                            }, enabled = ui.busy == null, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                                if (copying) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                                else Icon(Icons.Outlined.ContentCopy, null, Modifier.size(14.dp))
                                                Spacer(Modifier.width(4.dp)); Text(if (copying) "获取中" else "复制磁力", style = MaterialTheme.typography.labelMedium)
                                            }
                                            TextButton({ vm.downloadBtbtlaMagnet(sheet.card, resource, selected.resolution) },
                                                enabled = ui.busy == null && !downloaded, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                                when {
                                                    downloading -> CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
                                                    downloaded -> Icon(Icons.Outlined.Check, null, Modifier.size(14.dp))
                                                    else -> Icon(Icons.Outlined.Download, null, Modifier.size(14.dp))
                                                }
                                                Spacer(Modifier.width(4.dp))
                                                Text(when { downloading -> "提交中"; downloaded -> "已提交"; else -> "下载" }, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                        sheet.magnets[resource.id]?.let { magnet ->
                                            SelectionContainer { Text(magnet, style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Pine) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(color = HuluwaColors.Line)
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("最新单集优先 · 全集合集靠后", style = MaterialTheme.typography.labelMedium, color = HuluwaColors.Muted)
                        Text("下载任务可在「下载」查看", style = MaterialTheme.typography.labelSmall, color = HuluwaColors.Muted)
                    }
                    TextButton(vm::dismissSheet) { Text("关闭") }
                }
            }
        }
    }
}
