package com.app.resn8.domain.model

data class PlaybackHistory(
    val id: String,
    val mediaId: String,
    val sessionOccurrenceId: String,
    val startedAt: Long,
    val endedAt: Long,
    val accumulatedListenedDurationMs: Long,
    val isMeaningfulPlay: Boolean
)
