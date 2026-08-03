package com.app.resn8.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
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
import com.app.resn8.ui.navigation.SettingsRoute
import com.app.resn8.ui.startup.AppStartupCoordinator
import com.app.resn8.ui.startup.StartupState
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

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
    val startupCoordinator: AppStartupCoordinator = viewModel(
        factory = AppStartupCoordinator.Factory(container)
    )
    val startupState by startupCoordinator.state.collectAsState()

    when (val state = startupState) {
        StartupState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
        StartupState.NeedsSetup, is StartupState.Ready, is StartupState.RecoverableSetupProblem -> {
            val isSetupComplete = state is StartupState.Ready
            val startDestination = if (state is StartupState.Ready) state.startRoute else OnboardingRoute

            Resn8AppContent(
                container = container,
                navController = navController,
                isSetupComplete = isSetupComplete,
                startDestination = startDestination,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Resn8AppContent(
    container: AppContainer,
    navController: NavHostController,
    isSetupComplete: Boolean,
    startDestination: Any,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

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

    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val route = destination.route ?: return@addOnDestinationChangedListener
            val simpleName = route.substringAfterLast('.').substringBefore('?')
            val mappedRoute = when {
                simpleName.contains("SettingsRoute") -> "settings"
                simpleName.contains("NowPlayingRoute") -> "now_playing"
                simpleName.contains("QueueRoute") -> "queue"
                simpleName.contains("PlaylistsRoute") -> "playlists"
                simpleName.contains("PlaylistDetailRoute") -> "playlist_detail"
                simpleName.contains("FoldersRoute") -> "folders"
                simpleName.contains("LibraryRoute") -> "library"
                simpleName.contains("ArtistDetailRoute") -> "artist_detail"
                simpleName.contains("AlbumDetailRoute") -> "album_detail"
                else -> null
            }
            if (mappedRoute != null) {
                scope.launch {
                    val current = container.uiSessionRepository.getUiSessionStateFlow().firstOrNull()
                        ?: com.app.resn8.domain.model.UiSessionState()
                    if (current.currentRoute != mappedRoute) {
                        container.uiSessionRepository.saveUiSessionState(current.copy(currentRoute = mappedRoute))
                    }
                }
            }
        }
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
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = false
                                    }
                                    launchSingleTop = true
                                    restoreState = false
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
