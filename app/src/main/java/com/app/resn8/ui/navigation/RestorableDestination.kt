package com.app.resn8.ui.navigation

import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.firstOrNull

sealed interface RestorableDestination {
    data object Onboarding : RestorableDestination
    data class Library(val surface: LibrarySurface = LibrarySurface.ARTISTS) : RestorableDestination
    data class ArtistDetail(val collectionId: String, val artistKey: MetadataGroupKey) : RestorableDestination
    data class AlbumDetail(
        val collectionId: String,
        val albumKey: MetadataGroupKey,
        val albumArtistKey: MetadataGroupKey?
    ) : RestorableDestination
    data class Folder(val collectionId: String, val folderId: String) : RestorableDestination
    data object Playlists : RestorableDestination
    data class PlaylistDetail(val collectionId: String, val playlistId: String) : RestorableDestination
    data object Queue : RestorableDestination
    data object NowPlaying : RestorableDestination
    data object Settings : RestorableDestination

    companion object {
        fun fromSessionState(state: UiSessionState): RestorableDestination {
            val route = state.currentRoute
            val collectionId = state.selectedCollectionId ?: "MUSIC"
            return when {
                route.startsWith("settings") -> Settings
                route.startsWith("queue") -> Queue
                route.startsWith("now_playing") -> NowPlaying
                route.startsWith("playlist_detail") && state.selectedPlaylistId != null ->
                    PlaylistDetail(collectionId, state.selectedPlaylistId)
                route.startsWith("playlists") -> Playlists
                route.startsWith("folder") && state.selectedFolderId != null ->
                    Folder(collectionId, state.selectedFolderId)
                route.startsWith("album_detail") && state.selectedAlbumKey != null ->
                    AlbumDetail(collectionId, state.selectedAlbumKey, state.selectedAlbumArtistKey)
                route.startsWith("artist_detail") && state.selectedArtistKey != null ->
                    ArtistDetail(collectionId, state.selectedArtistKey)
                route.startsWith("library") -> Library(state.activeSurface)
                route.startsWith("onboarding") -> Onboarding
                else -> Library(state.activeSurface)
            }
        }

        suspend fun resolveValidDestination(
            destination: RestorableDestination,
            collectionRepository: CollectionRepository,
            mediaRepository: MediaRepository,
            playlistRepository: PlaylistRepository,
            queueRepository: com.app.resn8.domain.repository.QueueRepository? = null,
            sessionState: UiSessionState? = null
        ): RestorableDestination {
            val collections = collectionRepository.getCollectionsFlow().firstOrNull() ?: emptyList()
            val selectedCollection = collections.firstOrNull { it.id == sessionState?.selectedCollectionId }
            val availableCol = selectedCollection?.takeIf { col ->
                collectionRepository.getRootSourcesFlow(col.id).firstOrNull()?.any { it.isAvailable } == true
            } ?: collections.find { col ->
                collectionRepository.getRootSourcesFlow(col.id).firstOrNull()?.any { it.isAvailable } == true
            }
            val hasSource = availableCol != null

            if (!hasSource && destination != Settings) {
                return Onboarding
            }

            if (hasSource && destination == Onboarding) {
                val activeQueueId = sessionState?.activeQueueId
                if (activeQueueId != null && queueRepository != null) {
                    val savedQueue = queueRepository.getQueueByIdFlow(activeQueueId).firstOrNull()
                    if (savedQueue != null && savedQueue.items.isNotEmpty()) {
                        return NowPlaying
                    }
                }
                return if (availableCol?.profile == CollectionProfile.FLAT) {
                    availableCol.let { col ->
                        val source = collectionRepository.getRootSourcesFlow(col.id).firstOrNull()?.firstOrNull()
                        val folder = source?.let { mediaRepository.getRootFolderNode(it.id).firstOrNull() }
                        if (folder != null) Folder(col.id, folder.id) else Library(LibrarySurface.FOLDERS)
                    }
                } else Library(sessionState?.activeSurface ?: LibrarySurface.ARTISTS)
            }

            if (availableCol?.profile == CollectionProfile.FLAT &&
                (destination is Library || destination is ArtistDetail || destination is AlbumDetail)
            ) {
                val source = collectionRepository.getRootSourcesFlow(availableCol.id).firstOrNull()?.firstOrNull()
                val folder = source?.let { mediaRepository.getRootFolderNode(it.id).firstOrNull() }
                return if (folder != null) Folder(availableCol.id, folder.id) else Library(LibrarySurface.FOLDERS)
            }

            val destinationCollectionId = when (destination) {
                is ArtistDetail -> destination.collectionId
                is AlbumDetail -> destination.collectionId
                is Folder -> destination.collectionId
                is PlaylistDetail -> destination.collectionId
                else -> null
            }
            if (destinationCollectionId != null && destinationCollectionId != availableCol?.id) {
                return if (availableCol?.profile == CollectionProfile.FLAT) {
                    val source = collectionRepository.getRootSourcesFlow(availableCol.id).firstOrNull()?.firstOrNull()
                    val root = source?.let { mediaRepository.getRootFolderNode(it.id).firstOrNull() }
                    if (root != null) Folder(availableCol.id, root.id) else Library(LibrarySurface.FOLDERS)
                } else Library(LibrarySurface.ARTISTS)
            }

            return when (destination) {
                is Onboarding, is Library, is Queue, is NowPlaying, is Playlists, is Settings -> destination

                is ArtistDetail -> {
                    val artistTracks = mediaRepository.snapshotVisibleMediaIds(
                        com.app.resn8.domain.model.LibraryQuery(
                            collectionId = destination.collectionId,
                            artist = destination.artistKey
                        )
                    )
                    if (artistTracks.isNotEmpty()) destination else Library(LibrarySurface.ARTISTS)
                }

                is AlbumDetail -> {
                    val albumTracks = mediaRepository.snapshotVisibleMediaIds(
                        com.app.resn8.domain.model.LibraryQuery(
                            collectionId = destination.collectionId,
                            album = destination.albumKey,
                            albumArtist = destination.albumArtistKey
                        )
                    )
                    if (albumTracks.isNotEmpty()) destination else Library(LibrarySurface.ALBUMS)
                }

                is Folder -> {
                    val source = collectionRepository.getRootSourcesFlow(destination.collectionId).firstOrNull()
                        ?.firstOrNull()
                    val folder = source?.let { mediaRepository.getFolderNodesFlow(it.id).firstOrNull() }
                        ?.find { it.id == destination.folderId }
                    if (folder != null) destination else if (availableCol?.profile == CollectionProfile.FLAT) {
                        val root = source?.let { mediaRepository.getRootFolderNode(it.id).firstOrNull() }
                        if (root != null) Folder(destination.collectionId, root.id) else Library(LibrarySurface.FOLDERS)
                    } else Library(LibrarySurface.FOLDERS)
                }

                is PlaylistDetail -> {
                    val playlist = playlistRepository.getPlaylistById(destination.playlistId)
                    if (playlist != null) destination else Playlists
                }
            }
        }
    }
}
