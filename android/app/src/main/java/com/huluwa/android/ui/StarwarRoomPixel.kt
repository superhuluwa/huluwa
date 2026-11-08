package com.huluwa.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.huluwa.android.StarwarState
import com.huluwa.android.StarwarViewModel

/**
 * 房间等待界面（聊天室常驻）：原版 240×320 版式。
 *
 * <pre>
 * ┌────────────────────────────┐
 * │    房主的房间      2/6人    │
 * ├────────────────────────────┤
 * │ 玩家列表                    │
 * │ [机] 房主名字      已准备    │
 * │ [机] 对手名字      对手      │
 * ├────────────────────────────┤
 * │ 聊天室                      │
 * │ 系统：xxx 加入房间           │
 * │ 玩家：大家好                 │
 * ├────────────────────────────┤
 * │ [输入框]            发送     │
 * ├────────────────────────────┤
 * │ 准备 / 快速开始    退出      │
 * └────────────────────────────┘
 * │      选择        返回       │
 * └────────────────────────────┘
 * </pre>
 */
@Composable
fun StarwarRoomPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    openDialog: (StarwarLocal) -> Unit,
) {
    val room = ui.room ?: return
    val context = LocalContext.current
    val me = room.memberList.firstOrNull { it.user == ui.profile.user }
    val memberTop = StarwarLayout.BODY_TOP + 20f
    val chatTop = memberTop + room.memberList.size.coerceAtLeast(1) * 20f + 8f
    val chatBottom = StarwarLayout.BODY_BOTTOM - 56f

    StarwarPixelCanvas(Modifier.fillMaxSize(), onTap = { tx, ty ->
        val actionTop = StarwarLayout.BODY_BOTTOM - 28f
        when {
            // 玩家列表：点别人看资料，房主可踢
            ty >= memberTop && ty < chatTop - 4f -> {
                val index = ((ty - memberTop) / 20f).toInt()
                room.memberList.getOrNull(index)?.let { member ->
                    if (member.user != ui.profile.user) vm.openOpponent(member.user)
                    else openDialog(StarwarLocal.Status)
                }
            }
            // 聊天区 → 打开聊天窗口
            ty >= chatTop && ty < chatBottom -> openDialog(StarwarLocal.Chat)
            // 标题栏点右下角 → 房主的房间设置
            ty < StarwarLayout.BODY_TOP && tx > StarwarPx.WIDTH - 24f -> {
                if (ui.isOwner) openDialog(StarwarLocal.Settings)
            }
            // 底部操作
            ty >= actionTop && ty <= actionTop + 24f -> {
                if (tx < StarwarPx.WIDTH / 2f) {
                    if (ui.isOwner) vm.quickStart() else vm.toggleReady()
                } else {
                    vm.leaveRoom()
                }
            }
        }
    }) {
        pixelBodyBackground()
        pixelTitleBar(font, room.name.ifBlank { "房间" }, "${room.members}/${room.capacity}")

        // ---------------------------------------------------------- 房间信息
        pixelText(font, "房间 ${room.id} · ${room.mapName}", 6f, StarwarLayout.BODY_TOP + 3f, StarwarPx.Muted)

        // ---------------------------------------------------------- 玩家列表
        pixelText(font, "玩家列表", 6f, memberTop - 17f, StarwarPx.Text)
        room.memberList.forEachIndexed { index, member ->
            val y = memberTop + index * 20f
            if (member.user == ui.profile.user) {
                fill(0f, y, StarwarPx.WIDTH, 20f, Color(0xFFF0F6F8))
            }
            val planeBmp = starwarBitmap(context, StarwarArt.plane(member.plane))
            pixelSprite(planeBmp, 6f, y + 1f, 18f, 18f)
            pixelText(font, member.nick.ifBlank { member.user }, 28f, y + 3f, StarwarPx.Text)
            val tag = when {
                member.isOwner -> "房主"
                member.user == ui.profile.user -> if (member.ready) "已准备" else "未准备"
                else -> "对手"
            }
            pixelText(font, tag, StarwarPx.WIDTH - 6f, y + 3f,
                if (member.ready || member.isOwner) StarwarPx.Ready else StarwarPx.Muted, align = 2)
            pixelDivider(y + 19f, x0 = 4f, x1 = 236f)
        }

        // ---------------------------------------------------------- 聊天室
        pixelText(font, "聊天室", 6f, chatTop - 17f, StarwarPx.Text)
        fill(4f, chatTop, 232f, chatBottom - chatTop, Color(0xFFFAFCFC))
        pixelBorder(4f, chatTop, 232f, chatBottom - chatTop, Color(0xFFDCE3E6))
        val messages = ui.chat.messages.takeLast(4)
        if (messages.isEmpty()) {
            pixelText(font, ui.chat.notice.ifBlank { "聊天室公告：请爱护聊天环境" }.take(16),
                8f, chatTop + 4f, StarwarPx.Muted)
        } else {
            messages.forEachIndexed { index, message ->
                val text = if (message.kind == "system") message.content
                           else "${message.nick}：${message.content}"
                pixelText(font, text.take(16), 8f, chatTop + 4f + index * 16f,
                    if (message.kind == "system") StarwarPx.Muted else StarwarPx.Text)
            }
        }
        // 倒计时提示（房主开局后）
        if (room.countdownEnd > 0) {
            val left = ((room.countdownEnd - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
            pixelText(font, "${left}秒后开始", StarwarPx.WIDTH / 2f, chatTop + (chatBottom - chatTop) / 2f - 7f,
                StarwarPx.Danger, align = 1)
        }

        // ---------------------------------------------------------- 底部操作
        val actionTop = StarwarLayout.BODY_BOTTOM - 28f
        pixelDivider(actionTop - 2f, x0 = 0f, x1 = StarwarPx.WIDTH)
        val primary = if (ui.isOwner) "快速开始" else if (me?.ready == true) "取消准备" else "准备"
        pixelText(font, primary, StarwarPx.WIDTH * 0.25f, actionTop + 5f, StarwarPx.Ready, align = 1)
        pixelText(font, "退出", StarwarPx.WIDTH * 0.75f, actionTop + 5f, StarwarPx.Danger, align = 1)
        // 聊天间隔提示
        if (ui.chat.nextAllowedAt > System.currentTimeMillis()) {
            pixelText(font, "聊天间隔10秒,请勿刷屏!", StarwarPx.WIDTH / 2f,
                StarwarLayout.BODY_BOTTOM - 12f, StarwarPx.Gold, align = 1)
        }

        pixelSoftKeys(font, left = "选择", right = "返回")
    }
}
