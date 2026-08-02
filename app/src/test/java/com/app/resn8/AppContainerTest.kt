package com.app.resn8

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.di.DefaultAppContainer
import com.app.resn8.di.TestAppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppContainerTest {

    private lateinit var container: DefaultAppContainer
    private lateinit var testContainer: TestAppContainer

    @Before
    fun setUp() {
        container = DefaultAppContainer(ApplicationProvider.getApplicationContext())
        testContainer = TestAppContainer()
    }

    @Test
    fun appContainer_initializesRepositories() {
        assertNotNull(container.mediaRepository)
        assertNotNull(container.collectionRepository)
        assertNotNull(container.playlistRepository)
        assertNotNull(container.queueRepository)
        assertNotNull(container.uiSessionRepository)
    }

    @Test
    fun testAppContainer_returnsInitialEmptyFlow() = runBlocking {
        val mediaFiles = testContainer.mediaRepository.getMediaFilesFlow(collectionId = "col_1").first()
        assertTrue(mediaFiles.isEmpty())

        val collections = testContainer.collectionRepository.getCollectionsFlow().first()
        assertTrue(collections.isEmpty())
    }
}
