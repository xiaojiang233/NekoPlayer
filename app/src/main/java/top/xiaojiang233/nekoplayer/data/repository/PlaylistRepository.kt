package top.xiaojiang233.nekoplayer.data.repository

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
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

@SuppressLint("StaticFieldLeak")
object PlaylistRepository {
    private val context = NekoPlayerApplication.getAppContext()
    private val playlistsDir = File(context.filesDir, "playlists")
    private val playlistCoversDir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "NekoPlayerCovers")
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
        val allSongs = (SongRepository.getLocalSongs() + songs).distinctBy { it.id }.filter { song -> song.id in updatedSongIds }
        updatePlaylistCover(playlist.copy(songIds = updatedSongIds), allSongs)
        loadPlaylists()
    }

    fun removeSongFromPlaylist(playlist: Playlist, songId: String) {
        val updatedSongIds = playlist.songIds - songId
        val allSongs = SongRepository.getLocalSongs().filter { it.id in updatedSongIds }
        updatePlaylistCover(playlist.copy(songIds = updatedSongIds), allSongs)
        loadPlaylists()
    }

    fun updatePlaylistsOrder(playlists: List<Playlist>) {
        _playlists.value = playlists
        savePlaylistOrder(playlists.map { it.id })
    }

    fun updateSongsOrder(playlist: Playlist, newSongIds: List<String>) {
        val updated = playlist.copy(songIds = newSongIds)
        val songsForCover = SongRepository.getLocalSongs().filter { it.id in newSongIds }
        updatePlaylistCover(updated, songsForCover)
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
        if (songs.isEmpty()) {
            val coverFile = File(playlistCoversDir, "${playlist.id}.jpg")
            if (coverFile.exists()) coverFile.delete()
            savePlaylist(playlist.copy(coverUrl = null))
            return
        }

        val coverSize = 500 // px
        val resultBitmap = Bitmap.createBitmap(coverSize, coverSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        val songsToUse = songs.take(4)
        val cellSize = if (songsToUse.size <= 1) coverSize else coverSize / 2

        songsToUse.forEachIndexed { index, song ->
            var songBitmap: Bitmap? = null
            val safeTitle = song.title.replace(Regex("[\\/:*?\"<>|]"), "_")
            val safeArtist = song.artist.replace(Regex("[\\/:*?\"<>|]"), "_")
            val fileNameBase = "$safeTitle - $safeArtist".trim()
            val musicDir = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC)
            val cachedCover = if (musicDir != null) File(musicDir, "$fileNameBase.jpg") else null

            if (cachedCover?.exists() == true) {
                songBitmap = BitmapFactory.decodeFile(cachedCover.absolutePath)
            } else if (song.songUrl != null) {
                val mmr = android.media.MediaMetadataRetriever()
                try {
                    mmr.setDataSource(context, Uri.parse(song.songUrl))
                    mmr.embeddedPicture?.let { data ->
                        songBitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    mmr.release()
                }
            }

            val x = (index % 2) * cellSize
            val y = (index / 2) * cellSize

            if (songBitmap != null) {
                val scaled = Bitmap.createScaledBitmap(songBitmap!!, cellSize, cellSize, true)
                canvas.drawBitmap(scaled, x.toFloat(), y.toFloat(), paint)
                songBitmap!!.recycle()
                if (scaled != songBitmap) scaled.recycle()
            } else {
                paint.color = android.graphics.Color.DKGRAY
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + cellSize).toFloat(), (y + cellSize).toFloat(), paint)
            }
        }

        val coverFile = File(playlistCoversDir, "${playlist.id}.jpg")
        try {
            coverFile.outputStream().use { resultBitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            savePlaylist(playlist.copy(coverUrl = coverFile.absolutePath))
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            resultBitmap.recycle()
        }
    }

    fun savePlaylists(playlists: List<Playlist>) {
        if (!playlistsDir.exists()) playlistsDir.mkdirs()
        playlists.forEach { savePlaylist(it) }
        loadPlaylists()
    }

    fun getAllPlaylists(): List<Playlist> = _playlists.value
}
