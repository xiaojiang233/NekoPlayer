package top.xiaojiang233.nekoplayer.ui.navigation

import androidx.compose.runtime.Composable
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.compose.navigation.composable
import androidx.navigation.NavType
import androidx.navigation.navArgument

import top.xiaojiang233.nekoplayer.ui.screen.wear.WearPlaylistScreen
import top.xiaojiang233.nekoplayer.ui.screen.wear.WearSearchScreen
import top.xiaojiang233.nekoplayer.ui.screen.wear.WearSettingsScreen
import top.xiaojiang233.nekoplayer.ui.screen.PlayerScreen
import top.xiaojiang233.nekoplayer.ui.screen.WearHomeScreen
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
                onAddMusicClick = onAddMusicClick, // Pass the lambda here
                onPlaylistClick = { playlistId -> navController.navigate("playlist/$playlistId") }
            )
        }
        composable(Routes.SEARCH) {
            WearSearchScreen(
                onSongClick = { song ->
                    // Play and jump to Player screen
                    playerViewModel.playSong(song)
                    navController.navigate(Routes.PLAYER)
                }
            )
        }
        composable(Routes.SETTINGS) {
            WearSettingsScreen(
                onBackClick = { navController.popBackStack() }
            )
        }
        composable(Routes.PLAYER) {
            // Replace placeholder with actual PlayerScreen, adapted for Wear
            // Explicitly pass isWearableOverride = true
            PlayerScreen(
                viewModel = playerViewModel,
                onCloseClick = { navController.popBackStack() },
                isWearableOverride = true
            )
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(navArgument("playlistId") { type = NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId")
            if (playlistId != null) {
                WearPlaylistScreen(
                    playlistId = playlistId,
                    homeViewModel = homeViewModel,
                    playerViewModel = playerViewModel,
                    onSongClick = { navController.navigate(Routes.PLAYER) }
                )
            }
        }
    }
}
