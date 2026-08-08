package com.app.resn8.widget

import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueItem
import com.app.resn8.domain.model.SavedQueueKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWidgetSnapshotTest {
    @Test
    fun upcomingPreservesDuplicateOccurrenceIdentityAndSkipsUnavailableMedia() {
        val queue = queue(
            SavedQueueItem("occurrence-a", "media-a"),
            SavedQueueItem("occurrence-unavailable", "media-b"),
            SavedQueueItem("occurrence-a-duplicate", "media-a"),
            SavedQueueItem("occurrence-c", "media-c")
        )
        val snapshot = buildPlaybackWidgetSnapshot(
            queue = queue,
            profile = CollectionProfile.MUSIC,
            mediaById = mapOf(
                "media-a" to media("media-a", "A", "Artist A"),
                "media-b" to media("media-b", "B", "Artist B", available = false),
                "media-c" to media("media-c", "C", "Artist C")
            ),
            playerState = playerState("occurrence-a", 0),
            artworkUri = null
        )

        assertEquals(listOf("occurrence-a-duplicate", "occurrence-c"), snapshot.upcoming.map { it.queueItemId })
        assertEquals(listOf("A", "C"), snapshot.upcoming.map { it.title })
    }

    @Test
    fun flatCollectionOmitsSyntheticMusicMetadata() {
        val queue = queue(SavedQueueItem("occurrence", "media"))
        val snapshot = buildPlaybackWidgetSnapshot(
            queue = queue,
            profile = CollectionProfile.FLAT,
            mediaById = mapOf("media" to media("media", "Episode.mp3", null)),
            playerState = playerState("occurrence", 0),
            artworkUri = null
        )

        assertEquals("Episode.mp3", snapshot.title)
        assertEquals("", snapshot.secondaryText)
        assertTrue(snapshot.upcoming.isEmpty())
    }

    @Test
    fun controllerOccurrenceWinsPersistedIndexAndBoundaryControlsAreClamped() {
        val queue = queue(
            SavedQueueItem("first", "media-a"),
            SavedQueueItem("second", "media-b")
        ).copy(currentIndex = 0)
        val snapshot = buildPlaybackWidgetSnapshot(
            queue = queue,
            profile = CollectionProfile.MUSIC,
            mediaById = mapOf(
                "media-a" to media("media-a", "A", "Artist A"),
                "media-b" to media("media-b", "B", "Artist B", score = -1)
            ),
            playerState = playerState("second", 1),
            artworkUri = null
        )

        assertEquals("second", snapshot.currentQueueItemId)
        assertEquals("B", snapshot.title)
        assertEquals("Disliked", ratingLabel(snapshot.likeScore))
        assertTrue(snapshot.canSkipPrevious)
        assertFalse(snapshot.canSkipNext)
    }

    @Test
    fun positiveNeutralAndDislikedLabelsAreExplicit() {
        assertEquals("+3", ratingLabel(3))
        assertEquals("Neutral", ratingLabel(0))
        assertEquals("Disliked", ratingLabel(-1))
    }

    @Test
    fun ratingOverlaysConsolidateScoreWithoutLosingFullAccessibilityValue() {
        assertEquals("", likeOverlayLabel(0))
        assertEquals("+2", likeOverlayLabel(2))
        assertEquals("99+", likeOverlayLabel(100))
        assertEquals(
            "Like current track, current score +125",
            ratingContentDescription("Like current track", 125)
        )
        assertEquals(
            "Dislike current track, current score Disliked",
            ratingContentDescription("Dislike current track", -1)
        )
    }

    private fun queue(vararg items: SavedQueueItem) = SavedQueue(
        id = "active-queue",
        collectionId = "collection",
        kind = SavedQueueKind.EXPLICIT,
        orderedMediaIds = items.map { it.mediaId },
        items = items.toList()
    )

    private fun playerState(queueItemId: String, index: Int) = PlaybackWidgetPlayerState(
        currentQueueItemId = queueItemId,
        currentIndex = index,
        isPlaying = false,
        canPlayPause = true,
        canSkipPrevious = true,
        canSkipNext = true,
        canRate = true
    )

    private fun media(
        id: String,
        title: String,
        artist: String?,
        available: Boolean = true,
        score: Int = 0
    ) = MediaFile(
        id = id,
        sourceId = "source",
        folderId = "folder",
        documentUri = "content://media/$id",
        relativePath = "$title.mp3",
        filename = "$title.mp3",
        displayTitle = title,
        mimeType = "audio/mpeg",
        size = 1L,
        modifiedTimeMs = 1L,
        isAvailable = available,
        artist = artist,
        likeScore = score
    )
}
