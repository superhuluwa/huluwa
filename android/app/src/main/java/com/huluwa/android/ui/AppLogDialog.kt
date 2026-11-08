package com.huluwa.android.ui

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.huluwa.android.data.AppLog
import com.huluwa.android.data.CrashLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 闪退后可从「我的」查看并复制堆栈，也包含当前进程的运行日志。 */
@Composable
fun AppLogDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val currentLog = remember { AppLog.dump().takeLast(32_000) }
    var report by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(context) {
        val saved = withContext(Dispatchers.IO) { CrashLog.report(context) }
        report = "$saved\n\n===== 本次运行日志 =====\n$currentLog"
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(.94f)
                .heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .8f),
            shape = RoundedCornerShape(20.dp),
            color = HuluwaColors.Paper,
            border = BorderStroke(1.dp, HuluwaColors.Line),
        ) {
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("运行日志", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    TextButton(enabled = report != null, onClick = {
                        report?.let { text ->
                            context.getSystemService(ClipboardManager::class.java)
                                ?.setPrimaryClip(ClipData.newPlainText("葫芦娃运行日志", text))
                        }
                    }) { Text("复制") }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Outlined.Close, "关闭", Modifier.size(20.dp))
                    }
                }
                HorizontalDivider(color = HuluwaColors.Line)
                HuluwaDialogList(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                    item {
                        SelectionContainer {
                            Text(
                                report ?: "正在读取日志…",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                color = HuluwaColors.Ink,
                            )
                        }
                    }
                }
            }
        }
    }
}
