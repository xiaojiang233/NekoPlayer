package top.xiaojiang233.nekoplayer.ui.components.wear

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.dialog.Dialog
import top.xiaojiang233.nekoplayer.R

@Composable
fun WearWatchScaleSelectionDialog(
    showDialog: Boolean,
    onDismissRequest: () -> Unit,
    onScaleSelected: (Float) -> Unit
) {
    val listState = rememberScalingLazyListState()
    val scales = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 1.75f, 2.0f)

    Dialog(
        showDialog = showDialog,
        onDismissRequest = onDismissRequest
    ) {
        ScalingLazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState
        ) {
            item {
                ListHeader {
                    Text(text = stringResource(R.string.select_watch_scale))
                }
            }

            items(scales.size) { index ->
                val scale = scales[index]
                Chip(
                    label = { Text(String.format("%.1f", scale)) },
                    onClick = { onScaleSelected(scale) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors()
                )
            }
        }
    }
}

