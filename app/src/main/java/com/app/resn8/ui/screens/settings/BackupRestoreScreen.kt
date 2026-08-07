package com.app.resn8.ui.screens.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.resn8.data.backup.BackupCollectionPreview
import com.app.resn8.domain.model.CollectionProfile
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    viewModel: BackupRestoreViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var showImportConfirmation by remember { mutableStateOf(false) }
    var reconnectCollectionId by remember { mutableStateOf<String?>(null) }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let(viewModel::exportTo) }
    val openDocument = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(viewModel::inspect)
    }
    val openFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val collectionId = reconnectCollectionId
        if (uri != null && collectionId != null) viewModel.reconnectFolder(collectionId, uri)
        reconnectCollectionId = null
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings / Backup & Restore") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Settings") } },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Text(
                    "Backups contain personal listening metadata, playlists, and indexed track details. They do not contain audio files or portable folder permissions.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            SectionHeader(
                title = "Export backup",
                subtitle = "Choose the collections to save. All are selected initially.",
                allSelected = state.localCollections.isNotEmpty() && state.exportSelectedIds.size == state.localCollections.size,
                enabled = !state.isWorking && state.localCollections.isNotEmpty(),
                onToggleAll = viewModel::toggleAllExportCollections
            )
            state.localCollections.forEach { summary ->
                val collection = summary.collection
                SelectionRow(
                    checked = collection.id in state.exportSelectedIds,
                    title = collection.name,
                    subtitle = "${profileLabel(collection.profile)} • ${summary.totalTrackCount} tracks",
                    enabled = !state.isWorking,
                    onClick = { viewModel.toggleExportCollection(collection.id) }
                )
            }
            Button(
                onClick = { createDocument.launch("resn8-backup-${LocalDate.now()}.json") },
                enabled = !state.isWorking && state.exportSelectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Save, null)
                Spacer(Modifier.width(8.dp))
                Text("Save selected collections")
            }

            HorizontalDivider()

            Text("Import backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                "Choose a Resn8 JSON backup. It will be validated before you can select anything to restore.",
                style = MaterialTheme.typography.bodyMedium
            )
            OutlinedButton(
                onClick = { openDocument.launch(arrayOf("application/json", "text/json", "text/plain")) },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Restore, null)
                Spacer(Modifier.width(8.dp))
                Text(if (state.validatedBackup == null) "Choose backup JSON" else "Choose a different backup")
            }

            if (state.isWorking) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(state.statusMessage ?: "Working…")
                }
            }
            state.errorMessage?.let { MessageCard(it, isError = true, onDismiss = viewModel::clearMessages) }
            if (!state.isWorking) state.statusMessage?.let { MessageCard(it, isError = false, onDismiss = viewModel::clearMessages) }

            state.validatedBackup?.let { backup ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Valid Resn8 backup", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("Format version ${backup.envelope.version} • ${backup.collections.size} collection(s)")
                    }
                }
                SectionHeader(
                    title = "Collections to import",
                    subtitle = "Nothing is selected initially. Existing collections stay safe unless you explicitly replace them.",
                    allSelected = state.importSelectedIds.size == backup.collections.size,
                    enabled = !state.isWorking,
                    onToggleAll = viewModel::toggleAllImportCollections
                )
                backup.collections.forEach { preview ->
                    ImportCollectionRow(
                        preview = preview,
                        selected = preview.id in state.importSelectedIds,
                        replace = preview.id in state.replaceImportedIds,
                        enabled = !state.isWorking,
                        onToggle = { viewModel.toggleImportCollection(preview.id) },
                        onReplace = { viewModel.setReplace(preview.id, it) }
                    )
                }
                Button(
                    onClick = { showImportConfirmation = true },
                    enabled = !state.isWorking && state.importSelectedIds.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Review and import") }
            }

            state.importResult?.let { result ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Import complete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${result.restoredCollectionIds.size} restored • ${result.replacedCollectionCount} replaced • ${result.skippedCollectionCount} skipped")
                        if (result.needsFolderCollectionIds.isNotEmpty()) {
                            Text("Reconnect these folders to make restored tracks playable:")
                            result.needsFolderCollectionIds.forEach { collectionId ->
                                val name = state.localCollections.firstOrNull { it.collection.id == collectionId }?.collection?.name ?: "Restored collection"
                                OutlinedButton(
                                    onClick = { reconnectCollectionId = collectionId; openFolder.launch(null) },
                                    enabled = !state.isWorking,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.FolderOpen, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Select folder for $name")
                                }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showImportConfirmation) {
        val backup = state.validatedBackup
        val selected = state.importSelectedIds.size
        val replacing = state.replaceImportedIds.size
        val skipping = backup?.collections?.count {
            it.id in state.importSelectedIds && it.conflictingCollectionIds.isNotEmpty() && it.id !in state.replaceImportedIds
        } ?: 0
        AlertDialog(
            onDismissRequest = { showImportConfirmation = false },
            title = { Text("Import selected collections?") },
            text = {
                Text("$selected selected. $replacing will replace existing metadata and $skipping conflict(s) will be skipped. Source audio will not be changed. Folder access may need to be reselected afterward.")
            },
            confirmButton = {
                TextButton(onClick = { showImportConfirmation = false; viewModel.importSelected() }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { showImportConfirmation = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    subtitle: String,
    allSelected: Boolean,
    enabled: Boolean,
    onToggleAll: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = onToggleAll, enabled = enabled) {
            Icon(
                if (allSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                if (allSelected) "Select none" else "Select all"
            )
        }
    }
}

@Composable
private fun SelectionRow(
    checked: Boolean,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = { onClick() }, enabled = enabled)
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ImportCollectionRow(
    preview: BackupCollectionPreview,
    selected: Boolean,
    replace: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    onReplace: (Boolean) -> Unit
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            SelectionRow(
                checked = selected,
                title = preview.name,
                subtitle = "${preview.profile.lowercase().replaceFirstChar { it.uppercase() }} • ${preview.mediaCount} tracks • ${preview.playlistCount} playlists • ${preview.historyCount} history records",
                enabled = enabled,
                onClick = onToggle
            )
            if (preview.conflictingCollectionIds.isNotEmpty()) {
                Text(
                    if (replace) "The conflicting local collection will be replaced." else "Conflict found. This collection will be skipped unless replacement is enabled.",
                    color = if (replace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(
                        checked = replace,
                        onCheckedChange = onReplace,
                        enabled = enabled && selected
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Replace existing collection", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MessageCard(message: String, isError: Boolean, onDismiss: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    }
}

private fun profileLabel(profile: CollectionProfile): String = when (profile) {
    CollectionProfile.FLAT -> "Audio Files"
    CollectionProfile.MUSIC -> "Music"
    CollectionProfile.CONTEXTUAL -> "Contextual"
}
