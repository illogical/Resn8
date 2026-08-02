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
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.UiSessionRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class FoldersViewModel(
    private val mediaRepository: MediaRepository,
    private val collectionRepository: CollectionRepository,
    private val uiSessionRepository: UiSessionRepository
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

    init {
        viewModelScope.launch {
            collectionRepository.getRootSourcesFlow("MUSIC").collect { sources ->
                val srcId = sources.firstOrNull()?.id
                _sourceId.value = srcId
                if (srcId != null && _currentFolderId.value == null) {
                    mediaRepository.getRootFolderNode(srcId).collect { rootFolder ->
                        if (rootFolder != null && _currentFolderId.value == null) {
                            _currentFolderId.value = rootFolder.id
                        }
                    }
                }
            }
        }
    }

    val breadcrumbs: Flow<List<FolderBreadcrumb>> = _currentFolderId.flatMapLatest { folderId ->
        if (folderId == null) flowOf(emptyList()) else mediaRepository.getFolderBreadcrumbs(folderId)
    }

    val childFolders: Flow<List<FolderListItem>> = _currentFolderId.flatMapLatest { folderId ->
        if (folderId == null) flowOf(emptyList()) else mediaRepository.getDirectChildFolders(folderId)
    }

    val folderMediaPaged: Flow<PagingData<MediaFile>> = _currentFolderId.flatMapLatest { folderId ->
        if (folderId == null) flowOf(PagingData.empty()) else mediaRepository.getPagedFolderMedia(
            folderId = folderId,
            query = LibraryQuery(
                collectionId = "MUSIC",
                sourceId = _sourceId.value,
                folderId = folderId,
                sort = SortOrder.TITLE,
                filters = LibraryFilterSnapshot(availability = AvailabilityFilter.ALL)
            )
        )
    }.cachedIn(viewModelScope)

    fun navigateToFolder(folderId: String) {
        _currentFolderId.value = folderId
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
}
