package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.StagedFolder

@Entity(
    tableName = "staged_folders",
    foreignKeys = [
        ForeignKey(
            entity = ScanRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scanId", "relativePath"], unique = true)
    ]
)
data class StagedFolderEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val relativePath: String,
    val parentRelativePath: String?,
    val displayName: String,
    val resolvedFolderId: String?
)

fun StagedFolderEntity.toDomain() = StagedFolder(
    id = id,
    scanId = scanId,
    relativePath = relativePath,
    parentRelativePath = parentRelativePath,
    displayName = displayName,
    resolvedFolderId = resolvedFolderId
)

fun StagedFolder.toEntity() = StagedFolderEntity(
    id = id,
    scanId = scanId,
    relativePath = relativePath,
    parentRelativePath = parentRelativePath,
    displayName = displayName,
    resolvedFolderId = resolvedFolderId
)
