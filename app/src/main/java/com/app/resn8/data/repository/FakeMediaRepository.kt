package com.app.resn8.data.repository

import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMediaRepository(
    initialMediaFiles: List<MediaFile> = emptyList(),
    initialFolderNodes: List<FolderNode> = emptyList()
) : MediaRepository {

    private val _mediaFiles = MutableStateFlow(initialMediaFiles)
    private val _folderNodes = MutableStateFlow(initialFolderNodes)

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

    private fun String?.isNull_or_blank(): Boolean = this.isNullOrBlank()

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

    override suspend fun getFolderNodesFlow(sourceId: String): Flow<List<FolderNode>> {
        return _folderNodes.map { nodes -> nodes.filter { it.sourceId == sourceId } }
    }

    override suspend fun updateLikeScore(mediaId: String, delta: Int) {
        _mediaFiles.value = _mediaFiles.value.map { item ->
            if (item.id == mediaId) {
                item.copy(likeScore = item.likeScore + delta)
            } else {
                item
            }
        }
    }

    override suspend fun recordPlay(mediaId: String, listenedDurationMs: Long, isMeaningful: Boolean) {
        _mediaFiles.value = _mediaFiles.value.map { item ->
            if (item.id == mediaId) {
                item.copy(
                    playCount = if (isMeaningful) item.playCount + 1 else item.playCount,
                    lastPlayedAt = System.currentTimeMillis()
                )
            } else {
                item
            }
        }
    }

    fun addMediaFiles(files: List<MediaFile>) {
        _mediaFiles.value = _mediaFiles.value + files
    }
}
