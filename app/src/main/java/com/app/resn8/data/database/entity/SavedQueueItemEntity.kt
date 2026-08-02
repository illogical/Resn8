package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "saved_queue_items",
    primaryKeys = ["queueId", "itemIndex"],
    foreignKeys = [
        ForeignKey(
            entity = SavedQueueEntity::class,
            parentColumns = ["id"],
            childColumns = ["queueId"],
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
        Index(value = ["queueItemId"], unique = true)
    ]
)
data class SavedQueueItemEntity(
    val queueId: String,
    val itemIndex: Int,
    val queueItemId: String,
    val mediaId: String
)
