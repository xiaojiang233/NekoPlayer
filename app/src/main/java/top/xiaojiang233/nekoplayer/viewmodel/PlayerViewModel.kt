package top.xiaojiang233.nekoplayer.viewmodel

import android.os.Bundle
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.service.connection.MusicServiceConnection
import top.xiaojiang233.nekoplayer.utils.LyricLine
import top.xiaojiang233.nekoplayer.utils.LyricsParser
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers

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

    private val player
        get() = musicServiceConnection.getMediaController()

    init {
        viewModelScope.launch {
            nowPlaying.collect { mediaItem ->
                if (mediaItem != null) {
                    val extras = mediaItem.mediaMetadata.extras ?: mediaItem.requestMetadata.extras
                    val lyricUrl = extras?.getString("lyricUrl")
                    loadLyrics(lyricUrl)
                } else {
                    _lyrics.value = emptyList()
                }
            }
        }

        viewModelScope.launch {
            while (true) {
                val currentPos = player?.currentPosition ?: 0
                _currentPosition.value = currentPos
                val totalDur = player?.duration?.coerceAtLeast(0) ?: 0
                _totalDuration.value = totalDur

                // Update current lyric index
                val currentLyrics = _lyrics.value
                if (currentLyrics.isNotEmpty()) {
                    val index = currentLyrics.indexOfLast { it.time <= currentPos }
                    _currentLyricIndex.value = index
                }

                delay(50L)
            }
        }
    }

    fun playSong(song: OnlineSong) {
        playPlaylist(listOf(song), 0)
    }

    fun playPlaylist(songs: List<OnlineSong>, startIndex: Int) {
        if (songs.isEmpty()) return
        // Don't load lyrics here manually, let the observer handle it
        // val song = songs[startIndex]
        // loadLyrics(song)

        val mediaItems = songs.map { createMediaItem(it) }

        player?.setMediaItems(mediaItems, startIndex, 0)
        player?.prepare()
        player?.play()
    }



    private fun loadLyrics(lyricUrl: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lyricsList = if (!lyricUrl.isNullOrBlank()) {
                    if (lyricUrl.startsWith("http")) {
                        val content = URL(lyricUrl).readText()
                        LyricsParser.parse(content)
                    } else {
                        // Handle local file path
                        val file = File(lyricUrl)
                        if (file.exists()) {
                            LyricsParser.parseFile(file)
                        } else {
                            emptyList()
                        }
                    }
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
            // Current is List Random (Shuffle ON) -> Switch to List Loop
            player?.shuffleModeEnabled = false
            player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
        } else {
            when (currentRepeat) {
                androidx.media3.common.Player.REPEAT_MODE_ALL -> {
                    // Current is List Loop -> Switch to Single Loop
                    player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ONE
                }
                androidx.media3.common.Player.REPEAT_MODE_ONE -> {
                    // Current is Single Loop -> Switch to List Random
                    player?.repeatMode = androidx.media3.common.Player.REPEAT_MODE_ALL
                    player?.shuffleModeEnabled = true
                }
                else -> {
                    // Current is OFF or unknown -> Switch to List Loop
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

        val metadata = MediaMetadata.Builder()
            .setTitle(s.title)
            .setArtist(s.artist)
            .setArtworkUri(coverUri)
            .setExtras(Bundle().apply {
                putString("title", s.title)
                putString("artist", s.artist)
                putString("coverUrl", s.coverUrl)
                putString("platform", s.platform)
                putString("lyricUrl", s.lyricUrl)
            })
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(uri)
            .setExtras(Bundle().apply {
                putString("title", s.title)
                putString("artist", s.artist)
                putString("coverUrl", s.coverUrl)
                putString("platform", s.platform)
                putString("lyricUrl", s.lyricUrl)
            })
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
}
