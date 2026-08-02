package com.app.resn8.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.database.Resn8Database
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
class ScanOrchestratorTest {

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
    fun tier1Match_documentUriMatch_preservesMediaIdAndUserStats() = runBlocking {
        val col = collectionRepo.createCollection("Music Collection")
        val src = collectionRepo.addRootSource(col.id, "content://tree/music", "Music Folder")

        val initialIndexedAt = 100000L
        val originalMedia = MediaFile(
            id = "media_1",
            sourceId = src.id,
            folderId = "folder_root",
            documentUri = "content://doc/1",
            documentId = "doc_1",
            relativePath = "Track1.mp3",
            filename = "Track1.mp3",
            displayTitle = "Track 1",
            mimeType = "audio/mpeg",
            size = 5000L,
            modifiedTimeMs = 2000L,
            firstIndexedAt = initialIndexedAt,
            isAvailable = true,
            playCount = 10,
            likeScore = 1
        )
        val rootFolder = FolderNode("folder_root", src.id, null, "", "Root")

        val scan1 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(
            scan1,
            listOf(rootFolder),
            listOf(originalMedia),
            emptyList(),
            ScanResult(1, 1, 0, 0, 0, 0, 0, 0, 100L)
        )

        // Rescan with updated title and new firstIndexedAt attempt
        val rescannedMedia = originalMedia.copy(
            displayTitle = "Track 1 (Remastered)",
            firstIndexedAt = System.currentTimeMillis()
        )
        val scan2 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(
            scan2,
            listOf(rootFolder),
            listOf(rescannedMedia),
            emptyList(),
            ScanResult(1, 0, 1, 0, 0, 0, 0, 0, 100L)
        )

        val retrieved = mediaRepo.getMediaFileById("media_1")
        assertNotNull(retrieved)
        assertEquals("Track 1 (Remastered)", retrieved?.displayTitle)
        assertEquals(initialIndexedAt, retrieved?.firstIndexedAt)
        assertEquals(10, retrieved?.playCount)
        assertEquals(1, retrieved?.likeScore)
    }

    @Test
    fun tier2Match_relativePathMatch_preservesMediaId() = runBlocking {
        val col = collectionRepo.createCollection("Music Collection")
        val src = collectionRepo.addRootSource(col.id, "content://tree/music", "Music Folder")

        val originalMedia = MediaFile(
            id = "media_path_match",
            sourceId = src.id,
            folderId = "folder_root",
            documentUri = "content://doc/old_uri",
            documentId = "old_doc_id",
            relativePath = "Artist/Album/Song.mp3",
            filename = "Song.mp3",
            displayTitle = "Song",
            mimeType = "audio/mpeg",
            size = 4000L,
            modifiedTimeMs = 1500L,
            firstIndexedAt = 50000L
        )
        val rootFolder = FolderNode("folder_root", src.id, null, "", "Root")

        val scan1 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(
            scan1,
            listOf(rootFolder),
            listOf(originalMedia),
            emptyList(),
            ScanResult(1, 1, 0, 0, 0, 0, 0, 0, 100L)
        )

        // Rescan with changed URI but same relative path
        val updatedUriMedia = originalMedia.copy(
            documentUri = "content://doc/new_uri",
            documentId = "new_doc_id"
        )
        val scan2 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(
            scan2,
            listOf(rootFolder),
            listOf(updatedUriMedia),
            emptyList(),
            ScanResult(1, 0, 1, 0, 0, 0, 0, 0, 100L)
        )

        val retrieved = mediaRepo.getMediaFileById("media_path_match")
        assertNotNull(retrieved)
        assertEquals("content://doc/new_uri", retrieved?.documentUri)
        assertEquals("content://doc/new_uri", retrieved?.documentUri)
    }

    @Test
    fun publishingScan_marksMissingFilesUnavailable() = runBlocking {
        val col = collectionRepo.createCollection("Music Collection")
        val src = collectionRepo.addRootSource(col.id, "content://tree/music", "Music Folder")

        val rootFolder = FolderNode("folder_root", src.id, null, "", "Root")
        val media1 = MediaFile(id = "m1", sourceId = src.id, folderId = "folder_root", documentUri = "uri://1", relativePath = "1.mp3", filename = "1.mp3", displayTitle = "1", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)
        val media2 = MediaFile(id = "m2", sourceId = src.id, folderId = "folder_root", documentUri = "uri://2", relativePath = "2.mp3", filename = "2.mp3", displayTitle = "2", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)

        val scan1 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scan1, listOf(rootFolder), listOf(media1, media2), emptyList(), ScanResult(2, 2, 0, 0, 0, 0, 0, 0, 100))

        val scan2 = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scan2, listOf(rootFolder), listOf(media1), listOf("m2"), ScanResult(2, 0, 1, 1, 0, 0, 0, 0, 100))

        val m1Retrieved = mediaRepo.getMediaFileById("m1")
        val m2Retrieved = mediaRepo.getMediaFileById("m2")

        assertTrue(m1Retrieved!!.isAvailable)
        assertFalse(m2Retrieved!!.isAvailable)
    }

    @Test
    fun scanCancellation_purgesStagedData_andLeavesCanonicalIntact() = runBlocking {
        val col = collectionRepo.createCollection("Music Collection")
        val src = collectionRepo.addRootSource(col.id, "content://tree/music", "Music Folder")

        val scanId = mediaRepo.startScanRun(src.id)
        mediaRepo.stageFolders(scanId, listOf(StagedFolder("sf1", scanId, "Relative", null, "Relative")))
        mediaRepo.stageMedia(scanId, listOf(StagedMedia("sm1", scanId, "uri://staged", null, "Relative/staged.mp3", "staged.mp3", "Staged", "audio/mpeg", 100, null, 100)))

        // Cancel scan
        mediaRepo.cancelScanRun(scanId)

        // Verify canonical media flow remains empty
        val mediaList = mediaRepo.getMediaFilesFlow(col.id).first()
        assertTrue(mediaList.isEmpty())
    }

    @Test
    fun stagedPublication_preservesIdsWithoutMaterializingCanonicalLibraryInCoordinator() = runBlocking {
        val collection = collectionRepo.createCollection("Music")
        val source = collectionRepo.addRootSource(collection.id, "content://tree/music", "Music")
        val firstScan = mediaRepo.startScanRun(source.id)
        mediaRepo.stageFolders(
            firstScan,
            listOf(StagedFolder("sf1", firstScan, "Artist", null, "Artist"))
        )
        mediaRepo.stageMedia(
            firstScan,
            listOf(
                StagedMedia(
                    id = "sm1",
                    scanId = firstScan,
                    documentUri = "content://doc/song",
                    documentId = "song",
                    relativePath = "Artist/Song.mp3",
                    filename = "Song.mp3",
                    displayTitle = "Song",
                    mimeType = "audio/mpeg",
                    size = 1_000,
                    durationMs = 60_000,
                    modifiedTimeMs = 2_000
                )
            )
        )
        val firstResult = mediaRepo.publishStagedScan(
            firstScan,
            source.id,
            ScanResult(1, 0, 0, 0, 0, 0, 0, 0, 100)
        )
        val firstMedia = mediaRepo.getMediaFilesFlow(collection.id).first().single()

        val secondScan = mediaRepo.startScanRun(source.id)
        mediaRepo.stageFolders(
            secondScan,
            listOf(StagedFolder("sf2", secondScan, "Artist", null, "Artist"))
        )
        mediaRepo.stageMedia(
            secondScan,
            listOf(
                StagedMedia(
                    id = "sm2",
                    scanId = secondScan,
                    documentUri = "content://doc/song",
                    documentId = "song",
                    relativePath = "Artist/Song.mp3",
                    filename = "Song.mp3",
                    displayTitle = "Song refreshed",
                    mimeType = "audio/mpeg",
                    size = 1_000,
                    durationMs = 60_000,
                    modifiedTimeMs = 2_000
                )
            )
        )
        val secondResult = mediaRepo.publishStagedScan(
            secondScan,
            source.id,
            ScanResult(1, 0, 0, 0, 0, 0, 0, 0, 120)
        )
        val secondMedia = mediaRepo.getMediaFilesFlow(collection.id).first().single()

        assertEquals(1, firstResult.addedCount)
        assertEquals(1, secondResult.updatedCount)
        assertEquals(firstMedia.id, secondMedia.id)
        assertEquals(firstMedia.firstIndexedAt, secondMedia.firstIndexedAt)
        assertEquals("Song refreshed", secondMedia.displayTitle)
    }
}
