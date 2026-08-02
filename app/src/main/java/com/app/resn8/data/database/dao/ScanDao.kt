package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.resn8.data.database.entity.ScanRunEntity
import com.app.resn8.data.database.entity.StagedFolderEntity
import com.app.resn8.data.database.entity.StagedMediaEntity

@Dao
interface ScanDao {
    @Query("SELECT * FROM scan_runs WHERE id = :id LIMIT 1")
    fun getScanRunById(id: String): ScanRunEntity?

    @Query("SELECT * FROM scan_runs WHERE sourceId = :sourceId AND status = 'IN_PROGRESS' ORDER BY startedAt DESC LIMIT 1")
    fun getActiveScanRun(sourceId: String): ScanRunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertScanRun(scanRun: ScanRunEntity)

    @Query("UPDATE scan_runs SET status = :status, completedAt = :completedAt, errorSummary = :errorSummary WHERE id = :scanId")
    fun updateScanRunStatus(scanId: String, status: String, completedAt: Long?, errorSummary: String? = null)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStagedFolders(folders: List<StagedFolderEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertStagedMedia(media: List<StagedMediaEntity>)

    @Query("SELECT * FROM staged_folders WHERE scanId = :scanId")
    fun getStagedFolders(scanId: String): List<StagedFolderEntity>

    @Query("SELECT * FROM staged_media WHERE scanId = :scanId")
    fun getStagedMedia(scanId: String): List<StagedMediaEntity>

    @Query("DELETE FROM staged_folders WHERE scanId = :scanId")
    fun deleteStagedFolders(scanId: String)

    @Query("DELETE FROM staged_media WHERE scanId = :scanId")
    fun deleteStagedMedia(scanId: String)
}
