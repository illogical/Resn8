package com.app.resn8.playback

import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.PlaybackOrigin
import com.app.resn8.domain.model.QueueFilterSnapshot
import com.app.resn8.domain.model.resolvePlaybackOrigin
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackOriginTest {
    @Test
    fun explicitOriginsRemainAuthoritative() {
        val origins = listOf(
            PlaybackOrigin.Playlist("playlist", "Road Trip"),
            PlaybackOrigin.Album("KNOWN:Album", "KNOWN:Artist", "Album"),
            PlaybackOrigin.Artist("KNOWN:Artist", "Artist"),
            PlaybackOrigin.Folder("source", "folder", "Favorites"),
            PlaybackOrigin.AllTracks
        )

        origins.forEach { origin ->
            assertEquals(origin, QueueFilterSnapshot(collectionId = "collection", origin = origin).resolvePlaybackOrigin())
        }
    }

    @Test
    fun legacySnapshotsDeriveTheSafestOrigin() {
        assertEquals(
            PlaybackOrigin.Playlist("playlist", "Road Trip"),
            QueueFilterSnapshot(playlistId = "playlist", playlistName = "Road Trip").resolvePlaybackOrigin()
        )
        assertEquals(
            PlaybackOrigin.Album(
                MetadataGroupKey.Known("Album").serialize(),
                MetadataGroupKey.Known("Artist").serialize(),
                "Album"
            ),
            QueueFilterSnapshot(album = "Album", albumArtist = "Artist").resolvePlaybackOrigin()
        )
        assertEquals(
            PlaybackOrigin.Artist(MetadataGroupKey.Known("Artist").serialize(), "Artist"),
            QueueFilterSnapshot(artist = "Artist").resolvePlaybackOrigin()
        )
        assertEquals(
            PlaybackOrigin.Folder("source", "folder", "Favorites"),
            QueueFilterSnapshot(sourceId = "source", folderId = "folder", folderName = "Favorites").resolvePlaybackOrigin()
        )
        assertEquals(
            PlaybackOrigin.AllTracks,
            QueueFilterSnapshot(collectionId = "collection", searchQuery = "ambient").resolvePlaybackOrigin()
        )
    }
}
