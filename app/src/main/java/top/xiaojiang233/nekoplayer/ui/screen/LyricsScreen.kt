package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.ui.components.LyricsSearchDialog
import top.xiaojiang233.nekoplayer.utils.findActivity
import kotlin.math.abs
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel

@Composable
fun LyricsScreen(
    viewModel: PlayerViewModel,
    settingsViewModel: SettingsViewModel = viewModel(),
    isWearableOverride: Boolean? = null
) {
    val lyrics by viewModel.lyrics.collectAsState()
    val currentPosition by viewModel.currentPosition.collectAsState()
    val lyricsFontSize by settingsViewModel.lyricsFontSize.collectAsState()
    val lyricsFontFamilyName by settingsViewModel.lyricsFontFamily.collectAsState()
    val lyricsBlurIntensity by settingsViewModel.lyricsBlurIntensity.collectAsState()
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    var isUserScrolling by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }

    val configuration = LocalConfiguration.current
    val context = androidx.compose.ui.platform.LocalContext.current

    // Detect if running on wearable
    val isWearable = isWearableOverride ?: remember(configuration) {
        val pm = context.packageManager
        val isWatchFeature = pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_WATCH)

        val metrics = context.resources.displayMetrics
        val widthInches = metrics.widthPixels / metrics.xdpi
        val heightInches = metrics.heightPixels / metrics.ydpi
        val diagonalInches = kotlin.math.sqrt(widthInches * widthInches + heightInches * heightInches)

        isWatchFeature || diagonalInches < 2.4 || configuration.screenWidthDp < 250
    }

    // Adjust font size for wearable
    val adjustedFontSize = if (isWearable) {
        (lyricsFontSize * 0.6f).coerceAtLeast(12f)
    } else {
        lyricsFontSize.toFloat()
    }

    if (showSearchDialog) {
        val searchResults by viewModel.lyricSearchResults.collectAsState()
        val isSearching by viewModel.isSearchingLyrics.collectAsState()
        val currentSong = viewModel.nowPlaying.collectAsState().value

        val initialQuery = currentSong?.mediaMetadata?.let { "${it.title} ${it.artist}" } ?: ""

        LyricsSearchDialog(
            initialQuery = initialQuery,
            onDismiss = {
                showSearchDialog = false
                viewModel.clearSearchResults()
            },
            onSearch = { query -> viewModel.searchLyrics(query) },
            results = searchResults,
            isSearching = isSearching,
            onSelect = { song ->
                viewModel.applyMetadata(song)
                showSearchDialog = false
                viewModel.clearSearchResults()
            },
            onImportLrc = { uri ->
                viewModel.importLrcFile(uri)
                showSearchDialog = false
            }
        )
    }

    val fontFamily = when (lyricsFontFamilyName) {
        "Serif" -> FontFamily.Serif
        "SansSerif" -> FontFamily.SansSerif
        "Monospace" -> FontFamily.Monospace
        "Cursive" -> FontFamily.Cursive
        else -> FontFamily.Default
    }

    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

    val view = LocalView.current
    if (!view.isInEditMode) { // Optional check to avoid preview crashes
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

    val displayLyrics = remember(lyrics) {
        buildList {
            if (lyrics.isNotEmpty()) {
                val firstLyricTime = lyrics[0].time
                // Treat as prelude if first lyric is not immediate (e.g. > 1s)
                if (firstLyricTime > 1000) {
                    add(LyricItem.Interlude(time = 0, endTime = firstLyricTime))
                }
            }
            lyrics.forEachIndexed { index, lyric ->
                add(LyricItem.Content(lyric.time, lyric.text, lyric.translation))
                val nextTime = lyrics.getOrNull(index + 1)?.time
                if (nextTime != null && nextTime - lyric.time >= 10_000) {
                    // Insert interlude item starting 5s after current line
                    add(LyricItem.Interlude(time = lyric.time + 5000, endTime = nextTime))
                }
            }
        }
    }

    val currentDisplayIndex by remember(currentPosition, displayLyrics) {
        derivedStateOf {
            // Add a small offset (200ms early) to compensate for visual delay
            val adjustedPosition = currentPosition + 200
            displayLyrics.indexOfLast { it.time <= adjustedPosition }.coerceAtLeast(-1)
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) {
            isUserScrolling = true
        }
    }

    LaunchedEffect(isUserScrolling, isDragged, listState.isScrollInProgress) {
        if (isUserScrolling && !isDragged && !listState.isScrollInProgress) {
            delay(3000)
            isUserScrolling = false
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val containerHeight = maxHeight

        // Check if current lyric has translation to adjust padding dynamically
        val currentHasTranslation = remember(currentDisplayIndex, displayLyrics) {
            if (currentDisplayIndex >= 0 && currentDisplayIndex < displayLyrics.size) {
                val item = displayLyrics[currentDisplayIndex]
                item is LyricItem.Content && !item.translation.isNullOrBlank()
            } else {
                false
            }
        }

        // Adjust padding for wearable (less padding for round screens)
        // Dynamically adjust padding in both portrait and landscape modes when current lyric has translation
        val verticalPadding = if (isWearable) {
            containerHeight * 0.35f
        } else if (isLandscape) {
            // Landscape mode: also adjust padding when translation exists
            if (currentHasTranslation) {
                containerHeight * 0.30f
            } else {
                containerHeight * 0.40f
            }
        } else {
            // Portrait mode: increase padding when translation exists to keep focus higher
            if (currentHasTranslation) {
                containerHeight * 0.35f
            } else {
                containerHeight * 0.40f
            }
        }

        // Horizontal padding - less for wearable to avoid clipping on round screens
        val horizontalPadding = if (isWearable) 16.dp else 32.dp

        LaunchedEffect(currentDisplayIndex, isUserScrolling) {
            if (currentDisplayIndex >= 0 && !isUserScrolling) {
                 listState.animateScrollToItem(
                    index = currentDisplayIndex,
                    scrollOffset = 0
                )
            }
        }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        if (displayLyrics.isEmpty()) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(R.string.no_lyrics),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { showSearchDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.2f),
                        contentColor = Color.White
                    )
                ) {
                    Text(text = stringResource(R.string.search_lyrics))
                }
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(vertical = verticalPadding),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(displayLyrics) { index, item ->
                    val isCurrent = index == currentDisplayIndex
                    val distance = abs(index - currentDisplayIndex)

                    val scale by animateFloatAsState(
                        targetValue = if (isCurrent) 1f else 0.95f,
                        animationSpec = tween(durationMillis = 300),
                        label = "scale"
                    )

                    val targetAlpha = if (isUserScrolling) 0.6f else (1f - (distance * 0.15f)).coerceIn(0.1f, 1f)
                    val animatedAlpha by animateFloatAsState(
                        targetValue = if (isCurrent) 1f else targetAlpha,
                        animationSpec = tween(durationMillis = 300),
                        label = "alpha"
                    )

                    val targetBlur = if (isUserScrolling) 1f else if (isCurrent) 0f else (distance * 2f * (lyricsBlurIntensity / 10f)).coerceAtMost(lyricsBlurIntensity)
                    val blurRadius by animateFloatAsState(
                        targetValue = targetBlur,
                        animationSpec = tween(durationMillis = 300),
                        label = "blur"
                    )

                    when (item) {
                        is LyricItem.Content -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 12.dp)
                                    .graphicsLayer {
                                        alpha = 0.99f
                                        clip = false
                                    }
                                    .scale(scale)
                                    .alpha(animatedAlpha)
                                    .blur(blurRadius.dp)
                                    .clickable {
                                        viewModel.seekTo(item.time)
                                        isUserScrolling = false
                                    }
                            ) {
                                // Main lyric text (original)
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.headlineMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = adjustedFontSize.sp,
                                        lineHeight = (adjustedFontSize * 1.4).sp,
                                        fontFamily = fontFamily
                                    ),
                                    color = Color.White,
                                    textAlign = TextAlign.Start,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Translation text (if exists) - smaller font
                                if (!item.translation.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.translation,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = FontWeight.Normal,
                                            fontSize = (adjustedFontSize * 0.7f).sp,
                                            lineHeight = (adjustedFontSize * 0.7f * 1.3).sp,
                                            fontFamily = fontFamily
                                        ),
                                        color = Color.White.copy(alpha = 0.75f),
                                        textAlign = TextAlign.Start,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                        is LyricItem.Interlude -> {
                            val duration = item.endTime - item.time
                            val progress by remember(currentPosition, item) {
                                derivedStateOf {
                                    ((currentPosition - item.time).toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                                }
                            }

                            // Pulse animation for the dots
                            val infiniteTransition = rememberInfiniteTransition(label = "dots")
                            val dotPulseAlpha by infiniteTransition.animateFloat(
                                initialValue = 0.5f,
                                targetValue = 1f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(1000),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = "dotPulseAlpha"
                            )

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = horizontalPadding, vertical = 12.dp)
                                    .graphicsLayer {
                                        alpha = 0.99f
                                        clip = false
                                    }
                                    .scale(scale)
                                    .alpha(animatedAlpha)
                                    .blur(blurRadius.dp)
                                    .clickable {
                                        viewModel.seekTo(item.time)
                                        isUserScrolling = false
                                    },
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val dotSize = (adjustedFontSize * 0.8).dp
                                val spacing = if (isWearable) 12.dp else 16.dp

                                for (i in 0 until 3) {
                                    // Make dots disappear faster and earlier for smoother transition
                                    // Dot 2 (rightmost) disappears at 25% progress
                                    // Dot 1 (middle) disappears at 50% progress
                                    // Dot 0 (leftmost) disappears at 75% progress
                                    val disappearThreshold = (3 - i) * 0.25f
                                    val isVisible = progress < disappearThreshold

                                    val fadeAlpha by animateFloatAsState(
                                        targetValue = if (isVisible) 1f else 0f,
                                        animationSpec = tween(durationMillis = 150, easing = LinearEasing),
                                        label = "fadeAlpha$i"
                                    )

                                    if (fadeAlpha > 0f) {
                                        val currentDotAlpha = if (isCurrent) dotPulseAlpha * fadeAlpha else 0.5f * fadeAlpha
                                        Box(
                                            modifier = Modifier
                                                .size(dotSize)
                                                .background(Color.White.copy(alpha = currentDotAlpha), CircleShape)
                                        )
                                        Spacer(modifier = Modifier.width(spacing))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

sealed interface LyricItem {
    val time: Long
    data class Content(override val time: Long, val text: String, val translation: String? = null) : LyricItem
    data class Interlude(override val time: Long, val endTime: Long) : LyricItem
}
