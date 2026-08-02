package com.app.resn8.storage

import com.app.resn8.domain.model.MetadataValueSource
import com.app.resn8.storage.indexer.ExtractedTags
import com.app.resn8.storage.indexer.FallbackParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackParserTest {

    @Test
    fun normalize_usesEmbeddedTags_whenPresent() {
        val tags = ExtractedTags(
            title = "Tag Song Title",
            artist = "Tag Artist",
            album = "Tag Album",
            trackNumber = 5,
            discNumber = 1
        )

        val result = FallbackParser.normalize(
            relativePath = "Folder/SubFolder/01 - Ignored.mp3",
            filename = "01 - Ignored.mp3",
            tags = tags
        )

        assertEquals("Tag Song Title", result.displayTitle)
        assertEquals("Tag Song Title", result.title)
        assertEquals("Tag Artist", result.artist)
        assertEquals("Tag Album", result.album)
        assertEquals(5, result.trackNumber)
        assertEquals(1, result.discNumber)

        assertEquals(MetadataValueSource.TAG, result.titleSource)
        assertEquals(MetadataValueSource.TAG, result.artistSource)
        assertEquals(MetadataValueSource.TAG, result.albumSource)
        assertEquals(MetadataValueSource.TAG, result.trackNumberSource)
        assertEquals(MetadataValueSource.TAG, result.discNumberSource)
    }

    @Test
    fun normalize_infersArtistAndAlbumFromPath_whenTagsMissing() {
        val tags = ExtractedTags()

        val result = FallbackParser.normalize(
            relativePath = "Pink Floyd/The Wall/01 - In The Flesh.mp3",
            filename = "01 - In The Flesh.mp3",
            tags = tags
        )

        assertEquals("In The Flesh", result.displayTitle)
        assertEquals("In The Flesh", result.title)
        assertEquals("Pink Floyd", result.artist)
        assertEquals("The Wall", result.album)
        assertEquals(1, result.trackNumber)

        assertEquals(MetadataValueSource.FILENAME, result.titleSource)
        assertEquals(MetadataValueSource.PATH, result.artistSource)
        assertEquals(MetadataValueSource.PATH, result.albumSource)
        assertEquals(MetadataValueSource.FILENAME, result.trackNumberSource)
    }

    @Test
    fun normalize_parsesDiscTrackPrefixes() {
        val tags = ExtractedTags()

        val result1 = FallbackParser.normalize("Album/1-02 Comfortably Numb.mp3", "1-02 Comfortably Numb.mp3", tags)
        assertEquals("Comfortably Numb", result1.displayTitle)
        assertEquals(1, result1.discNumber)
        assertEquals(2, result1.trackNumber)

        val result2 = FallbackParser.normalize("Album/205 - Hey You.mp3", "205 - Hey You.mp3", tags)
        assertEquals("Hey You", result2.displayTitle)
        assertEquals(2, result2.discNumber)
        assertEquals(5, result2.trackNumber)
    }

    @Test
    fun normalize_cleansFilenameFallback_whenNoPatternMatches() {
        val tags = ExtractedTags()

        val result = FallbackParser.normalize("My_Random_Recording.mp3", "My_Random_Recording.mp3", tags)

        assertEquals("My Random Recording", result.displayTitle)
        assertEquals("My Random Recording", result.title)
        assertNull(result.artist)
        assertNull(result.album)
        assertNull(result.trackNumber)
        assertEquals(MetadataValueSource.FILENAME, result.titleSource)
    }
}
