package com.app.resn8.data.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.StagedFolder
import com.app.resn8.domain.model.StagedMedia
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScanPersistenceTest {

    private lateinit var db: Resn8Database
    private lateinit var collectionRepo: RoomCollectionRepository
    private lateinit var mediaRepo: RoomMediaRepository

    @Before
    fun setUp() {
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        collectionRepo = RoomCollectionRepository(db)
        mediaRepo = RoomMediaRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun scanStaging_isolatedFromCanonicalQueries_untilPublished() = runBlocking {
        val col = collectionRepo.createCollection("Main Music")
        val src = collectionRepo.addRootSource(col.id, "tree://music", "Music Folder")

        val scanId = mediaRepo.startScanRun(src.id)
        mediaRepo.stageFolders(scanId, listOf(
            StagedFolder("sf1", scanId, "Artist", null, "Artist")
        ))
        mediaRepo.stageMedia(scanId, listOf(
            StagedMedia(
                id = "sm1", scanId = scanId, documentUri = "uri://track1", relativePath = "Artist/track1.mp3",
                filename = "track1.mp3", displayTitle = "Track 1", mimeType = "audio/mpeg", size = 1000L, modifiedTimeMs = 1000L
            )
        ))

        // Canonical queries before publish are empty
        val mediaList = mediaRepo.getMediaFilesFlow(col.id).first()
        assertTrue(mediaList.isEmpty())

        // Publish resolved scan
        val folderNode = FolderNode("f1", src.id, null, "Artist", "Artist")
        val initialFirstIndexed = 123456789L
        val mediaFile = MediaFile(
            id = "m1", sourceId = src.id, folderId = "f1", documentUri = "uri://track1", relativePath = "Artist/track1.mp3",
            filename = "track1.mp3", displayTitle = "Track 1", mimeType = "audio/mpeg", size = 1000L, modifiedTimeMs = 1000L,
            firstIndexedAt = initialFirstIndexed, isAvailable = true
        )
        val scanResult = ScanResult(1, 1, 0, 0, 0, 0, 0, 0, 500L)

        mediaRepo.publishResolvedScan(scanId, listOf(folderNode), listOf(mediaFile), emptyList(), scanResult)

        // Canonical query now returns published media
        val published = mediaRepo.getMediaFilesFlow(col.id).first()
        assertEquals(1, published.size)
        assertEquals("m1", published[0].id)
        assertEquals(initialFirstIndexed, published[0].firstIndexedAt)
    }

    @Test
    fun reindexing_preservesFirstIndexedAt_andUserStats() = runBlocking {
        val col = collectionRepo.createCollection("Main Music")
        val src = collectionRepo.addRootSource(col.id, "tree://music", "Music Folder")

        val folder = FolderNode("f1", src.id, null, "", "Root")
        val firstIndexedTime = 5000L
        val media = MediaFile(
            id = "m1", sourceId = src.id, folderId = "f1", documentUri = "uri://track1", relativePath = "track1.mp3",
            filename = "track1.mp3", displayTitle = "Track 1", mimeType = "audio/mpeg", size = 1000L, modifiedTimeMs = 1000L,
            firstIndexedAt = firstIndexedTime, isAvailable = true, likeScore = 1, playCount = 5
        )

        val scan1 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scan1, listOf(folder), listOf(media), emptyList(), ScanResult(1, 1, 0, 0, 0, 0, 0, 0, 100L))

        // Perform re-index with updated metadata scan time
        val reindexedMedia = media.copy(
            displayTitle = "Track One (Remastered)",
            firstIndexedAt = System.currentTimeMillis() // Attempt new firstIndexedAt
        )
        val scan2 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scan2, listOf(folder), listOf(reindexedMedia), emptyList(), ScanResult(1, 0, 1, 0, 0, 0, 0, 0, 100L))

        val retrieved = mediaRepo.getMediaFileById("m1")
        assertNotNull(retrieved)
        assertEquals("Track One (Remastered)", retrieved?.displayTitle)
        assertEquals(firstIndexedTime, retrieved?.firstIndexedAt) // Retained!
        assertEquals(1, retrieved?.likeScore) // Retained!
        assertEquals(5, retrieved?.playCount) // Retained!
    }

    @Test
    fun publishingScan_marksMissingFilesUnavailable() = runBlocking {
        val col = collectionRepo.createCollection("Main Music")
        val src = collectionRepo.addRootSource(col.id, "tree://music", "Music Folder")

        val folder = FolderNode("f1", src.id, null, "", "Root")
        val media1 = MediaFile(id = "m1", sourceId = src.id, folderId = "f1", documentUri = "uri://1", relativePath = "1.mp3", filename = "1.mp3", displayTitle = "1", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)
        val media2 = MediaFile(id = "m2", sourceId = src.id, folderId = "f1", documentUri = "uri://2", relativePath = "2.mp3", filename = "2.mp3", displayTitle = "2", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)

        val scan1 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scan1, listOf(folder), listOf(media1, media2), emptyList(), ScanResult(2, 2, 0, 0, 0, 0, 0, 0, 100))

        // Second scan finds media1, but media2 is absent
        val scan2 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scan2, listOf(folder), listOf(media1), listOf("m2"), ScanResult(2, 0, 1, 1, 0, 0, 0, 0, 100))

        val m1Retrieved = mediaRepo.getMediaFileById("m1")
        val m2Retrieved = mediaRepo.getMediaFileById("m2")

        assertTrue(m1Retrieved!!.isAvailable)
        assertFalse(m2Retrieved!!.isAvailable)
    }
}
