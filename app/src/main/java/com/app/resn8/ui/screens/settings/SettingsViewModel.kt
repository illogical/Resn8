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
import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.storage.indexer.IndexingWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

data class SettingsUiState(
    val collectionName: String = "MUSIC",
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
            appContainer.collectionRepository.getCollectionsFlow().collect { collections ->
                val collection = collections.firstOrNull() ?: return@collect
                val rootSources = appContainer.collectionRepository.getRootSourcesFlow(collection.id).firstOrNull().orEmpty()
                val activeRoot = rootSources.firstOrNull()

                _uiState.value = _uiState.value.copy(
                    collectionName = collection.name,
                    activeSource = activeRoot
                )

                if (activeRoot != null) {
                    observeWork(activeRoot.id)
                }
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
                appContainer.collectionRepository.addRootSource(root.collectionId, uri.toString(), root.displayName)
                IndexingWorker.enqueue(context, root.id, uri.toString())
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
                            currentStep = "Indexing music library",
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
