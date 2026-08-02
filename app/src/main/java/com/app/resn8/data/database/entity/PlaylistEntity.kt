package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.Playlist

@Entity(
    tableName = "playlists",
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
        Index(value = ["collectionId", "normalizedName"], unique = true)
    ]
)
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Long,
    val updatedAt: Long
)

fun PlaylistEntity.toDomain() = Playlist(
    id = id,
    collectionId = collectionId,
    name = name,
    createdAt = createdAt,
    updatedAt = updatedAt
)
