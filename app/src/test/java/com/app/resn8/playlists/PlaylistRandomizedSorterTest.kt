package com.app.resn8.playlists

import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaylistRandomizedSortMethod
import com.app.resn8.domain.model.PlaylistRandomizedSorter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PlaylistRandomizedSorterTest {
    @Test
    fun mostLiked_removesDisliked_ordersScoreGroupsAndShufflesTies() {
        val input = listOf(
            media("a", likeScore = 3),
            media("b", likeScore = 3),
            media("c", likeScore = 1),
            media("d", likeScore = 0),
            media("e", likeScore = 0),
            media("f", likeScore = -1)
        )

        val result = PlaylistRandomizedSorter.sort(
            input,
            PlaylistRandomizedSortMethod.MOST_LIKED,
            Random(7)
        )

        assertEquals(listOf("f"), result.removedDislikedMediaIds)
        assertEquals(listOf(3, 3, 1, 0, 0), result.orderedMedia.map { it.likeScore })
        assertEquals(setOf("a", "b"), result.orderedMedia.take(2).map { it.id }.toSet())
        assertEquals(setOf("d", "e"), result.orderedMedia.takeLast(2).map { it.id }.toSet())
    }

    @Test
    fun methods_orderExactGroupsInTheRequiredDirection() {
        val input = listOf(
            media("old-high", playCount = 9, firstIndexedAt = 10),
            media("new-low", playCount = 0, firstIndexedAt = 30),
            media("middle", playCount = 4, firstIndexedAt = 20)
        )

        val least = PlaylistRandomizedSorter.sort(input, PlaylistRandomizedSortMethod.LEAST_PLAYED, Random(1))
        val most = PlaylistRandomizedSorter.sort(input, PlaylistRandomizedSortMethod.MOST_PLAYED, Random(1))
        val recent = PlaylistRandomizedSorter.sort(input, PlaylistRandomizedSortMethod.RECENTLY_ADDED, Random(1))

        assertEquals(listOf("new-low", "middle", "old-high"), least.orderedMedia.map { it.id })
        assertEquals(listOf("old-high", "middle", "new-low"), most.orderedMedia.map { it.id })
        assertEquals(listOf("new-low", "middle", "old-high"), recent.orderedMedia.map { it.id })
    }

    @Test
    fun equalTimestampTracksAreOneRandomizedGroup() {
        val input = (1..8).map { media("m$it", firstIndexedAt = 100) }
        val first = PlaylistRandomizedSorter.sort(input, PlaylistRandomizedSortMethod.RECENTLY_ADDED, Random(1))
        val second = PlaylistRandomizedSorter.sort(input, PlaylistRandomizedSortMethod.RECENTLY_ADDED, Random(2))

        assertEquals(input.map { it.id }.toSet(), first.orderedMedia.map { it.id }.toSet())
        assertNotEquals(first.orderedMedia.map { it.id }, second.orderedMedia.map { it.id })
    }

    @Test
    fun allDislikedProducesAnEmptyRetainedOrder() {
        val result = PlaylistRandomizedSorter.sort(
            listOf(media("a", likeScore = -1), media("b", likeScore = -4)),
            PlaylistRandomizedSortMethod.LEAST_PLAYED,
            Random(1)
        )

        assertTrue(result.orderedMedia.isEmpty())
        assertEquals(setOf("a", "b"), result.removedDislikedMediaIds.toSet())
    }

    private fun media(
        id: String,
        playCount: Int = 0,
        likeScore: Int = 0,
        firstIndexedAt: Long = 0
    ) = MediaFile(
        id = id,
        sourceId = "source",
        folderId = "folder",
        documentUri = "uri://$id",
        relativePath = "$id.mp3",
        filename = "$id.mp3",
        displayTitle = id,
        mimeType = "audio/mpeg",
        size = 1,
        modifiedTimeMs = 1,
        firstIndexedAt = firstIndexedAt,
        playCount = playCount,
        likeScore = likeScore
    )
}
