package com.app.resn8.domain.model

data class ScanProgress(
    val processedFiles: Int,
    val totalFiles: Int,
    val currentStep: String,
    val isCancelled: Boolean = false
)

data class ScanResult(
    val scannedCount: Int,
    val addedCount: Int,
    val updatedCount: Int,
    val unavailableCount: Int,
    val tagDerivedCount: Int,
    val pathDerivedCount: Int,
    val unrecognizedCount: Int,
    val unreadableCount: Int,
    val durationMs: Long
)
