package com.app.resn8.data.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.resn8.data.database.entity.PlaylistEntity
import com.app.resn8.data.database.entity.PlaylistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists WHERE collectionId = :collectionId ORDER BY name ASC")
    fun getPlaylistsFlow(collectionId: String): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists WHERE id = :id LIMIT 1")
    suspend fun getPlaylistById(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists WHERE collectionId = :collectionId AND normalizedName = :normalizedName LIMIT 1")
    suspend fun getPlaylistByNormalizedName(collectionId: String, normalizedName: String): PlaylistEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: String)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getPlaylistItems(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT MAX(position) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun getMaxPosition(playlistId: String): Long?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylistItems(items: List<PlaylistItemEntity>)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun deletePlaylistItem(playlistId: String, mediaId: String)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND mediaId IN (:mediaIds)")
    suspend fun deletePlaylistItems(playlistId: String, mediaIds: List<String>)

    @Query("UPDATE playlist_items SET position = :newPosition WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun updatePlaylistItemPosition(playlistId: String, mediaId: String, newPosition: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun deleteAllPlaylistItems(playlistId: String)
}
