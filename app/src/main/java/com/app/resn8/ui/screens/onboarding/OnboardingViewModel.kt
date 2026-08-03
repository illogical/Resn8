package com.app.resn8.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.storage.indexer.IndexingWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val context: Context,
    private val appContainer: AppContainer
) : ViewModel() {
    private val workManager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow<IndexingUiState>(IndexingUiState.FirstRun)
    val uiState: StateFlow<IndexingUiState> = _uiState.asStateFlow()
    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()
    private var activeSourceId: String? = null
    private var activeCollectionId: String? = null
    private var workObservation: Job? = null

    init {
        checkExistingRoot()
    }

    fun checkExistingRoot() {
        viewModelScope.launch {
            runCatching {
                val collection = appContainer.collectionRepository.getCollectionsFlow().first().firstOrNull()
                    ?: return@runCatching
                val root = appContainer.collectionRepository.getRootSourcesFlow(collection.id).first().firstOrNull()
                    ?: return@runCatching
                activeSourceId = root.id
                activeCollectionId = collection.id
                if (root.lastScanStatus == "IN_PROGRESS") {
                    observeWork(root.id)
                }
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val hasReadGrant = context.contentResolver.persistedUriPermissions.any {
                    it.uri == uri && it.isReadPermission
                }
                if (!hasReadGrant) throw SecurityException("Read access was not persisted")
                _uiState.value = IndexingUiState.FolderNaming(uri.toString(), getFolderDisplayName(context, uri))
            } catch (_: SecurityException) {
                _uiState.value = IndexingUiState.PermissionRevoked
            }
        }
    }

    fun startIndexing(treeUriStr: String, collectionName: String) {
        viewModelScope.launch {
            var phase = "CREATE_COLLECTION"
            try {
                val collection = appContainer.collectionRepository.createCollection(collectionName)
                phase = "ADD_ROOT_SOURCE"
                val root = appContainer.collectionRepository.addRootSource(collection.id, treeUriStr, collectionName)
                activeSourceId = root.id
                activeCollectionId = collection.id
                phase = "SELECT_COLLECTION"
                runCatching { persistActiveSelection(collection.id, root.id, "onboarding") }
                    .onFailure { error ->
                        Log.w(LOG_TAG, "initial_selection_save_failed category=${error::class.simpleName}")
                    }
                phase = "ENQUEUE_WORK"
                _uiState.value = IndexingUiState.Scanning(null)
                observeWork(root.id)
                IndexingWorker.enqueue(context, root.id, treeUriStr)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "onboarding_setup_failed phase=$phase category=${e::class.simpleName}")
                _uiState.value = IndexingUiState.ScanError("Unable to start indexing")
            }
        }
    }

    fun cancelIndexing() {
        activeSourceId?.let { workManager.cancelUniqueWork(IndexingWorker.uniqueWorkName(it)) }
    }

    fun resetToFirstRun() {
        _uiState.value = IndexingUiState.FirstRun
    }

    fun openLibrary(onReady: () -> Unit) {
        viewModelScope.launch {
            val collectionId = activeCollectionId
            val sourceId = activeSourceId
            if (collectionId == null || sourceId == null) {
                _uiState.value = IndexingUiState.ScanError("The indexed collection could not be selected.")
                return@launch
            }
            try {
                persistActiveSelection(collectionId, sourceId, "library")
                onReady()
            } catch (error: Exception) {
                Log.e(LOG_TAG, "library_handoff_failed category=${error::class.simpleName}")
                _uiState.value = IndexingUiState.ScanError("Unable to open the indexed collection.")
            }
        }
    }

    private suspend fun persistActiveSelection(collectionId: String, sourceId: String, route: String) {
        val session = appContainer.uiSessionRepository.getUiSessionStateFlow().first()
        appContainer.uiSessionRepository.saveUiSessionState(
            session.copy(
                currentRoute = route,
                selectedCollectionId = collectionId,
                selectedSourceId = sourceId
            )
        )
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
                            currentStep = "Scanning selected folder",
                            scanId = info.id.toString(),
                            phase = data.getString(IndexingWorker.KEY_PHASE) ?: "SCANNING",
                            startedAt = data.getLong(IndexingWorker.KEY_STARTED_AT, System.currentTimeMillis()),
                            scannedFolders = data.getInt(IndexingWorker.KEY_FOLDERS, 0),
                            inspectedDocuments = data.getInt(IndexingWorker.KEY_DOCUMENTS, 0),
                            admittedAudio = data.getInt(IndexingWorker.KEY_AUDIO, 0),
                            unsupportedCount = data.getInt(IndexingWorker.KEY_UNSUPPORTED, 0),
                            unreadableCount = data.getInt(IndexingWorker.KEY_UNREADABLE, 0),
                            metadataFailureCount = data.getInt(IndexingWorker.KEY_METADATA_FAILURES, 0),
                            artworkCandidateCount = data.getInt(IndexingWorker.KEY_ARTWORK, 0)
                        )
                        _scanProgress.value = progress
                        _uiState.value = IndexingUiState.Scanning(progress)
                    }
                    WorkInfo.State.SUCCEEDED -> refreshCompletedRoot(sourceId)
                    WorkInfo.State.CANCELLED -> _uiState.value = IndexingUiState.ScanError("Indexing was cancelled. Your previous library was not changed.")
                    WorkInfo.State.FAILED -> {
                        val errorPhase = info.outputData.getString(IndexingWorker.KEY_ERROR_PHASE)
                        val errorCategory = info.outputData.getString(IndexingWorker.KEY_ERROR_CATEGORY)
                        val detail = if (errorPhase != null) " (phase=$errorPhase category=$errorCategory)" else ""
                        Log.e(LOG_TAG, "worker_failed sourceId=$sourceId$detail")
                        _uiState.value = IndexingUiState.ScanError("Indexing failed. Your previous library was not changed.")
                    }
                }
            }
        }
    }

    private suspend fun refreshCompletedRoot(sourceId: String) {
        val collection = appContainer.collectionRepository.getCollectionsFlow().first().firstOrNull() ?: return
        val root = appContainer.collectionRepository.getRootSourcesFlow(collection.id).first()
            .firstOrNull { it.id == sourceId } ?: return
        val summary = root.lastScanSummary ?: return
        _uiState.value = if (summary.scannedCount == 0) IndexingUiState.EmptyFolder else IndexingUiState.Complete(summary)
    }

    private fun getFolderDisplayName(context: Context, uri: Uri): String {
        return runCatching {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
            context.contentResolver.query(
                docUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        }.getOrNull()?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.trim().takeUnless { it.isNullOrBlank() }
            ?: "Music Library"
    }

    class Factory(
        private val context: Context,
        private val appContainer: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            OnboardingViewModel(context, appContainer) as T
    }

    companion object {
        private const val LOG_TAG = "Resn8Onboarding"
    }
}
