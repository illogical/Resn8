package com.app.resn8.domain.repository

import com.app.resn8.domain.model.AddItemsResult
import com.app.resn8.domain.model.MoveDirection
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.model.PlaylistWithItemCount
import com.app.resn8.domain.model.PlaylistWithMembership
import kotlinx.coroutines.flow.Flow

interface PlaylistRepository {
    fun getPlaylistsFlow(collectionId: String): Flow<List<Playlist>>
    fun getPlaylistsWithItemCountFlow(collectionId: String): Flow<List<PlaylistWithItemCount>>
    suspend fun getPlaylistById(id: String): Playlist?
    suspend fun createPlaylist(collectionId: String, name: String): Result<Playlist>
    suspend fun renamePlaylist(playlistId: String, newName: String): Result<Unit>
    suspend fun deletePlaylist(playlistId: String)
    fun getPlaylistItemsFlow(playlistId: String): Flow<List<PlaylistItem>>
    suspend fun getPlaylistItems(playlistId: String): List<PlaylistItem>
    suspend fun addItemsToPlaylist(playlistId: String, mediaIds: List<String>): AddItemsResult
    suspend fun removeItemFromPlaylist(playlistId: String, mediaId: String)
    suspend fun removeItemsFromPlaylist(playlistId: String, mediaIds: List<String>)
    suspend fun movePlaylistItem(playlistId: String, mediaId: String, direction: MoveDirection)
    suspend fun movePlaylistItemToPosition(playlistId: String, mediaId: String, targetIndex: Int)
    suspend fun compactPlaylistRanks(playlistId: String)
    fun getPlaylistsWithMembershipFlow(collectionId: String, mediaIds: List<String>): Flow<List<PlaylistWithMembership>>
}

