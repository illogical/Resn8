package com.app.resn8.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.PlaylistMembershipState
import com.app.resn8.domain.model.QueueStartRequest
import com.app.resn8.playback.PlaybackUiState
import com.app.resn8.ui.components.PlaylistSelectorSheet
import com.app.resn8.ui.folders.FoldersViewModel
import com.app.resn8.ui.library.AlbumDetailScreen
import com.app.resn8.ui.library.AlbumDetailViewModel
import com.app.resn8.ui.library.ArtistDetailScreen
import com.app.resn8.ui.library.ArtistDetailViewModel
import com.app.resn8.ui.library.LibraryViewModel
import com.app.resn8.ui.playlists.PlaylistDetailViewModel
import com.app.resn8.ui.playlists.PlaylistsViewModel
import com.app.resn8.ui.screens.FoldersScreen
import com.app.resn8.ui.screens.LibraryScreen
import com.app.resn8.ui.screens.NowPlayingScreen
import com.app.resn8.ui.screens.OnboardingScreen
import com.app.resn8.ui.screens.PlaylistDetailScreen
import com.app.resn8.ui.screens.PlaylistsScreen
import com.app.resn8.ui.screens.QueueScreen
import com.app.resn8.ui.screens.onboarding.OnboardingViewModel
import com.app.resn8.ui.session.ActiveCollectionState
import com.app.resn8.ui.session.ActiveCollectionViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

data class PlaylistSelectorRequest(
    val mediaIds: List<String>,
    val title: String,
    val subtitle: String? = null
)

@Composable
fun Resn8NavHost(
    container: AppContainer,
    navController: NavHostController,
    startDestination: Any = OnboardingRoute,
    onRegisterOpenSelector: (((List<String>, String, String?) -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val playbackConnection = container.playbackConnection
    val playbackUiState by (playbackConnection?.uiState ?: remember { MutableStateFlow(PlaybackUiState()) }).collectAsState()
    val scope = rememberCoroutineScope()
    val activeCollectionViewModel: ActiveCollectionViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return ActiveCollectionViewModel(
                    collectionRepository = container.collectionRepository,
                    uiSessionRepository = container.uiSessionRepository
                ) as T
            }
        }
    )
    val observedActiveCollectionState by activeCollectionViewModel.state.collectAsState()
    val activeCollectionState = rememberUpdatedState(observedActiveCollectionState)
    val activeSelection = (observedActiveCollectionState as? ActiveCollectionState.Ready)?.selection

    var activeSelectorRequest by remember { mutableStateOf<PlaylistSelectorRequest?>(null) }

    val openSelector: (List<String>, String, String?) -> Unit = { mediaIds, title, subtitle ->
        if (mediaIds.isNotEmpty()) {
            activeSelectorRequest = PlaylistSelectorRequest(mediaIds, title, subtitle)
        }
    }

    androidx.compose.runtime.LaunchedEffect(openSelector) {
        onRegisterOpenSelector?.invoke(openSelector)
    }

    val simpleOpenSelector: (List<String>, String) -> Unit = { mediaIds, title ->
        openSelector(mediaIds, title, null)
    }

    Box(modifier = modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize()
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

            composable<LibraryRoute> {
                val routeState = activeCollectionState.value
                val selection = (routeState as? ActiveCollectionState.Ready)?.selection
                if (selection == null) {
                    ActiveCollectionStatus(routeState)
                } else {
                    val libraryViewModel: LibraryViewModel = viewModel(
                        key = "library-${selection.collectionId}",
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return LibraryViewModel(
                                    collectionId = selection.collectionId,
                                    sourceId = selection.sourceId,
                                    mediaRepository = container.mediaRepository,
                                    uiSessionRepository = container.uiSessionRepository
                                ) as T
                            }
                        }
                    )
                    val currentSort by libraryViewModel.sort.collectAsState()
                    val currentFilters by libraryViewModel.filters.collectAsState()
                    val searchText by libraryViewModel.searchText.collectAsState()

                    LibraryScreen(
                        viewModel = libraryViewModel,
                        onArtistClick = { artistKeySerialized ->
                            navController.navigate(ArtistDetailRoute(selection.collectionId, artistKeySerialized))
                        },
                        onAlbumClick = { albumCompositeKey ->
                            val (albumKey, albumArtistKey) = splitAlbumCompositeKey(albumCompositeKey)
                            navController.navigate(AlbumDetailRoute(selection.collectionId, albumKey, albumArtistKey))
                        },
                        onFoldersClick = {
                            navController.navigate(FoldersRoute())
                        },
                        onTrackClick = { mediaFile ->
                            val query = LibraryQuery(
                                collectionId = selection.collectionId,
                                sourceId = selection.sourceId,
                                searchText = searchText,
                                filters = currentFilters,
                                sort = currentSort
                            )
                            playbackConnection?.startQueue(
                                QueueStartRequest.Library(
                                    query = query,
                                    startingMediaId = mediaFile.id
                                )
                            )
                        },
                        onAddToPlaylist = simpleOpenSelector
                    )
                }
            }

            composable<ArtistDetailRoute> { backStackEntry ->
                val route: ArtistDetailRoute = backStackEntry.toRoute()
                val artistKey = MetadataGroupKey.deserialize(route.artistKeySerialized) ?: MetadataGroupKey.Unknown
                val viewModel: ArtistDetailViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return ArtistDetailViewModel(
                                collectionId = route.collectionId,
                                artistKey = artistKey,
                                mediaRepository = container.mediaRepository
                            ) as T
                        }
                    }
                )
                ArtistDetailScreen(
                    viewModel = viewModel,
                    onAlbumClick = { albumKeySerialized ->
                        val (albumKey, albumArtistKey) = splitAlbumCompositeKey(albumKeySerialized)
                        navController.navigate(AlbumDetailRoute(route.collectionId, albumKey, albumArtistKey))
                    },
                    onBack = { navController.popBackStack() },
                    onTrackClick = { mediaFile ->
                        val query = LibraryQuery(
                            collectionId = route.collectionId,
                            artist = artistKey
                        )
                        playbackConnection?.startQueue(
                            QueueStartRequest.Library(
                                query = query,
                                startingMediaId = mediaFile.id
                            )
                        )
                    },
                    onAddToPlaylist = simpleOpenSelector
                )
            }

            composable<AlbumDetailRoute> { backStackEntry ->
                val route: AlbumDetailRoute = backStackEntry.toRoute()
                val albumKey = MetadataGroupKey.deserialize(route.albumKeySerialized) ?: MetadataGroupKey.Unknown
                val albumArtistKey = MetadataGroupKey.deserialize(route.albumArtistKeySerialized)
                val viewModel: AlbumDetailViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return AlbumDetailViewModel(
                                collectionId = route.collectionId,
                                albumKey = albumKey,
                                albumArtistKey = albumArtistKey,
                                mediaRepository = container.mediaRepository
                            ) as T
                        }
                    }
                )
                AlbumDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onTrackClick = { mediaFile ->
                        val query = LibraryQuery(
                            collectionId = route.collectionId,
                            album = albumKey,
                            albumArtist = albumArtistKey
                        )
                        playbackConnection?.startQueue(
                            QueueStartRequest.Library(
                                query = query,
                                startingMediaId = mediaFile.id
                            )
                        )
                    },
                    onAddToPlaylist = simpleOpenSelector
                )
            }

            composable<FoldersRoute> {
                val routeState = activeCollectionState.value
                val selection = (routeState as? ActiveCollectionState.Ready)?.selection
                if (selection == null) {
                    ActiveCollectionStatus(routeState)
                } else {
                    val foldersViewModel: FoldersViewModel = viewModel(
                        key = "folders-${selection.collectionId}-${selection.sourceId}",
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return FoldersViewModel(
                                    collectionId = selection.collectionId,
                                    initialSourceId = selection.sourceId,
                                    mediaRepository = container.mediaRepository,
                                    collectionRepository = container.collectionRepository
                                ) as T
                            }
                        }
                    )
                    val currentFolderId by foldersViewModel.currentFolderId.collectAsState()

                    FoldersScreen(
                        viewModel = foldersViewModel,
                        onTrackClick = { mediaFile ->
                            val query = LibraryQuery(
                                collectionId = selection.collectionId,
                                sourceId = selection.sourceId,
                                folderId = currentFolderId
                            )
                            playbackConnection?.startQueue(
                                QueueStartRequest.Library(
                                    query = query,
                                    startingMediaId = mediaFile.id
                                )
                            )
                        },
                        onAddToPlaylist = simpleOpenSelector
                    )
                }
            }

            composable<PlaylistsRoute> {
                val routeState = activeCollectionState.value
                val selection = (routeState as? ActiveCollectionState.Ready)?.selection
                if (selection == null) {
                    ActiveCollectionStatus(routeState)
                } else {
                    val viewModel: PlaylistsViewModel = viewModel(
                        key = "playlists-${selection.collectionId}",
                        factory = object : ViewModelProvider.Factory {
                            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                                @Suppress("UNCHECKED_CAST")
                                return PlaylistsViewModel(
                                    collectionId = selection.collectionId,
                                    playlistRepository = container.playlistRepository
                                ) as T
                            }
                        }
                    )
                    PlaylistsScreen(
                        viewModel = viewModel,
                        onPlaylistClick = { playlistId ->
                            navController.navigate(PlaylistDetailRoute(playlistId = playlistId))
                        }
                    )
                }
            }

            composable<PlaylistDetailRoute> { backStackEntry ->
                val route: PlaylistDetailRoute = backStackEntry.toRoute()
                val viewModel: PlaylistDetailViewModel = viewModel(
                    key = route.playlistId,
                    factory = object : ViewModelProvider.Factory {
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            @Suppress("UNCHECKED_CAST")
                            return PlaylistDetailViewModel(
                                playlistId = route.playlistId,
                                playlistRepository = container.playlistRepository,
                                mediaRepository = container.mediaRepository
                            ) as T
                        }
                    }
                )
                val tracks by viewModel.tracks.collectAsState()
                PlaylistDetailScreen(
                    viewModel = viewModel,
                    onBack = { navController.popBackStack() },
                    onTrackClick = { mediaFile ->
                        playbackConnection?.startQueue(
                            QueueStartRequest.Playlist(
                                playlistId = route.playlistId,
                                startingMediaId = mediaFile.id
                            )
                        )
                        navController.navigate(NowPlayingRoute)
                    },
                    onPlayAll = {
                        val firstAvailableId = tracks.find { it.mediaFile.isAvailable }?.mediaFile?.id
                        if (firstAvailableId != null) {
                            playbackConnection?.startQueue(
                                QueueStartRequest.Playlist(
                                    playlistId = route.playlistId,
                                    startingMediaId = firstAvailableId
                                )
                            )
                            navController.navigate(NowPlayingRoute)
                        }
                    }
                )
            }

            composable<QueueRoute> {
                QueueScreen(
                    queueItems = playbackUiState.queueItems,
                    currentQueueItemId = playbackUiState.currentQueueItemId,
                    queueTitle = playbackUiState.queueTitle,
                    sourcePlaylistId = playbackUiState.sourcePlaylistId,
                    onOpenPlaylist = { playlistId -> navController.navigate(PlaylistDetailRoute(playlistId)) },
                    onItemClick = { queueItemId ->
                        playbackConnection?.skipToQueueItem(queueItemId)
                    },
                    onSaveAsPlaylist = openSelector
                )
            }

            composable<NowPlayingRoute> {
                NowPlayingScreen(
                    title = playbackUiState.title,
                    artist = playbackUiState.artist,
                    album = playbackUiState.album,
                    artworkUri = playbackUiState.artworkUri,
                    likeScore = playbackUiState.likeScore,
                    isPlaying = playbackUiState.isPlaying,
                    positionMs = playbackUiState.positionMs,
                    durationMs = playbackUiState.durationMs,
                    isDurationUnknown = playbackUiState.isDurationUnknown,
                    canPlayPause = playbackUiState.canPlayPause,
                    canSeek = playbackUiState.canSeek,
                    canSkipPrevious = playbackUiState.canSkipPrevious,
                    canSkipNext = playbackUiState.canSkipNext,
                    queueTitle = playbackUiState.queueTitle,
                    sourcePlaylistId = playbackUiState.sourcePlaylistId,
                    onOpenPlaylist = { playlistId -> navController.navigate(PlaylistDetailRoute(playlistId)) },
                    noticeMessage = playbackUiState.notice?.message,
                    onPlayPauseToggle = { playbackConnection?.togglePlayPause() },
                    onSeek = { pos -> playbackConnection?.seekTo(pos) },
                    onSkipPrevious = { playbackConnection?.skipToPrevious() },
                    onSkipNext = { playbackConnection?.skipToNext() },
                    onLikeClicked = { playbackConnection?.likeTrack() },
                    onDislikeClicked = { playbackConnection?.dislikeTrack() },
                    onAddToPlaylistClicked = {
                        val currentMediaId = playbackUiState.queueItems.find { it.queueItemId == playbackUiState.currentQueueItemId }?.mediaId
                        if (currentMediaId != null) {
                            openSelector(listOf(currentMediaId), "Add Currently Playing Track", null)
                        }
                    },
                    onQueueClicked = { navController.navigate(QueueRoute) },
                    onDismissNotice = { playbackConnection?.clearNotice() }
                )
            }

            composable<SettingsRoute> {
                val context = LocalContext.current
                val settingsViewModel: com.app.resn8.ui.screens.settings.SettingsViewModel = viewModel(
                    factory = com.app.resn8.ui.screens.settings.SettingsViewModel.Factory(context.applicationContext, container)
                )
                com.app.resn8.ui.screens.settings.SettingsScreen(viewModel = settingsViewModel)
            }
        }

        activeSelectorRequest?.let { selectorReq ->
            val collectionId = activeSelection?.collectionId ?: return@let
            val candidatePlaylists by container.playlistRepository
                .getPlaylistsWithMembershipFlow(collectionId, selectorReq.mediaIds)
                .collectAsState(initial = emptyList())

            PlaylistSelectorSheet(
                title = selectorReq.title,
                subtitle = selectorReq.subtitle,
                playlists = candidatePlaylists,
                onDismissRequest = { activeSelectorRequest = null },
                onTogglePlaylist = { playlistId, currentState ->
                    scope.launch {
                        if (currentState == PlaylistMembershipState.ALL) {
                            container.playlistRepository.removeItemsFromPlaylist(playlistId, selectorReq.mediaIds)
                        } else {
                            container.playlistRepository.addItemsToPlaylist(playlistId, selectorReq.mediaIds)
                        }
                    }
                },
                onCreatePlaylist = { name ->
                    val created = container.playlistRepository.createPlaylist(collectionId, name)
                    if (created.isSuccess) {
                        container.playlistRepository.addItemsToPlaylist(created.getOrThrow().id, selectorReq.mediaIds)
                        Result.success(Unit)
                    } else {
                        Result.failure(created.exceptionOrNull() ?: Exception("Failed to create playlist"))
                    }
                }
            )
        }
    }
}

@Composable
private fun ActiveCollectionStatus(state: ActiveCollectionState) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when (state) {
            ActiveCollectionState.Loading -> CircularProgressIndicator()
            ActiveCollectionState.NoCollections -> Text("Select and index a music folder to open your library.")
            ActiveCollectionState.SelectionRequired -> Text("Choose a collection before opening the library.")
            is ActiveCollectionState.Error -> Text(state.message)
            is ActiveCollectionState.Ready -> Unit
        }
    }
}

private fun splitAlbumCompositeKey(compositeKey: String): Pair<String, String?> {
    val parts = compositeKey.split("||", limit = 2)
    return parts.first() to parts.getOrNull(1)
}
