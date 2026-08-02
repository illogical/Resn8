package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.FolderNode

@Entity(
    tableName = "folder_nodes",
    foreignKeys = [
        ForeignKey(
            entity = RootSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = FolderNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sourceId"]),
        Index(value = ["parentId"]),
        Index(value = ["sourceId", "relativePath"], unique = true)
    ]
)
data class FolderNodeEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val parentId: String?,
    val relativePath: String,
    val displayName: String
)

fun FolderNodeEntity.toDomain() = FolderNode(
    id = id,
    sourceId = sourceId,
    parentId = parentId,
    relativePath = relativePath,
    displayName = displayName
)

fun FolderNode.toEntity() = FolderNodeEntity(
    id = id,
    sourceId = sourceId,
    parentId = parentId,
    relativePath = relativePath,
    displayName = displayName
)
