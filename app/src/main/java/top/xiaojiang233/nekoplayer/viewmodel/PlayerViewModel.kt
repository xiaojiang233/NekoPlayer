package top.xiaojiang233.nekoplayer.viewmodel

import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.service.connection.MusicServiceConnection
import top.xiaojiang233.nekoplayer.utils.LyricLine
import top.xiaojiang233.nekoplayer.utils.LyricsParser
import java.io.File
import java.net.URL

class PlayerViewModel(private val musicServiceConnection: MusicServiceConnection) : ViewModel() {

    val isPlaying = musicServiceConnection.isPlaying
    val nowPlaying = musicServiceConnection.nowPlaying
    val repeatMode = musicServiceConnection.repeatMode
    val shuffleMode = musicServiceConnection.shuffleMode

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition = _currentPosition.asStateFlow()

    private val _totalDuration = MutableStateFlow(0L)
    val totalDuration = _totalDuration.asStateFlow()

    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics = _lyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex = _currentLyricIndex.asStateFlow()

    private val _lyricSearchResults = MutableStateFlow<List<OnlineSong>>(emptyList())
    val lyricSearchResults = _lyricSearchResults.asStateFlow()

    private val _isSearchingLyrics = MutableStateFlow(false)
    val isSearchingLyrics = _isSearchingLyrics.asStateFlow()

    private val _customCover = MutableStateFlow<Any?>(null)
    val customCover = _customCover.asStateFlow()

    private val player
        get() = musicServiceConnection.getMediaController()

    init {
        viewModelScope.launch {
            nowPlaying.collect { mediaItem ->
                if (mediaItem != null) {
                    val extras = mediaItem.mediaMetadata.extras ?: mediaItem.requestMetadata.extras
                    val lyricUrl = extras?.getString("lyricUrl")
                    loadLyrics(lyricUrl, mediaItem.requestMetadata.mediaUri)
                    loadCustomCover(mediaItem)
                } else {
                    _lyrics.value = emptyList()
                    _customCover.value = null
                }
            }
        }

        viewModelScope.launch {
            isPlaying.collect { playing ->
                if (playing) {
                    while (viewModelScope.isActive && isPlaying.value) {
                        val currentPos = player?.currentPosition ?: 0
                        _currentPosition.value = currentPos
                        _totalDuration.value = player?.duration?.coerceAtLeast(0) ?: 0

                        val currentLyrics = _lyrics.value
                        if (currentLyrics.isNotEmpty()) {
                            val index = currentLyrics.indexOfLast { it.time <= currentPos }
                            if (index != _currentLyricIndex.value) {
                                _currentLyricIndex.value = index
                            }
                        }
                        delay(100L) // Update every 100ms for smoother progress and better performance
                    }
                }
            }
        }
    }

    private fun loadCustomCover(mediaItem: MediaItem) {
        viewModelScope.launch(Dispatchers.IO) {
             val title = mediaItem.mediaMetadata.title?.toString() ?: ""
             val artist = mediaItem.mediaMetadata.artist?.toString() ?: ""
             var foundCover: Any? = null
             if (title.isNotEmpty()) {
                 val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                 val safeArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                 val fileNameBase = "$safeTitle - $safeArtist".trim()
                 val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
                 val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                 val coverFile = File(musicDir, "$fileNameBase.jpg")
                 if (coverFile.exists()) {
                     foundCover = coverFile
                 }
             }
             _customCover.value = foundCover
        }
    }

    fun playSong(song: OnlineSong) {
        playPlaylist(listOf(song), 0)
    }

    fun playPlaylist(songs: List<OnlineSong>, startIndex: Int) {
        if (songs.isEmpty()) return

        val mediaItems = songs.map { createMediaItem(it) }

        player?.setMediaItems(mediaItems, startIndex, 0)
        player?.prepare()
        player?.play()
    }

    private fun loadLyrics(lyricUrl: String?, mediaUri: android.net.Uri? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            var sidecarLyrics: List<LyricLine>? = null
            val song = nowPlaying.value
            if (song != null) {
                 val title = song.mediaMetadata.title?.toString() ?: ""
                 val artist = song.mediaMetadata.artist?.toString() ?: ""
                 if (title.isNotEmpty()) {
                     val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                     val safeArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                     val fileNameBase = "$safeTitle - $safeArtist".trim()
                     val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
                     val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                     val lrcFile = File(musicDir, "$fileNameBase.lrc")
                     if (lrcFile.exists()) {
                         sidecarLyrics = LyricsParser.parseFile(lrcFile)
                     }
                 }
            }

            try {
                val lyricsList = sidecarLyrics ?: if (!lyricUrl.isNullOrBlank()) {
                    if (lyricUrl.startsWith("http")) {
                        val content = URL(lyricUrl).readText()
                        LyricsParser.parse(content)
                    } else {
                        val file = File(lyricUrl)
                        if (file.exists()) {
                            LyricsParser.parseFile(file)
                        } else {
                            emptyList()
                        }
                    }
                } else if (mediaUri != null && (mediaUri.scheme == "file" || mediaUri.scheme == "content")) {
                    // Try to read embedded lyrics
                    readEmbeddedLyrics(mediaUri)
                } else {
                    emptyList()
                }
                _lyrics.value = lyricsList
            } catch (e: Exception) {
                e.printStackTrace()
                _lyrics.value = emptyList()
            }
        }
    }

    private suspend fun readEmbeddedLyrics(uri: android.net.Uri): List<LyricLine> {
        val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
        return try {
            // Copy to temp file to read tags
            val tempFile = File.createTempFile("temp_lyrics", ".mp3", context.cacheDir)
            context.contentResolver.openInputStream(uri)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true
            val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
            val tag = audioFile.tag
            val lyricsContent = tag?.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS)

            tempFile.delete()

            if (!lyricsContent.isNullOrBlank()) {
                LyricsParser.parse(lyricsContent)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun onPlayPauseClick() {
        if (player?.isPlaying == true) {
            player?.pause()
        } else {
            player?.play()
        }
    }

    fun seekTo(position: Long) {
        player?.seekTo(position)
    }

    fun skipToNext() {
        player?.seekToNext()
    }

    fun skipToPrevious() {
        player?.seekToPrevious()
    }

    fun cyclePlaybackMode() {
        val currentShuffle = player?.shuffleModeEnabled ?: false
        val currentRepeat = player?.repeatMode ?: androidx.media3.common.Player.REPEAT_MODE_OFF

        if (currentShuffle) {
            player?.shuffleModeEnabled = false
            player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        } else {
            when (currentRepeat) {
                androidx.media3.common.Player.REPEAT_MODE_ALL -> {
                    player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                }
                androidx.media3.common.Player.REPEAT_MODE_ONE -> {
                    player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                    player?.shuffleModeEnabled = true
                }
                else -> {
                    player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                }
            }
        }
    }

    fun hasPlaylist(): Boolean {
        return (player?.mediaItemCount ?: 0) > 0
    }

    fun insertAndPlay(song: OnlineSong) {
        val player = this.player ?: return
        val currentMediaItemIndex = player.currentMediaItemIndex
        val nextIndex = if (currentMediaItemIndex < 0) 0 else currentMediaItemIndex + 1

        val mediaItem = createMediaItem(song)
        player.addMediaItem(nextIndex, mediaItem)
        player.seekTo(nextIndex, 0)
        player.play()
    }

    private fun createMediaItem(s: OnlineSong): MediaItem {
        val uri = if (s.songUrl?.startsWith("/") == true) {
            File(s.songUrl).toUri()
        } else {
            s.songUrl?.toUri()
        }

        val coverUri = if (s.coverUrl?.startsWith("/") == true) {
            File(s.coverUrl).toUri()
        } else {
            s.coverUrl?.toUri()
        }

        val extras = Bundle().apply {
            putString("title", s.title)
            putString("artist", s.artist)
            putString("coverUrl", s.coverUrl)
            putString("platform", s.platform)
            putString("lyricUrl", s.lyricUrl)
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setArtworkUri(coverUri)
            .setExtras(extras)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(uri)
            .setExtras(extras)
            .build()

        return MediaItem.Builder()
            .setMediaId(s.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .setRequestMetadata(requestMetadata)
            .build()
    }

    override fun onCleared() {
        super.onCleared()
        musicServiceConnection.release()
    }

    @Suppress("UNCHECKED_CAST")
    class Factory(private val musicServiceConnection: MusicServiceConnection) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PlayerViewModel(musicServiceConnection) as T
        }
    }

    fun searchLyrics(query: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isSearchingLyrics.value = true
            try {
                val response = top.xiaojiang233.nekoplayer.data.network.RetrofitInstance.musicApiService.searchSongs(keyword = query, limit = 50)
                if (response.code == 200) {
                    _lyricSearchResults.value = response.data.results
                } else {
                    _lyricSearchResults.value = emptyList()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _lyricSearchResults.value = emptyList()
            } finally {
                _isSearchingLyrics.value = false
            }
        }
    }

    fun clearSearchResults() {
        _lyricSearchResults.value = emptyList()
    }

    fun applyMetadata(selectedMatch: OnlineSong) {
        val current = nowPlaying.value ?: return

        val targetSong = OnlineSong(
            id = current.mediaId,
            title = current.mediaMetadata.title.toString(),
            artist = current.mediaMetadata.artist.toString(),
            album = current.mediaMetadata.albumTitle.toString(),
            platform = "local",
            songUrl = current.requestMetadata.mediaUri.toString(),
            coverUrl = null,
            lyricUrl = null
        )

        viewModelScope.launch {
            top.xiaojiang233.nekoplayer.data.repository.SongRepository.updateSongMetadata(targetSong, selectedMatch)
            // Reload lyrics
            loadLyrics(null, current.requestMetadata.mediaUri)
        }
    }
}
