package top.xiaojiang233.nekoplayer.ui.screen

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import top.xiaojiang233.nekoplayer.util.findActivity
import top.xiaojiang233.nekoplayer.viewmodel.SettingsViewModel
import top.xiaojiang233.nekoplayer.R

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

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri ->
            if (uri != null) {
                settingsViewModel.exportConfiguration(uri)
            }
        }
    )

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                settingsViewModel.importConfiguration(uri)
            }
        }
    )

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
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.clear_cache)) },
                supportingContent = { Text(stringResource(R.string.clear_cache_desc)) },
                modifier = Modifier.clickable {
                    settingsViewModel.clearCache()
                    Toast.makeText(context, context.getString(R.string.cache_cleared), Toast.LENGTH_SHORT).show()
                }
            )

            Text(
                text = stringResource(R.string.title_lyrics),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.font_size, lyricsFontSize.toInt())) },
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
                headlineContent = { Text(stringResource(R.string.font_family)) },
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
                headlineContent = { Text(stringResource(R.string.blur_intensity, lyricsBlurIntensity.toInt())) },
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
                text = stringResource(R.string.title_player),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.show_platform_tag)) },
                supportingContent = { Text(stringResource(R.string.show_platform_tag_desc)) },
                trailingContent = {
                    Switch(
                        checked = showPlatformTag,
                        onCheckedChange = { settingsViewModel.setShowPlatformTag(it) }
                    )
                }
            )

            Text(
                text = stringResource(R.string.title_backup_restore),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.export_config)) },
                supportingContent = { Text(stringResource(R.string.export_config_desc)) },
                modifier = Modifier.clickable {
                    exportLauncher.launch("NekoPlayer_Backup.json")
                }
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.import_config)) },
                supportingContent = { Text(stringResource(R.string.import_config_desc)) },
                modifier = Modifier.clickable {
                    importLauncher.launch(arrayOf("application/json"))
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.title_about),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
                    val versionName = packageInfo.versionName ?: "Unknown"
                    Text(
                        text = stringResource(R.string.version, versionName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.author),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    val uriHandler = LocalUriHandler.current
                    Button(onClick = { uriHandler.openUri("https://github.com/xiaojiang233/NekoPlayer") }) {
                        Text(stringResource(R.string.github))
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
