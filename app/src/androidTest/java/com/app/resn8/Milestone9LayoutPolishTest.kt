package com.app.resn8

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.FakePlaylistRepository
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.ui.library.TrackListItemRow
import com.app.resn8.ui.playlists.PlaylistsViewModel
import com.app.resn8.ui.screens.PlaylistItemRow
import com.app.resn8.ui.screens.PlaylistsScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalMaterial3Api::class)
class Milestone9LayoutPolishTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PostIndexTestActivity>()

    @Test
    fun playlistsToolbarSitsDirectlyBelowCollectionToolbar() {
        val viewModel = PlaylistsViewModel("collection-1", FakePlaylistRepository())

        composeRule.setContent {
            MaterialTheme {
                Scaffold(
                    topBar = { TopAppBar(title = { Text("Relax") }) }
                ) { outerPadding ->
                    PlaylistsScreen(
                        viewModel = viewModel,
                        onPlaylistClick = {},
                        modifier = Modifier
                            .padding(outerPadding)
                            .consumeWindowInsets(outerPadding)
                    )
                }
            }
        }

        val collectionCenter = composeRule.onNodeWithText("Relax")
            .fetchSemanticsNode().boundsInRoot.center.y
        val pageCenter = composeRule.onNodeWithText("Playlists")
            .fetchSemanticsNode().boundsInRoot.center.y
        val maximumToolbarCenterGap = with(composeRule.density) { 72.dp.toPx() }

        assertTrue(pageCenter - collectionCenter <= maximumToolbarCenterGap)
    }

    @Test
    fun flatFolderRowUsesTwoLineTitleWithoutFilenameSubtitle() {
        val longTitle = "ASMR Clay Cracking Balls Super Cube and Other Relaxing Sounds"
        val mediaFile = mediaFile(
            displayTitle = longTitle,
            filename = "$longTitle.mp3"
        )

        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.width(280.dp)) {
                    TrackListItemRow(
                        mediaFile = mediaFile,
                        isSelected = false,
                        onSelectToggle = {},
                        onClick = {},
                        showMusicMetadata = false,
                        modifier = Modifier.testTag("flat-folder-row")
                    )
                }
            }
        }

        composeRule.onNodeWithText(longTitle).assertIsDisplayed()
        composeRule.onNodeWithText(mediaFile.filename).assertDoesNotExist()
        val rowHeight = composeRule.onNodeWithTag("flat-folder-row")
            .fetchSemanticsNode().boundsInRoot.height
        val twoLineDensityLimit = with(composeRule.density) { 88.dp.toPx() }
        assertTrue(rowHeight <= twoLineDensityLimit)
    }

    @Test
    fun flatPlaylistRowKeepsCurrentAndUnavailableStatesWithoutDuplicateFilename() {
        val longTitle = "ASMR Sleep With Deep Brain Triggers for a Restful Night"
        val currentFile = mediaFile(longTitle, "$longTitle.mp3")
        val unavailableFile = mediaFile(
            displayTitle = "Unavailable long audio file title that still wraps cleanly",
            filename = "Unavailable long audio file title that still wraps cleanly.mp3",
            isAvailable = false
        )

        composeRule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column(modifier = Modifier.width(320.dp)) {
                    PlaylistItemRow(
                        index = 1,
                        mediaFile = currentFile,
                        isFirst = true,
                        isLast = false,
                        isSearchActive = false,
                        isCurrent = true,
                        isPlaying = true,
                        showMusicMetadata = false,
                        onTrackClick = {},
                        onMoveToTop = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        onMoveToBottom = {},
                        onRemove = {}
                    )
                    PlaylistItemRow(
                        index = 2,
                        mediaFile = unavailableFile,
                        isFirst = false,
                        isLast = true,
                        isSearchActive = false,
                        isCurrent = false,
                        isPlaying = false,
                        showMusicMetadata = false,
                        onTrackClick = {},
                        onMoveToTop = {},
                        onMoveUp = {},
                        onMoveDown = {},
                        onMoveToBottom = {},
                        onRemove = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText(currentFile.filename, useUnmergedTree = true).assertDoesNotExist()
        composeRule.onNodeWithText(longTitle, useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("(Unavailable)", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNode(hasStateDescription("Currently playing")).assertIsDisplayed()
    }

    @Test
    fun musicFolderRowKeepsArtistAndAlbumSubtitle() {
        val mediaFile = mediaFile(
            displayTitle = "Music Title",
            filename = "Music Title.mp3",
            artist = "Music Artist",
            album = "Music Album"
        )

        composeRule.setContent {
            MaterialTheme {
                TrackListItemRow(
                    mediaFile = mediaFile,
                    isSelected = true,
                    onSelectToggle = {},
                    onClick = {},
                    showMusicMetadata = true
                )
            }
        }

        composeRule.onNodeWithText("Music Title").assertIsDisplayed()
        composeRule.onNodeWithText("Music Artist • Music Album").assertIsDisplayed()
    }

    private fun mediaFile(
        displayTitle: String,
        filename: String,
        artist: String? = null,
        album: String? = null,
        isAvailable: Boolean = true
    ) = MediaFile(
        id = filename,
        sourceId = "source-1",
        folderId = "folder-1",
        documentUri = "content://test/$filename",
        relativePath = filename,
        filename = filename,
        displayTitle = displayTitle,
        mimeType = "audio/mpeg",
        size = 1_024L,
        modifiedTimeMs = 1L,
        artist = artist,
        album = album,
        isAvailable = isAvailable
    )
}
