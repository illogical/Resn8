package com.app.resn8.domain.repository

import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: String): Playlist?
    suspend fun createPlaylist(collectionId: String, name: String): Playlist
    suspend fun renamePlaylist(playlistId: String, newName: String)
    suspend fun deletePlaylist(playlistId: String)
    fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItem>>
    suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>)
    suspend fun removeItemFromPlaylist(playlistId: String, mediaId: String)
    suspend fun reorderPlaylistItem(playlistId: String, mediaId: String, newPosition: Double)
}
