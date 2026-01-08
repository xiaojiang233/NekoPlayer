package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.model.Playlist
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun PlaylistScreen(
    playlistId: String,
    onBackClick: () -> Unit,
    onPlayerClick: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    playerViewModel: PlayerViewModel
) {
    val playlists by homeViewModel.playlists.collectAsState()
    val playlist = playlists.find { it.id == playlistId }
    val localSongs by homeViewModel.localSongs.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val nowPlaying by playerViewModel.nowPlaying.collectAsState()

    var showAddSongsDialog by remember { mutableStateOf(false) }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }

    // Drag and Drop State
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    val itemHeight = 72.dp // Approximate height of SongItem
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    if (playlist == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.playlist_not_found))
            Button(onClick = onBackClick) { Text(stringResource(R.string.go_back)) }
        }
        return
    }

    val playlistSongs = remember(playlist, localSongs) {
        playlist.songIds.mapNotNull { id -> localSongs.find { it.id == id } }
    }

    if (showAddSongsDialog) {
        val songsToAdd = remember { mutableStateOf(setOf<String>()) }
        val availableSongs = remember(localSongs, playlist.songIds) {
            localSongs.filter { it.id !in playlist.songIds }
        }

        AlertDialog(
            onDismissRequest = { showAddSongsDialog = false },
            title = { Text(stringResource(R.string.add_songs)) },
            text = {
                if (availableSongs.isEmpty()) {
                    Text(stringResource(R.string.no_songs_available))
                } else {
                    LazyColumn(modifier = Modifier.height(300.dp)) {
                        items(availableSongs) { song ->
                            val isSelected = songsToAdd.value.contains(song.id)
                            ListItem(
                                headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { checked ->
                                            if (checked) {
                                                songsToAdd.value += song.id
                                            } else {
                                                songsToAdd.value -= song.id
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    if (isSelected) {
                                        songsToAdd.value -= song.id
                                    } else {
                                        songsToAdd.value += song.id
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selectedSongs = availableSongs.filter { it.id in songsToAdd.value }
                        if (selectedSongs.isNotEmpty()) {
                            homeViewModel.addSongsToPlaylist(playlist, selectedSongs)
                        }
                        showAddSongsDialog = false
                    },
                    enabled = availableSongs.isNotEmpty()
                ) {
                    Text(stringResource(R.string.add))
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSongsDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text(stringResource(R.string.selected_count, selectedSongIds.size))
                    } else {
                        Text(playlist.name)
                    }
                },
                navigationIcon = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            isSelectionMode = false
                            selectedSongIds = emptySet()
                        }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close_selection))
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedSongIds.forEach { id ->
                                homeViewModel.removeSongFromPlaylist(playlist, id)
                            }
                            isSelectionMode = false
                            selectedSongIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.remove_selected))
                        }
                    } else {
                        IconButton(onClick = { showAddSongsDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_songs))
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (nowPlaying != null) 80.dp else 0.dp)
            ) {
                item {
                    PlaylistHeader(
                        playlist = playlist,
                        songs = playlistSongs,
                        onPlayAll = {
                            if (playlistSongs.isNotEmpty()) {
                                playerViewModel.playPlaylist(playlistSongs, 0)
                                onPlayerClick()
                            }
                        }
                    )
                }

                itemsIndexed(items = playlistSongs, key = { _, item: OnlineSong -> item.id }) { index: Int, song: OnlineSong ->
                    val isDragging = index == draggingItemIndex
                    val offset = if (isDragging) draggingItemOffset else 0f
                    val zIndex = if (isDragging) 1f else 0f
                    val isSelected = selectedSongIds.contains(song.id)

                    Box(
                        modifier = Modifier
                            .zIndex(zIndex)
                            .graphicsLayer {
                                translationY = offset
                            }
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            selectedSongIds = if (isSelected) {
                                                selectedSongIds - song.id
                                            } else {
                                                selectedSongIds + song.id
                                            }
                                        } else {
                                            playerViewModel.playPlaylist(playlistSongs, index)
                                            onPlayerClick()
                                        }
                                    },
                                    onLongClick = {
                                        if (!isSelectionMode) {
                                            isSelectionMode = true
                                            selectedSongIds = setOf(song.id)
                                        }
                                    }
                                ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
                        ) {
                            ListItem(
                                headlineContent = { Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                leadingContent = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        if (isSelectionMode) {
                                            Checkbox(
                                                checked = isSelected,
                                                onCheckedChange = null
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.DragIndicator,
                                                contentDescription = stringResource(R.string.drag),
                                                modifier = Modifier
                                                    .padding(end = 8.dp)
                                                    .pointerInput(Unit) {
                                                        detectDragGestures(
                                                            onDragStart = { draggingItemIndex = index },
                                                            onDrag = { change, dragAmount ->
                                                                change.consume()
                                                                draggingItemOffset += dragAmount.y
                                                                val currentDraggingIndex = draggingItemIndex ?: return@detectDragGestures
                                                                val currentOffset = draggingItemOffset

                                                                // Calculate if we should swap
                                                                val movedItems = (currentOffset / itemHeightPx).roundToInt()
                                                                val targetIndex = (currentDraggingIndex + movedItems).coerceIn(0, playlistSongs.lastIndex)

                                                                if (targetIndex != currentDraggingIndex) {
                                                                    val newSongs = playlistSongs.toMutableList()
                                                                    val item = newSongs.removeAt(currentDraggingIndex)
                                                                    newSongs.add(targetIndex, item)
                                                                    homeViewModel.updatePlaylistSongsOrder(playlist, newSongs)
                                                                    draggingItemIndex = targetIndex
                                                                    draggingItemOffset -= (targetIndex - currentDraggingIndex) * itemHeightPx
                                                                }
                                                            },
                                                            onDragEnd = {
                                                                draggingItemIndex = null
                                                                draggingItemOffset = 0f
                                                            },
                                                            onDragCancel = {
                                                                draggingItemIndex = null
                                                                draggingItemOffset = 0f
                                                            }
                                                        )
                                                    }
                                            )
                                        }
                                        AsyncImage(
                                            model = song.coverUrl ?: song.songUrl?.let { url ->
                                                if (url.startsWith("/")) File(url) else null
                                            },
                                            contentDescription = stringResource(R.string.cover_image),
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                            placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                                            error = rememberVectorPainter(Icons.Default.MusicNote)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }

        }
    }
}

@Composable
fun PlaylistCoverGrid(songs: List<OnlineSong>) {
    val imageModifier = Modifier
        .aspectRatio(1f)
        .clip(RoundedCornerShape(0.dp)) // No rounding at the individual image level

    if (songs.isEmpty()) {
        // Show a placeholder if there are no songs
        Box(
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = stringResource(R.string.cover_image),
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        // Use a 2x2 grid for the song covers
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            userScrollEnabled = false,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(songs.take(4)) { song ->
                AsyncImage(
                    model = song.coverUrl ?: song.songUrl?.let { url ->
                        if (url.startsWith("/")) File(url) else null
                    },
                    contentDescription = stringResource(R.string.cover_image_for, song.title),
                    modifier = imageModifier,
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                    error = rememberVectorPainter(Icons.Default.MusicNote)
                )
            }
        }
    }
}

@Composable
fun PlaylistHeader(playlist: Playlist, songs: List<OnlineSong>, onPlayAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // If there's a specific coverUrl, use it. Otherwise, generate the grid.
        if (playlist.coverUrl != null) {
            AsyncImage(
                model = playlist.coverUrl,
                contentDescription = stringResource(R.string.cover_image),
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                error = rememberVectorPainter(Icons.Default.MusicNote)
            )
        } else {
            PlaylistCoverGrid(songs = songs)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = stringResource(R.string.song_count, songs.size),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPlayAll) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.play_all))
        }
    }
}
