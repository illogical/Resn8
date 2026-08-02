package com.app.resn8.domain.model

import kotlinx.serialization.Serializable

enum class SavedQueueKind {
    EXPLICIT,
    GENERATED
}

enum class SmartQueueMode {
    RANDOM_ELIGIBLE,
    UNPLAYED,
    LEAST_PLAYED,
    MOST_PLAYED,
    MOST_LIKED,
    MOST_RECENTLY_PLAYED,
    LEAST_RECENTLY_PLAYED
}

@Serializable
data class QueueFilterSnapshot(
    val collectionId: String? = null,
    val folderId: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val searchQuery: String? = null,
    val excludeDisliked: Boolean = true
)

data class SavedQueueItem(
    val queueItemId: String,
    val mediaId: String
)

data class SavedQueue(
    val id: String,
    val collectionId: String,
    val kind: SavedQueueKind,
    val mode: SmartQueueMode? = null,
    val filterSnapshot: QueueFilterSnapshot? = null,
    val seed: Long? = null,
    val orderedMediaIds: List<String> = emptyList(),
    val items: List<SavedQueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val currentMediaId: String? = null,
    val currentOccurrenceId: String? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playWhenReadyIntent: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
