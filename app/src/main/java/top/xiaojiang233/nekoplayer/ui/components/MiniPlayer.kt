package top.xiaojiang233.nekoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MiniPlayer(
    isPlaying: Boolean,
    nowPlaying: MediaItem?,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var backgroundColor by remember { mutableStateOf(Color.Gray) }
    var contentColor by remember { mutableStateOf(Color.White) }

    // Fallback for album art if it's a local file without explicit artwork URI in metadata
    val displayAlbumArt = nowPlaying?.mediaMetadata?.artworkUri ?: nowPlaying?.mediaId?.let { id ->
        nowPlaying.requestMetadata.mediaUri?.let { uri ->
            if (uri.scheme == "file") File(uri.path ?: "") else null
        }
    }

    LaunchedEffect(displayAlbumArt) {
        displayAlbumArt?.let { model ->
            withContext(Dispatchers.IO) {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .allowHardware(false)
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    val palette = Palette.from(bitmap).generate()
                    val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
                    swatch?.let {
                        backgroundColor = Color(it.rgb)
                        contentColor = Color(it.bodyTextColor)
                    }
                }
            }
        }
    }

    if (nowPlaying != null) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp)
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .background(backgroundColor)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = displayAlbumArt,
                    contentDescription = "Album Art",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
                Column(modifier = Modifier.padding(start = 12.dp)) {
                    Text(
                        text = nowPlaying.mediaMetadata.title.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor
                    )
                    Text(
                        text = nowPlaying.mediaMetadata.artist.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onPlayPauseClick) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = contentColor
                )
            }
        }
    }
}
