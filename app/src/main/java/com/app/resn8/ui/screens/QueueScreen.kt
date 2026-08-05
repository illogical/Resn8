package com.app.resn8.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.app.resn8.playback.PlaybackQueueItemState

@Composable
fun QueueScreen(
    queueItems: List<PlaybackQueueItemState> = emptyList(),
    currentQueueItemId: String? = null,
    queueTitle: String? = null,
    sourcePlaylistId: String? = null,
    onOpenPlaylist: ((String) -> Unit)? = null,
    onItemClick: (String) -> Unit = {},
    onSaveAsPlaylist: (List<String>, title: String, subtitle: String?) -> Unit = { _, _, _ -> },
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val headerText = queueTitle ?: "Active Playback Queue"
                if (sourcePlaylistId != null && onOpenPlaylist != null) {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onOpenPlaylist(sourcePlaylistId) }
                    )
                    Text(
                        text = "Tap to open playlist detail",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                } else {
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${queueItems.size} items in queue",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (queueItems.isNotEmpty()) {
                Button(
                    onClick = {
                        val mediaIds = queueItems.map { it.mediaId }
                        val distinctCount = mediaIds.distinct().size
                        val title = "Save Queue as Playlist"
                        val subtitle = if (distinctCount < mediaIds.size) {
                            "$distinctCount unique tracks from ${mediaIds.size} queue items (duplicates collapsed)"
                        } else {
                            "$distinctCount tracks"
                        }
                        onSaveAsPlaylist(mediaIds, title, subtitle)
                    }
                ) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Save")
                }
            }
        }

        Spacer(modifier = Modifier.padding(bottom = 12.dp))

        if (queueItems.isEmpty()) {
            Text(
                text = "Queue is empty. Select music from the library or folders to play.",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 32.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(
                    items = queueItems,
                    key = { _, item -> item.queueItemId }
                ) { index, item ->
                    val isCurrent = item.queueItemId == currentQueueItemId
                    var showRowMenu by remember { mutableStateOf(false) }

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onItemClick(item.queueItemId) },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainer
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(end = 12.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${item.artist} • ${item.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isCurrent) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            if (isCurrent) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                    contentDescription = "Playing",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp).padding(end = 4.dp)
                                )
                            }

                            Box {
                                IconButton(onClick = { showRowMenu = true }) {
                                    Icon(imageVector = Icons.Default.MoreVert, contentDescription = "Item actions")
                                }
                                DropdownMenu(
                                    expanded = showRowMenu,
                                    onDismissRequest = { showRowMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Add to Playlist") },
                                        leadingIcon = {
                                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                        },
                                        onClick = {
                                            showRowMenu = false
                                            onSaveAsPlaylist(listOf(item.mediaId), "Add '${item.title}' to Playlist", null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
