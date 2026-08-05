package com.app.resn8.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import com.app.resn8.ui.folders.BreadcrumbBar
import com.app.resn8.ui.folders.FoldersViewModel
import com.app.resn8.ui.library.TrackListItemRow
import com.app.resn8.ui.components.SelectionActionTray

@Composable
fun FoldersScreen(
    viewModel: FoldersViewModel,
    onTrackClick: (com.app.resn8.domain.model.MediaFile) -> Unit = {},
    onAddToPlaylist: (List<String>, title: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val breadcrumbs by viewModel.breadcrumbs.collectAsState(initial = emptyList())
    val childFolders by viewModel.childFolders.collectAsState(initial = emptyList())
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()
    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    val selectionResolution by viewModel.selectionResolution.collectAsState()
    val allDirectFilesSelected by viewModel.allDirectFilesSelected.collectAsState()

    val mediaFiles = viewModel.folderMediaPaged.collectAsLazyPagingItems()

    Column(modifier = modifier.fillMaxSize()) {
        BreadcrumbBar(
            breadcrumbs = breadcrumbs,
            onBreadcrumbClick = { viewModel.navigateToFolder(it) }
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = viewModel::toggleSelectAllDirectAvailable) {
                Icon(Icons.Default.SelectAll, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (allDirectFilesSelected) "Deselect All Audio Files" else "Select All Audio Files")
            }
        }

        val hasSelection = selectedFileIds.isNotEmpty() || selectedFolderIds.isNotEmpty()
        val resolvedIds = selectionResolution?.uniqueMediaIds ?: emptyList()
        Box(modifier = Modifier.weight(1f)) {
        if (childFolders.isEmpty() && mediaFiles.itemCount == 0) {
            Text(
                text = "Folder is empty",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = if (hasSelection) 128.dp else 0.dp)
            ) {
                items(childFolders.size) { index ->
                    val child = childFolders[index]
                    val isSelected = selectedFolderIds.contains(child.folder.id)
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.navigateToFolder(child.folder.id) }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Checkbox(
                                checked = isSelected,
                                onCheckedChange = { viewModel.toggleFolderSelection(child.folder.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = "Subfolder",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = child.folder.displayName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${child.childFolderCount} folders • ${child.directMediaCount} files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                items(mediaFiles.itemCount) { index ->
                    mediaFiles[index]?.let { mediaFile ->
                        TrackListItemRow(
                            mediaFile = mediaFile,
                            isSelected = selectedFileIds.contains(mediaFile.id),
                            onSelectToggle = { viewModel.toggleFileSelection(mediaFile.id) },
                            onClick = { onTrackClick(mediaFile) },
                            showMusicMetadata = viewModel.collectionProfile == com.app.resn8.domain.model.CollectionProfile.MUSIC
                        )
                    }
                }
            }
        }
        if (hasSelection) {
            SelectionActionTray(
                selectedFileCount = selectedFileIds.size,
                selectedFolderCount = selectedFolderIds.size,
                resolvedMediaCount = resolvedIds.size,
                onAddToPlaylist = {
                    if (resolvedIds.isNotEmpty()) {
                        onAddToPlaylist(resolvedIds, "Add ${resolvedIds.size} selected audio files")
                    }
                },
                onClear = viewModel::clearSelection,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
        }
    }
}
