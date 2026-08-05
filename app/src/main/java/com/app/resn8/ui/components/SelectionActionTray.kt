package com.app.resn8.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
fun SelectionActionTray(
    selectedFileCount: Int,
    selectedFolderCount: Int = 0,
    resolvedMediaCount: Int = selectedFileCount,
    onAddToPlaylist: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fileLabel = "$selectedFileCount ${if (selectedFileCount == 1) "file" else "files"}"
    val summary = if (selectedFolderCount > 0) {
        "$fileLabel • $selectedFolderCount ${if (selectedFolderCount == 1) "folder" else "folders"}"
    } else {
        "$fileLabel selected"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics {
                contentDescription = "$summary. $resolvedMediaCount available audio files will be added."
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(summary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Button(
                onClick = onAddToPlaylist,
                enabled = resolvedMediaCount > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add to Playlist")
            }
        }
    }
}
