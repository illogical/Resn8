package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.ScanResult

@Entity(
    tableName = "root_sources",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["collectionId"]),
        Index(value = ["treeUri"], unique = true)
    ]
)
data class RootSourceEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val treeUri: String,
    val displayName: String,
    val isAvailable: Boolean,
    val lastScanStatus: String?,
    val lastScannedAt: Long?,
    val lastScanStartedAt: Long?,
    val lastScanCompletedAt: Long?,
    val lastScanSummary: ScanResult?
)

fun RootSourceEntity.toDomain() = RootSource(
    id = id,
    collectionId = collectionId,
    treeUri = treeUri,
    displayName = displayName,
    isAvailable = isAvailable,
    lastScanStatus = lastScanStatus,
    lastScannedAt = lastScannedAt,
    lastScanStartedAt = lastScanStartedAt,
    lastScanCompletedAt = lastScanCompletedAt,
    lastScanSummary = lastScanSummary
)

fun RootSource.toEntity() = RootSourceEntity(
    id = id,
    collectionId = collectionId,
    treeUri = treeUri,
    displayName = displayName,
    isAvailable = isAvailable,
    lastScanStatus = lastScanStatus,
    lastScannedAt = lastScannedAt,
    lastScanStartedAt = lastScanStartedAt,
    lastScanCompletedAt = lastScanCompletedAt,
    lastScanSummary = lastScanSummary
)
