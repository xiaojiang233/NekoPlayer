package top.xiaojiang233.nekoplayer.ui.screen

import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.repository.SongRepository
import top.xiaojiang233.nekoplayer.service.DownloadService
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import top.xiaojiang233.nekoplayer.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    searchViewModel: SearchViewModel = viewModel(),
    playerViewModel: PlayerViewModel,
    onBackClick: () -> Unit,
    onSongClick: (OnlineSong) -> Unit,
    onPlayerClick: () -> Unit
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val groupedSearchResults by searchViewModel.groupedSearchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val downloadState by searchViewModel.downloadState.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val selectedPlatforms by searchViewModel.selectedPlatforms.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val nowPlaying by playerViewModel.nowPlaying.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(listState, isLoading, groupedSearchResults.size) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .collect { lastIndex ->
                if (lastIndex != null && lastIndex >= groupedSearchResults.size - 2 && !isLoading && groupedSearchResults.isNotEmpty()) {
                    searchViewModel.loadMore()
                }
            }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding().padding(top = 16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(24.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp)),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchViewModel.onSearchQueryChange(it) },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 12.dp),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox = { innerTextField ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (searchQuery.isEmpty()) {
                                    Text(stringResource(R.string.search_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                innerTextField()
                            }
                        }
                    )
                    IconButton(onClick = { searchViewModel.searchSongs() }) {
                        Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search))
                    }
                }

                // Platform Filter
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("netease", "qq", "kuwo").forEach { platform ->
                        FilterChip(
                            selected = platform in selectedPlatforms,
                            onClick = { searchViewModel.togglePlatform(platform) },
                            label = { Text(platform) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && groupedSearchResults.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (groupedSearchResults.isEmpty()) {
                if (searchHistory.isNotEmpty()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(stringResource(R.string.search_history), style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { searchViewModel.clearHistory() }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.clear_history))
                            }
                        }
                        LazyColumn {
                            items(searchHistory) { historyItem ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            searchViewModel.onSearchQueryChange(historyItem)
                                            searchViewModel.searchSongs()
                                        }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.size(16.dp))
                                    Text(historyItem)
                                }
                            }
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.find_favorite_songs),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    contentPadding = PaddingValues(bottom = if (nowPlaying != null) 80.dp else 0.dp)
                ) {
                    items(groupedSearchResults) { group ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    if (group.songs.isNotEmpty()) {
                                        val song = group.songs.random()
                                        if (playerViewModel.hasPlaylist()) {
                                            playerViewModel.insertAndPlay(song)
                                        } else {
                                            val allSongs = groupedSearchResults.flatMap { it.songs }
                                            val index = allSongs.indexOf(song)
                                            if (index != -1) {
                                                playerViewModel.playPlaylist(allSongs, index)
                                            } else {
                                                playerViewModel.playSong(song)
                                            }
                                        }
                                        onPlayerClick()
                                    }
                                },
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                        ) {
                            ListItem(
                                headlineContent = { Text(group.title) },
                                supportingContent = {
                                    Column {
                                        Text(group.artist)
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            group.songs.forEach { song ->
                                                Box(
                                                    modifier = Modifier
                                                        .background(getPlatformColor(song.platform), RoundedCornerShape(4.dp))
                                                        .clickable { onSongClick(song) }
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(
                                                        text = song.platform,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color.White
                                                    )
                                                }
                                            }
                                        }
                                    }
                                },
                                leadingContent = {
                                    AsyncImage(
                                        model = group.coverUrl,
                                        contentDescription = group.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                },
                                trailingContent = {
                                    val song = group.songs.firstOrNull()
                                    if (song != null) {
                                        val isWatch = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH) || (LocalConfiguration.current.screenWidthDp < 300)
                                        if (!isWatch) {
                                            val state = downloadState[song.id] ?: SongRepository.DownloadState.None
                                            when (state) {
                                                is SongRepository.DownloadState.None -> {
                                                    IconButton(onClick = {
                                                        val intent = Intent(context, DownloadService::class.java).apply {
                                                            putExtra(DownloadService.EXTRA_SONG, song)
                                                        }
                                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                            context.startForegroundService(intent)
                                                        } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                                            context.startForegroundService(intent)
                                                        } else {
                                                            context.startService(intent)
                                                        }
                                                    }) {
                                                        Icon(Icons.Default.Download, contentDescription = stringResource(R.string.download))
                                                    }
                                                }
                                                is SongRepository.DownloadState.Downloading -> {
                                                    CircularProgressIndicator(
                                                        progress = state.progress,
                                                        modifier = Modifier.size(24.dp),
                                                        strokeWidth = 2.dp
                                                    )
                                                }
                                                is SongRepository.DownloadState.Downloaded -> {
                                                    IconButton(onClick = { searchViewModel.deleteSong(song) }) {
                                                        Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete))
                                                    }
                                                }
                                                is SongRepository.DownloadState.Failed -> {
                                                    Icon(
                                                        Icons.Default.Error,
                                                        contentDescription = stringResource(R.string.download_failed_msg, state.message),
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }

                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }

        }
    }
}

fun getPlatformColor(platform: String): Color {
    return when (platform.lowercase()) {
        "netease" -> Color(0xFFC20C0C)
        "qq" -> Color(0xFFFFEB3B)
        "kuwo" -> Color(0xFF2196F3)
        else -> Color.Gray
    }
}
