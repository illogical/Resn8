package com.app.resn8.domain.repository

import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.model.PlaylistWithMembership
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>>
    suspend fun getPlaylistById(id: String): Playlist?
    suspend fun createPlaylist(collectionId: String, name: String): Result<Playlist>
    suspend fun renamePlaylist(playlistId: String, newName: String): Result<Unit>
    suspend fun deletePlaylist(playlistId: String)
    fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItem>>
    suspend fun getPlaylistItems(playlistId: String): List<PlaylistItem>
    suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>)
    suspend fun removeItemFromPlaylist(playlistId: String, mediaId: String)
    suspend fun removeItemsFromPlaylist(playlistId: String, mediaIds: List<String>)
    suspend fun reorderPlaylistItem(playlistId: String, mediaId: String, newPosition: Long)
    suspend fun compactPlaylistRanks(playlistId: String)
    fun getPlaylistsWithMembershipFlow(collectionId: String, mediaIds: List<String>): Flow<List<PlaylistWithMembership>>
}
