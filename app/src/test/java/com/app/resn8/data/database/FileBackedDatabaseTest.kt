package com.app.resn8.data.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.FolderNodeEntity
import com.app.resn8.data.database.entity.MediaFileEntity
import com.app.resn8.data.database.entity.PlaylistEntity
import com.app.resn8.data.database.entity.PlaylistItemEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.data.database.entity.SavedQueueEntity
import com.app.resn8.data.database.entity.SavedQueueItemEntity
import com.app.resn8.data.database.entity.UiSessionStateEntity
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.SortOrder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FileBackedDatabaseTest {

    private lateinit var dbFile: File

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        dbFile = File(context.cacheDir, "test_resn8_file_backed.db")
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @Test
    fun database_survivesCloseAndReopen() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()

        // 1. Create file-backed DB & populate data
        var db = Room.databaseBuilder(context, Resn8Database::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

        val collection = CollectionEntity("col_file", "Durable Collection", CollectionProfile.MUSIC, 1000L, 1000L)
        db.collectionDao().insertCollection(collection)

        val rootSource = RootSourceEntity("src_file", "col_file", "tree://durable", "Durable Root", true, null, null, null, null, null)
        db.collectionDao().insertRootSource(rootSource)

        val folder = FolderNodeEntity("f_file", "src_file", null, "", "Root")
        db.folderDao().insertFolderNode(folder)

        val media = MediaFileEntity(
            id = "m_file", sourceId = "src_file", folderId = "f_file", documentUri = "uri://durable_track", documentId = "doc_100",
            relativePath = "durable_track.mp3", filename = "durable_track.mp3", displayTitle = "Durable Track", mimeType = "audio/mpeg",
            size = 2048L, durationMs = 240000L, modifiedTimeMs = 5000L, firstIndexedAt = 10000L, isAvailable = true,
            metadataScanStatus = MetadataScanStatus.SUCCESS, title = "Durable Track", artist = "Durable Artist", albumArtist = null,
            album = "Durable Album", discNumber = 1, trackNumber = 5, year = 2026, genre = "Synth", artworkUri = null,
            titleSource = null, artistSource = null, albumArtistSource = null, albumSource = null,
            discNumberSource = null, trackNumberSource = null, playCount = 12, lastPlayedAt = 99999L, likeScore = 1
        )
        db.mediaFileDao().insertMediaFiles(listOf(media))

        val playlist = PlaylistEntity("p_file", "col_file", "My Saved Playlist", "my saved playlist", 2000L, 2000L)
        db.playlistDao().insertPlaylist(playlist)
        val playlistItem = PlaylistItemEntity("p_file", "m_file", 1024L, 2000L)
        db.playlistDao().insertPlaylistItems(listOf(playlistItem))

        val queue = SavedQueueEntity(
            id = "q_file", collectionId = "col_file", kind = SavedQueueKind.EXPLICIT, mode = null, filterSnapshot = null,
            seed = null, currentIndex = 0, currentMediaId = "m_file", currentOccurrenceId = "occ_restored_1", positionMs = 12345L,
            playWhenReadyIntent = false, playbackSpeed = 1.0f, repeatMode = RepeatMode.OFF, createdAt = 3000L, updatedAt = 3000L
        )
        db.savedQueueDao().upsertSavedQueue(queue)
        val queueItem = SavedQueueItemEntity("q_file", 0, "item_q_1", "m_file")
        db.savedQueueDao().insertSavedQueueItems(listOf(queueItem))

        val uiSession = UiSessionStateEntity(
            id = 1, currentRoute = "now_playing", selectedCollectionId = "col_file", selectedFolderId = "f_file",
            selectedArtist = "Durable Artist", selectedAlbum = "Durable Album", selectedPlaylistId = "p_file",
            activeQueueId = "q_file", activeSearchQuery = "Durable", activeSort = SortOrder.MOST_LIKED, activeFilterSnapshot = null
        )
        db.uiSessionDao().upsertUiSessionState(uiSession)

        // 2. Close database connection
        db.close()

        // 3. Re-open NEW database instance on same file
        db = Room.databaseBuilder(context, Resn8Database::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()

        // 4. Verify all persisted state is intact
        val restoredCol = db.collectionDao().getCollectionById("col_file")
        assertNotNull(restoredCol)
        assertEquals("Durable Collection", restoredCol?.name)

        val restoredMedia = db.mediaFileDao().getMediaFileById("m_file")
        assertNotNull(restoredMedia)
        assertEquals("Durable Track", restoredMedia?.displayTitle)
        assertEquals(10000L, restoredMedia?.firstIndexedAt)
        assertEquals(12, restoredMedia?.playCount)
        assertEquals(1, restoredMedia?.likeScore)

        val restoredPlaylist = db.playlistDao().getPlaylistById("p_file")
        assertNotNull(restoredPlaylist)
        assertEquals("My Saved Playlist", restoredPlaylist?.name)
        val restoredPlaylistItems = db.playlistDao().getPlaylistItems("p_file")
        assertEquals(1, restoredPlaylistItems.size)
        assertEquals("m_file", restoredPlaylistItems[0].mediaId)

        val restoredQueue = db.savedQueueDao().getSavedQueueById("q_file")
        assertNotNull(restoredQueue)
        assertEquals(12345L, restoredQueue?.positionMs)
        assertEquals("occ_restored_1", restoredQueue?.currentOccurrenceId)

        val restoredUiSession = db.uiSessionDao().getUiSessionState()
        assertNotNull(restoredUiSession)
        assertEquals("now_playing", restoredUiSession?.currentRoute)
        assertEquals(SortOrder.MOST_LIKED, restoredUiSession?.activeSort)

        db.close()
    }
}
