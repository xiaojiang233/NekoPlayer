package top.xiaojiang233.nekoplayer.ui.screen

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import top.xiaojiang233.nekoplayer.util.findActivity
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBackClick: () -> Unit, settingsViewModel: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val lyricsFontSize by settingsViewModel.lyricsFontSize.collectAsState()
    val lyricsFontFamily by settingsViewModel.lyricsFontFamily.collectAsState()
    val lyricsBlurIntensity by settingsViewModel.lyricsBlurIntensity.collectAsState()
    val showPlatformTag by settingsViewModel.showPlatformTag.collectAsState()
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            ListItem(
                headlineContent = { Text("Clear Cache") },
                supportingContent = { Text("Clear downloaded songs and images") },
                modifier = Modifier.clickable {
                    settingsViewModel.clearCache()
                    Toast.makeText(context, "Cache cleared", Toast.LENGTH_SHORT).show()
                }
            )

            Text(
                text = "Lyrics",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Font Size: ${lyricsFontSize.toInt()} sp") },
                supportingContent = {
                    Slider(
                        value = lyricsFontSize,
                        onValueChange = { settingsViewModel.setLyricsFontSize(it) },
                        valueRange = 16f..48f,
                        steps = 15
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Font Family") },
                supportingContent = { Text(lyricsFontFamily) },
                modifier = Modifier.clickable {
                    val nextFont = when (lyricsFontFamily) {
                        "Default" -> "Serif"
                        "Serif" -> "SansSerif"
                        "SansSerif" -> "Monospace"
                        "Monospace" -> "Cursive"
                        else -> "Default"
                    }
                    settingsViewModel.setLyricsFontFamily(nextFont)
                }
            )

            ListItem(
                headlineContent = { Text("Blur Intensity: ${lyricsBlurIntensity.toInt()}") },
                supportingContent = {
                    Slider(
                        value = lyricsBlurIntensity,
                        onValueChange = { settingsViewModel.setLyricsBlurIntensity(it) },
                        valueRange = 0f..25f,
                        steps = 25
                    )
                }
            )

            Text(
                text = "Player",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Show Platform Tag") },
                supportingContent = { Text("Show platform tag under artist name in player") },
                trailingContent = {
                    Switch(
                        checked = showPlatformTag,
                        onCheckedChange = { settingsViewModel.setShowPlatformTag(it) }
                    )
                }
            )

            Text(
                text = "Backup & Restore",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text("Export Configuration") },
                supportingContent = { Text("Save settings, songs, and playlists to Download/NekoMusic") },
                modifier = Modifier.clickable {
                    settingsViewModel.exportConfiguration()
                }
            )

            ListItem(
                headlineContent = { Text("Import Configuration") },
                supportingContent = { Text("Restore from Download/NekoMusic/backup.json") },
                modifier = Modifier.clickable {
                    settingsViewModel.importConfiguration()
                }
            )
        }
    }
}
