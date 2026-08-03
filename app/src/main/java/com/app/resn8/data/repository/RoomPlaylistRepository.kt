package com.app.resn8.data.repository

import android.database.sqlite.SQLiteConstraintException
import androidx.room.withTransaction
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.PlaylistEntity
import com.app.resn8.data.database.entity.PlaylistItemEntity
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.domain.model.AddItemsResult
import com.app.resn8.domain.model.MoveDirection
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.model.PlaylistMembershipState
import com.app.resn8.domain.model.PlaylistWithItemCount
import com.app.resn8.domain.model.PlaylistWithMembership
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.util.Locale
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class RoomPlaylistRepository(
    private val db: Resn8Database
) : PlaylistRepository {
    private val playlistDao = db.playlistDao()

    private fun normalizeName(name: String): String = name.trim().lowercase(Locale.ROOT)

    override fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsFlow(collectionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override fun getPlaylistsWithItemCountFlow(collectionId: String): Flow<List<PlaylistWithItemCount>> {
        return playlistDao.getPlaylistsWithItemCountFlow(collectionId).map { entities ->
            entities.map { PlaylistWithItemCount(it.playlist.toDomain(), it.itemCount) }
        }
    }

    override suspend fun getPlaylistById(id: String): Playlist? {
        return playlistDao.getPlaylistById(id)?.toDomain()
    }

    override suspend fun createPlaylist(collectionId: String, name: String): Result<Playlist> {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Playlist name cannot be blank"))
        }
        val normalized = normalizeName(trimmed)

        return try {
            db.withTransaction {
                val existing = playlistDao.getPlaylistByNormalizedName(collectionId, normalized)
                if (existing != null) {
                    return@withTransaction Result.failure(
                        IllegalArgumentException("Playlist with name '$trimmed' already exists in this collection")
                    )
                }

                val playlist = Playlist(
                    id = UUID.randomUUID().toString(),
                    collectionId = collectionId,
                    name = trimmed,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )

                playlistDao.insertPlaylist(
                    PlaylistEntity(
                        id = playlist.id,
                        collectionId = playlist.collectionId,
                        name = playlist.name,
                        normalizedName = normalized,
                        createdAt = playlist.createdAt,
                        updatedAt = playlist.updatedAt
                    )
                )

                Result.success(playlist)
            }
        } catch (e: SQLiteConstraintException) {
            Result.failure(IllegalArgumentException("Playlist with name '$trimmed' already exists in this collection", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Playlist name cannot be blank"))
        }
        val normalized = normalizeName(trimmed)

        return try {
            db.withTransaction {
                val playlist = playlistDao.getPlaylistById(playlistId)
                    ?: return@withTransaction Result.failure(IllegalArgumentException("Playlist not found"))

                val existing = playlistDao.getPlaylistByNormalizedName(playlist.collectionId, normalized)
                if (existing != null && existing.id != playlistId) {
                    return@withTransaction Result.failure(
                        IllegalArgumentException("Playlist with name '$trimmed' already exists in this collection")
                    )
                }

                playlistDao.updatePlaylist(
                    playlist.copy(
                        name = trimmed,
                        normalizedName = normalized,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                Result.success(Unit)
            }
        } catch (e: SQLiteConstraintException) {
            Result.failure(IllegalArgumentException("Playlist with name '$trimmed' already exists in this collection", e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
    }

    override fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItem>> {
        return playlistDao.getPlaylistItemsFlow(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getPlaylistItems(playlistId: String): List<PlaylistItem> {
        return playlistDao.getPlaylistItems(playlistId).map { it.toDomain() }
    }

    override suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>): AddItemsResult {
        if (mediaIds.isEmpty()) return AddItemsResult(0, 0)

        val distinctMediaIds = mediaIds.distinct()

        return db.withTransaction {
            val playlist = playlistDao.getPlaylistById(playlistId)
                ?: return@withTransaction AddItemsResult(0, 0, distinctMediaIds.size)
            val existingItems = playlistDao.getPlaylistItems(playlistId)
            val existingMediaIds = existingItems.map { it.mediaId }.toSet()

            val newMediaIds = distinctMediaIds.filterNot { existingMediaIds.contains(it) }
            val unchangedCount = distinctMediaIds.size - newMediaIds.size
            if (newMediaIds.isEmpty()) {
                return@withTransaction AddItemsResult(0, unchangedCount)
            }

            var maxPos = playlistDao.getMaxPosition(playlistId) ?: 0L
            if (maxPos > Long.MAX_VALUE - (newMediaIds.size + 1) * 1024L) {
                executeTwoPhaseCompaction(playlistId, existingItems)
                maxPos = playlistDao.getMaxPosition(playlistId) ?: 0L
            }

            val now = System.currentTimeMillis()
            val newEntities = newMediaIds.mapIndexed { index, mediaId ->
                PlaylistItemEntity(
                    playlistId = playlistId,
                    mediaId = mediaId,
                    position = maxPos + (index + 1) * 1024L,
                    addedAt = now
                )
            }

            playlistDao.insertPlaylistItems(newEntities)
            playlistDao.updatePlaylist(playlist.copy(updatedAt = now))

            AddItemsResult(addedCount = newMediaIds.size, unchangedCount = unchangedCount)
        }
    }

    override suspend fun removeItemFromPlaylist(playlistId: String, mediaId: String) {
        db.withTransaction {
            playlistDao.deletePlaylistItem(playlistId, mediaId)
            playlistDao.getPlaylistById(playlistId)?.let { playlist ->
                playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun removeItemsFromPlaylist(playlistId: String, mediaIds: List<String>) {
        if (mediaIds.isEmpty()) return
        db.withTransaction {
            playlistDao.deletePlaylistItems(playlistId, mediaIds)
            playlistDao.getPlaylistById(playlistId)?.let { playlist ->
                playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun movePlaylistItem(playlistId: String, mediaId: String, direction: MoveDirection) {
        db.withTransaction {
            val items = playlistDao.getPlaylistItems(playlistId)
            if (items.size <= 1) return@withTransaction
            val currentIndex = items.indexOfFirst { it.mediaId == mediaId }
            if (currentIndex < 0) return@withTransaction

            val targetIndex = when (direction) {
                MoveDirection.TOP -> 0
                MoveDirection.UP -> (currentIndex - 1).coerceAtLeast(0)
                MoveDirection.DOWN -> (currentIndex + 1).coerceAtMost(items.size - 1)
                MoveDirection.BOTTOM -> items.size - 1
            }

            if (currentIndex != targetIndex) {
                reorderItemsInternal(playlistId, items, currentIndex, targetIndex)
            }
        }
    }

    override suspend fun movePlaylistItemToPosition(playlistId: String, mediaId: String, targetIndex: Int) {
        db.withTransaction {
            val items = playlistDao.getPlaylistItems(playlistId)
            if (items.isEmpty()) return@withTransaction
            val currentIndex = items.indexOfFirst { it.mediaId == mediaId }
            if (currentIndex < 0) return@withTransaction
            val boundedTarget = targetIndex.coerceIn(0, items.size - 1)

            if (currentIndex != boundedTarget) {
                reorderItemsInternal(playlistId, items, currentIndex, boundedTarget)
            }
        }
    }

    private suspend fun reorderItemsInternal(
        playlistId: String,
        items: List<PlaylistItemEntity>,
        fromIndex: Int,
        toIndex: Int
    ) {
        val mutableItems = items.toMutableList()
        val movedItem = mutableItems.removeAt(fromIndex)
        mutableItems.add(toIndex, movedItem)

        executeTwoPhaseCompaction(playlistId, mutableItems)
        playlistDao.getPlaylistById(playlistId)?.let { playlist ->
            playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
        }
    }

    override suspend fun compactPlaylistRanks(playlistId: String) {
        db.withTransaction {
            val items = playlistDao.getPlaylistItems(playlistId)
            if (items.isEmpty()) return@withTransaction
            executeTwoPhaseCompaction(playlistId, items)
            playlistDao.getPlaylistById(playlistId)?.let { playlist ->
                playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    private suspend fun executeTwoPhaseCompaction(playlistId: String, items: List<PlaylistItemEntity>) {
        items.forEachIndexed { index, item ->
            playlistDao.updatePlaylistItemPosition(playlistId, item.mediaId, -(index + 1).toLong())
        }
        items.forEachIndexed { index, item ->
            playlistDao.updatePlaylistItemPosition(playlistId, item.mediaId, (index + 1) * 1024L)
        }
    }

    override fun getPlaylistsWithMembershipFlow(
        collectionId: String,
        mediaIds: List<String>
    ): Flow<List<PlaylistWithMembership>> {
        val distinctTargetIds = mediaIds.distinct()
        if (distinctTargetIds.isEmpty()) {
            return getPlaylistsWithItemCountFlow(collectionId).map { items ->
                items.map { item ->
                    PlaylistWithMembership(
                        playlist = item.playlist,
                        membershipState = PlaylistMembershipState.NONE,
                        itemCount = item.itemCount
                    )
                }.sortedWith(
                    compareBy<PlaylistWithMembership> { 2 }
                        .thenBy { normalizeName(it.playlist.name) }
                        .thenBy { it.playlist.id }
                )
            }
        }

        val playlistsFlow = playlistDao.getPlaylistsWithItemCountFlow(collectionId)
        val summariesFlow = playlistDao.getPlaylistMembershipSummariesFlow(collectionId, distinctTargetIds)

        return combine(playlistsFlow, summariesFlow) { playlistsWithCount, summaries ->
            val summaryMap = summaries.associateBy { it.playlistId }
            playlistsWithCount.map { item ->
                val summary = summaryMap[item.playlist.id]
                val matchingCount = summary?.matchingCount ?: 0
                val targetCount = distinctTargetIds.size
                val state = when {
                    matchingCount == targetCount -> PlaylistMembershipState.ALL
                    matchingCount > 0 -> PlaylistMembershipState.SOME
                    else -> PlaylistMembershipState.NONE
                }
                PlaylistWithMembership(
                    playlist = item.playlist.toDomain(),
                    membershipState = state,
                    itemCount = item.itemCount
                )
            }.sortedWith(
                compareBy<PlaylistWithMembership> {
                    when (it.membershipState) {
                        PlaylistMembershipState.ALL -> 0
                        PlaylistMembershipState.SOME -> 1
                        PlaylistMembershipState.NONE -> 2
                    }
                }
                .thenBy { normalizeName(it.playlist.name) }
                .thenBy { it.playlist.id }
            )
        }
    }
}
