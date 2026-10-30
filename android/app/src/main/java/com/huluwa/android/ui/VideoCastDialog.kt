package com.huluwa.android.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cast
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.huluwa.android.VideoCastState
import com.huluwa.android.VideoCastViewModel
import com.huluwa.android.data.BrowserVideo

@Composable fun VideoCastDialog(media: BrowserVideo?, state: VideoCastState, vm: VideoCastViewModel,
    onStarted: () -> Unit, dismiss: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, icon = { Icon(Icons.Outlined.Cast, null) },
        title = { Text("投屏到电视") },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(max = LocalConfiguration.current.screenHeightDp.dp * .6f)) {
                HuluwaDialogList(contentPadding = PaddingValues(0.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item(key = "description") {
                        Text("手机与电视连接同一 Wi-Fi，电视需开启 DLNA 投屏并支持 HLS 视频。", style = MaterialTheme.typography.bodySmall)
                    }
                    state.selected?.let { device ->
                        item(key = "playback") {
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("当前设备：${device.name}", style = MaterialTheme.typography.titleSmall)
                                Text(state.media?.title.orEmpty(), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(vm::togglePlayback, enabled = !state.working) {
                                        Icon(if (state.playing) Icons.Outlined.Pause else Icons.Outlined.PlayArrow, null, Modifier.size(17.dp))
                                        Text(if (state.playing) "暂停" else "继续")
                                    }
                                    TextButton(vm::stop, enabled = !state.working) {
                                        Icon(Icons.Outlined.Stop, null, Modifier.size(17.dp)); Text("停止投屏")
                                    }
                                }
                            }
                        }
                        item(key = "divider") { HorizontalDivider() }
                    }
                    item(key = "scan") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (media == null) "选择视频后可发起投屏" else "选择播放设备", Modifier.weight(1f))
                            if (state.scanning || state.working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            IconButton(vm::scan, enabled = !state.scanning) { Icon(Icons.Outlined.Refresh, "重新搜索") }
                        }
                    }
                    state.error?.let { error ->
                        item(key = "error") { Text(error, color = HuluwaColors.Danger, style = MaterialTheme.typography.bodySmall) }
                    }
                    if (!state.scanning && state.devices.isEmpty()) {
                        item(key = "empty") { Text("未发现设备，请打开电视投屏功能后重新搜索。", color = HuluwaColors.Muted) }
                    }
                    items(state.devices, key = { "device:${it.id}" }) { device ->
                        Row(Modifier.fillMaxWidth().clickable(enabled = media != null && !state.working) {
                            media?.let { vm.cast(device, it, onStarted) }
                        }.padding(vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(Icons.Outlined.Cast, null, Modifier.size(22.dp), tint = HuluwaColors.Pine)
                            Text(device.name, Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(dismiss) { Text("关闭") } })
}
