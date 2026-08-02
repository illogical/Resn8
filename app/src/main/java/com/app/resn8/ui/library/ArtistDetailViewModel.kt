package com.app.resn8.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.resn8.domain.model.AlbumSummary
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.repository.MediaRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest

@OptIn(ExperimentalCoroutinesApi::class)
class ArtistDetailViewModel(
    val collectionId: String,
    val artistKey: MetadataGroupKey,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    private val _sort = MutableStateFlow(SortOrder.TRACK)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    private val _filters = MutableStateFlow(LibraryFilterSnapshot())
    val filters: StateFlow<LibraryFilterSnapshot> = _filters.asStateFlow()

    val artistName: String = when (artistKey) {
        is MetadataGroupKey.Known -> artistKey.value
        is MetadataGroupKey.Unknown -> "Unknown Artist"
    }

    val albumsPaged: Flow<PagingData<AlbumSummary>> = mediaRepository.getAlbumSummariesPaged(
        LibraryQuery(
            collectionId = collectionId,
            artist = artistKey,
            sort = SortOrder.ALBUM
        )
    ).cachedIn(viewModelScope)

    val tracksPaged: Flow<PagingData<MediaFile>> = _sort.flatMapLatest { sortOrder ->
        mediaRepository.getTracksPaged(
            LibraryQuery(
                collectionId = collectionId,
                artist = artistKey,
                sort = sortOrder,
                filters = _filters.value
            )
        )
    }.cachedIn(viewModelScope)

    fun setSortOrder(sortOrder: SortOrder) {
        _sort.value = sortOrder
    }
}
