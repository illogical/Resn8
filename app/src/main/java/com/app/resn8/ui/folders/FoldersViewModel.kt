package com.app.resn8.ui.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.FolderBreadcrumb
import com.app.resn8.domain.model.FolderListItem
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SelectionResolutionResult
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.UiSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModel(
    private val collectionId: String,
    val collectionName: String,
    val collectionProfile: CollectionProfile,
    private val initialSourceId: String?,
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val initialFolderId: String? = null,
    private val uiSessionRepository: UiSessionRepository? = null
) : ViewModel() {

    private val _sourceId = MutableStateFlow<String?>(null)
    val sourceId: StateFlow<String?> = _sourceId.asStateFlow()

    private val _currentFolderId = MutableStateFlow<String?>(null)
    val currentFolderId: StateFlow<String?> = _currentFolderId.asStateFlow()

    private val _selectedFileIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFileIds: StateFlow<Set<String>> = _selectedFileIds.asStateFlow()

    private val _selectedFolderIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedFolderIds: StateFlow<Set<String>> = _selectedFolderIds.asStateFlow()

    private val _selectionResolution = MutableStateFlow<SelectionResolutionResult?>(null)
    val selectionResolution: StateFlow<SelectionResolutionResult?> = _selectionResolution.asStateFlow()
    private val _directAvailableMediaIds = MutableStateFlow<Set<String>>(emptySet())
    private val _allDirectFilesSelected = MutableStateFlow(false)
    val allDirectFilesSelected: StateFlow<Boolean> = _allDirectFilesSelected.asStateFlow()

    init {
        viewModelScope.launch {
            val source = initialSourceId
                ?: collectionRepository.getRootSourcesFlow(collectionId).first().firstOrNull()?.id
            _sourceId.value = source
            if (source != null) {
                val restoredFolderId = initialFolderId?.takeIf { requestedId ->
                    mediaRepository.getFolderNodesFlow(source).first().any { it.id == requestedId }
                }
                if (restoredFolderId != null) {
                    _currentFolderId.value = restoredFolderId
                    refreshDirectAvailableIds(restoredFolderId)
                    return@launch
                }
                mediaRepository.getRootFolderNode(source).collect { rootFolder ->
                    if (rootFolder != null && _currentFolderId.value == null) {
                        _currentFolderId.value = rootFolder.id
                        refreshDirectAvailableIds(rootFolder.id)
                    }
                }
            }
        }
    }

    val breadcrumbs: Flow<List<FolderBreadcrumb>> = _currentFolderId.flatMapLatest { folderId ->
        if (folderId == null) flowOf(emptyList()) else mediaRepository.getFolderBreadcrumbs(folderId)
    }.map { crumbs ->
        crumbs.mapIndexed { index, crumb -> if (index == 0) crumb.copy(displayName = collectionName) else crumb }
    }

    val childFolders: Flow<List<FolderListItem>> = _currentFolderId.flatMapLatest { folderId ->
        if (folderId == null) flowOf(emptyList()) else mediaRepository.getDirectChildFolders(folderId)
    }

    val folderMediaPaged: Flow<PagingData<MediaFile>> = _currentFolderId.flatMapLatest { folderId ->
        if (folderId == null) flowOf(PagingData.empty()) else mediaRepository.getPagedFolderMedia(
            folderId = folderId,
            query = LibraryQuery(
                collectionId = collectionId,
                sourceId = _sourceId.value,
                folderId = folderId,
                sort = SortOrder.TITLE,
                filters = LibraryFilterSnapshot(availability = AvailabilityFilter.ALL)
            )
        )
    }.cachedIn(viewModelScope)

    fun navigateToFolder(folderId: String) {
        clearSelection()
        _currentFolderId.value = folderId
        refreshDirectAvailableIds(folderId)
        val sessionRepository = uiSessionRepository ?: return
        viewModelScope.launch {
            val current = sessionRepository.getUiSessionStateFlow().first()
            sessionRepository.saveUiSessionState(
                current.copy(
                    currentRoute = "folders",
                    selectedCollectionId = collectionId,
                    selectedSourceId = _sourceId.value,
                    selectedFolderId = folderId,
                    selectedArtistKey = null,
                    selectedAlbumKey = null,
                    selectedAlbumArtistKey = null
                )
            )
        }
    }

    fun toggleFileSelection(fileId: String) {
        val current = _selectedFileIds.value.toMutableSet()
        if (current.contains(fileId)) current.remove(fileId) else current.add(fileId)
        _selectedFileIds.value = current
        updateAllDirectFilesSelected()
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
        _allDirectFilesSelected.value = false
    }

    fun toggleSelectAllDirectAvailable() {
        val folderId = _currentFolderId.value ?: return
        viewModelScope.launch {
            val ids = mediaRepository.snapshotVisibleMediaIds(
                LibraryQuery(
                    collectionId = collectionId,
                    sourceId = _sourceId.value,
                    folderId = folderId,
                    sort = SortOrder.TITLE,
                    filters = LibraryFilterSnapshot(availability = AvailabilityFilter.AVAILABLE_ONLY)
                )
            ).toSet()
            _directAvailableMediaIds.value = ids
            _selectedFileIds.value = if (ids.isNotEmpty() && ids.all(_selectedFileIds.value::contains)) {
                _selectedFileIds.value - ids
            } else {
                _selectedFileIds.value + ids
            }
            updateAllDirectFilesSelected()
            updateSelectionResolution()
        }
    }

    private fun refreshDirectAvailableIds(folderId: String) {
        viewModelScope.launch {
            _directAvailableMediaIds.value = mediaRepository.snapshotVisibleMediaIds(
                LibraryQuery(
                    collectionId = collectionId,
                    sourceId = _sourceId.value,
                    folderId = folderId,
                    sort = SortOrder.TITLE,
                    filters = LibraryFilterSnapshot(availability = AvailabilityFilter.AVAILABLE_ONLY)
                )
            ).toSet()
            updateAllDirectFilesSelected()
        }
    }

    private fun updateAllDirectFilesSelected() {
        val ids = _directAvailableMediaIds.value
        _allDirectFilesSelected.value = ids.isNotEmpty() && ids.all(_selectedFileIds.value::contains)
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
}
