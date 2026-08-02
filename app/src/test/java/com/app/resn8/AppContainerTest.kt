package com.app.resn8

import com.app.resn8.di.DefaultAppContainer
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppContainerTest {

    private lateinit var container: DefaultAppContainer

    @Before
    fun setUp() {
        container = DefaultAppContainer()
    }

    @Test
    fun appContainer_initializesRepositories() {
        assertNotNull(container.mediaRepository)
        assertNotNull(container.collectionRepository)
        assertNotNull(container.playlistRepository)
        assertNotNull(container.queueRepository)
    }

    @Test
    fun fakeMediaRepository_returnsInitialEmptyFlow() = runBlocking {
        val mediaFiles = container.mediaRepository.getMediaFilesFlow(collectionId = "col_1").first()
        assertTrue(mediaFiles.isEmpty())
    }

    @Test
    fun fakeCollectionRepository_returnsInitialEmptyFlow() = runBlocking {
        val collections = container.collectionRepository.getCollectionsFlow().first()
        assertTrue(collections.isEmpty())
    }
}
