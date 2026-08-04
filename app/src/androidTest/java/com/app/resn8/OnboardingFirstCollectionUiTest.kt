package com.app.resn8

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.ui.screens.CompleteSummaryContent
import com.app.resn8.ui.screens.FirstRunContent
import com.app.resn8.ui.screens.FolderNamingDialog
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingFirstCollectionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<PostIndexTestActivity>()

    @Test
    fun musicIsDefaultAndAudioFilesUpdatesTheFolderAction() {
        var selectedProfile by mutableStateOf(CollectionProfile.MUSIC)
        var folderSelectionProfile: CollectionProfile? = null

        composeRule.setContent {
            MaterialTheme {
                FirstRunContent(
                    selectedProfile = selectedProfile,
                    onProfileSelected = { selectedProfile = it },
                    onSelectFolderClicked = { folderSelectionProfile = selectedProfile }
                )
            }
        }

        composeRule.onNode(hasText("Music") and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithText("Select Music Folder").assertIsDisplayed()

        composeRule.onNode(hasText("Audio Files") and hasClickAction()).performClick()
        composeRule.onNode(hasText("Audio Files") and hasClickAction()).assertIsSelected()
        composeRule.onNodeWithText("Browse general audio by filename and folder.").assertIsDisplayed()
        composeRule.onNodeWithText("Select Audio Files Folder").assertIsDisplayed().performClick()

        composeRule.runOnIdle {
            assertEquals(CollectionProfile.FLAT, folderSelectionProfile)
        }
    }

    @Test
    fun namingDialogTrimsTheConfirmedCollectionName() {
        var confirmedName: String? = null

        composeRule.setContent {
            MaterialTheme {
                FolderNamingDialog(
                    defaultName = "Music",
                    onConfirm = { confirmedName = it },
                    onDismiss = {}
                )
            }
        }

        composeRule.onNodeWithText("Music").performTextReplacement("  Relax  ")
        composeRule.onNodeWithText("Start Indexing").performClick()

        composeRule.runOnIdle {
            assertEquals("Relax", confirmedName)
        }
    }

    @Test
    fun flatCompletionOffersFoldersAsTheDestination() {
        var opened = false

        composeRule.setContent {
            MaterialTheme {
                CompleteSummaryContent(
                    profile = CollectionProfile.FLAT,
                    summary = ScanResult(
                        scannedCount = 3,
                        addedCount = 3,
                        updatedCount = 0,
                        unavailableCount = 0,
                        tagDerivedCount = 0,
                        pathDerivedCount = 0,
                        unrecognizedCount = 3,
                        unreadableCount = 0,
                        durationMs = 1_000
                    ),
                    onOpenCollectionClicked = { opened = true }
                )
            }
        }

        composeRule.onNodeWithText("Audio files ready").assertIsDisplayed()
        composeRule.onNodeWithText("Open Folders").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(true, opened) }
    }
}
