package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.data.model.Playlist
import top.xiaojiang233.nekoplayer.ui.components.MiniPlayer
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import java.io.File
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.TextButton
import androidx.compose.runtime.DisposableEffect

import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.xiaojiang233.nekoplayer.util.findActivity
import top.xiaojiang233.nekoplayer.data.model.OnlineSong

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
    val viewMode by homeViewModel.viewMode.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val nowPlaying by playerViewModel.nowPlaying.collectAsState()
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
            Text("Playlist not found")
            Button(onClick = onBackClick) { Text("Go Back") }
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
            title = { Text("Add Songs") },
            text = {
                if (availableSongs.isEmpty()) {
                    Text("No songs available to add.")
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
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddSongsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("${selectedSongIds.size} Selected")
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
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    } else {
                        IconButton(onClick = onBackClick) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            Icon(Icons.Default.Delete, contentDescription = "Remove Selected")
                        }
                    } else {
                        IconButton(onClick = { showAddSongsDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Songs")
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
            if (viewMode == HomeViewModel.ViewMode.List) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
                ) {
                    item {
                        PlaylistHeader(
                            playlist = playlist,
                            songCount = playlistSongs.size,
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
                                                    contentDescription = "Drag",
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
                                                contentDescription = "Cover",
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
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 80.dp)
                ) {
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        PlaylistHeader(
                            playlist = playlist,
                            songCount = playlistSongs.size,
                            onPlayAll = {
                                if (playlistSongs.isNotEmpty()) {
                                    playerViewModel.playPlaylist(playlistSongs, 0)
                                    onPlayerClick()
                                }
                            }
                        )
                    }
                    itemsIndexed(playlistSongs) { index: Int, song: OnlineSong ->
                        val isSelected = selectedSongIds.contains(song.id)
                        SongItem(
                            song = song,
                            viewMode = HomeViewModel.ViewMode.Grid,
                            isSelectionMode = isSelectionMode,
                            isSelected = isSelected,
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
                        )
                    }
                }
            }

            MiniPlayer(
                isPlaying = isPlaying,
                nowPlaying = nowPlaying,
                onPlayPauseClick = { playerViewModel.onPlayPauseClick() },
                modifier = Modifier
                    .width(300.dp)
                    .align(Alignment.BottomEnd)
                    .clickable { onPlayerClick() }
            )
        }
    }
}

@Composable
fun PlaylistHeader(playlist: Playlist, songCount: Int, onPlayAll: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = playlist.coverUrl,
            contentDescription = "Cover",
            modifier = Modifier
                .size(200.dp)
                .clip(RoundedCornerShape(16.dp)),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(Icons.Default.MusicNote),
            error = rememberVectorPainter(Icons.Default.MusicNote)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = "$songCount songs",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onPlayAll) {
            Icon(Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.size(8.dp))
            Text("Play All")
        }
    }
}
