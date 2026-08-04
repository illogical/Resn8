package com.app.resn8.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class AlbumDetailViewModel(
    val collectionId: String,
    val albumKey: MetadataGroupKey,
    val albumArtistKey: MetadataGroupKey?,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()
    private val _allAvailableSelected = MutableStateFlow(false)
    val allAvailableSelected: StateFlow<Boolean> = _allAvailableSelected.asStateFlow()

    val albumTitle: String = when (albumKey) {
        is MetadataGroupKey.Known -> albumKey.value
        is MetadataGroupKey.Unknown -> "Unknown Album"
    }

    val tracksPaged: Flow<PagingData<MediaFile>> = mediaRepository.getTracksPaged(
        LibraryQuery(
            collectionId = collectionId,
            album = albumKey,
            albumArtist = albumArtistKey,
            sort = SortOrder.TRACK
        )
    ).cachedIn(viewModelScope)

    suspend fun getAllAlbumMediaIds(): List<String> {
        return mediaRepository.snapshotVisibleMediaIds(
            LibraryQuery(
                collectionId = collectionId,
                album = albumKey,
                albumArtist = albumArtistKey,
                sort = SortOrder.TRACK,
                filters = LibraryFilterSnapshot(availability = AvailabilityFilter.AVAILABLE_ONLY)
            )
        )
    }

    suspend fun getSelectedMediaIdsInAlbumOrder(): List<String> =
        getAllAlbumMediaIds().filter(_selectedFileIds.value::contains)

    fun toggleFileSelection(mediaId: String) {
        _selectedFileIds.value = if (mediaId in _selectedFileIds.value) {
            _selectedFileIds.value - mediaId
        } else {
            _selectedFileIds.value + mediaId
        }
        refreshAllSelected()
    }

    fun toggleSelectAll() {
        viewModelScope.launch {
            val ids = getAllAlbumMediaIds().toSet()
            _selectedFileIds.value = if (ids.isNotEmpty() && ids.all(_selectedFileIds.value::contains)) {
                _selectedFileIds.value - ids
            } else {
                _selectedFileIds.value + ids
            }
            _allAvailableSelected.value = ids.isNotEmpty() && ids.all(_selectedFileIds.value::contains)
        }
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
        _allAvailableSelected.value = false
    }

    private fun refreshAllSelected() {
        viewModelScope.launch {
            val ids = getAllAlbumMediaIds()
            _allAvailableSelected.value = ids.isNotEmpty() && ids.all(_selectedFileIds.value::contains)
        }
    }
}
