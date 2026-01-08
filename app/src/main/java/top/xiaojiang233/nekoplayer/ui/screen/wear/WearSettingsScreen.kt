package top.xiaojiang233.nekoplayer.ui.screen.wear

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Slider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold

import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.utils.OtherUtils
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel

@Composable
fun WearSettingsScreen(
    onBackClick: () -> Unit,
    settingsViewModel: SettingsViewModel = viewModel()
) {
    val showPlatformTag by settingsViewModel.showPlatformTag.collectAsState()
    val lyricsFontSize by settingsViewModel.lyricsFontSize.collectAsState()
    val lyricsBlurIntensity by settingsViewModel.lyricsBlurIntensity.collectAsState()
    val playbackDelay by settingsViewModel.playbackDelay.collectAsState()
    val fadeInDuration by settingsViewModel.fadeInDuration.collectAsState()
    val watchScale by settingsViewModel.watchScale.collectAsState()
    val listState = rememberScalingLazyListState()
    val context = LocalContext.current
    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 24.dp, start = 8.dp, end = 8.dp, bottom = 24.dp)
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.title_settings))
                }
            }

            item {
                ListHeader {
                    Text(text = stringResource(R.string.title_player))
                }
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.watch_scale, String.format("%.1f", watchScale))) },
                    onClick = {
                        val min = 0.5f
                        val max = 2.0f
                        val step = 0.25f

                        val newScale = if (watchScale + step <= max) {
                            watchScale + step
                        } else {
                            min
                        }

                        settingsViewModel.setWatchScale(newScale)


            },
                    modifier = Modifier.fillMaxWidth(),
                    secondaryLabel = { Text(stringResource(R.string.watch_scale_desc)) }
                )
            }

            item {
                ToggleChip(
                    checked = showPlatformTag,
                    onCheckedChange = { settingsViewModel.setShowPlatformTag(it) },
                    label = { Text(stringResource(R.string.show_platform_tag)) },
                    toggleControl = {
                        Switch(
                            checked = showPlatformTag,
                            enabled = true,
                            onCheckedChange = null // Handled by ToggleChip
                        )
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Text(stringResource(R.string.playback_delay, playbackDelay))
                Slider(
                    value = playbackDelay.toFloat(),
                    onValueChange = { settingsViewModel.setPlaybackDelay(it.toInt()) },
                    valueRange = 0f..1000f
                )
            }

            item {
                Text(stringResource(R.string.fade_in_duration, fadeInDuration))
                Slider(
                    value = fadeInDuration.toFloat(),
                    onValueChange = { settingsViewModel.setFadeInDuration(it.toInt()) },
                    valueRange = 0f..1000f
                )
            }

            item {
                ListHeader {
                    Text(text = stringResource(R.string.title_lyrics))
                }
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.font_size, lyricsFontSize.toInt())) },
                    onClick = {
                        val newSize: Float = when (lyricsFontSize.toInt()) {
                            in 12..19 -> 20f
                            in 20..27 -> 28f
                            in 28..35 -> 36f
                            in 36..43 -> 44f
                            else -> 12f
                        }
                        settingsViewModel.setLyricsFontSize(newSize)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.blur_intensity, lyricsBlurIntensity.toInt())) },
                    onClick = {
                        val newIntensity = when (lyricsBlurIntensity.toInt()) {
                            in 0..2 -> 3f
                            in 3..5 -> 6f
                            in 6..8 -> 9f
                            in 9..11 -> 12f
                            else -> 0f
                        }
                        settingsViewModel.setLyricsBlurIntensity(newIntensity)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.clear_cache)) },
                    onClick = { settingsViewModel.clearCache() },
                    secondaryLabel = { Text(stringResource(R.string.clear_cache_desc)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                ListHeader {
                    Text(text = stringResource(R.string.title_about))
                }
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.app_name)) },
                    onClick = { /* No action */ },
                    secondaryLabel = { Text(stringResource(R.string.version, OtherUtils.getAppVersionName(context))) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.author)) },
                    onClick = { /* No action */ },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
