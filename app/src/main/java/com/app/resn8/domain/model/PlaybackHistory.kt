package com.app.resn8.domain.model

data class PlaybackHistory(
    val id: String,
    val mediaId: String,
    val sessionOccurrenceId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val accumulatedListenedDurationMs: Long = 0L,
    val result: PlaybackHistoryResult = PlaybackHistoryResult.IN_PROGRESS,
    val countedAt: Long? = null
)
