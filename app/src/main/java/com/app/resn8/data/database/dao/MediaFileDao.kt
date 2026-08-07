package com.app.resn8.data.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.resn8.data.database.entity.MediaFileEntity
import kotlinx.coroutines.flow.Flow

data class ArtistSummaryRow(
    val rawArtist: String?,
    val totalTrackCount: Int,
    val availableTrackCount: Int,
    val albumCount: Int,
    val representativeArtworkUri: String?
)

data class AlbumSummaryRow(
    val rawAlbum: String?,
    val effectiveAlbumArtist: String?,
    val totalTrackCount: Int,
    val availableTrackCount: Int,
    val minYear: Int?,
    val representativeMediaId: String?,
    val representativeArtworkUri: String?
)

@Dao
interface MediaFileDao {
    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId")
    suspend fun getMediaFilesBySourceId(sourceId: String): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE id = :id LIMIT 1")
    suspend fun getMediaFileById(id: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE id IN (:ids)")
    suspend fun getMediaFilesByIds(ids: List<String>): List<MediaFileEntity>

    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId AND relativePath = :relativePath LIMIT 1")
    suspend fun getMediaFileByPath(sourceId: String, relativePath: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId AND documentUri = :documentUri LIMIT 1")
    suspend fun getMediaFileByUri(sourceId: String, documentUri: String): MediaFileEntity?

    @Query("SELECT * FROM media_files WHERE sourceId = :sourceId AND documentId = :documentId LIMIT 1")
    suspend fun getMediaFileByDocumentId(sourceId: String, documentId: String): MediaFileEntity?

    @Query(
        """
        SELECT * FROM media_files
        WHERE sourceId = :sourceId AND size = :size AND modifiedTimeMs = :modifiedTimeMs AND durationMs = :durationMs
        LIMIT 2
        """
    )
    suspend fun getMediaByCompleteSignature(
        sourceId: String,
        size: Long,
        modifiedTimeMs: Long,
        durationMs: Long
    ): List<MediaFileEntity>

    @Query(
        """
        SELECT 
          mf.artist AS rawArtist,
          COUNT(mf.id) AS totalTrackCount,
          SUM(CASE WHEN mf.isAvailable = 1 THEN 1 ELSE 0 END) AS availableTrackCount,
          COUNT(DISTINCT COALESCE(mf.album, '__UNKNOWN_ALBUM__')) AS albumCount,
          (SELECT mf2.artworkUri FROM media_files mf2 WHERE (mf2.artist = mf.artist OR (mf2.artist IS NULL AND mf.artist IS NULL)) AND mf2.artworkUri IS NOT NULL LIMIT 1) AS representativeArtworkUri
        FROM media_files mf
        INNER JOIN root_sources rs ON mf.sourceId = rs.id
        WHERE rs.collectionId = :collectionId
          AND (:availabilityFilter = 'ALL' OR (:availabilityFilter = 'AVAILABLE_ONLY' AND mf.isAvailable = 1) OR (:availabilityFilter = 'UNAVAILABLE_ONLY' AND mf.isAvailable = 0))
          AND (:excludeDisliked = 0 OR mf.likeScore >= 0)
          AND (
            :searchPattern IS NULL OR (
              mf.displayTitle LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.title, '') LIKE :searchPattern ESCAPE '\' OR
              mf.filename LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.artist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.albumArtist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.album, '') LIKE :searchPattern ESCAPE '\'
            )
          )
        GROUP BY mf.artist
        ORDER BY
          (mf.artist IS NULL) ASC,
          CASE WHEN :sortDirection = 'ASCENDING' THEN LOWER(TRIM(mf.artist)) END ASC,
          CASE WHEN :sortDirection = 'DESCENDING' THEN LOWER(TRIM(mf.artist)) END DESC
        """
    )
    fun getArtistSummariesPaged(
        collectionId: String,
        availabilityFilter: String,
        excludeDisliked: Int,
        searchPattern: String?,
        sortDirection: String = "ASCENDING"
    ): PagingSource<Int, ArtistSummaryRow>

    @Query(
        """
        SELECT 
          mf.album AS rawAlbum,
          COALESCE(mf.albumArtist, mf.artist) AS effectiveAlbumArtist,
          COUNT(mf.id) AS totalTrackCount,
          SUM(CASE WHEN mf.isAvailable = 1 THEN 1 ELSE 0 END) AS availableTrackCount,
          MIN(mf.year) AS minYear,
          MIN(mf.id) AS representativeMediaId,
          (SELECT mf2.artworkUri FROM media_files mf2 WHERE (mf2.album = mf.album OR (mf2.album IS NULL AND mf.album IS NULL)) AND (COALESCE(mf2.albumArtist, mf2.artist) = COALESCE(mf.albumArtist, mf.artist) OR (COALESCE(mf2.albumArtist, mf2.artist) IS NULL AND COALESCE(mf.albumArtist, mf.artist) IS NULL)) AND mf2.artworkUri IS NOT NULL LIMIT 1) AS representativeArtworkUri
        FROM media_files mf
        INNER JOIN root_sources rs ON mf.sourceId = rs.id
        WHERE rs.collectionId = :collectionId
          AND (
            :isArtistFilterNull = 1 OR
            (:artistKeyIsUnknown = 1 AND mf.artist IS NULL) OR
            (:artistKeyIsUnknown = 0 AND mf.artist = :artistKeyValue)
          )
          AND (:availabilityFilter = 'ALL' OR (:availabilityFilter = 'AVAILABLE_ONLY' AND mf.isAvailable = 1) OR (:availabilityFilter = 'UNAVAILABLE_ONLY' AND mf.isAvailable = 0))
          AND (:excludeDisliked = 0 OR mf.likeScore >= 0)
          AND (
            :searchPattern IS NULL OR (
              mf.displayTitle LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.title, '') LIKE :searchPattern ESCAPE '\' OR
              mf.filename LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.artist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.albumArtist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.album, '') LIKE :searchPattern ESCAPE '\'
            )
          )
        GROUP BY mf.album, COALESCE(mf.albumArtist, mf.artist)
        ORDER BY
          (mf.album IS NULL) ASC,
          CASE WHEN :sortDirection = 'ASCENDING' THEN LOWER(TRIM(mf.album)) END ASC,
          CASE WHEN :sortDirection = 'DESCENDING' THEN LOWER(TRIM(mf.album)) END DESC,
          (COALESCE(mf.albumArtist, mf.artist) IS NULL) ASC,
          LOWER(TRIM(COALESCE(mf.albumArtist, mf.artist))) ASC
        """
    )
    fun getAlbumSummariesPaged(
        collectionId: String,
        isArtistFilterNull: Int,
        artistKeyIsUnknown: Int,
        artistKeyValue: String?,
        availabilityFilter: String,
        excludeDisliked: Int,
        searchPattern: String?,
        sortDirection: String = "ASCENDING"
    ): PagingSource<Int, AlbumSummaryRow>

    @Query(
        """
        SELECT mf.* FROM media_files mf
        INNER JOIN root_sources rs ON mf.sourceId = rs.id
        WHERE rs.collectionId = :collectionId
          AND (:sourceId IS NULL OR mf.sourceId = :sourceId)
          AND (:folderId IS NULL OR mf.folderId = :folderId)
          AND (
            :isArtistFilterNull = 1 OR
            (:artistKeyIsUnknown = 1 AND mf.artist IS NULL) OR
            (:artistKeyIsUnknown = 0 AND mf.artist = :artistKeyValue)
          )
          AND (
            :isAlbumFilterNull = 1 OR
            (:albumKeyIsUnknown = 1 AND mf.album IS NULL) OR
            (:albumKeyIsUnknown = 0 AND mf.album = :albumKeyValue)
          )
          AND (
            :isAlbumArtistFilterNull = 1 OR
            (:albumArtistKeyIsUnknown = 1 AND COALESCE(mf.albumArtist, mf.artist) IS NULL) OR
            (:albumArtistKeyIsUnknown = 0 AND COALESCE(mf.albumArtist, mf.artist) = :albumArtistKeyValue)
          )
          AND (
            :availabilityFilter = 'ALL' OR
            (:availabilityFilter = 'AVAILABLE_ONLY' AND mf.isAvailable = 1) OR
            (:availabilityFilter = 'UNAVAILABLE_ONLY' AND mf.isAvailable = 0)
          )
          AND (:excludeDisliked = 0 OR mf.likeScore >= 0)
          AND (
            :searchPattern IS NULL OR (
              mf.displayTitle LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.title, '') LIKE :searchPattern ESCAPE '\' OR
              mf.filename LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.artist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.albumArtist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.album, '') LIKE :searchPattern ESCAPE '\'
            )
          )
        ORDER BY
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.artist IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' AND :sortDirection = 'ASCENDING' THEN LOWER(TRIM(mf.artist)) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' AND :sortDirection = 'DESCENDING' THEN LOWER(TRIM(mf.artist)) END DESC,
          CASE WHEN :sortOrder = 'ARTIST' THEN COALESCE(mf.albumArtist, mf.artist) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.album IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN LOWER(TRIM(mf.album)) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.discNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN mf.discNumber END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.trackNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN mf.trackNumber END ASC,

          CASE WHEN :sortOrder = 'ALBUM' THEN (mf.album IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' AND :sortDirection = 'ASCENDING' THEN LOWER(TRIM(mf.album)) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' AND :sortDirection = 'DESCENDING' THEN LOWER(TRIM(mf.album)) END DESC,
          CASE WHEN :sortOrder = 'ALBUM' THEN COALESCE(mf.albumArtist, mf.artist) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN (mf.discNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN mf.discNumber END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN (mf.trackNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN mf.trackNumber END ASC,

          CASE WHEN :sortOrder = 'TRACK' THEN (mf.discNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'ASCENDING' THEN mf.discNumber END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'DESCENDING' THEN mf.discNumber END DESC,
          CASE WHEN :sortOrder = 'TRACK' THEN (mf.trackNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'ASCENDING' THEN mf.trackNumber END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'DESCENDING' THEN mf.trackNumber END DESC,

          CASE WHEN :sortOrder = 'TITLE' AND :sortDirection = 'ASCENDING' THEN LOWER(TRIM(COALESCE(NULLIF(mf.displayTitle, ''), mf.filename))) END ASC,
          CASE WHEN :sortOrder = 'TITLE' AND :sortDirection = 'DESCENDING' THEN LOWER(TRIM(COALESCE(NULLIF(mf.displayTitle, ''), mf.filename))) END DESC,

          CASE WHEN :sortOrder = 'RECENTLY_ADDED' AND :sortDirection = 'ASCENDING' THEN mf.firstIndexedAt END ASC,
          CASE WHEN :sortOrder = 'RECENTLY_ADDED' AND :sortDirection = 'DESCENDING' THEN mf.firstIndexedAt END DESC,
          CASE WHEN :sortOrder IN ('MOST_PLAYED', 'LEAST_PLAYED') AND :sortDirection = 'ASCENDING' THEN mf.playCount END ASC,
          CASE WHEN :sortOrder IN ('MOST_PLAYED', 'LEAST_PLAYED') AND :sortDirection = 'DESCENDING' THEN mf.playCount END DESC,

          CASE WHEN :sortOrder = 'UNPLAYED' THEN (mf.playCount > 0) END ASC,

          CASE WHEN :sortOrder IN ('MOST_RECENT', 'LEAST_RECENT') THEN (mf.lastPlayedAt IS NULL) END ASC,
          CASE WHEN :sortOrder IN ('MOST_RECENT', 'LEAST_RECENT') AND :sortDirection = 'ASCENDING' THEN mf.lastPlayedAt END ASC,
          CASE WHEN :sortOrder IN ('MOST_RECENT', 'LEAST_RECENT') AND :sortDirection = 'DESCENDING' THEN mf.lastPlayedAt END DESC,

          CASE WHEN :sortOrder = 'MOST_LIKED' AND :sortDirection = 'ASCENDING' THEN mf.likeScore END ASC,
          CASE WHEN :sortOrder = 'MOST_LIKED' AND :sortDirection = 'DESCENDING' THEN mf.likeScore END DESC,

          LOWER(TRIM(COALESCE(NULLIF(mf.displayTitle, ''), mf.filename))) ASC,
          mf.id ASC
        """
    )
    fun getTracksPaged(
        collectionId: String,
        sourceId: String?,
        folderId: String?,
        isArtistFilterNull: Int,
        artistKeyIsUnknown: Int,
        artistKeyValue: String?,
        isAlbumFilterNull: Int,
        albumKeyIsUnknown: Int,
        albumKeyValue: String?,
        isAlbumArtistFilterNull: Int = 1,
        albumArtistKeyIsUnknown: Int = 0,
        albumArtistKeyValue: String? = null,
        availabilityFilter: String,
        excludeDisliked: Int,
        searchPattern: String?,
        sortOrder: String,
        sortDirection: String = "ASCENDING"
    ): PagingSource<Int, MediaFileEntity>

    @Query(
        """
        SELECT mf.id FROM media_files mf
        INNER JOIN root_sources rs ON mf.sourceId = rs.id
        WHERE rs.collectionId = :collectionId
          AND (:sourceId IS NULL OR mf.sourceId = :sourceId)
          AND (:folderId IS NULL OR mf.folderId = :folderId)
          AND (
            :isArtistFilterNull = 1 OR
            (:artistKeyIsUnknown = 1 AND mf.artist IS NULL) OR
            (:artistKeyIsUnknown = 0 AND mf.artist = :artistKeyValue)
          )
          AND (
            :isAlbumFilterNull = 1 OR
            (:albumKeyIsUnknown = 1 AND mf.album IS NULL) OR
            (:albumKeyIsUnknown = 0 AND mf.album = :albumKeyValue)
          )
          AND (
            :isAlbumArtistFilterNull = 1 OR
            (:albumArtistKeyIsUnknown = 1 AND COALESCE(mf.albumArtist, mf.artist) IS NULL) OR
            (:albumArtistKeyIsUnknown = 0 AND COALESCE(mf.albumArtist, mf.artist) = :albumArtistKeyValue)
          )
          AND (
            :availabilityFilter = 'ALL' OR
            (:availabilityFilter = 'AVAILABLE_ONLY' AND mf.isAvailable = 1) OR
            (:availabilityFilter = 'UNAVAILABLE_ONLY' AND mf.isAvailable = 0)
          )
          AND (:excludeDisliked = 0 OR mf.likeScore >= 0)
          AND (
            :searchPattern IS NULL OR (
              mf.displayTitle LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.title, '') LIKE :searchPattern ESCAPE '\' OR
              mf.filename LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.artist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.albumArtist, '') LIKE :searchPattern ESCAPE '\' OR
              COALESCE(mf.album, '') LIKE :searchPattern ESCAPE '\'
            )
          )
        ORDER BY
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.artist IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' AND :sortDirection = 'ASCENDING' THEN LOWER(TRIM(mf.artist)) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' AND :sortDirection = 'DESCENDING' THEN LOWER(TRIM(mf.artist)) END DESC,
          CASE WHEN :sortOrder = 'ARTIST' THEN COALESCE(mf.albumArtist, mf.artist) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.album IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN LOWER(TRIM(mf.album)) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.discNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN mf.discNumber END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN (mf.trackNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ARTIST' THEN mf.trackNumber END ASC,

          CASE WHEN :sortOrder = 'ALBUM' THEN (mf.album IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' AND :sortDirection = 'ASCENDING' THEN LOWER(TRIM(mf.album)) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' AND :sortDirection = 'DESCENDING' THEN LOWER(TRIM(mf.album)) END DESC,
          CASE WHEN :sortOrder = 'ALBUM' THEN COALESCE(mf.albumArtist, mf.artist) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN (mf.discNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN mf.discNumber END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN (mf.trackNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'ALBUM' THEN mf.trackNumber END ASC,

          CASE WHEN :sortOrder = 'TRACK' THEN (mf.discNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'ASCENDING' THEN mf.discNumber END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'DESCENDING' THEN mf.discNumber END DESC,
          CASE WHEN :sortOrder = 'TRACK' THEN (mf.trackNumber IS NULL) END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'ASCENDING' THEN mf.trackNumber END ASC,
          CASE WHEN :sortOrder = 'TRACK' AND :sortDirection = 'DESCENDING' THEN mf.trackNumber END DESC,

          CASE WHEN :sortOrder = 'TITLE' AND :sortDirection = 'ASCENDING' THEN LOWER(TRIM(COALESCE(NULLIF(mf.displayTitle, ''), mf.filename))) END ASC,
          CASE WHEN :sortOrder = 'TITLE' AND :sortDirection = 'DESCENDING' THEN LOWER(TRIM(COALESCE(NULLIF(mf.displayTitle, ''), mf.filename))) END DESC,

          CASE WHEN :sortOrder = 'RECENTLY_ADDED' AND :sortDirection = 'ASCENDING' THEN mf.firstIndexedAt END ASC,
          CASE WHEN :sortOrder = 'RECENTLY_ADDED' AND :sortDirection = 'DESCENDING' THEN mf.firstIndexedAt END DESC,
          CASE WHEN :sortOrder IN ('MOST_PLAYED', 'LEAST_PLAYED') AND :sortDirection = 'ASCENDING' THEN mf.playCount END ASC,
          CASE WHEN :sortOrder IN ('MOST_PLAYED', 'LEAST_PLAYED') AND :sortDirection = 'DESCENDING' THEN mf.playCount END DESC,

          CASE WHEN :sortOrder = 'UNPLAYED' THEN (mf.playCount > 0) END ASC,

          CASE WHEN :sortOrder IN ('MOST_RECENT', 'LEAST_RECENT') THEN (mf.lastPlayedAt IS NULL) END ASC,
          CASE WHEN :sortOrder IN ('MOST_RECENT', 'LEAST_RECENT') AND :sortDirection = 'ASCENDING' THEN mf.lastPlayedAt END ASC,
          CASE WHEN :sortOrder IN ('MOST_RECENT', 'LEAST_RECENT') AND :sortDirection = 'DESCENDING' THEN mf.lastPlayedAt END DESC,

          CASE WHEN :sortOrder = 'MOST_LIKED' AND :sortDirection = 'ASCENDING' THEN mf.likeScore END ASC,
          CASE WHEN :sortOrder = 'MOST_LIKED' AND :sortDirection = 'DESCENDING' THEN mf.likeScore END DESC,

          LOWER(TRIM(COALESCE(NULLIF(mf.displayTitle, ''), mf.filename))) ASC,
          mf.id ASC
        """
    )
    suspend fun snapshotVisibleMediaIds(
        collectionId: String,
        sourceId: String?,
        folderId: String?,
        isArtistFilterNull: Int,
        artistKeyIsUnknown: Int,
        artistKeyValue: String?,
        isAlbumFilterNull: Int,
        albumKeyIsUnknown: Int,
        albumKeyValue: String?,
        isAlbumArtistFilterNull: Int = 1,
        albumArtistKeyIsUnknown: Int = 0,
        albumArtistKeyValue: String? = null,
        availabilityFilter: String,
        excludeDisliked: Int,
        searchPattern: String?,
        sortOrder: String,
        sortDirection: String = "ASCENDING"
    ): List<String>

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
    suspend fun insertMediaFile(file: MediaFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMediaFiles(files: List<MediaFileEntity>)

    @Update
    suspend fun updateMediaFile(file: MediaFileEntity)

    @Query(
        """
        UPDATE media_files
        SET likeScore = CASE
            WHEN :delta < 0 THEN MAX(-1, likeScore + :delta)
            ELSE likeScore + :delta
        END
        WHERE id = :mediaId
        """
    )
    suspend fun updateLikeScore(mediaId: String, delta: Int): Int

    @Query("UPDATE media_files SET playCount = playCount + 1, lastPlayedAt = :now WHERE id = :mediaId")
    suspend fun incrementPlayCount(mediaId: String, now: Long)

    @Query("UPDATE media_files SET isAvailable = :isAvailable WHERE id = :mediaId")
    suspend fun updateAvailability(mediaId: String, isAvailable: Boolean)

    @Query("UPDATE media_files SET isAvailable = :isAvailable WHERE id IN (:mediaIds)")
    suspend fun updateAvailabilityForIds(mediaIds: List<String>, isAvailable: Boolean)

    @Query(
        """
        SELECT COUNT(*) FROM media_files
        WHERE sourceId = :sourceId
          AND id NOT IN (
            SELECT resolvedMediaId FROM staged_media
            WHERE scanId = :scanId AND resolvedMediaId IS NOT NULL
          )
        """
    )
    suspend fun countMissingFromStagedScan(sourceId: String, scanId: String): Int

    @Query(
        """
        UPDATE media_files SET isAvailable = 0
        WHERE sourceId = :sourceId
          AND id NOT IN (
            SELECT resolvedMediaId FROM staged_media
            WHERE scanId = :scanId AND resolvedMediaId IS NOT NULL
          )
        """
    )
    suspend fun markMissingFromStagedScanUnavailable(sourceId: String, scanId: String)
}
