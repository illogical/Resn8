package com.app.resn8

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.repository.UiSessionRepository
import com.app.resn8.ui.library.LibraryViewModel
import com.app.resn8.ui.screens.LibraryScreen
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalTestApi::class)
class PostIndexLibraryInteractionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PostIndexTestActivity>()

    @Test
    fun searchTabChangeAndTrackSelectionUseTheIndexedCollection() {
        val collectionId = "8054bbb4-f16a-4ec4-846d-da80cb044ad4"
        val sourceId = "93c06858-1938-4ea8-bf87-a34a3f78ceef"
        val media = MediaFile(
            id = "track-1",
            sourceId = sourceId,
            folderId = "folder-1",
            documentUri = "content://test/track-1",
            relativePath = "Artist/Album/Test Song.mp3",
            filename = "Test Song.mp3",
            displayTitle = "Test Song",
            mimeType = "audio/mpeg",
            size = 1_024L,
            modifiedTimeMs = 1L,
            artist = "Test Artist",
            album = "Test Album",
            isAvailable = true
        )
        val sessionRepository = TestUiSessionRepository()
        val viewModel = LibraryViewModel(
            collectionId = collectionId,
            sourceId = sourceId,
            mediaRepository = FakeMediaRepository(initialMediaFiles = listOf(media)),
            uiSessionRepository = sessionRepository
        )
        var selectedTrackId: String? = null

        composeRule.setContent {
            MaterialTheme {
                LibraryScreen(
                    viewModel = viewModel,
                    onArtistClick = {},
                    onAlbumClick = {},
                    onFoldersClick = {},
                    onTrackClick = { selectedTrackId = it.id }
                )
            }
        }

        composeRule.waitUntilAtLeastOneExists(hasText("Test Artist"), timeoutMillis = 10_000L)
        composeRule.onNodeWithText("Test Artist").assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            "Search library by title, artist, album, or filename"
        ).performTextInput("Test")
        composeRule.onNodeWithText("All tracks", useUnmergedTree = true).performClick()
        composeRule.waitUntilAtLeastOneExists(hasText("Test Song"), timeoutMillis = 10_000L)
        composeRule.onNodeWithText("Test Song").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals("track-1", selectedTrackId)
            assertEquals(collectionId, sessionRepository.state.value.selectedCollectionId)
            assertEquals(sourceId, sessionRepository.state.value.selectedSourceId)
        }
    }

    private class TestUiSessionRepository : UiSessionRepository {
        val state = MutableStateFlow(UiSessionState())

        override fun getUiSessionStateFlow() = state

        override suspend fun saveUiSessionState(state: UiSessionState) {
            this.state.value = state
        }
    }
}
