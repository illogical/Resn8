package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.resn8.data.database.entity.PlaybackHistoryEntity

@Dao
interface PlaybackHistoryDao {
    @Query("SELECT * FROM playback_history WHERE sessionOccurrenceId = :sessionOccurrenceId LIMIT 1")
    suspend fun getHistoryByOccurrenceId(sessionOccurrenceId: String): PlaybackHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(history: PlaybackHistoryEntity): Long

    @Update
    suspend fun updateHistory(history: PlaybackHistoryEntity)

    @Query("SELECT * FROM playback_history WHERE mediaId = :mediaId ORDER BY startedAt DESC")
    suspend fun getHistoryForMedia(mediaId: String): List<PlaybackHistoryEntity>
}
