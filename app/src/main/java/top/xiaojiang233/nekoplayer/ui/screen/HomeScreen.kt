package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import java.io.File
import kotlin.math.roundToInt
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.model.Playlist
import top.xiaojiang233.nekoplayer.ui.components.MiniPlayer
import top.xiaojiang233.nekoplayer.ui.components.LocalMusicSelectionDialog
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel

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
    val showLocalMusicSelection by homeViewModel.showLocalMusicSelection.collectAsState()
    val availableLocalSongs by homeViewModel.availableLocalSongs.collectAsState()

    LaunchedEffect(showLocalMusicSelection) {
        android.util.Log.d("HomeScreen", "showLocalMusicSelection changed: $showLocalMusicSelection, songs: ${availableLocalSongs.size}")
    }

    var showAddMenu by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showRenamePlaylistDialog by remember { mutableStateOf<Playlist?>(null) }
    var newPlaylistName by remember { mutableStateOf("") }

    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedSongIds by remember { mutableStateOf(setOf<String>()) }

    var draggingItemIndex by remember { mutableStateOf<Int?>(null) }
    var draggingItemOffset by remember { mutableStateOf(0f) }
    val itemHeight = 72.dp
    val itemHeightPx = with(LocalDensity.current) { itemHeight.toPx() }

    var draggingPlaylistIndex by remember { mutableStateOf<Int?>(null) }
    var draggingPlaylistOffset by remember { mutableStateOf(0f) }
    var showPlaylistOptionsDialog by remember { mutableStateOf<Playlist?>(null) }

    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(Unit) {
        // Only load songs if not first launch (user has already made a choice about local music)
        val prefs = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)

        if (!isFirstLaunch) {
            // User has already been asked, load their library
            homeViewModel.loadLocalSongs()
        }
        // If it's first launch, wait for permission callback to show selection dialog
    }

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text(stringResource(R.string.new_playlist)) },
            text = {
                TextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.playlist_name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        homeViewModel.createPlaylist(newPlaylistName)
                        newPlaylistName = ""
                        showCreatePlaylistDialog = false
                    }
                }) { Text(stringResource(R.string.create)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (showRenamePlaylistDialog != null) {
        val playlist = showRenamePlaylistDialog!!
        var renameText by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRenamePlaylistDialog = null },
            title = { Text(stringResource(R.string.rename_playlist)) },
            text = {
                TextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.name)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        homeViewModel.renamePlaylist(playlist, renameText)
                        showRenamePlaylistDialog = null
                    }
                }) { Text(stringResource(R.string.rename)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenamePlaylistDialog = null }) { Text(stringResource(R.string.cancel)) }
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
                        Text(stringResource(R.string.rename), modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                    TextButton(
                        onClick = {
                            showPlaylistOptionsDialog = null
                            homeViewModel.deletePlaylist(playlist)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Start)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPlaylistOptionsDialog = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Local music selection dialog
    if (showLocalMusicSelection) {
        LocalMusicSelectionDialog(
            availableSongs = availableLocalSongs,
            onDismiss = { homeViewModel.hideLocalMusicSelection() },
            onConfirm = { selectedSongs ->
                homeViewModel.addLocalSongs(selectedSongs)
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
                        Text(stringResource(R.string.app_name))
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
                        Box {
                            IconButton(onClick = { showAddMenu = true }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add))
                            }
                            DropdownMenu(
                                expanded = showAddMenu,
                                onDismissRequest = { showAddMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.import_songs)) },
                                    onClick = {
                                        showAddMenu = false
                                        onAddMusicClick()
                                    },
                                    leadingIcon = { Icon(Icons.Default.MusicNote, contentDescription = null) }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.new_playlist)) },
                                    onClick = {
                                        showAddMenu = false
                                        showCreatePlaylistDialog = true
                                    },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) }
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
                            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete_selected))
                        }
                    } else {
                        IconButton(onClick = { homeViewModel.toggleViewMode() }) {
                            Icon(
                                if (viewMode == HomeViewModel.ViewMode.List) Icons.Default.GridView else Icons.AutoMirrored.Filled.List,
                                contentDescription = stringResource(R.string.toggle_view)
                            )
                        }
                        IconButton(onClick = onSettingsClick) {
                            Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.settings))
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
                                stringResource(R.string.playlists_title),
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
                            stringResource(R.string.songs_title),
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
                                onDragStart = if (!isSelectionMode) {  {
                                    draggingItemIndex = index
                                } } else null,
                                onDrag = if (!isSelectionMode) { { dragAmount ->
                                    draggingItemOffset += dragAmount
                                    val currentDraggingIndex = draggingItemIndex ?: return@SongItem
                                    val currentOffset = draggingItemOffset

                                    val movedItems = (currentOffset / itemHeightPx).roundToInt()
                                    val targetIndex = (currentDraggingIndex + movedItems).coerceIn(0, localSongs.lastIndex)

                                    if (targetIndex != currentDraggingIndex) {
                                        val newSongs = localSongs.toMutableList()
                                        val item = newSongs.removeAt(currentDraggingIndex)
                                        newSongs.add(targetIndex, item)
                                        homeViewModel.updateLocalSongsOrder(newSongs)
                                        draggingItemIndex = targetIndex
                                        draggingItemOffset -= (targetIndex - currentDraggingIndex) * itemHeightPx.toInt()
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
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 150.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = if (nowPlaying != null) 80.dp else 8.dp)
                ) {
                    if (playlists.isNotEmpty() && !isSelectionMode) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                            Text(
                                stringResource(R.string.playlists_title),
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
                            stringResource(R.string.songs_title),
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
            
            val fabBottomPadding by animateDpAsState(
                targetValue = if (nowPlaying != null) 96.dp else 32.dp,
                label = "fabPadding"
            )

            FloatingActionButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 32.dp, bottom = fabBottomPadding)
            ) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
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
                supportingContent = {
                    Column {
                        Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isSelectionMode && song.platform == "local") {
                            Text(
                                text = stringResource(R.string.locally_imported),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                },
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
                                contentDescription = stringResource(R.string.drag),
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
                        contentDescription = stringResource(R.string.cover_image),
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
                if (isSelectionMode && song.platform == "local") {
                    Text(
                        text = stringResource(R.string.locally_imported),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
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
                supportingContent = { Text(stringResource(R.string.song_count, playlist.songIds.size)) },
                leadingContent = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDragStart != null && onDrag != null && onDragEnd != null) {
                            Icon(
                                imageVector = Icons.Default.DragIndicator,
                                contentDescription = stringResource(R.string.drag),
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
                            contentDescription = stringResource(R.string.cover_image),
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
                    contentDescription = stringResource(R.string.cover_image),
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
                    text = stringResource(R.string.song_count, playlist.songIds.size),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
