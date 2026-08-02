package com.app.resn8.ui.screens.onboarding

import com.app.resn8.domain.model.ScanProgress
import com.app.resn8.domain.model.ScanResult

sealed interface IndexingUiState {
    object FirstRun : IndexingUiState
    data class FolderNaming(val selectedTreeUri: String, val defaultName: String) : IndexingUiState
    data class Scanning(val progress: ScanProgress?) : IndexingUiState
    data class Complete(val summary: ScanResult) : IndexingUiState
    object EmptyFolder : IndexingUiState
    object PermissionRevoked : IndexingUiState
    data class ScanError(val message: String) : IndexingUiState
}
