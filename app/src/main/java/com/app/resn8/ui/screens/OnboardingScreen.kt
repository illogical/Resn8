package com.app.resn8.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.app.resn8.ui.screens.onboarding.IndexingUiState
import com.app.resn8.ui.screens.onboarding.OnboardingViewModel
import kotlinx.coroutines.delay

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scanProgress by viewModel.scanProgress.collectAsState()
    val context = LocalContext.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* WorkManager remains valid if the user declines; Android still exposes foreground work. */ }

    LaunchedEffect(uiState) {
        if (
            uiState is IndexingUiState.Scanning &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFolderSelected(uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        when (val state = uiState) {
            is IndexingUiState.FirstRun -> {
                FirstRunContent(
                    onSelectFolderClicked = { folderPickerLauncher.launch(null) }
                )
            }
            is IndexingUiState.FolderNaming -> {
                FolderNamingDialog(
                    defaultName = state.defaultName,
                    onConfirm = { name -> viewModel.startIndexing(state.selectedTreeUri, name) },
                    onDismiss = { viewModel.resetToFirstRun() }
                )
            }
            is IndexingUiState.Scanning -> {
                ScanningContent(
                    progress = scanProgress ?: state.progress,
                    onCancel = viewModel::cancelIndexing
                )
            }
            is IndexingUiState.Complete -> {
                CompleteSummaryContent(
                    summary = state.summary,
                    onGoToLibraryClicked = { viewModel.openLibrary(onNavigateToLibrary) }
                )
            }
            is IndexingUiState.EmptyFolder -> {
                EmptyFolderContent(
                    onSelectAnotherClicked = { folderPickerLauncher.launch(null) }
                )
            }
            is IndexingUiState.PermissionRevoked -> {
                PermissionRevokedContent(
                    onGrantAccessClicked = { folderPickerLauncher.launch(null) }
                )
            }
            is IndexingUiState.ScanError -> {
                ScanErrorContent(
                    errorMessage = state.message,
                    onRetryClicked = { folderPickerLauncher.launch(null) }
                )
            }
        }
    }
}

@Composable
private fun FirstRunContent(onSelectFolderClicked: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Welcome to Resn8",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Local-first offline audio player. Select a folder to index your music library.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onSelectFolderClicked) {
            Text("Select Music Folder")
        }
    }
}

@Composable
private fun FolderNamingDialog(
    defaultName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var collectionName by remember { mutableStateOf(defaultName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Name Your Collection") },
        text = {
            Column {
                Text("Enter a display name for this audio collection:")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = collectionName,
                    onValueChange = { collectionName = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (collectionName.isNotBlank()) {
                        onConfirm(collectionName.trim())
                    }
                }
            ) {
                Text("Start Indexing")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ScanningContent(
    progress: com.app.resn8.domain.model.ScanProgress?,
    onCancel: () -> Unit
) {
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(progress?.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val elapsedMs = progress?.startedAt?.takeIf { it > 0L }?.let { (now - it).coerceAtLeast(0L) } ?: 0L
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Indexing Library...",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Elapsed: ${formatDuration(elapsedMs)}",
            style = MaterialTheme.typography.bodyMedium
        )
        Text(text = "Tracks: ${progress?.admittedAudio ?: 0} • Files checked: ${progress?.inspectedDocuments ?: 0}")
        Text(text = "Folders: ${progress?.scannedFolders ?: 0} • Unsupported: ${progress?.unsupportedCount ?: 0}")
        val issueCount = (progress?.unreadableCount ?: 0) + (progress?.metadataFailureCount ?: 0)
        if (issueCount > 0) Text(text = "Read/metadata issues: $issueCount")
        if (progress?.currentStep?.isNotBlank() == true) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = progress.currentStep,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedButton(onClick = onCancel) {
            Text("Cancel Indexing")
        }
    }
}

@Composable
private fun CompleteSummaryContent(
    summary: com.app.resn8.domain.model.ScanResult,
    onGoToLibraryClicked: () -> Unit
) {
    var showDetails by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Library ready",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "${summary.scannedCount} tracks indexed",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )

            val hasChanges = summary.addedCount > 0 || summary.updatedCount > 0 || summary.unavailableCount > 0
            if (hasChanges) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${summary.addedCount} added • ${summary.updatedCount} updated • ${summary.unavailableCount} unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            val issueCount = summary.unreadableCount + summary.metadataFailureCount + summary.unsupportedAudioLikeCount
            if (issueCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Some files need attention ($issueCount issues)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onGoToLibraryClicked,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Open Library")
            }

            Spacer(modifier = Modifier.height(8.dp))

            TextButton(
                onClick = { showDetails = !showDetails }
            ) {
                Text(if (showDetails) "Hide scan details" else "View scan details")
            }

            if (showDetails) {
                Spacer(modifier = Modifier.height(12.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text("Elapsed: ${formatDuration(summary.durationMs)}", style = MaterialTheme.typography.bodySmall)
                    Text("Folders Scanned: ${summary.scannedFolderCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Documents Inspected: ${summary.inspectedDocumentCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Tag-Derived Titles: ${summary.tagDerivedCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Path/Filename Enhancements: ${summary.pathDerivedCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Unrecognized Patterns: ${summary.unrecognizedCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Unreadable Branches: ${summary.unreadableCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Metadata Fallbacks: ${summary.metadataFailureCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Unsupported Audio-Like: ${summary.unsupportedAudioLikeCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Ignored Non-Audio Documents: ${summary.ignoredNonAudioCount}", style = MaterialTheme.typography.bodySmall)
                    Text("Preferred Artwork Candidates: ${summary.artworkCandidateCount}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) / 1_000L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%dh %02dm %02ds".format(hours, minutes, seconds)
    else "%dm %02ds".format(minutes, seconds)
}

@Composable
private fun EmptyFolderContent(onSelectAnotherClicked: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No Audio Files Found",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "The selected folder does not contain any supported audio files (.mp3, .m4a, .flac, etc.).",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onSelectAnotherClicked) {
            Text("Select Different Folder")
        }
    }
}

@Composable
private fun PermissionRevokedContent(onGrantAccessClicked: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Permission Revoked",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Resn8 needs persistent access to your music folder to index and play audio.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onGrantAccessClicked) {
            Text("Re-grant Permission")
        }
    }
}

@Composable
private fun ScanErrorContent(
    errorMessage: String,
    onRetryClicked: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Indexing Failed",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = errorMessage,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onRetryClicked) {
            Text("Retry Indexing")
        }
    }
}
