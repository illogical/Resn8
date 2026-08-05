package com.app.resn8.ui

import com.app.resn8.data.repository.FakeCollectionRepository
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueItem
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.restorableQueueIdForCollection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CollectionPlaybackPolishTest {
    @Test
    fun fakeRepositoryKeepsAnIndependentQueuePointerPerCollection() = runTest {
        val repository = FakeCollectionRepository()
        val music = repository.createCollection("Music", CollectionProfile.MUSIC)
        val spoken = repository.createCollection("Spoken", CollectionProfile.FLAT)

        repository.setCollectionActiveQueue(music.id, "music-queue")
        repository.setCollectionActiveQueue(spoken.id, "spoken-queue")

        assertEquals("music-queue", repository.getCollectionPlaybackStateFlow(music.id).first()?.activeQueueId)
        assertEquals("spoken-queue", repository.getCollectionPlaybackState(spoken.id)?.activeQueueId)

        repository.deleteCollection(music.id)
        assertNull(repository.getCollectionPlaybackState(music.id))
        assertEquals(listOf(spoken.id), repository.getCollectionsFlow().first().map { it.id })
    }

    @Test
    fun switchQueueMustExistBelongToTargetAndContainAnOccurrence() {
        val valid = SavedQueue(
            id = "queue-a",
            collectionId = "collection-a",
            kind = SavedQueueKind.EXPLICIT,
            items = listOf(SavedQueueItem("occurrence-a", "media-a")),
            orderedMediaIds = listOf("media-a")
        )

        assertEquals("queue-a", restorableQueueIdForCollection("collection-a", "queue-a", valid))
        assertNull(restorableQueueIdForCollection("collection-b", "queue-a", valid))
        assertNull(restorableQueueIdForCollection("collection-a", "queue-b", valid))
        assertNull(restorableQueueIdForCollection("collection-a", "queue-a", valid.copy(items = emptyList())))
        assertNull(restorableQueueIdForCollection("collection-a", "queue-a", null))
    }
}
