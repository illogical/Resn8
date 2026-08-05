package com.app.resn8.ui.screens.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.CollectionSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onCollectionsClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Settings") }, windowInsets = WindowInsets(0, 0, 0, 0)) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            SettingsMenuRow(Icons.Default.Storage, "Collections", "Manage folders and indexing", onCollectionsClick)
            SettingsMenuRow(Icons.Default.Info, "About", "About Resn8 and local-first privacy", onAboutClick)
        }
    }
}

@Composable
private fun SettingsMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onCollectionClick: (String) -> Unit,
    onCollectionDeleted: (CollectionDeletionResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    var renameTarget by remember { mutableStateOf<Collection?>(null) }
    var renameValue by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<Collection?>(null) }
    var reselectTargetId by remember { mutableStateOf<String?>(null) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val collectionId = reselectTargetId
        if (uri != null && collectionId != null) viewModel.reselectCollectionFolder(collectionId, uri)
        reselectTargetId = null
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings / Collections") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Settings") } },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) { Icon(Icons.Default.Add, "Create Collection") }
        }
    ) { padding ->
        if (state.collectionSummaries.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No collections yet. Tap + to create one.")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.collectionSummaries, key = { it.collection.id }) { summary ->
                    CollectionCard(
                        summary = summary,
                        onClick = { onCollectionClick(summary.collection.id) },
                        onRename = {
                            renameValue = summary.collection.name
                            renameTarget = summary.collection
                        },
                        onReindex = { viewModel.reindexCollection(summary.collection.id) },
                        onReselect = {
                            reselectTargetId = summary.collection.id
                            folderPicker.launch(null)
                        },
                        onDelete = { deleteTarget = summary.collection }
                    )
                }
                item { Spacer(Modifier.height(80.dp)) }
            }
        }
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            title = { Text("Rename Collection") },
            text = { OutlinedTextField(renameValue, { renameValue = it }, label = { Text("Collection name") }, singleLine = true) },
            confirmButton = {
                TextButton(
                    enabled = renameValue.isNotBlank(),
                    onClick = { viewModel.renameCollection(target.id, renameValue) { renameTarget = null } }
                ) { Text("Rename") }
            },
            dismissButton = { TextButton(onClick = { renameTarget = null }) { Text("Cancel") } }
        )
    }
    deleteTarget?.let { target ->
        DeleteCollectionDialog(
            collection = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                viewModel.deleteCollection(target.id, onCollectionDeleted)
            }
        )
    }
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Collection action failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun CollectionCard(
    summary: CollectionSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onReindex: () -> Unit,
    onReselect: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val collection = summary.collection
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(collection.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                val type = if (collection.profile == CollectionProfile.FLAT) "Audio Files" else "Music"
                val unavailable = if (summary.unavailableTrackCount > 0) " • ${summary.unavailableTrackCount} unavailable" else ""
                Text("$type • ${summary.totalTrackCount} tracks$unavailable", style = MaterialTheme.typography.bodySmall)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, "Collection Actions") }
                DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename() })
                    DropdownMenuItem(text = { Text("Re-index") }, onClick = { menuExpanded = false; onReindex() })
                    DropdownMenuItem(text = { Text("Reselect Collection Folder") }, onClick = { menuExpanded = false; onReselect() })
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionDetailScreen(
    collectionId: String?,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onCollectionDeleted: (CollectionDeletionResult) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    val isCreate = collectionId == null
    var name by remember(collectionId, state.detailCollection?.name) { mutableStateOf(state.detailCollection?.name.orEmpty()) }
    var profile by remember { mutableStateOf(CollectionProfile.MUSIC) }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            if (isCreate) selectedUri = uri else collectionId?.let { viewModel.reselectCollectionFolder(it, uri) }
        }
    }

    LaunchedEffect(collectionId) {
        if (collectionId == null) viewModel.clearDetail() else viewModel.openCollection(collectionId)
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(if (isCreate) "Settings / Collections / New" else "Settings / Collections / ${state.detailCollection?.name.orEmpty()}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Collections") } },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Collection name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
            }
            item {
                Text("Type", style = MaterialTheme.typography.labelLarge)
                if (isCreate) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(profile == CollectionProfile.MUSIC, { profile = CollectionProfile.MUSIC }, { Text("Music") })
                        FilterChip(profile == CollectionProfile.FLAT, { profile = CollectionProfile.FLAT }, { Text("Audio Files") })
                    }
                } else {
                    Text(
                        if (state.detailCollection?.profile == CollectionProfile.FLAT) "Audio Files" else "Music",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text("Collection type cannot be changed after indexing.", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(16.dp))
            }
            item {
                val source = state.detailSource
                Text("Collection folder", style = MaterialTheme.typography.labelLarge)
                Text(
                    if (isCreate) selectedUri?.lastPathSegment ?: "No folder selected" else source?.displayName ?: "No folder configured",
                    style = MaterialTheme.typography.bodyLarge
                )
                OutlinedButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (isCreate) "Select Collection Folder" else "Reselect Collection Folder")
                }
                Spacer(Modifier.height(16.dp))
            }
            if (!isCreate) {
                item {
                    IndexSummaryCard(state)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { collectionId?.let { viewModel.renameCollection(it, name) } },
                        enabled = name.isNotBlank() && name.trim() != state.detailCollection?.name,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save Name") }
                    OutlinedButton(
                        onClick = { collectionId?.let(viewModel::reindexCollection) },
                        enabled = !state.isIndexing,
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Re-index Collection") }
                    TextButton(onClick = { showDelete = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Delete Collection", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                item {
                    Button(
                        onClick = {
                            selectedUri?.let { uri -> viewModel.createCollection(name, profile, uri) { onCreated(it.id) } }
                        },
                        enabled = name.isNotBlank() && selectedUri != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Create and Index Collection") }
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
                }
            }
            item { Spacer(Modifier.height(32.dp)) }
        }
    }

    if (showDelete && state.detailCollection != null) {
        DeleteCollectionDialog(
            collection = state.detailCollection!!,
            onDismiss = { showDelete = false },
            onConfirm = {
                showDelete = false
                viewModel.deleteCollection(state.detailCollection!!.id, onCollectionDeleted)
            }
        )
    }
    state.errorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::clearError,
            title = { Text("Collection action failed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = viewModel::clearError) { Text("OK") } }
        )
    }
}

@Composable
private fun IndexSummaryCard(state: SettingsUiState) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            if (state.isIndexing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Indexing collection", style = MaterialTheme.typography.titleSmall)
                        Text("${state.indexingProgress?.processedFiles ?: 0} audio files processed", style = MaterialTheme.typography.bodySmall)
                    }
                }
            } else {
                Text("Index summary", style = MaterialTheme.typography.titleSmall)
                val source = state.detailSource
                Text("Status: ${if (source?.isAvailable == true) "Available" else "Unavailable / Permission Needed"}")
                source?.lastScanSummary?.let { summary ->
                    Text("${summary.scannedCount} scanned • ${summary.addedCount} added • ${summary.unavailableCount} missing", style = MaterialTheme.typography.bodySmall)
                } ?: Text("This collection has not completed a scan yet.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun DeleteCollectionDialog(collection: Collection, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Collection") },
        text = {
            Text("Delete '${collection.name}' from Resn8? Its index, ratings, play history, playlists, saved queue, and folder access will be removed. Source audio files will not be changed or deleted.")
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Delete", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings / About") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to Settings") } },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(24.dp)) {
            Text("About Resn8", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Resn8 is a local-first Android audio player. Your audio, ratings, playlists, and listening history remain entirely on your device.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
