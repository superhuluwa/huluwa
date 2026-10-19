package com.huluwa.android.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object HuluwaColors {
    val Pine = Color(0xFF206C54)
    val PineDark = Color(0xFF194F3E)
    val Lime = Color(0xFFD5E9B8)
    val Paper = Color(0xFFF6F8F5)
    val White = Color.White
    val Ink = Color(0xFF243D32)
    val Muted = Color(0xFF63746A)
    val Line = Color(0xFFE3EAE0)
    val SoftGreen = Color(0xFFE6EFE2)
    val Danger = Color(0xFFB44452)
    val Amber = Color(0xFF956223)
    val Lilac = Color(0xFF8260A1)
    val Blue = Color(0xFF4E78A7)
}
private val Palette = lightColorScheme(
    primary = HuluwaColors.Pine, onPrimary = Color.White, primaryContainer = HuluwaColors.SoftGreen,
    onPrimaryContainer = HuluwaColors.PineDark, secondary = Color(0xFF557C46), secondaryContainer = Color(0xFFE8F0E1),
    background = HuluwaColors.Paper, onBackground = HuluwaColors.Ink, surface = Color.White,
    onSurface = HuluwaColors.Ink, surfaceVariant = Color(0xFFEEF3E9), onSurfaceVariant = HuluwaColors.Muted,
    outline = Color(0xFF83917D), outlineVariant = HuluwaColors.Line, error = HuluwaColors.Danger,
)
@Composable fun HuluwaTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Palette, shapes = Shapes(
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
