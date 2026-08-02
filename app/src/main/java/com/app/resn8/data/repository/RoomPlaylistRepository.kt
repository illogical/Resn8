package com.app.resn8.data.repository

import androidx.room.withTransaction
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.PlaylistEntity
import com.app.resn8.data.database.entity.PlaylistItemEntity
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomPlaylistRepository(
    private val db: Resn8Database
) : PlaylistRepository {
    private val playlistDao = db.playlistDao()

    private fun normalizeName(name: String): String = name.trim().lowercase()

    override fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>> {
        return playlistDao.getPlaylistsFlow(collectionId).map { entities ->
            entities.map { it.toDomain() }
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

        return db.withTransaction {
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
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String): Result<Unit> {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(IllegalArgumentException("Playlist name cannot be blank"))
        }
        val normalized = normalizeName(trimmed)

        return db.withTransaction {
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
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlistDao.deletePlaylist(playlistId)
    }

    override fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItem>> {
        return playlistDao.getPlaylistItemsFlow(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>) {
        if (mediaIds.isEmpty()) return

        db.withTransaction {
            val playlist = playlistDao.getPlaylistById(playlistId) ?: return@withTransaction
            val maxPos = playlistDao.getMaxPosition(playlistId) ?: 0L
            val now = System.currentTimeMillis()

            val newEntities = mediaIds.mapIndexed { index, mediaId ->
                PlaylistItemEntity(
                    playlistId = playlistId,
                    mediaId = mediaId,
                    position = maxPos + (index + 1) * 1024L,
                    addedAt = now
                )
            }

            playlistDao.insertPlaylistItems(newEntities)
            playlistDao.updatePlaylist(playlist.copy(updatedAt = now))
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

    override suspend fun reorderPlaylistItem(playlistId: String, mediaId: String, newPosition: Long) {
        db.withTransaction {
            playlistDao.updatePlaylistItemPosition(playlistId, mediaId, newPosition)
            playlistDao.getPlaylistById(playlistId)?.let { playlist ->
                playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    override suspend fun compactPlaylistRanks(playlistId: String) {
        db.withTransaction {
            val currentItems = playlistDao.getPlaylistItems(playlistId)
            if (currentItems.isEmpty()) return@withTransaction

            playlistDao.deleteAllPlaylistItems(playlistId)

            val compacted = currentItems.mapIndexed { index, item ->
                item.copy(position = (index + 1) * 1024L)
            }
            playlistDao.insertPlaylistItems(compacted)

            playlistDao.getPlaylistById(playlistId)?.let { playlist ->
                playlistDao.updatePlaylist(playlist.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }
}
