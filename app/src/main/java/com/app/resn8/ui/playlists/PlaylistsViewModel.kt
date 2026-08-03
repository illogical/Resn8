package com.app.resn8.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlaylistsViewModel(
    private val collectionId: String,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getPlaylistsFlow(collectionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun createPlaylist(name: String): Result<Playlist> {
        return playlistRepository.createPlaylist(collectionId, name)
    }

    suspend fun renamePlaylist(playlistId: String, newName: String): Result<Unit> {
        return playlistRepository.renamePlaylist(playlistId, newName)
    }

    fun deletePlaylist(playlistId: String) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }
}
