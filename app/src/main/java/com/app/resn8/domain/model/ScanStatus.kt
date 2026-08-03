package com.app.resn8.domain.model

import kotlinx.serialization.Serializable

data class ScanProgress(
    val processedFiles: Int,
    val totalFiles: Int,
    val currentStep: String,
    val isCancelled: Boolean = false,
    val scanId: String? = null,
    val phase: String = "SCANNING",
    val startedAt: Long = 0L,
    val scannedFolders: Int = 0,
    val inspectedDocuments: Int = 0,
    val admittedAudio: Int = processedFiles,
    val unsupportedCount: Int = 0,
    val unreadableCount: Int = 0,
    val metadataFailureCount: Int = 0,
    val artworkCandidateCount: Int = 0,
    val unsupportedAudioLikeCount: Int = 0,
    val ignoredNonAudioCount: Int = 0
)

@Serializable
data class ScanResult(
    val scannedCount: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val unavailableCount: Int,
    val tagDerivedCount: Int,
    val pathDerivedCount: Int,
    val unrecognizedCount: Int,
    val unreadableCount: Int,
    val durationMs: Long,
    val schemaVersion: Int = 3,
    val scannedFolderCount: Int = 0,
    val inspectedDocumentCount: Int = scannedCount,
    val unsupportedCount: Int = 0,
    val metadataFailureCount: Int = 0,
    val artworkCandidateCount: Int = 0,
    val unsupportedAudioLikeCount: Int = 0,
    val ignoredNonAudioCount: Int = 0,
    val zeroByteCount: Int = 0,
    val appleDoubleCount: Int = 0,
    val unsupportedMimeCount: Int = 0,
    val unsupportedExtensionCount: Int = 0,
    val malformedDocumentCount: Int = 0
)
