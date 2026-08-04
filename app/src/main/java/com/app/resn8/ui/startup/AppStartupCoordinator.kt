package com.app.resn8.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.ui.navigation.AlbumDetailRoute
import com.app.resn8.ui.navigation.ArtistDetailRoute
import com.app.resn8.ui.navigation.FoldersRoute
import com.app.resn8.ui.navigation.LibraryRoute
import com.app.resn8.ui.navigation.NowPlayingRoute
import com.app.resn8.ui.navigation.OnboardingRoute
import com.app.resn8.ui.navigation.PlaylistDetailRoute
import com.app.resn8.ui.navigation.PlaylistsRoute
import com.app.resn8.ui.navigation.QueueRoute
import com.app.resn8.ui.navigation.RestorableDestination
import com.app.resn8.ui.navigation.SettingsRoute
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

sealed interface StartupState {
    data object Loading : StartupState
    data object NeedsSetup : StartupState
    data class Ready(
        val destination: RestorableDestination,
        val startRoute: Any
    ) : StartupState
    data class RecoverableSetupProblem(val reason: String) : StartupState
}

class AppStartupCoordinator(
    private val container: AppContainer
) : ViewModel() {

    private val _state = MutableStateFlow<StartupState>(StartupState.Loading)
    val state: StateFlow<StartupState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            resolveStartupState()
        }
    }

    suspend fun resolveStartupState() {
        _state.value = StartupState.Loading

        val collections = container.collectionRepository.getCollectionsFlow().firstOrNull() ?: emptyList()
        if (collections.isEmpty()) {
            _state.value = StartupState.NeedsSetup
            return
        }

        val sessionState = container.uiSessionRepository.getUiSessionStateFlow().firstOrNull() ?: UiSessionState()
        var activeCollection = collections.firstOrNull { it.id == sessionState.selectedCollectionId }?.takeIf { col ->
            container.collectionRepository.getRootSourcesFlow(col.id).firstOrNull()?.any { it.isAvailable } == true
        } ?: collections.find { col ->
            container.collectionRepository.getRootSourcesFlow(col.id).firstOrNull()?.any { it.isAvailable } == true
        }

        if (activeCollection == null) {
            val firstCol = collections.first()
            val sources = container.collectionRepository.getRootSourcesFlow(firstCol.id).firstOrNull() ?: emptyList()
            if (sources.none { it.isAvailable }) {
                _state.value = StartupState.NeedsSetup
                return
            }
            activeCollection = firstCol
        }

        val collectionId = activeCollection.id

        val initialDest = RestorableDestination.fromSessionState(sessionState)

        val resolvedDest = RestorableDestination.resolveValidDestination(
            destination = initialDest,
            collectionRepository = container.collectionRepository,
            mediaRepository = container.mediaRepository,
            playlistRepository = container.playlistRepository,
            queueRepository = container.queueRepository,
            sessionState = sessionState
        )

        val startRoute: Any = when (resolvedDest) {
            is RestorableDestination.Onboarding -> OnboardingRoute
            is RestorableDestination.Library -> if (activeCollection.profile == CollectionProfile.FLAT) {
                FoldersRoute()
            } else {
                LibraryRoute(tab = resolvedDest.surface.name.lowercase())
            }
            is RestorableDestination.ArtistDetail -> if (activeCollection.profile == CollectionProfile.FLAT) FoldersRoute() else ArtistDetailRoute(resolvedDest.collectionId, resolvedDest.artistKey.serialize())
            is RestorableDestination.AlbumDetail -> if (activeCollection.profile == CollectionProfile.FLAT) FoldersRoute() else AlbumDetailRoute(resolvedDest.collectionId, resolvedDest.albumKey.serialize(), resolvedDest.albumArtistKey?.serialize())
            is RestorableDestination.Folder -> FoldersRoute(resolvedDest.folderId)
            is RestorableDestination.Playlists -> PlaylistsRoute
            is RestorableDestination.PlaylistDetail -> PlaylistDetailRoute(resolvedDest.playlistId)
            is RestorableDestination.Queue -> QueueRoute
            is RestorableDestination.NowPlaying -> NowPlayingRoute
            is RestorableDestination.Settings -> SettingsRoute
        }

        if (initialDest != resolvedDest || sessionState.selectedCollectionId != collectionId) {
            val updatedRouteName = when (resolvedDest) {
                is RestorableDestination.NowPlaying -> "now_playing"
                is RestorableDestination.Library -> if (activeCollection.profile == CollectionProfile.FLAT) "folders" else "library"
                is RestorableDestination.Settings -> "settings"
                else -> sessionState.currentRoute
            }
            container.uiSessionRepository.saveUiSessionState(
                sessionState.copy(
                    selectedCollectionId = collectionId,
                    currentRoute = updatedRouteName
                )
            )
        }

        _state.value = StartupState.Ready(resolvedDest, startRoute)
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AppStartupCoordinator(container) as T
        }
    }
}
