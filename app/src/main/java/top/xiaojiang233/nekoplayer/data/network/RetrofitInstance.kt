package top.xiaojiang233.nekoplayer.data.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import top.xiaojiang233.nekoplayer.NekoPlayerApplication

object RetrofitInstance {
    private const val BASE_URL = "https://music-dl.sayqz.com/"

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    private val cacheSize = (10 * 1024 * 1024).toLong() // 10 MB
    private val httpCacheDirectory = File(NekoPlayerApplication.getAppContext().cacheDir, "http-cache")
    private val cache = Cache(httpCacheDirectory, cacheSize)

    private val okHttpClient = OkHttpClient.Builder()
        .cache(cache)
        .build()

    private val retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
    }

    val musicApiService: MusicApiService by lazy {
        retrofit.create(MusicApiService::class.java)
    }
}

