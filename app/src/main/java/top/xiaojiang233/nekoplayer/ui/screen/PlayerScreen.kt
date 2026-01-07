package top.xiaojiang233.nekoplayer.ui.screen

import android.os.Build
import androidx.compose.animation.Crossfade
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
import androidx.compose.material3.*
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.Player
import coil.compose.AsyncImage
import coil.request.ImageRequest
import java.io.File
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.util.findActivity
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel
import top.xiaojiang233.nekoplayer.ui.components.LyricsSearchDialog

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

    // Local slider state
    var sliderPosition by remember { mutableFloatStateOf(0f) }
    var isSliderDragging by remember { mutableStateOf(false) }

    LaunchedEffect(currentPosition) {
        if (!isSliderDragging) {
            sliderPosition = currentPosition.toFloat()
        }
    }

    val configuration = LocalConfiguration.current
    val isWearable = configuration.screenWidthDp < 300
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    // Hide system bars in landscape
    val view = LocalView.current
    if (!isWearable) {
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
    }

    val albumArtUrl = nowPlaying?.mediaMetadata?.artworkUri
    val title = nowPlaying?.mediaMetadata?.title ?: ""
    val artist = nowPlaying?.mediaMetadata?.artist ?: ""
    val platform = nowPlaying?.requestMetadata?.extras?.getString("platform")

    val displayAlbumArt = albumArtUrl ?: nowPlaying?.mediaId?.let {
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
            val context = LocalContext.current
            val imageModel = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                art
            } else {
                ImageRequest.Builder(context)
                    .data(art)
                    .size(200)
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
                LyricsScreen(viewModel)
            }
        } else {
            // Phone/Tablet layout
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
                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        LyricsScreen(viewModel)
                    }
                }
            } else {
                HorizontalPager(state = pagerState) { page ->
                    when (page) {
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
                            LyricsScreen(viewModel)
                        }
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
                contentDescription = stringResource(R.string.close),
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
    // Remove complex animations or optimize them
    val thumbRadius = if (isDragging) 6.dp else 3.dp
    val trackHeight = if (isDragging) 2.dp else 1.dp

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
        // Optimize value calculations
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
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE && !isWearable
    val screenHeight = configuration.screenHeightDp.dp
    val screenWidth = configuration.screenWidthDp.dp

    val artSize = if (isWearable) {
        120.dp
    } else if (isLandscape) {
        // Dynamic size based on screen height for landscape
        (screenHeight * 0.3f).coerceAtMost(200.dp)
    } else {
        // Dynamic size based on screen width for portrait
        (screenWidth * 0.75f).coerceAtMost(350.dp)
    }
    val titleStyle = if (isWearable) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium
    val artistStyle = if (isWearable) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.titleMedium

    Crossfade(
        targetState = displayAlbumArt,
        label = "AlbumArtCrossfade",
        animationSpec = tween(500)
    ) { art ->
        AsyncImage(
            model = art,
            contentDescription = stringResource(R.string.album_art),
            modifier = Modifier
                .size(artSize)
                .aspectRatio(1f)
                .clip(RoundedCornerShape(if (isWearable) 60.dp else 16.dp))
        )
    }

    Spacer(modifier = Modifier.height(if (isWearable) 16.dp else if (isLandscape) 8.dp else 32.dp))

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

    Spacer(modifier = Modifier.height(if (isWearable) 16.dp else if (isLandscape) 16.dp else 32.dp))

    Column(modifier = Modifier.fillMaxWidth()) {
        val duration = totalDuration.coerceAtLeast(1L).toFloat()
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
        horizontalArrangement = if (isLandscape) Arrangement.SpaceBetween else Arrangement.SpaceEvenly,
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
                Icon(icon, contentDescription = stringResource(R.string.playback_mode), tint = tint)
            }
        }

        IconButton(onClick = onPreviousClick) {
            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous), tint = Color.White)
        }
        IconButton(onClick = onPlayPauseClick) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
                modifier = Modifier.size(48.dp),
                tint = Color.White
            )
        }
        IconButton(onClick = onNextClick) {
            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next), tint = Color.White)
        }

        if (isLandscape) {
             Spacer(modifier = Modifier.size(48.dp))
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
                Icon(icon, contentDescription = stringResource(R.string.playback_mode), tint = tint)
            }
        }
    }
}
