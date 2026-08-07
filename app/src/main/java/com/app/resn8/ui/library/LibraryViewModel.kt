package com.app.resn8.ui.library

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.resn8.domain.model.AlbumSummary
import com.app.resn8.domain.model.ArtistSummary
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.LibrarySortField
import com.app.resn8.domain.model.LibrarySortPreferences
import com.app.resn8.domain.model.LibrarySortSelection
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SelectionResolutionResult
import com.app.resn8.domain.model.SortDirection
import com.app.resn8.domain.model.toLegacySortOrder
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.UiSessionRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

import kotlinx.coroutines.flow.first

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class LibraryViewModel(
    collectionId: String,
    private val sourceId: String?,
    private val mediaRepository: MediaRepository,
    private val uiSessionRepository: UiSessionRepository,
    private val initialSurface: LibrarySurface? = null
) : ViewModel() {

    private val _surface = MutableStateFlow(LibrarySurface.ARTISTS)
    val surface: StateFlow<LibrarySurface> = _surface.asStateFlow()

    private val _collectionId = MutableStateFlow(collectionId)
    val collectionId: StateFlow<String> = _collectionId.asStateFlow()

    private val _sessionError = MutableStateFlow<String?>(null)
    val sessionError: StateFlow<String?> = _sessionError.asStateFlow()

    private val sessionSaveSignals = Channel<Unit>(Channel.CONFLATED)

    private val _searchText = MutableStateFlow("")
    val searchText: StateFlow<String> = _searchText.asStateFlow()

    private val _sortPreferences = MutableStateFlow(LibrarySortPreferences())
    val sortPreferences: StateFlow<LibrarySortPreferences> = _sortPreferences.asStateFlow()

    val sort: StateFlow<LibrarySortSelection> = combine(_surface, _sortPreferences) { surface, preferences ->
        preferences.selectionFor(surface)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, LibrarySortSelection())

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    private val _selectedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderIds: StateFlow<Set<String>> = _selectedFolderIds.asStateFlow()

    private val _selectionResolution = MutableStateFlow<SelectionResolutionResult?>(null)
    val selectionResolution: StateFlow<SelectionResolutionResult?> = _selectionResolution.asStateFlow()

    init {
        viewModelScope.launch {
            val session = uiSessionRepository.getUiSessionStateFlow().first()
            _surface.value = initialSurface ?: session.activeSurface
            _searchText.value = session.activeSearchQuery ?: ""
            _sortPreferences.value = session.librarySortPreferences

            for (signal in sessionSaveSignals) {
                persistCurrentSession()
            }
        }
    }

    private val debouncedSearchText: Flow<String> = _searchText
        .debounce(250L)
        .distinctUntilChanged()

    val currentQuery: Flow<LibraryQuery> = combine(
        _collectionId,
        _surface,
        debouncedSearchText,
        sort
    ) { collId, surf, search, sortSelection ->
        LibraryQuery(
            collectionId = collId,
            searchText = search,
            sort = sortSelection.toLegacySortOrder(),
            sortDirection = sortSelection.direction,
            filters = LibraryFilterSnapshot()
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
        if (surface != LibrarySurface.ALL_TRACKS) clearSelection()
        _surface.value = surface
        saveSessionState()
    }

    fun setSearchText(text: String) {
        _searchText.value = text
        saveSessionState()
    }

    fun setSortField(field: LibrarySortField) {
        val current = _sortPreferences.value.selectionFor(_surface.value)
        _sortPreferences.value = _sortPreferences.value.withSelection(
            _surface.value,
            current.copy(field = field)
        )
        saveSessionState()
    }

    fun setSortDirection(direction: SortDirection) {
        val current = _sortPreferences.value.selectionFor(_surface.value)
        _sortPreferences.value = _sortPreferences.value.withSelection(
            _surface.value,
            current.copy(direction = direction)
        )
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
                availability = AvailabilityFilter.ALL
            )
        }
    }

    private fun saveSessionState() {
        sessionSaveSignals.trySend(Unit)
    }

    private suspend fun persistCurrentSession() {
        try {
            val session = uiSessionRepository.getUiSessionStateFlow().first()
            uiSessionRepository.saveUiSessionState(
                session.copy(
                    activeSurface = _surface.value,
                    selectedCollectionId = _collectionId.value,
                    selectedSourceId = sourceId,
                    activeSearchQuery = _searchText.value.ifBlank { null },
                    activeSort = _sortPreferences.value.selectionFor(_surface.value).toLegacySortOrder(),
                    librarySortPreferences = _sortPreferences.value,
                    libraryFilterSnapshot = LibraryFilterSnapshot()
                )
            )
            _sessionError.value = null
        } catch (error: Exception) {
            _sessionError.value = "Library preferences could not be saved"
            Log.e(LOG_TAG, "library_session_save_failed category=${error::class.simpleName}")
        }
    }

    companion object {
        private const val LOG_TAG = "Resn8Library"
    }
}
