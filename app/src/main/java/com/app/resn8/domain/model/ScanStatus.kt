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
    val artworkCandidateCount: Int = 0
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
    val schemaVersion: Int = 2,
    val scannedFolderCount: Int = 0,
    val inspectedDocumentCount: Int = scannedCount,
    val unsupportedCount: Int = 0,
    val metadataFailureCount: Int = 0,
    val artworkCandidateCount: Int = 0
)
