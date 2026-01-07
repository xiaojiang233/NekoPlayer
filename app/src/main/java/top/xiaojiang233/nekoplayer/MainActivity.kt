package top.xiaojiang233.nekoplayer

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        musicServiceConnection = MusicServiceConnection.getInstance(this)

        val isWearable = packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)

        if (!isWearable) {
            enableEdgeToEdge()
        }

        setContent {
            val context = androidx.compose.ui.platform.LocalContext.current
            LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    val permissions = arrayOf(
                        android.Manifest.permission.READ_MEDIA_AUDIO,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    )
                    if (permissions.any { androidx.core.content.ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) {
                        requestPermissions(permissions, 0)
                    }
                } else {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                        requestPermissions(arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE), 0)
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
                        val navController = rememberNavController() // Hoist controller app-level
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route

                        // Root container holds navigation and the app-level MiniPlayer overlay
                        Box(modifier = Modifier.fillMaxSize()) {
                            AppNavigation(
                                playerViewModel = playerViewModel,
                                homeViewModel = homeViewModel,
                                onAddMusicClick = { addMusicLauncher.launch("audio/*") },
                                navController = navController
                            )

                            // App-level MiniPlayer overlay (visible when there is a nowPlaying item AND not on Player screen)
                            val isPlaying by playerViewModel.isPlaying.collectAsState()
                            val nowPlaying by playerViewModel.nowPlaying.collectAsState()
                            val customCover by playerViewModel.customCover.collectAsState()

                            if (nowPlaying != null && currentRoute != Routes.PLAYER) {
                                val configuration = LocalConfiguration.current
                                val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
                                MiniPlayer(
                                    isPlaying = isPlaying,
                                    nowPlaying = nowPlaying,
                                    onPlayPauseClick = { playerViewModel.onPlayPauseClick() },
                                    modifier = Modifier
                                        .align(if (isLandscape) Alignment.BottomEnd else Alignment.BottomCenter)
                                        .padding(if (isLandscape) androidx.compose.ui.unit.Dp(16f) else androidx.compose.ui.unit.Dp(10f)),
                                    customCover = customCover,
                                    onMiniPlayerClick = {
                                        navController.navigate(Routes.PLAYER) // Navigate to player
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WearApp(
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel,
    onAddMusicClick: () -> Unit
) {
    WearAppNavigation(
        playerViewModel = playerViewModel,
        homeViewModel = homeViewModel,
        onAddMusicClick = onAddMusicClick
    )
}
