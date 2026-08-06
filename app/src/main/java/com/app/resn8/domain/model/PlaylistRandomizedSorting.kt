package com.app.resn8.domain.model

import kotlin.random.Random

enum class PlaylistRandomizedSortMethod {
    LEAST_PLAYED,
    MOST_PLAYED,
    MOST_LIKED,
    RECENTLY_ADDED
}

data class PlaylistRandomizationResult(
    val orderedMediaIds: List<String>,
    val availableOrderedMediaIds: List<String>,
    val removedDislikedCount: Int
)

data class PlaylistRandomizationOrder(
    val orderedMedia: List<MediaFile>,
    val removedDislikedMediaIds: List<String>
)

object PlaylistRandomizedSorter {
    fun sort(
        mediaFiles: List<MediaFile>,
        method: PlaylistRandomizedSortMethod,
        random: Random
    ): PlaylistRandomizationOrder {
        val (disliked, eligible) = mediaFiles.partition { it.likeScore < 0 }
        val groups = eligible.groupBy { mediaFile ->
            when (method) {
                PlaylistRandomizedSortMethod.LEAST_PLAYED,
                PlaylistRandomizedSortMethod.MOST_PLAYED -> mediaFile.playCount.toLong()
                PlaylistRandomizedSortMethod.MOST_LIKED -> mediaFile.likeScore.toLong()
                PlaylistRandomizedSortMethod.RECENTLY_ADDED -> mediaFile.firstIndexedAt
            }
        }
        val keys = when (method) {
            PlaylistRandomizedSortMethod.LEAST_PLAYED -> groups.keys.sorted()
            PlaylistRandomizedSortMethod.MOST_PLAYED,
            PlaylistRandomizedSortMethod.MOST_LIKED,
            PlaylistRandomizedSortMethod.RECENTLY_ADDED -> groups.keys.sortedDescending()
        }
        return PlaylistRandomizationOrder(
            orderedMedia = keys.flatMap { key -> groups.getValue(key).shuffled(random) },
            removedDislikedMediaIds = disliked.map { it.id }
        )
    }
}
