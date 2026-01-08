package top.xiaojiang233.nekoplayer.data.repository

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import top.xiaojiang233.nekoplayer.NekoPlayerApplication
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.service.DownloadService
import java.io.File
import java.io.ObjectStreamException
import java.net.HttpURLConnection
import java.net.URL

@OptIn(ExperimentalSerializationApi::class)
@SuppressLint("StaticFieldLeak")
object SongRepository {

    private const val TAG = "SongRepository"
    private val context = NekoPlayerApplication.getAppContext()

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val downloadsDir = File(context.filesDir, "downloads")
    private val localSongsOrderFile = File(downloadsDir, "order.json")
    private val hiddenSongsFile = File(downloadsDir, "hidden.json")
    private val localSongsMetadataFile = File(downloadsDir, "local_metadata.json")

    const val ACTION_DOWNLOAD_STATUS = "top.xiaojiang233.nekoplayer.DOWNLOAD_STATUS"
    const val EXTRA_SONG_ID = "song_id"
    const val EXTRA_STATUS = "status"

    @Serializable
    data class LocalSongMetadata(
        val songId: String,
        val hasCover: Boolean = false,
        val hasLyrics: Boolean = false,
        val title: String? = null,
        val artist: String? = null,
        val platform: String? = null,
        val album: String? = null
    )

    @Serializable
    sealed class DownloadState : java.io.Serializable {
        object None : DownloadState() {
            private fun readResolve(): Any = None
        }
        data class Downloading(val progress: Float) : DownloadState()
        object Downloaded : DownloadState() {
            private fun readResolve(): Any = Downloaded
        }
        data class Failed(val message: String) : DownloadState()
    }

    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadState = _downloadState.asStateFlow()

    private val downloadStatusReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
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
        val filter = IntentFilter(ACTION_DOWNLOAD_STATUS)
        ContextCompat.registerReceiver(
            context,
            downloadStatusReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
    }

    fun createDownloadStatusIntent(songId: String, state: DownloadState): Intent {
        return Intent(ACTION_DOWNLOAD_STATUS).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_SONG_ID, songId)
            putExtra(EXTRA_STATUS, state)
        }
    }

    // --- Local Songs Order ---
    private fun getLocalSongsOrder(): List<String> {
        if (!localSongsOrderFile.exists()) return emptyList()
        return try {
            json.decodeFromString<List<String>>(localSongsOrderFile.readText())
        } catch (e: Exception) {
            Log.e(TAG, "Error reading song order", e)
            emptyList()
        }
    }

    fun updateLocalSongsOrder(songs: List<OnlineSong>) {
        try {
            val ids = songs.map { it.id }
            localSongsOrderFile.writeText(json.encodeToString(ids))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving song order", e)
        }
    }

    // --- Hidden Songs ---
    private fun getHiddenSongs(): MutableSet<String> {
        if (!hiddenSongsFile.exists()) return mutableSetOf()
        return try {
            json.decodeFromString<Set<String>>(hiddenSongsFile.readText()).toMutableSet()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading hidden songs", e)
            mutableSetOf()
        }
    }

    fun setHiddenSongs(hiddenSongIds: Set<String>) {
        try {
            hiddenSongsFile.writeText(json.encodeToString(hiddenSongIds))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving hidden songs", e)
        }
    }

    fun addLocalSong(uri: Uri) {
        if (uri.scheme == "content") {
            try {
                val id = ContentUris.parseId(uri)
                if (id != -1L) {
                    val hidden = getHiddenSongs()
                    if (hidden.remove(id.toString())) {
                        setHiddenSongs(hidden)
                    }
                }
            } catch (e: Exception) {
                // Not a valid ID-based URI or parse failed
            }
        }

        if (uri.scheme == "file" && uri.path != null) {
             android.media.MediaScannerConnection.scanFile(context, arrayOf(uri.path), null, null)
        }
    }

    fun deleteSong(song: OnlineSong) {
        // Add to hidden songs
        val hidden = getHiddenSongs()
        hidden.add(song.id)
        setHiddenSongs(hidden)

        try {
            val url = song.songUrl ?: return
            if (url.startsWith("content://")) {
                 context.contentResolver.delete(Uri.parse(url), null, null)
            } else if (url.startsWith("file://")) {
                val path = Uri.parse(url).path
                if (path != null) {
                    val file = File(path)
                    if (file.exists()) file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting song file", e)
        }
    }

    // --- Metadata ---
    private fun getLocalSongsMetadata(): Map<String, LocalSongMetadata> {
        if (!localSongsMetadataFile.exists()) return emptyMap()
        return try {
            val list = json.decodeFromString<List<LocalSongMetadata>>(localSongsMetadataFile.readText())
            list.associateBy { it.songId }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading metadata", e)
            emptyMap()
        }
    }

    fun saveLocalSongMetadata(metadata: LocalSongMetadata) {
        val allMetadata = getLocalSongsMetadata().toMutableMap()
        val existing = allMetadata[metadata.songId]
        val merged = LocalSongMetadata(
            songId = metadata.songId,
            hasCover = metadata.hasCover || (existing?.hasCover == true),
            hasLyrics = metadata.hasLyrics || (existing?.hasLyrics == true),
            title = metadata.title ?: existing?.title,
            artist = metadata.artist ?: existing?.artist,
            platform = metadata.platform ?: existing?.platform,
            album = metadata.album ?: existing?.album
        )
        allMetadata[metadata.songId] = merged

        try {
            localSongsMetadataFile.writeText(json.encodeToString(allMetadata.values.toList()))
        } catch (e: Exception) {
            Log.e(TAG, "Error saving metadata", e)
        }
    }

    // --- Queries ---
    fun getLocalSongs(): List<OnlineSong> {
        return queryMediaStore()
    }

    fun getAllMediaStoreMusic(): List<OnlineSong> {
        return queryMediaStore(filterHidden = false)
    }

    private fun queryMediaStore(filterHidden: Boolean = true): List<OnlineSong> {
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
            MediaStore.Audio.Media.DATA
        )

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"

        val metadata = getLocalSongsMetadata()
        val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
        val hiddenSongs = if (filterHidden) getHiddenSongs() else emptySet()

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
                    if (filterHidden && id.toString() in hiddenSongs) continue

                    val title = cursor.getString(titleCol)
                    val artist = cursor.getString(artistCol)
                    val album = cursor.getString(albumCol)
                    val contentUri = ContentUris.withAppendedId(collection, id)

                    val meta = metadata[id.toString()]

                    var coverUrl = contentUri.toString()
                    if (meta?.hasCover == true) {
                        val safeTitle = (meta.title ?: title).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val safeArtist = (meta.artist ?: artist).replace(Regex("[\\\\/:*?\"<>|]"), "_")
                        val fileNameBase = "$safeTitle - $safeArtist".trim()
                        val coverFile = File(musicDir, "$fileNameBase.jpg")
                        if (coverFile.exists()) {
                            coverUrl = Uri.fromFile(coverFile).toString()
                        }
                    }

                    songs.add(OnlineSong(
                        id = id.toString(),
                        title = meta?.title ?: title ?: "Unknown",
                        artist = meta?.artist ?: artist ?: "Unknown",
                        album = meta?.album ?: album,
                        platform = meta?.platform ?: "local",
                        songUrl = contentUri.toString(),
                        coverUrl = coverUrl,
                        lyricUrl = null
                    ))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MediaStore", e)
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

    suspend fun updateSongMetadata(song: OnlineSong, selectedMatch: OnlineSong) {
        withContext(Dispatchers.IO) {
            try {
                val safeTitle = song.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeArtist = song.artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val fileNameBase = "$safeTitle - $safeArtist".trim()
                val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: return@withContext

                if (!musicDir.exists()) musicDir.mkdirs()

                var hasLyrics = false
                if (!selectedMatch.lyricUrl.isNullOrBlank()) {
                    try {
                        val lyricsContent = downloadUrlText(selectedMatch.lyricUrl)
                        if (lyricsContent.isNotEmpty()) {
                            val lyricFile = File(musicDir, "$fileNameBase.lrc")
                            lyricFile.writeText(lyricsContent)
                            hasLyrics = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download lyrics", e)
                    }
                }

                var hasCover = false
                if (!selectedMatch.coverUrl.isNullOrBlank()) {
                    try {
                        val bitmap = downloadBitmap(selectedMatch.coverUrl)
                        if (bitmap != null) {
                            val coverFile = File(musicDir, "$fileNameBase.jpg")
                            coverFile.outputStream().use {
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it)
                            }
                            bitmap.recycle()
                            hasCover = true
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to download cover", e)
                    }
                }

                saveLocalSongMetadata(
                    LocalSongMetadata(
                        songId = song.id,
                        title = selectedMatch.title,
                        artist = selectedMatch.artist,
                        album = selectedMatch.album,
                        hasCover = hasCover,
                        hasLyrics = hasLyrics
                    )
                )

            } catch (e: Exception) {
                Log.e(TAG, "Error updating song metadata", e)
            }
        }
    }

    fun downloadSong(song: OnlineSong) {
        val intent = Intent(context, DownloadService::class.java).apply {
            putExtra("song", song)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun downloadUrlText(urlString: String): String {
        var connection: HttpURLConnection? = null
        try {
            var url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true

            var redirects = 0
            while (connection!!.responseCode in 300..399) {
                val newUrl = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                url = URL(newUrl)
                connection = url.openConnection() as HttpURLConnection
                redirects++
                if (redirects > 5) break
            }

            return connection.inputStream.reader().use { it.readText() }
        } finally {
            connection?.disconnect()
        }
    }

    private fun downloadBitmap(urlString: String): Bitmap? {
        var connection: HttpURLConnection? = null
        try {
            var url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = true

            var redirects = 0
            while (connection!!.responseCode in 300..399) {
                val newUrl = connection.getHeaderField("Location") ?: break
                connection.disconnect()
                url = URL(newUrl)
                connection = url.openConnection() as HttpURLConnection
                redirects++
                if (redirects > 5) break
            }

            return connection.inputStream.use { BitmapFactory.decodeStream(it) }
        } finally {
            connection?.disconnect()
        }
    }

    fun saveSongs(songs: List<OnlineSong>) {
        updateLocalSongsOrder(songs)

        // Also try to restore basic metadata if possible from the song objects
        // This is a best-effort since OnlineSong mixes computed/loaded data
        val currentMetadata = getLocalSongsMetadata().toMutableMap()
        var changed = false

        songs.forEach { song ->
            if (song.platform != "local") return@forEach // Only for local songs usually

            // If we have custom cover/lyrics in the backup (indicated by URLs usually pointing to local storage or http)
            // we might want to preserve that knowledge in metadata.
            // However, file paths might have changed if on a different device.
            // But let's assume we are restoring on same device or compatible one.

            val existing = currentMetadata[song.id]
            val hasCover = song.coverUrl != song.songUrl // If cover URL is different from song URL (content://...), it likely has a custom cover
            val hasLyrics = !song.lyricUrl.isNullOrEmpty()

            if (existing == null || existing.hasCover != hasCover || existing.hasLyrics != hasLyrics) {
                currentMetadata[song.id] = LocalSongMetadata(
                    songId = song.id,
                    hasCover = hasCover || (existing?.hasCover == true),
                    hasLyrics = hasLyrics || (existing?.hasLyrics == true),
                    title = song.title,
                    artist = song.artist,
                    album = song.album,
                    platform = song.platform
                )
                changed = true
            }
        }

        if (changed) {
            try {
                localSongsMetadataFile.writeText(json.encodeToString(currentMetadata.values.toList()))
            } catch (e: Exception) {
                Log.e(TAG, "Error saving restored metadata", e)
            }
        }
    }

    fun clearCache() {
        try {
            if (downloadsDir.exists()) {
                val files = downloadsDir.listFiles()
                files?.forEach { file ->
                    // Keep important json files, delete temporary files if any
                    // Actually, "cache" usually means temporary data.
                    // But here downloadsDir seems to hold metadata and config too.
                    // Maybe we should delete the 'downloads' content that are actual media?
                    // But SongRepository stores 'metadata' in downloadsDir.

                    // If the user means "Clear Cache" in settings, typically it means coil image cache or temp lyrics/mp3s.
                    // Coil cache is separate.
                    // If we downloaded temp files...
                    // We don't seem to have a dedicated temp folder tracked here, but we write .lrc and .jpg to musicDir.

                    // Let's assume clearCache clears internal app cache directory
                    context.cacheDir.deleteRecursively()

                    // And maybe keep metadata?
                    // If the user wants to reset app logic, deleting metadata is fine?
                    // "Clear Cache" usually doesn't delete user data like playlists/metadata options.
                    // But if it's "Clear Data"...

                    // Given the context of "NekoPlayer", maybe it refers to cached images/lyrics?
                    // But those are in ExternalFilesDir.

                    // Let's safe-clear standard cache.
                }
            }
            context.cacheDir.deleteRecursively()
            context.codeCacheDir.deleteRecursively()
        } catch (e: Exception) {
            Log.e(TAG, "Error clearing cache", e)
        }
    }

    suspend fun saveEmbeddedCoverForMediaUri(mediaUri: Uri, fileNameBase: String, songId: String): Boolean {
        // Implementation similar to what was likely there before or suitable for this purpose
        return withContext(Dispatchers.IO) {
            try {
                val retriever = android.media.MediaMetadataRetriever()
                retriever.setDataSource(context, mediaUri)
                val pic = retriever.embeddedPicture
                retriever.release()

                if (pic != null) {
                    val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
                    if (musicDir != null) {
                        if (!musicDir.exists()) musicDir.mkdirs()
                        val coverFile = File(musicDir, "$fileNameBase.jpg")
                        val bitmap = BitmapFactory.decodeByteArray(pic, 0, pic.size)

                        if (bitmap != null) {
                            coverFile.outputStream().use { out ->
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                            }
                            bitmap.recycle()

                            // Update metadata to reflect we now have a cover
                            saveLocalSongMetadata(LocalSongMetadata(songId = songId, hasCover = true))
                            return@withContext true
                        }
                    }
                }
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting embedded cover", e)
                false
            }
        }
    }
}
