package top.xiaojiang233.nekoplayer.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import top.xiaojiang233.nekoplayer.R
import top.xiaojiang233.nekoplayer.utils.findActivity
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val lyricsFontSize by viewModel.lyricsFontSize.collectAsState()
    val lyricsFontFamily by viewModel.lyricsFontFamily.collectAsState()
    val lyricsBlurIntensity by viewModel.lyricsBlurIntensity.collectAsState()
    val showPlatformTag by viewModel.showPlatformTag.collectAsState()
    val playbackDelay by viewModel.playbackDelay.collectAsState()
    val fadeInDuration by viewModel.fadeInDuration.collectAsState()

    val uriHandler = LocalUriHandler.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportConfiguration(uri)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importConfiguration(uri)
        }
    }

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
                title = { Text(stringResource(R.string.title_settings)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stringResource(R.string.title_player),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Show Platform Tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.show_platform_tag),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.show_platform_tag_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = showPlatformTag,
                    onCheckedChange = { viewModel.setShowPlatformTag(it) }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Playback Delay
            Text(text = stringResource(R.string.playback_delay, playbackDelay))
            Slider(
                value = playbackDelay.toFloat(),
                onValueChange = { viewModel.setPlaybackDelay(it.toInt()) },
                valueRange = 0f..1000f // 0 to 1 second
            )

            // Fade-in Duration
            Text(text = stringResource(R.string.fade_in_duration, fadeInDuration))
            Slider(
                value = fadeInDuration.toFloat(),
                onValueChange = { viewModel.setFadeInDuration(it.toInt()) },
                valueRange = 0f..1000f // 0 to 1 second
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = stringResource(R.string.title_lyrics),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Lyrics Font Size
            Text(text = stringResource(R.string.font_size, lyricsFontSize.toInt()))
            Slider(
                value = lyricsFontSize,
                onValueChange = { viewModel.setLyricsFontSize(it) },
                valueRange = 12f..48f
            )

            // Lyrics Font Family
            var expanded by remember { mutableStateOf(false) }
            val fontFamilies = listOf("Default", "Serif", "SansSerif", "Monospace", "Cursive")

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                TextField(
                    value = lyricsFontFamily,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.font_family)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable, true).fillMaxWidth()
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    fontFamilies.forEach { family ->
                        DropdownMenuItem(
                            text = { Text(family) },
                            onClick = {
                                viewModel.setLyricsFontFamily(family)
                                expanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Lyrics Blur Intensity
            Text(text = stringResource(R.string.blur_intensity, lyricsBlurIntensity.toInt()))
            Slider(
                value = lyricsBlurIntensity,
                onValueChange = { viewModel.setLyricsBlurIntensity(it) },
                valueRange = 0f..25f
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Text(
                text = stringResource(R.string.title_backup_restore),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text(stringResource(R.string.export_config)) },
                supportingContent = { Text(stringResource(R.string.export_config_desc)) },
                modifier = Modifier.clickable { exportLauncher.launch("nekoplayer_config_${System.currentTimeMillis()}.json") }
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.import_config)) },
                supportingContent = { Text(stringResource(R.string.import_config_desc)) },
                modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json")) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            Button(
                onClick = { viewModel.clearCache() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.clear_cache))
            }
            Text(
                text = stringResource(R.string.clear_cache_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            // About Section
            Text(
                text = stringResource(R.string.title_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                    Text(text = stringResource(R.string.version, "1.1"), style = MaterialTheme.typography.bodyMedium) // Using 1.1 based on errors build.gradle.kts
                    Text(text = stringResource(R.string.author), style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { uriHandler.openUri("https://github.com/xiaojiang233/NekoPlayer") },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.github))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
