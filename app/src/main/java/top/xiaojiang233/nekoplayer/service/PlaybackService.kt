package top.xiaojiang233.nekoplayer.service

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

class PlaybackService : MediaLibraryService() {

    private var mediaLibrarySession: MediaLibrarySession? = null
    private lateinit var player: Player

    private val librarySessionCallback = object : MediaLibrarySession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedMediaItems = mediaItems.map { mediaItem ->
                val extras = mediaItem.requestMetadata.extras
                if (extras != null) {
                    val metadata = MediaMetadata.Builder()
                        .setTitle(extras.getString("title"))
                        .setArtist(extras.getString("artist"))
                        .setArtworkUri(extras.getString("coverUrl")?.let { Uri.parse(it) })
                        .build()

                    mediaItem.buildUpon()
                        .setMediaMetadata(metadata)
                        .build()
                } else {
                    mediaItem
                }
            }.toMutableList()
            return Futures.immediateFuture(updatedMediaItems)
        }
    }

    override fun onCreate() {
        super.onCreate()

        val mediaSourceFactory =
            DefaultMediaSourceFactory(this).setDataSourceFactory(
                DefaultDataSource.Factory(
                    this,
                    DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
                )
            )

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()

        mediaLibrarySession = MediaLibrarySession.Builder(this, player, librarySessionCallback).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }
}
