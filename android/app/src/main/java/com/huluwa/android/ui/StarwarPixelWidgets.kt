package com.huluwa.android.ui

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext

/**
 * 原版界面的基础绘制件：标题栏、软键条、列表行、对话框、进度条。
 *
 * <p>这些函数都直接操作 240×320 的逻辑坐标，由 [StarwarPixelCanvas] 统一缩放。
 * 版式取自冒泡平台 240×320 界面的通行布局：
 * 标题栏 0–20、内容区 20–300、软键条 300–320。</p>
 */
object StarwarLayout {
    /** 顶部标题栏高度 */
    const val TITLE_H = 20f
    /** 底部软键条高度 */
    const val SOFTKEY_H = 20f
    /** 内容区上边界 */
    const val BODY_TOP = TITLE_H
    /** 内容区下边界 */
    const val BODY_BOTTOM = StarwarPx.HEIGHT - SOFTKEY_H
    /** 内容区可用宽度（左右各留 4） */
    const val CONTENT_X = 4f
    const val CONTENT_W = StarwarPx.WIDTH - 8f
    /** 列表行高：14 点阵字 + 上下各留 3 */
    const val ROW_H = 20f
}

// ------------------------------------------------------------------ 标题栏

/** 顶部标题栏：青蓝底 + 白色居中标题 + 右上角页码/计数 */
fun DrawScope.pixelTitleBar(font: StarwarFont, title: String, right: String = "") {
    drawRect(StarwarPx.HeaderTop, Offset(0f, 0f), androidx.compose.ui.geometry.Size(StarwarPx.WIDTH, StarwarLayout.TITLE_H))
    // 底部一条深色分隔线，原版标题栏与内容区之间有明显分界
    drawRect(Color(0xFF105470), Offset(0f, StarwarLayout.TITLE_H - 1f),
        androidx.compose.ui.geometry.Size(StarwarPx.WIDTH, 1f))
    pixelText(font, title, StarwarPx.WIDTH / 2f, 3f, StarwarPx.HeaderText, align = 1)
    if (right.isNotBlank()) {
        pixelText(font, right, StarwarPx.WIDTH - 4f, 3f, StarwarPx.HeaderText, align = 2)
    }
}

// ------------------------------------------------------------------ 软键条

/** 底部软键条：浅灰底，左侧「选择」、右侧「返回」，与原版一字不差 */
fun DrawScope.pixelSoftKeys(
    font: StarwarFont,
    left: String = "选择",
    right: String = "返回",
    center: String = "",
) {
    val top = StarwarPx.HEIGHT - StarwarLayout.SOFTKEY_H
    drawRect(Color(0xFFE0E4E0), Offset(0f, top), androidx.compose.ui.geometry.Size(StarwarPx.WIDTH, StarwarLayout.SOFTKEY_H))
    drawRect(Color(0xFF9AA4A0), Offset(0f, top), androidx.compose.ui.geometry.Size(StarwarPx.WIDTH, 1f))
    pixelText(font, left, 2f, top + 3f, StarwarPx.Text)
    pixelText(font, right, StarwarPx.WIDTH - 2f, top + 3f, StarwarPx.Text, align = 2)
    if (center.isNotBlank()) {
        pixelText(font, center, StarwarPx.WIDTH / 2f, top + 3f, StarwarPx.Text, align = 1)
    }
}

// ------------------------------------------------------------------ 列表

/** 列表行：选中时铺一条浅色高亮，左侧可带 20×20 图标 */
fun DrawScope.pixelListRow(
    font: StarwarFont,
    y: Float,
    text: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.ImageBitmap? = null,
    trailing: String = "",
    trailingColor: Color = StarwarPx.Muted,
) {
    val h = StarwarLayout.ROW_H
    if (selected) {
        drawRect(StarwarPx.Selected, Offset(0f, y), androidx.compose.ui.geometry.Size(StarwarPx.WIDTH, h))
        // 原版选中行左侧有一条深色指示竖条
        drawRect(StarwarPx.HeaderBottom, Offset(0f, y), androidx.compose.ui.geometry.Size(2f, h))
    }
    var textX = 6f
    if (icon != null) {
        pixelSprite(icon, 6f, y + (h - 18f) / 2f, 18f, 18f)
        textX = 28f
    }
    pixelText(font, text, textX, y + 3f, StarwarPx.Text)
    if (trailing.isNotBlank()) {
        pixelText(font, trailing, StarwarPx.WIDTH - 6f, y + 3f, trailingColor, align = 2)
    }
}

/** 列表滚动区域的上下边界裁切，避免内容压到标题栏/软键条 */
fun DrawScope.pixelBodyBackground() {
    drawRect(StarwarPx.Paper, Offset(0f, StarwarLayout.BODY_TOP),
        androidx.compose.ui.geometry.Size(StarwarPx.WIDTH, StarwarLayout.BODY_BOTTOM - StarwarLayout.BODY_TOP))
}

// ------------------------------------------------------------------ 文本块

/** 多行文本（自动折行），返回结束时的 y 坐标 */
fun DrawScope.pixelParagraph(
    font: StarwarFont,
    text: String,
    x: Float,
    y: Float,
    maxWidth: Float,
    color: Color = StarwarPx.Text,
    lineHeight: Float = 17f,
): Float {
    var cy = y
    font.wrap(text, maxWidth).forEach { line ->
        pixelText(font, line, x, cy, color)
        cy += lineHeight
    }
    return cy
}

/** 水平分隔线 */
fun DrawScope.pixelDivider(y: Float, color: Color = Color(0xFFBFC8CC), x0: Float = 0f, x1: Float = StarwarPx.WIDTH) {
    drawRect(color, Offset(x0, y), androidx.compose.ui.geometry.Size(x1 - x0, 1f))
}

/** 纯色矩形填充（便捷方法） */
fun DrawScope.fill(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, h))
}

/** 1 像素描边框 */
fun DrawScope.pixelBorder(x: Float, y: Float, w: Float, h: Float, color: Color) {
    drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(w, 1f))
    drawRect(color, Offset(x, y + h - 1f), androidx.compose.ui.geometry.Size(w, 1f))
    drawRect(color, Offset(x, y), androidx.compose.ui.geometry.Size(1f, h))
    drawRect(color, Offset(x + w - 1f, y), androidx.compose.ui.geometry.Size(1f, h))
}

// ------------------------------------------------------------------ 通用组件

/** 覆盖整屏的半透明遮罩，给弹窗做背景 */
@Composable
fun StarwarPixelScrim(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        StarwarPixelCanvas(Modifier.fillMaxSize()) {
            drawRect(Color(0x99000000))
        }
        content()
    }
}

/**
 * 原版风格对话框：白底、深色标题条、底部「确定 / 返回」两键。
 *
 * <p>用 Canvas 画外框与标题，内容区通过 [content] 绘制。</p>
 */
@Composable
fun StarwarPixelDialog(
    title: String,
    onClose: () -> Unit,
    okText: String? = null,
    onOk: (() -> Unit)? = null,
    cancelText: String = "返回",
    width: Float = 216f,
    height: Float = 220f,
    /** 内容区点击：坐标为相对对话框内容区左上角（标题栏下方）的逻辑坐标 */
    onContentTap: ((Float, Float) -> Unit)? = null,
    content: DrawScope.(StarwarFont) -> Unit,
) {
    val context = LocalContext.current
    val font = remember { StarwarFont.of(context) }
    val x0 = (StarwarPx.WIDTH - width) / 2f
    val y0 = (StarwarPx.HEIGHT - height) / 2f
    // 按钮区高度 + 标题栏高度
    val titleH = 20f
    val buttonH = 24f
    StarwarPixelScrim {
        StarwarPixelCanvas(Modifier.fillMaxSize(), onTap = { tx, ty ->
            // 点对话框外的区域等于返回
            if (tx < x0 || tx > x0 + width || ty < y0 || ty > y0 + height) {
                onClose()
                return@StarwarPixelCanvas
            }
            val by = y0 + height - buttonH
            when {
                // 底部按钮区：「确定」/「返回」
                ty >= by -> {
                    if (tx < x0 + width / 2f) onClose()
                    else onOk?.invoke()
                }
                // 标题栏不响应
                ty < y0 + titleH -> Unit
                // 内容区（坐标相对内容区左上角）
                else -> onContentTap?.invoke(tx - x0, ty - (y0 + titleH))
            }
        }) {
            // 外框
            fill(x0, y0, width, height, StarwarPx.Paper)
            pixelBorder(x0, y0, width, height, Color(0xFF5A6B73))
            // 标题条
            fill(x0, y0, width, titleH, StarwarPx.HeaderTop)
            pixelText(font, title, x0 + width / 2f, y0 + 3f, StarwarPx.HeaderText, align = 1)
            // 内容区（标题下方到按钮上方）
            translate(left = x0, top = y0 + titleH) {
                content(font)
            }
            // 底部按钮条
            val by = y0 + height - buttonH
            pixelDivider(by, x0 = x0, x1 = x0 + width)
            pixelText(font, okText ?: "", x0 + width * 0.25f, by + 5f, StarwarPx.Text, align = 1)
            pixelText(font, cancelText, x0 + width * 0.75f, by + 5f, StarwarPx.Text, align = 1)
            // 中间一条竖分隔
            drawRect(Color(0xFFBFC8CC), Offset(x0 + width / 2f, by), androidx.compose.ui.geometry.Size(1f, buttonH))
        }
    }
}

/** 确认框（确定 / 返回） */
@Composable
fun StarwarPixelConfirm(
    title: String,
    message: String,
    onOk: () -> Unit,
    onCancel: () -> Unit,
) {
    StarwarPixelDialog(title, onCancel, okText = "确定", onOk = onOk, height = 120f) { font ->
        pixelParagraph(font, message, 10f, 14f, 196f)
    }
}

/** 全屏加载提示 */
@Composable
fun StarwarLoadingPixel(font: StarwarFont, text: String) {
    Box(Modifier.fillMaxSize()) {
        StarwarPixelCanvas(Modifier.fillMaxSize()) {
            drawRect(Color(0x66000000))
            fill(20f, 140f, 200f, 36f, StarwarPx.Paper)
            pixelBorder(20f, 140f, 200f, 36f, Color(0xFF5A6B73))
            pixelText(font, text, 120f, 152f, StarwarPx.Text, align = 1)
        }
    }
}
