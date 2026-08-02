package com.app.resn8.data.database

import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.FolderNodeEntity
import com.app.resn8.data.database.entity.MediaFileEntity
import com.app.resn8.data.database.entity.PlaybackHistoryEntity
import com.app.resn8.data.database.entity.PlaylistEntity
import com.app.resn8.data.database.entity.PlaylistItemEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.PlaybackHistoryResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {

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
    fun database_initializesSuccessfully() {
        assertNotNull(db.collectionDao())
        assertNotNull(db.folderDao())
        assertNotNull(db.scanDao())
        assertNotNull(db.mediaFileDao())
        assertNotNull(db.playlistDao())
        assertNotNull(db.playbackHistoryDao())
        assertNotNull(db.savedQueueDao())
        assertNotNull(db.uiSessionDao())
    }

    @Test(expected = Exception::class)
    fun rootSource_uniqueTreeUri_enforced() = runBlocking {
        val col = CollectionEntity("col_1", "Main", CollectionProfile.MUSIC, 1000L, 1000L)
        db.collectionDao().insertCollection(col)

        val src1 = RootSourceEntity("src_1", "col_1", "tree://uri1", "Src 1", true, null, null, null, null, null)
        val src2 = RootSourceEntity("src_2", "col_1", "tree://uri1", "Src 2", true, null, null, null, null, null)

        db.collectionDao().insertRootSource(src1)
        db.collectionDao().insertRootSource(src2) // Throws unique constraint exception
    }

    @Test(expected = Exception::class)
    fun playlist_uniqueNormalizedName_enforced() = runBlocking {
        val col = CollectionEntity("col_1", "Main", CollectionProfile.MUSIC, 1000L, 1000L)
        db.collectionDao().insertCollection(col)

        val p1 = PlaylistEntity("p1", "col_1", "Rock Hits", "rock hits", 1000L, 1000L)
        val p2 = PlaylistEntity("p2", "col_1", "ROCK HITS", "rock hits", 1000L, 1000L)

        db.playlistDao().insertPlaylist(p1)
        db.playlistDao().insertPlaylist(p2) // Throws constraint exception
    }

    @Test(expected = Exception::class)
    fun playbackHistory_uniqueSessionOccurrenceId_enforced() = runBlocking {
        val col = CollectionEntity("col_1", "Main", CollectionProfile.MUSIC, 1000L, 1000L)
        db.collectionDao().insertCollection(col)
        val src = RootSourceEntity("src_1", "col_1", "tree://uri1", "Src 1", true, null, null, null, null, null)
        db.collectionDao().insertRootSource(src)
        val folder = FolderNodeEntity("f1", "src_1", null, "", "Root")
        db.folderDao().insertFolderNode(folder)

        val media = MediaFileEntity(
            id = "m1", sourceId = "src_1", folderId = "f1", documentUri = "uri1", documentId = null,
            relativePath = "track.mp3", filename = "track.mp3", displayTitle = "Track", mimeType = "audio/mpeg",
            size = 1000, durationMs = 180000, modifiedTimeMs = 1000, firstIndexedAt = 1000, isAvailable = true,
            metadataScanStatus = MetadataScanStatus.SUCCESS, title = null, artist = null, albumArtist = null,
            album = null, discNumber = null, trackNumber = null, year = null, genre = null, artworkUri = null,
            titleSource = null, artistSource = null, albumArtistSource = null, albumSource = null,
            discNumberSource = null, trackNumberSource = null, playCount = 0, lastPlayedAt = null, likeScore = 0
        )
        db.mediaFileDao().insertMediaFiles(listOf(media))

        val h1 = PlaybackHistoryEntity("h1", "m1", "occ_1", 1000L, 2000L, 1000L, PlaybackHistoryResult.THRESHOLD_COUNTED, 2000L)
        val h2 = PlaybackHistoryEntity("h2", "m1", "occ_1", 3000L, 4000L, 1000L, PlaybackHistoryResult.THRESHOLD_COUNTED, 4000L)

        db.playbackHistoryDao().insertHistory(h1)
        db.playbackHistoryDao().insertHistory(h2) // Throws constraint exception
    }
}
