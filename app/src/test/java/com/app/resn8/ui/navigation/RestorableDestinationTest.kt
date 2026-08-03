package com.app.resn8.ui.navigation

import com.app.resn8.data.repository.FakeCollectionRepository
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.data.repository.FakePlaylistRepository
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.UiSessionState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestorableDestinationTest {

    @Test
    fun `fromSessionState correctly maps route and session keys`() {
        val state = UiSessionState(
            currentRoute = "album_detail/album_1",
            selectedCollectionId = "col_1",
            selectedAlbumKey = MetadataGroupKey.Known("Album 1"),
            selectedAlbumArtistKey = MetadataGroupKey.Known("Album Artist 1")
        )

        val dest = RestorableDestination.fromSessionState(state)
        assertTrue(dest is RestorableDestination.AlbumDetail)
        val albumDest = dest as RestorableDestination.AlbumDetail
        assertEquals("col_1", albumDest.collectionId)
        assertEquals(MetadataGroupKey.Known("Album 1"), albumDest.albumKey)
        assertEquals(MetadataGroupKey.Known("Album Artist 1"), albumDest.albumArtistKey)
    }

    @Test
    fun `fromSessionState maps settings and playlists`() {
        val settingsState = UiSessionState(currentRoute = "settings")
        assertEquals(RestorableDestination.Settings, RestorableDestination.fromSessionState(settingsState))

        val playlistsState = UiSessionState(currentRoute = "playlists")
        assertEquals(RestorableDestination.Playlists, RestorableDestination.fromSessionState(playlistsState))
    }

    @Test
    fun `resolveValidDestination falls back to onboarding when no root source exists`() = runBlocking {
        val collectionRepo = FakeCollectionRepository()
        val mediaRepo = FakeMediaRepository()
        val playlistRepo = FakePlaylistRepository()

        val resolved = RestorableDestination.resolveValidDestination(
            destination = RestorableDestination.Library(LibrarySurface.ARTISTS),
            collectionRepository = collectionRepo,
            mediaRepository = mediaRepo,
            playlistRepository = playlistRepo
        )

        assertEquals(RestorableDestination.Onboarding, resolved)
    }

    @Test
    fun `resolveValidDestination falls back to parent when target entity is missing`() = runBlocking {
        val collectionRepo = FakeCollectionRepository()
        val col = collectionRepo.createCollection("MUSIC")
        val src = collectionRepo.addRootSource(col.id, "content://test", "Music")

        val mediaRepo = FakeMediaRepository()
        val playlistRepo = FakePlaylistRepository()

        // Artist detail with missing artist -> falls back to Library Artists
        val missingArtistDest = RestorableDestination.ArtistDetail(col.id, MetadataGroupKey.Known("Missing Artist"))
        val resolvedArtist = RestorableDestination.resolveValidDestination(
            destination = missingArtistDest,
            collectionRepository = collectionRepo,
            mediaRepository = mediaRepo,
            playlistRepository = playlistRepo
        )
        assertEquals(RestorableDestination.Library(LibrarySurface.ARTISTS), resolvedArtist)

        // Add media file with artist
        val file = MediaFile(
            id = "f1",
            sourceId = src.id,
            folderId = "fold1",
            documentUri = "content://f1",
            relativePath = "Artist 1/Album 1/song.mp3",
            filename = "song.mp3",
            displayTitle = "Song 1",
            mimeType = "audio/mpeg",
            size = 1000L,
            modifiedTimeMs = System.currentTimeMillis(),
            artist = "Artist 1"
        )
        mediaRepo.addMediaFiles(listOf(file))

        val validArtistDest = RestorableDestination.ArtistDetail(col.id, MetadataGroupKey.Known("Artist 1"))
        val resolvedValidArtist = RestorableDestination.resolveValidDestination(
            destination = validArtistDest,
            collectionRepository = collectionRepo,
            mediaRepository = mediaRepo,
            playlistRepository = playlistRepo
        )
        assertEquals(validArtistDest, resolvedValidArtist)
    }
}
