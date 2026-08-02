package com.app.resn8.domain.model

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

data class QueueFilterSnapshot(
    val collectionId: String? = null,
    val folderId: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val searchQuery: String? = null,
    val excludeDisliked: Boolean = true
)

data class SavedQueue(
    val id: String,
    val collectionId: String,
    val kind: SavedQueueKind,
    val mode: SmartQueueMode? = null,
    val filterSnapshot: QueueFilterSnapshot? = null,
    val seed: Long? = null,
    val orderedMediaIds: List<String> = emptyList(),
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)
