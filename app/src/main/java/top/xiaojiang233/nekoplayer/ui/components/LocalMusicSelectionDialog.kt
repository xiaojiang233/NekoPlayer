package top.xiaojiang233.nekoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong

@Composable
fun LocalMusicSelectionDialog(
    availableSongs: List<OnlineSong>,
    onDismiss: () -> Unit,
    onConfirm: (List<OnlineSong>) -> Unit
) {
    var selectedSongs by remember { mutableStateOf(availableSongs.toSet()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.add_local_music)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.select_songs_to_add),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        TextButton(
                            onClick = { selectedSongs = availableSongs.toSet() }
                        ) {
                            Text(stringResource(R.string.select_all))
                        }
                        TextButton(
                            onClick = { selectedSongs = emptySet() }
                        ) {
                            Text(stringResource(R.string.select_none))
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.found_songs, availableSongs.size),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                if (availableSongs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = androidx.compose.ui.Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.no_local_music_found),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp)
                    ) {
                        items(availableSongs) { song ->
                            val isSelected = selectedSongs.contains(song)
                            ListItem(
                                headlineContent = { Text(song.title) },
                                supportingContent = { Text(song.artist) },
                                trailingContent = {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = {
                                            selectedSongs = if (it) {
                                                selectedSongs + song
                                            } else {
                                                selectedSongs - song
                                            }
                                        }
                                    )
                                },
                                modifier = Modifier.clickable {
                                    selectedSongs = if (isSelected) {
                                        selectedSongs - song
                                    } else {
                                        selectedSongs + song
                                    }
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(selectedSongs.toList())
                    onDismiss()
                }
            ) {
                Text(stringResource(R.string.add_selected, selectedSongs.size))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

