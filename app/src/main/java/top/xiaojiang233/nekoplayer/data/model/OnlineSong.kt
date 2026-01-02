package top.xiaojiang233.nekoplayer.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OnlineSong(
    val id: String,
    @SerialName("name") val title: String,
    val artist: String,
    val album: String?,
    val platform: String,
    @SerialName("url") val songUrl: String?,
    @SerialName("pic") val coverUrl: String?,
    @SerialName("lrc") val lyricUrl: String?
)
