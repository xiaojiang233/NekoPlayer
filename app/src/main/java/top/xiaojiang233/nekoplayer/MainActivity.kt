package top.xiaojiang233.nekoplayer

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.core.content.ContextCompat
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.wear.compose.material.MaterialTheme as WearMaterialTheme
import top.xiaojiang233.nekoplayer.service.connection.MusicServiceConnection
import top.xiaojiang233.nekoplayer.ui.components.MiniPlayer
import top.xiaojiang233.nekoplayer.ui.navigation.AppNavigation
import top.xiaojiang233.nekoplayer.ui.navigation.Routes
import top.xiaojiang233.nekoplayer.ui.navigation.WearAppNavigation
import top.xiaojiang233.nekoplayer.ui.theme.NekoPlayerTheme
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel

class MainActivity : ComponentActivity() {

    companion object {
        private var dpiAdjusted = false
    }

    private lateinit var musicServiceConnection: MusicServiceConnection
    private val playerViewModel: PlayerViewModel by viewModels {
        PlayerViewModel.Factory(musicServiceConnection)
    }
    private val homeViewModel: HomeViewModel by viewModels()

    private val addMusicLauncher = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            homeViewModel.importSongs(uris)
        }
    }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        val audioGranted = permissions[audioPermission] == true ||
                permissions.values.all { it } // Fallback if specific key missing

        if (audioGranted) {
            checkFirstLaunch()
        }
    }

    private fun checkFirstLaunch() {
        val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
        val isFirstLaunch = prefs.getBoolean("is_first_launch", true)
        val isScaleSet = prefs.getBoolean("is_scale_set", false)

        val isSystemWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        val metrics = resources.displayMetrics
        val isWatchByResolution = metrics.widthPixels < 800 && metrics.heightPixels < 800
        val isWearable = isSystemWatch || isWatchByResolution

        if (isFirstLaunch) {
            if (isWearable && !isScaleSet) {
                homeViewModel.showWatchScaleSelection()
            } else {
                homeViewModel.showLocalMusicSelection()
            }
        } else {
            // Check if we need to apply scale again or if it was missed?
            // Actually, if !isFirstLaunch, we don't care about scale setting interruption anymore.
            homeViewModel.loadLocalSongs()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        musicServiceConnection = MusicServiceConnection.getInstance(this)

        val isSystemWatch = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)
        val metrics = resources.displayMetrics
        val isWatchByResolution = metrics.widthPixels < 800 && metrics.heightPixels < 800
        val isWearable = isSystemWatch || isWatchByResolution

        if (isWearable) {
            val prefs = getSharedPreferences("app_prefs", MODE_PRIVATE)
            val watchScale = prefs.getFloat("watch_scale", 1.0f)

            if (!dpiAdjusted && watchScale != 1.0f) {
                val config = resources.configuration
                // Store the original density if needed, or better yet, apply scale to the stable base density.
                // However, resources.configuration.densityDpi changes after updateConfiguration.
                // We shouldn't multiply repeatedly. But dpiAdjusted specifices this is done once per process lifecycle.
                // Wait, if activity restarts in same process, dpiAdjusted is true.
                // If it's a new process, dpiAdjusted is false.

                config.densityDpi = (config.densityDpi * watchScale).toInt()
                // Update configuration on the base context resource?
                resources.updateConfiguration(config, resources.displayMetrics)
                dpiAdjusted = true
            }
        } else {
            enableEdgeToEdge()
        }

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Manifest.permission.READ_MEDIA_AUDIO
                } else {
                    Manifest.permission.READ_EXTERNAL_STORAGE
                }

                val permissionsToRequest = mutableListOf<String>()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                    permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }

                val audioGranted = ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED

                if (audioGranted) {
                    // Audio permission already granted, check first launch
                    android.util.Log.d("MainActivity", "Audio permission already granted, checking first launch")
                    checkFirstLaunch()
                } else {
                    // Need to request permissions
                    android.util.Log.d("MainActivity", "Requesting permissions")
                    val permissionsNotGranted = permissionsToRequest.filter {
                        ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
                    }
                    if (permissionsNotGranted.isNotEmpty()) {
                        permissionRequestLauncher.launch(permissionsNotGranted.toTypedArray())
                    } else {
                        checkFirstLaunch()
                    }
                }
            }

            if (isWearable) {
                WearMaterialTheme {
                    WearAppNavigation(
                        playerViewModel = playerViewModel,
                        homeViewModel = homeViewModel,
                        onAddMusicClick = { addMusicLauncher.launch("audio/*") }
                    )
                }
            } else {
                NekoPlayerTheme {
                    Surface(color = MaterialTheme.colorScheme.background) {
                        val navController = rememberNavController()
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavigation(
                                playerViewModel = playerViewModel,
                                homeViewModel = homeViewModel,
                                onAddMusicClick = { addMusicLauncher.launch("audio/*") },
                                navController = navController
                            )

                            val isPlaying by playerViewModel.isPlaying.collectAsState()
                            val nowPlaying by playerViewModel.nowPlaying.collectAsState()
                            val customCover by playerViewModel.customCover.collectAsState()

                            AnimatedVisibility(
                                visible = nowPlaying != null && currentRoute != Routes.PLAYER,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                modifier = Modifier
                                    .align(if (LocalConfiguration.current.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) Alignment.BottomEnd else Alignment.BottomCenter)
                            ) {
                                val configuration = LocalConfiguration.current
                                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                MiniPlayer(
                                    isPlaying = isPlaying,
                                    nowPlaying = nowPlaying,
                                    onPlayPauseClick = { playerViewModel.onPlayPauseClick() },
                                    modifier = Modifier
                                        .padding(if (isLandscape) androidx.compose.ui.unit.Dp(16f) else androidx.compose.ui.unit.Dp(10f)),
                                    customCover = customCover,
                                    onMiniPlayerClick = { navController.navigate(Routes.PLAYER) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}