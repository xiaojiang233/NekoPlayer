package top.xiaojiang233.nekoplayer.ui.screen

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import top.xiaojiang233.nekoplayer.utils.LyricLine
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlayerScreen(
    viewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = viewModel(),
    onCloseClick: () -> Unit
) {
    val isPlaying by viewModel.isPlaying.collectAsState()
    val nowPlaying by viewModel.nowPlaying.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val totalDuration by viewModel.totalDuration.collectAsState()
    val repeatMode by viewModel.repeatMode.collectAsState()
    val shuffleMode by viewModel.shuffleMode.collectAsState()
    val showPlatformTag by settingsViewModel.showPlatformTag.collectAsState()

    var isSliderDragging by remember { mutableStateOf(false) }
    var sliderPosition by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(currentPosition) {
        if (!isSliderDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    val configuration = LocalConfiguration.current
    val isWearable = configuration.screenWidthDp < 300

    val albumArtUrl = nowPlaying?.mediaMetadata?.artworkUri
    val title = nowPlaying?.mediaMetadata?.title ?: ""
    val artist = nowPlaying?.mediaMetadata?.artist ?: ""
    val platform = nowPlaying?.requestMetadata?.extras?.getString("platform")

    val displayAlbumArt = albumArtUrl ?: nowPlaying?.mediaId?.let {
        nowPlaying?.requestMetadata?.mediaUri?.let { uri ->
            if (uri.scheme == "file") File(uri.path ?: "") else null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Crossfade(
            targetState = displayAlbumArt,
            label = "BackgroundCrossfade",
            animationSpec = tween(500)
        ) { art ->
            val context = LocalContext.current
            val imageModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                art
            } else {
                ImageRequest.Builder(context)
                    .data(art)
                    .size(200) // Request a smaller image for a pseudo-blur effect
                    .crossfade(true)
                    .build()
            }

            val backgroundModifier = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Modifier.fillMaxSize().blur(radius = 50.dp)
            } else {
                Modifier.fillMaxSize()
            }

            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = backgroundModifier
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        if (isWearable) {
            // Wearable layout
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                PlayerControls(
                    isWearable = true,
                    displayAlbumArt = displayAlbumArt,
                    title = title.toString(),
                    artist = artist.toString(),
                    platform = platform,
                    showPlatformTag = showPlatformTag,
                    totalDuration = totalDuration,
                    currentPosition = currentPosition,
                    isDragging = isSliderDragging,
                    sliderPosition = sliderPosition,
                    isPlaying = isPlaying,
                    repeatMode = repeatMode,
                    shuffleMode = shuffleMode,
                    onValueChange = { sliderPosition = it },
                    onValueChangeFinished = { viewModel.seekTo(sliderPosition.toLong()) },
                    onDragChange = { dragging -> isSliderDragging = dragging },
                    onPlayPauseClick = { viewModel.onPlayPauseClick() },
                    onPreviousClick = { viewModel.skipToPrevious() },
                    onNextClick = { viewModel.skipToNext() },
                    onPlaybackModeClick = { viewModel.cyclePlaybackMode() }
                )
                Spacer(modifier = Modifier.height(16.dp))
                LyricsScreen(viewModel, isWearable = true)
            }
        } else {
            // Phone/Tablet layout
            val pagerState = rememberPagerState(pageCount = { 2 })
            HorizontalPager(state = pagerState) {
                when (it) {
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            PlayerControls(
                                isWearable = false,
                                displayAlbumArt = displayAlbumArt,
                                title = title.toString(),
                                artist = artist.toString(),
                                platform = platform,
                                showPlatformTag = showPlatformTag,
                                totalDuration = totalDuration,
                                currentPosition = currentPosition,
                                isDragging = isSliderDragging,
                                sliderPosition = sliderPosition,
                                isPlaying = isPlaying,
                                repeatMode = repeatMode,
                                shuffleMode = shuffleMode,
                                onValueChange = { sliderPosition = it },
                                onValueChangeFinished = { viewModel.seekTo(sliderPosition.toLong()) },
                                onDragChange = { dragging -> isSliderDragging = dragging },
                                onPlayPauseClick = { viewModel.onPlayPauseClick() },
                                onPreviousClick = { viewModel.skipToPrevious() },
                                onNextClick = { viewModel.skipToNext() },
                                onPlaybackModeClick = { viewModel.cyclePlaybackMode() }
                            )
                        }
                    }
                    1 -> {
                        LyricsScreen(viewModel, isWearable = false)
                    }
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

@Composable
fun PlayerControls(
    isWearable: Boolean,
    displayAlbumArt: Any?,
    title: String,
    artist: String,
    platform: String?,
    showPlatformTag: Boolean,
    totalDuration: Long,
    currentPosition: Long,
    isDragging: Boolean,
    sliderPosition: Float,
    isPlaying: Boolean,
    repeatMode: Int,
    shuffleMode: Boolean,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    onDragChange: (Boolean) -> Unit = {},
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPlaybackModeClick: () -> Unit
) {
    val artSize = if (isWearable) 120.dp else 300.dp
    val titleStyle = if (isWearable) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
    val artistStyle = if (isWearable) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium

    Crossfade(
        targetState = displayAlbumArt,
        label = "AlbumArtCrossfade",
        animationSpec = tween(500)
    ) { art ->
        AsyncImage(
            model = art,
            contentDescription = "Album Art",
            modifier = Modifier
                .size(artSize)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(if (isWearable) 60.dp else 16.dp))
        )
    }
    Spacer(modifier = Modifier.height(if (isWearable) 16.dp else 32.dp))

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 4f),
        blurRadius = 8f
    )

    Text(
        text = title,
        style = titleStyle.copy(color = Color.White, shadow = textShadow),
        textAlign = TextAlign.Center
    )
    Text(
        text = artist,
        style = artistStyle.copy(color = Color.White.copy(alpha = 0.8f), shadow = textShadow),
        textAlign = TextAlign.Center
    )

    if (showPlatformTag && !platform.isNullOrBlank() && platform != "local") {
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .background(Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = platform,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White
            )
        }
    }

    Spacer(modifier = Modifier.height(if (isWearable) 16.dp else 32.dp))

    Column(modifier = Modifier.fillMaxWidth()) {
        val duration = totalDuration.toFloat().coerceAtLeast(1f)
        val position = if (isDragging) sliderPosition else currentPosition.toFloat()

        CustomProgressBar(
            value = position,
            maxValue = duration,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            onDragChange = onDragChange
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
        IconButton(onClick = onPlaybackModeClick) {
            val icon = if (shuffleMode) {
                Icons.Default.Shuffle
            } else {
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                    else -> Icons.Default.Repeat
                }
            }
            val tint = if (repeatMode != Player.REPEAT_MODE_OFF || shuffleMode) Color.White else Color.White.copy(alpha = 0.5f)
            Icon(icon, contentDescription = "Playback Mode", tint = tint)
        }

        IconButton(onClick = onPreviousClick) {
            Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = Color.White)
        }
        IconButton(onClick = onPlayPauseClick) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
        IconButton(onClick = onNextClick) {
            Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = Color.White)
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
    onValueChangeFinished: () -> Unit,
    onDragChange: (Boolean) -> Unit = {}
) {
    var isDragging by remember { mutableStateOf(false) }
    val thumbRadius by animateDpAsState(targetValue = if (isDragging) 6.dp else 3.dp, label = "thumbRadius")
    val trackHeight by animateDpAsState(targetValue = if (isDragging) 2.dp else 1.dp, label = "trackHeight")

    LaunchedEffect(isDragging) {
        onDragChange(isDragging)
    }

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
                    onPress = {
                        isDragging = true
                        val newFraction = (it.x / size.width).coerceIn(0f, 1f)
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

@Composable
fun LyricsScreen(viewModel: PlayerViewModel, isWearable: Boolean) {
    val lyrics by viewModel.lyrics.collectAsState()
    val currentLyricIndex by viewModel.currentLyricIndex.collectAsState()
    val listState = rememberScrollState()

    LaunchedEffect(currentLyricIndex) {
        if (currentLyricIndex > 0) {
            // This is a simple scroll, a more advanced version could calculate the exact position
            listState.animateScrollTo(currentLyricIndex * 50) // Approximate scroll
        }
    }

    if (lyrics.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No lyrics found", style = MaterialTheme.typography.bodyLarge)
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(listState)
                .padding(horizontal = 16.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            lyrics.forEachIndexed { index, line ->
                val style = if (index == currentLyricIndex) {
                    if (isWearable) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall
                } else {
                    if (isWearable) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium
                }
                val color = if (index == currentLyricIndex) {
                    Color.White
                } else {
                    Color.White.copy(alpha = 0.6f)
                }
                Text(
                    text = line.text,
                    style = style,
                    color = color,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = if (isWearable) 4.dp else 8.dp)
                )
            }
        }
    }
}
