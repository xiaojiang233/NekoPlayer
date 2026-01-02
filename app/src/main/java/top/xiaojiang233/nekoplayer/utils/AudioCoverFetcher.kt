package top.xiaojiang233.nekoplayer.utils

import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File

class AudioCoverFetcher(
    private val file: File,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val coverBytes = retriever.embeddedPicture
            if (coverBytes != null) {
                val bitmap = BitmapFactory.decodeByteArray(coverBytes, 0, coverBytes.size)
                DrawableResult(
                    drawable = BitmapDrawable(options.context.resources, bitmap),
                    isSampled = false,
                    dataSource = DataSource.DISK
                )
            } else {
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            retriever.release()
        }
    }

    class Factory : Fetcher.Factory<File> {
        override fun create(data: File, options: Options, imageLoader: ImageLoader): Fetcher? {
            val extension = data.extension.lowercase()
            if (extension in listOf("mp3", "flac", "m4a", "wav", "ogg")) {
                return AudioCoverFetcher(data, options)
            }
            return null
        }
    }
}

