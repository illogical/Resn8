package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.PlaybackHistory
import com.app.resn8.domain.model.PlaybackHistoryResult

@Entity(
    tableName = "playback_history",
    foreignKeys = [
        ForeignKey(
            entity = MediaFileEntity::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["startedAt"]),
        Index(value = ["sessionOccurrenceId"], unique = true)
    ]
)
data class PlaybackHistoryEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val sessionOccurrenceId: String,
    val startedAt: Long,
    val endedAt: Long?,
    val accumulatedListenedDurationMs: Long,
    val result: PlaybackHistoryResult,
    val countedAt: Long?
)

fun PlaybackHistoryEntity.toDomain() = PlaybackHistory(
    id = id,
    mediaId = mediaId,
    sessionOccurrenceId = sessionOccurrenceId,
    startedAt = startedAt,
    endedAt = endedAt,
    accumulatedListenedDurationMs = accumulatedListenedDurationMs,
    result = result,
    countedAt = countedAt
)

fun PlaybackHistory.toEntity() = PlaybackHistoryEntity(
    id = id,
    mediaId = mediaId,
    sessionOccurrenceId = sessionOccurrenceId,
    startedAt = startedAt,
    endedAt = endedAt,
    accumulatedListenedDurationMs = accumulatedListenedDurationMs,
    result = result,
    countedAt = countedAt
)
