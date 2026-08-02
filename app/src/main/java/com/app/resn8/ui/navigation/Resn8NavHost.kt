package com.app.resn8.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.app.resn8.ui.screens.FoldersScreen
import com.app.resn8.ui.screens.LibraryScreen
import com.app.resn8.ui.screens.NowPlayingScreen
import com.app.resn8.ui.screens.OnboardingScreen
import com.app.resn8.ui.screens.PlaylistDetailScreen
import com.app.resn8.ui.screens.PlaylistsScreen
import com.app.resn8.ui.screens.QueueScreen

@Composable
fun Resn8NavHost(
    navController: NavHostController,
    startDestination: Any = LibraryRoute(),
    onSelectFolderClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<OnboardingRoute> {
            OnboardingScreen(
                onSelectFolderClicked = onSelectFolderClicked
            )
        }

        composable<LibraryRoute> { backStackEntry ->
            val route: LibraryRoute = backStackEntry.toRoute()
            LibraryScreen(
                currentTab = route.tab,
                onTabSelected = { newTab ->
                    navController.navigate(LibraryRoute(tab = newTab)) {
                        popUpTo(LibraryRoute()) { inclusive = true }
                    }
                }
            )
        }

        composable<FoldersRoute> { backStackEntry ->
            val route: FoldersRoute = backStackEntry.toRoute()
            FoldersScreen(folderId = route.folderId)
        }

        composable<PlaylistsRoute> {
            PlaylistsScreen(
                onPlaylistClick = { playlistId ->
                    navController.navigate(PlaylistDetailRoute(playlistId = playlistId))
                }
            )
        }

        composable<PlaylistDetailRoute> { backStackEntry ->
            val route: PlaylistDetailRoute = backStackEntry.toRoute()
            PlaylistDetailScreen(playlistId = route.playlistId)
        }

        composable<QueueRoute> {
            QueueScreen()
        }

        composable<NowPlayingRoute> {
            NowPlayingScreen(
                onQueueClicked = {
                    navController.navigate(QueueRoute)
                }
            )
        }
    }
}
