package com.app.resn8.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.app.resn8.di.AppContainer
import com.app.resn8.ui.components.MiniPlayer
import com.app.resn8.ui.navigation.FoldersRoute
import com.app.resn8.ui.navigation.LibraryRoute
import com.app.resn8.ui.navigation.NowPlayingRoute
import com.app.resn8.ui.navigation.OnboardingRoute
import com.app.resn8.ui.navigation.PlaylistsRoute
import com.app.resn8.ui.navigation.Resn8NavHost

import androidx.compose.material.icons.filled.Settings
import com.app.resn8.ui.navigation.SettingsRoute

data class TopLevelDestination(
    val label: String,
    val route: Any,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Resn8App(
    container: AppContainer,
    navController: NavHostController = rememberNavController(),
    modifier: Modifier = Modifier
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val rootSources by container.collectionRepository.getRootSourcesFlow("MUSIC").collectAsState(initial = emptyList())
    val isSetupComplete = rootSources.any { it.isAvailable }

    val topLevelDestinations = remember(isSetupComplete) {
        val firstDest = if (isSetupComplete) {
            TopLevelDestination("Settings", SettingsRoute, Icons.Default.Settings)
        } else {
            TopLevelDestination("Onboarding", OnboardingRoute, Icons.Default.Home)
        }
        listOf(
            firstDest,
            TopLevelDestination("Library", LibraryRoute(), Icons.Default.LibraryMusic),
            TopLevelDestination("Folders", FoldersRoute(), Icons.Default.Folder),
            TopLevelDestination("Playlists", PlaylistsRoute, Icons.AutoMirrored.Filled.QueueMusic)
        )
    }

    val playbackConnection = container.playbackConnection
    val playbackUiState by (playbackConnection?.uiState ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.app.resn8.playback.PlaybackUiState()) }).collectAsState()

    var openSelectorHandler by remember { mutableStateOf<((List<String>, String, String?) -> Unit)?>(null) }

    val startDestination = remember(isSetupComplete) {
        if (isSetupComplete) LibraryRoute() else OnboardingRoute
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Resn8") }
            )
        },
        bottomBar = {
            Column {
                MiniPlayer(
                    title = playbackUiState.title,
                    artist = playbackUiState.artist,
                    isPlaying = playbackUiState.isPlaying,
                    likeScore = playbackUiState.likeScore,
                    canPlayPause = playbackUiState.canPlayPause,
                    canSkipNext = playbackUiState.canSkipNext,
                    onMiniPlayerClick = {
                        navController.navigate(NowPlayingRoute)
                    },
                    onPlayPauseClick = {
                        playbackConnection?.togglePlayPause()
                    },
                    onNextClick = {
                        playbackConnection?.skipToNext()
                    },
                    onAddToPlaylistClick = {
                        val currentMediaId = playbackUiState.queueItems.find { it.queueItemId == playbackUiState.currentQueueItemId }?.mediaId
                        if (currentMediaId != null) {
                            openSelectorHandler?.invoke(listOf(currentMediaId), "Add Currently Playing Track", null)
                        }
                    }
                )
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val routeName = destination.route::class.simpleName ?: ""
                        val currentRoute = currentDestination?.route ?: ""

                        val isSelected = when (routeName) {
                            "PlaylistsRoute" -> currentRoute.contains("PlaylistsRoute") || currentRoute.contains("PlaylistDetailRoute")
                            "FoldersRoute" -> currentRoute.contains("FoldersRoute") || currentRoute.contains("FolderRoute")
                            "LibraryRoute" -> currentRoute.contains("LibraryRoute") || currentRoute.contains("ArtistDetailRoute") || currentRoute.contains("AlbumDetailRoute")
                            else -> currentRoute.contains(routeName)
                        }

                        NavigationBarItem(
                            selected = isSelected,
                            onClick = {
                                if (isSelected) {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = false
                                        }
                                        launchSingleTop = true
                                        restoreState = false
                                    }
                                } else {
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = destination.label) },
                            label = { Text(destination.label) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Resn8NavHost(
            container = container,
            navController = navController,
            startDestination = startDestination,
            onRegisterOpenSelector = { handler -> openSelectorHandler = handler },
            modifier = Modifier.padding(innerPadding)
        )
    }
}
