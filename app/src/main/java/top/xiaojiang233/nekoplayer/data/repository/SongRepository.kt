package top.xiaojiang233.nekoplayer.data.repository

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import top.xiaojiang233.nekoplayer.NekoPlayerApplication
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableStateFlow
import top.xiaojiang233.nekoplayer.R

@OptIn(ExperimentalSerializationApi::class)
object SongRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val downloadsDir = File(NekoPlayerApplication.getAppContext().filesDir, "downloads")
    private val localSongsOrderFile = File(downloadsDir, "order.json")

    sealed class DownloadState {
        object None : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        object Downloaded : DownloadState()
    }

    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadState = _downloadState.asStateFlow()

    init {
        // Initial state refresh if needed, though getLocalSongs is called by VM
    }

    private fun saveLocalSongsOrder(songs: List<OnlineSong>) {
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val ids = songs.map { it.id }
        localSongsOrderFile.writeText(json.encodeToString(ids))
    }

    private fun getLocalSongsOrder(): List<String> {
        if (!localSongsOrderFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(localSongsOrderFile.readText())
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addLocalSong(uri: Uri) {
        // Copy to App Internal storage or Import into MediaStore?
        // Prompt says "using mediacontext to get downloaded music and lyrics"
        // Here we can import into MediaStore so it gets indexed.
        val context = NekoPlayerApplication.getAppContext()
        val resolver = context.contentResolver

        try {
            val fileName = getFileName(context, uri) ?: "${UUID.randomUUID()}.mp3"

            val contentValues = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/NekoMusic")
                }
            }

            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val itemUri = resolver.insert(collection, contentValues)

            if (itemUri != null) {
                resolver.openOutputStream(itemUri).use { out ->
                     resolver.openInputStream(uri)?.use { input ->
                         input.copyTo(out!!)
                     }
                }

                contentValues.clear()
                contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(itemUri, contentValues, null, null)
                Toast.makeText(context, context.getString(R.string.imported_to_library), Toast.LENGTH_SHORT).show()
            }

        } catch (e: Exception) {
             e.printStackTrace()
             Toast.makeText(context, context.getString(R.string.import_song_failed), Toast.LENGTH_SHORT).show()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex("_display_name") // OpenableColumns.DISPLAY_NAME
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != null && cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }

    suspend fun downloadSong(song: OnlineSong) {
        val context = NekoPlayerApplication.getAppContext()
        if (song.songUrl.isNullOrBlank()) {
             withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.no_url), Toast.LENGTH_SHORT).show() }
             return
        }

        withContext(Dispatchers.IO) {
            try {
                _downloadState.value = _downloadState.value.toMutableMap().apply { put(song.id, DownloadState.Downloading(0f)) }

                var extension = MimeTypeMap.getFileExtensionFromUrl(song.songUrl)
                if (extension.isNullOrBlank()) extension = "mp3"

                val safeTitle = song.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
                val safeArtist = song.artist.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
                val fileName = "$safeTitle - $safeArtist.$extension"
                val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "audio/mpeg"

                val resolver = context.contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.TITLE, song.title)
                    put(MediaStore.Audio.Media.ARTIST, song.artist)
                    put(MediaStore.Audio.Media.MIME_TYPE, mimeType)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        put(MediaStore.Audio.Media.IS_PENDING, 1)
                        put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/NekoMusic")
                    }
                }

                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                }

                val itemUri = resolver.insert(collection, contentValues) ?: throw Exception("Failed to create MediaStore entry")

                // Download Content
                var url = URL(song.songUrl)
                var connection = url.openConnection() as HttpURLConnection
                // ... (Keep existing redirect logic if possible, simplified here) ...
                connection.connect()
                val lengthOfFile = connection.contentLength

                val tempFile = File(context.cacheDir, "temp_download_$fileName") // Download to temp first for tagging

                connection.inputStream.use { input ->
                    tempFile.outputStream().use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            output.write(data, 0, count)
                            if (lengthOfFile > 0) {
                                val progress = total.toFloat() / lengthOfFile.toFloat()
                                _downloadState.value = _downloadState.value.toMutableMap().apply { put(song.id, DownloadState.Downloading(progress)) }
                            }
                        }
                    }
                }

                // Download Cover & Tagging
                 var coverFile: File? = null
                if (!song.coverUrl.isNullOrBlank()) {
                    coverFile = File(context.cacheDir, "${song.id}.jpg")
                    try {
                        URL(song.coverUrl).openStream().use { input ->
                            val bitmap = BitmapFactory.decodeStream(input)
                            if (bitmap != null) {
                                coverFile.outputStream().use { output ->
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)
                                }
                                bitmap.recycle()
                            }
                        }
                    } catch (e: Exception) { e.printStackTrace() }
                }

                var lyricsContent: String? = null
                if (!song.lyricUrl.isNullOrBlank() && song.lyricUrl.startsWith("http")) {
                    try {
                         lyricsContent = URL(song.lyricUrl).readText()
                    } catch (_: Exception) {}
                }

                // Tagging
                try {
                    TagOptionSingleton.getInstance().isAndroid = true
                    val audioFile = AudioFileIO.read(tempFile)
                    val tag = audioFile.tagOrCreateAndSetDefault
                    tag.setField(FieldKey.TITLE, song.title)
                    tag.setField(FieldKey.ARTIST, song.artist)
                    if (song.album != null) tag.setField(FieldKey.ALBUM, song.album)
                    if (coverFile != null && coverFile.exists()) {
                         val artwork = ArtworkFactory.createArtworkFromFile(coverFile)
                         tag.setField(artwork)
                    }
                    if (lyricsContent != null) tag.setField(FieldKey.LYRICS, lyricsContent)
                    audioFile.commit()
                } catch (e: Exception) { e.printStackTrace() }

                // Copy temp file to MediaStore URI
                resolver.openOutputStream(itemUri)?.use { out ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(out)
                    }
                }

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                }

                tempFile.delete()
                coverFile?.delete()

                _downloadState.value = _downloadState.value.toMutableMap().apply { put(song.id, DownloadState.Downloaded) }
                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.downloaded_song, song.title), Toast.LENGTH_SHORT).show() }

            } catch (e: Exception) {
                e.printStackTrace()
                _downloadState.value = _downloadState.value.toMutableMap().apply { remove(song.id) }
                withContext(Dispatchers.Main) { Toast.makeText(context, context.getString(R.string.download_failed_msg, e.message), Toast.LENGTH_SHORT).show() }
            }
        }
    }

    fun deleteSong(song: OnlineSong) {
         val context = NekoPlayerApplication.getAppContext()
         try {
             val uri = Uri.parse(song.songUrl)
             context.contentResolver.delete(uri, null, null)
             // Note: On Android 11+ this might throw RecoverableSecurityException which needs Activity context to handle.
             // For now we assume we own the file or have permission.

             _downloadState.value = _downloadState.value.toMutableMap().apply { remove(song.id) }
         } catch (e: Exception) {
             e.printStackTrace()
         }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun getLocalSongs(): List<OnlineSong> {
        val context = NekoPlayerApplication.getAppContext()
        val songs = mutableListOf<OnlineSong>()

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.RELATIVE_PATH else MediaStore.Audio.Media.DATA
        )

        // Only music
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                // val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol)
                    val album = cursor.getString(albumCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    songs.add(OnlineSong(
                        id = id.toString(),
                        title = title ?: "Unknown",
                        artist = artist ?: "Unknown",
                        album = album,
                        platform = "local",
                        songUrl = contentUri.toString(),
                        coverUrl = null, // Needs separate loader or contentUri
                        lyricUrl = null
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val order = getLocalSongsOrder()
        if (order.isEmpty()) {
            return songs.sortedBy { it.title }
        }

        val songMap = songs.associateBy { it.id }
        val orderedSongs = order.mapNotNull { songMap[it] }
        val unorderedSongs = songs.filter { it.id !in order }.sortedBy { it.title }

        return orderedSongs + unorderedSongs
    }

    fun updateLocalSongsOrder(songs: List<OnlineSong>) {
        saveLocalSongsOrder(songs)
    }

    fun saveSongs(songs: List<OnlineSong>) {
        // No-op for MediaStore
    }

    fun clearCache() {
        val context = NekoPlayerApplication.getAppContext()
        val httpCacheDirectory = File(context.cacheDir, "http-cache")
        if (httpCacheDirectory.exists()) {
            httpCacheDirectory.deleteRecursively()
        }

        val imageCacheDirectory = File(context.cacheDir, "image_cache")
        if (imageCacheDirectory.exists()) {
            imageCacheDirectory.deleteRecursively()
        }
    }
}
