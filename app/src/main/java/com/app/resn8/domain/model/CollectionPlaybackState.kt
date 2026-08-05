package com.app.resn8.domain.model

data class CollectionPlaybackState(
    val collectionId: String,
    val activeQueueId: String?,
    val updatedAt: Long
)

data class CollectionSummary(
    val collection: Collection,
    val totalTrackCount: Int,
    val unavailableTrackCount: Int
)

fun restorableQueueIdForCollection(
    collectionId: String,
    storedQueueId: String?,
    queue: SavedQueue?
): String? = queue
    ?.takeIf { storedQueueId == it.id && it.collectionId == collectionId && it.items.isNotEmpty() }
    ?.id
