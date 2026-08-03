package com.app.resn8.storage.indexer

import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MetadataValueSource
import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.StagedFolder
import com.app.resn8.domain.model.StagedMedia
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.storage.artwork.ArtworkCandidateIndex
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID

class ScanOrchestrator(
    context: Context,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    batchSize: Int = 100
) {
    private val scanner = DocumentTreeScanner(context.applicationContext, batchSize)
    private val metadataExtractor = AudioMetadataExtractor(context.applicationContext)
    private val artworkCandidateIndex = ArtworkCandidateIndex(context.applicationContext)
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: Flow<ScanProgress?> = _scanProgress.asStateFlow()

    suspend fun executeScan(sourceId: String, treeUri: Uri): ScanResult = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val monotonicStart = SystemClock.elapsedRealtime()
        var phase = "INIT"
        val scanId = mediaRepository.startScanRun(sourceId)
        collectionRepository.updateRootScanState(sourceId, "IN_PROGRESS", startedAt, null, null)
        Log.i(LOG_TAG, "scan_started scanId=$scanId sourceId=$sourceId")

        var tagDerivedCount = 0
        var pathDerivedCount = 0
        var unrecognizedCount = 0
        var metadataFailureCount = 0
        var traversal = ScanTraversalProgress(0, 0, 0, 0, 0, 0, "")
        var lastLoggedDocuments = 0
        var lastLoggedAt = monotonicStart

        try {
            phase = "TRAVERSAL"
            scanner.scanTree(
                treeUri = treeUri,
                onFolderBatch = { folderBatch ->
                    mediaRepository.stageFolders(
                        scanId,
                        folderBatch.map { folder ->
                            StagedFolder(
                                id = UUID.randomUUID().toString(),
                                scanId = scanId,
                                relativePath = folder.relativePath,
                                parentRelativePath = folder.parentRelativePath,
                                displayName = folder.displayName
                            )
                        }
                    )
                },
                onMediaBatch = { mediaBatch ->
                    val stagedMedia = mediaBatch.map { file ->
                        val extraction = metadataExtractor.extract(file.documentUri)
                        if (!extraction.succeeded) metadataFailureCount++
                        val normalized = FallbackParser.normalize(file.relativePath, file.filename, extraction.tags)
                        if (normalized.titleSource == MetadataValueSource.TAG) tagDerivedCount++
                        if (
                            normalized.artistSource == MetadataValueSource.PATH ||
                            normalized.albumSource == MetadataValueSource.PATH ||
                            normalized.titleSource == MetadataValueSource.FILENAME
                        ) pathDerivedCount++
                        if (!normalized.isPatternRecognized) unrecognizedCount++
                        StagedMedia(
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
                            metadataScanStatus = if (extraction.succeeded) MetadataScanStatus.SUCCESS else MetadataScanStatus.FAILED,
                            title = normalized.title,
                            artist = normalized.artist,
                            albumArtist = normalized.albumArtist,
                            album = normalized.album,
                            discNumber = normalized.discNumber,
                            trackNumber = normalized.trackNumber,
                            year = normalized.year,
                            genre = normalized.genre,
                            artworkUri = null,
                            titleSource = normalized.titleSource,
                            artistSource = normalized.artistSource,
                            albumArtistSource = normalized.albumArtistSource,
                            albumSource = normalized.albumSource,
                            discNumberSource = normalized.discNumberSource,
                            trackNumberSource = normalized.trackNumberSource
                        )
                    }
                    mediaRepository.stageMedia(scanId, stagedMedia)
                },
                onArtworkCandidate = { artworkCandidateIndex.record(it) },
                onProgressUpdate = { current ->
                    traversal = current
                    _scanProgress.value = current.toDomain(scanId, startedAt, metadataFailureCount)
                    val now = SystemClock.elapsedRealtime()
                    if (current.inspectedDocuments - lastLoggedDocuments >= LOG_DOCUMENT_INTERVAL || now - lastLoggedAt >= LOG_TIME_INTERVAL_MS) {
                        Log.i(
                            LOG_TAG,
                            "scan_progress scanId=$scanId sourceId=$sourceId folders=${current.scannedFolders} " +
                                "documents=${current.inspectedDocuments} audio=${current.admittedAudio} " +
                                "unsupported=${current.unsupportedDocuments} errors=${current.unreadableBranches + metadataFailureCount} " +
                                "elapsedMs=${now - monotonicStart}"
                        )
                        lastLoggedDocuments = current.inspectedDocuments
                        lastLoggedAt = now
                    }
                }
            )

            val durationMs = SystemClock.elapsedRealtime() - monotonicStart
            val preliminary = ScanResult(
                scannedCount = traversal.admittedAudio,
                addedCount = 0,
                updatedCount = 0,
                unavailableCount = 0,
                tagDerivedCount = tagDerivedCount,
                pathDerivedCount = pathDerivedCount,
                unrecognizedCount = unrecognizedCount,
                unreadableCount = traversal.unreadableBranches,
                durationMs = durationMs,
                scannedFolderCount = traversal.scannedFolders,
                inspectedDocumentCount = traversal.inspectedDocuments,
                unsupportedCount = traversal.unsupportedDocuments,
                metadataFailureCount = metadataFailureCount,
                artworkCandidateCount = traversal.artworkCandidates,
                unsupportedAudioLikeCount = traversal.unsupportedAudioLike,
                ignoredNonAudioCount = traversal.ignoredNonAudio,
                zeroByteCount = traversal.rejectionCounts[AudioAdmissionPolicy.RejectionReason.ZERO_BYTE] ?: 0,
                appleDoubleCount = traversal.rejectionCounts[AudioAdmissionPolicy.RejectionReason.APPLEDOUBLE_SIDECAR] ?: 0,
                unsupportedMimeCount = traversal.rejectionCounts[AudioAdmissionPolicy.RejectionReason.UNSUPPORTED_MIME] ?: 0,
                unsupportedExtensionCount = traversal.rejectionCounts[AudioAdmissionPolicy.RejectionReason.UNSUPPORTED_EXTENSION] ?: 0,
                malformedDocumentCount = traversal.rejectionCounts[AudioAdmissionPolicy.RejectionReason.MALFORMED_DOCUMENT] ?: 0
            )
            phase = "PUBLICATION"
            val result = mediaRepository.publishStagedScan(scanId, sourceId, preliminary)
            runCatching { artworkCandidateIndex.publish(sourceId) }
                .onFailure { Log.w(LOG_TAG, "artwork_candidate_index_failed scanId=$scanId sourceId=$sourceId") }
            _scanProgress.value = traversal.toDomain(scanId, startedAt, metadataFailureCount).copy(
                phase = "COMPLETE",
                currentStep = "Scan complete"
            )
            Log.i(
                LOG_TAG,
                "scan_completed scanId=$scanId sourceId=$sourceId audio=${result.scannedCount} " +
                    "added=${result.addedCount} updated=${result.updatedCount} unavailable=${result.unavailableCount} " +
                    "durationMs=${result.durationMs}"
            )
            result
        } catch (cancelled: CancellationException) {
            withContext(NonCancellable) {
                mediaRepository.cancelScanRun(scanId)
                collectionRepository.updateRootScanState(sourceId, "CANCELLED", startedAt, System.currentTimeMillis(), null)
            }
            Log.i(LOG_TAG, "scan_cancelled scanId=$scanId sourceId=$sourceId elapsedMs=${SystemClock.elapsedRealtime() - monotonicStart}")
            throw cancelled
        } catch (error: Exception) {
            phase = "CLEANUP"
            val category = error::class.simpleName ?: "ScanFailure"
            mediaRepository.failScanRun(scanId, category)
            if (error is SecurityException) collectionRepository.updateRootSourceAvailability(sourceId, false)
            collectionRepository.updateRootScanState(sourceId, "FAILED", startedAt, System.currentTimeMillis(), null)
            Log.e(LOG_TAG, "scan_failed scanId=$scanId sourceId=$sourceId phase=$phase category=$category elapsedMs=${SystemClock.elapsedRealtime() - monotonicStart}")
            throw error
        }
    }

    private fun ScanTraversalProgress.toDomain(
        scanId: String,
        startedAt: Long,
        metadataFailureCount: Int
    ) = ScanProgress(
        processedFiles = admittedAudio,
        totalFiles = 0,
        currentStep = if (currentRelativePath.isBlank()) "Scanning selected folder" else "Scanning: $currentRelativePath",
        scanId = scanId,
        phase = "SCANNING",
        startedAt = startedAt,
        scannedFolders = scannedFolders,
        inspectedDocuments = inspectedDocuments,
        admittedAudio = admittedAudio,
        unsupportedCount = unsupportedDocuments,
        unreadableCount = unreadableBranches,
        metadataFailureCount = metadataFailureCount,
        artworkCandidateCount = artworkCandidates,
        unsupportedAudioLikeCount = unsupportedAudioLike,
        ignoredNonAudioCount = ignoredNonAudio
    )

    companion object {
        private const val LOG_TAG = "Resn8Indexer"
        private const val LOG_DOCUMENT_INTERVAL = 1_000
        private const val LOG_TIME_INTERVAL_MS = 60_000L
    }
}
