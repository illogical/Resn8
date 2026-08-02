package com.app.resn8.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.FolderNodeEntity
import com.app.resn8.data.database.entity.MediaFileEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.LibraryQuery
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MetadataValueSource
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
class BrowsingQueryTest {

    private lateinit var db: Resn8Database

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Resn8Database.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun collectionScopePreventsCrossCollectionLeakage() = runBlocking {
        val now = System.currentTimeMillis()
        db.collectionDao().insertCollection(CollectionEntity("col_1", "Col 1", CollectionProfile.MUSIC, now, now))
        db.collectionDao().insertCollection(CollectionEntity("col_2", "Col 2", CollectionProfile.MUSIC, now, now))

        db.collectionDao().insertRootSource(RootSourceEntity("src_1", "col_1", "uri_1", "Root 1", true, "COMPLETED", now, now, now, null))
        db.collectionDao().insertRootSource(RootSourceEntity("src_2", "col_2", "uri_2", "Root 2", true, "COMPLETED", now, now, now, null))

        db.folderDao().insertFolderNode(FolderNodeEntity("f_1", "src_1", null, "", "Root"))
        db.folderDao().insertFolderNode(FolderNodeEntity("f_2", "src_2", null, "", "Root"))

        db.mediaFileDao().insertMediaFile(
            MediaFileEntity(
                id = "m_1", sourceId = "src_1", folderId = "f_1", documentUri = "uri_1", documentId = "doc_1",
                relativePath = "t1.mp3", filename = "t1.mp3", displayTitle = "Track 1", mimeType = "audio/mpeg",
                size = 100, durationMs = 100, modifiedTimeMs = now, firstIndexedAt = now, isAvailable = true,
                metadataScanStatus = MetadataScanStatus.SUCCESS, title = "Track 1", artist = "Artist A",
                albumArtist = null, album = "Album A", discNumber = 1, trackNumber = 1, year = 2020, genre = "Pop",
                artworkUri = null, titleSource = MetadataValueSource.TAG, artistSource = MetadataValueSource.TAG,
                albumArtistSource = null, albumSource = MetadataValueSource.TAG, discNumberSource = MetadataValueSource.TAG,
                trackNumberSource = MetadataValueSource.TAG, playCount = 0, lastPlayedAt = null, likeScore = 0
            )
        )
        db.mediaFileDao().insertMediaFile(
            MediaFileEntity(
                id = "m_2", sourceId = "src_2", folderId = "f_2", documentUri = "uri_2", documentId = "doc_2",
                relativePath = "t2.mp3", filename = "t2.mp3", displayTitle = "Track 2", mimeType = "audio/mpeg",
                size = 100, durationMs = 100, modifiedTimeMs = now, firstIndexedAt = now, isAvailable = true,
                metadataScanStatus = MetadataScanStatus.SUCCESS, title = "Track 2", artist = "Artist B",
                albumArtist = null, album = "Album B", discNumber = 1, trackNumber = 1, year = 2020, genre = "Pop",
                artworkUri = null, titleSource = MetadataValueSource.TAG, artistSource = MetadataValueSource.TAG,
                albumArtistSource = null, albumSource = MetadataValueSource.TAG, discNumberSource = MetadataValueSource.TAG,
                trackNumberSource = MetadataValueSource.TAG, playCount = 0, lastPlayedAt = null, likeScore = 0
            )
        )

        val col1Snapshot = db.mediaFileDao().snapshotVisibleMediaIds(
            collectionId = "col_1",
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
            sortOrder = "TITLE"
        )

        assertEquals(listOf("m_1"), col1Snapshot)
    }

    @Test
    fun literalSearchEscapesWildcardsCorrectly() = runBlocking {
        val now = System.currentTimeMillis()
        db.collectionDao().insertCollection(CollectionEntity("col_1", "Col 1", CollectionProfile.MUSIC, now, now))
        db.collectionDao().insertRootSource(RootSourceEntity("src_1", "col_1", "uri_1", "Root 1", true, "COMPLETED", now, now, now, null))
        db.folderDao().insertFolderNode(FolderNodeEntity("f_1", "src_1", null, "", "Root"))

        db.mediaFileDao().insertMediaFile(
            MediaFileEntity(
                id = "m_1", sourceId = "src_1", folderId = "f_1", documentUri = "uri_1", documentId = "doc_1",
                relativePath = "100%_Pure.mp3", filename = "100%_Pure.mp3", displayTitle = "100% Pure", mimeType = "audio/mpeg",
                size = 100, durationMs = 100, modifiedTimeMs = now, firstIndexedAt = now, isAvailable = true,
                metadataScanStatus = MetadataScanStatus.SUCCESS, title = "100% Pure", artist = "Artist",
                albumArtist = null, album = "Album", discNumber = 1, trackNumber = 1, year = 2020, genre = "Pop",
                artworkUri = null, titleSource = MetadataValueSource.TAG, artistSource = MetadataValueSource.TAG,
                albumArtistSource = null, albumSource = MetadataValueSource.TAG, discNumberSource = MetadataValueSource.TAG,
                trackNumberSource = MetadataValueSource.TAG, playCount = 0, lastPlayedAt = null, likeScore = 0
            )
        )
        db.mediaFileDao().insertMediaFile(
            MediaFileEntity(
                id = "m_2", sourceId = "src_1", folderId = "f_1", documentUri = "uri_2", documentId = "doc_2",
                relativePath = "1000Pure.mp3", filename = "1000Pure.mp3", displayTitle = "1000Pure", mimeType = "audio/mpeg",
                size = 100, durationMs = 100, modifiedTimeMs = now, firstIndexedAt = now, isAvailable = true,
                metadataScanStatus = MetadataScanStatus.SUCCESS, title = "1000Pure", artist = "Artist",
                albumArtist = null, album = "Album", discNumber = 1, trackNumber = 1, year = 2020, genre = "Pop",
                artworkUri = null, titleSource = MetadataValueSource.TAG, artistSource = MetadataValueSource.TAG,
                albumArtistSource = null, albumSource = MetadataValueSource.TAG, discNumberSource = MetadataValueSource.TAG,
                trackNumberSource = MetadataValueSource.TAG, playCount = 0, lastPlayedAt = null, likeScore = 0
            )
        )

        val queryWithPercent = LibraryQuery(collectionId = "col_1", searchText = "100%")
        val snapshot = db.mediaFileDao().snapshotVisibleMediaIds(
            collectionId = "col_1",
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
            searchPattern = queryWithPercent.escapedSearchPattern(),
            sortOrder = "TITLE"
        )

        assertEquals(listOf("m_1"), snapshot)
    }

    @Test
    fun recursiveCteFolderSelectionResolution() = runBlocking {
        val now = System.currentTimeMillis()
        db.collectionDao().insertCollection(CollectionEntity("col_1", "Col 1", CollectionProfile.MUSIC, now, now))
        db.collectionDao().insertRootSource(RootSourceEntity("src_1", "col_1", "uri_1", "Root 1", true, "COMPLETED", now, now, now, null))

        db.folderDao().insertFolderNode(FolderNodeEntity("f_root", "src_1", null, "", "Root"))
        db.folderDao().insertFolderNode(FolderNodeEntity("f_sub1", "src_1", "f_root", "Sub1", "Sub1"))
        db.folderDao().insertFolderNode(FolderNodeEntity("f_sub2", "src_1", "f_sub1", "Sub1/Sub2", "Sub2"))

        db.mediaFileDao().insertMediaFile(
            MediaFileEntity(
                id = "m_1", sourceId = "src_1", folderId = "f_root", documentUri = "uri_1", documentId = "doc_1",
                relativePath = "r.mp3", filename = "r.mp3", displayTitle = "Root Track", mimeType = "audio/mpeg",
                size = 100, durationMs = 100, modifiedTimeMs = now, firstIndexedAt = now, isAvailable = true,
                metadataScanStatus = MetadataScanStatus.SUCCESS, title = "Root Track", artist = null,
                albumArtist = null, album = null, discNumber = null, trackNumber = null, year = null, genre = null,
                artworkUri = null, titleSource = null, artistSource = null, albumArtistSource = null,
                albumSource = null, discNumberSource = null, trackNumberSource = null, playCount = 0, lastPlayedAt = null, likeScore = 0
            )
        )
        db.mediaFileDao().insertMediaFile(
            MediaFileEntity(
                id = "m_2", sourceId = "src_1", folderId = "f_sub1", documentUri = "uri_2", documentId = "doc_2",
                relativePath = "s1.mp3", filename = "s1.mp3", displayTitle = "Sub1 Track", mimeType = "audio/mpeg",
                size = 100, durationMs = 100, modifiedTimeMs = now, firstIndexedAt = now, isAvailable = true,
                metadataScanStatus = MetadataScanStatus.SUCCESS, title = "Sub1 Track", artist = null,
                albumArtist = null, album = null, discNumber = null, trackNumber = null, year = null, genre = null,
                artworkUri = null, titleSource = null, artistSource = null, albumArtistSource = null,
                albumSource = null, discNumberSource = null, trackNumberSource = null, playCount = 0, lastPlayedAt = null, likeScore = 0
            )
        )
        db.mediaFileDao().insertMediaFile(
            MediaFileEntity(
                id = "m_3", sourceId = "src_1", folderId = "f_sub2", documentUri = "uri_3", documentId = "doc_3",
                relativePath = "s2.mp3", filename = "s2.mp3", displayTitle = "Sub2 Track", mimeType = "audio/mpeg",
                size = 100, durationMs = 100, modifiedTimeMs = now, firstIndexedAt = now, isAvailable = false,
                metadataScanStatus = MetadataScanStatus.SUCCESS, title = "Sub2 Track", artist = null,
                albumArtist = null, album = null, discNumber = null, trackNumber = null, year = null, genre = null,
                artworkUri = null, titleSource = null, artistSource = null, albumArtistSource = null,
                albumSource = null, discNumberSource = null, trackNumberSource = null, playCount = 0, lastPlayedAt = null, likeScore = 0
            )
        )

        val resolvedAll = db.folderDao().resolveSelectionMediaIds(
            fileIds = emptyList(),
            folderIds = listOf("f_sub1"),
            availabilityFilter = "ALL"
        )
        assertEquals(listOf("m_2", "m_3"), resolvedAll)

        val resolvedAvailableOnly = db.folderDao().resolveSelectionMediaIds(
            fileIds = emptyList(),
            folderIds = listOf("f_sub1"),
            availabilityFilter = "AVAILABLE_ONLY"
        )
        assertEquals(listOf("m_2"), resolvedAvailableOnly)
    }
}
