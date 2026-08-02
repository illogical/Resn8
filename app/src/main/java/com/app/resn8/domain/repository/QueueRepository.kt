package com.app.resn8.domain.repository

import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueue
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    fun getActiveQueueFlow(): Flow<SavedQueue?>
    suspend fun saveQueue(queue: SavedQueue)
    suspend fun replaceQueueSnapshot(queue: SavedQueue, orderedMediaIds: List<String>): SavedQueue
    suspend fun updatePlaybackPosition(queueId: String, currentIndex: Int, positionMs: Long, isPlaying: Boolean)
    suspend fun updatePlaybackCheckpoint(
        queueId: String,
        currentIndex: Int,
        currentMediaId: String?,
        currentOccurrenceId: String?,
        positionMs: Long,
        playWhenReadyIntent: Boolean,
        playbackSpeed: Float,
        repeatMode: RepeatMode
    )
}
