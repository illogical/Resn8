package com.app.resn8.domain.repository

import androidx.paging.PagingData
import com.app.resn8.domain.model.AlbumSummary
import com.app.resn8.domain.model.ArtistSummary
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.FolderBreadcrumb
import com.app.resn8.domain.model.FolderListItem
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.SelectionResolutionResult
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.StagedFolder
import com.app.resn8.domain.model.StagedMedia
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    fun getArtistSummariesPaged(query: LibraryQuery): Flow<PagingData<ArtistSummary>>
    fun getAlbumSummariesPaged(query: LibraryQuery): Flow<PagingData<AlbumSummary>>
    fun getTracksPaged(query: LibraryQuery): Flow<PagingData<MediaFile>>
    
    fun getRootFolderNode(sourceId: String): Flow<FolderNode?>
    fun getDirectChildFolders(parentId: String): Flow<List<FolderListItem>>
    fun getFolderBreadcrumbs(folderId: String): Flow<List<FolderBreadcrumb>>
    fun getPagedFolderMedia(folderId: String, query: LibraryQuery): Flow<PagingData<MediaFile>>

    suspend fun resolveSelectionMediaIds(
        selectedFileIds: Set<String>,
        selectedFolderIds: Set<String>,
        availability: AvailabilityFilter = AvailabilityFilter.ALL
    ): SelectionResolutionResult

    suspend fun snapshotVisibleMediaIds(query: LibraryQuery): List<String>

    fun getMediaFilesFlow(
        collectionId: String?,
        folderId: String? = null,
        artist: String? = null,
        album: String? = null,
        searchQuery: String? = null,
        sortOrder: SortOrder = SortOrder.ARTIST
    ): Flow<List<MediaFile>>

    suspend fun getMediaFileById(id: String): MediaFile?
    suspend fun getMediaFilesByIdsPreservingOrder(mediaIds: List<String>): List<MediaFile>
    fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNode>>
    suspend fun updateLikeScore(mediaId: String, delta: Int)
    suspend fun recordPlay(mediaId: String, listenedDurationMs: Long, isMeaningful: Boolean)
    suspend fun commitMeaningfulPlay(
        sessionOccurrenceId: String,
        mediaId: String,
        startedAt: Long,
        endedAt: Long? = null,
        accumulatedListenedDurationMs: Long = 0L,
        result: PlaybackHistoryResult = PlaybackHistoryResult.THRESHOLD_COUNTED
    ): Boolean

    suspend fun updateMediaAvailability(mediaId: String, isAvailable: Boolean)

    // Scan staging & publication
    suspend fun startScanRun(sourceId: String): String
    suspend fun stageFolders(scanId: String, folders: List<StagedFolder>)
    suspend fun stageMedia(scanId: String, media: List<StagedMedia>)
    suspend fun publishResolvedScan(
        scanId: String,
        resolvedFolders: List<FolderNode>,
        resolvedMedia: List<MediaFile>,
        unavailableMediaIds: List<String>,
        scanResult: ScanResult
    )
    suspend fun publishStagedScan(scanId: String, sourceId: String, scanResult: ScanResult): ScanResult
    suspend fun cancelScanRun(scanId: String)
    suspend fun failScanRun(scanId: String, errorSummary: String)
}
