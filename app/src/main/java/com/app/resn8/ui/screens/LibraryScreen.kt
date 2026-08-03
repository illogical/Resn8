package com.app.resn8.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.ui.library.AlbumSummaryRowItem
import com.app.resn8.ui.library.ArtistSummaryRowItem
import com.app.resn8.ui.library.LibraryFilterSheet
import com.app.resn8.ui.library.LibraryViewModel
import com.app.resn8.ui.library.TrackListItemRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel,
    onArtistClick: (String) -> Unit,
    onAlbumClick: (String) -> Unit,
    onFoldersClick: () -> Unit,
    onTrackClick: (MediaFile) -> Unit = {},
    onAddToPlaylist: (List<String>, title: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val currentSurface by viewModel.surface.collectAsState()
    val searchText by viewModel.searchText.collectAsState()
    val currentSort by viewModel.sort.collectAsState()
    val currentFilters by viewModel.filters.collectAsState()
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()
    val selectedFolderIds by viewModel.selectedFolderIds.collectAsState()
    val selectionResolution by viewModel.selectionResolution.collectAsState()
    val sessionError by viewModel.sessionError.collectAsState()

    var showFilterSheet by remember { mutableStateOf(false) }

    val artistSummaries = viewModel.artistSummariesPaged.collectAsLazyPagingItems()
    val albumSummaries = viewModel.albumSummariesPaged.collectAsLazyPagingItems()
    val tracks = viewModel.tracksPaged.collectAsLazyPagingItems()

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = currentSurface.ordinal) {
            LibrarySurface.entries.forEach { surface ->
                Tab(
                    selected = currentSurface == surface,
                    onClick = {
                        if (surface == LibrarySurface.FOLDERS) {
                            onFoldersClick()
                        } else {
                            viewModel.setSurface(surface)
                        }
                    },
                    text = { Text(surface.name.replace("_", " ").lowercase().replaceFirstChar { it.uppercase() }) }
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            OutlinedTextField(
                value = searchText,
                onValueChange = { viewModel.setSearchText(it) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .semantics {
                        contentDescription = "Search library by title, artist, album, or filename"
                    },
                placeholder = { Text("Search library", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchText("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(onClick = { showFilterSheet = true }) {
                Icon(Icons.Default.FilterList, contentDescription = "Sort and filter")
            }
        }

        if (sessionError != null) {
            Text(
                text = sessionError.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }

        if (selectedFileIds.isNotEmpty() || selectedFolderIds.isNotEmpty()) {
            val resolvedIds = selectionResolution?.uniqueMediaIds ?: emptyList()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Selected: ${selectedFileIds.size} files, ${selectedFolderIds.size} folders",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${resolvedIds.size} unique audio tracks",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Button(
                        onClick = {
                            if (resolvedIds.isNotEmpty()) {
                                onAddToPlaylist(resolvedIds, "Add ${resolvedIds.size} selected tracks")
                            }
                        },
                        enabled = resolvedIds.isNotEmpty()
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add to Playlist")
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(onClick = { viewModel.clearSelection() }) {
                        Text("Clear")
                    }
                }
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            when (currentSurface) {
                LibrarySurface.ARTISTS -> {
                    PagingContent(
                        items = artistSummaries,
                        searchText = searchText,
                        emptyLabel = "No artists are indexed in this collection"
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(artistSummaries.itemCount) { index ->
                                artistSummaries[index]?.let { artistSummary ->
                                    ArtistSummaryRowItem(
                                        artistSummary = artistSummary,
                                        onClick = { onArtistClick(artistSummary.key.serialize()) }
                                    )
                                }
                            }
                        }
                    }
                }
                LibrarySurface.ALBUMS -> {
                    PagingContent(
                        items = albumSummaries,
                        searchText = searchText,
                        emptyLabel = "No albums are indexed in this collection"
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(albumSummaries.itemCount) { index ->
                                albumSummaries[index]?.let { albumSummary ->
                                    AlbumSummaryRowItem(
                                        albumSummary = albumSummary,
                                        onClick = { onAlbumClick(albumSummary.compositeKey) }
                                    )
                                }
                            }
                        }
                    }
                }
                LibrarySurface.ALL_TRACKS -> {
                    PagingContent(
                        items = tracks,
                        searchText = searchText,
                        emptyLabel = "No tracks are indexed in this collection"
                    ) {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(tracks.itemCount) { index ->
                                tracks[index]?.let { mediaFile ->
                                    TrackListItemRow(
                                        mediaFile = mediaFile,
                                        isSelected = selectedFileIds.contains(mediaFile.id),
                                        onSelectToggle = { viewModel.toggleFileSelection(mediaFile.id) },
                                        onClick = { onTrackClick(mediaFile) },
                                        onAddToPlaylist = {
                                            onAddToPlaylist(listOf(mediaFile.id), "Add '${mediaFile.displayTitle}' to Playlist")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                LibrarySurface.FOLDERS -> {
                    // Delegated to FoldersScreen or onFoldersClick
                }
            }
        }
    }

    if (showFilterSheet) {
        LibraryFilterSheet(
            currentSort = currentSort,
            currentFilters = currentFilters,
            onSortSelected = {
                viewModel.setSortOrder(it)
            },
            onAvailabilitySelected = {
                viewModel.setAvailabilityFilter(it)
            },
            onToggleExcludeDisliked = {
                viewModel.toggleExcludeDisliked()
            },
            onDismiss = { showFilterSheet = false }
        )
    }
}

@Composable
private fun <T : Any> PagingContent(
    items: LazyPagingItems<T>,
    searchText: String,
    emptyLabel: String,
    content: @Composable () -> Unit
) {
    val refresh = items.loadState.refresh
    when {
        refresh is LoadState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        refresh is LoadState.Error -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Unable to load this library view", color = MaterialTheme.colorScheme.error)
            TextButton(onClick = items::retry) { Text("Retry") }
        }
        items.itemCount == 0 -> Text(
            text = if (searchText.isBlank()) emptyLabel else "No matches for this search",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(16.dp)
        )
        else -> content()
    }
}
