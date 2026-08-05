package com.app.resn8.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.app.resn8.ui.navigation.PlaylistDetailRoute
import com.app.resn8.ui.navigation.Resn8NavHost
import com.app.resn8.ui.navigation.SettingsRoute
import com.app.resn8.ui.startup.AppStartupCoordinator
import com.app.resn8.ui.startup.StartupState
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.restorableQueueIdForCollection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

internal fun nowPlayingPlaylistRoute(playlistId: String) = PlaylistDetailRoute(
    playlistId = playlistId,
    revealCurrentTrack = true
)

@Composable
internal fun NowPlayingPlaylistAction(
    isNowPlaying: Boolean,
    queueTitle: String?,
    sourcePlaylistId: String?,
    onOpenPlaylist: (String) -> Unit
) {
    if (!isNowPlaying || sourcePlaylistId == null) return

    TextButton(
        onClick = { onOpenPlaylist(sourcePlaylistId) },
        modifier = Modifier.testTag("now-playing-playlist-link")
    ) {
        Text(
            text = queueTitle ?: "Playlist",
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 168.dp)
        )
    }
}

data class TopLevelDestination(
    val label: String,
    val route: Any,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)

internal fun buildTopLevelDestinations(
    hasCollections: Boolean,
    activeProfile: CollectionProfile
): List<TopLevelDestination> {
    if (!hasCollections) {
        return listOf(TopLevelDestination("Onboarding", OnboardingRoute, Icons.Default.Home))
    }
    return buildList {
        add(TopLevelDestination("Settings", SettingsRoute, Icons.Default.Settings))
        if (activeProfile == CollectionProfile.MUSIC) {
            add(TopLevelDestination("Library", LibraryRoute(), Icons.Default.LibraryMusic))
        }
        add(TopLevelDestination("Folders", FoldersRoute(), Icons.Default.Folder))
        add(TopLevelDestination("Playlists", PlaylistsRoute, Icons.AutoMirrored.Filled.QueueMusic))
    }
}

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
            val startDestination = if (state is StartupState.Ready) state.startRoute else OnboardingRoute

            Resn8AppContent(
                container = container,
                navController = navController,
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
    startDestination: Any,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val collections by container.collectionRepository.getCollectionsFlow().collectAsState(initial = emptyList())
    val session by container.uiSessionRepository.getUiSessionStateFlow().collectAsState(
        initial = com.app.resn8.domain.model.UiSessionState()
    )
    val activeCollection = collections.firstOrNull { it.id == session.selectedCollectionId }
        ?: collections.singleOrNull()
    val activeProfile = activeCollection?.profile ?: CollectionProfile.MUSIC
    val hasCollections = collections.isNotEmpty()
    var collectionMenuExpanded by remember { mutableStateOf(false) }
    var collectionSwitchGeneration by remember { mutableIntStateOf(0) }

    val topLevelDestinations = remember(hasCollections, activeProfile) {
        buildTopLevelDestinations(hasCollections, activeProfile)
    }

    val playbackConnection = container.playbackConnection
    val playbackUiState by (playbackConnection?.uiState ?: remember { kotlinx.coroutines.flow.MutableStateFlow(com.app.resn8.playback.PlaybackUiState()) }).collectAsState()
    val isNowPlaying = currentDestination?.route?.contains("NowPlayingRoute") == true

    var openSelectorHandler by remember { mutableStateOf<((List<String>, String, String?) -> Unit)?>(null) }

    LaunchedEffect(navController) {
        navController.addOnDestinationChangedListener { _, destination, _ ->
            val route = destination.route ?: return@addOnDestinationChangedListener
            val simpleName = route.substringAfterLast('.').substringBefore('?')
            val mappedRoute = when {
                simpleName.contains("Settings") || simpleName.contains("CollectionDetailRoute") -> "settings"
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
                title = {
                    Box {
                        TextButton(
                            onClick = { collectionMenuExpanded = true },
                            enabled = hasCollections
                        ) {
                            Text(activeCollection?.name ?: "Resn8")
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Choose collection")
                        }
                        DropdownMenu(
                            expanded = collectionMenuExpanded,
                            onDismissRequest = { collectionMenuExpanded = false }
                        ) {
                            collections.forEach { collection ->
                                DropdownMenuItem(
                                    text = {
                                        Text(if (collection.profile == CollectionProfile.FLAT) "${collection.name} · Audio Files" else "${collection.name} · Music")
                                    },
                                    onClick = {
                                        collectionMenuExpanded = false
                                        if (collection.id != activeCollection?.id) {
                                            collectionSwitchGeneration += 1
                                            val requestedGeneration = collectionSwitchGeneration
                                            scope.launch {
                                                playbackConnection?.checkpointAndStopForCollectionSwitch()
                                                if (requestedGeneration != collectionSwitchGeneration) return@launch
                                                val source = container.collectionRepository
                                                    .getRootSourcesFlow(collection.id).firstOrNull()
                                                    ?.singleOrNull()
                                                val storedQueueId = container.collectionRepository
                                                    .getCollectionPlaybackState(collection.id)
                                                    ?.activeQueueId
                                                val storedQueue = storedQueueId?.let { queueId ->
                                                    container.queueRepository.getQueueByIdFlow(queueId).firstOrNull()
                                                }
                                                val restorableQueueId = restorableQueueIdForCollection(
                                                    collection.id,
                                                    storedQueueId,
                                                    storedQueue
                                                )
                                                if (storedQueueId != null && restorableQueueId == null) {
                                                    container.collectionRepository.setCollectionActiveQueue(collection.id, null)
                                                }
                                                val current = container.uiSessionRepository.getUiSessionStateFlow().firstOrNull()
                                                    ?: com.app.resn8.domain.model.UiSessionState()
                                                val targetRoute = when {
                                                    restorableQueueId != null -> "now_playing"
                                                    collection.profile == CollectionProfile.FLAT -> "folders"
                                                    else -> "library"
                                                }
                                                container.uiSessionRepository.saveUiSessionState(
                                                    current.copy(
                                                        currentRoute = targetRoute,
                                                        selectedCollectionId = collection.id,
                                                        selectedSourceId = source?.id,
                                                        selectedFolderId = null,
                                                        selectedArtistKey = null,
                                                        selectedAlbumKey = null,
                                                        selectedAlbumArtistKey = null,
                                                        selectedPlaylistId = null,
                                                        activeQueueId = restorableQueueId,
                                                        activeSearchQuery = null,
                                                        activeSort = if (collection.profile == CollectionProfile.FLAT) SortOrder.TITLE else SortOrder.ARTIST,
                                                        activeSurface = if (collection.profile == CollectionProfile.FLAT) LibrarySurface.FOLDERS else LibrarySurface.ARTISTS,
                                                        libraryFilterSnapshot = LibraryFilterSnapshot(),
                                                        activeFilterSnapshot = null
                                                    )
                                                )
                                                val route = when {
                                                    restorableQueueId != null -> NowPlayingRoute
                                                    collection.profile == CollectionProfile.FLAT -> FoldersRoute()
                                                    else -> LibraryRoute()
                                                }
                                                navController.navigate(route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                                                    launchSingleTop = true
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    NowPlayingPlaylistAction(
                        isNowPlaying = isNowPlaying,
                        queueTitle = playbackUiState.queueTitle,
                        sourcePlaylistId = playbackUiState.sourcePlaylistId,
                        onOpenPlaylist = { playlistId ->
                            navController.navigate(nowPlayingPlaylistRoute(playlistId))
                        }
                    )
                }
            )
        },
        bottomBar = {
            Column {
                if (!isNowPlaying) {
                    MiniPlayer(
                        title = playbackUiState.title,
                        artist = playbackUiState.artist,
                        showUnknownArtist = !playbackUiState.isFlatCollection,
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
                }
                NavigationBar {
                    topLevelDestinations.forEach { destination ->
                        val routeName = destination.route::class.simpleName ?: ""
                        val currentRoute = currentDestination?.route ?: ""

                        val isSelected = when (routeName) {
                            "PlaylistsRoute" -> currentRoute.contains("PlaylistsRoute") || currentRoute.contains("PlaylistDetailRoute")
                            "FoldersRoute" -> currentRoute.contains("FoldersRoute") || currentRoute.contains("FolderRoute")
                            "LibraryRoute" -> currentRoute.contains("LibraryRoute") || currentRoute.contains("ArtistDetailRoute") || currentRoute.contains("AlbumDetailRoute")
                            "SettingsRoute" -> currentRoute.contains("Settings") || currentRoute.contains("CollectionDetailRoute")
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
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)
        )
    }
}
