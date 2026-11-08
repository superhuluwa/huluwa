package com.huluwa.android.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.huluwa.android.StarwarState
import com.huluwa.android.StarwarViewModel
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * 对战画面：按原版 240×320 战场还原。
 *
 * <p>原版对局界面的元素分布（由资源清单反推）：</p>
 * <pre>
 * ┌────────────────────────────┐  0
 * │ [live] 血条 91x27           │  左上：存活者标签 + health 贴图
 * │ [timebar] 234x27            │  顶部：时间条（贴图本身带进度）
 * │ [scores] 本方积分: 1234      │  结算前显示的本方分数
 * ├────────────────────────────┤
 * │                            │
 * │      战场 240×260           │  子弹/陨石/道具/飞机/爆炸
 * │                            │
 * ├────────────────────────────┤
 * │ [pslot]道具栏      [clock]  │  底部：道具槽 + 倒计时
 * └────────────────────────────┘  320
 * </pre>
 *
 * <p>操作沿用原版软键：2/4/6/8 移动、5 确定、* 使用道具、# 聊天。</p>
 */
@Composable
fun StarwarBattlePixel(
    ui: StarwarState,
    vm: StarwarViewModel,
    font: StarwarFont,
) {
    val world = ui.world ?: return
    val context = LocalContext.current
    val me = world.players.firstOrNull { it.user == ui.profile.user }
    val bitmaps = remember {
        mapOf(
            "plane" to starwarBitmap(context, StarwarArt.PLANE),
            "bullet" to starwarBitmap(context, StarwarArt.BULLET1),
            "bullet2" to starwarBitmap(context, StarwarArt.BULLET2),
            "prop" to starwarBitmap(context, StarwarArt.PROP),
            "explode" to starwarBitmap(context, StarwarArt.EXPLODE2),
            "explode1" to starwarBitmap(context, StarwarArt.EXPLODE1),
            "explode3" to starwarBitmap(context, StarwarArt.EXPLODE3),
            "damage1" to starwarBitmap(context, StarwarArt.DAMAGE1),
            "damage2" to starwarBitmap(context, StarwarArt.DAMAGE2),
            "damage3" to starwarBitmap(context, StarwarArt.DAMAGE3),
            "stone1" to starwarBitmap(context, StarwarArt.STONE1),
            "stone2" to starwarBitmap(context, StarwarArt.STONE2),
            "shell" to starwarBitmap(context, StarwarArt.SHELL),
            "surround" to starwarBitmap(context, StarwarArt.SURROUND),
            "launch" to starwarBitmap(context, StarwarArt.LAUNCH),
            "health" to starwarBitmap(context, StarwarArt.HEALTH),
            "timebar" to starwarBitmap(context, StarwarArt.TIMEBAR),
            "pslot" to starwarBitmap(context, StarwarArt.PSLOT),
            "clock" to starwarBitmap(context, StarwarArt.CLOCK),
            "live" to starwarBitmap(context, StarwarArt.LIVE),
            "scores" to starwarBitmap(context, StarwarArt.SCORES),
            "dead" to starwarBitmap(context, StarwarArt.AI),
        )
    }
    // 战场区域：顶部信息条（时间条/血条/分数）之下、道具栏之上
    val fieldTop = 78f
    val fieldBottom = 286f
    val fieldW = StarwarPx.WIDTH
    val fieldH = fieldBottom - fieldTop
    // 世界坐标 240×320 → 战场区域。保持等比缩放（否则贴着上下边界的物体会被压扁），
    // 纵向按宽度换算后把超出部分裁掉。
    val sx = fieldW / world.width
    val sy = sx
    // 世界高度 320 按等比只有 320*sx=320 高，战场只有 208，纵向居中裁切
    val fieldOffsetY = fieldTop + (fieldH - world.height * sy) / 2f

    StarwarPixelCanvas(Modifier.fillMaxSize(), onTap = { tx, ty ->
        // 底部道具槽 = 使用道具
        if (ty >= fieldBottom) vm.useItem()
    }) {
        // ---------------------------------------------------------- 黑色战场底
        fill(0f, 0f, StarwarPx.WIDTH, StarwarPx.HEIGHT, StarwarPx.Space)

        // ---------------------------------------------------------- 生命条
        // 原版 health.bmp 是 91×27 的「血槽 + 填充条」贴图，直接用整图做底
        pixelSprite(bitmaps["health"], 2f, 34f, 91f, 27f)
        val hp = me?.hp ?: 0
        pixelText(font, "$hp", 96f, 40f, Color(0xFF9FE8B0))

        // ---------------------------------------------------------- 时间条
        // timebar.bmp 原尺寸 234×27，横向铺在顶部
        val leftMs = ((20_000 - world.elapsed).coerceAtLeast(0)) / 1000
        pixelSprite(bitmaps["timebar"], 2f, 2f, 234f, 27f)
        pixelText(font, "${leftMs}秒", StarwarPx.WIDTH - 4f, 8f, Color.White, align = 2)

        // ---------------------------------------------------------- 本方分数
        // scores.bmp 是「本方积分」标签图，后接数字
        pixelSprite(bitmaps["scores"], 4f, 62f, 47f, 12f)
        pixelText(font, "${me?.score ?: 0}", 54f, 61f, Color(0xFF8FE3C0))

        // ---------------------------------------------------------- 战场元素
        // 战场区 = fieldTop..fieldBottom。等比换算后世界高 320*sx 会超过 208，
        // 因此每个元素都先判断是否落在战场内再画，避免盖住顶部信息条与底部道具栏。
        fun inField(syPos: Float): Boolean = syPos >= fieldTop && syPos <= fieldBottom
        // 子弹：原版三种 5×5 / 12×12 / 11×11 贴图
        world.bullets.forEach { bullet ->
            val y = fieldOffsetY + bullet.y * sy
            if (inField(y)) {
                val size = StarwarSpriteSize.of("bullet1")
                pixelSpriteCentered(bitmaps["bullet"], bullet.x * sx, y, size.first, size.second)
            }
        }
        // 陨石
        world.stones.forEach { stone ->
            val y = fieldOffsetY + stone.y * sy
            if (inField(y)) {
                val key = if (stone.kind == 2) "stone2" else "stone1"
                val size = StarwarSpriteSize.of(key)
                pixelSpriteCentered(bitmaps[key], stone.x * sx, y, size.first, size.second)
            }
        }
        // 道具
        world.props.forEach { prop ->
            val y = fieldOffsetY + prop.y * sy
            if (inField(y)) {
                val size = StarwarSpriteSize.of("prop")
                pixelSpriteCentered(bitmaps["prop"], prop.x * sx, y, size.first, size.second)
            }
        }
        // 导弹（shell.bmp）
        world.missiles.forEach { missile ->
            val y = fieldOffsetY + missile.y * sy
            if (inField(y)) pixelSpriteCentered(bitmaps["shell"], missile.x * sx, y, 20f, 20f)
        }

        // ---------------------------------------------------------- 玩家
        world.players.forEach { player ->
            val px = player.x * sx
            val py = fieldOffsetY + player.y * sy
            val key = when {
                !player.alive -> "explode"
                player.hp <= 20 -> "damage3"
                player.hp <= 40 -> "damage2"
                player.hp <= 60 -> "damage1"
                else -> "plane"
            }
            if (inField(py)) {
                val size = StarwarSpriteSize.of(if (key == "plane") "plane" else key)
                pixelSpriteCentered(bitmaps[key], px, py, size.first, size.second)
                // 护盾光圈（surround.bmp）
                if (player.shield || player.surround) {
                    pixelSpriteCentered(bitmaps["surround"], px, py, 20f, 20f)
                }
            }
        }

        // ---------------------------------------------------------- 玩家名单
        // 原版左下角：selfs「本方分数:」/ oppos「对方分数:」标签 + 数值
        var listY = fieldBottom - 4f
        world.players.reversed().forEach { player ->
            listY -= 14f
            val mine = player.user == ui.profile.user
            val tag = if (mine) StarwarArt.SELFS else StarwarArt.OPPOS
            pixelSprite(starwarBitmap(context, tag), 4f, listY, 40f, 10f)
            pixelText(font, "${player.score}", 48f, listY - 1f,
                if (player.alive) Color.White else Color(0xFF808080))
        }

        // ---------------------------------------------------------- 道具栏
        // pslot.bmp 24×24 槽位 + 剩余道具数
        pixelSprite(bitmaps["pslot"], 4f, 288f, 24f, 24f)
        pixelSprite(bitmaps["prop"], 28f, 290f, 20f, 20f)
        pixelText(font, "×${me?.shells ?: 0}", 50f, 294f, Color.White)
        // 右下角时钟（原版对局倒计时）
        pixelSprite(bitmaps["clock"], StarwarPx.WIDTH - 24f, 288f, 20f, 20f)

        // ---------------------------------------------------------- 操作提示条
        // 原版底部一行简短提示：2/4/6/8 移动，5 确定，* 道具
        pixelText(font, "拖拽移动 · 点道具栏使用", StarwarPx.WIDTH / 2f, 314f,
            Color(0xFFB0BEC5), align = 1)
    }
    // 触控层叠在画布之上：战场内拖拽移动，点道具栏使用道具
    StarwarBattleJoystick(vm, fieldTop, fieldBottom)
}

/**
 * 对战触控：原版靠 2/4/6/8 按住持续移动，触摸屏上改成一个虚拟摇杆。
 *
 * <p>在战场区域内按下 → 以按下点为原点出现摇杆，拖出方向即持续移动（每 120ms 重发），
 * 松手归零；点右下道具槽 = 使用道具（对应原版 * 键）。</p>
 */
@Composable
private fun StarwarBattleJoystick(vm: StarwarViewModel, fieldTop: Float, fieldBottom: Float) {
    var knob by remember { mutableStateOf<Offset?>(null) }
    var dir by remember { mutableStateOf(0 to 0) }
    var holding by remember { mutableStateOf(false) }
    LaunchedEffect(holding, dir) {
        if (holding) {
            vm.input(dir.first, dir.second)
            while (holding) {
                delay(120)
                vm.input(dir.first, dir.second)
            }
        }
    }
    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            // 组件本身就是 240:320 等比尺寸，触摸像素宽 = 240 逻辑像素
            val s = size.width / StarwarPx.WIDTH
            detectDragGestures(
                onDragStart = { offset ->
                    val gy = offset.y / s
                    if (gy in fieldTop..fieldBottom) {
                        knob = offset; holding = true
                    }
                },
                onDrag = { change, drag ->
                    change.consume()
                    dir = if (abs(drag.x) > abs(drag.y)) {
                        (if (drag.x > 0) 1 else -1) to 0
                    } else {
                        0 to (if (drag.y > 0) 1 else -1)
                    }
                },
                onDragEnd = { holding = false; knob = null; vm.input(0, 0) },
                onDragCancel = { holding = false; knob = null; vm.input(0, 0) },
            )
        }
            // 轻点道具栏（底部 288..312） = 使用道具，对应原版 * 键
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val s = size.width / StarwarPx.WIDTH
                    val gy = offset.y / s
                    val gx = offset.x / s
                    if (gy >= fieldBottom && gx < 80f) vm.useItem()
                }
            },
    ) {
        // 摇杆按下点的视觉反馈：以按下点为圆心画一个淡色圆环
        knob?.let { center ->
            StarwarPixelCanvas(Modifier.fillMaxSize()) {
                drawCircle(Color(0x33FFFFFF), radius = 22f, center = Offset(center.x, center.y))
                drawCircle(Color(0x66FFFFFF), radius = 4f,
                    center = Offset(center.x + dir.first * 14f, center.y + dir.second * 14f))
            }
        }
    }
}