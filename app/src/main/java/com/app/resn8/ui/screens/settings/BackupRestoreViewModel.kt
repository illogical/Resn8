package com.app.resn8.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.app.resn8.data.backup.BackupExportResult
import com.app.resn8.data.backup.BackupImportResult
import com.app.resn8.data.backup.BackupValidationException
import com.app.resn8.data.backup.ValidatedBackup
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.CollectionSummary
import com.app.resn8.storage.indexer.IndexingWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class BackupRestoreUiState(
    val localCollections: List<CollectionSummary> = emptyList(),
    val exportSelectedIds: Set<String> = emptySet(),
    val validatedBackup: ValidatedBackup? = null,
    val importSelectedIds: Set<String> = emptySet(),
    val replaceImportedIds: Set<String> = emptySet(),
    val isWorking: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val importResult: BackupImportResult? = null
)

class BackupRestoreViewModel(
    private val context: Context,
    private val container: AppContainer
) : ViewModel() {
    private val repository = requireNotNull(container.backupRepository) { "Backup repository is unavailable" }
    private val workManager = WorkManager.getInstance(context)
    private val _uiState = MutableStateFlow(BackupRestoreUiState())
    val uiState: StateFlow<BackupRestoreUiState> = _uiState.asStateFlow()
    private var initializedExportSelection = false

    init {
        viewModelScope.launch {
            container.collectionRepository.getCollectionSummariesFlow().collect { collections ->
                _uiState.update { current ->
                    val selected = if (!initializedExportSelection) {
                        initializedExportSelection = true
                        collections.mapTo(linkedSetOf()) { it.collection.id }
                    } else {
                        current.exportSelectedIds.intersect(collections.mapTo(hashSetOf()) { it.collection.id })
                    }
                    current.copy(localCollections = collections, exportSelectedIds = selected)
                }
            }
        }
    }

    fun toggleExportCollection(collectionId: String) = _uiState.update {
        it.copy(exportSelectedIds = it.exportSelectedIds.toggle(collectionId), statusMessage = null, errorMessage = null)
    }

    fun toggleAllExportCollections() = _uiState.update { state ->
        val allIds = state.localCollections.mapTo(linkedSetOf()) { it.collection.id }
        state.copy(
            exportSelectedIds = if (state.exportSelectedIds.size == allIds.size) emptySet() else allIds,
            statusMessage = null,
            errorMessage = null
        )
    }

    fun exportTo(uri: Uri) {
        val selected = _uiState.value.exportSelectedIds
        if (selected.isEmpty()) return
        viewModelScope.launch {
            runOperation {
                val result = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { output ->
                        repository.exportBackup(selected, output)
                    } ?: error("The selected file could not be opened for writing.")
                }
                _uiState.update { it.copy(statusMessage = result.exportMessage(), importResult = null) }
            }
        }
    }

    fun inspect(uri: Uri) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isWorking = true,
                    validatedBackup = null,
                    importSelectedIds = emptySet(),
                    replaceImportedIds = emptySet(),
                    importResult = null,
                    statusMessage = "Validating backup…",
                    errorMessage = null
                )
            }
            try {
                val backup = withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: error("The selected file could not be opened.")
                    input.use { repository.inspectBackup(it) }
                }
                _uiState.update {
                    it.copy(
                        validatedBackup = backup,
                        importSelectedIds = emptySet(),
                        replaceImportedIds = emptySet(),
                        isWorking = false,
                        statusMessage = "Backup validated. Choose collections to import."
                    )
                }
            } catch (error: Exception) {
                val message = if (error is BackupValidationException) error.message else "The backup could not be read."
                _uiState.update { it.copy(isWorking = false, statusMessage = null, errorMessage = message) }
            }
        }
    }

    fun toggleImportCollection(collectionId: String) = _uiState.update { state ->
        val selected = state.importSelectedIds.toggle(collectionId)
        state.copy(
            importSelectedIds = selected,
            replaceImportedIds = state.replaceImportedIds.intersect(selected),
            statusMessage = null,
            errorMessage = null
        )
    }

    fun toggleAllImportCollections() = _uiState.update { state ->
        val allIds = state.validatedBackup?.collections?.mapTo(linkedSetOf()) { it.id }.orEmpty()
        val selected = if (state.importSelectedIds.size == allIds.size) emptySet() else allIds
        state.copy(importSelectedIds = selected, replaceImportedIds = state.replaceImportedIds.intersect(selected))
    }

    fun setReplace(collectionId: String, replace: Boolean) = _uiState.update { state ->
        state.copy(
            replaceImportedIds = if (replace) state.replaceImportedIds + collectionId else state.replaceImportedIds - collectionId
        )
    }

    fun importSelected() {
        val state = _uiState.value
        val backup = state.validatedBackup ?: return
        if (state.importSelectedIds.isEmpty()) return
        viewModelScope.launch {
            runOperation {
                container.playbackConnection?.checkpointAndStopForCollectionSwitch()
                state.replaceImportedIds.flatMap { importedId ->
                    backup.collections.first { it.id == importedId }.conflictingCollectionIds
                }.distinct().forEach { collectionId ->
                    container.collectionRepository.getRootSourcesFlow(collectionId).firstOrNull().orEmpty()
                        .forEach { workManager.cancelUniqueWork(IndexingWorker.uniqueWorkName(it.id)) }
                }
                val result = withContext(Dispatchers.IO) {
                    repository.importBackup(backup, state.importSelectedIds, state.replaceImportedIds)
                }
                result.reindexSourceIds.forEach { sourceId ->
                    container.collectionRepository.getRootSourceById(sourceId)?.let { source ->
                        IndexingWorker.enqueue(context, source.id, source.treeUri)
                    }
                }
                _uiState.update {
                    it.copy(
                        importResult = result,
                        validatedBackup = null,
                        importSelectedIds = emptySet(),
                        replaceImportedIds = emptySet(),
                        statusMessage = "Restored ${result.restoredCollectionIds.size} collection(s). " +
                            "${result.unresolvedMediaCount} track record(s) will be reconciled during indexing."
                    )
                }
            }
        }
    }

    fun reconnectFolder(collectionId: String, uri: Uri) {
        viewModelScope.launch {
            runOperation {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                val source = container.collectionRepository.getRootSourcesFlow(collectionId).firstOrNull()?.firstOrNull()
                    ?: error("The restored collection source no longer exists.")
                val updated = container.collectionRepository.reselectRootSource(source.id, uri.toString())
                IndexingWorker.enqueue(context, updated.id, updated.treeUri)
                _uiState.update { current ->
                    current.copy(
                        importResult = current.importResult?.copy(
                            needsFolderCollectionIds = current.importResult.needsFolderCollectionIds - collectionId
                        ),
                        statusMessage = "Folder connected. Resn8 is reconciling restored metadata."
                    )
                }
            }
        }
    }

    fun clearMessages() = _uiState.update { it.copy(statusMessage = null, errorMessage = null) }

    private suspend fun runOperation(block: suspend () -> Unit) {
        _uiState.update { it.copy(isWorking = true, errorMessage = null) }
        try {
            block()
        } catch (error: Exception) {
            _uiState.update { it.copy(errorMessage = error.message ?: "The backup operation failed.") }
        } finally {
            _uiState.update { it.copy(isWorking = false) }
        }
    }

    class Factory(private val context: Context, private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            BackupRestoreViewModel(context.applicationContext, container) as T
    }
}

private fun Set<String>.toggle(value: String): Set<String> = if (value in this) this - value else this + value

private fun BackupExportResult.exportMessage(): String =
    "Backup saved: $collectionCount collection(s), $mediaCount tracks, $playlistCount playlists, and $historyCount history records."
