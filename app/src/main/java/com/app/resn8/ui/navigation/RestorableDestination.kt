package com.app.resn8.ui.navigation

import com.app.resn8.domain.model.LibrarySurface
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
            playlistRepository: PlaylistRepository
        ): RestorableDestination {
            val collectionId = when (destination) {
                is ArtistDetail -> destination.collectionId
                is AlbumDetail -> destination.collectionId
                is Folder -> destination.collectionId
                is PlaylistDetail -> destination.collectionId
                else -> "MUSIC"
            }
            val hasSource = collectionRepository.getRootSourcesFlow(collectionId).firstOrNull()?.any { it.isAvailable } == true
            if (!hasSource && destination != Onboarding && destination != Settings) {
                return Onboarding
            }

            return when (destination) {
                is Onboarding, is Library, is Queue, is NowPlaying, is Playlists, is Settings -> destination

                is ArtistDetail -> {
                    // Check if artist has any tracks
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
                    val folder = mediaRepository.getFolderNodesFlow("").firstOrNull()?.find { it.id == destination.folderId }
                    if (folder != null) destination else Library(LibrarySurface.FOLDERS)
                }

                is PlaylistDetail -> {
                    val playlist = playlistRepository.getPlaylistById(destination.playlistId)
                    if (playlist != null) destination else Playlists
                }
            }
        }
    }
}
