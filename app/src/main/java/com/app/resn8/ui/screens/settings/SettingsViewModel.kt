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
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.CollectionSummary
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.model.restorableQueueIdForCollection
import com.app.resn8.storage.indexer.IndexingWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class SettingsUiState(
    val collectionSummaries: List<CollectionSummary> = emptyList(),
    val detailCollection: Collection? = null,
    val detailSource: RootSource? = null,
    val isIndexing: Boolean = false,
    val indexingProgress: ScanProgress? = null,
    val errorMessage: String? = null
)

data class CollectionDeletionResult(
    val hasCollections: Boolean,
    val nextCollectionProfile: CollectionProfile? = null,
    val restoredQueue: Boolean = false
)

class SettingsViewModel(
    private val context: Context,
    private val appContainer: AppContainer
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()
    private var sourceObservation: Job? = null
    private var workObservation: Job? = null

    init {
        viewModelScope.launch {
            appContainer.collectionRepository.getCollectionSummariesFlow().collect { summaries ->
                val detailId = _uiState.value.detailCollection?.id
                _uiState.value = _uiState.value.copy(
                    collectionSummaries = summaries,
                    detailCollection = summaries.firstOrNull { it.collection.id == detailId }?.collection
                        ?: _uiState.value.detailCollection?.takeIf { detail -> summaries.any { it.collection.id == detail.id } }
                )
            }
        }
    }

    fun openCollection(collectionId: String) {
        sourceObservation?.cancel()
        sourceObservation = viewModelScope.launch {
            val collection = appContainer.collectionRepository.getCollectionById(collectionId)
            if (collection == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Collection no longer exists")
                return@launch
            }
            _uiState.value = _uiState.value.copy(detailCollection = collection, errorMessage = null)
            appContainer.collectionRepository.getRootSourcesFlow(collectionId).collect { sources ->
                val source = sources.singleOrNull() ?: sources.firstOrNull()
                _uiState.value = _uiState.value.copy(detailSource = source)
                if (source != null) observeWork(source.id)
            }
        }
    }

    fun clearDetail() {
        sourceObservation?.cancel()
        workObservation?.cancel()
        _uiState.value = _uiState.value.copy(
            detailCollection = null,
            detailSource = null,
            isIndexing = false,
            indexingProgress = null,
            errorMessage = null
        )
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    fun createCollection(
        name: String,
        profile: CollectionProfile,
        uri: Uri,
        onCreated: (Collection) -> Unit
    ) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val displayName = uri.lastPathSegment?.substringAfterLast(':')?.takeIf { it.isNotBlank() }
                    ?: name.trim()
                val (collection, source) = appContainer.collectionRepository.createCollectionWithSource(
                    name = name,
                    profile = profile,
                    treeUri = uri.toString(),
                    displayName = displayName
                )
                appContainer.playbackConnection?.checkpointAndStopForCollectionSwitch()
                appContainer.collectionRepository.setCollectionActiveQueue(collection.id, null)
                appContainer.uiSessionRepository.saveUiSessionState(
                    profileHomeSession(collection, source)
                )
                IndexingWorker.enqueue(context, source.id, source.treeUri)
                openCollection(collection.id)
                _uiState.value = _uiState.value.copy(errorMessage = null)
                onCreated(collection)
            } catch (error: Exception) {
                Log.e(LOG_TAG, "create_collection_failed category=${error::class.simpleName}")
                _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to create collection")
            }
        }
    }

    fun renameCollection(collectionId: String, name: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                val updated = appContainer.collectionRepository.renameCollection(collectionId, name)
                _uiState.value = _uiState.value.copy(detailCollection = updated, errorMessage = null)
                onSuccess()
            } catch (error: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to rename collection")
            }
        }
    }

    fun reindexCollection(collectionId: String) {
        viewModelScope.launch {
            val source = appContainer.collectionRepository.getRootSourcesFlow(collectionId).firstOrNull()?.firstOrNull()
            if (source == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Collection folder is not configured")
                return@launch
            }
            try {
                IndexingWorker.enqueue(context, source.id, source.treeUri)
                observeWork(source.id)
            } catch (error: Exception) {
                Log.e(LOG_TAG, "reindex_failed", error)
                _uiState.value = _uiState.value.copy(errorMessage = "Unable to start re-indexing")
            }
        }
    }

    fun reselectCollectionFolder(collectionId: String, uri: Uri) {
        viewModelScope.launch {
            val source = appContainer.collectionRepository.getRootSourcesFlow(collectionId).firstOrNull()?.firstOrNull()
            if (source == null) {
                _uiState.value = _uiState.value.copy(errorMessage = "Collection folder is not configured")
                return@launch
            }
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val updated = appContainer.collectionRepository.reselectRootSource(source.id, uri.toString())
                IndexingWorker.enqueue(context, updated.id, updated.treeUri)
                observeWork(updated.id)
                _uiState.value = _uiState.value.copy(errorMessage = null)
            } catch (error: Exception) {
                Log.e(LOG_TAG, "reselect_permission_failed", error)
                _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Permission reselection failed")
            }
        }
    }

    fun deleteCollection(collectionId: String, onDeleted: (CollectionDeletionResult) -> Unit) {
        viewModelScope.launch {
            try {
                val source = appContainer.collectionRepository.getRootSourcesFlow(collectionId).firstOrNull()?.firstOrNull()
                source?.let { workManager.cancelUniqueWork(IndexingWorker.uniqueWorkName(it.id)) }
                val session = appContainer.uiSessionRepository.getUiSessionStateFlow().firstOrNull() ?: UiSessionState()
                val deletingActive = session.selectedCollectionId == collectionId
                if (deletingActive) appContainer.playbackConnection?.checkpointAndStopForCollectionSwitch()

                appContainer.collectionRepository.deleteCollection(collectionId)
                if (source != null) {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            Uri.parse(source.treeUri),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                    }
                }

                val remaining = appContainer.collectionRepository.getCollectionsFlow().firstOrNull().orEmpty()
                if (remaining.isEmpty()) {
                    appContainer.uiSessionRepository.saveUiSessionState(UiSessionState())
                    onDeleted(CollectionDeletionResult(hasCollections = false))
                    return@launch
                }

                if (!deletingActive) {
                    onDeleted(CollectionDeletionResult(hasCollections = true))
                    return@launch
                }

                val next = remaining.first()
                val nextSource = appContainer.collectionRepository.getRootSourcesFlow(next.id).firstOrNull()?.firstOrNull()
                val savedQueueId = appContainer.collectionRepository.getCollectionPlaybackState(next.id)?.activeQueueId
                val queue = savedQueueId?.let { appContainer.queueRepository.getQueueByIdFlow(it).firstOrNull() }
                val restorableQueueId = restorableQueueIdForCollection(next.id, savedQueueId, queue)
                if (savedQueueId != null && restorableQueueId == null) {
                    appContainer.collectionRepository.setCollectionActiveQueue(next.id, null)
                }
                appContainer.uiSessionRepository.saveUiSessionState(
                    profileHomeSession(next, nextSource).copy(
                        currentRoute = if (restorableQueueId != null) "now_playing" else profileHomeRoute(next.profile),
                        activeQueueId = restorableQueueId
                    )
                )
                onDeleted(
                    CollectionDeletionResult(
                        hasCollections = true,
                        nextCollectionProfile = next.profile,
                        restoredQueue = restorableQueueId != null
                    )
                )
            } catch (error: Exception) {
                Log.e(LOG_TAG, "delete_collection_failed", error)
                _uiState.value = _uiState.value.copy(errorMessage = error.message ?: "Unable to delete collection")
            }
        }
    }

    private fun profileHomeSession(collection: Collection, source: RootSource?): UiSessionState = UiSessionState(
        currentRoute = profileHomeRoute(collection.profile),
        selectedCollectionId = collection.id,
        selectedSourceId = source?.id,
        activeSort = if (collection.profile == CollectionProfile.FLAT) SortOrder.TITLE else SortOrder.ARTIST,
        activeSurface = if (collection.profile == CollectionProfile.FLAT) LibrarySurface.FOLDERS else LibrarySurface.ARTISTS,
        libraryFilterSnapshot = LibraryFilterSnapshot()
    )

    private fun profileHomeRoute(profile: CollectionProfile): String =
        if (profile == CollectionProfile.FLAT) "folders" else "library"

    private fun observeWork(sourceId: String) {
        workObservation?.cancel()
        workObservation = viewModelScope.launch {
            workManager.getWorkInfosForUniqueWorkFlow(IndexingWorker.uniqueWorkName(sourceId)).collect { infos ->
                val info = infos.maxByOrNull { it.runAttemptCount } ?: return@collect
                when (info.state) {
                    WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED, WorkInfo.State.RUNNING -> {
                        _uiState.value = _uiState.value.copy(
                            isIndexing = true,
                            indexingProgress = ScanProgress(
                                processedFiles = info.progress.getInt(IndexingWorker.KEY_AUDIO, 0),
                                totalFiles = 0,
                                currentStep = "Indexing collection",
                                scanId = info.id.toString(),
                                phase = info.progress.getString(IndexingWorker.KEY_PHASE) ?: "SCANNING"
                            )
                        )
                    }
                    WorkInfo.State.SUCCEEDED -> _uiState.value = _uiState.value.copy(isIndexing = false, indexingProgress = null)
                    WorkInfo.State.CANCELLED, WorkInfo.State.FAILED -> _uiState.value = _uiState.value.copy(
                        isIndexing = false,
                        indexingProgress = null,
                        errorMessage = if (info.state == WorkInfo.State.FAILED) "Indexing failed" else _uiState.value.errorMessage
                    )
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
