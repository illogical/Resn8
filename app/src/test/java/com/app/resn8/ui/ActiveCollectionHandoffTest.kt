package com.app.resn8.ui

import com.app.resn8.data.repository.FakeCollectionRepository
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.repository.UiSessionRepository
import com.app.resn8.ui.library.LibraryViewModel
import com.app.resn8.ui.session.ActiveCollectionState
import com.app.resn8.ui.session.ActiveCollectionViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ActiveCollectionHandoffTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun soleUuidCollectionRepairsAnEmptyLegacySession() = runTest(dispatcher) {
        val collectionId = "3b392d10-48c0-4b32-8ca3-7db67ef5a8d0"
        val sourceId = "f25a9c79-3075-486d-98f1-c20db963b894"
        val sessionRepository = RecordingUiSessionRepository()
        val collectionRepository = FakeCollectionRepository(
            initialCollections = listOf(
                Collection(collectionId, "Music", CollectionProfile.MUSIC)
            ),
            initialRootSources = listOf(
                RootSource(sourceId, collectionId, "content://test/music", "Music")
            )
        )

        val viewModel = ActiveCollectionViewModel(collectionRepository, sessionRepository)
        advanceUntilIdle()

        assertEquals(
            ActiveCollectionState.Ready::class,
            viewModel.state.value::class
        )
        val ready = viewModel.state.value as ActiveCollectionState.Ready
        assertEquals(collectionId, ready.selection.collectionId)
        assertEquals(sourceId, ready.selection.sourceId)
        assertEquals(collectionId, sessionRepository.state.value.selectedCollectionId)
        assertEquals(sourceId, sessionRepository.state.value.selectedSourceId)
    }

    @Test
    fun multipleCollectionsWithoutASelectionAreNeverGuessed() = runTest(dispatcher) {
        val sessionRepository = RecordingUiSessionRepository()
        val collectionRepository = FakeCollectionRepository(
            initialCollections = listOf(
                Collection("collection-a", "A"),
                Collection("collection-b", "B")
            )
        )

        val viewModel = ActiveCollectionViewModel(collectionRepository, sessionRepository)
        advanceUntilIdle()

        assertEquals(ActiveCollectionState.SelectionRequired, viewModel.state.value)
        assertNull(sessionRepository.state.value.selectedCollectionId)
    }

    @Test
    fun libraryInteractionsPersistTheRealUuidAsOneCurrentSnapshot() = runTest(dispatcher) {
        val collectionId = "8ae1e797-b389-49ec-aa3f-1caed7e6bc03"
        val sourceId = "c4241ef7-e5e8-4fd0-9d41-82fab3a5f888"
        val sessionRepository = RecordingUiSessionRepository()
        val viewModel = LibraryViewModel(
            collectionId = collectionId,
            sourceId = sourceId,
            mediaRepository = FakeMediaRepository(),
            uiSessionRepository = sessionRepository
        )
        advanceUntilIdle()

        viewModel.setSearchText("Miles")
        viewModel.setSurface(LibrarySurface.ALL_TRACKS)
        advanceUntilIdle()

        val saved = sessionRepository.state.value
        assertEquals(collectionId, saved.selectedCollectionId)
        assertEquals(sourceId, saved.selectedSourceId)
        assertEquals("Miles", saved.activeSearchQuery)
        assertEquals(LibrarySurface.ALL_TRACKS, saved.activeSurface)
        assertNull(viewModel.sessionError.value)
    }

    @Test
    fun sessionWriteFailureIsExposedWithoutEscapingTheViewModel() = runTest(dispatcher) {
        val sessionRepository = RecordingUiSessionRepository(failWrites = true)
        val viewModel = LibraryViewModel(
            collectionId = "valid-collection",
            sourceId = "valid-source",
            mediaRepository = FakeMediaRepository(),
            uiSessionRepository = sessionRepository
        )
        advanceUntilIdle()

        viewModel.setSearchText("safe")
        advanceUntilIdle()

        assertEquals("Library preferences could not be saved", viewModel.sessionError.value)
    }

    private class RecordingUiSessionRepository(
        initial: UiSessionState = UiSessionState(),
        private val failWrites: Boolean = false
    ) : UiSessionRepository {
        private val mutableState = MutableStateFlow(initial)
        val state = mutableState.asStateFlow()

        override fun getUiSessionStateFlow() = state

        override suspend fun saveUiSessionState(state: UiSessionState) {
            if (failWrites) error("synthetic write failure")
            mutableState.value = state
        }
    }
}
