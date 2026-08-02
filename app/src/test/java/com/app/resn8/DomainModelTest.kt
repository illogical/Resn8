package com.app.resn8

import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.data.database.Converters
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.fixtures.AudioFixtureGenerator
import com.app.resn8.fixtures.FakeClock
import com.app.resn8.fixtures.FakeRandom
import com.app.resn8.fixtures.createTestMediaFile
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainModelTest {

    @Test
    fun mediaFile_defaultValues_areCorrect() {
        val media = createTestMediaFile()
        assertEquals(0, media.playCount)
        assertEquals(0, media.likeScore)
        assertTrue(media.isAvailable)
    }

    @Test
    fun fakeMediaRepository_likeScore_updatesAtomically() = runBlocking {
        val initialMedia = createTestMediaFile(id = "track_1", likeScore = 0)
        val repository = FakeMediaRepository(initialMediaFiles = listOf(initialMedia))

        // Like (+1)
        repository.updateLikeScore("track_1", 1)
        var updated = repository.getMediaFileById("track_1")
        assertEquals(1, updated?.likeScore)

        // Like (+1 again)
        repository.updateLikeScore("track_1", 1)
        updated = repository.getMediaFileById("track_1")
        assertEquals(2, updated?.likeScore)

        // Dislike (-1)
        repository.updateLikeScore("track_1", -1)
        updated = repository.getMediaFileById("track_1")
        assertEquals(1, updated?.likeScore)
    }

    @Test
    fun fakeClockAndRandom_behaveDeterministically() {
        val clock = FakeClock(1000L)
        clock.advanceBy(500L)
        assertEquals(1500L, clock.currentTimeMs)

        val random1 = FakeRandom(123L)
        val random2 = FakeRandom(123L)
        val list = listOf("A", "B", "C", "D")
        assertEquals(random1.shuffle(list), random2.shuffle(list))
    }

    @Test
    fun audioFixtureGenerator_producesValidMp3Header() {
        val mp3Bytes = AudioFixtureGenerator.createMp3WithId3Tags(
            title = "Test Title",
            artist = "Test Artist",
            album = "Test Album",
            trackNumber = "01"
        )
        assertNotNull(mp3Bytes)
        assertTrue(mp3Bytes.size > 10)
        // Verify ID3 magic header
        assertEquals('I'.code.toByte(), mp3Bytes[0])
        assertEquals('D'.code.toByte(), mp3Bytes[1])
        assertEquals('3'.code.toByte(), mp3Bytes[2])
    }

    @Test
    fun scanResult_oldJson_decodesWithAdditiveDefaults() {
        val oldJson = """{"scannedCount":12,"addedCount":12,"updatedCount":0,"unavailableCount":0,"tagDerivedCount":8,"pathDerivedCount":4,"unrecognizedCount":1,"unreadableCount":2,"durationMs":3456}"""

        val decoded = Converters().toScanResult(oldJson)

        assertNotNull(decoded)
        assertEquals(12, decoded?.inspectedDocumentCount)
        assertEquals(0, decoded?.unsupportedCount)
        assertEquals(0, decoded?.metadataFailureCount)
        assertEquals(2, decoded?.schemaVersion)
    }
}
