package com.app.resn8.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.app.resn8.di.AppContainer
import com.app.resn8.ui.screens.FoldersScreen
import com.app.resn8.ui.screens.LibraryScreen
import com.app.resn8.ui.screens.NowPlayingScreen
import com.app.resn8.ui.screens.OnboardingScreen
import com.app.resn8.ui.screens.PlaylistDetailScreen
import com.app.resn8.ui.screens.PlaylistsScreen
import com.app.resn8.ui.screens.QueueScreen
import com.app.resn8.ui.screens.onboarding.OnboardingViewModel

@Composable
fun Resn8NavHost(
    container: AppContainer,
    navController: NavHostController,
    startDestination: Any = OnboardingRoute,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable<OnboardingRoute> {
            val context = LocalContext.current
            val onboardingViewModel: OnboardingViewModel = viewModel(
                factory = OnboardingViewModel.Factory(context.applicationContext, container)
            )
            OnboardingScreen(
                viewModel = onboardingViewModel,
                onNavigateToLibrary = {
                    navController.navigate(LibraryRoute()) {
                        popUpTo(OnboardingRoute) { inclusive = true }
                    }
                }
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
