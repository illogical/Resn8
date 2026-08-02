package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.QueueFilterSnapshot
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueItem
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.SmartQueueMode

@Entity(
    tableName = "saved_queues",
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
        Index(value = ["updatedAt"])
    ]
)
data class SavedQueueEntity(
    @PrimaryKey val id: String,
    val collectionId: String,
    val kind: SavedQueueKind,
    val mode: SmartQueueMode?,
    val filterSnapshot: QueueFilterSnapshot?,
    val seed: Long?,
    val currentIndex: Int,
    val currentMediaId: String?,
    val currentOccurrenceId: String?,
    val positionMs: Long,
    val playWhenReadyIntent: Boolean,
    val playbackSpeed: Float,
    val repeatMode: RepeatMode,
    val createdAt: Long,
    val updatedAt: Long
)

fun SavedQueueEntity.toDomain(items: List<SavedQueueItemEntity> = emptyList()) = SavedQueue(
    id = id,
    collectionId = collectionId,
    kind = kind,
    mode = mode,
    filterSnapshot = filterSnapshot,
    seed = seed,
    orderedMediaIds = items.sortedBy { it.itemIndex }.map { it.mediaId },
    items = items.sortedBy { it.itemIndex }.map { SavedQueueItem(it.queueItemId, it.mediaId) },
    currentIndex = currentIndex,
    currentMediaId = currentMediaId,
    currentOccurrenceId = currentOccurrenceId,
    positionMs = positionMs,
    isPlaying = false,
    playWhenReadyIntent = playWhenReadyIntent,
    playbackSpeed = playbackSpeed,
    repeatMode = repeatMode,
    createdAt = createdAt,
    updatedAt = updatedAt
)
