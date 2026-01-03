package top.xiaojiang233.nekoplayer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val songIds: List<String>,
    val coverUrl: String? = null
)

