package com.app.resn8

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.domain.model.LibrarySortField
import com.app.resn8.domain.model.LibrarySortSelection
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.ui.library.LibrarySortSheet
import com.app.resn8.ui.screens.PlaylistItemRow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UxImprovementsV3UiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PostIndexTestActivity>()

    @Test
    fun allTracksSortSheetShowsOnlyContextFieldsAndDirection() {
        composeRule.setContent {
            MaterialTheme {
                LibrarySortSheet(
                    currentSurface = LibrarySurface.ALL_TRACKS,
                    currentSort = LibrarySortSelection(LibrarySortField.PLAY_COUNT),
                    onFieldSelected = {},
                    onDirectionSelected = {},
                    onDismiss = {}
                )
            }
        }

        listOf("Alphabetical", "Artist", "Album", "Date Added", "Play Count", "Last Played", "Rating")
            .forEach { composeRule.onNodeWithText(it).assertIsDisplayed() }
        composeRule.onNodeWithText("Ascending").assertIsDisplayed()
        composeRule.onNodeWithText("Descending").assertIsDisplayed()
        composeRule.onNodeWithText("Availability").assertDoesNotExist()
        composeRule.onNodeWithText("Exclude Disliked Tracks").assertDoesNotExist()
        composeRule.onNodeWithText("Unplayed").assertDoesNotExist()
        composeRule.onNodeWithText("Track").assertDoesNotExist()
    }

    @Test
    fun artistSortSheetShowsOnlyAlphabeticalAndDirection() {
        composeRule.setContent {
            MaterialTheme {
                LibrarySortSheet(
                    currentSurface = LibrarySurface.ARTISTS,
                    currentSort = LibrarySortSelection(),
                    onFieldSelected = {},
                    onDirectionSelected = {},
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Alphabetical").assertIsDisplayed()
        composeRule.onNodeWithText("Ascending").assertIsDisplayed()
        composeRule.onNodeWithText("Descending").assertIsDisplayed()
        composeRule.onNodeWithText("Artist").assertDoesNotExist()
        composeRule.onNodeWithText("Album").assertDoesNotExist()
        composeRule.onNodeWithText("Play Count").assertDoesNotExist()
    }

    @Test
    fun playlistNumberExposesLongPressDragHandleSemantics() {
        val media = MediaFile(
            id = "track",
            sourceId = "source",
            folderId = "folder",
            documentUri = "content://track",
            relativePath = "track.mp3",
            filename = "track.mp3",
            displayTitle = "Drag Me",
            mimeType = "audio/mpeg",
            size = 1,
            modifiedTimeMs = 1
        )
        composeRule.setContent {
            MaterialTheme {
                PlaylistItemRow(
                    index = 4,
                    mediaFile = media,
                    isFirst = false,
                    isLast = false,
                    isSearchActive = false,
                    isCurrent = false,
                    isPlaying = false,
                    dragEnabled = true,
                    showMusicMetadata = true,
                    onTrackClick = {},
                    onMoveToTop = {},
                    onMoveUp = {},
                    onMoveDown = {},
                    onMoveToBottom = {},
                    onRemove = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Reorder track 4: Drag Me. Long press and drag.")
            .assertIsDisplayed()
    }
}
