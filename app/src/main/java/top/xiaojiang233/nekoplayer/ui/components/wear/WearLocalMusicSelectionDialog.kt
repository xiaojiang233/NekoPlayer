package top.xiaojiang233.nekoplayer.ui.components.wear

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.*
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.data.model.OnlineSong

@Composable
fun WearLocalMusicSelectionDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    availableSongs: List<OnlineSong>,
    onSongsSelected: (List<OnlineSong>) -> Unit
) {
    if (!showDialog) return
    var selectedSongs by remember { mutableStateOf(setOf<String>()) }

    androidx.wear.compose.material.dialog.Dialog(
        showDialog = true,
        onDismissRequest = onDismissRequest
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp)
        ) {
            item {
                ListHeader {
                    Text(stringResource(R.string.select_local_music))
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    CompactChip(
                        label = { Text(stringResource(R.string.select_all)) },
                        onClick = {
                            selectedSongs = availableSongs.map { it.id }.toSet()
                        }
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    CompactChip(
                        label = { Text(stringResource(R.string.clear)) },
                        onClick = {
                            selectedSongs = emptySet()
                        }
                    )
                }
            }

            items(availableSongs) { song ->
                ToggleChip(
                    label = { Text(song.title) },
                    secondaryLabel = { Text(song.artist) },
                    checked = selectedSongs.contains(song.id),
                    onCheckedChange = { checked ->
                        selectedSongs = if (checked) {
                            selectedSongs + song.id
                        } else {
                            selectedSongs - song.id
                        }
                    },
                    toggleControl = {
                        Checkbox(
                            checked = selectedSongs.contains(song.id),
                            onCheckedChange = null
                        )
                    }
                )
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.add_selected, selectedSongs.size)) },
                    onClick = {
                        val selected = availableSongs.filter { selectedSongs.contains(it.id) }
                        onSongsSelected(selected)
                    },
                    enabled = selectedSongs.isNotEmpty(),
                    colors = ChipDefaults.primaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Chip(
                    label = { Text(stringResource(R.string.cancel)) },
                    onClick = onDismissRequest,
                    colors = ChipDefaults.secondaryChipColors(),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}
