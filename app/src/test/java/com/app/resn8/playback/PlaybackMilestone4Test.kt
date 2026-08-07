package com.app.resn8.playback

import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.data.repository.FakePlaylistRepository
import com.app.resn8.data.repository.FakeQueueRepository
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaybackOrigin
import com.app.resn8.domain.model.QueueStartRequest
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.repository.UiSessionRepository
import com.app.resn8.domain.usecase.StartQueueUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackMilestone4Test {

    private val mediaRepository = FakeMediaRepository()
    private val playlistRepository = FakePlaylistRepository()
    private val queueRepository = FakeQueueRepository()
    private val uiSessionRepository = object : UiSessionRepository {
        private val _state = MutableStateFlow(UiSessionState())
        override fun getUiSessionStateFlow() = _state
        override suspend fun saveUiSessionState(state: UiSessionState) {
            _state.value = state
        }
    }

    private val startQueueUseCase = StartQueueUseCase(
        mediaRepository = mediaRepository,
        playlistRepository = playlistRepository,
        queueRepository = queueRepository,
        uiSessionRepository = uiSessionRepository
    )

    private fun createTestMediaFile(id: String, artist: String = "Artist A", album: String = "Album A", trackNumber: Int = 1, isAvailable: Boolean = true): MediaFile {
        return MediaFile(
            id = id,
            sourceId = "src1",
            folderId = "folder1",
            documentUri = "content://media/$id",
            relativePath = "Track_$id.mp3",
            filename = "Track_$id.mp3",
            displayTitle = "Track $id",
            mimeType = "audio/mpeg",
            size = 1024L,
            modifiedTimeMs = 1000L,
            artist = artist,
            album = album,
            trackNumber = trackNumber,
            isAvailable = isAvailable
        )
    }

    @Test
    fun `startQueue creates explicit queue and updates session state`() = runBlocking {
        val tracks = (1..5).map { i ->
            createTestMediaFile(id = "track_$i", trackNumber = i)
        }
        mediaRepository.addMediaFiles(tracks)

        val query = LibraryQuery(
            collectionId = "3b392d10-48c0-4b32-8ca3-7db67ef5a8d0",
            sort = SortOrder.TRACK
        )

        val request = QueueStartRequest.Library(
            query = query,
            startingMediaId = "track_3",
            origin = PlaybackOrigin.AllTracks
        )

        val result = startQueueUseCase(request)
        assertTrue(result.isSuccess)

        val queue = result.getOrNull()
        assertNotNull(queue)
        assertEquals(SavedQueueKind.EXPLICIT, queue?.kind)
        assertEquals("3b392d10-48c0-4b32-8ca3-7db67ef5a8d0", queue?.collectionId)
        assertEquals(5, queue?.orderedMediaIds?.size)
        assertEquals("track_3", queue?.currentMediaId)
        assertEquals(2, queue?.currentIndex)
        assertEquals(PlaybackOrigin.AllTracks, queue?.filterSnapshot?.origin)

        val sessionState = uiSessionRepository.getUiSessionStateFlow().value
        assertEquals(queue?.id, sessionState.activeQueueId)
    }

    @Test
    fun `startQueue filters out unavailable files`() = runBlocking {
        val tracks = listOf(
            createTestMediaFile(id = "t1", isAvailable = true),
            createTestMediaFile(id = "t2", isAvailable = false),
            createTestMediaFile(id = "t3", isAvailable = true)
        )
        mediaRepository.addMediaFiles(tracks)

        val query = LibraryQuery(collectionId = "DEFAULT_COLLECTION")
        val request = QueueStartRequest.Library(query = query, startingMediaId = "t3", origin = PlaybackOrigin.AllTracks)

        val result = startQueueUseCase(request)
        assertTrue(result.isSuccess)
        val queue = result.getOrThrow()
        assertEquals(listOf("t1", "t3"), queue.orderedMediaIds)
        assertEquals("t3", queue.currentMediaId)
        assertEquals(1, queue.currentIndex)
    }

    @Test
    fun `startQueue fails when starting track is unavailable`() = runBlocking {
        val tracks = listOf(
            createTestMediaFile(id = "t1", isAvailable = false)
        )
        mediaRepository.addMediaFiles(tracks)

        val query = LibraryQuery(collectionId = "DEFAULT_COLLECTION")
        val request = QueueStartRequest.Library(query = query, startingMediaId = "t1", origin = PlaybackOrigin.AllTracks)

        val result = startQueueUseCase(request)
        assertTrue(result.isFailure)
    }

    @Test
    fun `getMediaFilesByIdsPreservingOrder preserves caller order and duplicates`() = runBlocking {
        val tracks = listOf(
            createTestMediaFile(id = "m1"),
            createTestMediaFile(id = "m2")
        )
        mediaRepository.addMediaFiles(tracks)

        val inputIds = listOf("m2", "m1", "m2")
        val result = mediaRepository.getMediaFilesByIdsPreservingOrder(inputIds)

        assertEquals(3, result.size)
        assertEquals("m2", result[0].id)
        assertEquals("m1", result[1].id)
        assertEquals("m2", result[2].id)
    }

    @Test
    fun `25,000 item queue creation performance benchmark`() = runBlocking {
        val tracks = (1..25000).map { i ->
            createTestMediaFile(id = "m_$i")
        }
        mediaRepository.addMediaFiles(tracks)

        val query = LibraryQuery(collectionId = "DEFAULT_COLLECTION")
        val availableIds = mediaRepository.snapshotVisibleMediaIds(query)
        val targetMediaId = availableIds[12500]
        val request = QueueStartRequest.Library(query = query, startingMediaId = targetMediaId, origin = PlaybackOrigin.AllTracks)

        val start = System.currentTimeMillis()
        val result = startQueueUseCase(request)
        val elapsed = System.currentTimeMillis() - start

        assertTrue(result.isSuccess)
        val queue = result.getOrThrow()
        assertEquals(25000, queue.orderedMediaIds.size)
        assertEquals(12500, queue.currentIndex)
        println("25,000 item queue creation took: ${elapsed}ms")
        assertTrue("Queue creation took $elapsed ms, expected under 1000ms", elapsed < 1000)
    }
}
