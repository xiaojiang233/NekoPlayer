package top.xiaojiang233.nekoplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument
import top.xiaojiang233.nekoplayer.ui.screen.WearHomeScreen
import top.xiaojiang233.nekoplayer.ui.screen.wear.WearPlaceholderScreen
import top.xiaojiang233.nekoplayer.ui.screen.wear.WearSearchScreen
import top.xiaojiang233.nekoplayer.ui.screen.PlayerScreen
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel

@Composable
fun WearAppNavigation(
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel,
    onAddMusicClick: () -> Unit
) {
    val navController = rememberSwipeDismissableNavController()

    SwipeDismissableNavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            WearHomeScreen(
                homeViewModel = homeViewModel,
                playerViewModel = playerViewModel,
                onSearchClick = { navController.navigate(Routes.SEARCH) },
                onPlayerClick = { navController.navigate(Routes.PLAYER) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
                onAddMusicClick = onAddMusicClick,
                onPlaylistClick = { playlistId -> navController.navigate("playlist/$playlistId") }
            )
        }
        composable(Routes.SEARCH) {
            WearSearchScreen(
                playerViewModel = playerViewModel,
                onSongClick = { song ->
                    // Play and jump to Player screen
                    playerViewModel.playSong(song)
                    navController.navigate(Routes.PLAYER)
                }
            )
        }
        composable(Routes.SETTINGS) {
            WearPlaceholderScreen(screenName = "Settings")
        }
        composable(Routes.PLAYER) {
            // Replace placeholder with actual PlayerScreen, adapted for Wear
            // By passing isWearable=true (inferred from configuration inside PlayerScreen if we didn't remove it or logic is there)
            // But I removed `isWearable` parameter from PlayerScreen signature before.
            // PlayerScreen logic now automatically detects configuration.
            // However, Wear navigation passes control here.
            // I should use the existing PlayerScreen which handles `isWearable` check internally via `LocalConfiguration`.
            PlayerScreen(
                viewModel = playerViewModel,
                onCloseClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            WearPlaceholderScreen(screenName = "Playlist: ${playlistId ?: "Unknown"}")
        }
    }
}
