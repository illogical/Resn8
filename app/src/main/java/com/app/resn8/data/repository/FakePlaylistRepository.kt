package com.app.resn8.data.repository

import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.model.PlaylistMembershipState
import com.app.resn8.domain.model.PlaylistWithMembership
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

    private fun normalize(name: String) = name.trim().lowercase()

    override fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>> {
        return _playlists.map { list -> list.filter { it.collectionId == collectionId } }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        return _playlists.value.find { it.id == id }
    }

    override suspend fun createPlaylist(collectionId: String, name: String): Result<Playlist> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Blank name"))
        val normalized = normalize(trimmed)
        if (_playlists.value.any { it.collectionId == collectionId && normalize(it.name) == normalized }) {
            return Result.failure(IllegalArgumentException("Duplicate playlist name"))
        }

        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            collectionId = collectionId,
            name = trimmed
        )
        _playlists.value = _playlists.value + newPlaylist
        return Result.success(newPlaylist)
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Blank name"))
        val playlist = _playlists.value.find { it.id == playlistId }
            ?: return Result.failure(IllegalArgumentException("Playlist not found"))
        val normalized = normalize(trimmed)
        if (_playlists.value.any { it.collectionId == playlist.collectionId && it.id != playlistId && normalize(it.name) == normalized }) {
            return Result.failure(IllegalArgumentException("Duplicate playlist name"))
        }

        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(name = trimmed, updatedAt = System.currentTimeMillis()) else it
        }
        return Result.success(Unit)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        _playlists.value = _playlists.value.filterNot { it.id == playlistId }
        _items.value = _items.value.filterNot { it.playlistId == playlistId }
    }

    override fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItem>> {
        return _items.map { list -> list.filter { it.playlistId == playlistId }.sortedBy { it.position } }
    }

    override suspend fun getPlaylistItems(playlistId: String): List<PlaylistItem> {
        return _items.value.filter { it.playlistId == playlistId }.sortedBy { it.position }
    }

    override suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>) {
        val currentMaxPosition = _items.value.filter { it.playlistId == playlistId }.maxOfOrNull { it.position } ?: 0L
        val existingMediaIds = _items.value.filter { it.playlistId == playlistId }.map { it.mediaId }.toSet()

        val newItems = mediaIds.filterNot { existingMediaIds.contains(it) }.mapIndexed { index, mediaId ->
            PlaylistItem(
                playlistId = playlistId,
                mediaId = mediaId,
                position = currentMaxPosition + (index + 1) * 1024L
            )
        }
        _items.value = _items.value + newItems
    }

    override suspend fun removeItemFromPlaylist(playlistId: String, mediaId: String) {
        _items.value = _items.value.filterNot { it.playlistId == playlistId && it.mediaId == mediaId }
    }

    override suspend fun removeItemsFromPlaylist(playlistId: String, mediaIds: List<String>) {
        val removeSet = mediaIds.toSet()
        _items.value = _items.value.filterNot { it.playlistId == playlistId && removeSet.contains(it.mediaId) }
    }

    override suspend fun reorderPlaylistItem(playlistId: String, mediaId: String, newPosition: Long) {
        _items.value = _items.value.map {
            if (it.playlistId == playlistId && it.mediaId == mediaId) {
                it.copy(position = newPosition)
            } else {
                it
            }
        }
    }

    override suspend fun compactPlaylistRanks(playlistId: String) {
        val sorted = _items.value.filter { it.playlistId == playlistId }.sortedBy { it.position }
        val other = _items.value.filterNot { it.playlistId == playlistId }
        val remapped = sorted.mapIndexed { index, item ->
            item.copy(position = (index + 1) * 1024L)
        }
        _items.value = other + remapped
    }

    override fun getPlaylistsWithMembershipFlow(
        collectionId: String,
        mediaIds: List<String>
    ): Flow<List<PlaylistWithMembership>> {
        val targetSet = mediaIds.toSet()
        return _playlists.map { playlists ->
            val result = playlists.filter { it.collectionId == collectionId }.map { playlist ->
                val items = _items.value.filter { it.playlistId == playlist.id }
                val itemCount = items.size
                val matchingCount = if (targetSet.isEmpty()) 0 else items.count { targetSet.contains(it.mediaId) }
                val state = when {
                    targetSet.isEmpty() -> PlaylistMembershipState.NONE
                    matchingCount == targetSet.size -> PlaylistMembershipState.ALL
                    matchingCount == 0 -> PlaylistMembershipState.NONE
                    else -> PlaylistMembershipState.SOME
                }
                PlaylistWithMembership(playlist, state, itemCount)
            }
            result.sortedWith(
                compareBy<PlaylistWithMembership> {
                    when (it.membershipState) {
                        PlaylistMembershipState.ALL -> 0
                        PlaylistMembershipState.SOME -> 1
                        PlaylistMembershipState.NONE -> 2
                    }
                }.thenBy { it.playlist.name }
            )
        }
    }
}
