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
import kotlinx.coroutines.withContext
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.repository.SettingsRepository
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

    private var playbackDelay = 0
    private var fadeInDuration = 0

    private val player
        get() = musicServiceConnection.getMediaController()

    init {
        viewModelScope.launch {
            SettingsRepository.playbackDelay.collect { playbackDelay = it }
        }

        viewModelScope.launch {
            SettingsRepository.fadeInDuration.collect { fadeInDuration = it }
        }

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
                 val safeTitle = title.replace(Regex("[\\/:*?\"<>|]"), "_")
                 val safeArtist = artist.replace(Regex("[\\/:*?\"<>|]"), "_")
                 val fileNameBase = "$safeTitle - $safeArtist".trim()
                 val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
                 val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                 val coverFile = File(musicDir, "$fileNameBase.jpg")
                 if (coverFile.exists()) {
                     foundCover = coverFile
                 } else {
                     // Try extracting embedded artwork and save it
                     try {
                         val mediaUri = mediaItem.requestMetadata.mediaUri
                         if (mediaUri != null) {
                             val saved = top.xiaojiang233.nekoplayer.data.repository.SongRepository.saveEmbeddedCoverForMediaUri(mediaUri, fileNameBase, mediaItem.mediaId)
                             if (saved && coverFile.exists()) {
                                 foundCover = coverFile
                                 // After saving embedded cover, refresh media metadata so system controls show artwork
                                 withContext(Dispatchers.Main) {
                                     refreshNowPlayingMetadata()
                                 }
                             }
                         }
                     } catch (e: Exception) {
                         e.printStackTrace()
                     }
                 }
             }
             _customCover.value = foundCover
        }
    }

    // Convert MediaItem to OnlineSong for rebuilding MediaItem metadata
    private fun mediaItemToOnlineSong(item: MediaItem): OnlineSong {
        val meta = item.mediaMetadata
        val extras = meta.extras ?: item.requestMetadata.extras
        val title = meta.title?.toString() ?: extras?.getString("title") ?: ""
        val artist = meta.artist?.toString() ?: extras?.getString("artist") ?: ""
        val album = meta.albumTitle?.toString() ?: extras?.getString("album")
        val coverUrl = extras?.getString("coverUrl")
        val lyricUrl = extras?.getString("lyricUrl")
        val platform = extras?.getString("platform") ?: "local"
        val songUrl = item.requestMetadata.mediaUri?.toString()

        return OnlineSong(
            id = item.mediaId,
            title = title,
            artist = artist,
            album = album,
            platform = platform,
            songUrl = songUrl,
            coverUrl = coverUrl,
            lyricUrl = lyricUrl
        )
    }

    // Rebuild and replace the current media item metadata so media session and UI update
    private fun refreshNowPlayingMetadata() {
        viewModelScope.launch(Dispatchers.Main) {
            val p = player ?: return@launch
            val currentIndex = p.currentMediaItemIndex
            if (currentIndex < 0) return@launch
            val currentPosition = p.currentPosition
            val wasPlaying = p.isPlaying

            val itemCount = p.mediaItemCount
            val items = mutableListOf<MediaItem>()
            for (i in 0 until itemCount) {
                val item = p.getMediaItemAt(i)
                if (i == currentIndex) {
                    val song = mediaItemToOnlineSong(item)
                    items.add(createMediaItem(song))
                } else {
                    items.add(item)
                }
            }

            try {
                p.setMediaItems(items, currentIndex, currentPosition)
                p.prepare()
                if (wasPlaying) p.play()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playSong(song: OnlineSong) {
        playPlaylist(listOf(song), 0)
    }

    fun playPlaylist(songs: List<OnlineSong>, startIndex: Int) {
        if (songs.isEmpty()) return

        viewModelScope.launch {
            val mediaItems = songs.map { createMediaItem(it) }

            player?.setMediaItems(mediaItems, startIndex, 0)
            player?.prepare()
            player?.play()

            // Apply delay and fade-in
            delay(playbackDelay.toLong())
            if (fadeInDuration > 0) {
                val steps = 20
                val stepDuration = fadeInDuration.toLong() / steps
                for (i in 1..steps) {
                    player?.volume = i / steps.toFloat()
                    delay(stepDuration)
                }
            }
            player?.volume = 1f
        }
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
        return withContext(Dispatchers.IO) {
            try {
                // Check if URI is accessible
                context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                    if (pfd.statSize <= 0) return@withContext emptyList<LyricLine>()
                }

                // Copy to temp file to read tags
                val tempFile = File.createTempFile("temp_lyrics", ".mp3", context.cacheDir)
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }

                    if (tempFile.length() > 0) {
                        org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true
                        val audioFile = org.jaudiotagger.audio.AudioFileIO.read(tempFile)
                        val tag = audioFile.tag
                        val lyricsContent = tag?.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS)

                        if (!lyricsContent.isNullOrBlank()) {
                            LyricsParser.parse(lyricsContent)
                        } else {
                            emptyList()
                        }
                    } else {
                        emptyList()
                    }
                } finally {
                    if (tempFile.exists()) tempFile.delete()
                }
            } catch (e: Exception) {
                // Log and ignore
                android.util.Log.e("PlayerViewModel", "Error reading embedded lyrics", e)
                emptyList()
            }
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

        // Try to get cover URI from multiple sources
        var coverUri = if (s.coverUrl?.startsWith("/") == true) {
            File(s.coverUrl).toUri()
        } else if (s.coverUrl?.startsWith("file://") == true) {
            s.coverUrl.toUri()
        } else if (s.coverUrl?.startsWith("http") == true) {
            s.coverUrl.toUri()
        } else if (s.coverUrl?.startsWith("content://") == true) {
            s.coverUrl.toUri()
        } else {
            null
        }

        // For local music (platform="local") or when no cover URI resolved yet, try to find downloaded cover file
        if (coverUri == null || s.platform == "local") {
            try {
                val safeTitle = s.title.replace(Regex("[\\\\/:*?\"<>]"), "_")
                val safeArtist = s.artist.replace(Regex("[\\\\/:*?\"<>]"), "_")
                val fileNameBase = "$safeTitle - $safeArtist".trim()
                val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()
                val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                val coverFile = File(musicDir, "$fileNameBase.jpg")
                if (coverFile.exists()) {
                    coverUri = coverFile.toUri()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // If still no cover URI and we have a content URI from coverUrl, use it
        // This allows AudioCoverFetcher to extract embedded album art in UI,
        // but for MediaSession we prefer actual file URIs
        if (coverUri == null && s.coverUrl?.startsWith("content://") == true) {
            coverUri = s.coverUrl.toUri()
        }

        val extras = Bundle().apply {
            putString("title", s.title)
            putString("artist", s.artist)
            // Put resolved coverUri string so MediaLibrarySession callback can use it
            putString("coverUrl", coverUri?.toString())
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
            // Refresh metadata and artwork
            refreshNowPlayingMetadata()
        }
    }

    fun importLrcFile(uri: android.net.Uri) {
        val current = nowPlaying.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = top.xiaojiang233.nekoplayer.NekoPlayerApplication.getAppContext()

                // Use the same location and naming pattern as loadLyrics
                val title = current.mediaMetadata.title?.toString() ?: ""
                val artist = current.mediaMetadata.artist?.toString() ?: ""

                if (title.isEmpty()) return@launch

                val safeTitle = title.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val safeArtist = artist.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                val fileNameBase = "$safeTitle - $safeArtist".trim()

                val musicDir = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MUSIC)
                if (musicDir != null && !musicDir.exists()) {
                    musicDir.mkdirs()
                }

                val lrcFile = File(musicDir, "$fileNameBase.lrc")

                // Copy content from URI to storage
                context.contentResolver.openInputStream(uri)?.use { input ->
                    lrcFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // Reload lyrics
                withContext(Dispatchers.Main) {
                    loadLyrics(null, current.requestMetadata.mediaUri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
