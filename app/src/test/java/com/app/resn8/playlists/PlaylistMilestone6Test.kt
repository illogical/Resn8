package com.app.resn8.playlists

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.data.repository.RoomPlaylistRepository
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.PlaylistMembershipState
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.usecase.StartQueueUseCase
import com.app.resn8.domain.model.QueueStartRequest
import com.app.resn8.data.repository.RoomQueueRepository
import com.app.resn8.data.repository.RoomUiSessionRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistMilestone6Test {

    private lateinit var db: Resn8Database
    private lateinit var collectionRepo: RoomCollectionRepository
    private lateinit var mediaRepo: RoomMediaRepository
    private lateinit var playlistRepo: RoomPlaylistRepository
    private lateinit var queueRepo: RoomQueueRepository
    private lateinit var uiSessionRepo: RoomUiSessionRepository
    private lateinit var startQueueUseCase: StartQueueUseCase
    private lateinit var colId: String

    @Before
    fun setUp() = runBlocking {
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        collectionRepo = RoomCollectionRepository(db)
        mediaRepo = RoomMediaRepository(db)
        playlistRepo = RoomPlaylistRepository(db)
        queueRepo = RoomQueueRepository(db)
        uiSessionRepo = RoomUiSessionRepository(db)
        startQueueUseCase = StartQueueUseCase(mediaRepo, playlistRepo, queueRepo, uiSessionRepo)

        val col = collectionRepo.createCollection("Main Collection")
        colId = col.id
        val src = collectionRepo.addRootSource(colId, "tree://music", "Music")
        val folder1 = FolderNode("f1", src.id, null, "Rock", "Rock")
        val folder2 = FolderNode("f2", src.id, "f1", "Rock/Hard", "Hard")

        val media1 = MediaFile(id = "m1", sourceId = src.id, folderId = "f1", documentUri = "uri://1", relativePath = "Rock/1.mp3", filename = "1.mp3", displayTitle = "Track 1", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)
        val media2 = MediaFile(id = "m2", sourceId = src.id, folderId = "f1", documentUri = "uri://2", relativePath = "Rock/2.mp3", filename = "2.mp3", displayTitle = "Track 2", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)
        val media3 = MediaFile(id = "m3", sourceId = src.id, folderId = "f2", documentUri = "uri://3", relativePath = "Rock/Hard/3.mp3", filename = "3.mp3", displayTitle = "Track 3", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)

        val scanId = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scanId, listOf(folder1, folder2), listOf(media1, media2, media3), emptyList(), ScanResult(3, 3, 0, 0, 0, 0, 0, 0, 100))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun triStateMembershipFlow_calculatesAllSomeNoneCorrectly() = runBlocking {
        val p1 = playlistRepo.createPlaylist(colId, "Playlist All").getOrThrow()
        val p2 = playlistRepo.createPlaylist(colId, "Playlist Some").getOrThrow()
        val p3 = playlistRepo.createPlaylist(colId, "Playlist None").getOrThrow()

        // p1 has m1, m2, m3 (All selected)
        playlistRepo.addItemsToPlaylist(p1.id, listOf("m1", "m2", "m3"))

        // p2 has m1 only (Some selected)
        playlistRepo.addItemsToPlaylist(p2.id, listOf("m1"))

        // p3 has no items (None selected)

        val candidatePlaylists = playlistRepo.getPlaylistsWithMembershipFlow(colId, listOf("m1", "m2", "m3")).first()

        assertEquals(3, candidatePlaylists.size)
        // Playlists containing ALL are sorted first
        assertEquals(p1.id, candidatePlaylists[0].playlist.id)
        assertEquals(PlaylistMembershipState.ALL, candidatePlaylists[0].membershipState)

        // Playlists containing SOME are sorted second
        assertEquals(p2.id, candidatePlaylists[1].playlist.id)
        assertEquals(PlaylistMembershipState.SOME, candidatePlaylists[1].membershipState)

        // Playlists containing NONE are sorted last
        assertEquals(p3.id, candidatePlaylists[2].playlist.id)
        assertEquals(PlaylistMembershipState.NONE, candidatePlaylists[2].membershipState)
    }

    @Test
    fun bulkAddAndRemove_updatesPlaylistItemsAtomically() = runBlocking {
        val playlist = playlistRepo.createPlaylist(colId, "Bulk Ops").getOrThrow()

        playlistRepo.addItemsToPlaylist(playlist.id, listOf("m1", "m2", "m3"))
        val itemsAfterAdd = playlistRepo.getPlaylistItems(playlist.id)
        assertEquals(3, itemsAfterAdd.size)

        // Bulk remove m1 and m3
        playlistRepo.removeItemsFromPlaylist(playlist.id, listOf("m1", "m3"))
        val itemsAfterRemove = playlistRepo.getPlaylistItems(playlist.id)
        assertEquals(1, itemsAfterRemove.size)
        assertEquals("m2", itemsAfterRemove[0].mediaId)
    }

    @Test
    fun duplicateAdditions_ignoredWithoutDuplicates() = runBlocking {
        val playlist = playlistRepo.createPlaylist(colId, "Dedupe").getOrThrow()

        playlistRepo.addItemsToPlaylist(playlist.id, listOf("m1", "m2"))
        playlistRepo.addItemsToPlaylist(playlist.id, listOf("m2", "m3"))

        val items = playlistRepo.getPlaylistItems(playlist.id)
        assertEquals(3, items.size)
        assertEquals(listOf("m1", "m2", "m3"), items.map { it.mediaId })
    }

    @Test
    fun startingQueueFromPlaylist_createsIsolatedSnapshot() = runBlocking {
        val playlist = playlistRepo.createPlaylist(colId, "Favorites").getOrThrow()
        playlistRepo.addItemsToPlaylist(playlist.id, listOf("m1", "m2", "m3"))

        val queueResult = startQueueUseCase(QueueStartRequest.Playlist(playlist.id, startingMediaId = "m1"))
        assertTrue(queueResult.isSuccess)
        val queue = queueResult.getOrThrow()

        val queueItemsBefore = db.savedQueueDao().getSavedQueueItems(queue.id)
        assertEquals(3, queueItemsBefore.size)
        assertEquals(listOf("m1", "m2", "m3"), queueItemsBefore.map { it.mediaId })

        // Now modify underlying playlist by removing m2
        playlistRepo.removeItemFromPlaylist(playlist.id, "m2")

        // Active playing queue items remain completely isolated and intact!
        val queueItemsAfter = db.savedQueueDao().getSavedQueueItems(queue.id)
        assertEquals(3, queueItemsAfter.size)
        assertEquals(listOf("m1", "m2", "m3"), queueItemsAfter.map { it.mediaId })
    }

    @Test
    fun folderDescendantsExpansion_resolvesNestedMediaIds() = runBlocking {
        val resolution = mediaRepo.resolveSelectionMediaIds(
            selectedFileIds = emptySet(),
            selectedFolderIds = setOf("f1")
        )

        assertNotNull(resolution)
        // f1 contains m1, m2 directly and m3 in subfolder f2
        assertEquals(3, resolution!!.uniqueMediaIds.size)
        assertTrue(resolution.uniqueMediaIds.containsAll(listOf("m1", "m2", "m3")))
    }
}
