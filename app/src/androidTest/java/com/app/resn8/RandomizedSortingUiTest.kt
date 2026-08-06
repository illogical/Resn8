package com.app.resn8

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.data.repository.FakePlaylistRepository
import com.app.resn8.ui.playlists.PlaylistDetailViewModel
import com.app.resn8.ui.screens.PlaylistDetailScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RandomizedSortingUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PostIndexTestActivity>()

    @Test
    fun playlistDetailExposesExactlyFourMethodsAndDislikedRemovalDisclosure() {
        val viewModel = PlaylistDetailViewModel(
            playlistId = "playlist",
            playlistRepository = FakePlaylistRepository(),
            mediaRepository = FakeMediaRepository()
        )
        composeRule.setContent {
            MaterialTheme {
                PlaylistDetailScreen(
                    viewModel = viewModel,
                    onBack = {},
                    onTrackClick = {},
                    onPlayAll = {},
                    onRandomizedSortingApplied = {}
                )
            }
        }

        composeRule.onNodeWithContentDescription("Randomized Sorting").performClick()
        composeRule.onNodeWithText("Disliked tracks will be removed").assertIsDisplayed()
        composeRule.onNodeWithText("Least Played").assertIsDisplayed()
        composeRule.onNodeWithText("Most Played").assertIsDisplayed()
        composeRule.onNodeWithText("Most Liked").assertIsDisplayed()
        composeRule.onNodeWithText("Recently Added").assertIsDisplayed()
    }
}
