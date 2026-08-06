package com.app.resn8.playback

import com.app.resn8.domain.model.QueueFilterSnapshot
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RandomizedPlaylistPlaybackPolicyTest {
    @Test
    fun onlyTheExactSourcePlaylistIsSynchronized() {
        val playlistQueue = queue(QueueFilterSnapshot(playlistId = "playlist-a"))
        val libraryQueue = queue(QueueFilterSnapshot(collectionId = "collection"))

        assertTrue(shouldSynchronizeRandomizedPlaylist(playlistQueue, "playlist-a"))
        assertFalse(shouldSynchronizeRandomizedPlaylist(playlistQueue, "playlist-b"))
        assertFalse(shouldSynchronizeRandomizedPlaylist(libraryQueue, "playlist-a"))
    }

    private fun queue(filter: QueueFilterSnapshot) = SavedQueue(
        id = "queue",
        collectionId = "collection",
        kind = SavedQueueKind.EXPLICIT,
        filterSnapshot = filter
    )
}
