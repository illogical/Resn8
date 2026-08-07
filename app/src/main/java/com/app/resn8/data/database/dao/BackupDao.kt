package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.CollectionPlaybackStateEntity
import com.app.resn8.data.database.entity.FolderNodeEntity
import com.app.resn8.data.database.entity.MediaFileEntity
import com.app.resn8.data.database.entity.PlaybackHistoryEntity
import com.app.resn8.data.database.entity.PlaylistEntity
import com.app.resn8.data.database.entity.PlaylistItemEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.data.database.entity.SavedQueueEntity
import com.app.resn8.data.database.entity.SavedQueueItemEntity
import com.app.resn8.data.database.entity.UiSessionStateEntity

@Dao
interface BackupDao {
    @Query("SELECT * FROM collections WHERE id IN (:ids) ORDER BY id")
    suspend fun getCollections(ids: List<String>): List<CollectionEntity>

    @Query("SELECT * FROM collections ORDER BY id")
    suspend fun getAllCollections(): List<CollectionEntity>

    @Query("SELECT * FROM root_sources WHERE collectionId IN (:collectionIds) ORDER BY id")
    suspend fun getSources(collectionIds: List<String>): List<RootSourceEntity>

    @Query("SELECT * FROM folder_nodes WHERE sourceId IN (:sourceIds) ORDER BY sourceId, relativePath, id")
    suspend fun getFolders(sourceIds: List<String>): List<FolderNodeEntity>

    @Query("SELECT * FROM media_files WHERE sourceId IN (:sourceIds) ORDER BY sourceId, relativePath, id")
    suspend fun getMedia(sourceIds: List<String>): List<MediaFileEntity>

    @Query("SELECT * FROM playback_history WHERE mediaId IN (:mediaIds) ORDER BY mediaId, startedAt, id")
    suspend fun getHistory(mediaIds: List<String>): List<PlaybackHistoryEntity>

    @Query("SELECT * FROM playlists WHERE collectionId IN (:collectionIds) ORDER BY collectionId, id")
    suspend fun getPlaylists(collectionIds: List<String>): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_items WHERE playlistId IN (:playlistIds) ORDER BY playlistId, position, mediaId")
    suspend fun getPlaylistItems(playlistIds: List<String>): List<PlaylistItemEntity>

    @Query("SELECT * FROM saved_queues WHERE collectionId IN (:collectionIds) ORDER BY collectionId, id")
    suspend fun getQueues(collectionIds: List<String>): List<SavedQueueEntity>

    @Query("SELECT * FROM saved_queue_items WHERE queueId IN (:queueIds) ORDER BY queueId, itemIndex")
    suspend fun getQueueItems(queueIds: List<String>): List<SavedQueueItemEntity>

    @Query("SELECT * FROM collection_playback_state WHERE collectionId IN (:collectionIds) ORDER BY collectionId")
    suspend fun getCollectionPlaybackStates(collectionIds: List<String>): List<CollectionPlaybackStateEntity>

    @Query("SELECT * FROM ui_session_state WHERE id = 1 LIMIT 1")
    suspend fun getUiSession(): UiSessionStateEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollections(rows: List<CollectionEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSources(rows: List<RootSourceEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertFolders(rows: List<FolderNodeEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMedia(rows: List<MediaFileEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertHistory(rows: List<PlaybackHistoryEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylists(rows: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylistItems(rows: List<PlaylistItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQueues(rows: List<SavedQueueEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertQueueItems(rows: List<SavedQueueItemEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCollectionPlaybackStates(rows: List<CollectionPlaybackStateEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertUiSession(row: UiSessionStateEntity)
}
