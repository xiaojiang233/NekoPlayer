package top.xiaojiang233.nekoplayer.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.SimpleBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext

@UnstableApi
class CustomBitmapLoader(private val context: Context) : BitmapLoader {
    private val delegate = SimpleBitmapLoader()

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> {
        return delegate.decodeBitmap(data)
    }

    override fun supportsMimeType(mimeType: String): Boolean {
        return delegate.supportsMimeType(mimeType)
    }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        // First check if it's a web URL
        if (uri.scheme?.startsWith("http") == true) {
            return delegate.loadBitmap(uri)
        }

        // Handle local URIs (file:// or content://)
        // Check if it's an audio file URI that might contain embedded artwork
        val type = context.contentResolver.getType(uri)
        val isAudio = type?.startsWith("audio/") == true ||
                uri.path?.lowercase()?.let { path ->
                    listOf(".mp3", ".flac", ".m4a", ".wav", ".ogg").any { path.endsWith(it) }
                } == true

        if (isAudio) {
            // Use coroutine to extract bitmap in background using Guava bridge
            return kotlinx.coroutines.GlobalScope.future(Dispatchers.IO) {
                try {
                    extractBitmapFromAudio(uri)
                } catch (e: Exception) {
                    // Fallback to delegate if extraction fails
                    withContext(Dispatchers.Main) {
                        delegate.loadBitmap(uri).get()
                    }
                }
            }
        }

        // Otherwise, try normal loading
        return delegate.loadBitmap(uri)
    }

    private fun extractBitmapFromAudio(uri: Uri): Bitmap {
        val retriever = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") {
                retriever.setDataSource(uri.path)
            } else {
                retriever.setDataSource(context, uri)
            }
            val pic = retriever.embeddedPicture
            if (pic != null) {
                BitmapFactory.decodeByteArray(pic, 0, pic.size)
            } else {
                throw Exception("No embedded artwork found")
            }
        } finally {
            retriever.release()
        }
    }
}
