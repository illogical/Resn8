package com.app.resn8.data.repository

import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.repository.QueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeQueueRepository(
    initialQueue: SavedQueue? = null
) : QueueRepository {

    private val _activeQueue = MutableStateFlow(initialQueue)

    override fun getActiveQueueFlow(): Flow<SavedQueue?> = _activeQueue

    override suspend fun saveQueue(queue: SavedQueue) {
        _activeQueue.value = queue
    }

    override suspend fun updatePlaybackPosition(
        queueId: String,
        currentIndex: Int,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        val current = _activeQueue.value
        if (current?.id == queueId) {
            _activeQueue.value = current.copy(
                currentIndex = currentIndex,
                positionMs = positionMs,
                isPlaying = isPlaying,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
