package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.resn8.data.database.entity.UiSessionStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UiSessionDao {
    @Query("SELECT * FROM ui_session_state WHERE id = 1 LIMIT 1")
    fun getUiSessionStateFlow(): Flow<UiSessionStateEntity?>

    @Query("SELECT * FROM ui_session_state WHERE id = 1 LIMIT 1")
    suspend fun getUiSessionState(): UiSessionStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUiSessionState(state: UiSessionStateEntity)
}
