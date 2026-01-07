package top.xiaojiang233.nekoplayer.ui.screen.wear

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import top.xiaojiang233.nekoplayer.data.model.OnlineSong
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel
import top.xiaojiang233.nekoplayer.viewmodel.SearchViewModel

@Composable
fun WearSearchScreen(
    searchViewModel: SearchViewModel = viewModel(),
    playerViewModel: PlayerViewModel,
    onSongClick: (OnlineSong) -> Unit
) {
    val searchQuery by searchViewModel.searchQuery.collectAsState()
    val groupedSearchResults by searchViewModel.groupedSearchResults.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()

    ScalingLazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            // On Wear OS, text input is typically handled by launching a system activity.
            // For simplicity, we use a Chip to trigger a search for a predefined query.
            // A real implementation would use RemoteInput to get user input.
            Chip(
                label = { Text(if (searchQuery.isEmpty()) "Search" else searchQuery) },
                onClick = { /* TODO: Implement text input, e.g., using RemoteInput */ },
                icon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )
        }

        if (isLoading) {
            item {
                CircularProgressIndicator()
            }
        } else {
            items(groupedSearchResults) { group ->
                Card(
                    onClick = { 
                        group.songs.firstOrNull()?.let { onSongClick(it) }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text(text = group.title, modifier = Modifier.padding(8.dp), maxLines = 1)
                    Text(text = group.artist, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.body2, maxLines = 1)
                }
            }
        }
    }
}
