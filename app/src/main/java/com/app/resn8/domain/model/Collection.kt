package com.app.resn8.domain.model

import java.util.Locale

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
    val updatedAt: Long = System.currentTimeMillis(),
    val normalizedName: String = normalizeCollectionName(name)
)

fun normalizeCollectionName(name: String): String = name.trim().lowercase(Locale.ROOT)

class CollectionNameConflictException(name: String) :
    IllegalArgumentException("A collection named '$name' already exists")

class CollectionSourceConflictException(message: String) : IllegalArgumentException(message)

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
