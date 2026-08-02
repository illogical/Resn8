package com.app.resn8.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.PlaybackHistoryEntity
import com.app.resn8.data.database.entity.ScanRunEntity
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.data.database.entity.toEntity
import com.app.resn8.domain.model.AlbumSummary
import com.app.resn8.domain.model.ArtistSummary
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.FolderBreadcrumb
import com.app.resn8.domain.model.FolderListItem
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.SelectionResolutionResult
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

    override fun getArtistSummariesPaged(query: LibraryQuery): Flow<PagingData<ArtistSummary>> {
        return Pager(
            config = PagingConfig(pageSize = 50, prefetchDistance = 20, enablePlaceholders = false)
        ) {
            mediaFileDao.getArtistSummariesPaged(
                collectionId = query.collectionId,
                availabilityFilter = query.filters.availability.name,
                excludeDisliked = if (query.filters.excludeDisliked) 1 else 0,
                searchPattern = query.escapedSearchPattern()
            )
        }.flow.map { pagingData ->
            pagingData.map { row ->
                ArtistSummary(
                    key = if (row.rawArtist == null) MetadataGroupKey.Unknown else MetadataGroupKey.Known(row.rawArtist),
                    displayName = row.rawArtist ?: "Unknown Artist",
                    totalTrackCount = row.totalTrackCount,
                    availableTrackCount = row.availableTrackCount,
                    albumCount = row.albumCount,
                    representativeArtworkUri = row.representativeArtworkUri
                )
            }
        }
    }

    override fun getAlbumSummariesPaged(query: LibraryQuery): Flow<PagingData<AlbumSummary>> {
        val (isArtistNull, artistIsUnknown, artistValue) = parseGroupKey(query.artist)
        return Pager(
            config = PagingConfig(pageSize = 50, prefetchDistance = 20, enablePlaceholders = false)
        ) {
            mediaFileDao.getAlbumSummariesPaged(
                collectionId = query.collectionId,
                isArtistFilterNull = isArtistNull,
                artistKeyIsUnknown = artistIsUnknown,
                artistKeyValue = artistValue,
                availabilityFilter = query.filters.availability.name,
                excludeDisliked = if (query.filters.excludeDisliked) 1 else 0,
                searchPattern = query.escapedSearchPattern()
            )
        }.flow.map { pagingData ->
            pagingData.map { row ->
                AlbumSummary(
                    albumKey = if (row.rawAlbum == null) MetadataGroupKey.Unknown else MetadataGroupKey.Known(row.rawAlbum),
                    albumDisplayName = row.rawAlbum ?: "Unknown Album",
                    effectiveAlbumArtistKey = if (row.effectiveAlbumArtist == null) MetadataGroupKey.Unknown else MetadataGroupKey.Known(row.effectiveAlbumArtist),
                    effectiveAlbumArtistDisplayName = row.effectiveAlbumArtist ?: "Unknown Artist",
                    totalTrackCount = row.totalTrackCount,
                    availableTrackCount = row.availableTrackCount,
                    minYear = row.minYear,
                    representativeMediaId = row.representativeMediaId,
                    representativeArtworkUri = row.representativeArtworkUri
                )
            }
        }
    }

    override fun getTracksPaged(query: LibraryQuery): Flow<PagingData<MediaFile>> {
        val (isArtistNull, artistIsUnknown, artistValue) = parseGroupKey(query.artist)
        val (isAlbumNull, albumIsUnknown, albumValue) = parseGroupKey(query.album)
        return Pager(
            config = PagingConfig(pageSize = 50, prefetchDistance = 20, enablePlaceholders = false)
        ) {
            mediaFileDao.getTracksPaged(
                collectionId = query.collectionId,
                sourceId = query.sourceId,
                folderId = query.folderId,
                isArtistFilterNull = isArtistNull,
                artistKeyIsUnknown = artistIsUnknown,
                artistKeyValue = artistValue,
                isAlbumFilterNull = isAlbumNull,
                albumKeyIsUnknown = albumIsUnknown,
                albumKeyValue = albumValue,
                availabilityFilter = query.filters.availability.name,
                excludeDisliked = if (query.filters.excludeDisliked) 1 else 0,
                searchPattern = query.escapedSearchPattern(),
                sortOrder = query.sort.name
            )
        }.flow.map { pagingData ->
            pagingData.map { it.toDomain() }
        }
    }

    override fun getRootFolderNode(sourceId: String): Flow<FolderNode?> {
        return folderDao.getRootFolderNode(sourceId).map { it?.toDomain() }
    }

    override fun getDirectChildFolders(parentId: String): Flow<List<FolderListItem>> {
        return folderDao.getDirectChildFolders(parentId).map { rows ->
            rows.map { row ->
                FolderListItem(
                    folder = row.folder.toDomain(),
                    childFolderCount = row.childFolderCount,
                    directMediaCount = row.directMediaCount
                )
            }
        }
    }

    override fun getFolderBreadcrumbs(folderId: String): Flow<List<FolderBreadcrumb>> {
        return folderDao.getFolderBreadcrumbs(folderId).map { rows ->
            rows.map { FolderBreadcrumb(id = it.id, displayName = it.displayName) }
        }
    }

    override fun getPagedFolderMedia(folderId: String, query: LibraryQuery): Flow<PagingData<MediaFile>> {
        return getTracksPaged(query.copy(folderId = folderId))
    }

    override suspend fun resolveSelectionMediaIds(
        selectedFileIds: Set<String>,
        selectedFolderIds: Set<String>,
        availability: AvailabilityFilter
    ): SelectionResolutionResult {
        if (selectedFileIds.isEmpty() && selectedFolderIds.isEmpty()) {
            return SelectionResolutionResult(emptyList(), 0, 0)
        }
        val uniqueIds = folderDao.resolveSelectionMediaIds(
            fileIds = selectedFileIds.toList(),
            folderIds = selectedFolderIds.toList(),
            availabilityFilter = availability.name
        )
        val total = uniqueIds.size
        val available = if (uniqueIds.isEmpty()) 0 else folderDao.countAvailableMediaIds(uniqueIds)
        return SelectionResolutionResult(
            uniqueMediaIds = uniqueIds,
            totalCount = total,
            availableCount = available
        )
    }

    override suspend fun snapshotVisibleMediaIds(query: LibraryQuery): List<String> {
        val (isArtistNull, artistIsUnknown, artistValue) = parseGroupKey(query.artist)
        val (isAlbumNull, albumIsUnknown, albumValue) = parseGroupKey(query.album)
        return mediaFileDao.snapshotVisibleMediaIds(
            collectionId = query.collectionId,
            sourceId = query.sourceId,
            folderId = query.folderId,
            isArtistFilterNull = isArtistNull,
            artistKeyIsUnknown = artistIsUnknown,
            artistKeyValue = artistValue,
            isAlbumFilterNull = isAlbumNull,
            albumKeyIsUnknown = albumIsUnknown,
            albumKeyValue = albumValue,
            availabilityFilter = query.filters.availability.name,
            excludeDisliked = if (query.filters.excludeDisliked) 1 else 0,
            searchPattern = query.escapedSearchPattern(),
            sortOrder = query.sort.name
        )
    }

    private fun parseGroupKey(key: MetadataGroupKey?): Triple<Int, Int, String?> {
        return when (key) {
            null -> Triple(1, 0, null)
            is MetadataGroupKey.Unknown -> Triple(0, 1, null)
            is MetadataGroupKey.Known -> Triple(0, 0, key.value)
        }
    }

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
