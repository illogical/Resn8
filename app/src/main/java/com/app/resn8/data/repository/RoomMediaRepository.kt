package com.app.resn8.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.map
import androidx.room.withTransaction
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.Converters
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
import java.nio.charset.StandardCharsets

class RoomMediaRepository(
    private val db: Resn8Database
) : MediaRepository {
    private val mediaFileDao = db.mediaFileDao()
    private val folderDao = db.folderDao()
    private val scanDao = db.scanDao()
    private val playbackHistoryDao = db.playbackHistoryDao()
    private val collectionDao = db.collectionDao()

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

    override suspend fun getMediaFilesByIdsPreservingOrder(mediaIds: List<String>): List<MediaFile> {
        if (mediaIds.isEmpty()) return emptyList()
        val uniqueIds = mediaIds.distinct()
        val entityMap = mutableMapOf<String, MediaFile>()
        uniqueIds.chunked(500).forEach { chunk ->
            val entities = mediaFileDao.getMediaFilesByIds(chunk)
            entities.forEach { entity ->
                entityMap[entity.id] = entity.toDomain()
            }
        }
        return mediaIds.mapNotNull { id -> entityMap[id] }
    }

    override fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNode>> {
        return folderDao.getFolderNodesFlow(sourceId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun updateLikeScore(mediaId: String, delta: Int): Result<Int> {
        if (delta != 1 && delta != -1) {
            return Result.failure(IllegalArgumentException("Like score delta must be +1 or -1"))
        }
        return try {
            val newScore = db.withTransaction {
                val existing = mediaFileDao.getMediaFileById(mediaId)
                    ?: throw IllegalArgumentException("Media file not found: $mediaId")
                val updatedScore = existing.likeScore + delta
                mediaFileDao.updateLikeScore(mediaId, delta)
                updatedScore
            }
            Result.success(newScore)
        } catch (e: Exception) {
            Result.failure(e)
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
            scanDao.getActiveScanRun(sourceId)?.let { interrupted ->
                scanDao.updateScanRunStatus(
                    scanId = interrupted.id,
                    status = "INTERRUPTED",
                    completedAt = System.currentTimeMillis(),
                    errorSummary = "PROCESS_INTERRUPTED"
                )
                scanDao.deleteStagedFolders(interrupted.id)
                scanDao.deleteStagedMedia(interrupted.id)
            }
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

    override suspend fun publishStagedScan(
        scanId: String,
        sourceId: String,
        scanResult: ScanResult
    ): ScanResult = db.withTransaction {
        val pageSize = 250
        val rootPath = ""
        val existingRoot = folderDao.getFolderNodeByPath(sourceId, rootPath)
        val rootId = existingRoot?.id ?: stableFolderId(sourceId, rootPath)
        val rootEntity = FolderNode(
            id = rootId,
            sourceId = sourceId,
            parentId = null,
            relativePath = rootPath,
            displayName = "Root"
        ).toEntity()
        if (existingRoot == null) folderDao.insertFolderNode(rootEntity) else folderDao.updateFolderNode(rootEntity)

        var folderOffset = 0
        while (true) {
            val batch = scanDao.getStagedFolderBatch(scanId, pageSize, folderOffset)
            if (batch.isEmpty()) break
            batch.forEach { staged ->
                val existing = folderDao.getFolderNodeByPath(sourceId, staged.relativePath)
                val folderId = existing?.id ?: stableFolderId(sourceId, staged.relativePath)
                val parentPath = staged.parentRelativePath.orEmpty()
                val parentId = folderDao.getFolderNodeByPath(sourceId, parentPath)?.id
                    ?: stableFolderId(sourceId, parentPath)
                val entity = FolderNode(
                    id = folderId,
                    sourceId = sourceId,
                    parentId = parentId,
                    relativePath = staged.relativePath,
                    displayName = staged.displayName
                ).toEntity()
                if (existing == null) folderDao.insertFolderNode(entity) else folderDao.updateFolderNode(entity)
                scanDao.setResolvedFolder(staged.id, folderId)
            }
            folderOffset += batch.size
        }

        var addedCount = 0
        var updatedCount = 0
        val claimedIds = HashSet<String>()
        var mediaOffset = 0
        while (true) {
            val batch = scanDao.getStagedMediaBatch(scanId, pageSize, mediaOffset)
            if (batch.isEmpty()) break
            batch.forEach { staged ->
                val parentPath = staged.relativePath.substringBeforeLast('/', "")
                val folderId = folderDao.getFolderNodeByPath(sourceId, parentPath)?.id ?: rootId
                val directMatch = staged.documentId?.let { mediaFileDao.getMediaFileByDocumentId(sourceId, it) }
                    ?: mediaFileDao.getMediaFileByUri(sourceId, staged.documentUri)
                    ?: mediaFileDao.getMediaFileByPath(sourceId, staged.relativePath)
                val signatureMatch = if (
                    directMatch == null && staged.size > 0L && staged.modifiedTimeMs > 0L && (staged.durationMs ?: 0L) > 0L
                ) {
                    mediaFileDao.getMediaByCompleteSignature(
                        sourceId,
                        staged.size,
                        staged.modifiedTimeMs,
                        staged.durationMs!!
                    ).singleOrNull()
                } else null
                val existing = (directMatch ?: signatureMatch)?.takeUnless { it.id in claimedIds }
                val mediaId = existing?.id ?: UUID.randomUUID().toString()
                val firstIndexedAt = existing?.firstIndexedAt ?: System.currentTimeMillis()
                val resolved = MediaFile(
                    id = mediaId,
                    sourceId = sourceId,
                    folderId = folderId,
                    documentUri = staged.documentUri,
                    documentId = staged.documentId,
                    relativePath = staged.relativePath,
                    filename = staged.filename,
                    displayTitle = staged.displayTitle,
                    mimeType = staged.mimeType,
                    size = staged.size,
                    durationMs = staged.durationMs,
                    modifiedTimeMs = staged.modifiedTimeMs,
                    firstIndexedAt = firstIndexedAt,
                    isAvailable = true,
                    metadataScanStatus = staged.metadataScanStatus,
                    title = staged.title,
                    artist = staged.artist,
                    albumArtist = staged.albumArtist,
                    album = staged.album,
                    discNumber = staged.discNumber,
                    trackNumber = staged.trackNumber,
                    year = staged.year,
                    genre = staged.genre,
                    artworkUri = existing?.artworkUri,
                    titleSource = staged.titleSource,
                    artistSource = staged.artistSource,
                    albumArtistSource = staged.albumArtistSource,
                    albumSource = staged.albumSource,
                    discNumberSource = staged.discNumberSource,
                    trackNumberSource = staged.trackNumberSource,
                    playCount = existing?.playCount ?: 0,
                    lastPlayedAt = existing?.lastPlayedAt,
                    likeScore = existing?.likeScore ?: 0
                ).toEntity()
                if (existing == null) {
                    mediaFileDao.insertMediaFile(resolved)
                    addedCount++
                } else {
                    mediaFileDao.updateMediaFile(resolved)
                    updatedCount++
                }
                claimedIds.add(mediaId)
                scanDao.setResolvedMedia(staged.id, mediaId, folderId)
            }
            mediaOffset += batch.size
        }

        val unavailableCount = mediaFileDao.countMissingFromStagedScan(sourceId, scanId)
        mediaFileDao.markMissingFromStagedScanUnavailable(sourceId, scanId)
        val completedAt = System.currentTimeMillis()
        val finalResult = scanResult.copy(
            scannedCount = scanDao.countStagedMedia(scanId),
            addedCount = addedCount,
            updatedCount = updatedCount,
            unavailableCount = unavailableCount
        )
        scanDao.completeScanRun(
            scanId = scanId,
            completedAt = completedAt,
            scannedCount = finalResult.scannedCount,
            addedCount = finalResult.addedCount,
            updatedCount = finalResult.updatedCount,
            unavailableCount = finalResult.unavailableCount
        )
        collectionDao.updateRootSourceAvailability(sourceId, true)
        collectionDao.updateRootScanState(
            sourceId = sourceId,
            status = "SUCCESS",
            startedAt = null,
            completedAt = completedAt,
            summaryJson = Converters().fromScanResult(finalResult)
        )
        scanDao.deleteStagedFolders(scanId)
        scanDao.deleteStagedMedia(scanId)
        finalResult
    }

    private fun stableFolderId(sourceId: String, relativePath: String): String =
        UUID.nameUUIDFromBytes("$sourceId\u0000$relativePath".toByteArray(StandardCharsets.UTF_8)).toString()

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
