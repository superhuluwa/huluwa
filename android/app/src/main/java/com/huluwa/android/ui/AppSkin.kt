package com.huluwa.android.ui

import androidx.compose.ui.graphics.Color

/** 预览使用参考图的浅色组合，正文和操作色使用同色系的深色。 */
data class SkinPalette(
    val primary: Color,
    val primaryDark: Color,
    val accent: Color,
    val background: Color,
    val ink: Color,
    val muted: Color,
    val line: Color,
    val primarySoft: Color,
    val secondary: Color,
    val secondarySoft: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val previewColors: List<Color>,
)

enum class AppSkin(val id: String, val title: String, val description: String, val palette: SkinPalette) {
    Pine("pine", "松柏绿", "原有配色 · 清爽自然", SkinPalette(
        primary = Color(0xFF206C54), primaryDark = Color(0xFF194F3E), accent = Color(0xFFD5E9B8),
        background = Color(0xFFF6F8F5), ink = Color(0xFF243D32), muted = Color(0xFF63746A),
        line = Color(0xFFE3EAE0), primarySoft = Color(0xFFE6EFE2),
        secondary = Color(0xFF557C46), secondarySoft = Color(0xFFE8F0E1),
        surfaceVariant = Color(0xFFEEF3E9), outline = Color(0xFF83917D),
        previewColors = listOf(Color(0xFF206C54), Color(0xFFD5E9B8), Color(0xFFE6EFE2)),
    )),
    SeaSalt("sea_salt", "海盐蓝绿", "晴空蓝 · 薄荷绿 · 湖水青", SkinPalette(
        primary = Color(0xFF3562BD), primaryDark = Color(0xFF254681), accent = Color(0xFF9AD4CB),
        background = Color(0xFFF4F9FC), ink = Color(0xFF223B50), muted = Color(0xFF596E7B),
        line = Color(0xFFDCE8EF), primarySoft = Color(0xFFE2ECFC),
        secondary = Color(0xFF247785), secondarySoft = Color(0xFFDCF1ED),
        surfaceVariant = Color(0xFFECF3F9), outline = Color(0xFF788FA0),
        previewColors = listOf(Color(0xFF598BFD), Color(0xFF9AD4CB), Color(0xFF35B3C9)),
    )),
    Sunset("sunset", "落霞紫粉", "柔雾紫 · 樱花粉 · 蜜桃白", SkinPalette(
        primary = Color(0xFF7742C2), primaryDark = Color(0xFF57308C), accent = Color(0xFFF698B8),
        background = Color(0xFFFBF6FC), ink = Color(0xFF43314E), muted = Color(0xFF76617D),
        line = Color(0xFFEBDFEE), primarySoft = Color(0xFFEEE3FC),
        secondary = Color(0xFFA44870), secondarySoft = Color(0xFFFBE3EC),
        surfaceVariant = Color(0xFFF5ECF8), outline = Color(0xFF96829F),
        previewColors = listOf(Color(0xFF945FFA), Color(0xFFF698B8), Color(0xFFF7DFD9)),
    )),
    Blossom("blossom", "奶油花漾", "淡丁香 · 奶油黄 · 玫瑰粉", SkinPalette(
        primary = Color(0xFF74609E), primaryDark = Color(0xFF51416F), accent = Color(0xFFFEC96D),
        background = Color(0xFFFCF9F4), ink = Color(0xFF493B4C), muted = Color(0xFF786A76),
        line = Color(0xFFECE2E7), primarySoft = Color(0xFFEEE7F8),
        secondary = Color(0xFF946123), secondarySoft = Color(0xFFFFEFCD),
        surfaceVariant = Color(0xFFF5EEF3), outline = Color(0xFF988796),
        previewColors = listOf(Color(0xFFBBADE6), Color(0xFFFEC96D), Color(0xFFE2ADB4)),
    )),
    Coral("coral", "珊瑚暖阳", "珊瑚橘 · 杏桃粉 · 香草奶油", SkinPalette(
        primary = Color(0xFFAC503D), primaryDark = Color(0xFF7C382B), accent = Color(0xFFF0BEA8),
        background = Color(0xFFFFF8F3), ink = Color(0xFF50382F), muted = Color(0xFF806A60),
        line = Color(0xFFF0E1D8), primarySoft = Color(0xFFFBE5DC),
        secondary = Color(0xFF896529), secondarySoft = Color(0xFFFBF1D7),
        surfaceVariant = Color(0xFFFBEEE7), outline = Color(0xFFA3897C),
        previewColors = listOf(Color(0xFFEC826C), Color(0xFFEAB4A6), Color(0xFFFFFADE)),
    ));

    companion object {
        fun fromId(id: String?): AppSkin = entries.firstOrNull { it.id == id } ?: Pine
    }
}
