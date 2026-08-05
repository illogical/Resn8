package com.app.resn8.data.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.data.repository.RoomPlaylistRepository
import com.app.resn8.data.repository.RoomQueueRepository
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.ScanResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CollectionDeletionTest {
    private lateinit var db: Resn8Database

    @Before
    fun setUp() {
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun deletionRemovesCollectionScopedAppDataInForeignKeySafeOrder() = runBlocking {
        val collections = RoomCollectionRepository(db)
        val media = RoomMediaRepository(db)
        val playlists = RoomPlaylistRepository(db)
        val queues = RoomQueueRepository(db)
        val collection = collections.createCollection("Delete Me")
        val source = collections.addRootSource(collection.id, "content://delete-me", "Delete Me")
        val folder = FolderNode("delete-folder", source.id, null, "", "Delete Me")
        val track = MediaFile(
            id = "delete-track",
            sourceId = source.id,
            folderId = folder.id,
            documentUri = "content://delete-me/track",
            relativePath = "track.mp3",
            filename = "track.mp3",
            displayTitle = "Track",
            mimeType = "audio/mpeg",
            size = 1,
            modifiedTimeMs = 1
        )
        val scanId = media.startScanRun(source.id)
        media.publishResolvedScan(scanId, listOf(folder), listOf(track), emptyList(), ScanResult(1, 1, 0, 0, 0, 0, 0, 0, 1))
        val playlist = playlists.createPlaylist(collection.id, "Playlist").getOrThrow()
        playlists.addItemsToPlaylist(playlist.id, listOf(track.id))
        val queue = queues.replaceQueueSnapshot(
            SavedQueue(id = "delete-queue", collectionId = collection.id, kind = SavedQueueKind.EXPLICIT),
            listOf(track.id)
        )
        collections.setCollectionActiveQueue(collection.id, queue.id)
        media.commitMeaningfulPlay(
            sessionOccurrenceId = "delete-occurrence",
            mediaId = track.id,
            startedAt = 1,
            accumulatedListenedDurationMs = 10,
            result = PlaybackHistoryResult.IN_PROGRESS
        )

        collections.deleteCollection(collection.id)

        assertTrue(collections.getCollectionsFlow().first().isEmpty())
        assertNull(media.getMediaFileById(track.id))
        assertNull(playlists.getPlaylistById(playlist.id))
        assertNull(queues.getQueueByIdFlow(queue.id).first())
        assertNull(collections.getCollectionPlaybackState(collection.id))
    }
}
