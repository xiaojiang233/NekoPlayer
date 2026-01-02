package top.xiaojiang233.nekoplayer.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import androidx.lifecycle.viewmodel.compose.viewModel
import top.xiaojiang233.nekoplayer.ui.components.MiniPlayer
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onSearchClick: () -> Unit,
    onPlayerClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAddMusicClick: () -> Unit,
    homeViewModel: HomeViewModel = viewModel(),
    playerViewModel: PlayerViewModel
) {
    val localSongs by homeViewModel.localSongs.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val nowPlaying by playerViewModel.nowPlaying.collectAsState()

    LaunchedEffect(Unit) {
        homeViewModel.loadLocalSongs()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("NekoPlayer") },
                navigationIcon = {
                    IconButton(onClick = onAddMusicClick) {
                        Icon(Icons.Default.Add, contentDescription = "Add Local Music")
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
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
            LazyColumn(modifier = Modifier.padding(horizontal = 8.dp)) {
                items(localSongs) { song ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .combinedClickable(
                                onClick = {
                                    playerViewModel.playSong(song)
                                    onPlayerClick()
                                },
                                onDoubleClick = {
                                    homeViewModel.deleteSong(song)
                                }
                            ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        ListItem(
                            headlineContent = { Text(song.title) },
                            supportingContent = { Text(song.artist) },
                            leadingContent = {
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
                            },
                            trailingContent = {
                                IconButton(onClick = { homeViewModel.deleteSong(song) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = onSearchClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (nowPlaying != null) 100.dp else 16.dp)
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search")
            }

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
