package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.resn8.data.database.entity.MediaFileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId")
    fun getMediaFilesBySourceId(sourceId: String): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE id = :id LIMIT 1")
    fun getMediaFileById(id: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId AND relativePath = :relativePath LIMIT 1")
    fun getMediaFileByPath(sourceId: String, relativePath: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId AND documentUri = :documentUri LIMIT 1")
    fun getMediaFileByUri(sourceId: String, documentUri: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId AND documentId = :documentId LIMIT 1")
    fun getMediaFileByDocumentId(sourceId: String, documentId: String): MediaFileEntity?

    @Query(
        """
        SELECT * FROM media_files 
        WHERE (:folderId IS NULL OR folderId = :folderId)
          AND (:artist IS NULL OR artist = :artist)
          AND (:album IS NULL OR album = :album)
          AND (:searchQuery IS NULL OR title LIKE '%' || :searchQuery || '%' OR filename LIKE '%' || :searchQuery || '%' OR artist LIKE '%' || :searchQuery || '%')
        ORDER BY 
          CASE WHEN :sortOrder = 'TITLE' THEN displayTitle END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN artist END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN album END ASC,
          CASE WHEN :sortOrder = 'MOST_PLAYED' THEN playCount END DESC,
          CASE WHEN :sortOrder = 'LEAST_PLAYED' THEN playCount END ASC,
          CASE WHEN :sortOrder = 'MOST_RECENT' THEN lastPlayedAt END DESC,
          CASE WHEN :sortOrder = 'LEAST_RECENT' THEN lastPlayedAt END ASC,
          CASE WHEN :sortOrder = 'MOST_LIKED' THEN likeScore END DESC,
          CASE WHEN :sortOrder = 'RECENTLY_ADDED' THEN firstIndexedAt END DESC,
          firstIndexedAt DESC
        """
    )
    fun getMediaFilesFlow(
        folderId: String?,
        artist: String?,
        album: String?,
        searchQuery: String?,
        sortOrder: String
    ): Flow<List<MediaFileEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMediaFile(file: MediaFileEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertMediaFiles(files: List<MediaFileEntity>)

    @Update
    fun updateMediaFile(file: MediaFileEntity)

    @Query("UPDATE media_files SET likeScore = likeScore + :delta WHERE id = :mediaId")
    fun updateLikeScore(mediaId: String, delta: Int)

    @Query("UPDATE media_files SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :mediaId")
    fun incrementPlayCount(mediaId: String, now: Long)

    @Query("UPDATE media_files SET isAvailable = :isAvailable WHERE id = :mediaId")
    fun updateAvailability(mediaId: String, isAvailable: Boolean)

    @Query("UPDATE media_files SET isAvailable = :isAvailable WHERE id IN (:mediaIds)")
    fun updateAvailabilityForIds(mediaIds: List<String>, isAvailable: Boolean)
}
