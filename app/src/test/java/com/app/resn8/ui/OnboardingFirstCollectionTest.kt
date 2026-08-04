package com.app.resn8.ui

import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.QueueFilterSnapshot
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.ui.screens.onboardingCompletionButtonLabel
import com.app.resn8.ui.screens.onboardingFolderButtonLabel
import com.app.resn8.ui.screens.onboardingProfileDescription
import com.app.resn8.ui.screens.onboarding.forInitialCollection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingFirstCollectionTest {
    @Test
    fun onboardingCopyIsProfileAware() {
        assertEquals("Select Music Folder", onboardingFolderButtonLabel(CollectionProfile.MUSIC))
        assertEquals("Open Library", onboardingCompletionButtonLabel(CollectionProfile.MUSIC))
        assertEquals(
            "Browse music by artist, album, track, and folder.",
            onboardingProfileDescription(CollectionProfile.MUSIC)
        )

        assertEquals("Select Audio Files Folder", onboardingFolderButtonLabel(CollectionProfile.FLAT))
        assertEquals("Open Folders", onboardingCompletionButtonLabel(CollectionProfile.FLAT))
        assertEquals(
            "Browse general audio by filename and folder.",
            onboardingProfileDescription(CollectionProfile.FLAT)
        )
    }

    @Test
    fun initialCollectionSessionUsesMusicHomeAndClearsStaleState() {
        val result = dirtySession().forInitialCollection(
            collectionId = "music-collection",
            sourceId = "music-source",
            profile = CollectionProfile.MUSIC,
            route = "library"
        )

        assertEquals("library", result.currentRoute)
        assertEquals("music-collection", result.selectedCollectionId)
        assertEquals("music-source", result.selectedSourceId)
        assertEquals(SortOrder.ARTIST, result.activeSort)
        assertEquals(LibrarySurface.ARTISTS, result.activeSurface)
        assertCleared(result)
    }

    @Test
    fun initialCollectionSessionUsesFlatHomeAndClearsStaleState() {
        val result = dirtySession().forInitialCollection(
            collectionId = "flat-collection",
            sourceId = "flat-source",
            profile = CollectionProfile.FLAT,
            route = "folders"
        )

        assertEquals("folders", result.currentRoute)
        assertEquals("flat-collection", result.selectedCollectionId)
        assertEquals("flat-source", result.selectedSourceId)
        assertEquals(SortOrder.TITLE, result.activeSort)
        assertEquals(LibrarySurface.FOLDERS, result.activeSurface)
        assertCleared(result)
    }

    @Test
    fun topLevelDestinationsReactToCollectionAvailabilityAndProfile() {
        assertEquals(
            listOf("Onboarding"),
            buildTopLevelDestinations(false, CollectionProfile.MUSIC).map { it.label }
        )
        assertEquals(
            listOf("Settings", "Library", "Folders", "Playlists"),
            buildTopLevelDestinations(true, CollectionProfile.MUSIC).map { it.label }
        )
        assertEquals(
            listOf("Settings", "Folders", "Playlists"),
            buildTopLevelDestinations(true, CollectionProfile.FLAT).map { it.label }
        )
    }

    private fun dirtySession() = UiSessionState(
        currentRoute = "playlist_detail",
        selectedCollectionId = "old-collection",
        selectedSourceId = "old-source",
        selectedFolderId = "old-folder",
        selectedArtistKey = MetadataGroupKey.Known("Old Artist"),
        selectedAlbumKey = MetadataGroupKey.Known("Old Album"),
        selectedAlbumArtistKey = MetadataGroupKey.Known("Old Album Artist"),
        selectedPlaylistId = "old-playlist",
        activeQueueId = "old-queue",
        activeSearchQuery = "old search",
        activeSort = SortOrder.MOST_PLAYED,
        activeSurface = LibrarySurface.ALBUMS,
        libraryFilterSnapshot = LibraryFilterSnapshot(excludeDisliked = true),
        activeFilterSnapshot = QueueFilterSnapshot(searchQuery = "old queue filter")
    )

    private fun assertCleared(state: UiSessionState) {
        assertNull(state.selectedFolderId)
        assertNull(state.selectedArtistKey)
        assertNull(state.selectedAlbumKey)
        assertNull(state.selectedAlbumArtistKey)
        assertNull(state.selectedPlaylistId)
        assertNull(state.activeQueueId)
        assertNull(state.activeSearchQuery)
        assertEquals(LibraryFilterSnapshot(), state.libraryFilterSnapshot)
        assertNull(state.activeFilterSnapshot)
    }
}
