package com.app.resn8.storage

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.storage.indexer.AudioAdmissionPolicy
import com.app.resn8.storage.indexer.DocumentTreeScanner
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
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
        assertFalse(scanner.isSupportedAudio("mislabelled.mp3", "video/mp4"))
        assertFalse(scanner.isSupportedAudio("empty.mp3", "audio/mpeg", size = 0L))
        assertFalse(scanner.isSupportedAudio("legacy.wma", "application/octet-stream"))
        assertFalse(scanner.isSupportedAudio("._Song.mp3", "audio/mpeg", size = 4_096L))
    }

    @Test
    fun isSupportedAudio_usesExtensionOnlyForGenericProviderMime() {
        val scanner = DocumentTreeScanner(ApplicationProvider.getApplicationContext())

        assertTrue(scanner.isSupportedAudio("UPPERCASE.FLAC", "application/octet-stream", size = null))
        assertTrue(scanner.isSupportedAudio(".hidden.opus", "", size = 10L))
        assertFalse(scanner.isSupportedAudio("cover.jpg", "application/octet-stream", size = 10L))
    }

    @Test
    fun admissionRejectionsExposePrivacySafeReasonsAndAudioLikeClassification() {
        assertEquals(
            AudioAdmissionPolicy.RejectionReason.APPLEDOUBLE_SIDECAR,
            AudioAdmissionPolicy.evaluate("._Song.mp3", "audio/mpeg", 4_096L).rejectionReason
        )
        assertEquals(
            AudioAdmissionPolicy.RejectionReason.UNSUPPORTED_MIME,
            AudioAdmissionPolicy.evaluate("song.mp3", "video/mp4", 4_096L).rejectionReason
        )
        assertEquals(
            AudioAdmissionPolicy.RejectionReason.UNSUPPORTED_EXTENSION,
            AudioAdmissionPolicy.evaluate("legacy.wma", "application/octet-stream", 4_096L).rejectionReason
        )
        assertTrue(AudioAdmissionPolicy.isAudioLike("legacy.wma", "application/octet-stream"))
        assertFalse(AudioAdmissionPolicy.isAudioLike("cover.jpg", "image/jpeg"))
    }
}
