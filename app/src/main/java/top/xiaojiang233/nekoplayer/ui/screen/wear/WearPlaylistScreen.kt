package top.xiaojiang233.nekoplayer.ui.screen.wear

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Checkbox
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.model.Playlist
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun WearPlaylistScreen(
    playlistId: String,
    homeViewModel: HomeViewModel,
    playerViewModel: PlayerViewModel,
    onSongClick: () -> Unit
) {
    val playlists by homeViewModel.playlists.collectAsState()
    val localSongs by homeViewModel.localSongs.collectAsState()

    val playlist = remember(playlists, playlistId) { playlists.find { it.id == playlistId } }

    val playlistSongs = remember(playlist, localSongs) {
        playlist?.songIds?.mapNotNull { id ->
            localSongs.find { song -> song.id == id }
        } ?: emptyList()
    }

    var selectedSong by remember { mutableStateOf<OnlineSong?>(null) }
    var showAddSongsDialog by remember { mutableStateOf(false) }

    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 24.dp, start = 8.dp, end = 8.dp, bottom = 24.dp)
        ) {
            item {
                ListHeader {
                    Text(text = playlist?.name ?: stringResource(R.string.playlists_title))
                }
            }

            // Playlist cover grid (show first 4 covers)
            item {
                val coverSongs = playlistSongs.take(4)
                if (coverSongs.isNotEmpty()) {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .padding(vertical = 8.dp),
                        userScrollEnabled = false
                    ) {
                        items(coverSongs) { song ->
                            AsyncImage(
                                model = song.coverUrl,
                                contentDescription = stringResource(R.string.cover_image),
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }

            // Action buttons
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactChip(
                        label = { Text(stringResource(R.string.play_all)) },
                        icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            if (playlistSongs.isNotEmpty()) {
                                playerViewModel.playPlaylist(playlistSongs, 0)
                                onSongClick()
                            }
                        },
                        enabled = playlistSongs.isNotEmpty()
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CompactChip(
                        label = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { showAddSongsDialog = true },
                        modifier = Modifier.weight(1f),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
            }

            // Song list
            items(playlistSongs) { song ->
                Card(
                    onClick = {
                        val index = playlistSongs.indexOf(song)
                        if (index >= 0) {
                            playerViewModel.playPlaylist(playlistSongs, index)
                            onSongClick()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            onClick = {
                                val index = playlistSongs.indexOf(song)
                                if (index >= 0) {
                                    playerViewModel.playPlaylist(playlistSongs, index)
                                    onSongClick()
                                }
                            },
                            onLongClick = {
                                selectedSong = song
                            }
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.coverUrl,
                            contentDescription = stringResource(R.string.cover_image),
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.body1,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = song.artist,
                                style = MaterialTheme.typography.body2,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }

    if (selectedSong != null) {
        Dialog(
            showDialog = true,
            onDismissRequest = { selectedSong = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = selectedSong?.title ?: "",
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(16.dp))
                Chip(
                    label = { Text(stringResource(R.string.delete)) },
                    onClick = {
                        if (playlist != null && selectedSong != null) {
                            homeViewModel.removeSongFromPlaylist(playlist, selectedSong!!.id)
                        }
                        selectedSong = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(),
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
        }
    }

    if (showAddSongsDialog) {
        val availableToAdd = localSongs.filter { song ->
            playlist?.songIds?.contains(song.id) == false
        }

        Dialog(
            showDialog = true,
            onDismissRequest = { showAddSongsDialog = false }
        ) {
            val selectionListState = rememberScalingLazyListState()
            var selectedIds by remember { mutableStateOf(setOf<String>()) }

            Column(modifier = Modifier.fillMaxSize()) {
                ScalingLazyColumn(
                    state = selectionListState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(top = 24.dp, bottom = 8.dp)
                ) {
                    item {
                        ListHeader { Text(stringResource(R.string.add_songs)) }
                    }
                    if (availableToAdd.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.no_songs_available),
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(availableToAdd) { song ->
                            ToggleChip(
                                checked = selectedIds.contains(song.id),
                                onCheckedChange = { checked ->
                                    selectedIds = if (checked) {
                                        selectedIds + song.id
                                    } else {
                                        selectedIds - song.id
                                    }
                                },
                                label = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                toggleControl = {
                                    Checkbox(
                                        checked = selectedIds.contains(song.id),
                                        onCheckedChange = null
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                if (availableToAdd.isNotEmpty()) {
                    Chip(
                        label = { Text(stringResource(R.string.add)) },
                        onClick = {
                            val selectedSongs = availableToAdd.filter { it.id in selectedIds }
                            if (playlist != null) {
                                homeViewModel.addSongsToPlaylist(playlist, selectedSongs)
                            }
                            showAddSongsDialog = false
                        },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        enabled = selectedIds.isNotEmpty()
                    )
                }
            }
        }
    }
}
