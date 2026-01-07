package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.*
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings

@Composable
fun WearHomeScreen(
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onSearchClick: () -> Unit,
    onPlayerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddMusicClick: () -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val localSongs by homeViewModel.localSongs.collectAsState()
    val playlists by homeViewModel.playlists.collectAsState()
    val nowPlaying by playerViewModel.nowPlaying.collectAsState()

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(top = 24.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
        ) {
            if (nowPlaying != null) {
                item {
                    Card(
                        onClick = onPlayerClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "Now Playing: ${nowPlaying?.mediaMetadata?.title ?: ""}",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
            }

            item { ListHeader { Text("Library") } }

            item {
                Chip(
                    label = { Text("Search") },
                    onClick = onSearchClick,
                    icon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text("Settings") },
                    onClick = onSettingsClick,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { ListHeader { Text("Playlists") } }
            items(playlists) { playlist ->
                Chip(
                    label = { Text(playlist.name) },
                    onClick = { onPlaylistClick(playlist.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item { ListHeader { Text("Songs") } }
            items(localSongs) { song ->
                Chip(
                    label = { Text(song.title) },
                    onClick = {
                        val index = localSongs.indexOf(song)
                        if (index >= 0) {
                            playerViewModel.playPlaylist(localSongs, index)
                            onPlayerClick()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
