package com.app.resn8.ui.library

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.paging.compose.collectAsLazyPagingItems
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(
    viewModel: ArtistDetailViewModel,
    onAlbumClick: (String) -> Unit,
    onBack: () -> Unit,
    onTrackClick: (com.app.resn8.domain.model.MediaFile) -> Unit = {},
    onAddToPlaylist: (List<String>, title: String) -> Unit = { _, _ -> }
) {
    val albums = viewModel.albumsPaged.collectAsLazyPagingItems()
    val tracks = viewModel.tracksPaged.collectAsLazyPagingItems()
    val scope = rememberCoroutineScope()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(viewModel.artistName) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val ids = viewModel.getAllArtistMediaIds()
                                if (ids.isNotEmpty()) {
                                    onAddToPlaylist(ids, "Add Artist '${viewModel.artistName}' (${ids.size} tracks)")
                                }
                            }
                        }
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = "Add Artist Songs to Playlist")
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
            item {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(albums.itemCount) { index ->
                albums[index]?.let { albumSummary ->
                    AlbumSummaryRowItem(
                        albumSummary = albumSummary,
                        onClick = { onAlbumClick(albumSummary.compositeKey) }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Tracks",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(tracks.itemCount) { index ->
                tracks[index]?.let { mediaFile ->
                    TrackListItemRow(
                        mediaFile = mediaFile,
                        isSelected = false,
                        onSelectToggle = {},
                        onClick = { onTrackClick(mediaFile) },
                        showSelectionToggle = false,
                        onAddToPlaylist = {
                            onAddToPlaylist(listOf(mediaFile.id), "Add '${mediaFile.displayTitle}' to Playlist")
                        }
                    )
                }
            }
        }
    }
}
