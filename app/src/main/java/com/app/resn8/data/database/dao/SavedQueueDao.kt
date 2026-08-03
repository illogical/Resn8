package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.resn8.data.database.entity.SavedQueueEntity
import com.app.resn8.data.database.entity.SavedQueueItemEntity
import com.app.resn8.domain.model.RepeatMode
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedQueueDao {
    @Query("SELECT * FROM saved_queues ORDER BY updatedAt DESC LIMIT 1")
    fun getActiveQueueFlow(): Flow<SavedQueueEntity?>

    @Query("SELECT * FROM saved_queues WHERE id = :id LIMIT 1")
    suspend fun getSavedQueueById(id: String): SavedQueueEntity?

    @Query("SELECT * FROM saved_queues WHERE id = :id LIMIT 1")
    fun getSavedQueueByIdFlow(id: String): Flow<SavedQueueEntity?>

    @Query("SELECT * FROM saved_queue_items WHERE queueId = :queueId ORDER BY itemIndex ASC")
    fun getSavedQueueItemsFlow(queueId: String): Flow<List<SavedQueueItemEntity>>

    @Query("SELECT * FROM saved_queue_items WHERE queueId = :queueId ORDER BY itemIndex ASC")
    suspend fun getSavedQueueItems(queueId: String): List<SavedQueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSavedQueue(queue: SavedQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedQueueItems(items: List<SavedQueueItemEntity>)

    @Query("DELETE FROM saved_queue_items WHERE queueId = :queueId")
    suspend fun deleteSavedQueueItems(queueId: String)

    @Query("UPDATE saved_queues SET currentIndex = :currentIndex, positionMs = :positionMs, updatedAt = :updatedAt WHERE id = :queueId")
    suspend fun updatePosition(queueId: String, currentIndex: Int, positionMs: Long, updatedAt: Long)

    @Query(
        """
        UPDATE saved_queues 
        SET currentIndex = :currentIndex, 
            currentMediaId = :currentMediaId, 
            currentOccurrenceId = :currentOccurrenceId, 
            positionMs = :positionMs, 
            playWhenReadyIntent = :playWhenReadyIntent, 
            playbackSpeed = :playbackSpeed, 
            repeatMode = :repeatMode, 
            updatedAt = :updatedAt 
        WHERE id = :queueId
        """
    )
    suspend fun updateCheckpoint(
        queueId: String,
        currentIndex: Int,
        currentMediaId: String?,
        currentOccurrenceId: String?,
        positionMs: Long,
        playWhenReadyIntent: Boolean,
        playbackSpeed: Float,
        repeatMode: RepeatMode,
        updatedAt: Long
    )
}
