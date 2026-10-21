@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.*

@Composable fun AppSheets(ui: AppState, vm: HuluwaViewModel) {
    val sheet = ui.sheet ?: return
    if (sheet is AppSheet.Confirm) {
        AlertDialog(onDismissRequest = vm::dismissSheet, title = { Text(sheet.title) }, text = { Text(sheet.description) },
            confirmButton = { TextButton(vm::confirmSheet, enabled = ui.busy == null) { Text(sheet.positive) } },
            dismissButton = { TextButton(vm::dismissSheet) { Text("再想想") } })
        return
    }
    var media by remember { mutableStateOf<String?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = vm::dismissSheet, sheetState = state, containerColor = HuluwaColors.Paper) {
        val maxHeight = LocalConfiguration.current.screenHeightDp.dp * .86f
        Column(Modifier.fillMaxWidth().heightIn(max = maxHeight).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val title = when (sheet) {
                    AppSheet.Account -> "连接配置"
                    AppSheet.NewDownload -> "新建下载"
                    is AppSheet.TunnelEdit -> if (sheet.tunnel == null) "新增隧道" else "编辑隧道"
                    is AppSheet.Settings -> settingsTitle(sheet.kind)
                    is AppSheet.Films -> sheet.card.title
                    is AppSheet.Episode -> "离线下载"
                    is AppSheet.Text -> sheet.title
                    is AppSheet.ContainerDetail -> sheet.container.name
                    is AppSheet.CreateResource -> if (sheet.section == com.huluwa.android.data.DockerSection.Images) "拉取镜像" else "新增${sheet.section.title}"
                    else -> "操作"
                }
                // 弹层标题右侧同样显示「重连中」，与各页面标题行保持一致。
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    TitleReconnectBadge(rememberReconnecting(vm))
                }
                IconButton(vm::dismissSheet) { Icon(Icons.Outlined.Close, "关闭弹层") }
            }
            if (ui.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            ui.actionError?.takeUnless { it.isServerUnreachable() }?.let { Text(it, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
            when (sheet) {
                AppSheet.Account -> AccountSheet(ui, vm)
                AppSheet.NewDownload -> NewDownloadSheet(ui.busy != null, vm)
                is AppSheet.TunnelEdit -> TunnelEditor(sheet.tunnel, ui.busy != null, vm)
                is AppSheet.Settings -> ServiceSettings(sheet, ui.busy != null, vm)
                is AppSheet.Films -> FilmResources(sheet, ui.busy != null, vm, onPlay = { media = it })
                is AppSheet.Episode -> EpisodeForm(sheet, ui.busy != null, vm)
                is AppSheet.Text -> TextSheet(sheet, ui.busy != null)
                is AppSheet.ContainerDetail -> ContainerDetail(sheet, ui.busy != null, vm)
                is AppSheet.CreateResource -> CreateResource(sheet, ui.busy != null, vm)
                else -> Unit
            }
            Spacer(Modifier.height(22.dp))
        }
    }
    media?.let { MediaViewer(it, true) { media = null } }
}
@Composable private fun TextSheet(sheet: AppSheet.Text, busy: Boolean) {
    var edited by remember(sheet.data.data) { mutableStateOf(sheet.data.data) }
    if (sheet.data.loading) CircularProgressIndicator(Modifier.size(26.dp))
    sheet.data.error?.let { Text(it, color = HuluwaColors.Danger) }
    if (sheet.data.loaded && sheet.data.error == null) {
        if (sheet.onSave != null) {
            OutlinedTextField(edited, { edited = it }, Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 430.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), enabled = !busy, label = { Text("配置内容") })
            Button({ sheet.onSave.invoke(edited) }, Modifier.fillMaxWidth().heightIn(min = 48.dp), enabled = !busy) { Text("保存并热加载") }
        } else SelectionContainer {
            Text(sheet.data.data.ifBlank { "暂无内容" }, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), modifier = Modifier.fillMaxWidth())
        }
    }
}
