package com.app.resn8.ui

import com.app.resn8.domain.model.PlaybackOrigin
import com.app.resn8.ui.navigation.AlbumDetailRoute
import com.app.resn8.ui.navigation.ArtistDetailRoute
import com.app.resn8.ui.navigation.FoldersRoute
import com.app.resn8.ui.navigation.LibraryRoute
import com.app.resn8.ui.navigation.PlaylistDetailRoute
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

    @Test
    fun everyPlaybackOriginMapsToItsExactDestination() {
        val playlist = nowPlayingOriginRoute(PlaybackOrigin.Playlist("p", "Playlist"), "collection") as PlaylistDetailRoute
        assertEquals("p", playlist.playlistId)
        assertTrue(playlist.revealCurrentTrack)

        val album = nowPlayingOriginRoute(
            PlaybackOrigin.Album("KNOWN:Album", "KNOWN:Artist", "Album"),
            "collection"
        ) as AlbumDetailRoute
        assertEquals("collection", album.collectionId)
        assertEquals("KNOWN:Album", album.albumKeySerialized)
        assertEquals("KNOWN:Artist", album.albumArtistKeySerialized)

        val artist = nowPlayingOriginRoute(
            PlaybackOrigin.Artist("KNOWN:Artist", "Artist"),
            "collection"
        ) as ArtistDetailRoute
        assertEquals("KNOWN:Artist", artist.artistKeySerialized)

        val folder = nowPlayingOriginRoute(
            PlaybackOrigin.Folder("source", "folder", "Folder"),
            "collection"
        ) as FoldersRoute
        assertEquals("folder", folder.folderId)

        val library = nowPlayingOriginRoute(PlaybackOrigin.AllTracks, "collection") as LibraryRoute
        assertEquals("all_tracks", library.tab)
    }
}
