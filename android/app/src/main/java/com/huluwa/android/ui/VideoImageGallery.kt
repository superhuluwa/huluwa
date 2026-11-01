package com.huluwa.android.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.huluwa.android.data.ParsedImage
import kotlinx.coroutines.launch

/** 同一作品的图片与实况照片共用一个分页区域，切页立即停止视频播放。 */
@Composable internal fun VideoImageGallery(
    images: List<ParsedImage>, playingVideo: Boolean, onVideoPlay: () -> Unit, onVideoStop: () -> Unit,
    savingImageUrls: Set<String>, onSaveImage: (String) -> Unit, onOpenImage: (String) -> Unit,
) {
    val pager = rememberPagerState(pageCount = { images.size })
    val scope = rememberCoroutineScope()
    LaunchedEffect(pager.targetPage, pager.isScrollInProgress) {
        if (pager.isScrollInProgress) onVideoStop()
    }
    Column(Modifier.width(240.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HorizontalPager(pager, Modifier.fillMaxWidth().aspectRatio(3f / 4f).clip(RoundedCornerShape(10.dp)).background(HuluwaColors.Paper)) { page ->
            val image = images[page]
            if (image.livePhotoUrl.isNotBlank()) {
                InlineVideoBubble(image.livePhotoUrl, image.url,
                    playingVideo && page == pager.currentPage && !pager.isScrollInProgress,
                    onVideoPlay, onVideoStop, modifier = Modifier.fillMaxSize())
            } else {
                var failed by remember(image.url) { mutableStateOf(false) }
                Box(Modifier.fillMaxSize().clickable(role = Role.Button, onClickLabel = "查看第 ${page + 1} 张大图") { onOpenImage(image.url) }, contentAlignment = Alignment.Center) {
                    AsyncImage(image.url, "第 ${page + 1} 张图片", Modifier.fillMaxSize(), contentScale = ContentScale.Fit,
                        onError = { failed = true }, onSuccess = { failed = false })
                    if (failed) Text("图片加载失败", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        if (images.size > 1) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage - 1) } }, enabled = pager.currentPage > 0 && !pager.isScrollInProgress,
                modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ChevronLeft, "上一张", Modifier.size(20.dp), tint = if (pager.currentPage > 0) HuluwaColors.Pine else HuluwaColors.Line)
            }
            Canvas(Modifier.weight(1f).height(4.dp)) {
                val radius = CornerRadius(size.height / 2)
                val segmentWidth = size.width / images.size
                drawRoundRect(HuluwaColors.Line, cornerRadius = radius)
                drawRoundRect(HuluwaColors.Pine,
                    topLeft = Offset(segmentWidth * pager.currentPage, 0f), size = Size(segmentWidth, size.height), cornerRadius = radius)
            }
            Text("${pager.currentPage + 1} / ${images.size}", color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium)
            IconButton({ scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } }, enabled = pager.currentPage < images.lastIndex && !pager.isScrollInProgress,
                modifier = Modifier.size(36.dp)) {
                Icon(Icons.Outlined.ChevronRight, "下一张", Modifier.size(20.dp), tint = if (pager.currentPage < images.lastIndex) HuluwaColors.Pine else HuluwaColors.Line)
            }
        }
        images.getOrNull(pager.currentPage)?.let { image ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                SaveOriginalImageButton(image.url in savingImageUrls, { onSaveImage(image.url) }, enabled = !pager.isScrollInProgress)
            }
        }
    }
}

@Composable internal fun SaveOriginalImageButton(saving: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    TextButton(onClick, enabled = enabled && !saving, colors = ButtonDefaults.textButtonColors(contentColor = HuluwaColors.Pine)) {
        if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 1.5.dp)
        else Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
        Spacer(Modifier.width(5.dp))
        Text(if (saving) "保存中…" else "保存原图", style = MaterialTheme.typography.labelMedium)
    }
}
