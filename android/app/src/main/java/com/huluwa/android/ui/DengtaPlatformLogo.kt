package com.huluwa.android.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.OndemandVideo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.huluwa.android.R

private data class DengtaPlaybackPlatform(val code: String, val name: String, val logo: Int, val aliases: List<String>)

/** 图标取自灯塔榜单网页，随 App 打包；平台名称仅用于匹配、筛选和无障碍描述。 */
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

@Composable internal fun DengtaPlatformDropdown(value: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = dengtaPlaybackPlatforms.firstOrNull { it.name == value }
    Box {
        TextButton({ expanded = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
            if (current == null) Text("全部平台", fontSize = 11.sp, maxLines = 1)
            else DengtaPlaybackLogo(current, 22.dp)
            Icon(Icons.Outlined.ExpandMore, "选择播放平台", Modifier.size(14.dp))
        }
        DropdownMenu(expanded, { expanded = false }) {
            DropdownMenuItem(text = { Text("全部平台") }, onClick = { expanded = false; onSelect("全部平台") },
                modifier = Modifier.semantics { selected = current == null },
                trailingIcon = { if (current == null) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = HuluwaColors.Pine) })
            dengtaPlaybackPlatforms.forEach { platform ->
                DropdownMenuItem(text = { DengtaPlaybackLogo(platform, 24.dp) },
                    onClick = { expanded = false; onSelect(platform.name) },
                    modifier = Modifier.semantics { selected = value == platform.name },
                    trailingIcon = { if (value == platform.name) Icon(Icons.Outlined.Check, null, Modifier.size(18.dp), tint = HuluwaColors.Pine) })
            }
        }
    }
}

@Composable private fun DengtaPlaybackLogo(platform: DengtaPlaybackPlatform, size: Dp) {
    Image(painterResource(platform.logo), platform.name, Modifier.size(size), contentScale = ContentScale.Fit)
}
