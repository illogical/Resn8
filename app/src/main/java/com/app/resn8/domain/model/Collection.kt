package com.app.resn8.domain.model

enum class CollectionProfile {
    MUSIC,
    CONTEXTUAL,
    FLAT
}

data class Collection(
    val id: String,
    val name: String,
    val profile: CollectionProfile = CollectionProfile.MUSIC,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class RootSource(
    val id: String,
    val collectionId: String,
    val treeUri: String,
    val displayName: String,
    val isAvailable: Boolean = true,
    val lastScanStatus: String? = null,
    val lastScannedAt: Long? = null,
    val lastScanStartedAt: Long? = null,
    val lastScanCompletedAt: Long? = null,
    val lastScanSummary: ScanResult? = null
)

data class FolderNode(
    val id: String,
    val sourceId: String,
    val parentId: String? = null,
    val relativePath: String,
    val displayName: String
)
