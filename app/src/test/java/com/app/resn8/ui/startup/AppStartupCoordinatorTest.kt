package com.app.resn8.ui.startup

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.FakeCollectionRepository
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.data.repository.FakePlaylistRepository
import com.app.resn8.data.repository.FakeQueueRepository
import com.app.resn8.di.TestAppContainer
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueItem
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.ui.navigation.LibraryRoute
import com.app.resn8.ui.navigation.NowPlayingRoute
import com.app.resn8.ui.navigation.RestorableDestination
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppStartupCoordinatorTest {

    @Test
    fun unconfiguredApp_resolvesToNeedsSetup() = runBlocking {
        val collectionRepo = FakeCollectionRepository()
        val mediaRepo = FakeMediaRepository()
        val playlistRepo = FakePlaylistRepository()
        val queueRepo = FakeQueueRepository()

        val container = TestAppContainer(
            collectionRepository = collectionRepo,
            mediaRepository = mediaRepo,
            playlistRepository = playlistRepo,
            queueRepository = queueRepo
        )

        val coordinator = AppStartupCoordinator(container)
        coordinator.resolveStartupState()

        val state = coordinator.state.value
        assertEquals(StartupState.NeedsSetup, state)
    }

    @Test
    fun configuredApp_withStaleOnboardingRoute_andValidActiveQueue_resolvesToNowPlaying() = runBlocking {
        val collectionRepo = FakeCollectionRepository()
        val col = collectionRepo.createCollection("Music Collection")
        collectionRepo.addRootSource(col.id, "content://test", "Music")

        val mediaRepo = FakeMediaRepository()
        val playlistRepo = FakePlaylistRepository()
        val queueRepo = FakeQueueRepository()

        val queue = SavedQueue(
            id = "q1",
            collectionId = col.id,
            kind = SavedQueueKind.EXPLICIT,
            orderedMediaIds = listOf("m1"),
            items = listOf(SavedQueueItem("qitem1", "m1"))
        )
        queueRepo.saveQueue(queue)

        val container = TestAppContainer(
            collectionRepository = collectionRepo,
            mediaRepository = mediaRepo,
            playlistRepository = playlistRepo,
            queueRepository = queueRepo
        )

        container.uiSessionRepository.saveUiSessionState(
            UiSessionState(
                currentRoute = "onboarding",
                selectedCollectionId = col.id,
                activeQueueId = "q1"
            )
        )

        val coordinator = AppStartupCoordinator(container)
        coordinator.resolveStartupState()

        val state = coordinator.state.value
        assertTrue(state is StartupState.Ready)
        val ready = state as StartupState.Ready
        assertEquals(RestorableDestination.NowPlaying, ready.destination)
        assertEquals(NowPlayingRoute, ready.startRoute)

        val savedSession = container.uiSessionRepository.getUiSessionStateFlow().first()
        assertEquals("now_playing", savedSession.currentRoute)
    }

    @Test
    fun configuredApp_withStaleOnboardingRoute_andNoActiveQueue_resolvesToLibrary() = runBlocking {
        val collectionRepo = FakeCollectionRepository()
        val col = collectionRepo.createCollection("Music Collection")
        collectionRepo.addRootSource(col.id, "content://test", "Music")

        val mediaRepo = FakeMediaRepository()
        val playlistRepo = FakePlaylistRepository()
        val queueRepo = FakeQueueRepository()

        val container = TestAppContainer(
            collectionRepository = collectionRepo,
            mediaRepository = mediaRepo,
            playlistRepository = playlistRepo,
            queueRepository = queueRepo
        )

        container.uiSessionRepository.saveUiSessionState(
            UiSessionState(
                currentRoute = "onboarding",
                selectedCollectionId = col.id,
                activeQueueId = null
            )
        )

        val coordinator = AppStartupCoordinator(container)
        coordinator.resolveStartupState()

        val state = coordinator.state.value
        assertTrue(state is StartupState.Ready)
        val ready = state as StartupState.Ready
        assertTrue(ready.destination is RestorableDestination.Library)
        assertTrue(ready.startRoute is LibraryRoute)

        val savedSession = container.uiSessionRepository.getUiSessionStateFlow().first()
        assertEquals("library", savedSession.currentRoute)
    }
}
