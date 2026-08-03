package com.app.resn8.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistDetailViewModel(
    val playlistId: String,
    private val playlistRepository: PlaylistRepository,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist

    val searchQuery = MutableStateFlow("")

    val itemsFlow = playlistRepository.getPlaylistItemsFlow(playlistId)

    val tracks: StateFlow<List<MediaFile>> = itemsFlow.combine(_playlist) { items, _ ->
        if (items.isEmpty()) emptyList()
        else {
            val mediaIds = items.map { it.mediaId }
            mediaRepository.getMediaFilesByIdsPreservingOrder(mediaIds)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredTracks: StateFlow<List<MediaFile>> = combine(tracks, searchQuery) { trackList, query ->
        val trimmed = query.trim().lowercase()
        if (trimmed.isEmpty()) {
            trackList
        } else {
            trackList.filter { track ->
                (track.title?.lowercase()?.contains(trimmed) == true) ||
                (track.artist?.lowercase()?.contains(trimmed) == true) ||
                (track.album?.lowercase()?.contains(trimmed) == true) ||
                track.filename.lowercase().contains(trimmed)
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
            val items = playlistRepository.getPlaylistItems(playlistId)
            val minPos = items.minOfOrNull { it.position } ?: 0L
            playlistRepository.reorderPlaylistItem(playlistId, mediaId, minPos - 1024L)
        }
    }

    fun moveTrackToBottom(mediaId: String) {
        viewModelScope.launch {
            val items = playlistRepository.getPlaylistItems(playlistId)
            val maxPos = items.maxOfOrNull { it.position } ?: 0L
            playlistRepository.reorderPlaylistItem(playlistId, mediaId, maxPos + 1024L)
        }
    }

    fun moveTrackUp(mediaId: String) {
        viewModelScope.launch {
            val items = playlistRepository.getPlaylistItems(playlistId)
            val index = items.indexOfFirst { it.mediaId == mediaId }
            if (index > 0) {
                val newPos = if (index == 1) {
                    items[0].position - 1024L
                } else {
                    (items[index - 2].position + items[index - 1].position) / 2
                }
                playlistRepository.reorderPlaylistItem(playlistId, mediaId, newPos)
                if (index > 1 && newPos == items[index - 2].position) {
                    playlistRepository.compactPlaylistRanks(playlistId)
                }
            }
        }
    }

    fun moveTrackDown(mediaId: String) {
        viewModelScope.launch {
            val items = playlistRepository.getPlaylistItems(playlistId)
            val index = items.indexOfFirst { it.mediaId == mediaId }
            if (index in 0 until items.size - 1) {
                val newPos = if (index == items.size - 2) {
                    items[items.size - 1].position + 1024L
                } else {
                    (items[index + 1].position + items[index + 2].position) / 2
                }
                playlistRepository.reorderPlaylistItem(playlistId, mediaId, newPos)
                if (index < items.size - 2 && newPos == items[index + 1].position) {
                    playlistRepository.compactPlaylistRanks(playlistId)
                }
            }
        }
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
