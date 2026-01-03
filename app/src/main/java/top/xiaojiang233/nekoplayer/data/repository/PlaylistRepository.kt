package top.xiaojiang233.nekoplayer.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Environment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import top.xiaojiang233.nekoplayer.NekoPlayerApplication
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.model.Playlist
import java.io.File
import java.util.UUID

object PlaylistRepository {
    private val context = NekoPlayerApplication.getAppContext()
    private val playlistsDir = File(context.filesDir, "playlists")
    private val playlistCoversDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "NekoMusic/CoverImage")
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists = _playlists.asStateFlow()

    init {
        if (!playlistsDir.exists()) playlistsDir.mkdirs()
        if (!playlistCoversDir.exists()) playlistCoversDir.mkdirs()
        loadPlaylists()
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun loadPlaylists() {
        val order = loadPlaylistOrder()
        val loaded = playlistsDir.listFiles { _, name -> name.endsWith(".json") && name != "order.json" }?.mapNotNull {
            try {
                json.decodeFromStream<Playlist>(it.inputStream())
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        } ?: emptyList()

        val sorted = if (order.isNotEmpty()) {
            loaded.sortedBy { order.indexOf(it.id).takeIf { idx -> idx != -1 } ?: Int.MAX_VALUE }
        } else {
            loaded.sortedBy { it.name }
        }
        _playlists.value = sorted
    }

    fun createPlaylist(name: String, songs: List<OnlineSong> = emptyList()): Playlist {
        val id = UUID.randomUUID().toString()
        val playlist = Playlist(
            id = id,
            name = name,
            songIds = songs.map { it.id }
        )
        savePlaylist(playlist)
        if (songs.isNotEmpty()) {
            updatePlaylistCover(playlist, songs)
        }
        loadPlaylists()
        return playlist
    }

    fun deletePlaylist(playlist: Playlist) {
        File(playlistsDir, "${playlist.id}.json").delete()
        File(playlistCoversDir, "${playlist.id}.jpg").delete()
        loadPlaylists()
    }

    fun renamePlaylist(playlist: Playlist, newName: String) {
        val updated = playlist.copy(name = newName)
        savePlaylist(updated)
        loadPlaylists()
    }

    fun addSongsToPlaylist(playlist: Playlist, songs: List<OnlineSong>) {
        val updatedSongIds = (playlist.songIds + songs.map { it.id }).distinct()
        val updated = playlist.copy(songIds = updatedSongIds)
        savePlaylist(updated)

        // Update cover if needed (e.g. if it was empty or we want to refresh)
        // For now, let's update cover every time we add songs if it's less than 4 or just refresh it
        // To get song objects from IDs, we need SongRepository.
        // But SongRepository depends on us? No. We depend on SongRepository?
        // Circular dependency risk if we use SongRepository here directly if SongRepository uses PlaylistRepository.
        // SongRepository doesn't seem to use PlaylistRepository.

        val allSongs = SongRepository.getLocalSongs().filter { it.id in updatedSongIds }
        updatePlaylistCover(updated, allSongs)

        loadPlaylists()
    }

    fun removeSongFromPlaylist(playlist: Playlist, songId: String) {
        val updatedSongIds = playlist.songIds - songId
        val updated = playlist.copy(songIds = updatedSongIds)
        savePlaylist(updated)

        val allSongs = SongRepository.getLocalSongs().filter { it.id in updatedSongIds }
        updatePlaylistCover(updated, allSongs)

        loadPlaylists()
    }

    fun updatePlaylistsOrder(playlists: List<Playlist>) {
        _playlists.value = playlists
        savePlaylistOrder(playlists.map { it.id })
    }

    fun updateSongsOrder(playlist: Playlist, newSongIds: List<String>) {
        val updated = playlist.copy(songIds = newSongIds)
        savePlaylist(updated)
        loadPlaylists()
    }

    private fun savePlaylist(playlist: Playlist) {
        File(playlistsDir, "${playlist.id}.json").writeText(json.encodeToString(playlist))
    }

    private fun savePlaylistOrder(ids: List<String>) {
        val orderFile = File(playlistsDir, "order.json")
        orderFile.writeText(json.encodeToString(ids))
    }

    private fun loadPlaylistOrder(): List<String> {
        val orderFile = File(playlistsDir, "order.json")
        if (!orderFile.exists()) return emptyList()
        return try {
            json.decodeFromStream<List<String>>(orderFile.inputStream())
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun updatePlaylistCover(playlist: Playlist, songs: List<OnlineSong>) {
        // Generate a grid cover (2x2) from the first 4 songs
        if (songs.isEmpty()) return

        val coverSize = 500 // px
        val bitmap = Bitmap.createBitmap(coverSize, coverSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint()

        val songsToUse = songs.take(4)
        val cellSize = if (songsToUse.size == 1) coverSize else coverSize / 2

        songsToUse.forEachIndexed { index, song ->
            val path = song.songUrl // Local path
            // We need cover image.
            // If song is local, we might need to extract cover or use cached cover.
            // SongRepository saves coverUrl as null for local songs but we might have a cache or embedded art.
            // For local files, we can try to load using Coil or BitmapFactory if we have the path.
            // But here we are in Repository, no Coil.
            // We can try to use MediaMetadataRetriever or check if there is a cached cover.

            // Try to find cached cover first
            val cachedCover = File(context.cacheDir, "${song.id}.jpg")
            var songBitmap: Bitmap? = null

            if (cachedCover.exists()) {
                songBitmap = BitmapFactory.decodeFile(cachedCover.absolutePath)
            } else if (song.songUrl != null) {
                 // Try to extract from audio file
                 try {
                     val mmr = android.media.MediaMetadataRetriever()
                     mmr.setDataSource(song.songUrl)
                     val data = mmr.embeddedPicture
                     if (data != null) {
                         songBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                     }
                     mmr.release()
                 } catch (e: Exception) {
                     e.printStackTrace()
                 }
            }

            if (songBitmap != null) {
                val x = (index % 2) * cellSize
                val y = (index / 2) * cellSize
                // Scale bitmap
                val scaled = Bitmap.createScaledBitmap(songBitmap, cellSize, cellSize, true)
                canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), paint)
                if (songBitmap != scaled) songBitmap.recycle()
            } else {
                // Draw placeholder color
                paint.color = android.graphics.Color.GRAY
                val x = (index % 2) * cellSize
                val y = (index / 2) * cellSize
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + cellSize).toFloat(), (y + cellSize).toFloat(), paint)
            }
        }

        val coverFile = File(playlistCoversDir, "${playlist.id}.jpg")
        coverFile.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 70, it)
        }

        // Update playlist with cover path
        val updated = playlist.copy(coverUrl = coverFile.absolutePath)
        savePlaylist(updated)
    }

    fun savePlaylists(playlists: List<Playlist>) {
        if (!playlistsDir.exists()) playlistsDir.mkdirs()
        playlists.forEach { playlist ->
            savePlaylist(playlist)
        }
        loadPlaylists()
    }

    fun getAllPlaylists(): List<Playlist> {
        return _playlists.value
    }
}
