package com.app.resn8.ui.screens.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.storage.indexer.ScanOrchestrator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class OnboardingViewModel(
    private val context: Context,
    private val appContainer: AppContainer
) : ViewModel() {

    private val scanOrchestrator = ScanOrchestrator(
        context = context,
        mediaRepository = appContainer.mediaRepository,
        collectionRepository = appContainer.collectionRepository,
        database = appContainer.database
    )

    private val _uiState = MutableStateFlow<IndexingUiState>(IndexingUiState.FirstRun)
    val uiState: StateFlow<IndexingUiState> = _uiState.asStateFlow()

    private val _scanProgress = MutableStateFlow<ScanProgress?>(null)
    val scanProgress: StateFlow<ScanProgress?> = _scanProgress.asStateFlow()

    init {
        checkExistingRoot()
        viewModelScope.launch {
            scanOrchestrator.scanProgress.collect { progress ->
                _scanProgress.value = progress
                if (_uiState.value is IndexingUiState.Scanning) {
                    _uiState.value = IndexingUiState.Scanning(progress)
                }
            }
        }
    }

    fun checkExistingRoot() {
        viewModelScope.launch {
            try {
                val collections = appContainer.collectionRepository.getCollectionsFlow().first()
                if (collections.isNotEmpty()) {
                    val defaultCol = collections.first()
                    val roots = appContainer.collectionRepository.getRootSourcesFlow(defaultCol.id).first()
                    if (roots.isNotEmpty()) {
                        val root = roots.first()
                        if (root.lastScanSummary != null) {
                            _uiState.value = IndexingUiState.Complete(root.lastScanSummary)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            } catch (_: Exception) {
                // Persistent grant may already exist or fail gracefully
            }

            val defaultName = getFolderDisplayName(context, uri)
            _uiState.value = IndexingUiState.FolderNaming(uri.toString(), defaultName)
        }
    }

    fun startIndexing(treeUriStr: String, collectionName: String) {
        viewModelScope.launch {
            try {
                _uiState.value = IndexingUiState.Scanning(null)
                val collection = appContainer.collectionRepository.createCollection(collectionName)
                val rootSource = appContainer.collectionRepository.addRootSource(
                    collectionId = collection.id,
                    treeUri = treeUriStr,
                    displayName = collectionName
                )

                val result = scanOrchestrator.executeScan(
                    sourceId = rootSource.id,
                    treeUri = Uri.parse(treeUriStr)
                )

                if (result.scannedCount == 0) {
                    _uiState.value = IndexingUiState.EmptyFolder
                } else {
                    _uiState.value = IndexingUiState.Complete(result)
                }
            } catch (e: Exception) {
                _uiState.value = IndexingUiState.ScanError(e.message ?: "Failed to index folder")
            }
        }
    }

    fun resetToFirstRun() {
        _uiState.value = IndexingUiState.FirstRun
    }

    private fun getFolderDisplayName(context: Context, uri: Uri): String {
        try {
            val treeDocId = DocumentsContract.getTreeDocumentId(uri)
            if (treeDocId != null) {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(uri, treeDocId)
                context.contentResolver.query(docUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name
                    }
                }
            }
        } catch (_: Exception) {}

        val lastSegment = uri.lastPathSegment?.substringAfterLast(':')?.trim()
        return if (!lastSegment.isNullOrBlank()) lastSegment else "Music Library"
    }

    class Factory(
        private val context: Context,
        private val appContainer: AppContainer
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return OnboardingViewModel(context, appContainer) as T
        }
    }
}
