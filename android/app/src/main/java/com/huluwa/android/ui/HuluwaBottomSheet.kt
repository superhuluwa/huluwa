@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.huluwa.android.ui

import androidx.compose.foundation.LocalOverscrollFactory
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** 统一关闭弹层及内部滚动区域的边缘回弹，避免触底时与弹层拖动叠加造成抖动。 */
@Composable internal fun HuluwaBottomSheet(
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    CompositionLocalProvider(LocalOverscrollFactory provides null) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = HuluwaColors.Paper,
            content = content,
        )
    }
}
