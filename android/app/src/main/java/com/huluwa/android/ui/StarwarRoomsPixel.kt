package com.huluwa.android.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.huluwa.android.StarwarState
import com.huluwa.android.StarwarViewModel
import com.huluwa.android.data.StarwarRoom

/**
 * 专区房间列表：原版 240×320 版式。
 *
 * <pre>
 * ┌────────────────────────────┐
 * │      多人战专区      1/3    │  标题栏
 * ├────────────────────────────┤
 * │ 房主的房间              进入 │  每行一个房间：
 * │ 地球 · 2/6人 · 无密码        │  名称/进入 在上行，地图/人数在下行
 * │ ...                         │
 * ├────────────────────────────┤
 * │ 创建房间    刷新             │  操作行
 * ├────────────────────────────┤
 * │ 首页 上页 下页 末页 刷新     │  原版五行分页（原文字序）
 * └────────────────────────────┘
 * │      选择        返回       │
 * └────────────────────────────┘
 * </pre>
 */
@Composable
fun StarwarRoomsPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    cursor: Int,
    setCursor: (Int) -> Unit,
) {
    val page = ui.roomPage
    val rooms = page?.rooms.orEmpty()
    val listTop = StarwarLayout.BODY_TOP + 4f
    val listBottom = StarwarLayout.BODY_BOTTOM - 46f
    val rowH = 38f
    val visible = ((listBottom - listTop) / rowH).toInt().coerceAtLeast(1)

    StarwarPixelCanvas(Modifier.fillMaxSize(), onTap = { tx, ty ->
        val actionTop = listBottom + 2f
        val pagerTop = actionTop + 24f
        when {
            ty >= listTop && ty < listBottom -> {
                val index = ((ty - listTop) / rowH).toInt()
                rooms.getOrNull(index)?.let { setCursor(index); vm.tapRoom(it) }
            }
            ty >= actionTop && ty < actionTop + 22f -> {
                if (tx < StarwarPx.WIDTH / 2f) vm.showCreateRoom() else vm.refreshRooms()
            }
            ty >= pagerTop -> {
                // 原版分页五键：首页 / 上页 / 下页 / 末页 / 刷新
                when ((tx / 48f).toInt()) {
                    0 -> vm.firstPage()
                    1 -> vm.page(-1)
                    2 -> vm.page(1)
                    3 -> vm.lastPage()
                    else -> vm.refreshRooms()
                }
            }
        }
    }) {
        pixelBodyBackground()
        pixelTitleBar(font, ui.zoneTitle.ifBlank { "专区" },
            "${ui.page + 1}/${page?.pages ?: 1}")

        // ---------------------------------------------------------- 房间列表
        if (rooms.isEmpty()) {
            pixelText(font, "还没有房间", StarwarPx.WIDTH / 2f, listTop + 40f,
                StarwarPx.Muted, align = 1)
            pixelText(font, "点「创建房间」开一个", StarwarPx.WIDTH / 2f, listTop + 58f,
                StarwarPx.Muted, align = 1)
        } else {
            val start = cursor.coerceAtLeast(0).coerceAtMost((rooms.size - visible).coerceAtLeast(0))
            rooms.drop(start).take(visible).forEachIndexed { i, room ->
                val y = listTop + i * rowH
                val index = start + i
                if (index == cursor) {
                    fill(0f, y, StarwarPx.WIDTH, rowH, StarwarPx.Selected)
                    fill(0f, y, 2f, rowH, StarwarPx.HeaderBottom)
                }
                // 第一行：房间名 + 「进入」；第二行：地图 · 人数 · 密码
                // 第一行：房间名（截 10 字）+「进入」；第二行：地图/人数/密码
                val title = room.name.ifBlank { "房间" }
                pixelText(font, title.take(10), 6f, y + 2f, StarwarPx.Text)
                pixelText(font, "进入", StarwarPx.WIDTH - 6f, y + 2f, StarwarPx.Crystal, align = 2)
                val detail = buildString {
                    append(room.mapName.ifBlank { "地球" })
                    append(" ")
                    append("${room.members}/${room.capacity}人")
                    if (room.hasPassword) append(" 有密码")
                }
                pixelText(font, detail, 6f, y + 19f, StarwarPx.Muted)
                pixelDivider(y + rowH - 1f, x0 = 4f, x1 = 236f)
            }
            // 滚动条：原版列表右侧的细竖条
            if (rooms.size > visible) {
                val trackH = listBottom - listTop
                val barH = (trackH * visible / rooms.size).coerceAtLeast(12f)
                val barY = listTop + (trackH - barH) * start / (rooms.size - visible).coerceAtLeast(1)
                fill(StarwarPx.WIDTH - 4f, listTop, 2f, trackH, Color(0xFFE3E8EA))
                fill(StarwarPx.WIDTH - 4f, barY, 2f, barH, Color(0xFF9AA4A0))
            }
        }

        // ---------------------------------------------------------- 操作行
        val actionTop = listBottom + 2f
        pixelDivider(actionTop, x0 = 0f, x1 = StarwarPx.WIDTH)
        pixelText(font, "创建房间", StarwarPx.WIDTH * 0.25f, actionTop + 5f, StarwarPx.Crystal, align = 1)
        pixelText(font, "刷新", StarwarPx.WIDTH * 0.75f, actionTop + 5f, StarwarPx.Text, align = 1)

        // ---------------------------------------------------------- 分页栏
        val pagerTop = actionTop + 24f
        pixelDivider(pagerTop - 2f, x0 = 0f, x1 = StarwarPx.WIDTH)
        listOf("首页", "上页", "下页", "末页", "刷新").forEachIndexed { i, label ->
            pixelText(font, label, 24f + i * 48f, pagerTop + 2f, StarwarPx.Text, align = 1)
        }
        pixelText(font, "共${page?.total ?: 0}个房间", StarwarPx.WIDTH / 2f,
            StarwarLayout.BODY_BOTTOM - 12f, StarwarPx.Muted, align = 1)

        pixelSoftKeys(font, left = "选择", right = "返回")
    }
}
