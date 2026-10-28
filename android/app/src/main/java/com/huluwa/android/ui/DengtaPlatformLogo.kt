package com.huluwa.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.huluwa.android.R

private data class DengtaPlaybackPlatform(val code: String, val name: String, val logo: Int, val aliases: List<String>)

/** 图标取自灯塔榜单网页，随 App 打包；平台名称仅用于匹配和无障碍描述。 */
private val dengtaPlaybackPlatforms = listOf(
    DengtaPlaybackPlatform("youku", "优酷", R.drawable.dengta_platform_youku, listOf("优酷", "youku")),
    DengtaPlaybackPlatform("iqiyi", "爱奇艺", R.drawable.dengta_platform_iqiyi, listOf("爱奇艺", "iqiyi")),
    DengtaPlaybackPlatform("tencent", "腾讯视频", R.drawable.dengta_platform_tencent, listOf("腾讯", "tencent")),
    DengtaPlaybackPlatform("mango", "芒果TV", R.drawable.dengta_platform_mango, listOf("芒果", "mango")),
    DengtaPlaybackPlatform("bilibili", "哔哩哔哩", R.drawable.dengta_platform_bilibili, listOf("哔哩哔哩", "B站", "bilibili")),
)

@Composable internal fun DengtaPlatformLogos(codes: String, names: String) {
    val platforms = remember(codes, names) {
        codes.split(',').map(String::trim).filter(String::isNotBlank).mapNotNull { code ->
            dengtaPlaybackPlatforms.firstOrNull { it.code.equals(code, ignoreCase = true) }
        }.distinctBy { it.code }.ifEmpty {
            dengtaPlaybackPlatforms.filter { platform -> platform.aliases.any { names.contains(it, ignoreCase = true) } }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        if (platforms.isEmpty()) Icon(Icons.Outlined.OndemandVideo, "播出平台待公布", Modifier.size(14.dp), tint = HuluwaColors.Muted)
        else platforms.forEach { DengtaPlaybackLogo(it, 14.dp) }
    }
}

@Composable private fun DengtaPlaybackLogo(platform: DengtaPlaybackPlatform, size: Dp) {
    Image(painterResource(platform.logo), platform.name, Modifier.size(size), contentScale = ContentScale.Fit)
}
