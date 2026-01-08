package top.xiaojiang233.nekoplayer.ui.screen.wear

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Card
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.data.repository.SongRepository
import top.xiaojiang233.nekoplayer.service.DownloadService
import top.xiaojiang233.nekoplayer.utils.launchTextInput
import top.xiaojiang233.nekoplayer.viewmodel.SearchViewModel

@Composable
fun WearSearchScreen(
    searchViewModel: SearchViewModel = viewModel(),
    onSongClick: (OnlineSong) -> Unit
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val groupedSearchResults by searchViewModel.groupedSearchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val searchHistory by searchViewModel.searchHistory.collectAsState()
    val downloadState by searchViewModel.downloadState.collectAsState()

    var selectedSong by remember { mutableStateOf<OnlineSong?>(null) }
    val context = LocalContext.current
    val searchHint = stringResource(id = R.string.search_hint)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        result.data?.let { data ->
            val results = RemoteInput.getResultsFromIntent(data)
            results?.getCharSequence("search_query")?.toString()?.let {
                searchViewModel.onSearchQueryChange(it)
                searchViewModel.searchSongs()
            }
        }
    }

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Chip(
                label = { Text(if (searchQuery.isEmpty()) stringResource(R.string.search) else searchQuery) },
                onClick = {
                    launchTextInput(
                        context = context,
                        launcher = launcher,
                        remoteInputKey = "search_query",
                        label = searchHint
                    ) { query ->
                        searchViewModel.onSearchQueryChange(query)
                        searchViewModel.searchSongs()
                    }
                },
                icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search)) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        if (searchQuery.isEmpty()) {
            if (searchHistory.isNotEmpty()) {
                item { ListHeader { Text(stringResource(R.string.search_history)) } }
                items(searchHistory) { history ->
                    Chip(
                        label = { Text(history) },
                        onClick = {
                            searchViewModel.onSearchQueryChange(history)
                            searchViewModel.searchSongs()
                        },
                        icon = { Icon(Icons.Default.History, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.secondaryChipColors()
                    )
                }
                item {
                    CompactChip(
                        label = { Text(stringResource(R.string.clear_history)) },
                        onClick = { searchViewModel.clearHistory() },
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        } else if (isLoading && groupedSearchResults.isEmpty()) {
            item {
                CircularProgressIndicator()
            }
        } else {
            items(groupedSearchResults) { group ->
                Card(
                    onClick = { group.songs.firstOrNull()?.let { selectedSong = it } },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = group.title, maxLines = 1)
                            Text(
                                text = "${group.artist} - ${group.songs.firstOrNull()?.platform ?: ""}",
                                style = MaterialTheme.typography.body2,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }

    Dialog(
        showDialog = selectedSong != null,
        onDismissRequest = { selectedSong = null }
    ) {
        val song = selectedSong ?: return@Dialog
        val currentDownloadState = downloadState[song.id]

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .background(MaterialTheme.colors.background),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = song.title,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.title3,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = song.artist,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Play button
            Chip(
                label = { Text(stringResource(R.string.play)) },
                icon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                onClick = {
                    onSongClick(song)
                    selectedSong = null
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Download button
            val isDownloaded = currentDownloadState == SongRepository.DownloadState.Downloaded
            val isDownloading = currentDownloadState is SongRepository.DownloadState.Downloading

            Chip(
                label = {
                    Text(
                        when {
                            isDownloaded -> stringResource(R.string.downloaded)
                            isDownloading -> stringResource(R.string.downloading)
                            else -> stringResource(R.string.download)
                        }
                    )
                },
                icon = {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            indicatorColor = MaterialTheme.colors.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = if (isDownloaded) Icons.Default.Check else Icons.Default.Download,
                            contentDescription = null
                        )
                    }
                },
                onClick = {
                    if (!isDownloaded && !isDownloading) {
                        val intent = Intent(context, DownloadService::class.java).apply {
                            putExtra(DownloadService.EXTRA_SONG, song)
                        }
                        context.startService(intent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ChipDefaults.primaryChipColors(),
                enabled = !isDownloading && !isDownloaded
            )

        }
    }
}