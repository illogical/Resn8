package com.app.resn8

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.ui.NowPlayingContextAction
import com.app.resn8.domain.model.PlaybackOrigin
import com.app.resn8.ui.screens.NowPlayingScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingAdaptiveLayoutTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PostIndexTestActivity>()

    @Test
    fun portraitAndCompactLayoutsKeepEveryPlayerActionVisibleWithoutScrolling() {
        val width = mutableStateOf(360.dp)
        val height = mutableStateOf(640.dp)
        composeRule.setContent {
            MaterialTheme {
                Box(
                    modifier = Modifier
                        .width(width.value)
                        .height(height.value)
                ) {
                    TestPlayer()
                }
            }
        }

        assertControlsInsidePlayer()
        composeRule.onNode(hasScrollAction()).assertDoesNotExist()
        composeRule.onNodeWithText("View Queue").assertDoesNotExist()

        composeRule.runOnIdle {
            width.value = 320.dp
            height.value = 480.dp
        }
        composeRule.waitForIdle()
        assertControlsInsidePlayer()
        composeRule.onNode(hasScrollAction()).assertDoesNotExist()
    }

    @Test
    fun landscapeAndLargeFontLayoutsKeepEveryPlayerActionVisible() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = 1.5f)
            ) {
                MaterialTheme {
                    Box(modifier = Modifier.size(width = 640.dp, height = 360.dp)) {
                        TestPlayer()
                    }
                }
            }
        }

        assertControlsInsidePlayer()
        composeRule.onNode(hasScrollAction()).assertDoesNotExist()
    }

    @Test
    fun artworkShrinksThenDisappearsBeforeControls() {
        val height = mutableStateOf(520.dp)
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 360.dp, height = height.value)) {
                    TestPlayer()
                }
            }
        }

        val roomyArtworkHeight = composeRule.onNodeWithTag("now-playing-artwork")
            .fetchSemanticsNode().boundsInRoot.height

        composeRule.runOnIdle { height.value = 400.dp }
        composeRule.waitForIdle()
        val compactArtworkHeight = composeRule.onNodeWithTag("now-playing-artwork")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(compactArtworkHeight < roomyArtworkHeight)
        assertControlsInsidePlayer()

        composeRule.runOnIdle { height.value = 320.dp }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now-playing-artwork").assertDoesNotExist()
        assertControlsInsidePlayer()
    }

    @Test
    fun audioFilesUseCompactTwoLineTitleWithoutMusicMetadata() {
        val longTitle = "ASMR I Just KNOW You Want To SLEEP Right Now With A Very Long Filename"
        composeRule.setContent {
            MaterialTheme {
                Box(modifier = Modifier.size(width = 320.dp, height = 480.dp)) {
                    NowPlayingScreen(
                        title = longTitle,
                        artist = "Unknown Artist",
                        album = "Unknown Album",
                        showMusicMetadata = false,
                        durationMs = 60_000L
                    )
                }
            }
        }

        composeRule.onNodeWithTag("now-playing-title").assertTextEquals(longTitle)
        composeRule.onNodeWithText("Unknown Artist").assertDoesNotExist()
        composeRule.onNodeWithText("Unknown Album").assertDoesNotExist()
        val titleHeight = composeRule.onNodeWithTag("now-playing-title")
            .fetchSemanticsNode().boundsInRoot.height
        assertTrue(titleHeight <= with(composeRule.density) { 56.dp.toPx() })
    }

    @Test
    fun playlistActionKeepsFullLabelAndOnlyAppearsOnNowPlaying() {
        val longLabel = "Playlist: A Very Long Relaxation Playlist Name"
        var openedPlaylistId: String? = null
        val isNowPlaying = mutableStateOf(true)

        composeRule.setContent {
            MaterialTheme {
                Row(modifier = Modifier.width(180.dp).testTag("app-bar-actions")) {
                    NowPlayingContextAction(
                        isNowPlaying = isNowPlaying.value,
                        origin = PlaybackOrigin.Playlist("playlist-42", "A Very Long Relaxation Playlist Name"),
                        onOpenOrigin = { openedPlaylistId = (it as PlaybackOrigin.Playlist).playlistId }
                    )
                }
            }
        }

        composeRule.onNodeWithText(longLabel).assertIsDisplayed()
        composeRule.onNodeWithTag("now-playing-context-link").performClick()
        composeRule.runOnIdle { assertEquals("playlist-42", openedPlaylistId) }

        composeRule.runOnIdle { isNowPlaying.value = false }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("now-playing-context-link").assertDoesNotExist()
    }

    private fun assertControlsInsidePlayer() {
        val root = composeRule.onNodeWithTag("now-playing-root")
            .fetchSemanticsNode().boundsInRoot
        listOf(
            "Playback position",
            "Previous Track",
            "Play",
            "Next Track",
            "Dislike",
            "Like",
            "Add to Playlist"
        ).forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
            val bounds = composeRule.onNodeWithContentDescription(description)
                .fetchSemanticsNode().boundsInRoot
            assertContained(root, bounds, description)
        }
        composeRule.onNodeWithText("0").assertIsDisplayed()
    }

    private fun assertContained(root: Rect, child: Rect, label: String) {
        assertTrue("$label starts above the player", child.top >= root.top)
        assertTrue("$label ends below the player", child.bottom <= root.bottom)
        assertTrue("$label starts left of the player", child.left >= root.left)
        assertTrue("$label ends right of the player", child.right <= root.right)
    }

    @androidx.compose.runtime.Composable
    private fun TestPlayer() {
        NowPlayingScreen(
            title = "Favorite Things",
            artist = "Incubus",
            album = "S.C.I.E.N.C.E.",
            showMusicMetadata = true,
            likeScore = 0,
            positionMs = 9_000L,
            durationMs = 191_000L,
            canPlayPause = true,
            canSeek = true,
            canSkipPrevious = true,
            canSkipNext = true,
            modifier = Modifier.fillMaxSize()
        )
    }
}
