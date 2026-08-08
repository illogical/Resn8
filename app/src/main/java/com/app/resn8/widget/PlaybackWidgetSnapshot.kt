package com.app.resn8.widget

import android.graphics.Bitmap
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SavedQueue

internal enum class PlaybackWidgetStatus {
    READY,
    EMPTY,
    ERROR
}

internal data class PlaybackWidgetQueueRow(
    val queueItemId: String,
    val title: String,
    val secondaryText: String
)

internal data class PlaybackWidgetSnapshot(
    val status: PlaybackWidgetStatus,
    val emptyDestination: WidgetDestination = WidgetDestination.ONBOARDING,
    val currentQueueItemId: String? = null,
    val currentMediaId: String? = null,
    val title: String = "",
    val secondaryText: String = "",
    val artworkUri: String? = null,
    val artwork: Bitmap? = null,
    val likeScore: Int = 0,
    val isPlaying: Boolean = false,
    val canPlayPause: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val canRate: Boolean = false,
    val upcoming: List<PlaybackWidgetQueueRow> = emptyList(),
    val message: String? = null
)

internal data class PlaybackWidgetPlayerState(
    val currentQueueItemId: String?,
    val currentIndex: Int,
    val isPlaying: Boolean,
    val canPlayPause: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val canRate: Boolean
)

internal fun buildPlaybackWidgetSnapshot(
    queue: SavedQueue,
    profile: CollectionProfile,
    mediaById: Map<String, MediaFile>,
    playerState: PlaybackWidgetPlayerState,
    artworkUri: String?,
    artwork: Bitmap? = null
): PlaybackWidgetSnapshot {
    if (queue.items.isEmpty()) {
        return PlaybackWidgetSnapshot(
            status = PlaybackWidgetStatus.EMPTY,
            emptyDestination = WidgetDestination.FOLDERS
        )
    }

    val resolvedIndex = when {
        playerState.currentQueueItemId != null -> queue.items.indexOfFirst {
            it.queueItemId == playerState.currentQueueItemId
        }
        else -> playerState.currentIndex
    }.takeIf { it in queue.items.indices }
        ?: queue.currentIndex.coerceIn(queue.items.indices)

    val currentItem = queue.items[resolvedIndex]
    val currentMedia = mediaById[currentItem.mediaId]
    val isFlat = profile == CollectionProfile.FLAT
    val upcoming = queue.items
        .drop(resolvedIndex + 1)
        .mapNotNull { item ->
            val media = mediaById[item.mediaId]?.takeIf { it.isAvailable } ?: return@mapNotNull null
            PlaybackWidgetQueueRow(
                queueItemId = item.queueItemId,
                title = media.displayTitle,
                secondaryText = if (isFlat) "" else media.artist ?: "Unknown Artist"
            )
        }
        .take(3)

    return PlaybackWidgetSnapshot(
        status = PlaybackWidgetStatus.READY,
        currentQueueItemId = currentItem.queueItemId,
        currentMediaId = currentItem.mediaId,
        title = currentMedia?.displayTitle ?: "Unavailable track",
        secondaryText = if (isFlat) "" else currentMedia?.artist ?: "Unknown Artist",
        artworkUri = artworkUri,
        artwork = artwork,
        likeScore = currentMedia?.likeScore ?: 0,
        isPlaying = playerState.isPlaying,
        canPlayPause = playerState.canPlayPause,
        canSkipPrevious = playerState.canSkipPrevious && resolvedIndex > 0,
        canSkipNext = playerState.canSkipNext && resolvedIndex < queue.items.lastIndex,
        canRate = playerState.canRate && currentMedia != null,
        upcoming = upcoming
    )
}

internal fun ratingLabel(score: Int): String = when {
    score > 0 -> "+$score"
    score < 0 -> "Disliked"
    else -> "Neutral"
}

internal fun likeOverlayLabel(score: Int): String = when {
    score > 99 -> "99+"
    score > 0 -> "+$score"
    else -> ""
}

internal fun ratingContentDescription(action: String, score: Int): String =
    "$action, current score ${ratingLabel(score)}"
