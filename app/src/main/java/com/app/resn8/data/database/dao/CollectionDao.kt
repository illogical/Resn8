package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CollectionDao {
    @Query("SELECT * FROM collections ORDER BY name ASC")
    fun getCollectionsFlow(): Flow<List<CollectionEntity>>

    @Query("SELECT * FROM collections WHERE id = :id LIMIT 1")
    fun getCollectionById(id: String): CollectionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertCollection(collection: CollectionEntity)

    @Query("SELECT * FROM root_sources WHERE collectionId = :collectionId")
    fun getRootSourcesFlow(collectionId: String): Flow<List<RootSourceEntity>>

    @Query("SELECT * FROM root_sources WHERE id = :id LIMIT 1")
    fun getRootSourceById(id: String): RootSourceEntity?

    @Query("SELECT * FROM root_sources WHERE treeUri = :treeUri LIMIT 1")
    fun getRootSourceByTreeUri(treeUri: String): RootSourceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertRootSource(rootSource: RootSourceEntity)

    @Query("UPDATE root_sources SET isAvailable = :isAvailable WHERE id = :sourceId")
    fun updateRootSourceAvailability(sourceId: String, isAvailable: Boolean)

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
    fun updateRootScanState(
        sourceId: String,
        status: String,
        startedAt: Long?,
        completedAt: Long?,
        summaryJson: String?
    )
}
