package com.app.resn8.data.repository

import com.app.resn8.domain.model.AddItemsResult
import com.app.resn8.domain.model.MoveDirection
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.model.PlaylistRandomizationResult
import com.app.resn8.domain.model.PlaylistRandomizedSortMethod
import com.app.resn8.domain.model.PlaylistRandomizedSorter
import com.app.resn8.domain.model.PlaylistMembershipState
import com.app.resn8.domain.model.PlaylistWithItemCount
import com.app.resn8.domain.model.PlaylistWithMembership
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID
import kotlin.random.Random

class FakePlaylistRepository(
    initialPlaylists: List<Playlist> = emptyList(),
    initialItems: List<PlaylistItem> = emptyList(),
    initialMediaFiles: List<MediaFile> = emptyList(),
    private val randomFactory: () -> Random = { Random.Default }
) : PlaylistRepository {

    private val _playlists = MutableStateFlow(initialPlaylists)
    private val _items = MutableStateFlow(initialItems)
    private val mediaFilesById = initialMediaFiles.associateBy { it.id }.toMutableMap()

    private fun normalize(name: String) = name.trim().lowercase(Locale.ROOT)

    override fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>> {
        return _playlists.map { list -> list.filter { it.collectionId == collectionId } }
    }

    override fun getPlaylistsWithItemCountFlow(collectionId: String): Flow<List<PlaylistWithItemCount>> {
        return combine(_playlists, _items) { playlists, items ->
            playlists.filter { it.collectionId == collectionId }.map { playlist ->
                val count = items.count { it.playlistId == playlist.id }
                PlaylistWithItemCount(playlist, count)
            }.sortedBy { it.playlist.name }
        }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        return _playlists.value.find { it.id == id }
    }

    override suspend fun createPlaylist(collectionId: String, name: String): Result<Playlist> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Playlist name cannot be blank"))
        val normalized = normalize(trimmed)
        if (_playlists.value.any { it.collectionId == collectionId && normalize(it.name) == normalized }) {
            return Result.failure(IllegalArgumentException("Playlist with name '$trimmed' already exists in this collection"))
        }

        val newPlaylist = Playlist(
            id = UUID.randomUUID().toString(),
            collectionId = collectionId,
            name = trimmed,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        _playlists.value = _playlists.value + newPlaylist
        return Result.success(newPlaylist)
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("Playlist name cannot be blank"))
        val playlist = _playlists.value.find { it.id == playlistId }
            ?: return Result.failure(IllegalArgumentException("Playlist not found"))
        val normalized = normalize(trimmed)
        if (_playlists.value.any { it.collectionId == playlist.collectionId && it.id != playlistId && normalize(it.name) == normalized }) {
            return Result.failure(IllegalArgumentException("Playlist with name '$trimmed' already exists in this collection"))
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

    override suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>): AddItemsResult {
        if (mediaIds.isEmpty()) return AddItemsResult(0, 0)
        val distinctMediaIds = mediaIds.distinct()
        val playlist = _playlists.value.find { it.id == playlistId }
            ?: return AddItemsResult(0, 0, distinctMediaIds.size)

        val currentItems = _items.value.filter { it.playlistId == playlistId }
        val currentMaxPosition = currentItems.maxOfOrNull { it.position } ?: 0L
        val existingMediaIds = currentItems.map { it.mediaId }.toSet()

        val newMediaIds = distinctMediaIds.filterNot { existingMediaIds.contains(it) }
        val unchangedCount = distinctMediaIds.size - newMediaIds.size
        if (newMediaIds.isEmpty()) return AddItemsResult(0, unchangedCount)

        val now = System.currentTimeMillis()
        val newItems = newMediaIds.mapIndexed { index, mediaId ->
            PlaylistItem(
                playlistId = playlistId,
                mediaId = mediaId,
                position = currentMaxPosition + (index + 1) * 1024L,
                addedAt = now
            )
        }
        _items.value = _items.value + newItems
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(updatedAt = now) else it
        }
        return AddItemsResult(addedCount = newMediaIds.size, unchangedCount = unchangedCount)
    }

    override suspend fun removeItemFromPlaylist(playlistId: String, mediaId: String) {
        _items.value = _items.value.filterNot { it.playlistId == playlistId && it.mediaId == mediaId }
        val now = System.currentTimeMillis()
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(updatedAt = now) else it
        }
    }

    override suspend fun removeItemsFromPlaylist(playlistId: String, mediaIds: List<String>) {
        val removeSet = mediaIds.toSet()
        _items.value = _items.value.filterNot { it.playlistId == playlistId && removeSet.contains(it.mediaId) }
        val now = System.currentTimeMillis()
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(updatedAt = now) else it
        }
    }

    override suspend fun movePlaylistItem(playlistId: String, mediaId: String, direction: MoveDirection) {
        val items = _items.value.filter { it.playlistId == playlistId }.sortedBy { it.position }
        if (items.size <= 1) return
        val currentIndex = items.indexOfFirst { it.mediaId == mediaId }
        if (currentIndex < 0) return

        val targetIndex = when (direction) {
            MoveDirection.TOP -> 0
            MoveDirection.UP -> (currentIndex - 1).coerceAtLeast(0)
            MoveDirection.DOWN -> (currentIndex + 1).coerceAtMost(items.size - 1)
            MoveDirection.BOTTOM -> items.size - 1
        }

        if (currentIndex != targetIndex) {
            reorderInternal(playlistId, items, currentIndex, targetIndex)
        }
    }

    override suspend fun movePlaylistItemToPosition(playlistId: String, mediaId: String, targetIndex: Int) {
        val items = _items.value.filter { it.playlistId == playlistId }.sortedBy { it.position }
        if (items.isEmpty()) return
        val currentIndex = items.indexOfFirst { it.mediaId == mediaId }
        if (currentIndex < 0) return
        val boundedTarget = targetIndex.coerceIn(0, items.size - 1)

        if (currentIndex != boundedTarget) {
            reorderInternal(playlistId, items, currentIndex, boundedTarget)
        }
    }

    private fun reorderInternal(playlistId: String, items: List<PlaylistItem>, fromIndex: Int, toIndex: Int) {
        val mutable = items.toMutableList()
        val item = mutable.removeAt(fromIndex)
        mutable.add(toIndex, item)

        val remapped = mutable.mapIndexed { index, pi ->
            pi.copy(position = (index + 1) * 1024L)
        }

        val other = _items.value.filterNot { it.playlistId == playlistId }
        _items.value = other + remapped

        val now = System.currentTimeMillis()
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(updatedAt = now) else it
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

    override suspend fun applyRandomizedSorting(
        playlistId: String,
        method: PlaylistRandomizedSortMethod
    ): Result<PlaylistRandomizationResult> = runCatching {
        if (_playlists.value.none { it.id == playlistId }) {
            throw IllegalArgumentException("Playlist not found")
        }
        val playlistItems = _items.value.filter { it.playlistId == playlistId }.sortedBy { it.position }
        val mediaFiles = playlistItems.map { item ->
            mediaFilesById[item.mediaId]
                ?: throw IllegalStateException("Playlist metadata changed while randomized sorting was applied")
        }
        val order = PlaylistRandomizedSorter.sort(mediaFiles, method, randomFactory())
        val retainedItems = playlistItems.associateBy { it.mediaId }
        val remapped = order.orderedMedia.mapIndexed { index, media ->
            retainedItems.getValue(media.id).copy(position = (index + 1) * 1024L)
        }
        _items.value = _items.value.filterNot { it.playlistId == playlistId } + remapped
        val now = System.currentTimeMillis()
        _playlists.value = _playlists.value.map {
            if (it.id == playlistId) it.copy(updatedAt = now) else it
        }
        PlaylistRandomizationResult(
            orderedMediaIds = order.orderedMedia.map { it.id },
            availableOrderedMediaIds = order.orderedMedia.filter { it.isAvailable }.map { it.id },
            removedDislikedCount = order.removedDislikedMediaIds.size
        )
    }

    override fun getPlaylistsWithMembershipFlow(
        collectionId: String,
        mediaIds: List<String>
    ): Flow<List<PlaylistWithMembership>> {
        val distinctTargetSet = mediaIds.distinct().toSet()
        return combine(_playlists, _items) { playlists, allItems ->
            val collectionPlaylists = playlists.filter { it.collectionId == collectionId }
            val result = collectionPlaylists.map { playlist ->
                val items = allItems.filter { it.playlistId == playlist.id }
                val itemCount = items.size
                val matchingCount = if (distinctTargetSet.isEmpty()) 0 else items.count { distinctTargetSet.contains(it.mediaId) }
                val state = when {
                    distinctTargetSet.isEmpty() -> PlaylistMembershipState.NONE
                    matchingCount == distinctTargetSet.size -> PlaylistMembershipState.ALL
                    matchingCount > 0 -> PlaylistMembershipState.SOME
                    else -> PlaylistMembershipState.NONE
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
                }
                .thenBy { normalize(it.playlist.name) }
                .thenBy { it.playlist.id }
            )
        }
    }
}
