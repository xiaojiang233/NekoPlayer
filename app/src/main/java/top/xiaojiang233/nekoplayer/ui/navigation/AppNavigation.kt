package top.xiaojiang233.nekoplayer.ui.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import top.xiaojiang233.nekoplayer.ui.screen.HomeScreen
import top.xiaojiang233.nekoplayer.ui.screen.PlayerScreen
import top.xiaojiang233.nekoplayer.ui.screen.SearchScreen
import top.xiaojiang233.nekoplayer.ui.screen.SettingsScreen
import top.xiaojiang233.nekoplayer.viewmodel.HomeViewModel
import top.xiaojiang233.nekoplayer.viewmodel.PlayerViewModel

object Routes {
    const val HOME = "home"
    const val SEARCH = "search"
    const val PLAYER = "player"
    const val SETTINGS = "settings"
    const val PLAYLIST = "playlist/{playlistId}"
}

@Composable
fun AppNavigation(
    playerViewModel: PlayerViewModel,
    homeViewModel: HomeViewModel,
    onAddMusicClick: () -> Unit
) {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
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
            SearchScreen(
                playerViewModel = playerViewModel,
                onBackClick = { navController.popBackStack() },
                onSongClick = {
                    playerViewModel.playSong(it)
                    navController.navigate(Routes.PLAYER)
                },
                onPlayerClick = { navController.navigate(Routes.PLAYER) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBackClick = { navController.popBackStack() })
        }
        composable(
            route = Routes.PLAYER,
            enterTransition = {
                slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(durationMillis = 400)
                )
            },
            exitTransition = {
                slideOutVertically(
                    targetOffsetY = { it },
                    animationSpec = tween(durationMillis = 400)
                )
            }
        ) {
            PlayerScreen(
                viewModel = playerViewModel,
                onCloseClick = { navController.popBackStack() }
            )
        }
        composable(
            route = Routes.PLAYLIST,
            arguments = listOf(androidx.navigation.navArgument("playlistId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val playlistId = backStackEntry.arguments?.getString("playlistId") ?: return@composable
            top.xiaojiang233.nekoplayer.ui.screen.PlaylistScreen(
                playlistId = playlistId,
                onBackClick = { navController.popBackStack() },
                onPlayerClick = { navController.navigate(Routes.PLAYER) },
                homeViewModel = homeViewModel,
                playerViewModel = playerViewModel
            )
        }
    }
}
