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
 * 结算页：原版「游戏排名结果」。
 *
 * <pre>
 * ┌────────────────────────────┐
 * │        游戏排名结果         │
 * ├────────────────────────────┤
 * │ [冠] 昵称          1234分   │  冠军用 crown.bmp 王冠
 * │  2   昵称           980分   │
 * │  3   昵称           760分   │
 * ├────────────────────────────┤
 * │ 注意：奖励水晶在所有玩家结束 │
 * │ 并公布最终排名后才增加       │
 * ├────────────────────────────┤
 * │ 继续      返回      再来一局 │
 * └────────────────────────────┘
 * │      选择        返回       │
 * └────────────────────────────┘
 * </pre>
 */
@Composable
fun StarwarResultPixel(ui: StarwarState, vm: StarwarViewModel, font: StarwarFont) {
    val context = LocalContext.current
    val rows = if (ui.result.isNotEmpty()) ui.result else ui.standings
    val listTop = StarwarLayout.BODY_TOP + 8f
    val actionTop = StarwarLayout.BODY_BOTTOM - 26f

    StarwarPixelCanvas(Modifier.fillMaxSize(), onTap = { tx, ty ->
        if (ty >= actionTop) {
            when ((tx / 80f).toInt()) {
                0 -> vm.exitBattle()
                1 -> vm.backToRoomsFromResult()
                else -> vm.playAgain()
            }
        }
    }) {
        pixelBodyBackground()
        pixelTitleBar(font, "游戏排名结果")

        // ---------------------------------------------------------- 排名列表
        pixelSprite(starwarBitmap(context, StarwarArt.SCORES), 6f, listTop, 47f, 12f)
        var y = listTop + 16f
        if (rows.isEmpty()) {
            pixelText(font, "游戏结束!", StarwarPx.WIDTH / 2f, y + 20f, StarwarPx.Muted, align = 1)
        }
        rows.forEach { standing ->
            fill(4f, y, 232f, 36f, Color(0xFFF7FAFB))
            pixelBorder(4f, y, 232f, 36f, Color(0xFFE0E7EA))
            // 名次：第 1 名用原版 crown.bmp 王冠贴图
            if (standing.ranking == 1) {
                pixelSprite(starwarBitmap(context, StarwarArt.CROWN), 10f, y + 8f, 20f, 20f)
            } else {
                val rankColor = when (standing.ranking) {
                    2 -> Color(0xFF8C9AA5)
                    3 -> Color(0xFFB87333)
                    else -> StarwarPx.Muted
                }
                pixelText(font, "${standing.ranking}", 20f, y + 11f, rankColor, align = 1)
            }
            pixelText(font, standing.nickname.ifBlank { standing.username }.take(10), 36f, y + 2f, StarwarPx.Text)
            pixelText(font, "${standing.score}分 · ${standing.survivedMs / 1000}秒",
                36f, y + 19f, StarwarPx.Muted)
            if (standing.survivedTarget) {
                // 达成「撑住 20 秒」用原版强调色标出
                pixelText(font, "达标", StarwarPx.WIDTH - 8f, y + 11f, StarwarPx.Ready, align = 2)
            }
            y += 40f
            if (y > actionTop - 30f) return@forEach
        }

        // ---------------------------------------------------------- 奖励说明
        pixelDivider(actionTop - 30f, x0 = 4f, x1 = 236f)
        pixelParagraph(font,
            "请注意：每局游戏结束后奖励的水晶,在本局游戏所有玩家都结束游戏并公布最终排名信息后才会增加。",
            6f, actionTop - 26f, 228f, StarwarPx.Muted, lineHeight = 13f)

        // ---------------------------------------------------------- 底部三键
        pixelDivider(actionTop, x0 = 0f, x1 = StarwarPx.WIDTH)
        pixelText(font, "继续", 40f, actionTop + 6f, StarwarPx.Text, align = 1)
        pixelText(font, "返回", 120f, actionTop + 6f, StarwarPx.Text, align = 1)
        pixelText(font, "再来一局!", 200f, actionTop + 6f, StarwarPx.Ready, align = 1)

        pixelSoftKeys(font, left = "选择", right = "返回")
    }
}
