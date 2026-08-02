package com.app.resn8.data.repository

import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakePlaylistRepository(
    initialPlaylists: List<Playlist> = emptyList(),
    initialItems: List<PlaylistItem> = emptyList()
) : PlaylistRepository {

    private val _playlists = MutableStateFlow(initialPlaylists)
    private val _items = MutableStateFlow(initialItems)

    override fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>> {
        return _playlists.map { list -> list.filter { it.collectionId == collectionId } }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        return _playlists.value.find { it.id == id }
    }

    override suspend fun createPlaylist(collectionId: String, name: String): Playlist {
        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            collectionId = collectionId,
            name = name
        )
        _playlists.value = _playlists.value + newPlaylist
        return newPlaylist
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(name = newName, updatedAt = System.currentTimeMillis()) else it
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        _items.value = _items.value.filterNot { it.playlistId == playlistId }
    }

    override fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItem>> {
        return _items.map { list -> list.filter { it.playlistId == playlistId }.sortedBy { it.position } }
    }

    override suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>) {
        val currentMaxPosition = _items.value.filter { it.playlistId == playlistId }.maxOfOrNull { it.position } ?: 0.0
        val newItems = mediaIds.mapIndexed { index, mediaId ->
            PlaylistItem(
                playlistId = playlistId,
                mediaId = mediaId,
                position = currentMaxPosition + index + 1.0
            )
        }
        _items.value = _items.value + newItems
    }

    override suspend fun removeItemFromPlaylist(playlistId: String, mediaId: String) {
        _items.value = _items.value.filterNot { it.playlistId == playlistId && it.mediaId == mediaId }
    }

    override suspend fun reorderPlaylistItem(playlistId: String, mediaId: String, newPosition: Double) {
        _items.value = _items.value.map {
            if (it.playlistId == playlistId && it.mediaId == mediaId) {
                it.copy(position = newPosition)
            } else {
                it
            }
        }
    }
}
