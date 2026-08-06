package com.app.resn8.ui

import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.data.database.entity.toEntity
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.LibrarySortField
import com.app.resn8.domain.model.LibrarySortPreferences
import com.app.resn8.domain.model.LibrarySortSelection
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SortDirection
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.model.toLegacySortOrder
import com.app.resn8.domain.model.toLibrarySortSelection
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class LibrarySortV3Test {
    private val media = listOf(
        media("a", "Alpha", "Artist A", "Album A", indexed = 10, plays = 2, lastPlayed = 20, rating = 1),
        media("z", "Zulu", "Artist Z", "Album Z", indexed = 30, plays = 8, lastPlayed = 40, rating = -1),
        media("u", "Unknown", null, null, indexed = 20, plays = 0, lastPlayed = null, rating = 4)
    )
    private val repository = FakeMediaRepository(media)

    @Test
    fun allTracksFieldsHonorBothDirectionsAndKeepUnknownValuesLast() = runBlocking {
        assertOrder(LibrarySortField.ALPHABETICAL, SortDirection.ASCENDING, "a", "u", "z")
        assertOrder(LibrarySortField.ALPHABETICAL, SortDirection.DESCENDING, "z", "u", "a")
        assertOrder(LibrarySortField.ARTIST, SortDirection.ASCENDING, "a", "z", "u")
        assertOrder(LibrarySortField.ARTIST, SortDirection.DESCENDING, "z", "a", "u")
        assertOrder(LibrarySortField.ALBUM, SortDirection.ASCENDING, "a", "z", "u")
        assertOrder(LibrarySortField.ALBUM, SortDirection.DESCENDING, "z", "a", "u")
        assertOrder(LibrarySortField.DATE_ADDED, SortDirection.ASCENDING, "a", "u", "z")
        assertOrder(LibrarySortField.DATE_ADDED, SortDirection.DESCENDING, "z", "u", "a")
        assertOrder(LibrarySortField.PLAY_COUNT, SortDirection.ASCENDING, "u", "a", "z")
        assertOrder(LibrarySortField.PLAY_COUNT, SortDirection.DESCENDING, "z", "a", "u")
        assertOrder(LibrarySortField.LAST_PLAYED, SortDirection.ASCENDING, "a", "z", "u")
        assertOrder(LibrarySortField.LAST_PLAYED, SortDirection.DESCENDING, "z", "a", "u")
        assertOrder(LibrarySortField.RATING, SortDirection.ASCENDING, "z", "a", "u")
        assertOrder(LibrarySortField.RATING, SortDirection.DESCENDING, "u", "a", "z")
    }

    @Test
    fun preferencesRememberEachSurfaceAndLegacyRemovedModesNormalize() {
        val preferences = LibrarySortPreferences()
            .withSelection(LibrarySurface.ARTISTS, LibrarySortSelection(direction = SortDirection.DESCENDING))
            .withSelection(LibrarySurface.ALBUMS, LibrarySortSelection(direction = SortDirection.ASCENDING))
            .withSelection(
                LibrarySurface.ALL_TRACKS,
                LibrarySortSelection(LibrarySortField.RATING, SortDirection.DESCENDING)
            )

        assertEquals(SortDirection.DESCENDING, preferences.selectionFor(LibrarySurface.ARTISTS).direction)
        assertEquals(SortDirection.ASCENDING, preferences.selectionFor(LibrarySurface.ALBUMS).direction)
        assertEquals(LibrarySortField.RATING, preferences.selectionFor(LibrarySurface.ALL_TRACKS).field)
        assertEquals(LibrarySortSelection(), SortOrder.TRACK.toLibrarySortSelection())
        assertEquals(LibrarySortSelection(), SortOrder.UNPLAYED.toLibrarySortSelection())
    }

    @Test
    fun sessionRoundTripPersistsPreferencesAndClearsLegacyHiddenFilters() {
        val preferences = LibrarySortPreferences(
            artists = LibrarySortSelection(direction = SortDirection.DESCENDING),
            allTracks = LibrarySortSelection(LibrarySortField.PLAY_COUNT, SortDirection.DESCENDING)
        )
        val restored = UiSessionState(
            librarySortPreferences = preferences,
            libraryFilterSnapshot = LibraryFilterSnapshot(
                availability = AvailabilityFilter.UNAVAILABLE_ONLY,
                excludeDisliked = true
            )
        ).toEntity().toDomain()

        assertEquals(preferences, restored.librarySortPreferences)
        assertEquals(LibraryFilterSnapshot(), restored.libraryFilterSnapshot)
    }

    private suspend fun assertOrder(
        field: LibrarySortField,
        direction: SortDirection,
        vararg expectedIds: String
    ) {
        val selection = LibrarySortSelection(field, direction)
        val actual = repository.snapshotVisibleMediaIds(
            LibraryQuery(
                collectionId = "collection",
                sort = selection.toLegacySortOrder(),
                sortDirection = direction
            )
        )
        assertEquals(expectedIds.toList(), actual)
    }

    private fun media(
        id: String,
        title: String,
        artist: String?,
        album: String?,
        indexed: Long,
        plays: Int,
        lastPlayed: Long?,
        rating: Int
    ) = MediaFile(
        id = id,
        sourceId = "source",
        folderId = "folder",
        documentUri = "content://$id",
        relativePath = "$id.mp3",
        filename = "$id.mp3",
        displayTitle = title,
        mimeType = "audio/mpeg",
        size = 1,
        modifiedTimeMs = 1,
        firstIndexedAt = indexed,
        artist = artist,
        album = album,
        playCount = plays,
        lastPlayedAt = lastPlayed,
        likeScore = rating
    )
}
