package top.xiaojiang233.nekoplayer.utils

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import java.io.File

class AudioCoverFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(options.context, uri)
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
            e.printStackTrace()
            null
        } finally {
            retriever.release()
        }
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            // Check if it looks like an audio file content URI or file URI
            // Simplified check: if scheme is content, we assume it might be audio if we are asked to load it
            // Or we can rely on caller.
            // But to avoid interfering with other images, maybe check type?
            // For now, let's assume this fetcher is used specifically for audio URIs passed as models.
            // However, Coil will try all factories.
            // If data is a content URI for an image, MediaMetadataRetriever might fail or default.
            // Safest: Check mime type if possible, or extension if file uri.
            val scheme = data.scheme
            if (scheme == "content") {
                val type = options.context.contentResolver.getType(data)
                if (type?.startsWith("audio/") == true) {
                    return AudioCoverFetcher(data, options)
                }
            } else if (scheme == "file") {
                val path = data.path ?: ""
                val extension = File(path).extension.lowercase()
                if (extension in listOf("mp3", "flac", "m4a", "wav", "ogg")) {
                    return AudioCoverFetcher(data, options)
                }
            }
            return null
        }
    }
}
