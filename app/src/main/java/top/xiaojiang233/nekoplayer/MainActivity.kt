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
import androidx.compose.ui.Modifier
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

    private val addMusicLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            homeViewModel.addLocalSong(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        musicServiceConnection = MusicServiceConnection.getInstance(this)

        enableEdgeToEdge()
        setContent {
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
