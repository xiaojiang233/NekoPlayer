package top.xiaojiang233.nekoplayer.data.repository

import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
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

@OptIn(ExperimentalSerializationApi::class)
object SongRepository {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private val downloadsDir = File(NekoPlayerApplication.getAppContext().filesDir, "downloads")

    private val publicMusicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NekoMusic/Music")
    private val publicLyricDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NekoMusic/Lyric")

    sealed class DownloadState {
        object None : DownloadState()
        data class Downloading(val progress: Float) : DownloadState()
        object Downloaded : DownloadState()
    }

    private val _downloadState = MutableStateFlow<Map<String, DownloadState>>(emptyMap())
    val downloadState = _downloadState.asStateFlow()

    init {
        refreshLocalSongsState()
    }

    private fun refreshLocalSongsState() {
        val localSongs = getLocalSongs()
        val stateMap = localSongs.associate { it.id to DownloadState.Downloaded }
        _downloadState.value = stateMap
    }

    fun addLocalSong(uri: Uri) {
        val context = NekoPlayerApplication.getAppContext()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE) ?: "Unknown Title"
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST) ?: "Unknown Artist"
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)

            if (!publicMusicDir.exists()) publicMusicDir.mkdirs()

            // Copy file to NekoMusic/Music
            val fileName = "${UUID.randomUUID()}.mp3" // Or try to get original name
            val destFile = File(publicMusicDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                destFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            val newSong = OnlineSong(
                id = UUID.randomUUID().toString(),
                title = title,
                artist = artist,
                album = album,
                platform = "local",
                songUrl = destFile.absolutePath,
                coverUrl = null,
                lyricUrl = null
            )

            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val metadataFile = File(downloadsDir, "${newSong.id}.json")
            metadataFile.writeText(json.encodeToString(newSong))
            refreshLocalSongsState()

        } catch (_: Exception) {
            Toast.makeText(context, "Failed to add song", Toast.LENGTH_SHORT).show()
        } finally {
            retriever.release()
        }
    }

    suspend fun downloadSong(song: OnlineSong) {
        val context = NekoPlayerApplication.getAppContext()
        if (song.songUrl.isNullOrBlank()) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Download failed: No song URL", Toast.LENGTH_SHORT).show()
            }
            return
        }

        _downloadState.value = _downloadState.value.toMutableMap().apply {
            put(song.id, DownloadState.Downloading(0f))
        }

        withContext(Dispatchers.IO) {
            try {
                if (!publicMusicDir.exists()) publicMusicDir.mkdirs()
                if (!publicLyricDir.exists()) publicLyricDir.mkdirs()

                var extension = MimeTypeMap.getFileExtensionFromUrl(song.songUrl)
                if (extension.isNullOrBlank()) {
                    extension = "mp3"
                }

                val safeTitle = song.title.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
                val safeArtist = song.artist.replace("[\\\\/:*?\"<>|]".toRegex(), "_").trim()
                val songFileName = "$safeTitle - $safeArtist.$extension"

                val songFile = File(publicMusicDir, songFileName)

                // Download Song with progress and redirect handling
                var url = URL(song.songUrl)
                var connection = url.openConnection() as HttpURLConnection
                var redirects = 0
                while (redirects < 10) {
                    connection.instanceFollowRedirects = false // Handle redirects manually
                    connection.connect()
                    val responseCode = connection.responseCode
                    if (responseCode in 300..399) {
                        val location = connection.getHeaderField("Location")
                        if (location != null) {
                            url = URL(url, location) // Handle relative URLs
                            connection = url.openConnection() as HttpURLConnection
                            redirects++
                            continue
                        }
                    }
                    break
                }

                if (connection.responseCode !in 200..299) {
                    throw Exception("Server returned HTTP ${connection.responseCode}")
                }

                val contentType = connection.contentType
                if (contentType != null && (contentType.startsWith("text/") || contentType.contains("html"))) {
                     throw Exception("Invalid content type: $contentType")
                }

                val lengthOfFile = connection.contentLength

                connection.inputStream.use { input ->
                    songFile.outputStream().use { output ->
                        val data = ByteArray(4096)
                        var total: Long = 0
                        var count: Int
                        while (input.read(data).also { count = it } != -1) {
                            total += count
                            output.write(data, 0, count)
                            if (lengthOfFile > 0) {
                                val progress = total.toFloat() / lengthOfFile.toFloat()
                                _downloadState.value = _downloadState.value.toMutableMap().apply {
                                    put(song.id, DownloadState.Downloading(progress))
                                }
                            }
                        }
                    }
                }

                if (songFile.length() == 0L) {
                    songFile.delete()
                    throw Exception("Downloaded file is empty")
                }

                // Download Cover (Temp)
                var coverFile: File? = null
                if (!song.coverUrl.isNullOrBlank()) {
                    coverFile = File(NekoPlayerApplication.getAppContext().cacheDir, "${song.id}.jpg")
                    URL(song.coverUrl).openStream().use { input ->
                        coverFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Download Lyrics
                var lyricsContent: String? = null
                var lrcFile: File? = null
                if (!song.lyricUrl.isNullOrBlank()) {
                    if (song.lyricUrl.startsWith("http")) {
                        lyricsContent = URL(song.lyricUrl).readText()
                        val lrcFileName = "${song.title} - ${song.artist}.lrc".replace("[\\\\/:*?\"<>|]".toRegex(), "_")
                        lrcFile = File(publicLyricDir, lrcFileName)
                        lrcFile.writeText(lyricsContent)
                    }
                }

                // Tagging
                try {
                    TagOptionSingleton.getInstance().isAndroid = true
                    val audioFile = AudioFileIO.read(songFile)
                    val tag = audioFile.tagOrCreateAndSetDefault

                    tag.setField(FieldKey.TITLE, song.title)
                    tag.setField(FieldKey.ARTIST, song.artist)
                    if (song.album != null) {
                        tag.setField(FieldKey.ALBUM, song.album)
                    }

                    if (coverFile != null && coverFile.exists()) {
                        val artwork = ArtworkFactory.createArtworkFromFile(coverFile)
                        tag.setField(artwork)
                    }

                    if (lyricsContent != null) {
                        tag.setField(FieldKey.LYRICS, lyricsContent)
                    }

                    audioFile.commit()
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    coverFile?.delete()
                }

                // Save metadata JSON for app logic (pointing to local file)
                val songWithLocalPaths = song.copy(
                    songUrl = songFile.absolutePath,
                    coverUrl = null, // Embedded
                    lyricUrl = lrcFile?.absolutePath // Point to local LRC file
                )

                if (!downloadsDir.exists()) {
                    downloadsDir.mkdirs()
                }
                val metadataFile = File(downloadsDir, "${song.id}.json")
                metadataFile.writeText(json.encodeToString(songWithLocalPaths))

                _downloadState.value = _downloadState.value.toMutableMap().apply {
                    put(song.id, DownloadState.Downloaded)
                }

                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Downloaded '${song.title}'", Toast.LENGTH_SHORT).show()
                }

            } catch (e: Exception) {
                e.printStackTrace()
                _downloadState.value = _downloadState.value.toMutableMap().apply {
                    remove(song.id)
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteSong(song: OnlineSong) {
        val metadataFile = File(downloadsDir, "${song.id}.json")
        if (metadataFile.exists()) {
            metadataFile.delete()
        }

        // Avoid deleting files managed by MediaStore
        song.songUrl?.let { if (!it.startsWith("content://")) File(it).delete() }
        song.coverUrl?.let { File(it).delete() }
        song.lyricUrl?.let { File(it).delete() }

        _downloadState.value = _downloadState.value.toMutableMap().apply {
            remove(song.id)
        }
    }

    @OptIn(ExperimentalSerializationApi::class)
    fun getLocalSongs(): List<OnlineSong> {
        if (!downloadsDir.exists()) return emptyList()

        return downloadsDir.listFiles { _, name -> name.endsWith(".json") }?.mapNotNull {
            try {
                json.decodeFromStream<OnlineSong>(it.inputStream())
            } catch (_: Exception) {
                null
            }
        }?.sortedBy { it.title } ?: emptyList()
    }

    fun clearCache() {
        val context = NekoPlayerApplication.getAppContext()
        val httpCacheDirectory = File(context.cacheDir, "http-cache")
        httpCacheDirectory.deleteRecursively()

        if (downloadsDir.exists()) {
            downloadsDir.deleteRecursively()
        }

        context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)?.deleteRecursively()
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.deleteRecursively()
        context.getExternalFilesDir("lyrics")?.deleteRecursively()
    }
}
