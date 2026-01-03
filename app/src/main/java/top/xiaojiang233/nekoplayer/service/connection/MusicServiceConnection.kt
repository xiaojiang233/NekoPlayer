package top.xiaojiang233.nekoplayer.service.connection

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaBrowser
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import top.xiaojiang233.nekoplayer.service.PlaybackService

class MusicServiceConnection private constructor(context: Context) {

    val isConnected = mutableStateOf(false)
    val nowPlaying = MutableStateFlow<MediaItem?>(null)
    val playbackState = MutableStateFlow(Player.STATE_IDLE)
    val isPlaying = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val shuffleMode = MutableStateFlow(false)

    private var mediaBrowser: MediaBrowser? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            playbackState.value = state
        }
        override fun onIsPlayingChanged(playing: Boolean) {
            isPlaying.value = playing
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            nowPlaying.value = mediaItem
        }
        override fun onRepeatModeChanged(mode: Int) {
            repeatMode.value = mode
        }
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffleMode.value = shuffleModeEnabled
        }
    }

    init {
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val controllerFuture = MediaBrowser.Builder(context, sessionToken).buildAsync()
        controllerFuture.addListener(
            {
                mediaBrowser = controllerFuture.get()
                mediaBrowser?.addListener(playerListener)
                isConnected.value = true
                // Update states for the first time
                nowPlaying.value = mediaBrowser?.currentMediaItem
                playbackState.value = mediaBrowser?.playbackState ?: Player.STATE_IDLE
                isPlaying.value = mediaBrowser?.isPlaying ?: false
                repeatMode.value = mediaBrowser?.repeatMode ?: Player.REPEAT_MODE_OFF
                shuffleMode.value = mediaBrowser?.shuffleModeEnabled ?: false
            },
            MoreExecutors.directExecutor()
        )
    }

    fun getMediaController(): Player? {
        return mediaBrowser
    }

    fun release() {
        mediaBrowser?.release()
        mediaBrowser = null
        isConnected.value = false
    }

    companion object {
        @Volatile
        private var instance: MusicServiceConnection? = null

        fun getInstance(context: Context) =
            instance ?: synchronized(this) {
                instance ?: MusicServiceConnection(context.applicationContext).also { instance = it }
            }
    }
}
