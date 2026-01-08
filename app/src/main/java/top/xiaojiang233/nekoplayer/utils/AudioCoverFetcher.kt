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
import org.jaudiotagger.audio.AudioFileIO

class AudioCoverFetcher(
    private val uri: Uri,
    private val options: Options
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val context = options.context

        // Strategy:
        // 1. Try to find sidecar .jpg in DIRECTORY_MUSIC based on metadata
        // 2. Try MediaMetadataRetriever (embedded or system-provided)
        // 3. Try Jaudiotagger for local files (more details)

        val metadata = getMetadataFromUri(context, uri)
        if (metadata != null) {
            val (title, artist) = metadata
            val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileNameBase = "$safeTitle - $safeArtist".trim()
            val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            val sidecarCover = File(musicDir, "$fileNameBase.jpg")

            if (sidecarCover.exists()) {
                val bitmap = BitmapFactory.decodeFile(sidecarCover.absolutePath)
                if (bitmap != null) {
                    return DrawableResult(
                        drawable = BitmapDrawable(context.resources, bitmap),
                        isSampled = false,
                        dataSource = DataSource.DISK
                    )
                }
            }
        }

        // Try Jaudiotagger for local files first (fallback)
        if (uri.scheme == "file") {
            try {
                val path = uri.path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) {
                         try {
                            val audioFile = AudioFileIO.read(file)
                            val artwork = audioFile.tag?.firstArtwork
                            val binaryData = artwork?.binaryData
                            if (binaryData != null) {
                                val bitmap = BitmapFactory.decodeByteArray(binaryData, 0, binaryData.size)
                                return DrawableResult(
                                    drawable = BitmapDrawable(context.resources, bitmap),
                                    isSampled = false,
                                    dataSource = DataSource.DISK
                                )
                            }
                        } catch (e: Exception) {
                            // Jaudiotagger failed, fall back to MediaMetadataRetriever
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                // For content URIs, take extra care
                try {
                    retriever.setDataSource(context, uri)
                } catch (e: Exception) {
                    // This is where status 0x80000000 often happens if file is pending or inaccessible
                    return null
                }
            }
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

    private fun getMetadataFromUri(context: Context, uri: Uri): Pair<String, String>? {
        if (uri.scheme != "content") return null

        try {
            val cursor = context.contentResolver.query(uri, arrayOf(
                android.provider.MediaStore.Audio.Media.TITLE,
                android.provider.MediaStore.Audio.Media.ARTIST
            ), null, null, null)

            cursor?.use { c ->
                if (c.moveToFirst()) {
                    val title = c.getString(0) ?: "Unknown"
                    val artist = c.getString(1) ?: "Unknown"
                    return Pair(title, artist)
                }
            }
        } catch (_: Exception) { }
        return null
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
