package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel
import java.io.File
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import top.xiaojiang233.nekoplayer.util.findActivity

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
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val view = LocalView.current
    DisposableEffect(isLandscape) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            if (isLandscape) {
                insetsController.hide(WindowInsetsCompat.Type.systemBars())
                insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val window = view.context.findActivity()?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    val albumArtUrl = nowPlaying?.mediaMetadata?.artworkUri
    val title = nowPlaying?.mediaMetadata?.title ?: ""
    val artist = nowPlaying?.mediaMetadata?.artist ?: ""
    val platform = nowPlaying?.requestMetadata?.extras?.getString("platform")

    // Fallback for album art if it's a local file without explicit artwork URI in metadata
    val displayAlbumArt = albumArtUrl ?: nowPlaying?.mediaId?.let { _ ->
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
        Crossfade(
            targetState = displayAlbumArt,
            label = "BackgroundCrossfade",
            animationSpec = tween(500)
        ) { art ->
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(radius = 50.dp)
            )
        }
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    PlayerControls(
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
                        onValueChange = {
                            sliderPosition = it
                        },
                        onValueChangeFinished = {
                            viewModel.seekTo(sliderPosition.toLong())
                        },
                        onDragChange = { dragging -> isSliderDragging = dragging },
                        onPlayPauseClick = { viewModel.onPlayPauseClick() },
                        onPreviousClick = { viewModel.skipToPrevious() },
                        onNextClick = { viewModel.skipToNext() },
                        onPlaybackModeClick = { viewModel.cyclePlaybackMode() }
                    )
                }
                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    LyricsScreen(viewModel)
                }
            }
        } else {
            HorizontalPager(state = pagerState) {
                when (it) {
                    0 -> {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            PlayerControls(
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
                                onValueChange = {
                                    sliderPosition = it
                                },
                                onValueChangeFinished = {
                                    viewModel.seekTo(sliderPosition.toLong())
                                },
                                onDragChange = { dragging -> isSliderDragging = dragging },
                                onPlayPauseClick = { viewModel.onPlayPauseClick() },
                                onPreviousClick = { viewModel.skipToPrevious() },
                                onNextClick = { viewModel.skipToNext() },
                                onPlaybackModeClick = { viewModel.cyclePlaybackMode() }
                            )
                        }
                    }
                    1 -> {
                        LyricsScreen(viewModel)
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

@Composable
fun PlayerControls(
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    Crossfade(
        targetState = displayAlbumArt,
        label = "AlbumArtCrossfade",
        animationSpec = tween(500)
    ) { art ->
        AsyncImage(
            model = art,
            contentDescription = "Album Art",
            modifier = Modifier
                .fillMaxWidth(if (isLandscape) 0.4f else 0.8f)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
        )
    }
    Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.5f),
        offset = Offset(0f, 4f),
        blurRadius = 8f
    )

    Text(
        text = title,
        style = MaterialTheme.typography.headlineMedium.copy(
            color = Color.White,
            shadow = textShadow
        )
    )
    Text(
        text = artist,
        style = MaterialTheme.typography.titleMedium.copy(
            color = Color.White.copy(alpha = 0.8f),
            shadow = textShadow
        )
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

    Spacer(modifier = Modifier.height(if (isLandscape) 16.dp else 32.dp))

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
        if (isLandscape) {
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

        if (isLandscape) {
             // Placeholder to balance the row if needed, or just let SpaceEvenly handle it.
             // But wait, if I put the mode button on the left, maybe I should put something on the right or just leave it.
             // Let's just add a dummy spacer or nothing. SpaceEvenly will distribute 4 items.
             // Actually, let's put the mode button on the right side of Next button if we want it to look like a row of controls.
             // Or maybe on the far left?
             // Let's try adding it to the row.
             Spacer(modifier = Modifier.size(48.dp)) // Balance the size of Play button roughly or just empty
        }
    }

    if (!isLandscape) {
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
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
        }
    }
}
