@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.huluwa.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp

@Composable internal fun SkinPickerSheet(onDismiss: () -> Unit, onSkinSelected: (AppSkin) -> Unit) {
    val selected = HuluwaColors.skin
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .78f
    val largeFont = LocalDensity.current.fontScale >= 1.3f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HuluwaColors.Paper,
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val columns = if (maxWidth < 360.dp || largeFont) 1 else 2
            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = maxHeight).selectableGroup(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("选择你的配色", color = HuluwaColors.Ink, style = MaterialTheme.typography.titleLarge)
                            Text("当前使用 · ${selected.title}", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                        }
                        IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "关闭皮肤选择") }
                    }
                }
                item {
                    SkinOption(AppSkin.Pine, selected == AppSkin.Pine, { onSkinSelected(AppSkin.Pine) }, compact = true)
                }
                item {
                    Text("弥散灵感", color = HuluwaColors.Ink, style = MaterialTheme.typography.titleMedium)
                }
                items(AppSkin.entries.filterNot { it == AppSkin.Pine }.chunked(columns), key = { it.first().id }) { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { skin ->
                            SkinOption(skin, selected == skin, { onSkinSelected(skin) }, Modifier.weight(1f))
                        }
                    }
                }
                item {
                    Text("点击即刻应用，自动记住你的选择。", color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable private fun SkinOption(
    skin: AppSkin, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, compact: Boolean = false,
) {
    val palette = skin.palette
    val shape = RoundedCornerShape(20.dp)
    val option = modifier.fillMaxWidth().clip(shape).background(Color.White)
        .border(BorderStroke(if (selected) 2.dp else 1.dp, if (selected) palette.primary else HuluwaColors.Line), shape)
        .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
        .semantics(mergeDescendants = true) { stateDescription = if (selected) "正在使用" else "未选择" }
        .padding(12.dp)
    if (compact) {
        Row(option, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SkinPreview(skin, Modifier.size(width = 84.dp, height = 76.dp))
            SkinLabel(skin, selected, Modifier.weight(1f))
        }
    } else {
        Column(option, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SkinPreview(skin, Modifier.fillMaxWidth().height(100.dp))
            SkinLabel(skin, selected)
        }
    }
}

@Composable private fun SkinLabel(skin: AppSkin, selected: Boolean, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(skin.title, Modifier.weight(1f), color = skin.palette.ink, style = MaterialTheme.typography.titleMedium)
            if (selected) Icon(Icons.Outlined.CheckCircle, null, Modifier.size(20.dp), tint = skin.palette.primary)
        }
        Text(skin.description, color = skin.palette.muted, style = MaterialTheme.typography.labelMedium)
    }
}

/** 用原生渐变绘制微型界面预览，无需下载图片或额外依赖。 */
@Composable private fun SkinPreview(skin: AppSkin, modifier: Modifier = Modifier) {
    val palette = skin.palette
    val colors = palette.previewColors
    Box(
        modifier.clearAndSetSemantics { }.clip(RoundedCornerShape(12.dp)).background(palette.background).drawWithCache {
            val base = Brush.linearGradient(colors.map { it.copy(alpha = .65f) })
            val glow = Brush.radialGradient(
                colors = listOf(colors[2].copy(alpha = .9f), Color.Transparent),
                center = Offset(size.width * .9f, size.height * .15f),
                radius = size.maxDimension.coerceAtLeast(1f) * .85f,
            )
            onDrawBehind {
                drawRect(base)
                drawRect(glow)
            }
        }.padding(10.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Icon(Icons.Outlined.Palette, null, Modifier.size(16.dp), tint = palette.primaryDark)
                Box(Modifier.width(28.dp).height(5.dp).clip(CircleShape).background(palette.primaryDark.copy(alpha = .55f)))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                colors.forEach { color ->
                    Box(Modifier.size(17.dp).clip(CircleShape).background(color).border(1.dp, Color.White.copy(alpha = .85f), CircleShape))
                }
            }
        }
        Box(Modifier.align(Alignment.CenterEnd).fillMaxWidth(.55f).height(24.dp).clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = .65f)).padding(horizontal = 8.dp), contentAlignment = Alignment.CenterStart) {
            Box(Modifier.fillMaxWidth(.7f).height(4.dp).clip(CircleShape).background(palette.primary.copy(alpha = .65f)))
        }
    }
}
