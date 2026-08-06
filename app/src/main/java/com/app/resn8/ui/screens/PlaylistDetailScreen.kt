package com.app.resn8.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignBottom
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.ui.components.RenamePlaylistDialog
import com.app.resn8.ui.playlists.PlaylistDetailViewModel
import com.app.resn8.ui.playlists.PlaylistItemUiModel
import com.app.resn8.ui.playlists.currentPlaylistItemIndex
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    viewModel: PlaylistDetailViewModel,
    onBack: () -> Unit,
    onTrackClick: (MediaFile) -> Unit,
    onPlayAll: () -> Unit,
    currentMediaId: String? = null,
    isCurrentTrackPlaying: Boolean = false,
    showMusicMetadata: Boolean = true,
    revealCurrentTrack: Boolean = false,
    modifier: Modifier = Modifier
) {
    val playlist by viewModel.playlist.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val filteredTracks by viewModel.filteredTracks.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val currentTrackIndex = currentPlaylistItemIndex(tracks, currentMediaId)

    var isSearchActive by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameError by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showPlaylistMenu by remember { mutableStateOf(false) }
    var revealConsumed by remember(revealCurrentTrack) { mutableStateOf(false) }
    var optimisticTracks by remember { mutableStateOf<List<PlaylistItemUiModel>?>(null) }
    var draggedMediaId by remember { mutableStateOf<String?>(null) }
    var draggedCenterY by remember { mutableStateOf(0f) }
    var reorderPending by remember { mutableStateOf(false) }
    var reorderError by remember { mutableStateOf<String?>(null) }

    val reorderEnabled = searchQuery.isEmpty() && !reorderPending
    val displayedTracks = if (searchQuery.isEmpty()) optimisticTracks ?: tracks else filteredTracks

    fun cancelDrag() {
        optimisticTracks = null
        draggedMediaId = null
        draggedCenterY = 0f
    }

    fun updateDrag(dragAmountY: Float) {
        val mediaId = draggedMediaId ?: return
        val currentItems = optimisticTracks ?: return
        draggedCenterY += dragAmountY

        val layoutInfo = listState.layoutInfo
        val edgeSize = with(density) { 56.dp.toPx() }
        when {
            draggedCenterY < layoutInfo.viewportStartOffset + edgeSize -> scope.launch {
                listState.scrollBy(-edgeSize / 2f)
            }
            draggedCenterY > layoutInfo.viewportEndOffset - edgeSize -> scope.launch {
                listState.scrollBy(edgeSize / 2f)
            }
        }

        val target = layoutInfo.visibleItemsInfo.minByOrNull { item ->
            kotlin.math.abs((item.offset + item.size / 2f) - draggedCenterY)
        } ?: return
        val fromIndex = currentItems.indexOfFirst { it.mediaFile.id == mediaId }
        val toIndex = target.index.coerceIn(0, currentItems.lastIndex)
        if (fromIndex >= 0 && fromIndex != toIndex) {
            optimisticTracks = currentItems.toMutableList().apply {
                add(toIndex, removeAt(fromIndex))
            }
        }
    }

    fun finishDrag() {
        val mediaId = draggedMediaId ?: return
        val targetIndex = optimisticTracks?.indexOfFirst { it.mediaFile.id == mediaId } ?: -1
        draggedMediaId = null
        draggedCenterY = 0f
        if (targetIndex < 0) {
            optimisticTracks = null
            return
        }
        reorderPending = true
        reorderError = null
        scope.launch {
            val result = viewModel.reorderTrack(mediaId, targetIndex)
            if (result.isFailure) reorderError = "Playlist order could not be saved"
            optimisticTracks = null
            reorderPending = false
        }
    }

    suspend fun revealCurrentMedia(targetMediaId: String) {
        if (viewModel.searchQuery.value.isNotEmpty()) {
            viewModel.searchQuery.value = ""
            snapshotFlow { Triple(searchQuery, filteredTracks, tracks) }
                .first { (query, visibleItems, allItems) ->
                    query.isEmpty() && (
                        visibleItems.any { it.mediaFile.id == targetMediaId } ||
                            allItems.none { it.mediaFile.id == targetMediaId }
                        )
                }
        }
        val targetIndex = filteredTracks.indexOfFirst { it.mediaFile.id == targetMediaId }
        if (targetIndex >= 0) listState.scrollToItem(targetIndex)
    }

    LaunchedEffect(revealCurrentTrack, currentMediaId, tracks.size) {
        val targetMediaId = currentMediaId
        if (revealCurrentTrack && !revealConsumed && targetMediaId != null && tracks.isNotEmpty()) {
            revealConsumed = true
            revealCurrentMedia(targetMediaId)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.searchQuery.value = it },
                            placeholder = { Text("Filter playlist...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            viewModel.searchQuery.value = ""
                        }) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close search")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Clear text")
                            }
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            } else {
                TopAppBar(
                    title = {
                        Text(
                            text = playlist?.name ?: "Playlist",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(imageVector = Icons.Default.Search, contentDescription = "Search Playlist")
                        }
                        Box {
                            IconButton(onClick = { showPlaylistMenu = true }) {
                                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Playlist Options")
                            }
                            DropdownMenu(
                                expanded = showPlaylistMenu,
                                onDismissRequest = { showPlaylistMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        showPlaylistMenu = false
                                        renameError = null
                                        showRenameDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        showPlaylistMenu = false
                                        showDeleteDialog = true
                                    }
                                )
                            }
                        }
                    },
                    windowInsets = WindowInsets(0, 0, 0, 0)
                )
            }
        },
        modifier = modifier
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Header Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "${filteredTracks.size} ${if (filteredTracks.size == 1) "track" else "tracks"}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (searchQuery.isNotEmpty()) {
                        Text(
                            text = "Filtering hides reorder controls",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    reorderError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Row {
                    if (currentTrackIndex >= 0 && currentMediaId != null) {
                        IconButton(
                            onClick = {
                                val targetMediaId = currentMediaId
                                scope.launch { revealCurrentMedia(targetMediaId) }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = "Jump to current track"
                            )
                        }
                    }
                    Button(
                        onClick = onPlayAll,
                        enabled = filteredTracks.any { it.mediaFile.isAvailable }
                    ) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play All")
                    }
                }
            }

            if (filteredTracks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No matching tracks found." else "Playlist is empty.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(
                        items = displayedTracks,
                        key = { _, item -> item.mediaFile.id }
                    ) { index, item ->
                        PlaylistItemRow(
                            index = if (searchQuery.isEmpty()) index + 1 else item.originalIndex,
                            mediaFile = item.mediaFile,
                            isFirst = index == 0,
                            isLast = index == filteredTracks.size - 1,
                            isSearchActive = searchQuery.isNotEmpty(),
                            isCurrent = item.mediaFile.id == currentMediaId,
                            isPlaying = isCurrentTrackPlaying,
                            isDragging = draggedMediaId == item.mediaFile.id,
                            dragEnabled = reorderEnabled,
                            showMusicMetadata = showMusicMetadata,
                            onTrackClick = {
                                if (item.mediaFile.isAvailable) {
                                    onTrackClick(item.mediaFile)
                                }
                            },
                            onMoveToTop = { viewModel.moveTrackToTop(item.mediaFile.id) },
                            onMoveUp = { viewModel.moveTrackUp(item.mediaFile.id) },
                            onMoveDown = { viewModel.moveTrackDown(item.mediaFile.id) },
                            onMoveToBottom = { viewModel.moveTrackToBottom(item.mediaFile.id) },
                            onRemove = { viewModel.removeTrack(item.mediaFile.id) },
                            onDragStart = {
                                optimisticTracks = tracks
                                draggedMediaId = item.mediaFile.id
                                val visibleItem = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.key == item.mediaFile.id }
                                draggedCenterY = visibleItem?.let { it.offset + it.size / 2f } ?: 0f
                            },
                            onDrag = ::updateDrag,
                            onDragEnd = ::finishDrag,
                            onDragCancel = ::cancelDrag
                        )
                    }
                }
            }
        }
    }

    if (showRenameDialog && playlist != null) {
        RenamePlaylistDialog(
            currentName = playlist!!.name,
            errorMessage = renameError,
            onDismissRequest = { showRenameDialog = false },
            onRenamePlaylist = { newName ->
                scope.launch {
                    val result = viewModel.renamePlaylist(newName)
                    if (result.isSuccess) {
                        showRenameDialog = false
                    } else {
                        renameError = result.exceptionOrNull()?.message ?: "Failed to rename"
                    }
                }
            }
        )
    }

    if (showDeleteDialog && playlist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete '${playlist!!.name}'? Audio files on disk will NOT be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deletePlaylist(onDeleted = onBack)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
internal fun PlaylistItemRow(
    index: Int,
    mediaFile: MediaFile,
    isFirst: Boolean,
    isLast: Boolean,
    isSearchActive: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    isDragging: Boolean = false,
    dragEnabled: Boolean = false,
    showMusicMetadata: Boolean,
    onTrackClick: () -> Unit,
    onMoveToTop: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onMoveToBottom: () -> Unit,
    onRemove: () -> Unit,
    onDragStart: () -> Unit = {},
    onDrag: (Float) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                if (isCurrent) {
                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                } else {
                    MaterialTheme.colorScheme.surface
                }
            )
            .graphicsLayer {
                shadowElevation = if (isDragging) 12.dp.toPx() else 0f
                scaleX = if (isDragging) 1.01f else 1f
                scaleY = if (isDragging) 1.01f else 1f
            }
            .then(
                if (isCurrent) {
                    Modifier.semantics(mergeDescendants = true) {
                        stateDescription = if (isPlaying) {
                            "Currently playing"
                        } else {
                            "Current track, paused"
                        }
                    }
                } else {
                    Modifier
                }
            )
            .clickable(enabled = mediaFile.isAvailable, onClick = onTrackClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .width(48.dp)
                .heightIn(min = 48.dp)
                .semantics {
                    contentDescription = if (dragEnabled) {
                        "Reorder track $index: ${mediaFile.displayTitle}. Long press and drag."
                    } else {
                        "Track $index"
                    }
                }
                .then(
                    if (dragEnabled) {
                        Modifier.pointerInput(mediaFile.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { onDragStart() },
                                onDragEnd = onDragEnd,
                                onDragCancel = onDragCancel,
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    onDrag(dragAmount.y)
                                }
                            )
                        }
                    } else {
                        Modifier
                    }
                )
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (mediaFile.isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = mediaFile.displayTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (showMusicMetadata) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    color = if (mediaFile.isAvailable) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isCurrent) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                if (!mediaFile.isAvailable) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(Unavailable)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            val subtitle = if (showMusicMetadata) {
                listOfNotNull(mediaFile.artist, mediaFile.album).joinToString(" • ")
            } else null
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (mediaFile.isAvailable) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Item Actions")
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (!isSearchActive) {
                    if (!isFirst) {
                        DropdownMenuItem(
                            text = { Text("Move to Top") },
                            leadingIcon = { Icon(Icons.Default.VerticalAlignTop, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMoveToTop()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move Up") },
                            leadingIcon = { Icon(Icons.Default.ArrowUpward, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMoveUp()
                            }
                        )
                    }
                    if (!isLast) {
                        DropdownMenuItem(
                            text = { Text("Move Down") },
                            leadingIcon = { Icon(Icons.Default.ArrowDownward, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMoveDown()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Move to Bottom") },
                            leadingIcon = { Icon(Icons.Default.VerticalAlignBottom, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onMoveToBottom()
                            }
                        )
                    }
                }
                DropdownMenuItem(
                    text = { Text("Remove from Playlist") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    onClick = {
                        showMenu = false
                        onRemove()
                    }
                )
            }
        }
    }
}
