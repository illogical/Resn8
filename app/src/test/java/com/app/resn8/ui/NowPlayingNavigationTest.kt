package com.app.resn8.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingNavigationTest {
    @Test
    fun playlistLinkRequestsCurrentTrackReveal() {
        val route = nowPlayingPlaylistRoute("playlist-42")

        assertEquals("playlist-42", route.playlistId)
        assertTrue(route.revealCurrentTrack)
    }
}
