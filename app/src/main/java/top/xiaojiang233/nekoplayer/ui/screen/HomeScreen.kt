package top.xiaojiang233.nekoplayer.ui.screen

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
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
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.xiaojiang233.nekoplayer.util.findActivity

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onPlayerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddMusicClick: () -> Unit,
    onPlaylistClick: (String) -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    playerViewModel: PlayerViewModel
) {
    val localSongs by homeViewModel.localSongs.collectAsState()
    val playlists by homeViewModel.playlists.collectAsState()
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

    var showAddMenu by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showRenamePlaylistDialog by remember { mutableStateOf<Playlist?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Multi-select state
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }

    // Drag and Drop State
    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    val itemHeight = 72.dp // Approximate height of SongItem
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    // Playlist Drag and Drop State
    var draggingPlaylistIndex by remember { mutableStateOf<Int?>(null) }
    var draggingPlaylistOffset by remember { mutableStateOf(0f) }
    var showPlaylistOptionsDialog by remember { mutableStateOf<Playlist?>(null) }

    LaunchedEffect(Unit) {
        homeViewModel.loadLocalSongs()
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("New Playlist") },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text("Playlist Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        homeViewModel.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showRenamePlaylistDialog != null) {
        val playlist = showRenamePlaylistDialog!!
        var renameText by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenamePlaylistDialog = null },
            title = { Text("Rename Playlist") },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        homeViewModel.renamePlaylist(playlist, renameText)
                        showRenamePlaylistDialog = null
                    }
                }) { Text("Rename") }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePlaylistDialog = null }) { Text("Cancel") }
            }
        )
    }

    if (showPlaylistOptionsDialog != null) {
        val playlist = showPlaylistOptionsDialog!!
        AlertDialog(
            onDismissRequest = { showPlaylistOptionsDialog = null },
            title = { Text(playlist.name) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPlaylistOptionsDialog = null
                            showRenamePlaylistDialog = playlist
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Rename", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                    TextButton(
                        onClick = {
                            showPlaylistOptionsDialog = null
                            homeViewModel.deletePlaylist(playlist)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete", color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistOptionsDialog = null }) { Text("Cancel") }
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
                        Text("NekoPlayer")
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
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add")
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Import Songs") },
                                    onClick = {
                                        showAddMenu = false
                                        onAddMusicClick()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("New Playlist") },
                                    onClick = {
                                        showAddMenu = false
                                        showCreatePlaylistDialog = true
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            selectedSongIds.forEach { id ->
                                localSongs.find { it.id == id }?.let { homeViewModel.deleteSong(it) }
                            }
                            isSelectionMode = false
                            selectedSongIds = emptySet()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete Selected")
                        }
                    } else {
                        IconButton(onClick = { homeViewModel.toggleViewMode() }) {
                            Icon(
                                if (viewMode == HomeViewModel.ViewMode.List) Icons.Default.GridView else Icons.AutoMirrored.Filled.List,
                                contentDescription = "Toggle View"
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
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
                    contentPadding = PaddingValues(bottom = if (nowPlaying != null) 80.dp else 0.dp)
                ) {
                    if (playlists.isNotEmpty() && !isSelectionMode) {
                        item {
                            Text(
                                "Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                        itemsIndexed(items = playlists, key = { _, item -> item.id }) { index, playlist ->
                            val isDragging = index == draggingPlaylistIndex
                            val offset = if (isDragging) draggingPlaylistOffset else 0f
                            val zIndex = if (isDragging) 1f else 0f

                            Box(
                                modifier = Modifier
                                    .zIndex(zIndex)
                                    .graphicsLayer {
                                        translationY = offset
                                    }
                                    .then(if (isDragging) Modifier else Modifier.animateItem())
                            ) {
                                PlaylistItem(
                                    playlist = playlist,
                                    viewMode = HomeViewModel.ViewMode.List,
                                    onClick = { onPlaylistClick(playlist.id) },
                                    onLongClick = { showPlaylistOptionsDialog = playlist },
                                    onDragStart = { draggingPlaylistIndex = index },
                                    onDrag = { dragAmount ->
                                        draggingPlaylistOffset += dragAmount
                                        val currentDraggingIndex = draggingPlaylistIndex ?: return@PlaylistItem
                                        val currentOffset = draggingPlaylistOffset

                                        // Calculate if we should swap
                                        val movedItems = (currentOffset / itemHeightPx).roundToInt()
                                        val targetIndex = (currentDraggingIndex + movedItems).coerceIn(0, playlists.lastIndex)

                                        if (targetIndex != currentDraggingIndex) {
                                            val newPlaylists = playlists.toMutableList()
                                            val item = newPlaylists.removeAt(currentDraggingIndex)
                                            newPlaylists.add(targetIndex, item)
                                            homeViewModel.updatePlaylistsOrder(newPlaylists)
                                            draggingPlaylistIndex = targetIndex
                                            draggingPlaylistOffset -= (targetIndex - currentDraggingIndex) * itemHeightPx
                                        }
                                    },
                                    onDragEnd = {
                                        draggingPlaylistIndex = null
                                        draggingPlaylistOffset = 0f
                                    }
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            "Songs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    itemsIndexed(items = localSongs, key = { _, item -> item.id }) { index, song ->
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
                                .then(if (isDragging) Modifier else Modifier.animateItem())
                        ) {
                            SongItem(
                                song = song,
                                viewMode = HomeViewModel.ViewMode.List,
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
                                        playerViewModel.playPlaylist(localSongs, index)
                                        onPlayerClick()
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedSongIds = setOf(song.id)
                                    }
                                },
                                onDragStart = if (!isSelectionMode) { {
                                    draggingItemIndex = index
                                } } else null,
                                onDrag = if (!isSelectionMode) { { dragAmount ->
                                    draggingItemOffset += dragAmount
                                    val currentDraggingIndex = draggingItemIndex ?: return@SongItem
                                    val currentOffset = draggingItemOffset

                                    // Calculate if we should swap
                                    val movedItems = (currentOffset / itemHeightPx).roundToInt()
                                    val targetIndex = (currentDraggingIndex + movedItems).coerceIn(0, localSongs.lastIndex)

                                    if (targetIndex != currentDraggingIndex) {
                                        val newSongs = localSongs.toMutableList()
                                        val item = newSongs.removeAt(currentDraggingIndex)
                                        newSongs.add(targetIndex, item)
                                        homeViewModel.updateLocalSongsOrder(newSongs)
                                        draggingItemIndex = targetIndex
                                        draggingItemOffset -= (targetIndex - currentDraggingIndex) * itemHeightPx
                                    }
                                } } else null,
                                onDragEnd = if (!isSelectionMode) { {
                                    draggingItemIndex = null
                                    draggingItemOffset = 0f
                                } } else null
                            )
                        }
                    }
                }
            } else {
                // Grid View (Simplified for now, no drag and drop in grid yet)
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (nowPlaying != null) 80.dp else 8.dp)
                ) {
                    if (playlists.isNotEmpty() && !isSelectionMode) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                "Playlists",
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                        items(playlists) { playlist ->
                            PlaylistItem(
                                playlist = playlist,
                                viewMode = HomeViewModel.ViewMode.Grid,
                                onClick = { onPlaylistClick(playlist.id) },
                                onLongClick = { showPlaylistOptionsDialog = playlist }
                            )
                        }
                    }

                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                        Text(
                            "Songs",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    itemsIndexed(localSongs) { index: Int, song: OnlineSong ->
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
                                    playerViewModel.playPlaylist(localSongs, index)
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

            FloatingActionButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomEnd)
                    .padding(
                        end = if (isLandscape) 0.dp else 16.dp,
                        start = if (isLandscape) 16.dp else 0.dp,
                        bottom = if (nowPlaying != null && !isLandscape) 100.dp else 16.dp
                    )
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }

            if (nowPlaying != null) {
                if (isLandscape) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(16.dp)
                            .width(300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPlayerClick() }
                    ) {
                        MiniPlayer(
                            isPlaying = isPlaying,
                            nowPlaying = nowPlaying,
                            onPlayPauseClick = { playerViewModel.onPlayPauseClick() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    MiniPlayer(
                        isPlaying = isPlaying,
                        nowPlaying = nowPlaying,
                        onPlayPauseClick = { playerViewModel.onPlayPauseClick() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .clickable { onPlayerClick() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongItem(
    song: OnlineSong,
    viewMode: HomeViewModel.ViewMode,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    if (viewMode == HomeViewModel.ViewMode.List) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
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
                                onCheckedChange = null // Handled by onClick
                            )
                        } else if (onDragStart != null && onDrag != null && onDragEnd != null) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Drag",
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { onDragStart() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onDrag(dragAmount.y)
                                            },
                                            onDragEnd = { onDragEnd() },
                                            onDragCancel = { onDragEnd() }
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
    } else {
        Card(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = if (isSelected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else CardDefaults.cardColors()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                Box {
                    AsyncImage(
                        model = song.coverUrl ?: song.songUrl?.let { url ->
                            if (url.startsWith("/")) File(url) else null
                        },
                        contentDescription = "Cover",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(Icons.Default.MusicNote),
                        error = rememberVectorPainter(Icons.Default.MusicNote)
                    )
                    if (isSelectionMode) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistItem(
    playlist: Playlist,
    viewMode: HomeViewModel.ViewMode,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDragStart: (() -> Unit)? = null,
    onDrag: ((Float) -> Unit)? = null,
    onDragEnd: (() -> Unit)? = null
) {
    if (viewMode == HomeViewModel.ViewMode.List) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ListItem(
                headlineContent = { Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                supportingContent = { Text("${playlist.songIds.size} songs") },
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDragStart != null && onDrag != null && onDragEnd != null) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = "Drag",
                                modifier = Modifier
                                    .padding(end = 8.dp)
                                    .pointerInput(Unit) {
                                        detectDragGestures(
                                            onDragStart = { onDragStart() },
                                            onDrag = { change, dragAmount ->
                                                change.consume()
                                                onDrag(dragAmount.y)
                                            },
                                            onDragEnd = { onDragEnd() },
                                            onDragCancel = { onDragEnd() }
                                        )
                                    }
                            )
                        }
                        AsyncImage(
                            model = playlist.coverUrl,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop,
                            placeholder = rememberVectorPainter(Icons.AutoMirrored.Filled.QueueMusic),
                            error = rememberVectorPainter(Icons.AutoMirrored.Filled.QueueMusic)
                        )
                    }
                }
            )
        }
    } else {
        Card(
            modifier = Modifier
                .padding(4.dp)
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(8.dp)
            ) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = "Cover",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(Icons.AutoMirrored.Filled.QueueMusic),
                    error = rememberVectorPainter(Icons.AutoMirrored.Filled.QueueMusic)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = playlist.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlist.songIds.size} songs",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

