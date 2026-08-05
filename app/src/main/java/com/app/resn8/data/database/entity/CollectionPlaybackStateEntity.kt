package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.CollectionPlaybackState

@Entity(
    tableName = "collection_playback_state",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["collectionId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = SavedQueueEntity::class,
            parentColumns = ["id"],
            childColumns = ["activeQueueId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index(value = ["activeQueueId"])]
)
data class CollectionPlaybackStateEntity(
    @PrimaryKey val collectionId: String,
    val activeQueueId: String?,
    val updatedAt: Long
)

fun CollectionPlaybackStateEntity.toDomain() = CollectionPlaybackState(
    collectionId = collectionId,
    activeQueueId = activeQueueId,
    updatedAt = updatedAt
)
