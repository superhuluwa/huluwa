@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** 与 TerminalBuddy 的 AiSheet 保持相同结构：外层限高、标题固定，正文独立滚动。 */
@Composable internal fun HuluwaBottomSheet(
    title: String,
    onDismissRequest: () -> Unit,
    subtitle: String? = null,
    titleExtra: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .8f
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HuluwaColors.Paper,
    ) {
        // 限高留在内容外层；键盘避让由 ModalBottomSheet 统一处理。
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight)) {
            Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                        Text(title, Modifier.weight(1f, fill = false), style = MaterialTheme.typography.titleLarge,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        titleExtra()
                    }
                    subtitle?.let { Text(it, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall) }
                }
                IconButton(onDismissRequest) { Icon(Icons.Outlined.Close, "关闭$title") }
            }
            HorizontalDivider(color = HuluwaColors.Line)
            content()
        }
    }
}

/** 正文只占标题之外的剩余高度，不让表单滚动参与弹层外框的尺寸计算。 */
@Composable internal fun ColumnScope.HuluwaSheetForm(content: @Composable ColumnScope.() -> Unit) {
    HuluwaDialogList(
        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 13.dp, bottom = 22.dp),
    ) {
        item {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(13.dp), content = content)
        }
    }
}
