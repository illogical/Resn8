package com.app.resn8.data.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.data.repository.RoomQueueRepository
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.ScanResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QueuePersistenceTest {

    private lateinit var db: Resn8Database
    private lateinit var collectionRepo: RoomCollectionRepository
    private lateinit var mediaRepo: RoomMediaRepository
    private lateinit var queueRepo: RoomQueueRepository
    private lateinit var colId: String

    @Before
    fun setUp() = runBlocking {
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        collectionRepo = RoomCollectionRepository(db)
        mediaRepo = RoomMediaRepository(db)
        queueRepo = RoomQueueRepository(db)

        val col = collectionRepo.createCollection("Main")
        colId = col.id
        val src = collectionRepo.addRootSource(colId, "tree://music", "Music")
        val folder = FolderNode("f1", src.id, null, "", "Root")
        val media1 = MediaFile(id = "m1", sourceId = src.id, folderId = "f1", documentUri = "uri://1", relativePath = "1.mp3", filename = "1.mp3", displayTitle = "1", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)
        val media2 = MediaFile(id = "m2", sourceId = src.id, folderId = "f1", documentUri = "uri://2", relativePath = "2.mp3", filename = "2.mp3", displayTitle = "2", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)

        val scanId = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scanId, listOf(folder), listOf(media1, media2), emptyList(), ScanResult(2, 2, 0, 0, 0, 0, 0, 0, 100))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun replaceQueueSnapshot_assignsUniqueQueueItemIds_andPreservesOrder() = runBlocking {
        val initialQueue = SavedQueue(
            id = "q1",
            collectionId = colId,
            kind = SavedQueueKind.EXPLICIT
        )

        // Queue contains m1, m2, and m1 again (duplicate media ID)
        val saved = queueRepo.replaceQueueSnapshot(initialQueue, listOf("m1", "m2", "m1"))

        assertEquals(3, saved.items.size)
        assertEquals("m1", saved.items[0].mediaId)
        assertEquals("m2", saved.items[1].mediaId)
        assertEquals("m1", saved.items[2].mediaId)

        // Verify distinct queue item IDs for duplicate media IDs
        assertNotEquals(saved.items[0].queueItemId, saved.items[2].queueItemId)

        val active = queueRepo.getActiveQueueFlow().first()
        assertNotNull(active)
        assertEquals("q1", active?.id)
        assertEquals(3, active?.items?.size)
    }

    @Test
    fun updatePlaybackCheckpoint_validatesAndPersistsState() = runBlocking {
        val queue = SavedQueue(id = "q1", collectionId = colId, kind = SavedQueueKind.EXPLICIT)
        queueRepo.replaceQueueSnapshot(queue, listOf("m1", "m2"))

        val occurrenceId = "occ_restore_1"
        queueRepo.updatePlaybackCheckpoint(
            queueId = "q1",
            currentIndex = 1,
            currentMediaId = "m2",
            currentOccurrenceId = occurrenceId,
            positionMs = 45000L,
            playWhenReadyIntent = true,
            playbackSpeed = 1.25f,
            repeatMode = RepeatMode.ALL
        )

        val restored = queueRepo.getActiveQueueFlow().first()
        assertNotNull(restored)
        assertEquals(1, restored?.currentIndex)
        assertEquals("m2", restored?.currentMediaId)
        assertEquals(occurrenceId, restored?.currentOccurrenceId)
        assertEquals(45000L, restored?.positionMs)
        assertEquals(true, restored?.playWhenReadyIntent)
        assertEquals(1.25f, restored?.playbackSpeed)
        assertEquals(RepeatMode.ALL, restored?.repeatMode)
    }
}
