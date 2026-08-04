package com.app.resn8.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.storage.indexer.IndexingWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class SettingsUiState(
    val collections: List<Collection> = emptyList(),
    val activeCollection: Collection? = null,
    val activeSource: RootSource? = null,
    val isIndexing: Boolean = false,
    val indexingProgress: ScanProgress? = null,
    val errorMessage: String? = null
)

class SettingsViewModel(
    private val context: Context,
    private val appContainer: AppContainer
) : ViewModel() {

    private val workManager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var workObservation: Job? = null

    init {
        loadSettings()
    }

    fun loadSettings() {
        viewModelScope.launch {
            combine(
                appContainer.collectionRepository.getCollectionsFlow(),
                appContainer.uiSessionRepository.getUiSessionStateFlow()
            ) { collections, session -> collections to session }.collectLatest { (collections, session) ->
                val collection = collections.firstOrNull { it.id == session.selectedCollectionId }
                    ?: collections.singleOrNull()
                    ?: return@collectLatest
                val rootSources = appContainer.collectionRepository.getRootSourcesFlow(collection.id).firstOrNull().orEmpty()
                val activeRoot = rootSources.firstOrNull { it.id == session.selectedSourceId } ?: rootSources.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    collections = collections,
                    activeCollection = collection,
                    activeSource = activeRoot
                )

                if (activeRoot != null) {
                    observeWork(activeRoot.id)
                }
            }
        }
    }

    fun createCollection(name: String, profile: CollectionProfile, uri: Uri, onCreated: (CollectionProfile) -> Unit) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val (collection, source) = appContainer.collectionRepository.createCollectionWithSource(
                    name = name,
                    profile = profile,
                    treeUri = uri.toString(),
                    displayName = name.trim()
                )
                appContainer.playbackConnection?.stopForCollectionSwitch()
                val current = appContainer.uiSessionRepository.getUiSessionStateFlow().firstOrNull()
                    ?: com.app.resn8.domain.model.UiSessionState()
                appContainer.uiSessionRepository.saveUiSessionState(
                    current.copy(
                        currentRoute = if (profile == CollectionProfile.FLAT) "folders" else "library",
                        selectedCollectionId = collection.id,
                        selectedSourceId = source.id,
                        selectedFolderId = null,
                        selectedArtistKey = null,
                        selectedAlbumKey = null,
                        selectedAlbumArtistKey = null,
                        selectedPlaylistId = null,
                        activeQueueId = null,
                        activeSearchQuery = null,
                        activeSort = if (profile == CollectionProfile.FLAT) SortOrder.TITLE else SortOrder.ARTIST,
                        activeSurface = if (profile == CollectionProfile.FLAT) LibrarySurface.FOLDERS else LibrarySurface.ARTISTS,
                        libraryFilterSnapshot = com.app.resn8.domain.model.LibraryFilterSnapshot()
                    )
                )
                IndexingWorker.enqueue(context, source.id, source.treeUri)
                observeWork(source.id)
                _uiState.value = _uiState.value.copy(errorMessage = null)
                onCreated(profile)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "create_collection_failed category=${e::class.simpleName}")
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Unable to create collection")
            }
        }
    }

    fun renameActiveCollection(name: String) {
        val collection = _uiState.value.activeCollection ?: return
        viewModelScope.launch {
            try {
                appContainer.collectionRepository.renameCollection(collection.id, name)
                _uiState.value = _uiState.value.copy(errorMessage = null)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Unable to rename collection")
            }
        }
    }

    fun reindexSource() {
        val root = _uiState.value.activeSource ?: return
        viewModelScope.launch {
            try {
                IndexingWorker.enqueue(context, root.id, root.treeUri)
                observeWork(root.id)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "reindex_failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Unable to start re-indexing")
            }
        }
    }

    fun onFolderReselected(uri: Uri) {
        val root = _uiState.value.activeSource ?: return
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val updated = appContainer.collectionRepository.reselectRootSource(root.id, uri.toString())
                IndexingWorker.enqueue(context, updated.id, updated.treeUri)
                observeWork(root.id)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "reselect_permission_failed", e)
                _uiState.value = _uiState.value.copy(errorMessage = "Permission reselection failed")
            }
        }
    }

    private fun observeWork(sourceId: String) {
        workObservation?.cancel()
        workObservation = viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(IndexingWorker.uniqueWorkName(sourceId)).collect { infos ->
                val info = infos.maxByOrNull { it.runAttemptCount } ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                        val data = info.progress
                        val progress = ScanProgress(
                            processedFiles = data.getInt(IndexingWorker.KEY_AUDIO, 0),
                            totalFiles = 0,
                            currentStep = "Indexing collection",
                            scanId = info.id.toString(),
                            phase = data.getString(IndexingWorker.KEY_PHASE) ?: "SCANNING"
                        )
                        _uiState.value = _uiState.value.copy(isIndexing = true, indexingProgress = progress)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        _uiState.value = _uiState.value.copy(isIndexing = false, indexingProgress = null)
                    }
                    WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> {
                        _uiState.value = _uiState.value.copy(isIndexing = false, indexingProgress = null)
                    }
                }
            }
        }
    }

    class Factory(
        private val context: Context,
        private val appContainer: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            SettingsViewModel(context, appContainer) as T
    }

    companion object {
        private const val LOG_TAG = "Resn8Settings"
    }
}
