package com.app.resn8.data.repository

import androidx.paging.PagingData
import com.app.resn8.domain.model.AlbumSummary
import com.app.resn8.domain.model.ArtistSummary
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.FolderBreadcrumb
import com.app.resn8.domain.model.FolderListItem
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.PlaybackHistory
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.SelectionResolutionResult
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.SortDirection
import com.app.resn8.domain.model.defaultDirection
import com.app.resn8.domain.model.StagedFolder
import com.app.resn8.domain.model.StagedMedia
import com.app.resn8.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeMediaRepository(
    initialMediaFiles: List<MediaFile> = emptyList(),
    initialFolderNodes: List<FolderNode> = emptyList()
) : MediaRepository {

    private val _mediaFiles = MutableStateFlow(initialMediaFiles)
    private val _folderNodes = MutableStateFlow(initialFolderNodes)
    private val _historyOccurrences = mutableSetOf<String>()
    private val stagedFoldersByScan = mutableMapOf<String, MutableList<StagedFolder>>()
    private val stagedMediaByScan = mutableMapOf<String, MutableList<StagedMedia>>()

    private fun filterMediaFiles(query: LibraryQuery): List<MediaFile> {
        val normSearch = query.normalizedSearchText()
        return _mediaFiles.value.filter { item ->
            val matchesSource = query.sourceId == null || item.sourceId == query.sourceId
            val matchesFolder = query.folderId == null || item.folderId == query.folderId
            val matchesArtist = when (val k = query.artist) {
                null -> true
                is MetadataGroupKey.Unknown -> item.artist == null
                is MetadataGroupKey.Known -> item.artist.equals(k.value, ignoreCase = true)
            }
            val matchesAlbum = when (val k = query.album) {
                null -> true
                is MetadataGroupKey.Unknown -> item.album == null
                is MetadataGroupKey.Known -> item.album.equals(k.value, ignoreCase = true)
            }
            val matchesAvailability = when (query.filters.availability) {
                AvailabilityFilter.ALL -> true
                AvailabilityFilter.AVAILABLE_ONLY -> item.isAvailable
                AvailabilityFilter.UNAVAILABLE_ONLY -> !item.isAvailable
            }
            val matchesDislike = !query.filters.excludeDisliked || item.likeScore >= 0
            val matchesSearch = normSearch == null || (
                item.displayTitle.contains(normSearch, ignoreCase = true) ||
                (item.title?.contains(normSearch, ignoreCase = true) == true) ||
                item.filename.contains(normSearch, ignoreCase = true) ||
                (item.artist?.contains(normSearch, ignoreCase = true) == true) ||
                (item.albumArtist?.contains(normSearch, ignoreCase = true) == true) ||
                (item.album?.contains(normSearch, ignoreCase = true) == true)
            )

            matchesSource && matchesFolder && matchesArtist && matchesAlbum &&
                matchesAvailability && matchesDislike && matchesSearch
        }.sortedWith(getComparator(query.sort, query.sortDirection))
    }

    override fun getArtistSummariesPaged(query: LibraryQuery): Flow<PagingData<ArtistSummary>> {
        return _mediaFiles.map {
            val filtered = filterMediaFiles(query)
            val groups = filtered.groupBy { it.artist }
            val summaries = groups.map { (artist, files) ->
                ArtistSummary(
                    key = if (artist == null) MetadataGroupKey.Unknown else MetadataGroupKey.Known(artist),
                    displayName = artist ?: "Unknown Artist",
                    totalTrackCount = files.size,
                    availableTrackCount = files.count { it.isAvailable },
                    albumCount = files.map { it.album ?: "Unknown Album" }.distinct().size,
                    representativeArtworkUri = files.firstOrNull { it.artworkUri != null }?.artworkUri
                )
            }.sortedWith(
                compareBy<ArtistSummary> { it.key is MetadataGroupKey.Unknown }
                    .thenBy { it.displayName.lowercase() }
            ).let { summaries ->
                if (query.sortDirection == SortDirection.ASCENDING) summaries
                else summaries.filterNot { it.key is MetadataGroupKey.Unknown }.reversed() +
                    summaries.filter { it.key is MetadataGroupKey.Unknown }
            }
            PagingData.from(summaries)
        }
    }

    override fun getAlbumSummariesPaged(query: LibraryQuery): Flow<PagingData<AlbumSummary>> {
        return _mediaFiles.map {
            val filtered = filterMediaFiles(query)
            val groups = filtered.groupBy { Pair(it.album, it.albumArtist ?: it.artist) }
            val summaries = groups.map { (pair, files) ->
                val album = pair.first
                val artist = pair.second
                AlbumSummary(
                    albumKey = if (album == null) MetadataGroupKey.Unknown else MetadataGroupKey.Known(album),
                    albumDisplayName = album ?: "Unknown Album",
                    effectiveAlbumArtistKey = if (artist == null) MetadataGroupKey.Unknown else MetadataGroupKey.Known(artist),
                    effectiveAlbumArtistDisplayName = artist ?: "Unknown Artist",
                    totalTrackCount = files.size,
                    availableTrackCount = files.count { it.isAvailable },
                    minYear = files.mapNotNull { it.year }.minOrNull(),
                    representativeMediaId = files.firstOrNull()?.id,
                    representativeArtworkUri = files.firstOrNull { it.artworkUri != null }?.artworkUri
                )
            }.sortedWith(
                compareBy<AlbumSummary> { it.albumKey is MetadataGroupKey.Unknown }
                    .thenBy { it.albumDisplayName.lowercase() }
            ).let { summaries ->
                if (query.sortDirection == SortDirection.ASCENDING) summaries
                else summaries.filterNot { it.albumKey is MetadataGroupKey.Unknown }.reversed() +
                    summaries.filter { it.albumKey is MetadataGroupKey.Unknown }
            }
            PagingData.from(summaries)
        }
    }

    override fun getTracksPaged(query: LibraryQuery): Flow<PagingData<MediaFile>> {
        return _mediaFiles.map {
            val filtered = filterMediaFiles(query)
            PagingData.from(filtered)
        }
    }

    override fun getRootFolderNode(sourceId: String): Flow<FolderNode?> {
        return _folderNodes.map { nodes ->
            nodes.find { it.sourceId == sourceId && (it.parentId == null || it.relativePath.isEmpty()) }
        }
    }

    override fun getDirectChildFolders(parentId: String): Flow<List<FolderListItem>> {
        return _folderNodes.map { nodes ->
            nodes.filter { it.parentId == parentId }.map { folder ->
                val childFolderCount = _folderNodes.value.count { it.parentId == folder.id }
                val directMediaCount = _mediaFiles.value.count { it.folderId == folder.id }
                FolderListItem(folder, childFolderCount, directMediaCount)
            }.sortedWith(compareBy<FolderListItem> { it.folder.displayName.lowercase() }.thenBy { it.folder.id })
        }
    }

    override fun getFolderBreadcrumbs(folderId: String): Flow<List<FolderBreadcrumb>> {
        return _folderNodes.map { nodes ->
            val breadcrumbs = mutableListOf<FolderBreadcrumb>()
            var current = nodes.find { it.id == folderId }
            while (current != null) {
                breadcrumbs.add(0, FolderBreadcrumb(id = current.id, displayName = current.displayName))
                current = nodes.find { it.id == current?.parentId }
            }
            breadcrumbs
        }
    }

    override fun getPagedFolderMedia(folderId: String, query: LibraryQuery): Flow<PagingData<MediaFile>> {
        return getTracksPaged(query.copy(folderId = folderId))
    }

    override suspend fun resolveSelectionMediaIds(
        selectedFileIds: Set<String>,
        selectedFolderIds: Set<String>,
        availability: AvailabilityFilter
    ): SelectionResolutionResult {
        if (selectedFileIds.isEmpty() && selectedFolderIds.isEmpty()) {
            return SelectionResolutionResult(emptyList(), 0, 0)
        }
        val allSubFolderIds = mutableSetOf<String>()
        val queue = ArrayDeque<String>(selectedFolderIds)
        while (queue.isNotEmpty()) {
            val currentId = queue.removeFirst()
            allSubFolderIds.add(currentId)
            _folderNodes.value.filter { it.parentId == currentId }.forEach { queue.addLast(it.id) }
        }

        val matchingFiles = _mediaFiles.value.filter { file ->
            (selectedFileIds.contains(file.id) || allSubFolderIds.contains(file.folderId)) &&
                when (availability) {
                    AvailabilityFilter.ALL -> true
                    AvailabilityFilter.AVAILABLE_ONLY -> file.isAvailable
                    AvailabilityFilter.UNAVAILABLE_ONLY -> !file.isAvailable
                }
        }
        val uniqueIds = matchingFiles.map { it.id }.distinct().sorted()
        val total = uniqueIds.size
        val available = matchingFiles.count { it.isAvailable }
        return SelectionResolutionResult(uniqueIds, total, available)
    }

    override suspend fun snapshotVisibleMediaIds(query: LibraryQuery): List<String> {
        return filterMediaFiles(query).map { it.id }
    }

    override fun getMediaFilesFlow(
        collectionId: String?,
        folderId: String?,
        artist: String?,
        album: String?,
        searchQuery: String?,
        sortOrder: SortOrder
    ): Flow<List<MediaFile>> {
        return _mediaFiles.map { list ->
            list.filter { item ->
                (folderId == null || item.folderId == folderId) &&
                (artist == null || item.artist?.equals(artist, ignoreCase = true) == true) &&
                (album == null || item.album?.equals(album, ignoreCase = true) == true) &&
                (searchQuery.isNullOrBlank() || item.displayTitle.contains(searchQuery, ignoreCase = true) || (item.artist?.contains(searchQuery, ignoreCase = true) == true))
            }.sortedWith(getComparator(sortOrder, sortOrder.defaultDirection()))
        }
    }

    private fun getComparator(
        sortOrder: SortOrder,
        direction: SortDirection
    ): Comparator<MediaFile> {
        fun directed(value: Int): Int = if (direction == SortDirection.ASCENDING) value else -value
        fun compareText(left: String?, right: String?, selectedDirection: Boolean): Int {
            if (left == null && right == null) return 0
            if (left == null) return 1
            if (right == null) return -1
            val comparison = left.trim().lowercase().compareTo(right.trim().lowercase())
            return if (selectedDirection) directed(comparison) else comparison
        }
        fun compareLong(left: Long?, right: Long?): Int {
            if (left == null && right == null) return 0
            if (left == null) return 1
            if (right == null) return -1
            return directed(left.compareTo(right))
        }
        val finalTieBreaker = compareBy<MediaFile> {
            (it.displayTitle.ifEmpty { it.filename }).trim().lowercase()
        }.thenBy { it.id }
        val primaryComparator: Comparator<MediaFile> = when (sortOrder) {
            SortOrder.ARTIST -> Comparator<MediaFile> { left, right ->
                compareText(left.artist, right.artist, selectedDirection = true)
            }.thenBy { (it.albumArtist ?: it.artist)?.lowercase() }
                .thenBy { it.album == null }
                .thenBy { it.album?.lowercase() }
                .thenBy { it.discNumber == null }
                .thenBy { it.discNumber ?: 0 }
                .thenBy { it.trackNumber == null }
                .thenBy { it.trackNumber ?: 0 }
            SortOrder.ALBUM -> Comparator<MediaFile> { left, right ->
                compareText(left.album, right.album, selectedDirection = true)
            }
                .thenBy { (it.albumArtist ?: it.artist)?.lowercase() }
                .thenBy { it.discNumber == null }
                .thenBy { it.discNumber ?: 0 }
                .thenBy { it.trackNumber == null }
                .thenBy { it.trackNumber ?: 0 }
            SortOrder.TITLE -> Comparator<MediaFile> { left, right ->
                compareText(
                    left.displayTitle.ifEmpty { left.filename },
                    right.displayTitle.ifEmpty { right.filename },
                    selectedDirection = true
                )
            }
            SortOrder.TRACK -> compareBy<MediaFile> { it.discNumber == null }
                .thenBy { it.discNumber ?: 0 }
                .thenBy { it.trackNumber == null }
                .thenBy { it.trackNumber ?: 0 }
            SortOrder.RECENTLY_ADDED -> Comparator<MediaFile> { left, right -> directed(left.firstIndexedAt.compareTo(right.firstIndexedAt)) }
            SortOrder.MOST_PLAYED,
            SortOrder.LEAST_PLAYED -> Comparator<MediaFile> { left, right -> directed(left.playCount.compareTo(right.playCount)) }
            SortOrder.UNPLAYED -> compareBy { it.playCount != 0 }
            SortOrder.MOST_RECENT,
            SortOrder.LEAST_RECENT -> Comparator<MediaFile> { left, right -> compareLong(left.lastPlayedAt, right.lastPlayedAt) }
            SortOrder.MOST_LIKED -> Comparator<MediaFile> { left, right -> directed(left.likeScore.compareTo(right.likeScore)) }
        }
        return primaryComparator.thenComparing(finalTieBreaker)
    }

    override suspend fun getMediaFileById(id: String): MediaFile? {
        return _mediaFiles.value.find { it.id == id }
    }

    override suspend fun getMediaFilesByIdsPreservingOrder(mediaIds: List<String>): List<MediaFile> {
        val map = _mediaFiles.value.associateBy { it.id }
        return mediaIds.mapNotNull { map[it] }
    }

    override fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNode>> {
        return _folderNodes.map { nodes -> nodes.filter { it.sourceId == sourceId } }
    }

    override suspend fun updateLikeScore(mediaId: String, delta: Int): Result<Int> {
        if (delta != 1 && delta != -1) {
            return Result.failure(IllegalArgumentException("Like score delta must be +1 or -1"))
        }
        val file = _mediaFiles.value.find { it.id == mediaId }
            ?: return Result.failure(IllegalArgumentException("Media file not found: $mediaId"))
        val newScore = if (delta < 0) (file.likeScore + delta).coerceAtLeast(-1) else file.likeScore + delta
        _mediaFiles.value = _mediaFiles.value.map { item ->
            if (item.id == mediaId) {
                item.copy(likeScore = newScore)
            } else {
                item
            }
        }
        return Result.success(newScore)
    }

    override suspend fun recordPlay(mediaId: String, listenedDurationMs: Long, isMeaningful: Boolean) {
        val sessionOccurrenceId = UUID.randomUUID().toString()
        val result = if (isMeaningful) PlaybackHistoryResult.THRESHOLD_COUNTED else PlaybackHistoryResult.DISCARDED
        commitMeaningfulPlay(sessionOccurrenceId, mediaId, System.currentTimeMillis() - listenedDurationMs, System.currentTimeMillis(), listenedDurationMs, result)
    }

    override suspend fun getPlaybackHistoryByOccurrenceId(sessionOccurrenceId: String): PlaybackHistory? {
        return null
    }

    override suspend fun commitMeaningfulPlay(
        sessionOccurrenceId: String,
        mediaId: String,
        startedAt: Long,
        endedAt: Long?,
        accumulatedListenedDurationMs: Long,
        result: PlaybackHistoryResult
    ): Boolean {
        if (_historyOccurrences.contains(sessionOccurrenceId)) {
            return false
        }
        _historyOccurrences.add(sessionOccurrenceId)
        if (result == PlaybackHistoryResult.THRESHOLD_COUNTED || result == PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED) {
            _mediaFiles.value = _mediaFiles.value.map { item ->
                if (item.id == mediaId) {
                    item.copy(playCount = item.playCount + 1, lastPlayedAt = System.currentTimeMillis())
                } else item
            }
        }
        return true
    }

    override suspend fun updateMediaAvailability(mediaId: String, isAvailable: Boolean) {
        _mediaFiles.value = _mediaFiles.value.map {
            if (it.id == mediaId) it.copy(isAvailable = isAvailable) else it
        }
    }

    override suspend fun startScanRun(sourceId: String): String = UUID.randomUUID().toString()

    override suspend fun stageFolders(scanId: String, folders: List<StagedFolder>) {
        stagedFoldersByScan.getOrPut(scanId) { mutableListOf() }.addAll(folders)
    }

    override suspend fun stageMedia(scanId: String, media: List<StagedMedia>) {
        stagedMediaByScan.getOrPut(scanId) { mutableListOf() }.addAll(media)
    }

    override suspend fun publishResolvedScan(
        scanId: String,
        resolvedFolders: List<FolderNode>,
        resolvedMedia: List<MediaFile>,
        unavailableMediaIds: List<String>,
        scanResult: ScanResult
    ) {
        _folderNodes.value = resolvedFolders
        val existingMap = _mediaFiles.value.associateBy { it.id }
        val updatedMedia = resolvedMedia.map { newMedia ->
            existingMap[newMedia.id]?.let { existing ->
                newMedia.copy(
                    firstIndexedAt = existing.firstIndexedAt,
                    playCount = existing.playCount,
                    lastPlayedAt = existing.lastPlayedAt,
                    likeScore = existing.likeScore
                )
            } ?: newMedia
        }
        val unavailableSet = unavailableMediaIds.toSet()
        _mediaFiles.value = updatedMedia.map {
            if (unavailableSet.contains(it.id)) it.copy(isAvailable = false) else it
        }
    }

    override suspend fun publishStagedScan(
        scanId: String,
        sourceId: String,
        scanResult: ScanResult
    ): ScanResult {
        val stagedFolders = stagedFoldersByScan.remove(scanId).orEmpty()
        val stagedMedia = stagedMediaByScan.remove(scanId).orEmpty()
        val existingByUri = _mediaFiles.value.filter { it.sourceId == sourceId }.associateBy { it.documentUri }
        val existingByPath = _mediaFiles.value.filter { it.sourceId == sourceId }.associateBy { it.relativePath }
        val folderIds = mutableMapOf("" to UUID.nameUUIDFromBytes("$sourceId\u0000".toByteArray()).toString())
        val folders = mutableListOf(
            FolderNode(folderIds.getValue(""), sourceId, null, "", "Root")
        )
        stagedFolders.sortedBy { it.relativePath.count { char -> char == '/' } }.forEach { staged ->
            val parentPath = staged.parentRelativePath.orEmpty()
            val id = UUID.nameUUIDFromBytes("$sourceId\u0000${staged.relativePath}".toByteArray()).toString()
            folderIds[staged.relativePath] = id
            folders += FolderNode(id, sourceId, folderIds[parentPath], staged.relativePath, staged.displayName)
        }
        var added = 0
        var updated = 0
        val resolved = stagedMedia.map { staged ->
            val existing = existingByUri[staged.documentUri] ?: existingByPath[staged.relativePath]
            if (existing == null) added++ else updated++
            MediaFile(
                id = existing?.id ?: UUID.randomUUID().toString(),
                sourceId = sourceId,
                folderId = folderIds[staged.relativePath.substringBeforeLast('/', "")] ?: folderIds.getValue(""),
                documentUri = staged.documentUri,
                documentId = staged.documentId,
                relativePath = staged.relativePath,
                filename = staged.filename,
                displayTitle = staged.displayTitle,
                mimeType = staged.mimeType,
                size = staged.size,
                durationMs = staged.durationMs,
                modifiedTimeMs = staged.modifiedTimeMs,
                firstIndexedAt = existing?.firstIndexedAt ?: System.currentTimeMillis(),
                metadataScanStatus = staged.metadataScanStatus,
                title = staged.title,
                artist = staged.artist,
                albumArtist = staged.albumArtist,
                album = staged.album,
                discNumber = staged.discNumber,
                trackNumber = staged.trackNumber,
                year = staged.year,
                genre = staged.genre,
                artworkUri = existing?.artworkUri,
                titleSource = staged.titleSource,
                artistSource = staged.artistSource,
                albumArtistSource = staged.albumArtistSource,
                albumSource = staged.albumSource,
                discNumberSource = staged.discNumberSource,
                trackNumberSource = staged.trackNumberSource,
                playCount = existing?.playCount ?: 0,
                lastPlayedAt = existing?.lastPlayedAt,
                likeScore = existing?.likeScore ?: 0
            )
        }
        val resolvedIds = resolved.mapTo(mutableSetOf()) { it.id }
        val missing = _mediaFiles.value.count { it.sourceId == sourceId && it.id !in resolvedIds }
        _folderNodes.value = folders
        _mediaFiles.value = _mediaFiles.value.filterNot { it.sourceId == sourceId } + resolved +
            _mediaFiles.value.filter { it.sourceId == sourceId && it.id !in resolvedIds }.map { it.copy(isAvailable = false) }
        return scanResult.copy(
            scannedCount = stagedMedia.size,
            addedCount = added,
            updatedCount = updated,
            unavailableCount = missing
        )
    }

    override suspend fun cancelScanRun(scanId: String) {
        stagedFoldersByScan.remove(scanId)
        stagedMediaByScan.remove(scanId)
    }

    override suspend fun failScanRun(scanId: String, errorSummary: String) {
        cancelScanRun(scanId)
    }

    fun addMediaFiles(files: List<MediaFile>) {
        _mediaFiles.value = _mediaFiles.value + files
    }
}
