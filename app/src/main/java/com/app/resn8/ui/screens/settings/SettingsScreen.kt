package com.app.resn8.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.onFolderReselected(uri)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = "Settings",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Collection: ${state.collectionName}",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))

                val source = state.activeSource
                if (source != null) {
                    Text(text = "Root Folder: ${source.displayName}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Status: ${if (source.isAvailable) "Available" else "Unavailable / Permission Needed"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (source.isAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    val summary = source.lastScanSummary
                    if (summary != null) {
                        Text(
                            text = "Last Scan Summary: ${summary.scannedCount} files scanned (${summary.addedCount} added, ${summary.unavailableCount} missing)",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                } else {
                    Text(text = "No active root folder configured.", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(text = "Library Actions", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = { viewModel.reindexSource() },
            enabled = !state.isIndexing && state.activeSource != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.isIndexing) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp).padding(end = 8.dp))
                Text("Re-indexing...")
            } else {
                Text("Manual Re-Index Library")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = { folderPickerLauncher.launch(null) },
            enabled = !state.isIndexing,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Reselect Root Folder (SAF Permission)")
        }

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "About Resn8", style = MaterialTheme.typography.titleSmall)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Resn8 is a local-first Android audio player. Your audio, ratings, playlists, and listening history remain entirely on your device.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
