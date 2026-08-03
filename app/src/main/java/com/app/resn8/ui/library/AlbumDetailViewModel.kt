package com.app.resn8.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow

class AlbumDetailViewModel(
    val collectionId: String,
    val albumKey: MetadataGroupKey,
    private val mediaRepository: MediaRepository
) : ViewModel() {

    val albumTitle: String = when (albumKey) {
        is MetadataGroupKey.Known -> albumKey.value
        is MetadataGroupKey.Unknown -> "Unknown Album"
    }

    val tracksPaged: Flow<PagingData<MediaFile>> = mediaRepository.getTracksPaged(
        LibraryQuery(
            collectionId = collectionId,
            album = albumKey,
            sort = SortOrder.TRACK
        )
    ).cachedIn(viewModelScope)

    suspend fun getAllAlbumMediaIds(): List<String> {
        return mediaRepository.snapshotVisibleMediaIds(
            LibraryQuery(
                collectionId = collectionId,
                album = albumKey,
                sort = SortOrder.TRACK
            )
        )
    }
}
