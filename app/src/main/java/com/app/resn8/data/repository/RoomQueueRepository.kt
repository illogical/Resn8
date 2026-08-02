package com.app.resn8.data.repository

import androidx.room.withTransaction
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.SavedQueueEntity
import com.app.resn8.data.database.entity.SavedQueueItemEntity
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueItem
import com.app.resn8.domain.repository.QueueRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.util.UUID

class RoomQueueRepository(
    private val db: Resn8Database
) : QueueRepository {
    private val savedQueueDao = db.savedQueueDao()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getActiveQueueFlow(): Flow<SavedQueue?> {
        return savedQueueDao.getActiveQueueFlow().flatMapLatest { queueEntity ->
            if (queueEntity == null) {
                flowOf(null)
            } else {
                savedQueueDao.getSavedQueueItemsFlow(queueEntity.id).map { itemEntities ->
                    queueEntity.toDomain(itemEntities)
                }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun getQueueByIdFlow(queueId: String): Flow<SavedQueue?> {
        return savedQueueDao.getSavedQueueByIdFlow(queueId).flatMapLatest { queueEntity ->
            if (queueEntity == null) {
                flowOf(null)
            } else {
                savedQueueDao.getSavedQueueItemsFlow(queueEntity.id).map { itemEntities ->
                    queueEntity.toDomain(itemEntities)
                }
            }
        }
    }

    override suspend fun saveQueue(queue: SavedQueue) {
        replaceQueueSnapshot(queue, queue.orderedMediaIds)
    }

    override suspend fun replaceQueueSnapshot(queue: SavedQueue, orderedMediaIds: List<String>): SavedQueue {
        return db.withTransaction {
            val now = System.currentTimeMillis()
            val queueEntity = SavedQueueEntity(
                id = queue.id,
                collectionId = queue.collectionId,
                kind = queue.kind,
                mode = queue.mode,
                filterSnapshot = queue.filterSnapshot,
                seed = queue.seed,
                currentIndex = queue.currentIndex,
                currentMediaId = queue.currentMediaId ?: orderedMediaIds.getOrNull(queue.currentIndex),
                currentOccurrenceId = queue.currentOccurrenceId,
                positionMs = queue.positionMs,
                playWhenReadyIntent = queue.playWhenReadyIntent,
                playbackSpeed = queue.playbackSpeed,
                repeatMode = queue.repeatMode,
                createdAt = queue.createdAt,
                updatedAt = now
            )

            savedQueueDao.upsertSavedQueue(queueEntity)
            savedQueueDao.deleteSavedQueueItems(queue.id)

            val itemEntities = orderedMediaIds.mapIndexed { index, mediaId ->
                SavedQueueItemEntity(
                    queueId = queue.id,
                    itemIndex = index,
                    queueItemId = UUID.randomUUID().toString(),
                    mediaId = mediaId
                )
            }
            if (itemEntities.isNotEmpty()) {
                savedQueueDao.insertSavedQueueItems(itemEntities)
            }

            queueEntity.toDomain(itemEntities)
        }
    }

    override suspend fun updatePlaybackPosition(
        queueId: String,
        currentIndex: Int,
        positionMs: Long,
        isPlaying: Boolean
    ) {
        savedQueueDao.updatePosition(queueId, currentIndex, positionMs, System.currentTimeMillis())
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

        db.withTransaction {
            val savedQueue = savedQueueDao.getSavedQueueById(queueId) ?: return@withTransaction
            val items = savedQueueDao.getSavedQueueItems(queueId)

            if (items.isNotEmpty() && currentIndex < items.size) {
                val expectedMediaId = items[currentIndex].mediaId
                if (currentMediaId != null && currentMediaId != expectedMediaId) {
                    throw IllegalArgumentException(
                        "Mismatched currentMediaId '$currentMediaId' for item at index $currentIndex (expected '$expectedMediaId')"
                    )
                }
            }

            savedQueueDao.updateCheckpoint(
                queueId = queueId,
                currentIndex = currentIndex,
                currentMediaId = currentMediaId ?: items.getOrNull(currentIndex)?.mediaId,
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
