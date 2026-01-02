package top.xiaojiang233.nekoplayer.data.model

import kotlinx.serialization.Serializable

@Serializable
data class MusicApiResponse(
    val code: Int,
    val message: String,
    val data: SearchData
)

@Serializable
data class SearchData(
    val keyword: String,
    val limit: Int,
    val page: Int,
    val results: List<OnlineSong>
)
