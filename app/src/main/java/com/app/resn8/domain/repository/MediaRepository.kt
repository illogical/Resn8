package com.app.resn8.domain.repository

import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SortOrder
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getMediaFilesFlow(
        collectionId: String?,
        folderId: String? = null,
        artist: String? = null,
        album: String? = null,
        searchQuery: String? = null,
        sortOrder: SortOrder = SortOrder.ARTIST
    ): Flow<List<MediaFile>>

    suspend fun getMediaFileById(id: String): MediaFile?
    suspend fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNode>>
    suspend fun updateLikeScore(mediaId: String, delta: Int)
    suspend fun recordPlay(mediaId: String, listenedDurationMs: Long, isMeaningful: Boolean)
}
