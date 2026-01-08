package top.xiaojiang233.nekoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.media3.common.MediaItem
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun MiniPlayer(
    isPlaying: Boolean,
    nowPlaying: MediaItem?,
    onPlayPauseClick: () -> Unit,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    customCover: Any? = null,
    onMiniPlayerClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val isWearable = configuration.screenWidthDp < 300

    var backgroundColor by remember { mutableStateOf(Color.Gray) }
    var contentColor by remember { mutableStateOf(Color.White) }

    val displayAlbumArt = remember(nowPlaying, customCover) {
        customCover ?: nowPlaying?.mediaMetadata?.artworkUri ?: nowPlaying?.requestMetadata?.mediaUri
    }

    LaunchedEffect(displayAlbumArt) {
        if (displayAlbumArt != null) {
            val swatch = withContext(Dispatchers.IO) {
                val loader = context.imageLoader
                val request = ImageRequest.Builder(context)
                    .data(displayAlbumArt)
                    .allowHardware(false)
                    .bitmapConfig(android.graphics.Bitmap.Config.ARGB_8888) // Ensure compatible config
                    .size(100) // Downsample for performance and reliability
                    .build()
                val result = loader.execute(request)
                if (result is SuccessResult) {
                    val bitmap = result.drawable.toBitmap()
                    val palette = Palette.from(bitmap).generate()
                    // Try dominant, then others. Ensure we get a non-null swatch if possible.
                    palette.dominantSwatch
                        ?: palette.vibrantSwatch
                        ?: palette.mutedSwatch
                        ?: palette.darkVibrantSwatch
                        ?: palette.lightVibrantSwatch
                } else null
            }

            swatch?.let {
                backgroundColor = Color(it.rgb)
                contentColor = Color(it.bodyTextColor)
            }
        } else {
            // Reset to default if no art
            backgroundColor = Color.Gray
            contentColor = Color.White
        }
    }

    if (nowPlaying != null) {
        if (isWearable) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) }
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(50.dp))
                        .background(backgroundColor.copy(alpha = 0.85f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = displayAlbumArt,
                        contentDescription = "Album Art",
                        modifier = Modifier.size(36.dp).clip(RoundedCornerShape(50.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = nowPlaying.mediaMetadata.title.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onPlayPauseClick) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = contentColor
                        )
                    }
                }
            }
        } else if (isLandscape) {
            Row(
                modifier = modifier
                    .padding(16.dp)
                    .widthIn(max = 400.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .clickable(enabled = onMiniPlayerClick != null) { onMiniPlayerClick?.invoke() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = displayAlbumArt,
                    contentDescription = "Album Art",
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                )
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f)
                ) {
                    Text(
                        text = nowPlaying.mediaMetadata.title.toString(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = nowPlaying.mediaMetadata.artist.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = contentColor.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPlayPauseClick) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = contentColor
                    )
                }
            }
        } else {
            Row(
                modifier = modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .shadow(elevation = 8.dp, shape = RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
                    .background(backgroundColor)
                    .clickable(enabled = onMiniPlayerClick != null) { onMiniPlayerClick?.invoke() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = displayAlbumArt,
                        contentDescription = "Album Art",
                        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
                    )
                    Column(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .weight(1f)
                    ) {
                        Text(
                            text = nowPlaying.mediaMetadata.title.toString(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = contentColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = nowPlaying.mediaMetadata.artist.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = contentColor.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
}
