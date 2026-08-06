package com.app.resn8.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.FolderNodeEntity
import com.app.resn8.data.database.entity.MediaFileEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MetadataScanStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoomLibrarySortV3Test {
    private lateinit var db: Resn8Database

    @Before
    fun setup() = runBlocking {
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext<Context>())
        db.collectionDao().insertCollection(CollectionEntity("collection", "Music", CollectionProfile.MUSIC, 1, 1))
        db.collectionDao().insertRootSource(
            RootSourceEntity("source", "collection", "tree://music", "Music", true, null, null, null, null, null)
        )
        db.folderDao().insertFolderNode(FolderNodeEntity("folder", "source", null, "", "Music"))
        db.mediaFileDao().insertMediaFiles(
            listOf(
                media("a", "Alpha", "Artist A", "Album A", 10, 2, 20, 1),
                media("z", "Zulu", "Artist Z", "Album Z", 30, 8, 40, -1),
                media("u", "Unknown", null, null, 20, 0, null, 4)
            )
        )
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun roomSnapshotSupportsEveryV3FieldInBothDirections() = runBlocking {
        assertEquals(listOf("a", "u", "z"), snapshot("TITLE", "ASCENDING"))
        assertEquals(listOf("z", "u", "a"), snapshot("TITLE", "DESCENDING"))
        assertEquals(listOf("a", "z", "u"), snapshot("ARTIST", "ASCENDING"))
        assertEquals(listOf("z", "a", "u"), snapshot("ARTIST", "DESCENDING"))
        assertEquals(listOf("a", "z", "u"), snapshot("ALBUM", "ASCENDING"))
        assertEquals(listOf("z", "a", "u"), snapshot("ALBUM", "DESCENDING"))
        assertEquals(listOf("a", "u", "z"), snapshot("RECENTLY_ADDED", "ASCENDING"))
        assertEquals(listOf("z", "u", "a"), snapshot("RECENTLY_ADDED", "DESCENDING"))
        assertEquals(listOf("u", "a", "z"), snapshot("MOST_PLAYED", "ASCENDING"))
        assertEquals(listOf("z", "a", "u"), snapshot("MOST_PLAYED", "DESCENDING"))
        assertEquals(listOf("a", "z", "u"), snapshot("MOST_RECENT", "ASCENDING"))
        assertEquals(listOf("z", "a", "u"), snapshot("MOST_RECENT", "DESCENDING"))
        assertEquals(listOf("z", "a", "u"), snapshot("MOST_LIKED", "ASCENDING"))
        assertEquals(listOf("u", "a", "z"), snapshot("MOST_LIKED", "DESCENDING"))
    }

    private suspend fun snapshot(sort: String, direction: String): List<String> =
        db.mediaFileDao().snapshotVisibleMediaIds(
            collectionId = "collection",
            sourceId = null,
            folderId = null,
            isArtistFilterNull = 1,
            artistKeyIsUnknown = 0,
            artistKeyValue = null,
            isAlbumFilterNull = 1,
            albumKeyIsUnknown = 0,
            albumKeyValue = null,
            availabilityFilter = "ALL",
            excludeDisliked = 0,
            searchPattern = null,
            sortOrder = sort,
            sortDirection = direction
        )

    private fun media(
        id: String,
        title: String,
        artist: String?,
        album: String?,
        indexed: Long,
        plays: Int,
        lastPlayed: Long?,
        rating: Int
    ) = MediaFileEntity(
        id = id,
        sourceId = "source",
        folderId = "folder",
        documentUri = "content://$id",
        documentId = id,
        relativePath = "$id.mp3",
        filename = "$id.mp3",
        displayTitle = title,
        mimeType = "audio/mpeg",
        size = 1,
        durationMs = 1,
        modifiedTimeMs = 1,
        firstIndexedAt = indexed,
        isAvailable = true,
        metadataScanStatus = MetadataScanStatus.SUCCESS,
        title = title,
        artist = artist,
        albumArtist = null,
        album = album,
        discNumber = 1,
        trackNumber = 1,
        year = null,
        genre = null,
        artworkUri = null,
        titleSource = null,
        artistSource = null,
        albumArtistSource = null,
        albumSource = null,
        discNumberSource = null,
        trackNumberSource = null,
        playCount = plays,
        lastPlayedAt = lastPlayed,
        likeScore = rating
    )
}
