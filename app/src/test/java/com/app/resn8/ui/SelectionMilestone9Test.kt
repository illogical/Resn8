package com.app.resn8.ui

import com.app.resn8.data.repository.FakeCollectionRepository
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.RootSource
import com.app.resn8.fixtures.createTestMediaFile
import com.app.resn8.ui.folders.FoldersViewModel
import com.app.resn8.ui.library.AlbumDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SelectionMilestone9Test {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() = Dispatchers.setMain(dispatcher)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun folderSelectAllUsesAvailableDirectFilesOnly() = runTest(dispatcher) {
        val root = FolderNode("root", "source", null, "", "Root")
        val child = FolderNode("child", "source", "root", "Child", "Child")
        val media = FakeMediaRepository(
            initialMediaFiles = listOf(
                createTestMediaFile(id = "direct", sourceId = "source", folderId = "root"),
                createTestMediaFile(id = "unavailable", sourceId = "source", folderId = "root", isAvailable = false),
                createTestMediaFile(id = "descendant", sourceId = "source", folderId = "child")
            ),
            initialFolderNodes = listOf(root, child)
        )
        val collections = FakeCollectionRepository(
            initialCollections = listOf(Collection("collection", "Audio", CollectionProfile.FLAT)),
            initialRootSources = listOf(RootSource("source", "collection", "content://audio", "Audio"))
        )
        val viewModel = FoldersViewModel(
            "collection", "Audio", CollectionProfile.FLAT, "source", media, collections
        )
        advanceUntilIdle()

        viewModel.toggleSelectAllDirectAvailable()
        advanceUntilIdle()

        assertEquals(setOf("direct"), viewModel.selectedFileIds.value)
        assertTrue(viewModel.allDirectFilesSelected.value)
        assertFalse("descendant" in viewModel.selectedFileIds.value)
    }

    @Test
    fun albumSelectAllExcludesUnavailableAndTogglesOff() = runTest(dispatcher) {
        val media = FakeMediaRepository(
            initialMediaFiles = listOf(
                createTestMediaFile(id = "one", album = "Album"),
                createTestMediaFile(id = "two", album = "Album", isAvailable = false)
            )
        )
        val viewModel = AlbumDetailViewModel(
            "collection", MetadataGroupKey.Known("Album"), null, media
        )

        viewModel.toggleSelectAll()
        advanceUntilIdle()
        assertEquals(setOf("one"), viewModel.selectedFileIds.value)

        viewModel.toggleSelectAll()
        advanceUntilIdle()
        assertTrue(viewModel.selectedFileIds.value.isEmpty())
    }
}
