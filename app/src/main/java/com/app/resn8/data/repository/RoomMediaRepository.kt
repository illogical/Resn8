package com.app.resn8.data.repository

import androidx.room.withTransaction
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.PlaybackHistoryEntity
import com.app.resn8.data.database.entity.ScanRunEntity
import com.app.resn8.data.database.entity.toEntity
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.StagedFolder
import com.app.resn8.domain.model.StagedMedia
import com.app.resn8.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomMediaRepository(
    private val db: Resn8Database
) : MediaRepository {
    private val mediaFileDao = db.mediaFileDao()
    private val folderDao = db.folderDao()
    private val scanDao = db.scanDao()
    private val playbackHistoryDao = db.playbackHistoryDao()

    override fun getMediaFilesFlow(
        collectionId: String?,
        folderId: String?,
        artist: String?,
        album: String?,
        searchQuery: String?,
        sortOrder: SortOrder
    ): Flow<List<MediaFile>> {
        return mediaFileDao.getMediaFilesFlow(
            folderId = folderId,
            artist = artist,
            album = album,
            searchQuery = searchQuery,
            sortOrder = sortOrder.name
        ).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getMediaFileById(id: String): MediaFile? {
        return mediaFileDao.getMediaFileById(id)?.toDomain()
    }

    override fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNode>> {
        return folderDao.getFolderNodesFlow(sourceId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateLikeScore(mediaId: String, delta: Int) {
        require(delta == 1 || delta == -1) { "Like score delta must be +1 or -1" }
        db.withTransaction {
            mediaFileDao.updateLikeScore(mediaId, delta)
        }
    }

    override suspend fun recordPlay(mediaId: String, listenedDurationMs: Long, isMeaningful: Boolean) {
        val result = if (isMeaningful) PlaybackHistoryResult.THRESHOLD_COUNTED else PlaybackHistoryResult.DISCARDED
        val sessionOccurrenceId = UUID.randomUUID().toString()
        commitMeaningfulPlay(
            sessionOccurrenceId = sessionOccurrenceId,
            mediaId = mediaId,
            startedAt = System.currentTimeMillis() - listenedDurationMs,
            endedAt = System.currentTimeMillis(),
            accumulatedListenedDurationMs = listenedDurationMs,
            result = result
        )
    }

    override suspend fun commitMeaningfulPlay(
        sessionOccurrenceId: String,
        mediaId: String,
        startedAt: Long,
        endedAt: Long?,
        accumulatedListenedDurationMs: Long,
        result: PlaybackHistoryResult
    ): Boolean {
        return db.withTransaction {
            val existing = playbackHistoryDao.getHistoryByOccurrenceId(sessionOccurrenceId)
            if (existing != null && (existing.result == PlaybackHistoryResult.THRESHOLD_COUNTED || existing.result == PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED)) {
                return@withTransaction false
            }

            val now = System.currentTimeMillis()
            val historyEntity = PlaybackHistoryEntity(
                id = existing?.id ?: UUID.randomUUID().toString(),
                mediaId = mediaId,
                sessionOccurrenceId = sessionOccurrenceId,
                startedAt = startedAt,
                endedAt = endedAt,
                accumulatedListenedDurationMs = accumulatedListenedDurationMs,
                result = result,
                countedAt = if (result == PlaybackHistoryResult.THRESHOLD_COUNTED || result == PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED) now else null
            )

            if (existing == null) {
                playbackHistoryDao.insertHistory(historyEntity)
            } else {
                playbackHistoryDao.updateHistory(historyEntity)
            }

            if (result == PlaybackHistoryResult.THRESHOLD_COUNTED || result == PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED) {
                mediaFileDao.incrementPlayCount(mediaId, now)
            }
            true
        }
    }

    override suspend fun updateMediaAvailability(mediaId: String, isAvailable: Boolean) {
        mediaFileDao.updateAvailability(mediaId, isAvailable)
    }

    override suspend fun startScanRun(sourceId: String): String {
        val scanId = UUID.randomUUID().toString()
        val scanRun = ScanRunEntity(
            id = scanId,
            sourceId = sourceId,
            status = "IN_PROGRESS",
            startedAt = System.currentTimeMillis()
        )
        db.withTransaction {
            scanDao.insertScanRun(scanRun)
        }
        return scanId
    }

    override suspend fun stageFolders(scanId: String, folders: List<StagedFolder>) {
        val entities = folders.map { it.toEntity() }
        scanDao.insertStagedFolders(entities)
    }

    override suspend fun stageMedia(scanId: String, media: List<StagedMedia>) {
        val entities = media.map { it.toEntity() }
        scanDao.insertStagedMedia(entities)
    }

    override suspend fun publishResolvedScan(
        scanId: String,
        resolvedFolders: List<FolderNode>,
        resolvedMedia: List<MediaFile>,
        unavailableMediaIds: List<String>,
        scanResult: ScanResult
    ) {
        db.withTransaction {
            resolvedFolders.forEach { folderNode ->
                val entity = folderNode.toEntity()
                val existing = folderDao.getFolderNodeById(entity.id)
                if (existing != null) {
                    folderDao.updateFolderNode(entity)
                } else {
                    folderDao.insertFolderNode(entity)
                }
            }

            resolvedMedia.forEach { domainMedia ->
                val existing = mediaFileDao.getMediaFileById(domainMedia.id)
                if (existing != null) {
                    val updatedEntity = domainMedia.toEntity().copy(
                        firstIndexedAt = existing.firstIndexedAt,
                        playCount = existing.playCount,
                        lastPlayedAt = existing.lastPlayedAt,
                        likeScore = existing.likeScore
                    )
                    mediaFileDao.updateMediaFile(updatedEntity)
                } else {
                    mediaFileDao.insertMediaFile(domainMedia.toEntity())
                }
            }

            if (unavailableMediaIds.isNotEmpty()) {
                mediaFileDao.updateAvailabilityForIds(unavailableMediaIds, false)
            }

            scanDao.updateScanRunStatus(
                scanId = scanId,
                status = "COMPLETED",
                completedAt = System.currentTimeMillis()
            )

            scanDao.deleteStagedFolders(scanId)
            scanDao.deleteStagedMedia(scanId)
        }
    }

    override suspend fun cancelScanRun(scanId: String) {
        db.withTransaction {
            scanDao.updateScanRunStatus(
                scanId = scanId,
                status = "CANCELLED",
                completedAt = System.currentTimeMillis()
            )
            scanDao.deleteStagedFolders(scanId)
            scanDao.deleteStagedMedia(scanId)
        }
    }

    override suspend fun failScanRun(scanId: String, errorSummary: String) {
        db.withTransaction {
            scanDao.updateScanRunStatus(
                scanId = scanId,
                status = "FAILED",
                completedAt = System.currentTimeMillis(),
                errorSummary = errorSummary
            )
            scanDao.deleteStagedFolders(scanId)
            scanDao.deleteStagedMedia(scanId)
        }
    }
}
