package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.CollectionPlaybackStateEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import androidx.room.Embedded
import kotlinx.coroutines.flow.Flow

data class CollectionSummaryRow(
    @Embedded val collection: CollectionEntity,
    val totalTrackCount: Int,
    val unavailableTrackCount: Int
)

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getCollectionsFlow(): Flow<List<CollectionEntity>>

    @Query(
        """
        SELECT c.*, COUNT(mf.id) AS totalTrackCount,
               SUM(CASE WHEN mf.id IS NOT NULL AND mf.isAvailable = 0 THEN 1 ELSE 0 END) AS unavailableTrackCount
        FROM collections c
        LEFT JOIN root_sources rs ON rs.collectionId = c.id
        LEFT JOIN media_files mf ON mf.sourceId = rs.id
        GROUP BY c.id
        ORDER BY c.name COLLATE NOCASE ASC
        """
    )
    fun getCollectionSummariesFlow(): Flow<List<CollectionSummaryRow>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    suspend fun getCollectionById(id: String): CollectionEntity?

    @Query("SELECT * FROM collections WHERE normalizedName = :normalizedName LIMIT 1")
    suspend fun getCollectionByNormalizedName(normalizedName: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollection(collection: CollectionEntity): Long

    @Query("UPDATE collections SET name = :name, normalizedName = :normalizedName, updatedAt = :updatedAt WHERE id = :collectionId")
    suspend fun renameCollection(collectionId: String, name: String, normalizedName: String, updatedAt: Long): Int

    @Query("SELECT * FROM collection_playback_state WHERE collectionId = :collectionId LIMIT 1")
    fun getCollectionPlaybackStateFlow(collectionId: String): Flow<CollectionPlaybackStateEntity?>

    @Query("SELECT * FROM collection_playback_state WHERE collectionId = :collectionId LIMIT 1")
    suspend fun getCollectionPlaybackState(collectionId: String): CollectionPlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCollectionPlaybackState(state: CollectionPlaybackStateEntity)

    @Query("DELETE FROM collection_playback_state WHERE collectionId = :collectionId")
    suspend fun deleteCollectionPlaybackState(collectionId: String)

    @Query("SELECT id FROM folder_nodes WHERE sourceId IN (SELECT id FROM root_sources WHERE collectionId = :collectionId) ORDER BY LENGTH(relativePath) DESC")
    suspend fun getFolderIdsForCollectionDeletion(collectionId: String): List<String>

    @Query("DELETE FROM folder_nodes WHERE id = :folderId")
    suspend fun deleteFolderById(folderId: String)

    @Query("DELETE FROM collections WHERE id = :collectionId")
    suspend fun deleteCollection(collectionId: String): Int

    @Query("SELECT * FROM root_sources WHERE collectionId = :collectionId")
    fun getRootSourcesFlow(collectionId: String): Flow<List<RootSourceEntity>>

    @Query("SELECT * FROM root_sources WHERE collectionId = :collectionId")
    suspend fun getRootSourcesForCollection(collectionId: String): List<RootSourceEntity>

    @Query("SELECT * FROM root_sources WHERE id = :id LIMIT 1")
    suspend fun getRootSourceById(id: String): RootSourceEntity?

    @Query("SELECT * FROM root_sources WHERE treeUri = :treeUri LIMIT 1")
    suspend fun getRootSourceByTreeUri(treeUri: String): RootSourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRootSource(rootSource: RootSourceEntity): Long

    @Query("UPDATE root_sources SET treeUri = :treeUri, isAvailable = 1 WHERE id = :sourceId")
    suspend fun reselectRootSource(sourceId: String, treeUri: String): Int

    @Query("UPDATE root_sources SET isAvailable = :isAvailable WHERE id = :sourceId")
    suspend fun updateRootSourceAvailability(sourceId: String, isAvailable: Boolean)

    @Query(
        """
        UPDATE root_sources 
        SET lastScanStatus = :status, 
            lastScanStartedAt = COALESCE(:startedAt, lastScanStartedAt), 
            lastScanCompletedAt = COALESCE(:completedAt, lastScanCompletedAt),
            lastScannedAt = COALESCE(:completedAt, lastScannedAt),
            lastScanSummary = COALESCE(:summaryJson, lastScanSummary) 
        WHERE id = :sourceId
        """
    )
    suspend fun updateRootScanState(
        sourceId: String,
        status: String,
        startedAt: Long?,
        completedAt: Long?,
        summaryJson: String?
    )
}
