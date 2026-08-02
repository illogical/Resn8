package com.app.resn8.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.resn8.domain.model.AlbumSummary
import com.app.resn8.domain.model.ArtistSummary
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SelectionResolutionResult
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.UiSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val uiSessionRepository: UiSessionRepository
) : ViewModel() {

    private val _surface = MutableStateFlow(LibrarySurface.ARTISTS)
    val surface: StateFlow<LibrarySurface> = _surface.asStateFlow()

    private val _collectionId = MutableStateFlow("MUSIC")
    val collectionId: StateFlow<String> = _collectionId.asStateFlow()

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sort = MutableStateFlow(SortOrder.ARTIST)
    val sort: StateFlow<SortOrder> = _sort.asStateFlow()

    private val _filters = MutableStateFlow(LibraryFilterSnapshot())
    val filters: StateFlow<LibraryFilterSnapshot> = _filters.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    private val _selectedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderIds: StateFlow<Set<String>> = _selectedFolderIds.asStateFlow()

    private val _selectionResolution = MutableStateFlow<SelectionResolutionResult?>(null)
    val selectionResolution: StateFlow<SelectionResolutionResult?> = _selectionResolution.asStateFlow()

    init {
        viewModelScope.launch {
            val session = uiSessionRepository.getUiSessionStateFlow().first()
            _surface.value = session.activeSurface
            _collectionId.value = session.selectedCollectionId ?: "MUSIC"
            _searchText.value = session.activeSearchQuery ?: ""
            _sort.value = session.activeSort
            _filters.value = session.libraryFilterSnapshot
        }
    }

    private val debouncedSearchText: Flow<String> = _searchText
        .debounce(250L)
        .distinctUntilChanged()

    val currentQuery: Flow<LibraryQuery> = combine(
        _collectionId,
        _surface,
        debouncedSearchText,
        _sort,
        _filters
    ) { collId, surf, search, sortOrder, filterSnapshot ->
        LibraryQuery(
            collectionId = collId,
            searchText = search,
            sort = sortOrder,
            filters = filterSnapshot
        )
    }.distinctUntilChanged()

    val artistSummariesPaged: Flow<PagingData<ArtistSummary>> = currentQuery.flatMapLatest { query ->
        mediaRepository.getArtistSummariesPaged(query)
    }.cachedIn(viewModelScope)

    val albumSummariesPaged: Flow<PagingData<AlbumSummary>> = currentQuery.flatMapLatest { query ->
        mediaRepository.getAlbumSummariesPaged(query)
    }.cachedIn(viewModelScope)

    val tracksPaged: Flow<PagingData<MediaFile>> = currentQuery.flatMapLatest { query ->
        mediaRepository.getTracksPaged(query)
    }.cachedIn(viewModelScope)

    fun setSurface(surface: LibrarySurface) {
        _surface.value = surface
        saveSessionState()
    }

    fun setSearchText(text: String) {
        _searchText.value = text
        saveSessionState()
    }

    fun setSortOrder(sortOrder: SortOrder) {
        _sort.value = sortOrder
        saveSessionState()
    }

    fun setAvailabilityFilter(availability: AvailabilityFilter) {
        _filters.value = _filters.value.copy(availability = availability)
        saveSessionState()
    }

    fun toggleExcludeDisliked() {
        _filters.value = _filters.value.copy(excludeDisliked = !_filters.value.excludeDisliked)
        saveSessionState()
    }

    fun toggleFileSelection(fileId: String) {
        val current = _selectedFileIds.value.toMutableSet()
        if (current.contains(fileId)) current.remove(fileId) else current.add(fileId)
        _selectedFileIds.value = current
        updateSelectionResolution()
    }

    fun toggleFolderSelection(folderId: String) {
        val current = _selectedFolderIds.value.toMutableSet()
        if (current.contains(folderId)) current.remove(folderId) else current.add(folderId)
        _selectedFolderIds.value = current
        updateSelectionResolution()
    }

    fun clearSelection() {
        _selectedFileIds.value = emptySet()
        _selectedFolderIds.value = emptySet()
        _selectionResolution.value = null
    }

    private fun updateSelectionResolution() {
        viewModelScope.launch {
            _selectionResolution.value = mediaRepository.resolveSelectionMediaIds(
                selectedFileIds = _selectedFileIds.value,
                selectedFolderIds = _selectedFolderIds.value,
                availability = _filters.value.availability
            )
        }
    }

    private fun saveSessionState() {
        viewModelScope.launch {
            val session = uiSessionRepository.getUiSessionStateFlow().first()
            uiSessionRepository.saveUiSessionState(
                session.copy(
                    activeSurface = _surface.value,
                    selectedCollectionId = _collectionId.value,
                    activeSearchQuery = _searchText.value.ifBlank { null },
                    activeSort = _sort.value,
                    libraryFilterSnapshot = _filters.value
                )
            )
        }
    }
}
