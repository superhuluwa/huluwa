package com.huluwa.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.huluwa.android.StarwarState
import com.huluwa.android.StarwarViewModel
import com.huluwa.android.data.StarwarPlanet

/**
 * 大厅首页：原版 240×320 版式。
 *
 * <pre>
 * ┌────────────────────────────┐  0
 * │        星球大战             │  标题栏 20
 * ├────────────────────────────┤
 * │ [飞机] 昵称  飞行下士 Lv.3  │  个人信息块
 * │ 12胜 3负 · 在线 8 人        │
 * │ K币 1200   水晶 30          │
 * ├────────────────────────────┤
 * │ 多人战专区               >  │  四个专区，方向键上下选择
 * │ 双人战专区               >  │
 * │ 组队战专区               >  │
 * │ 活动专区                 >  │
 * ├────────────────────────────┤
 * │ 飞机商店 我的机库 交易市场 排行榜│ 功能入口
 * ├────────────────────────────┤
 * │ 九大星球                    │
 * │ ○ ○ ○ ○ ○ ○ ○ ○ ○          │
 * ├────────────────────────────┤
 * │ 游戏帮助：是男人就撑20秒…   │
 * └────────────────────────────┘
 * │      选择        返回       │  软键条 20
 * └────────────────────────────┘  320
 * </pre>
 */
@Composable
fun StarwarLobbyPixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
    openDialog: (StarwarLocal) -> Unit,
) {
    val context = LocalContext.current
    val plane = remember(ui.profile.plane) { starwarBitmap(context, StarwarArt.plane(ui.profile.plane)) }
    // 可点区域：靠 y 坐标命中（与原版触屏映射一致）
    val zones = ui.zones.ifEmpty { emptyList() }
    val zoneTop = 108f
    val fnTop = zoneTop + zones.size.coerceAtLeast(4) * 17f + 6f
    val planetTop = fnTop + 38f

    StarwarPixelCanvas(Modifier.fillMaxSize(), onTap = { tx, ty ->
        if (ty < StarwarLayout.BODY_TOP || ty > StarwarLayout.BODY_BOTTOM) return@StarwarPixelCanvas
        when {
            // 个人信息块 → 我的状态
            ty in 24f..100f -> openDialog(StarwarLocal.Status)
            // 专区列表
            ty >= zoneTop && zones.isNotEmpty() &&
                ty < zoneTop + zones.size * 17f -> {
                val index = ((ty - zoneTop) / 17f).toInt()
                zones.getOrNull(index)?.let { vm.openZone(it.key, it.title) }
            }
            // 四个功能入口
            ty >= fnTop && ty < fnTop + 36f -> {
                val index = ((tx - 4f) / 58f).toInt().coerceIn(0, 3)
                when (index) {
                    0 -> openDialog(StarwarLocal.Shop)
                    1 -> openDialog(StarwarLocal.Hangar)
                    2 -> vm.showToast("即将推出,敬请期待!")
                    3 -> openDialog(StarwarLocal.Ranking)
                }
            }
            // 九大星球
            ty >= planetTop && ty < planetTop + 20f -> {
                val index = ((tx - 22f) / 22f).toInt()
                ui.planets.getOrNull(index)?.let { p ->
                    if (p.locked) vm.showToast("${p.name}：${p.unlockText}")
                    else vm.showToast("${p.name}：可对战")
                }
            }
            // 帮助
            ty >= planetTop + 48f -> openDialog(StarwarLocal.Help)
        }
    }) {
        pixelBodyBackground()
        pixelTitleBar(font, "星球大战", "Lv.${ui.profile.level}")

        // ---------------------------------------------------------- 个人信息
        // 14 点阵行高 17（14 字模 + 3 行距），三行文字 + 一行货币共需 68，块高 76
        var y = 24f
        fill(4f, y, 232f, 76f, Color(0xFFF2F6F7))
        pixelBorder(4f, y, 232f, 76f, Color(0xFFCFD8DC))
        pixelSprite(plane, 10f, y + 4f, 20f, 20f)
        pixelText(font, ui.profile.nick.ifBlank { ui.profile.user }.take(8), 36f, y + 3f, StarwarPx.Text)
        pixelText(font, "${ui.profile.rank} 等级${ui.profile.level}", 36f, y + 20f, StarwarPx.Muted)
        pixelText(font, "胜率${ui.profile.winRate.toInt()}% ${ui.profile.wins}胜${ui.profile.losses}负",
            36f, y + 37f, StarwarPx.Muted)
        // 货币：原版 gold/crystal 是标签贴图，这里贴图 + 数值
        pixelSprite(starwarBitmap(context, StarwarArt.GOLD), 6f, y + 61f, 32f, 11f)
        pixelText(font, " ${ui.profile.gold}", 40f, y + 58f, StarwarPx.Gold)
        pixelSprite(starwarBitmap(context, StarwarArt.CRYSTAL), 104f, y + 61f, 32f, 11f)
        pixelText(font, " ${ui.profile.crystal}", 138f, y + 58f, StarwarPx.Crystal)
        pixelText(font, "在线${ui.online}", 236f, y + 58f, StarwarPx.Muted, align = 2)

        // ---------------------------------------------------------- 专区列表
        y = zoneTop
        pixelDivider(y - 6f, x0 = 4f, x1 = 228f)
        zones.forEachIndexed { index, zone ->
            val rowY = y + index * 17f
            pixelListRow(font, rowY, zone.title, selected = false,
                icon = starwarBitmap(context, StarwarArt.PLANE),
                trailing = "${zone.capacity}人")
        }

        // ---------------------------------------------------------- 功能入口
        y = fnTop
        pixelDivider(y - 4f, x0 = 4f, x1 = 228f)
        val labels = listOf(
            "飞机商店" to StarwarArt.PLANE,
            "我的机库" to StarwarArt.ENGINE,
            "交易市场" to StarwarArt.LOTTERY,
            "排行榜" to StarwarArt.RANK,
        )
        // 一行四个：每格 58 宽，图标居中在上、四个字 56px 居中在下（留 1px 余量）
        labels.forEachIndexed { index, (label, res) ->
            val x = 4f + index * 58f
            pixelSprite(starwarBitmap(context, res), x + 21f, y + 1f, 16f, 16f)
            pixelText(font, label, x + 29f, y + 19f, StarwarPx.Text, align = 1)
        }

        // ---------------------------------------------------------- 九大星球
        // 九个 20×20 图标横排，间距 2px：9*20 + 8*2 = 196，左右各留 22 居中
        y = planetTop
        // 星球：图标 16 + 名称并排。名称 2~3 字（28~42px），
        // 按左侧一列图标、右侧一列名称分两栏排 9 行的一半：这里改用
        // 「名称下方换行」太挤，统一取每行 5 个、列宽 46、只显示 2 字简称。
        ui.planets.take(9).forEachIndexed { index, planet ->
            val col = index % 5
            val row = index / 5
            val x = 4f + (if (row == 1) 23f else 0f) + col * 46f
            val cy = y + row * 22f
            val bmp = if (planet.locked) starwarBitmap(context, StarwarArt.LOCK)
                      else starwarBitmap(context, StarwarArt.planet(planet.icon))
            pixelSprite(bmp, x, cy, 18f, 18f)
            // 星球名最长3字（天王星/海王星/冥王星），14px×3=42，放不下图标右侧的 26px
            // 用「去尾字」的 2 字短名保证不串行，完整名在点按时提示
            val short = if (font.isWide(planet.name.getOrNull(2) ?: ' ')) planet.name.take(2) else planet.name
            pixelText(font, short, x + 20f, cy + 2f,
                if (planet.locked) StarwarPx.Muted else StarwarPx.Text)
        }

        // ---------------------------------------------------------- 帮助入口
        // 原版只有一行简介，超出会压到软键条，这里按宽度截断
        val helpY = planetTop + 46f
        pixelDivider(helpY, x0 = 4f, x1 = 228f)
        pixelText(font, "游戏帮助", 6f, helpY + 3f, StarwarPx.Text)
        val intro = ui.help.intro.ifBlank { "是男人就撑20秒游戏的网络对战版" }
        // 内容区宽 228，按 14/7 像素宽截断
        var used = 0
        val shown = intro.takeWhile { ch ->
            val w = if (font.isWide(ch)) 14 else 7
            if (used + w > 228) false else { used += w; true }
        }
        pixelText(font, shown, 6f, helpY + 16f, StarwarPx.Muted)

        // ---------------------------------------------------------- 活动公告
        if (ui.activityNotice.isNotBlank()) {
            pixelText(font, ui.activityNotice.take(16), 6f, StarwarLayout.BODY_BOTTOM - 16f,
                if (ui.activityFree) StarwarPx.Gold else StarwarPx.Muted)
        }

        pixelSoftKeys(font, left = "选择", right = "返回")
    }
}
