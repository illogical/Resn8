package com.app.resn8.domain.repository

import com.app.resn8.domain.model.SavedQueue
import kotlinx.coroutines.flow.Flow

interface QueueRepository {
    fun getActiveQueueFlow(): Flow<SavedQueue?>
    suspend fun saveQueue(queue: SavedQueue)
    suspend fun updatePlaybackPosition(queueId: String, currentIndex: Int, positionMs: Long, isPlaying: Boolean)
}
