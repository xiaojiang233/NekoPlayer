package top.xiaojiang233.nekoplayer.data.network

import retrofit2.http.GET
import retrofit2.http.Query
import top.xiaojiang233.nekoplayer.data.model.MusicApiResponse

interface MusicApiService {
    @GET("api/?type=aggregateSearch")
    suspend fun searchSongs(
        @Query("keyword") keyword: String,
        @Query("limit") limit: Int = 20, // Default to 20 results per page
        @Query("page") page: Int = 1
    ): MusicApiResponse
}
