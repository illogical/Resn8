package com.app.resn8.data.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.ScanResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MediaPersistenceTest {

    private lateinit var db: Resn8Database
    private lateinit var collectionRepo: RoomCollectionRepository
    private lateinit var mediaRepo: RoomMediaRepository
    private lateinit var testMediaId: String

    @Before
    fun setUp() = runBlocking {
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        collectionRepo = RoomCollectionRepository(db)
        mediaRepo = RoomMediaRepository(db)

        val col = collectionRepo.createCollection("Main")
        val src = collectionRepo.addRootSource(col.id, "tree://music", "Music")
        val folder = FolderNode("f1", src.id, null, "", "Root")
        val media = MediaFile(
            id = "media_100", sourceId = src.id, folderId = "f1", documentUri = "uri://100", relativePath = "song.mp3",
            filename = "song.mp3", displayTitle = "Song", mimeType = "audio/mpeg", size = 1000, modifiedTimeMs = 1000,
            likeScore = 0, playCount = 0
        )

        val scanId = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scanId, listOf(folder), listOf(media), emptyList(), ScanResult(1, 1, 0, 0, 0, 0, 0, 0, 100))
        testMediaId = "media_100"
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun signedScore_updatesAtomically_crossingZero() = runBlocking {
        // 0 -> 1
        val res1 = mediaRepo.updateLikeScore(testMediaId, 1)
        assertTrue(res1.isSuccess)
        assertEquals(1, res1.getOrNull())
        assertEquals(1, mediaRepo.getMediaFileById(testMediaId)?.likeScore)

        // 1 -> 2
        val res2 = mediaRepo.updateLikeScore(testMediaId, 1)
        assertTrue(res2.isSuccess)
        assertEquals(2, res2.getOrNull())
        assertEquals(2, mediaRepo.getMediaFileById(testMediaId)?.likeScore)

        // 2 -> 1
        val res3 = mediaRepo.updateLikeScore(testMediaId, -1)
        assertTrue(res3.isSuccess)
        assertEquals(1, res3.getOrNull())
        assertEquals(1, mediaRepo.getMediaFileById(testMediaId)?.likeScore)

        // 1 -> 0
        mediaRepo.updateLikeScore(testMediaId, -1)
        assertEquals(0, mediaRepo.getMediaFileById(testMediaId)?.likeScore)

        // 0 -> -1
        mediaRepo.updateLikeScore(testMediaId, -1)
        assertEquals(-1, mediaRepo.getMediaFileById(testMediaId)?.likeScore)

        // -1 remains the minimum
        mediaRepo.updateLikeScore(testMediaId, -1)
        assertEquals(-1, mediaRepo.getMediaFileById(testMediaId)?.likeScore)
    }

    @Test
    fun signedScore_rejectsInvalidDelta_andMissingMedia() = runBlocking {
        val invalidDelta = mediaRepo.updateLikeScore(testMediaId, 2)
        assertTrue(invalidDelta.isFailure)

        val missingMedia = mediaRepo.updateLikeScore("non_existent_id", 1)
        assertTrue(missingMedia.isFailure)
    }

    @Test
    fun commitMeaningfulPlay_isIdempotentPerOccurrence() = runBlocking {
        val occurrenceId = "occ_12345"

        // First commit: counted
        val committed = mediaRepo.commitMeaningfulPlay(
            sessionOccurrenceId = occurrenceId,
            mediaId = testMediaId,
            startedAt = System.currentTimeMillis() - 120000L,
            endedAt = System.currentTimeMillis(),
            accumulatedListenedDurationMs = 120000L,
            result = PlaybackHistoryResult.THRESHOLD_COUNTED
        )
        assertTrue(committed)
        assertEquals(1, mediaRepo.getMediaFileById(testMediaId)?.playCount)

        // Retry same occurrence: ignored
        val retryCommitted = mediaRepo.commitMeaningfulPlay(
            sessionOccurrenceId = occurrenceId,
            mediaId = testMediaId,
            startedAt = System.currentTimeMillis() - 120000L,
            endedAt = System.currentTimeMillis(),
            accumulatedListenedDurationMs = 120000L,
            result = PlaybackHistoryResult.THRESHOLD_COUNTED
        )
        assertFalse(retryCommitted)
        assertEquals(1, mediaRepo.getMediaFileById(testMediaId)?.playCount) // Still 1

        // Second distinct occurrence: counted
        val secondCommitted = mediaRepo.commitMeaningfulPlay(
            sessionOccurrenceId = "occ_67890",
            mediaId = testMediaId,
            startedAt = System.currentTimeMillis() - 120000L,
            endedAt = System.currentTimeMillis(),
            accumulatedListenedDurationMs = 120000L,
            result = PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED
        )
        assertTrue(secondCommitted)
        assertEquals(2, mediaRepo.getMediaFileById(testMediaId)?.playCount) // Incremented to 2
    }
}
