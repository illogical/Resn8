package com.app.resn8

import com.app.resn8.data.repository.FakeMediaRepository
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
}
