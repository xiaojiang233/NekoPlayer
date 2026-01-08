package top.xiaojiang233.nekoplayer.data.repository

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.ContextCompat
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
    private val hiddenSongsFile = File(downloadsDir, "hidden.json")
    private val localSongsMetadataFile = File(downloadsDir, "local_metadata.json")

    const val ACTION_DOWNLOAD_STATUS = "top.xiaojiang233.nekoplayer.DOWNLOAD_STATUS"
    const val EXTRA_SONG_ID = "song_id"
    const val EXTRA_STATUS = "status"

    @kotlinx.serialization.Serializable
    data class LocalSongMetadata(
        val songId: String,
        val hasCover: Boolean = false,
        val hasLyrics: Boolean = false
    )

    sealed class DownloadState : java.io.Serializable {
        object None : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        object Downloaded : DownloadState()
        data class Failed(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadState = _downloadState.asStateFlow()

    private val downloadStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: android.content.Intent?) {
            if (intent?.action == ACTION_DOWNLOAD_STATUS) {
                val songId = intent.getStringExtra(EXTRA_SONG_ID) ?: return
                val status = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getSerializableExtra(EXTRA_STATUS, DownloadState::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getSerializableExtra(EXTRA_STATUS) as? DownloadState
                } ?: return

                _downloadState.value = _downloadState.value.toMutableMap().apply {
                    this[songId] = status
                }
            }
        }
    }

    init {
        // Register broadcast receiver for download status updates
        val filter = IntentFilter(ACTION_DOWNLOAD_STATUS)
        ContextCompat.registerReceiver(
            NekoPlayerApplication.getAppContext(),
            downloadStatusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    fun createDownloadStatusIntent(songId: String, state: DownloadState): android.content.Intent {
        return android.content.Intent(ACTION_DOWNLOAD_STATUS).apply {
            putExtra(EXTRA_SONG_ID, songId)
            putExtra(EXTRA_STATUS, state)
        }
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

    private fun getHiddenSongs(): MutableSet<String> {
        if (!hiddenSongsFile.exists()) return mutableSetOf()
        return try {
            json.decodeFromString<Set<String>>(hiddenSongsFile.readText()).toMutableSet()
        } catch (e: Exception) {
            mutableSetOf()
        }
    }

    private fun saveHiddenSongs(hiddenSongs: Set<String>) {
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        hiddenSongsFile.writeText(json.encodeToString(hiddenSongs))
    }

    fun setHiddenSongs(hiddenSongIds: Set<String>) {
        saveHiddenSongs(hiddenSongIds)
    }

    private fun getLocalSongsMetadata(): Map<String, LocalSongMetadata> {
        if (!localSongsMetadataFile.exists()) return emptyMap()
        return try {
            val list = json.decodeFromString<List<LocalSongMetadata>>(localSongsMetadataFile.readText())
            list.associateBy { it.songId }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyMap()
        }
    }

    private fun saveLocalSongMetadata(metadata: LocalSongMetadata) {
        val allMetadata = getLocalSongsMetadata().toMutableMap()
        val existing = allMetadata[metadata.songId]
        val merged = LocalSongMetadata(
            songId = metadata.songId,
            hasCover = metadata.hasCover || (existing?.hasCover == true),
            hasLyrics = metadata.hasLyrics || (existing?.hasLyrics == true)
        )
        allMetadata[metadata.songId] = merged

        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        localSongsMetadataFile.writeText(json.encodeToString(allMetadata.values.toList()))
    }

    suspend fun saveEmbeddedCoverForMediaUri(mediaUri: Uri, fileNameBase: String, songId: String): Boolean {
        val context = NekoPlayerApplication.getAppContext()
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, mediaUri)
            val pic = retriever.embeddedPicture
            retriever.release()
            if (pic != null) {
                val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                if (musicDir != null && !musicDir.exists()) musicDir.mkdirs()
                val coverFile = File(musicDir, "$fileNameBase.jpg")
                try {
                    val bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size)
                    if (bitmap != null) {
                        coverFile.outputStream().use { out ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                        }
                        bitmap.recycle()
                        saveLocalSongMetadata(LocalSongMetadata(songId = songId, hasCover = true, hasLyrics = false))
                        return true
                    }
                } catch (_: Exception) { }
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    fun getLocalSongs(): List<OnlineSong> {
        val context = NekoPlayerApplication.getAppContext()
        val songs = mutableListOf<OnlineSong>()

        android.util.Log.d("SongRepository", "getLocalSongs called")

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

        android.util.Log.d("SongRepository", "Using collection URI: $collection")

        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        try {
            android.util.Log.d("SongRepository", "Starting MediaStore query")
            context.contentResolver.query(
                collection,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )?.use { cursor ->
                android.util.Log.d("SongRepository", "Query successful, cursor count: ${cursor.count}")
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)

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
                        coverUrl = contentUri.toString(),
                        lyricUrl = null
                    ))
                }
                android.util.Log.d("SongRepository", "Processed ${songs.size} songs from cursor")
            } ?: run {
                android.util.Log.e("SongRepository", "Query returned null cursor")
            }
        } catch (e: Exception) {
            android.util.Log.e("SongRepository", "Error querying MediaStore", e)
            e.printStackTrace()
        }

        android.util.Log.d("SongRepository", "Total songs before filtering: ${songs.size}")

        val hiddenSongs = getHiddenSongs()
        android.util.Log.d("SongRepository", "Hidden songs count: ${hiddenSongs.size}")
        val visibleSongs = songs.filter { it.id !in hiddenSongs }

        android.util.Log.d("SongRepository", "Visible songs after filtering: ${visibleSongs.size}")

        val metadata = getLocalSongsMetadata()
        val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)

        val songsWithMetadata = visibleSongs.map { song ->
            val meta = metadata[song.id]
            if (meta != null && meta.hasCover) {
                val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val fileNameBase = "$safeTitle - $safeArtist".trim()
                val coverFile = File(musicDir, "$fileNameBase.jpg")

                if (coverFile.exists()) {
                    song.copy(coverUrl = Uri.fromFile(coverFile).toString())
                } else {
                    song
                }
            } else {
                song
            }
        }

        val order = getLocalSongsOrder()
        if (order.isEmpty()) {
            return songsWithMetadata.sortedBy { it.title }
        }

        val songMap = songsWithMetadata.associateBy { it.id }
        val orderedSongs = order.mapNotNull { songMap[it] }
        val unorderedSongs = songsWithMetadata.filter { it.id !in order }.sortedBy { it.title }

        return orderedSongs + unorderedSongs
    }

    fun updateLocalSongsOrder(songs: List<OnlineSong>) {
        saveLocalSongsOrder(songs)
    }

    fun getAllMediaStoreMusic(): List<OnlineSong> {
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
            MediaStore.Audio.Media.ALBUM
        )

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
                        coverUrl = contentUri.toString(),
                        lyricUrl = null
                    ))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return songs.sortedBy { it.title }
    }

    suspend fun updateSongMetadata(song: OnlineSong, selectedMatch: OnlineSong) {
        val context = NekoPlayerApplication.getAppContext()

        withContext(Dispatchers.IO) {
            try {
                val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val fileNameBase = "$safeTitle - $safeArtist".trim()

                val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)

                var hasLyrics = false
                var hasCover = false

                if (!selectedMatch.lyricUrl.isNullOrBlank() && selectedMatch.lyricUrl.startsWith("http")) {
                    try {
                        val lyricsContent = URL(selectedMatch.lyricUrl).readText()
                        val lrcFile = File(musicDir, "$fileNameBase.lrc")
                        lrcFile.writeText(lyricsContent)
                        hasLyrics = true
                    } catch (_: Exception) { }
                }

                if (!selectedMatch.coverUrl.isNullOrBlank()) {
                    try {
                        val coverFile = File(musicDir, "$fileNameBase.jpg")
                        val url = URL(selectedMatch.coverUrl)
                        val connection = url.openConnection() as HttpURLConnection
                        connection.instanceFollowRedirects = true
                        connection.connect()
                        if (connection.responseCode in 200..299) {
                            connection.inputStream.use { input ->
                                val bitmap = BitmapFactory.decodeStream(input)
                                if (bitmap != null) {
                                    coverFile.outputStream().use { output ->
                                        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, output)
                                    }
                                    bitmap.recycle()
                                    hasCover = true
                                }
                            }
                        }
                    } catch (_: Exception) { }
                }

                if (hasLyrics || hasCover) {
                    val metadata = LocalSongMetadata(
                        songId = song.id,
                        hasCover = hasCover,
                        hasLyrics = hasLyrics
                    )
                    saveLocalSongMetadata(metadata)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.config_imported_success).replace("Configuration imported", "Metadata saved"), Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error saving metadata: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteSong(song: OnlineSong) {
        val context = NekoPlayerApplication.getAppContext()
        try {
            val uri = Uri.parse(song.songUrl)
            try {
                context.contentResolver.delete(uri, null, null)
            } catch (e: Exception) {
                e.printStackTrace()
                val hidden = getHiddenSongs()
                hidden.add(song.id)
                saveHiddenSongs(hidden)
            }

            _downloadState.value = _downloadState.value.toMutableMap().apply { remove(song.id) }

            val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
            val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val fileNameBase = "$safeTitle - $safeArtist".trim()
            val lyricFile = File(musicDir, "$fileNameBase.lrc")
            if (lyricFile.exists()) {
                lyricFile.delete()
            }
            val coverFile = File(musicDir, "$fileNameBase.jpg")
            if (coverFile.exists()) {
                coverFile.delete()
            }

            val allMetadata = getLocalSongsMetadata().toMutableMap()
            allMetadata.remove(song.id)
            if (!downloadsDir.exists()) downloadsDir.mkdirs()
            localSongsMetadataFile.writeText(json.encodeToString(allMetadata.values.toList()))
        } catch (e: Exception) {
            e.printStackTrace()
        }
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

    suspend fun addLocalSong(uri: Uri) {
        // Import a song from URI into MediaStore
        val context = NekoPlayerApplication.getAppContext()
        val resolver = context.contentResolver

        try {
            val fileName = uri.pathSegments.lastOrNull() ?: "${System.currentTimeMillis()}.mp3"

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

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    contentValues.clear()
                    contentValues.put(MediaStore.Audio.Media.IS_PENDING, 0)
                    resolver.update(itemUri, contentValues, null, null)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun downloadSong(song: OnlineSong) {
        val context = NekoPlayerApplication.getAppContext()
        val intent = android.content.Intent(context, top.xiaojiang233.nekoplayer.service.DownloadService::class.java).apply {
            putExtra("EXTRA_SONG", song)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun saveSongs(songs: List<OnlineSong>) {
        // This method is a no-op for MediaStore-based implementation
        // Songs are automatically saved to MediaStore when downloaded/imported
        android.util.Log.d("SongRepository", "saveSongs called with ${songs.size} songs (no-op for MediaStore)")
    }
}
