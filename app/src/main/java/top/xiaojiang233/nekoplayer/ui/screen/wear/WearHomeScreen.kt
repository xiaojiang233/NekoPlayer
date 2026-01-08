package top.xiaojiang233.nekoplayer.ui.screen

import android.app.AlertDialog
import android.app.RemoteInput
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.widget.EditText
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Card
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
import androidx.wear.compose.material.TitleCard
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.compose.material.dialog.Dialog
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.model.Playlist
import top.xiaojiang233.nekoplayer.ui.components.wear.WearLocalMusicSelectionDialog
import top.xiaojiang233.nekoplayer.utils.launchTextInput
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel

@OptIn(ExperimentalFoundationApi::class)
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
    val showLocalMusicSelection by homeViewModel.showLocalMusicSelection.collectAsState()
    val availableLocalSongs by homeViewModel.availableLocalSongs.collectAsState()

    var selectedSong by remember { mutableStateOf<OnlineSong?>(null) }
    var showPlaylistOptionsDialog by remember { mutableStateOf<Playlist?>(null) }
    val context = LocalContext.current

    val voiceInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            val results: Bundle = RemoteInput.getResultsFromIntent(data)
            results.getCharSequence("text_input")?.toString()?.let {
                homeViewModel.createPlaylist(it)
            }
        }
    }

    val renameVoiceLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            val results: Bundle = RemoteInput.getResultsFromIntent(data)
            val newName = results.getCharSequence("text_input")?.toString()
            val playlist = showPlaylistOptionsDialog
            if (!newName.isNullOrBlank() && playlist != null) {
                homeViewModel.renamePlaylist(playlist, newName)
            }
        }
    }

    val listState = rememberScalingLazyListState()
    val view = LocalView.current

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
            if (nowPlaying != null) {
                item {
                    TitleCard(
                        onClick = onPlayerClick,
                        title = { Text(stringResource(R.string.now_playing)) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${nowPlaying?.mediaMetadata?.title ?: stringResource(R.string.unknown_title)} - ${nowPlaying?.mediaMetadata?.artist ?: stringResource(R.string.unknown_artist)}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item { ListHeader { Text(stringResource(R.string.library)) } }

            item {
                Chip(
                    label = { Text(stringResource(R.string.search)) },
                    onClick = onSearchClick,
                    icon = { Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text(stringResource(R.string.import_songs)) },
                    onClick = onAddMusicClick,
                    icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.import_songs)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                Chip(
                    label = { Text(stringResource(R.string.settings)) },
                    onClick = onSettingsClick,
                    icon = { Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.settings)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ListHeader { Text(stringResource(R.string.playlists_title)) }
                    CompactChip(
                        label = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.new_playlist)) },
                        onClick = {
                            launchTextInput(
                                context = context,
                                launcher = voiceInputLauncher,
                                remoteInputKey = "text_input",
                                label = context.getString(R.string.new_playlist)
                            ) { newName ->
                                homeViewModel.createPlaylist(newName)
                            }
                        }
                    )
                }
            }

            items(playlists) { playlist ->
                Card(
                    onClick = { onPlaylistClick(playlist.id) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            onClick = { onPlaylistClick(playlist.id) },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                showPlaylistOptionsDialog = playlist
                            }
                        )
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = playlist.name,
                            style = MaterialTheme.typography.body1,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = stringResource(R.string.song_count, playlist.songIds.size),
                            style = MaterialTheme.typography.body2,
                            maxLines = 1
                        )
                    }
                }
            }

            item { ListHeader { Text(stringResource(R.string.songs_title)) } }

            items(localSongs) { song ->
                Card(
                    onClick = {
                        val index = localSongs.indexOf(song)
                        if (index >= 0) {
                            playerViewModel.playPlaylist(localSongs, index)
                            onPlayerClick()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .combinedClickable(
                            onClick = {
                                val index = localSongs.indexOf(song)
                                if (index >= 0) {
                                    playerViewModel.playPlaylist(localSongs, index)
                                    onPlayerClick()
                                }
                            },
                            onLongClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
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
        val song = selectedSong!!
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
                    text = song.title,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Chip(
                    label = { Text(stringResource(R.string.delete)) },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        homeViewModel.deleteSong(song)
                        selectedSong = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )

                Chip(
                    label = { Text(stringResource(R.string.cancel)) },
                    onClick = { selectedSong = null },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }

    if (showPlaylistOptionsDialog != null) {
        val playlist = showPlaylistOptionsDialog!!
        Dialog(
            showDialog = true,
            onDismissRequest = { showPlaylistOptionsDialog = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.title3,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Chip(
                    label = { Text(stringResource(R.string.rename)) },
                    icon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        showPlaylistOptionsDialog = null
                        launchTextInput(
                            context = context,
                            launcher = renameVoiceLauncher,
                            remoteInputKey = "text_input",
                            label = context.getString(R.string.rename_playlist),
                            initialValue = playlist.name
                        ) { newName ->
                            homeViewModel.renamePlaylist(playlist, newName)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors()
                )

                Chip(
                    label = { Text(stringResource(R.string.delete)) },
                    icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        homeViewModel.deletePlaylist(playlist)
                        showPlaylistOptionsDialog = null
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ChipDefaults.secondaryChipColors()
                )

                Chip(
                    label = { Text(stringResource(R.string.cancel)) },
                    onClick = { showPlaylistOptionsDialog = null },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = ChipDefaults.primaryChipColors()
                )
            }
        }
    }

    if (showLocalMusicSelection) {
        WearLocalMusicSelectionDialog(
            availableSongs = availableLocalSongs,
            onDismiss = { homeViewModel.setShowLocalMusicSelection(false) },
            onConfirm = { selectedSongs ->
                homeViewModel.addLocalSongs(selectedSongs)
            }
        )
    }
}
