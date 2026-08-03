package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.resn8.data.database.entity.FolderNodeEntity
import kotlinx.coroutines.flow.Flow

data class FolderChildRow(
    @Embedded val folder: FolderNodeEntity,
    val childFolderCount: Int,
    val directMediaCount: Int
)

data class FolderBreadcrumbRow(
    val id: String,
    val displayName: String
)

@Dao
interface FolderDao {
    @Query("SELECT * FROM folder_nodes WHERE sourceId = :sourceId AND (parentId IS NULL OR relativePath = '') LIMIT 1")
    fun getRootFolderNode(sourceId: String): Flow<FolderNodeEntity?>

    @Query(
        """
        SELECT 
          fn.*,
          (SELECT COUNT(*) FROM folder_nodes child WHERE child.parentId = fn.id) AS childFolderCount,
          (SELECT COUNT(*) FROM media_files mf WHERE mf.folderId = fn.id) AS directMediaCount
        FROM folder_nodes fn
        WHERE fn.parentId = :parentId
        ORDER BY LOWER(TRIM(fn.displayName)) ASC, fn.id ASC
        """
    )
    fun getDirectChildFolders(parentId: String): Flow<List<FolderChildRow>>

    @Query(
        """
        WITH RECURSIVE ancestors AS (
          SELECT id, parentId, displayName, 0 AS depth
          FROM folder_nodes
          WHERE id = :folderId
          UNION ALL
          SELECT fn.id, fn.parentId, fn.displayName, a.depth + 1
          FROM folder_nodes fn
          INNER JOIN ancestors a ON fn.id = a.parentId
        )
        SELECT id, displayName FROM ancestors ORDER BY depth DESC
        """
    )
    fun getFolderBreadcrumbs(folderId: String): Flow<List<FolderBreadcrumbRow>>

    @Query(
        """
        WITH RECURSIVE descendant_folders AS (
          SELECT id FROM folder_nodes WHERE id IN (:folderIds)
          UNION ALL
          SELECT fn.id FROM folder_nodes fn
          INNER JOIN descendant_folders df ON fn.parentId = df.id
        )
        SELECT DISTINCT mf.id FROM media_files mf
        WHERE (mf.id IN (:fileIds) OR mf.folderId IN descendant_folders)
          AND (
            :availabilityFilter = 'ALL' OR
            (:availabilityFilter = 'AVAILABLE_ONLY' AND mf.isAvailable = 1) OR
            (:availabilityFilter = 'UNAVAILABLE_ONLY' AND mf.isAvailable = 0)
          )
        ORDER BY mf.id ASC
        """
    )
    suspend fun resolveSelectionMediaIds(
        fileIds: List<String>,
        folderIds: List<String>,
        availabilityFilter: String
    ): List<String>

    @Query("SELECT COUNT(*) FROM media_files WHERE id IN (:mediaIds) AND isAvailable = 1")
    suspend fun countAvailableMediaIds(mediaIds: List<String>): Int

    @Query("SELECT * FROM folder_nodes WHERE sourceId = :sourceId ORDER BY relativePath ASC")
    fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNodeEntity>>

    @Query("SELECT * FROM folder_nodes WHERE id = :id LIMIT 1")
    suspend fun getFolderNodeById(id: String): FolderNodeEntity?

    @Query("SELECT * FROM folder_nodes WHERE sourceId = :sourceId AND relativePath = :relativePath LIMIT 1")
    suspend fun getFolderNodeByPath(sourceId: String, relativePath: String): FolderNodeEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolderNode(folder: FolderNodeEntity): Long

    @Update
    suspend fun updateFolderNode(folder: FolderNodeEntity)
}
