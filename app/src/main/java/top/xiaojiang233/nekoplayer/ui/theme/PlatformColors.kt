package top.xiaojiang233.nekoplayer.ui.theme

import androidx.compose.ui.graphics.Color

fun getPlatformColor(platform: String): Color {
    return when (platform.lowercase()) {
        "netease" -> Color(0xFFC20C0C)
        "qq" -> Color(0xFFFFEB3B)
        "kuwo" -> Color(0xFF2196F3)
        else -> Color.Gray
    }
}

