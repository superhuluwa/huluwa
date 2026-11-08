package com.huluwa.android.ui

import android.content.Context
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * 星球大战像素画布。
 *
 * <p>原版是 240×320、14px 字高的 MTK 手机游戏，所有界面元素都按像素坐标硬布局。
 * 这里不再用 Material 组件拼界面，而是把整屏当成一块 [WIDTH]×[HEIGHT] 的画布，
 * 用原版贴图与原版点阵字库 1:1 地画上去，再整体等比放大铺满屏幕。
 * 这样换算关系与当年手机一致：1 个游戏像素 = 画布上的 1 个逻辑点。</p>
 */
object StarwarPx {
    /** 原版屏幕宽高（config.ini: SCREEN_X=240 SCREEN_Y=320） */
    const val WIDTH = 240f
    const val HEIGHT = 320f
    /** 原版 font/asc14.sky 与 gb14.sky 都是 14 点阵 */
    const val TEXT_H = 14f
    /** 半角字宽：asc14.sky 每字 14 字节 = 8×14，实际字形占 7 列 */
    const val ASCII_W = 7f

    /** 原版调色板：冒泡平台的白底蓝头，与游戏内的黑底战场的双色体系 */
    val Paper = Color(0xFFFFFFFF)
    val Ink = Color(0xFF000000)
    /** 标题栏 / 高亮条：原版平台截图里那种青蓝渐变的主色 */
    val HeaderTop = Color(0xFF2C6E8F)
    val HeaderBottom = Color(0xFF1B4F6B)
    val HeaderText = Color(0xFFFFFFFF)
    /** 列表选中条 */
    val Selected = Color(0xFFE8EEF2)
    /** 未选中 / 禁用文字 */
    val Muted = Color(0xFF6E7B85)
    /** 战斗场景底色 */
    val Space = Color(0xFF000000)
    /** 中文点阵字默认色 */
    val Text = Color(0xFF1A1A1A)
    /** 原版强调色：K币的金、水晶的蓝 */
    val Gold = Color(0xFFC08A00)
    val Crystal = Color(0xFF2E6DA4)
    /** 命中 / 达成目标的高亮绿 */
    val Ready = Color(0xFF1F9E4A)
    /** 危险 / 退出 */
    val Danger = Color(0xFFB3261E)
}

/**
 * 点阵字库。首次使用时把 assets 里的 `starwar/gb14.sky`、`starwar/asc14.sky`
 * 读进内存，之后按 GB2312 区位或 ASCII 码取字模。
 *
 * <p>gb14.sky 共 8178 个 14×14 字模（每字 28 字节，两字节一行、高位在左）；
 * asc14.sky 共 255 个 8×14 字模（每字 14 字节，一字节一行）。</p>
 */
class StarwarFont private constructor(
    private val gb: ByteArray,
    private val ascii: ByteArray,
) {
    companion object {
        @Volatile
        private var cached: StarwarFont? = null

        fun of(context: Context): StarwarFont =
            cached ?: synchronized(this) {
                cached ?: load(context.applicationContext).also { cached = it }
            }

        private fun load(context: Context): StarwarFont {
            val gb = runCatching {
                context.assets.open("starwar/gb14.sky").use { it.readBytes() }
            }.getOrElse { ByteArray(0) }
            val ascii = runCatching {
                context.assets.open("starwar/asc14.sky").use { it.readBytes() }
            }.getOrElse { ByteArray(0) }
            return StarwarFont(gb, ascii)
        }

        /** Android 的 Charsets 里没有 GBK，必须显式按名取；只取一次 */
        private val GBK: java.nio.charset.Charset = java.nio.charset.Charset.forName("GBK")
    }

    /** 字符 → 字模偏移缓存（GBK 编码转换有开销，逐像素绘制时不能反复做） */
    private val offsetCache = java.util.concurrent.ConcurrentHashMap<Char, Int>()

    /**
     * GB2312 区位 → 字模偏移。字库按「区 1..94 × 位 1..94」顺序排列，
     * 每字 28 字节。
     *
     * <p>注意：绝不能拿 `Char.code` 当 GBK 码用——中文在 UTF-16 里是单个 BMP 码位，
     * 拿不到 GB2312 的双字节区位。这里把字符还原成 GBK 字节序列再判断。</p>
     */
    private fun gbOffset(ch: Char): Int = offsetCache.getOrPut(ch) {
        val bytes = ch.toString().toByteArray(GBK)
        if (bytes.size != 2) return@getOrPut -1
        val hi = bytes[0].toInt() and 0xFF
        val lo = bytes[1].toInt() and 0xFF
        if (hi < 0xA1 || hi > 0xF7 || lo < 0xA1 || lo > 0xFE) return@getOrPut -1
        val off = ((hi - 0xA1) * 94 + (lo - 0xA1)) * 28
        if (off >= 0 && off + 28 <= gb.size) off else -1
    }

    /** 全角字符取一个 14×14 点阵，按 (row, col) 判断是否有笔划。 */
    fun glyph(ch: Char, row: Int, col: Int): Boolean {
        val off = gbOffset(ch)
        if (off < 0) return false
        if (row !in 0..13 || col !in 0..15) return false
        val bits = ((gb[off + row * 2].toInt() and 0xFF) shl 8) or (gb[off + row * 2 + 1].toInt() and 0xFF)
        return bits and (1 shl (15 - col)) != 0
    }

    /** 半角字符取一个 8×14 点阵（字形只占左侧 7 列）。 */
    fun asciiGlyph(ch: Char, row: Int, col: Int): Boolean {
        val code = ch.code
        if (code < 0 || code > 254) return false
        val off = code * 14
        if (off + 14 > ascii.size) return false
        if (row !in 0..13 || col !in 0..7) return false
        return ascii[off + row].toInt() and (1 shl (7 - col)) != 0
    }

    /** 该字符是否是全角（走 14 宽字模）。GBK 双字节字符都是全角，ASCII 都是半角。 */
    fun isWide(ch: Char): Boolean = ch.code >= 0x80

    /** 计算一段文字在 14 点阵下的像素宽度。 */
    fun measure(text: String): Float = text.sumOf { ch ->
        (if (isWide(ch)) StarwarPx.TEXT_H else StarwarPx.ASCII_W).toDouble()
    }.toFloat()

    /**
     * 把单个字模烘焙成 14×14 的白色位图，绘制时用 tint 上色。
     *
     * <p>逐像素 `drawRect` 一屏要上万次调用，60 帧下开销不可接受。
     * 烘焙成 ImageBitmap 后一个字只需一次 `drawImage`，着色交给
     * `ColorFilter.tint`。</p>
     */
    private val glyphCache = java.util.concurrent.ConcurrentHashMap<Char, ImageBitmap>()

    fun glyphBitmap(ch: Char): ImageBitmap = glyphCache.getOrPut(ch) {
        val wide = isWide(ch)
        val w = if (wide) 14 else 8
        val colors = IntArray(w * 14)
        val on = android.graphics.Color.WHITE
        for (row in 0 until 14) {
            for (col in 0 until w) {
                val lit = if (wide) glyph(ch, row, col) else asciiGlyph(ch, row, col)
                if (lit) colors[row * w + col] = on
            }
        }
        android.graphics.Bitmap.createBitmap(colors, w, 14, android.graphics.Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }
}

/**
 * 在画布上绘制点阵文字。
 *
 * <p>字模在 [StarwarFont] 里烘焙成位图后缓存，这里逐字 `drawImage`，
 * 用 `ColorFilter.tint` 上色——字形与原版一模一样，且只按字开销而不是按像素开销。</p>
 */
fun DrawScope.pixelText(
    font: StarwarFont,
    text: String,
    x: Float,
    y: Float,
    color: Color,
    maxWidth: Float = Float.MAX_VALUE,
    align: Int = 0, // 0 左对齐, 1 居中, 2 右对齐
) {
    var cx = when (align) {
        1 -> x - font.measure(text) / 2f
        2 -> x - font.measure(text)
        else -> x
    }
    val tint = androidx.compose.ui.graphics.ColorFilter.tint(color)
    for (ch in text) {
        if (maxWidth != Float.MAX_VALUE && cx > x + maxWidth) break
        if (ch == ' ') {
            cx += if (font.isWide(ch)) StarwarPx.TEXT_H else StarwarPx.ASCII_W
            continue
        }
        val bmp = font.glyphBitmap(ch)
        drawImage(
            image = bmp,
            dstOffset = androidx.compose.ui.unit.IntOffset(cx.toInt(), y.toInt()),
            colorFilter = tint,
        )
        cx += if (font.isWide(ch)) StarwarPx.TEXT_H else StarwarPx.ASCII_W
    }
}

/** 折行：按 14 点阵的实际宽度把一段文字切成若干行。 */
fun StarwarFont.wrap(text: String, maxWidth: Float): List<String> {
    val lines = mutableListOf<String>()
    val sb = StringBuilder()
    var w = 0f
    for (ch in text) {
        val cw = if (isWide(ch)) StarwarPx.TEXT_H else StarwarPx.ASCII_W
        if (ch == '\n') {
            lines += sb.toString(); sb.clear(); w = 0f; continue
        }
        if (w + cw > maxWidth && sb.isNotEmpty()) {
            lines += sb.toString(); sb.clear(); w = 0f
        }
        sb.append(ch); w += cw
    }
    if (sb.isNotEmpty()) lines += sb.toString()
    return lines
}

// ------------------------------------------------------------------ 贴图

/** 原版贴图的绘制尺寸，取自 starwar.ext 内嵌的资源表（名称 + w + h）。 */
object StarwarSpriteSize {
    val sizes: Map<String, Pair<Float, Float>> = mapOf(
        "health" to (91f to 27f),
        "bullet" to (5f to 5f),
        "bullet1" to (12f to 12f),
        "bullet2" to (11f to 11f),
        "plane" to (20f to 20f),
        "stone1" to (22f to 21f),
        "stone2" to (32f to 32f),
        "prop" to (20f to 20f),
        "live" to (44f to 13f),
        "gold" to (32f to 13f),
        "number" to (7f to 10f),
        "clock" to (20f to 20f),
        "timebar" to (234f to 27f),
        "pslot" to (24f to 24f),
        "exploid1" to (28f to 29f),
        "exploid2" to (42f to 41f),
        "exploid3" to (28f to 29f),
        "damage1" to (14f to 14f),
        "damage2" to (19f to 19f),
        "damage3" to (16f to 16f),
        "flash" to (22f to 105f),
        "listicon" to (20f to 20f),
        "crystal" to (32f to 13f),
        "rank" to (32f to 13f),
        "prgs1" to (12f to 24f),
        "prgs2" to (40f to 24f),
        "prgs3" to (12f to 24f),
        "operate" to (104f to 13f),
        "youuse" to (48f to 13f),
        "oppouse" to (84f to 13f),
        "surround" to (20f to 20f),
        "lauch" to (20f to 20f),
        "oppoself" to (60f to 13f),
        "scores" to (47f to 12f),
        "team" to (60f to 13f),
        "selfs" to (53f to 13f),
        "oppos" to (53f to 13f),
        "lock" to (20f to 20f),
        "crown" to (20f to 20f),
        "lottery" to (18f to 18f),
        "shell" to (20f to 20f),
        "ai" to (20f to 20f),
        "engine" to (20f to 20f),
        "sun" to (20f to 20f),
    )

    fun of(name: String): Pair<Float, Float> = sizes[name] ?: (20f to 20f)
}

/** 把资源 id 解成 ImageBitmap，供 Canvas 按原始像素尺寸绘制。 */
fun starwarBitmap(context: Context, resId: Int): ImageBitmap? {
    val d = ContextCompat.getDrawable(context, resId) ?: return null
    val w = d.intrinsicWidth.coerceAtLeast(1)
    val h = d.intrinsicHeight.coerceAtLeast(1)
    val bmp = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    d.setBounds(0, 0, w, h)
    d.draw(canvas)
    return bmp.asImageBitmap()
}

/** 按原版资源表里的尺寸绘制一张贴图（左上角对齐）。 */
fun DrawScope.pixelSprite(
    bmp: ImageBitmap?,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
) {
    if (bmp == null) return
    // 放大时用最近邻，保持像素画的硬边
    drawImage(
        bmp,
        dstOffset = androidx.compose.ui.unit.IntOffset(x.toInt(), y.toInt()),
        dstSize = androidx.compose.ui.unit.IntSize(
            w.toInt().coerceAtLeast(1), h.toInt().coerceAtLeast(1)),
        filterQuality = androidx.compose.ui.graphics.FilterQuality.None,
    )
}

/** 按原版资源表里的尺寸绘制一张贴图（中心对齐）。 */
fun DrawScope.pixelSpriteCentered(
    bmp: ImageBitmap?,
    cx: Float,
    cy: Float,
    w: Float,
    h: Float,
) = pixelSprite(bmp, cx - w / 2f, cy - h / 2f, w, h)

/**
 * 逻辑像素画布：把 240×320 坐标系映射到组件实际尺寸，最近邻放大，保持像素硬边。
 * 支持把点击换算回逻辑坐标（[onTap]）。
 */
@Composable
fun StarwarPixelCanvas(
    modifier: Modifier = Modifier,
    onTap: ((Float, Float) -> Unit)? = null,
    onDraw: DrawScope.(StarwarFont) -> Unit,
) {
    val context = LocalContext.current
    val font = remember { StarwarFont.of(context) }
    Canvas(
        modifier = modifier.pointerInput(onTap) {
            if (onTap != null) {
                detectTapGestures { offset ->
                    val s = minOf(size.width / StarwarPx.WIDTH, size.height / StarwarPx.HEIGHT)
                    val dx = (size.width - StarwarPx.WIDTH * s) / 2f
                    val dy = (size.height - StarwarPx.HEIGHT * s) / 2f
                    onTap((offset.x - dx) / s, (offset.y - dy) / s)
                }
            }
        },
    ) {
        val s = minOf(size.width / StarwarPx.WIDTH, size.height / StarwarPx.HEIGHT)
        val dx = (size.width - StarwarPx.WIDTH * s) / 2f
        val dy = (size.height - StarwarPx.HEIGHT * s) / 2f
        translate(left = dx, top = dy) {
            scale(scale = s, pivot = Offset.Zero) {
                onDraw(font)
            }
        }
    }
}
