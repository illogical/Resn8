package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.resn8.data.database.entity.FolderNodeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {
    @Query("SELECT * FROM folder_nodes WHERE sourceId = :sourceId ORDER BY relativePath ASC")
    fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNodeEntity>>

    @Query("SELECT * FROM folder_nodes WHERE id = :id LIMIT 1")
    fun getFolderNodeById(id: String): FolderNodeEntity?

    @Query("SELECT * FROM folder_nodes WHERE sourceId = :sourceId AND relativePath = :relativePath LIMIT 1")
    fun getFolderNodeByPath(sourceId: String, relativePath: String): FolderNodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertFolderNode(folder: FolderNodeEntity)

    @Update
    fun updateFolderNode(folder: FolderNodeEntity)
}
