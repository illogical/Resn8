package com.app.resn8.data.repository

import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.SortOrder
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
            }.sortedWith(getComparator(sortOrder))
        }
    }

    private fun getComparator(sortOrder: SortOrder): Comparator<MediaFile> {
        return when (sortOrder) {
            SortOrder.ARTIST -> compareBy<MediaFile> { it.artist ?: "Unknown Artist" }.thenBy { it.album ?: "Unknown Album" }.thenBy { it.trackNumber ?: 0 }
            SortOrder.ALBUM -> compareBy<MediaFile> { it.album ?: "Unknown Album" }.thenBy { it.discNumber ?: 0 }.thenBy { it.trackNumber ?: 0 }
            SortOrder.TITLE -> compareBy { it.displayTitle }
            SortOrder.TRACK -> compareBy<MediaFile> { it.discNumber ?: 0 }.thenBy { it.trackNumber ?: 0 }.thenBy { it.displayTitle }
            SortOrder.MOST_PLAYED -> compareByDescending { it.playCount }
            SortOrder.LEAST_PLAYED -> compareBy { it.playCount }
            SortOrder.UNPLAYED -> compareBy { it.playCount > 0 }
            SortOrder.MOST_RECENT -> compareByDescending { it.lastPlayedAt ?: 0L }
            SortOrder.LEAST_RECENT -> compareBy { it.lastPlayedAt ?: Long.MAX_VALUE }
            SortOrder.MOST_LIKED -> compareByDescending { it.likeScore }
        }
    }

    override suspend fun getMediaFileById(id: String): MediaFile? {
        return _mediaFiles.value.find { it.id == id }
    }

    override fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNode>> {
        return _folderNodes.map { nodes -> nodes.filter { it.sourceId == sourceId } }
    }

    override suspend fun updateLikeScore(mediaId: String, delta: Int) {
        require(delta == 1 || delta == -1) { "Like score delta must be +1 or -1" }
        _mediaFiles.value = _mediaFiles.value.map { item ->
            if (item.id == mediaId) {
                item.copy(likeScore = item.likeScore + delta)
            } else {
                item
            }
        }
    }

    override suspend fun recordPlay(mediaId: String, listenedDurationMs: Long, isMeaningful: Boolean) {
        val sessionOccurrenceId = UUID.randomUUID().toString()
        val result = if (isMeaningful) PlaybackHistoryResult.THRESHOLD_COUNTED else PlaybackHistoryResult.DISCARDED
        commitMeaningfulPlay(sessionOccurrenceId, mediaId, System.currentTimeMillis() - listenedDurationMs, System.currentTimeMillis(), listenedDurationMs, result)
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

    override suspend fun stageFolders(scanId: String, folders: List<StagedFolder>) {}

    override suspend fun stageMedia(scanId: String, media: List<StagedMedia>) {}

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

    override suspend fun cancelScanRun(scanId: String) {}

    override suspend fun failScanRun(scanId: String, errorSummary: String) {}

    fun addMediaFiles(files: List<MediaFile>) {
        _mediaFiles.value = _mediaFiles.value + files
    }
}
