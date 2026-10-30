package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.huluwa.android.*
import com.huluwa.android.data.isServerUnreachable
import com.huluwa.android.data.text

@Composable fun AppSheets(ui: AppState, vm: HuluwaViewModel, snackbar: SnackbarHostState) {
    val sheet = ui.sheet ?: return
    if (sheet is AppSheet.Confirm) {
        AlertDialog(onDismissRequest = vm::dismissSheet, title = { Text(sheet.title) }, text = { Text(sheet.description) },
            confirmButton = { TextButton(vm::confirmSheet, enabled = ui.busy == null) { Text(sheet.positive) } },
            dismissButton = { TextButton(vm::dismissSheet) { Text("再想想") } })
        return
    }
    var media by remember { mutableStateOf<String?>(null) }
    // 资源列表（对齐网页端 n-modal）：独立居中弹框，不再套底部弹层
    if (sheet is AppSheet.Films) {
        FilmResources(sheet, ui.busy != null, ui.actionError, vm, onPlay = { media = it })
        media?.let { url ->
            if (sheet.card.isMovie) MovieVideoViewer(url, sheet.card.title,
                sheet.onlineData.data.text("url", fallback = sheet.card.url)) { media = null }
            else MediaViewer(url, true) { media = null }
        }
        return
    }
    // 懂片帝资源弹框：独立于电影港 / 电影天堂的 Films 弹层
    if (sheet is AppSheet.Dpd) {
        DpdSourceDialog(sheet, ui.busy != null, ui.actionError, vm, snackbar) { media = it }
        media?.let { url -> MediaViewer(url, true) { media = null } }
        return
    }
    val title = when (sheet) {
        AppSheet.Account -> "连接配置"
        AppSheet.NewDownload -> "新建下载"
        is AppSheet.TunnelEdit -> if (sheet.tunnel == null) "新增隧道" else "编辑隧道"
        is AppSheet.Settings -> settingsTitle(sheet.kind)
        is AppSheet.Episode -> "离线下载"
        is AppSheet.Text -> sheet.title
        is AppSheet.ContainerDetail -> sheet.container.name
        is AppSheet.CreateResource -> if (sheet.section == com.huluwa.android.data.DockerSection.Images) "拉取镜像" else "新增${sheet.section.title}"
        else -> "操作"
    }
    HuluwaBottomSheet(title = title, onDismissRequest = vm::dismissSheet,
        titleExtra = { TitleReconnectBadge(rememberReconnecting(vm)) }) {
        HuluwaSheetForm {
            ui.actionError?.takeUnless { it.isServerUnreachable() }?.let { Text(it, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
            when (sheet) {
                AppSheet.Account -> AccountSheet(ui, vm)
                AppSheet.NewDownload -> NewDownloadSheet(ui.busy != null, vm)
                is AppSheet.TunnelEdit -> TunnelEditor(sheet.tunnel, ui.busy != null || rememberReconnecting(vm) || ui.tunnels.error != null, vm)
                is AppSheet.Settings -> ServiceSettings(sheet, ui.busy != null, vm)
                is AppSheet.Episode -> EpisodeForm(sheet, ui.busy != null, vm)
                is AppSheet.Text -> TextSheet(sheet, ui.busy != null || rememberReconnecting(vm))
                is AppSheet.ContainerDetail -> ContainerDetail(sheet, ui.busy != null || rememberReconnecting(vm) || ui.containers.error != null || !ui.overview.online, vm)
                is AppSheet.CreateResource -> CreateResource(sheet, ui.busy != null || rememberReconnecting(vm) || ui.containers.error != null || !ui.overview.online, vm)
                else -> Unit
            }
        }
    }
    media?.let { MediaViewer(it, true) { media = null } }
}
@Composable private fun TextSheet(sheet: AppSheet.Text, busy: Boolean) {
    var edited by remember(sheet.data.data) { mutableStateOf(sheet.data.data) }
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
