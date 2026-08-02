package com.app.resn8.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.storage.indexer.DocumentTreeScanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DocumentTreeScannerTest {

    @Test
    fun isSupportedAudio_admitsSupportedMimeTypesAndExtensions() {
        val scanner = DocumentTreeScanner(ApplicationProvider.getApplicationContext())

        assertTrue(scanner.isSupportedAudio("song.mp3", "audio/mpeg"))
        assertTrue(scanner.isSupportedAudio("track.m4a", "audio/mp4"))
        assertTrue(scanner.isSupportedAudio("music.flac", "audio/flac"))
        assertTrue(scanner.isSupportedAudio("audio.ogg", "audio/ogg"))
        assertTrue(scanner.isSupportedAudio("audio.wav", "audio/wav"))
        assertTrue(scanner.isSupportedAudio("audio.opus", "audio/opus"))
        assertTrue(scanner.isSupportedAudio("unlabeled_song.mp3", ""))
    }

    @Test
    fun isSupportedAudio_rejectsNonAudioFiles() {
        val scanner = DocumentTreeScanner(ApplicationProvider.getApplicationContext())

        assertFalse(scanner.isSupportedAudio("image.jpg", "image/jpeg"))
        assertFalse(scanner.isSupportedAudio("video.mp4", "video/mp4"))
        assertFalse(scanner.isSupportedAudio("document.pdf", "application/pdf"))
        assertFalse(scanner.isSupportedAudio("archive.zip", "application/zip"))
    }
}
