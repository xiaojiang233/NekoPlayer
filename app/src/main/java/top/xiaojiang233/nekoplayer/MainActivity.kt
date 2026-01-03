package top.xiaojiang233.nekoplayer

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.xiaojiang233.nekoplayer.service.connection.MusicServiceConnection
import top.xiaojiang233.nekoplayer.ui.navigation.AppNavigation
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

        enableEdgeToEdge()
        setContent {
            val configuration = LocalConfiguration.current
            val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
            val view = LocalView.current

            LaunchedEffect(isLandscape) {
                val window = this@MainActivity.window
                val insetsController = WindowCompat.getInsetsController(window, view)
                if (isLandscape) {
                    insetsController.hide(WindowInsetsCompat.Type.systemBars())
                    insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                } else {
                    insetsController.show(WindowInsetsCompat.Type.systemBars())
                }
            }

            NekoPlayerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
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
