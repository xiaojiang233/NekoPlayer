package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel, onCloseClick: () -> Unit) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()

    var isDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentPosition) {
        if (!isDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    val albumArtUrl = nowPlaying?.mediaMetadata?.artworkUri
    val title = nowPlaying?.mediaMetadata?.title ?: ""
    val artist = nowPlaying?.mediaMetadata?.artist ?: ""

    // Fallback for album art if it's a local file without explicit artwork URI in metadata
    val displayAlbumArt = albumArtUrl ?: nowPlaying?.mediaId?.let { id ->
        // Try to find the local file if we know it's a local song
        // This is a bit hacky, but if we don't have the file path here, we can't use AudioCoverFetcher easily
        // unless we pass the file path in extras or mediaId is the path.
        // In PlayerViewModel, we set mediaId to song.id.
        // We also set extras "coverUrl" which might be null.
        // If it's null, we should try to use the song URI if it's a file URI.
        nowPlaying?.requestMetadata?.mediaUri?.let { uri ->
            if (uri.scheme == "file") File(uri.path ?: "") else null
        }
    }

    val pagerState = rememberPagerState(pageCount = { 2 })

    Box(modifier = Modifier.fillMaxSize()) {
        AsyncImage(
            model = displayAlbumArt,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(radius = 50.dp)
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        HorizontalPager(state = pagerState) {
            when (it) {
                0 -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        AsyncImage(
                            model = displayAlbumArt,
                            contentDescription = "Album Art",
                            modifier = Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        val textShadow = Shadow(
                            color = Color.Black.copy(alpha = 0.5f),
                            offset = Offset(0f, 4f),
                            blurRadius = 8f
                        )

                        Text(
                            text = title.toString(),
                            style = MaterialTheme.typography.headlineMedium.copy(
                                color = Color.White,
                                shadow = textShadow
                            )
                        )
                        Text(
                            text = artist.toString(),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = Color.White.copy(alpha = 0.8f),
                                shadow = textShadow
                            )
                        )
                        Spacer(modifier = Modifier.height(32.dp))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            val duration = totalDuration.toFloat().coerceAtLeast(1f)
                            val position = if (isDragging) sliderPosition else currentPosition.toFloat()

                            CustomProgressBar(
                                value = position,
                                maxValue = duration,
                                onValueChange = {
                                    isDragging = true
                                    sliderPosition = it
                                },
                                onValueChangeFinished = {
                                    viewModel.seekTo(sliderPosition.toLong())
                                    isDragging = false
                                }
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(position.toLong()),
                                    style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Text(
                                    text = formatTime(totalDuration),
                                    style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { /* TODO */ }) {
                                Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
                            }
                            IconButton(onClick = { viewModel.onPlayPauseClick() }) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    modifier = Modifier.size(48.dp),
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { /* TODO */ }) {
                                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
                            }
                        }
                    }
                }
                1 -> {
                    LyricsScreen(viewModel)
                }
            }
        }

        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowDown,
                contentDescription = "Close",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

@Composable
fun CustomProgressBar(
    value: Float,
    maxValue: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    val thumbRadius by animateDpAsState(targetValue = if (isDragging) 6.dp else 3.dp, label = "thumbRadius")
    val trackHeight by animateDpAsState(targetValue = if (isDragging) 2.dp else 1.dp, label = "trackHeight")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(newFraction * maxValue)
                    },
                    onDragEnd = {
                        isDragging = false
                        onValueChangeFinished()
                    },
                    onDragCancel = {
                        isDragging = false
                        onValueChangeFinished()
                    }
                ) { change, _ ->
                    val newFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newFraction * maxValue)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isDragging = true
                        val newFraction = (offset.x / size.width).coerceIn(0f, 1f)
                        onValueChange(newFraction * maxValue)
                        tryAwaitRelease()
                        isDragging = false
                        onValueChangeFinished()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val fraction = if (maxValue > 0) (value / maxValue).coerceIn(0f, 1f) else 0f

        Canvas(modifier = Modifier.fillMaxWidth().height(trackHeight)) {
            val trackCornerRadius = CornerRadius(size.height / 2)

            // Background track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.3f),
                size = size,
                cornerRadius = trackCornerRadius
            )

            // Progress track
            drawRoundRect(
                color = Color.White.copy(alpha = 0.7f),
                size = size.copy(width = size.width * fraction),
                cornerRadius = trackCornerRadius
            )

            // Thumb
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = thumbRadius.toPx(),
                center = Offset(x = size.width * fraction, y = size.height / 2)
            )
        }
    }
}
