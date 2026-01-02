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

                delay(100L)
            }
        }
    }

    fun playSong(song: OnlineSong) {
        loadLyrics(song)

        val uri = if (song.songUrl?.startsWith("/") == true) {
            File(song.songUrl).toUri()
        } else {
            song.songUrl?.toUri()
        }

        val coverUri = if (song.coverUrl?.startsWith("/") == true) {
            File(song.coverUrl).toUri()
        } else {
            song.coverUrl?.toUri()
        }

        val metadata = MediaMetadata.Builder()
            .setTitle(song.title)
            .setArtist(song.artist)
            .setArtworkUri(coverUri)
            .build()

        val requestMetadata = MediaItem.RequestMetadata.Builder()
            .setMediaUri(uri)
            .setExtras(Bundle().apply {
                putString("title", song.title)
                putString("artist", song.artist)
                putString("coverUrl", song.coverUrl)
            })
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(uri)
            .setMediaMetadata(metadata)
            .setRequestMetadata(requestMetadata)
            .build()

        player?.setMediaItem(mediaItem)
        player?.prepare()
        player?.play()
    }


    private fun loadLyrics(song: OnlineSong) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lyricsList = if (!song.lyricUrl.isNullOrBlank()) {
                    if (song.lyricUrl.startsWith("http")) {
                        val content = URL(song.lyricUrl).readText()
                        LyricsParser.parse(content)
                    } else {
                        // Handle local file path
                        val file = File(song.lyricUrl)
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
