package top.xiaojiang233.nekoplayer.data.model

import android.net.Uri

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumArtUri: Uri?,
    val mediaUri: Uri
)
