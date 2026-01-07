package top.xiaojiang233.nekoplayer

import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.wear.compose.material.MaterialTheme as WearMaterialTheme
import top.xiaojiang233.nekoplayer.service.connection.MusicServiceConnection
import top.xiaojiang233.nekoplayer.ui.navigation.AppNavigation
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
            if (isWearable) {
                WearMaterialTheme {
                    WearApp(
                        playerViewModel = playerViewModel,
                        homeViewModel = homeViewModel,
                        onAddMusicClick = { addMusicLauncher.launch("audio/*") }
                    )
                }
            } else {
                NekoPlayerTheme {
                    AppNavigation(
                        playerViewModel = playerViewModel,
                        homeViewModel = homeViewModel,
                        onAddMusicClick = { addMusicLauncher.launch("audio/*") }
                    )
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
