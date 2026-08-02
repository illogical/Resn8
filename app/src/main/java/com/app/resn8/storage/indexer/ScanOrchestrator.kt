package com.app.resn8.storage.indexer

import android.content.Context
import android.net.Uri
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.StagedFolder
import com.app.resn8.domain.model.StagedMedia
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class ScanOrchestrator(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val database: Resn8Database? = null
) {
    private val scanner = DocumentTreeScanner(context)
    private val metadataExtractor = AudioMetadataExtractor(context)

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: Flow<ScanProgress?> = _scanProgress.asStateFlow()

    suspend fun executeScan(
        sourceId: String,
        treeUri: Uri
    ): ScanResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        val scanId = mediaRepository.startScanRun(sourceId)
        collectionRepository.updateRootScanState(sourceId, "IN_PROGRESS", startTime, null, null)

        val stagedFoldersList = mutableListOf<StagedFolder>()
        val stagedMediaList = mutableListOf<StagedMedia>()

        var tagDerivedCount = 0
        var pathDerivedCount = 0
        var unrecognizedCount = 0
        var unreadableCount = 0

        try {
            scanner.scanTree(
                treeUri = treeUri,
                onFolderBatch = { folderBatch ->
                    val stagedFolders = folderBatch.map { folder ->
                        StagedFolder(
                            id = UUID.randomUUID().toString(),
                            scanId = scanId,
                            relativePath = folder.relativePath,
                            parentRelativePath = folder.parentRelativePath,
                            displayName = folder.displayName
                        )
                    }
                    stagedFoldersList.addAll(stagedFolders)
                    mediaRepository.stageFolders(scanId, stagedFolders)
                },
                onMediaBatch = { mediaBatch ->
                    val stagedMediaBatch = mutableListOf<StagedMedia>()
                    for (file in mediaBatch) {
                        var normalized: NormalizedMetadata
                        try {
                            val tags = metadataExtractor.extractTags(file.documentUri)
                            normalized = FallbackParser.normalize(file.relativePath, file.filename, tags)
                        } catch (e: Exception) {
                            unreadableCount++
                            normalized = FallbackParser.normalize(file.relativePath, file.filename, ExtractedTags())
                        }

                        if (normalized.titleSource == com.app.resn8.domain.model.MetadataValueSource.TAG) tagDerivedCount++
                        if (normalized.titleSource == com.app.resn8.domain.model.MetadataValueSource.PATH) pathDerivedCount++
                        if (!normalized.isPatternRecognized) unrecognizedCount++

                        val staged = StagedMedia(
                            id = UUID.randomUUID().toString(),
                            scanId = scanId,
                            documentUri = file.documentUri.toString(),
                            documentId = file.documentId,
                            relativePath = file.relativePath,
                            filename = file.filename,
                            displayTitle = normalized.displayTitle,
                            mimeType = file.mimeType,
                            size = file.size,
                            durationMs = normalized.durationMs,
                            modifiedTimeMs = file.modifiedTimeMs,
                            metadataScanStatus = MetadataScanStatus.SUCCESS,
                            title = normalized.title,
                            artist = normalized.artist,
                            albumArtist = normalized.albumArtist,
                            album = normalized.album,
                            discNumber = normalized.discNumber,
                            trackNumber = normalized.trackNumber,
                            year = normalized.year,
                            genre = normalized.genre,
                            artworkUri = normalized.artworkUri,
                            titleSource = normalized.titleSource,
                            artistSource = normalized.artistSource,
                            albumArtistSource = normalized.albumArtistSource,
                            albumSource = normalized.albumSource,
                            discNumberSource = normalized.discNumberSource,
                            trackNumberSource = normalized.trackNumberSource
                        )
                        stagedMediaBatch.add(staged)
                    }
                    stagedMediaList.addAll(stagedMediaBatch)
                    mediaRepository.stageMedia(scanId, stagedMediaBatch)
                },
                onProgressUpdate = { scannedFolders, discoveredMedia, currentPath ->
                    _scanProgress.value = ScanProgress(
                        processedFiles = discoveredMedia,
                        totalFiles = discoveredMedia,
                        currentStep = "Scanning: $currentPath"
                    )
                }
            )

            // Resolve folder hierarchy
            val folderMap = mutableMapOf<String, FolderNode>()
            val rootNode = FolderNode(
                id = UUID.randomUUID().toString(),
                sourceId = sourceId,
                parentId = null,
                relativePath = "",
                displayName = "Root"
            )
            folderMap[""] = rootNode

            stagedFoldersList.forEach { sf ->
                val parentId = sf.parentRelativePath?.let { folderMap[it]?.id } ?: rootNode.id
                val node = FolderNode(
                    id = UUID.randomUUID().toString(),
                    sourceId = sourceId,
                    parentId = parentId,
                    relativePath = sf.relativePath,
                    displayName = sf.displayName
                )
                folderMap[sf.relativePath] = node
            }

            // Retrieve existing canonical media for source to perform 3-tier matching
            val existingMedia: List<MediaFile> = database?.mediaFileDao()?.getMediaFilesBySourceId(sourceId)
                ?.map { entity -> entity.toDomain() } ?: emptyList()

            val uriMap = existingMedia.associateBy { m -> m.documentUri }
            val pathMap = existingMedia.associateBy { m -> m.relativePath }
            val signatureGroups = existingMedia.groupBy { m -> "${m.size}_${m.modifiedTimeMs}_${m.durationMs}" }
            val uniqueSignatureMap = signatureGroups.filterValues { list -> list.size == 1 }.mapValues { entry -> entry.value.first() }

            val matchedCanonicalIds = mutableSetOf<String>()
            val resolvedMediaList = mutableListOf<MediaFile>()
            var addedCount = 0
            var updatedCount = 0

            for (staged in stagedMediaList) {
                val parentPath = staged.relativePath.substringBeforeLast('/', "").ifEmpty { "" }
                val folderId = folderMap[parentPath]?.id ?: rootNode.id

                val tier1Match = uriMap[staged.documentUri] ?: staged.documentId?.let { docId ->
                    existingMedia.find { m -> m.documentId == docId }
                }
                val tier2Match = tier1Match ?: pathMap[staged.relativePath]
                val sigKey = "${staged.size}_${staged.modifiedTimeMs}_${staged.durationMs}"
                val tier3Match = tier2Match ?: uniqueSignatureMap[sigKey]

                val existing = tier3Match
                if (existing != null) {
                    updatedCount++
                    matchedCanonicalIds.add(existing.id)
                    val updated = existing.copy(
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
                        artworkUri = staged.artworkUri,
                        titleSource = staged.titleSource,
                        artistSource = staged.artistSource,
                        albumArtistSource = staged.albumArtistSource,
                        albumSource = staged.albumSource,
                        discNumberSource = staged.discNumberSource,
                        trackNumberSource = staged.trackNumberSource
                    )
                    resolvedMediaList.add(updated)
                } else {
                    addedCount++
                    val newMedia = MediaFile(
                        id = UUID.randomUUID().toString(),
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
                        firstIndexedAt = System.currentTimeMillis(),
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
                        artworkUri = staged.artworkUri,
                        titleSource = staged.titleSource,
                        artistSource = staged.artistSource,
                        albumArtistSource = staged.albumArtistSource,
                        albumSource = staged.albumSource,
                        discNumberSource = staged.discNumberSource,
                        trackNumberSource = staged.trackNumberSource,
                        playCount = 0,
                        lastPlayedAt = null,
                        likeScore = 0
                    )
                    resolvedMediaList.add(newMedia)
                }
            }

            val unavailableMediaIds = existingMedia.map { m -> m.id }.filter { id -> id !in matchedCanonicalIds }
            val endTime = System.currentTimeMillis()
            val scanResult = ScanResult(
                scannedCount = stagedMediaList.size,
                addedCount = addedCount,
                updatedCount = updatedCount,
                unavailableCount = unavailableMediaIds.size,
                tagDerivedCount = tagDerivedCount,
                pathDerivedCount = pathDerivedCount,
                unrecognizedCount = unrecognizedCount,
                unreadableCount = unreadableCount,
                durationMs = endTime - startTime
            )

            mediaRepository.publishResolvedScan(
                scanId = scanId,
                resolvedFolders = folderMap.values.toList(),
                resolvedMedia = resolvedMediaList,
                unavailableMediaIds = unavailableMediaIds,
                scanResult = scanResult
            )

            collectionRepository.updateRootScanState(
                sourceId = sourceId,
                status = "SUCCESS",
                startedAt = startTime,
                completedAt = endTime,
                summary = scanResult
            )

            _scanProgress.value = ScanProgress(
                processedFiles = stagedMediaList.size,
                totalFiles = stagedMediaList.size,
                currentStep = "Scan complete"
            )

            scanResult
        } catch (e: Exception) {
            mediaRepository.failScanRun(scanId, e.message ?: "Unknown scan error")
            collectionRepository.updateRootScanState(sourceId, "FAILED", startTime, System.currentTimeMillis(), null)
            throw e
        }
    }

    suspend fun cancelScan(scanId: String) {
        mediaRepository.cancelScanRun(scanId)
    }
}
