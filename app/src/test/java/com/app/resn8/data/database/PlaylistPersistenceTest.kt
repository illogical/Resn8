package com.app.resn8.data.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.data.repository.RoomPlaylistRepository
import com.app.resn8.domain.model.FolderNode
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.ScanResult
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
class PlaylistPersistenceTest {

    private lateinit var db: Resn8Database
    private lateinit var collectionRepo: RoomCollectionRepository
    private lateinit var mediaRepo: RoomMediaRepository
    private lateinit var playlistRepo: RoomPlaylistRepository
    private lateinit var colId: String

    @Before
    fun setUp() = runBlocking {
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        collectionRepo = RoomCollectionRepository(db)
        mediaRepo = RoomMediaRepository(db)
        playlistRepo = RoomPlaylistRepository(db)

        val col = collectionRepo.createCollection("Main Collection")
        colId = col.id
        val src = collectionRepo.addRootSource(colId, "tree://music", "Music")
        val folder = FolderNode("f1", src.id, null, "", "Root")
        val media1 = MediaFile(id = "m1", sourceId = src.id, folderId = "f1", documentUri = "uri://1", relativePath = "1.mp3", filename = "1.mp3", displayTitle = "1", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)
        val media2 = MediaFile(id = "m2", sourceId = src.id, folderId = "f1", documentUri = "uri://2", relativePath = "2.mp3", filename = "2.mp3", displayTitle = "2", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)
        val media3 = MediaFile(id = "m3", sourceId = src.id, folderId = "f1", documentUri = "uri://3", relativePath = "3.mp3", filename = "3.mp3", displayTitle = "3", mimeType = "audio/mpeg", size = 100, modifiedTimeMs = 100)

        val scanId = mediaRepo.startScanRun(src.id)
        mediaRepo.publishResolvedScan(scanId, listOf(folder), listOf(media1, media2, media3), emptyList(), ScanResult(3, 3, 0, 0, 0, 0, 0, 0, 100))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun playlist_normalizedNameUniqueness_enforced() = runBlocking {
        val res1 = playlistRepo.createPlaylist(colId, "Road Trip")
        assertTrue(res1.isSuccess)

        // Attempt creation with duplicate normalized name (whitespace + casing)
        val res2 = playlistRepo.createPlaylist(colId, "  road trip  ")
        assertTrue(res2.isFailure)
    }

    @Test
    fun playlistItems_rankAllocation_andCompaction() = runBlocking {
        val playlist = playlistRepo.createPlaylist(colId, "My List").getOrThrow()

        playlistRepo.addItemsToPlaylist(playlist.id, listOf("m1", "m2", "m3"))

        val items = playlistRepo.getPlaylistItemsFlow(playlist.id).first()
        assertEquals(3, items.size)
        assertEquals(1024L, items[0].position)
        assertEquals(2048L, items[1].position)
        assertEquals(3072L, items[2].position)

        // Reorder m3 to position 500 (before m1)
        playlistRepo.reorderPlaylistItem(playlist.id, "m3", 500L)
        val reordered = playlistRepo.getPlaylistItemsFlow(playlist.id).first()
        assertEquals("m3", reordered[0].mediaId)
        assertEquals("m1", reordered[1].mediaId)

        // Compact ranks
        playlistRepo.compactPlaylistRanks(playlist.id)
        val compacted = playlistRepo.getPlaylistItemsFlow(playlist.id).first()
        assertEquals(1024L, compacted[0].position)
        assertEquals("m3", compacted[0].mediaId)
        assertEquals(2048L, compacted[1].position)
        assertEquals("m1", compacted[1].mediaId)
    }

    @Test
    fun deletingPlaylist_deletesJoinRows_preservesMedia() = runBlocking {
        val playlist = playlistRepo.createPlaylist(colId, "Temporary").getOrThrow()
        playlistRepo.addItemsToPlaylist(playlist.id, listOf("m1", "m2"))

        playlistRepo.deletePlaylist(playlist.id)

        // Playlist items flow is empty
        val items = playlistRepo.getPlaylistItemsFlow(playlist.id).first()
        assertTrue(items.isEmpty())

        // Media files still exist
        assertNotNull(mediaRepo.getMediaFileById("m1"))
        assertNotNull(mediaRepo.getMediaFileById("m2"))
    }
}
