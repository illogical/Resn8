package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import com.app.resn8.domain.model.PlaylistItem

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "mediaId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["playlistId", "position"], unique = true)
    ]
)
data class PlaylistItemEntity(
    val playlistId: String,
    val mediaId: String,
    val position: Long,
    val addedAt: Long
)

fun PlaylistItemEntity.toDomain() = PlaylistItem(
    playlistId = playlistId,
    mediaId = mediaId,
    position = position,
    addedAt = addedAt
)

fun PlaylistItem.toEntity() = PlaylistItemEntity(
    playlistId = playlistId,
    mediaId = mediaId,
    position = position,
    addedAt = addedAt
)
