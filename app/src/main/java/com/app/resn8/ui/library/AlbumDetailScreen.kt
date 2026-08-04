package com.app.resn8.ui.library

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    viewModel: AlbumDetailViewModel,
    onBack: () -> Unit,
    onTrackClick: (com.app.resn8.domain.model.MediaFile) -> Unit = {},
    onAddToPlaylist: (List<String>, title: String) -> Unit = { _, _ -> }
) {
    val tracks = viewModel.tracksPaged.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()
    val selectedFileIds by viewModel.selectedFileIds.collectAsState()
    val allAvailableSelected by viewModel.allAvailableSelected.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(viewModel.albumTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = viewModel::toggleSelectAll
                    ) {
                        Icon(imageVector = Icons.Default.SelectAll, contentDescription = if (allAvailableSelected) "Deselect all available songs" else "Select all available songs")
                    }
                    if (selectedFileIds.isNotEmpty()) {
                        IconButton(onClick = {
                            scope.launch {
                                val ids = viewModel.getSelectedMediaIdsInAlbumOrder()
                                if (ids.isNotEmpty()) onAddToPlaylist(ids, "Add ${ids.size} selected songs")
                            }
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add selected songs to playlist")
                        }
                    }
                },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            items(tracks.itemCount) { index ->
                tracks[index]?.let { mediaFile ->
                    TrackListItemRow(
                        mediaFile = mediaFile,
                        isSelected = mediaFile.id in selectedFileIds,
                        onSelectToggle = { if (mediaFile.isAvailable) viewModel.toggleFileSelection(mediaFile.id) },
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
