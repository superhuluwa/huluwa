package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.huluwa.android.DengtaState
import com.huluwa.android.DengtaViewModel
import com.huluwa.android.data.*
import kotlinx.coroutines.launch

@Composable internal fun DengtaDetailContentDialog(
    kind: String, ui: DengtaState, vm: DengtaViewModel, onDismiss: () -> Unit,
    onImages: (List<String>, Int, String) -> Unit, onLink: (String) -> Unit,
    onMaterial: (DengtaMaterial, List<DengtaMaterial>) -> Unit,
) {
    val detail = ui.detail.data ?: return
    LaunchedEffect(kind, detail.media.id) {
        when (kind) { "marketing" -> vm.loadMarketing(); "materials" -> vm.loadMaterials(); "highlights" -> vm.loadHighlights() }
    }
    val title = when (kind) { "marketing" -> "营销事件"; "materials" -> "物料"; "people" -> "演职人员";
        "companies" -> "出品发行"; "highlights" -> "高光时刻"; else -> "播放平台与更新规则" }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.White).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭$title", tint = DengtaDetailColors.Ink) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = DengtaDetailColors.Ink, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text(detail.media.name, color = DengtaDetailColors.Muted, fontSize = 11.sp)
                }
            }
            HorizontalDivider(color = DengtaDetailColors.Line)
            when (kind) {
                "marketing" -> {
                    DengtaContentFilters(ui.marketing.data.options, ui.marketingType) { vm.loadMarketing(it) }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                        item {
                            if (ui.marketing.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 16.dp), color = DengtaDetailColors.Orange)
                            ui.marketing.error?.let { StatusNotice(it, onRetry = { vm.loadMarketing(force = true) }) }
                            if (!ui.marketing.loading || ui.marketing.data.events.isNotEmpty())
                                DengtaMarketingTimeline(ui.marketing.data.events, true, onImages, onLink)
                        }
                    }
                }
                "materials" -> {
                    val data = ui.materials.data
                    if (data.tabs.size > 1) DengtaContentFilters(data.tabs.map { DengtaContentOption(it, if (it == "VIDEO") "视频" else "图片") }, ui.materialTab) {
                        vm.loadMaterials(tab = it, category = "all")
                    }
                    DengtaContentFilters(data.options, ui.materialCategory) { vm.loadMaterials(category = it) }
                    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item {
                            if (ui.materials.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DengtaDetailColors.Orange)
                            ui.materials.error?.let { StatusNotice(it, onRetry = { vm.loadMaterials(force = true) }) }
                            if (ui.materials.loaded && data.items.isEmpty()) DengtaEmpty("该分类暂无物料")
                        }
                        val columns = if (ui.materialTab == "VIDEO") 2 else 3
                        items(data.items.chunked(columns)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                                row.forEach { material ->
                                    DengtaMaterialThumbnail(material, Modifier.weight(1f).aspectRatio(if (material.type == "VIDEO") 1.4f else .68f)) {
                                        onMaterial(material, data.items)
                                    }
                                }
                                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                "people" -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    if (detail.information.people.isEmpty()) item { DengtaEmpty("暂无演职人员资料") }
                    detail.information.people.groupBy { it.type }.forEach { (type, people) ->
                        item { Text(when (type) { "director" -> "导演"; "actor" -> "演员"; "writer", "screenwriter" -> "编剧"; else -> "其他主创" },
                            color = DengtaDetailColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        items(people.chunked(3)) { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                row.forEach { person -> DengtaPersonCard(person, Modifier.weight(1f), onImages) }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
                "companies" -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                    if (detail.information.companyGroups.isEmpty()) {
                        if (detail.information.companies.isEmpty()) item { DengtaEmpty("暂无出品发行资料") }
                        items(detail.information.companies) { DengtaCompanyRow(it) }
                    } else detail.information.companyGroups.forEach { group ->
                        item { Text(group.name, Modifier.padding(top = 12.dp, bottom = 6.dp), color = DengtaDetailColors.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
                        items(group.companies) { DengtaCompanyRow(it) }
                    }
                }
                "highlights" -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    val data = ui.highlights.data
                    val rows = if (ui.highlights.loaded) data.items else detail.highlights
                    items(rows) { highlight ->
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(dengtaHighlightTime(highlight.timestamp), color = DengtaDetailColors.Muted, fontSize = 12.sp)
                            Text(dengtaPlainText(highlight.html), color = DengtaDetailColors.Ink, fontSize = 14.sp, lineHeight = 23.sp)
                            HorizontalDivider(color = DengtaDetailColors.Line)
                        }
                    }
                    item {
                        if (ui.highlights.loading) LinearProgressIndicator(Modifier.fillMaxWidth(), color = DengtaDetailColors.Orange)
                        ui.highlights.error?.let { StatusNotice(it, onRetry = { vm.loadHighlights(more = ui.highlights.loaded) }) }
                        if (!ui.highlights.loading && ui.highlights.loaded) {
                            if (rows.isEmpty()) DengtaEmpty("暂无高光动态")
                            if (data.nextTime.isNotBlank()) TextButton({ vm.loadHighlights(more = true) }, Modifier.fillMaxWidth()) {
                                Text("加载更多动态", color = DengtaDetailColors.Orange)
                            }
                        }
                    }
                }
                else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
                    item {
                        Text(detail.media.platforms.ifBlank { "播出平台待公布" }, color = DengtaDetailColors.Ink, fontSize = 17.sp)
                        Spacer(Modifier.height(16.dp))
                        DengtaRules(ui, vm)
                    }
                }
            }
        }
    }
}

@Composable private fun DengtaContentFilters(options: List<DengtaContentOption>, selected: String, onSelect: (String) -> Unit) {
    if (options.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options) { option ->
            FilterChip(selected = selected == option.code, onClick = { onSelect(option.code) }, label = { Text(option.name, fontSize = 12.sp) },
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color(0xFFFFEEE8), selectedLabelColor = DengtaDetailColors.Orange))
        }
    }
}

@Composable internal fun DengtaDetailImageGallery(urls: List<String>, initialIndex: Int, title: String, onDismiss: () -> Unit) {
    if (urls.isEmpty()) return
    val pager = rememberPagerState(initialPage = initialIndex.coerceIn(0, urls.lastIndex), pageCount = { urls.size })
    val scope = rememberCoroutineScope()
    var zoomed by remember { mutableStateOf(false) }
    Dialog(onDismiss, DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onDismiss) { Icon(Icons.Outlined.Close, "关闭图片预览", tint = Color.White) }
                Text(title, Modifier.weight(1f), color = Color.White, fontSize = 15.sp, maxLines = 2)
                Text("${pager.currentPage + 1}/${urls.size}", Modifier.padding(horizontal = 12.dp), color = Color.White, fontSize = 12.sp)
            }
            HorizontalPager(pager, Modifier.weight(1f).fillMaxWidth(), userScrollEnabled = !zoomed) { page ->
                DengtaZoomableDetailImage(urls[page], title, page == pager.currentPage) { zoomed = it }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0) {
                    Icon(Icons.Outlined.ChevronLeft, "上一张", tint = if (pager.currentPage > 0) Color.White else Color.DarkGray)
                }
                Text("左右切换 · 双指缩放 · 拖动查看", color = Color.LightGray, fontSize = 11.sp)
                IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < urls.lastIndex) {
                    Icon(Icons.Outlined.ChevronRight, "下一张", tint = if (pager.currentPage < urls.lastIndex) Color.White else Color.DarkGray)
                }
            }
        }
    }
}

@Composable private fun DengtaZoomableDetailImage(url: String, title: String, active: Boolean, onZoom: (Boolean) -> Unit) {
    var scale by remember(url) { mutableFloatStateOf(1f) }
    var offset by remember(url) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var image by remember(url) { mutableStateOf(Size.Zero) }
    var failed by remember(url) { mutableStateOf(false) }
    var loading by remember(url) { mutableStateOf(true) }
    var retry by remember(url) { mutableIntStateOf(0) }
    LaunchedEffect(active) { scale = 1f; offset = Offset.Zero; if (active) onZoom(false) }
    val transform = rememberTransformableState { zoom, pan, _ ->
        val next = (scale * zoom).coerceIn(1f, 5f)
        val fit = if (image.width > 0 && image.height > 0) minOf(viewport.width / image.width, viewport.height / image.height) else 1f
        val maxX = ((image.width * fit * next - viewport.width) / 2).coerceAtLeast(0f)
        val maxY = ((image.height * fit * next - viewport.height) / 2).coerceAtLeast(0f)
        val moved = offset * (next / scale) + pan
        scale = next
        offset = Offset(moved.x.coerceIn(-maxX, maxX), moved.y.coerceIn(-maxY, maxY))
        if (active) onZoom(next > 1f)
    }
    Box(Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
        .transformable(transform, canPan = { scale > 1f }), contentAlignment = Alignment.Center) {
        key(retry) {
            AsyncImage(url, title, Modifier.fillMaxSize().graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
                contentScale = ContentScale.Fit, onSuccess = {
                    val size = it.painter.intrinsicSize
                    if (size.width.isFinite() && size.height.isFinite() && size.width > 0 && size.height > 0) image = size
                    loading = false; failed = false
                }, onError = { failed = true; loading = false })
        }
        if (loading) CircularProgressIndicator(Modifier.size(28.dp), color = Color.White)
        if (failed) Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("图片暂时无法加载", color = Color.White)
            TextButton({ retry++; failed = false; loading = true }) { Text("重新加载", color = Color.White) }
        }
    }
}
