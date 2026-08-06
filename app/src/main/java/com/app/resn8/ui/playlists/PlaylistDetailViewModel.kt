package com.app.resn8.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MoveDirection
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlaylistItemUiModel(
    val originalIndex: Int,
    val mediaFile: MediaFile
)

class PlaylistDetailViewModel(
    val playlistId: String,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist

    val searchQuery = MutableStateFlow("")

    val itemsFlow = playlistRepository.getPlaylistItemsFlow(playlistId)

    val tracks: StateFlow<List<PlaylistItemUiModel>> = itemsFlow.combine(_playlist) { items, _ ->
        if (items.isEmpty()) emptyList()
        else {
            val mediaIds = items.map { it.mediaId }
            val fetchedFilesMap = mediaRepository.getMediaFilesByIdsPreservingOrder(mediaIds)
                .associateBy { it.id }

            items.mapIndexed { index, item ->
                val fetched = fetchedFilesMap[item.mediaId]
                val mediaFile = fetched ?: MediaFile(
                    id = item.mediaId,
                    sourceId = "",
                    folderId = "",
                    documentUri = "",
                    relativePath = "",
                    filename = "Unavailable Track",
                    displayTitle = "Unavailable Track (${item.mediaId.take(8)})",
                    mimeType = "audio/mpeg",
                    size = 0,
                    modifiedTimeMs = 0,
                    isAvailable = false,
                    metadataScanStatus = MetadataScanStatus.FAILED
                )
                PlaylistItemUiModel(
                    originalIndex = index + 1,
                    mediaFile = mediaFile
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTracks: StateFlow<List<PlaylistItemUiModel>> = combine(tracks, searchQuery) { trackModels, query ->
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            trackModels
        } else {
            trackModels.filter { model ->
                val track = model.mediaFile
                (track.title?.lowercase()?.contains(trimmed) == true) ||
                (track.artist?.lowercase()?.contains(trimmed) == true) ||
                (track.album?.lowercase()?.contains(trimmed) == true) ||
                track.filename.lowercase().contains(trimmed) ||
                track.displayTitle.lowercase().contains(trimmed)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            _playlist.value = playlistRepository.getPlaylistById(playlistId)
        }
    }

    fun removeTrack(mediaId: String) {
        viewModelScope.launch {
            playlistRepository.removeItemFromPlaylist(playlistId, mediaId)
        }
    }

    fun moveTrackToTop(mediaId: String) {
        viewModelScope.launch {
            playlistRepository.movePlaylistItem(playlistId, mediaId, MoveDirection.TOP)
        }
    }

    fun moveTrackToBottom(mediaId: String) {
        viewModelScope.launch {
            playlistRepository.movePlaylistItem(playlistId, mediaId, MoveDirection.BOTTOM)
        }
    }

    fun moveTrackUp(mediaId: String) {
        viewModelScope.launch {
            playlistRepository.movePlaylistItem(playlistId, mediaId, MoveDirection.UP)
        }
    }

    fun moveTrackDown(mediaId: String) {
        viewModelScope.launch {
            playlistRepository.movePlaylistItem(playlistId, mediaId, MoveDirection.DOWN)
        }
    }

    suspend fun reorderTrack(mediaId: String, targetIndex: Int): Result<Unit> = runCatching {
            playlistRepository.movePlaylistItemToPosition(playlistId, mediaId, targetIndex)
    }

    suspend fun renamePlaylist(newName: String): Result<Unit> {
        val result = playlistRepository.renamePlaylist(playlistId, newName)
        if (result.isSuccess) {
            _playlist.value = playlistRepository.getPlaylistById(playlistId)
        }
        return result
    }

    fun deletePlaylist(onDeleted: () -> Unit) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
            onDeleted()
        }
    }
}
