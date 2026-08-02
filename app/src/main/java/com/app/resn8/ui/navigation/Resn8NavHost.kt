package com.app.resn8.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.QueueStartRequest
import com.app.resn8.playback.PlaybackUiState
import com.app.resn8.ui.folders.FoldersViewModel
import com.app.resn8.ui.library.AlbumDetailScreen
import com.app.resn8.ui.library.AlbumDetailViewModel
import com.app.resn8.ui.library.ArtistDetailScreen
import com.app.resn8.ui.library.ArtistDetailViewModel
import com.app.resn8.ui.library.LibraryViewModel
import com.app.resn8.ui.screens.FoldersScreen
import com.app.resn8.ui.screens.LibraryScreen
import com.app.resn8.ui.screens.NowPlayingScreen
import com.app.resn8.ui.screens.OnboardingScreen
import com.app.resn8.ui.screens.PlaylistDetailScreen
import com.app.resn8.ui.screens.PlaylistsScreen
import com.app.resn8.ui.screens.QueueScreen
import com.app.resn8.ui.screens.onboarding.OnboardingViewModel
import kotlinx.coroutines.flow.MutableStateFlow

@Composable
fun Resn8NavHost(
    container: AppContainer,
    navController: NavHostController,
    startDestination: Any = OnboardingRoute,
    modifier: Modifier = Modifier
) {
    val playbackConnection = container.playbackConnection
    val playbackUiState by (playbackConnection?.uiState ?: remember { MutableStateFlow(PlaybackUiState()) }).collectAsState()

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

        composable<LibraryRoute> {
            val libraryViewModel: LibraryViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return LibraryViewModel(
                            mediaRepository = container.mediaRepository,
                            collectionRepository = container.collectionRepository,
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
                    navController.navigate(ArtistDetailRoute(artistKeySerialized = artistKeySerialized))
                },
                onAlbumClick = { albumKeySerialized ->
                    navController.navigate(AlbumDetailRoute(albumKeySerialized = albumKeySerialized))
                },
                onFoldersClick = {
                    navController.navigate(FoldersRoute())
                },
                onTrackClick = { mediaFile ->
                    val query = LibraryQuery(
                        collectionId = "DEFAULT_COLLECTION",
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
                }
            )
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
                    navController.navigate(AlbumDetailRoute(collectionId = route.collectionId, albumKeySerialized = albumKeySerialized))
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
                }
            )
        }

        composable<AlbumDetailRoute> { backStackEntry ->
            val route: AlbumDetailRoute = backStackEntry.toRoute()
            val albumKey = MetadataGroupKey.deserialize(route.albumKeySerialized) ?: MetadataGroupKey.Unknown
            val viewModel: AlbumDetailViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return AlbumDetailViewModel(
                            collectionId = route.collectionId,
                            albumKey = albumKey,
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
                        album = albumKey
                    )
                    playbackConnection?.startQueue(
                        QueueStartRequest.Library(
                            query = query,
                            startingMediaId = mediaFile.id
                        )
                    )
                }
            )
        }

        composable<FoldersRoute> {
            val foldersViewModel: FoldersViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        @Suppress("UNCHECKED_CAST")
                        return FoldersViewModel(
                            mediaRepository = container.mediaRepository,
                            collectionRepository = container.collectionRepository,
                            uiSessionRepository = container.uiSessionRepository
                        ) as T
                    }
                }
            )
            val currentFolderId by foldersViewModel.currentFolderId.collectAsState()

            FoldersScreen(
                viewModel = foldersViewModel,
                onTrackClick = { mediaFile ->
                    val query = LibraryQuery(
                        collectionId = "DEFAULT_COLLECTION",
                        folderId = currentFolderId
                    )
                    playbackConnection?.startQueue(
                        QueueStartRequest.Library(
                            query = query,
                            startingMediaId = mediaFile.id
                        )
                    )
                }
            )
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
            QueueScreen(
                queueItems = playbackUiState.queueItems,
                currentQueueItemId = playbackUiState.currentQueueItemId,
                onItemClick = { queueItemId ->
                    playbackConnection?.skipToQueueItem(queueItemId)
                }
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
                noticeMessage = playbackUiState.notice?.message,
                onPlayPauseToggle = { playbackConnection?.togglePlayPause() },
                onSeek = { pos -> playbackConnection?.seekTo(pos) },
                onSkipPrevious = { playbackConnection?.skipToPrevious() },
                onSkipNext = { playbackConnection?.skipToNext() },
                onLikeClicked = { playbackConnection?.likeTrack() },
                onDislikeClicked = { playbackConnection?.dislikeTrack() },
                onQueueClicked = { navController.navigate(QueueRoute) },
                onDismissNotice = { playbackConnection?.clearNotice() }
            )
        }
    }
}
