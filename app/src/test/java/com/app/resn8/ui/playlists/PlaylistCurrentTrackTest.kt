package com.app.resn8.ui.playlists

import com.app.resn8.domain.model.MediaFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaylistCurrentTrackTest {

    @Test
    fun `current media is exposed only for the exact source playlist`() {
        assertEquals("m2", currentMediaIdForPlaylist("p1", "p1", "m2"))
        assertNull(currentMediaIdForPlaylist("p1", "p2", "m2"))
        assertNull(currentMediaIdForPlaylist("p1", null, "m2"))
        assertNull(currentMediaIdForPlaylist("p1", "p1", null))
    }

    @Test
    fun `live playlist index includes unavailable rows before the current item`() {
        val items = listOf(
            playlistItem(1, "unavailable", isAvailable = false),
            playlistItem(2, "m1"),
            playlistItem(3, "m2")
        )

        assertEquals(2, currentPlaylistItemIndex(items, "m2"))
        assertEquals(3, items[currentPlaylistItemIndex(items, "m2")].originalIndex)
    }

    @Test
    fun `removed or missing current membership has no live row`() {
        val items = listOf(playlistItem(1, "m1"), playlistItem(2, "m2"))

        assertEquals(-1, currentPlaylistItemIndex(items, "removed"))
        assertEquals(-1, currentPlaylistItemIndex(items, null))
    }

    private fun playlistItem(
        originalIndex: Int,
        mediaId: String,
        isAvailable: Boolean = true
    ) = PlaylistItemUiModel(
        originalIndex = originalIndex,
        mediaFile = MediaFile(
            id = mediaId,
            sourceId = "source",
            folderId = "folder",
            documentUri = "content://media/$mediaId",
            relativePath = "$mediaId.mp3",
            filename = "$mediaId.mp3",
            displayTitle = mediaId,
            mimeType = "audio/mpeg",
            size = 1L,
            modifiedTimeMs = 1L,
            isAvailable = isAvailable
        )
    )
}
