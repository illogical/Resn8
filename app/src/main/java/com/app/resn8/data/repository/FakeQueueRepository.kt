package com.app.resn8.data.repository

import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueItem
import com.app.resn8.domain.repository.QueueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.UUID

class FakeQueueRepository(
    initialQueue: SavedQueue? = null
) : QueueRepository {

    private val _activeQueue = MutableStateFlow(initialQueue)

    override fun getActiveQueueFlow(): Flow<SavedQueue?> = _activeQueue

    override suspend fun saveQueue(queue: SavedQueue) {
        replaceQueueSnapshot(queue, queue.orderedMediaIds)
    }

    override suspend fun replaceQueueSnapshot(queue: SavedQueue, orderedMediaIds: List<String>): SavedQueue {
        val items = orderedMediaIds.map { SavedQueueItem(UUID.randomUUID().toString(), it) }
        val updated = queue.copy(
            orderedMediaIds = orderedMediaIds,
            items = items,
            currentMediaId = queue.currentMediaId ?: orderedMediaIds.getOrNull(queue.currentIndex),
            updatedAt = System.currentTimeMillis()
        )
        _activeQueue.value = updated
        return updated
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

    override suspend fun updatePlaybackCheckpoint(
        queueId: String,
        currentIndex: Int,
        currentMediaId: String?,
        currentOccurrenceId: String?,
        positionMs: Long,
        playWhenReadyIntent: Boolean,
        playbackSpeed: Float,
        repeatMode: RepeatMode
    ) {
        require(currentIndex >= 0) { "currentIndex must be non-negative" }
        require(positionMs >= 0) { "positionMs must be non-negative" }
        val current = _activeQueue.value
        if (current?.id == queueId) {
            _activeQueue.value = current.copy(
                currentIndex = currentIndex,
                currentMediaId = currentMediaId ?: current.orderedMediaIds.getOrNull(currentIndex),
                currentOccurrenceId = currentOccurrenceId,
                positionMs = positionMs,
                playWhenReadyIntent = playWhenReadyIntent,
                playbackSpeed = playbackSpeed,
                repeatMode = repeatMode,
                updatedAt = System.currentTimeMillis()
            )
        }
    }
}
