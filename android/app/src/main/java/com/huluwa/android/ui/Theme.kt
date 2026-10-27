package com.huluwa.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 保留原有取色入口，通过快照状态让已有页面随皮肤变化重新绘制。 */
object HuluwaColors {
    var skin by mutableStateOf(AppSkin.Pine)
        private set

    fun selectSkin(value: AppSkin) { skin = value }

    val Pine get() = skin.palette.primary
    val PineDark get() = skin.palette.primaryDark
    val Lime get() = skin.palette.accent
    val Paper get() = skin.palette.background
    val White = Color.White
    val Ink get() = skin.palette.ink
    val Muted get() = skin.palette.muted
    val Line get() = skin.palette.line
    val SoftGreen get() = skin.palette.primarySoft
    val ChatBubble get() = if (skin == AppSkin.Pine) Color(0xFFDFEBD4) else SoftGreen
    // 错误、警告和业务分类色保留语义，不随外观改变。
    val Danger = Color(0xFFB44452)
    val Amber = Color(0xFF956223)
    val Lilac = Color(0xFF8260A1)
    val Blue = Color(0xFF4E78A7)
}

/** 新皮肤叠加低浓度弥散光晕，原有皮肤继续使用原来的纯色背景。 */
internal fun Modifier.skinBackground(base: Color = HuluwaColors.Paper): Modifier {
    val skin = HuluwaColors.skin
    val background = background(base)
    if (skin == AppSkin.Pine) return background
    val colors = skin.palette.previewColors
    return background.drawWithCache {
        val upperGlow = Brush.radialGradient(
            colors = listOf(colors[0].copy(alpha = .12f), Color.Transparent),
            center = Offset(size.width * .05f, size.height * .08f),
            radius = size.maxDimension.coerceAtLeast(1f) * .78f,
        )
        val lowerGlow = Brush.radialGradient(
            colors = listOf(colors[1].copy(alpha = .18f), Color.Transparent),
            center = Offset(size.width * .95f, size.height * .72f),
            radius = size.maxDimension.coerceAtLeast(1f) * .7f,
        )
        onDrawBehind {
            drawRect(upperGlow)
            drawRect(lowerGlow)
        }
    }
}

@Composable fun HuluwaTheme(content: @Composable () -> Unit) {
    val skin = HuluwaColors.skin
    val palette = remember(skin) {
        val colors = skin.palette
        if (skin == AppSkin.Pine) lightColorScheme(
            primary = colors.primary, onPrimary = Color.White, primaryContainer = colors.primarySoft,
            onPrimaryContainer = colors.primaryDark, secondary = colors.secondary, secondaryContainer = colors.secondarySoft,
            background = colors.background, onBackground = colors.ink, surface = Color.White,
            onSurface = colors.ink, surfaceVariant = colors.surfaceVariant, onSurfaceVariant = colors.muted,
            outline = colors.outline, outlineVariant = colors.line, error = HuluwaColors.Danger,
        ) else lightColorScheme(
            primary = colors.primary, onPrimary = Color.White, primaryContainer = colors.primarySoft,
            onPrimaryContainer = colors.primaryDark, secondary = colors.secondary,
            onSecondary = Color.White, secondaryContainer = colors.secondarySoft,
            onSecondaryContainer = colors.ink,
            tertiary = colors.secondary, onTertiary = Color.White,
            tertiaryContainer = colors.secondarySoft, onTertiaryContainer = colors.ink,
            background = colors.background, onBackground = colors.ink, surface = Color.White,
            onSurface = colors.ink, surfaceVariant = colors.surfaceVariant, onSurfaceVariant = colors.muted,
            outline = colors.outline, outlineVariant = colors.line, error = HuluwaColors.Danger,
            surfaceTint = colors.primary,
            surfaceDim = colors.line, surfaceBright = Color.White,
            surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
            surfaceContainer = colors.background, surfaceContainerHigh = colors.surfaceVariant,
            surfaceContainerHighest = colors.primarySoft,
            inverseSurface = colors.ink, inverseOnSurface = Color.White, inversePrimary = colors.accent,
        )
    }
    MaterialTheme(colorScheme = palette, shapes = Shapes(
        extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(21.dp), extraLarge = RoundedCornerShape(28.dp)),
        typography = Typography(
            headlineMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 28.sp, lineHeight = 36.sp),
            titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 29.sp),
            titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 23.sp),
            bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 24.sp),
            bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 21.sp),
            bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp),
            labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 19.sp),
            labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 17.sp),
            labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 15.sp),
        ), content = content)
}
