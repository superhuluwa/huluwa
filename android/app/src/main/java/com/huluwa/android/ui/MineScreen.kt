package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.AppSheet
import com.huluwa.android.AppState
import com.huluwa.android.HuluwaViewModel

@Composable fun MineScreen(ui: AppState, vm: HuluwaViewModel) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 3.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { AccountCard(ui) }
        item {
            SectionTitle("连接与账户")
            Panel(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    MineEntry(Icons.Outlined.Dns, "连接配置", "${ui.auth.server} · ${ui.auth.username}") { vm.showSheet(AppSheet.Account) }
                    HorizontalDivider(color = HuluwaColors.Line)
                    MineEntry(Icons.Outlined.Logout, "退出登录 / 切换服务器", "退出后服务器任务继续运行", danger = true) {
                        vm.confirm("退出登录", "退出后可以切换服务器或账号，服务器任务会继续运行。", "退出") { vm.logout() }
                    }
                }
            }
        }
        item { Text("葫芦娃 · 家里的服务，随身就好。", Modifier.padding(top = 6.dp), color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium) }
    }
}

@Composable private fun AccountCard(ui: AppState) {
    Panel(Modifier.fillMaxWidth(), HuluwaColors.SoftGreen) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(Color(0xFFE8F0E1)),
                contentAlignment = Alignment.Center,
            ) {
                Text("葫", color = Color(0xFF49633B), fontSize = 24.sp, lineHeight = 28.sp, fontFamily = FontFamily.Serif)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(ui.auth.username.ifBlank { "未登录" }, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(ui.auth.server, color = HuluwaColors.Muted, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            StatusTag(if (ui.realtime) "已连接" else "轮询同步", ui.realtime)
        }
    }
}

@Composable private fun MineEntry(
    icon: ImageVector,
    title: String,
    subtitle: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 4.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val tone = if (danger) HuluwaColors.Danger else HuluwaColors.Pine
        Icon(icon, null, Modifier.size(21.dp), tint = tone)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = if (danger) HuluwaColors.Danger else HuluwaColors.Ink,
                fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = HuluwaColors.Muted, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.size(16.dp), tint = HuluwaColors.Muted)
    }
}
