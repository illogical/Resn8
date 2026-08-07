package com.app.resn8.data.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.CollectionPlaybackStateEntity
import com.app.resn8.data.database.entity.FolderNodeEntity
import com.app.resn8.data.database.entity.MediaFileEntity
import com.app.resn8.data.database.entity.PlaybackHistoryEntity
import com.app.resn8.data.database.entity.PlaylistEntity
import com.app.resn8.data.database.entity.PlaylistItemEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.data.database.entity.SavedQueueEntity
import com.app.resn8.data.database.entity.SavedQueueItemEntity
import com.app.resn8.data.database.entity.UiSessionStateEntity
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.SortOrder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRepositoryTest {
    private lateinit var context: Context
    private lateinit var sourceDb: Resn8Database
    private lateinit var targetDb: Resn8Database
    private lateinit var sourceRepository: RoomBackupRepository
    private lateinit var targetRepository: RoomBackupRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sourceDb = Resn8Database.buildInMemoryDatabase(context)
        targetDb = Resn8Database.buildInMemoryDatabase(context)
        sourceRepository = RoomBackupRepository(context, sourceDb)
        targetRepository = RoomBackupRepository(context, targetDb)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun fullRoundTripPreservesUserMetadataAndStableIdentities() = runBlocking {
        seedCollection(sourceDb, "collection-a", "My Music")
        val bytes = export(sourceRepository, setOf("collection-a"))
        val preview = targetRepository.inspectBackup(ByteArrayInputStream(bytes))

        assertEquals(1, preview.collections.size)
        assertTrue(preview.collections.single().conflictingCollectionIds.isEmpty())

        val result = targetRepository.importBackup(preview, setOf("collection-a"), emptySet())
        assertEquals(listOf("collection-a"), result.restoredCollectionIds)
        assertEquals(listOf("collection-a"), result.needsFolderCollectionIds)

        val media = targetDb.mediaFileDao().getMediaFileById("media-collection-a")
        assertNotNull(media)
        assertEquals(12, media?.playCount)
        assertEquals(3, media?.likeScore)
        assertEquals(1234L, media?.firstIndexedAt)
        assertFalse(media?.isAvailable ?: true)
        assertEquals("media-collection-a", targetDb.playlistDao().getPlaylistItems("playlist-collection-a").single().mediaId)
        assertEquals("queue-item-collection-a", targetDb.savedQueueDao().getSavedQueueItems("queue-collection-a").single().queueItemId)
        assertEquals("occurrence-collection-a", targetDb.backupDao().getHistory(listOf("media-collection-a")).single().sessionOccurrenceId)
    }

    @Test
    fun exportSubsetContainsOnlySelectedCollections() = runBlocking {
        seedCollection(sourceDb, "collection-a", "My Music")
        seedCollection(sourceDb, "collection-b", "Audio Books")

        val preview = targetRepository.inspectBackup(
            ByteArrayInputStream(export(sourceRepository, setOf("collection-b")))
        )

        assertEquals(listOf("collection-b"), preview.collections.map { it.id })
        assertEquals("SELECTED_COLLECTIONS", preview.envelope.payload.scope)
        assertEquals(null, preview.envelope.payload.uiSession)
    }

    @Test
    fun conflictsSkipByDefaultAndReplaceOnlyWhenExplicit() = runBlocking {
        seedCollection(sourceDb, "collection-a", "My Music")
        seedCollection(targetDb, "local-existing", "My Music")
        targetDb.backupDao().upsertUiSession(
            UiSessionStateEntity(
                currentRoute = "now_playing",
                selectedCollectionId = "local-existing",
                selectedFolderId = "folder-local-existing",
                selectedArtist = null,
                selectedAlbum = null,
                selectedPlaylistId = "playlist-local-existing",
                activeQueueId = "queue-local-existing",
                activeSearchQuery = null,
                activeSort = SortOrder.MOST_LIKED,
                activeFilterSnapshot = null
            )
        )
        val bytes = export(sourceRepository, setOf("collection-a"))
        var preview = targetRepository.inspectBackup(ByteArrayInputStream(bytes))
        assertEquals(setOf("local-existing"), preview.collections.single().conflictingCollectionIds)

        val skipped = targetRepository.importBackup(preview, setOf("collection-a"), emptySet())
        assertEquals(1, skipped.skippedCollectionCount)
        assertNotNull(targetDb.collectionDao().getCollectionById("local-existing"))
        assertEquals(null, targetDb.collectionDao().getCollectionById("collection-a"))

        preview = targetRepository.inspectBackup(ByteArrayInputStream(bytes))
        val replaced = targetRepository.importBackup(preview, setOf("collection-a"), setOf("collection-a"))
        assertEquals(1, replaced.replacedCollectionCount)
        assertEquals(null, targetDb.collectionDao().getCollectionById("local-existing"))
        assertNotNull(targetDb.collectionDao().getCollectionById("collection-a"))
        val session = targetDb.uiSessionDao().getUiSessionState()
        assertEquals("collection-a", session?.selectedCollectionId)
        assertEquals("library", session?.currentRoute)
        assertEquals(null, session?.activeQueueId)
    }

    @Test
    fun failedReplacementRollsBackDeletedLocalCollection() = runBlocking {
        seedCollection(sourceDb, "collection-a", "My Music")
        seedCollection(targetDb, "local-existing", "My Music")
        targetDb.backupDao().insertCollections(
            listOf(CollectionEntity("unrelated", "Unrelated", CollectionProfile.MUSIC, 1, 1))
        )
        targetDb.backupDao().insertSources(
            listOf(RootSourceEntity("source-collection-a", "unrelated", "content://unrelated", "Unrelated", true, null, null, null, null, null))
        )
        val preview = targetRepository.inspectBackup(
            ByteArrayInputStream(export(sourceRepository, setOf("collection-a")))
        )

        assertThrows(Exception::class.java) {
            runBlocking {
                targetRepository.importBackup(preview, setOf("collection-a"), setOf("collection-a"))
            }
        }

        assertNotNull(targetDb.collectionDao().getCollectionById("local-existing"))
        assertEquals(null, targetDb.collectionDao().getCollectionById("collection-a"))
        assertNotNull(targetDb.collectionDao().getCollectionById("unrelated"))
    }

    @Test
    fun corruptChecksumIsRejectedAndVersionOneRemainsReadable() = runBlocking {
        seedCollection(sourceDb, "collection-a", "My Music")
        val json = export(sourceRepository, setOf("collection-a")).toString(Charsets.UTF_8)
        val corrupt = json.replace("My Music", "Changed Music")

        assertThrows(BackupValidationException::class.java) {
            runBlocking { targetRepository.inspectBackup(ByteArrayInputStream(corrupt.toByteArray())) }
        }

        val versionOne = json.replace("\"version\": 2", "\"version\": 1")
        val preview = targetRepository.inspectBackup(ByteArrayInputStream(versionOne.toByteArray()))
        assertEquals(1, preview.envelope.version)
        assertEquals("collection-a", preview.collections.single().id)
    }

    private suspend fun export(repository: BackupRepository, ids: Set<String>): ByteArray {
        val output = ByteArrayOutputStream()
        repository.exportBackup(ids, output)
        return output.toByteArray()
    }

    private suspend fun seedCollection(db: Resn8Database, collectionId: String, name: String) {
        val dao = db.backupDao()
        val sourceId = "source-$collectionId"
        val folderId = "folder-$collectionId"
        val mediaId = "media-$collectionId"
        val playlistId = "playlist-$collectionId"
        val queueId = "queue-$collectionId"
        dao.insertCollections(listOf(CollectionEntity(collectionId, name, CollectionProfile.MUSIC, 10, 20)))
        dao.insertSources(listOf(RootSourceEntity(sourceId, collectionId, "content://tree/$collectionId", name, true, "SUCCESS", 20, 10, 20, null)))
        dao.insertFolders(listOf(FolderNodeEntity(folderId, sourceId, null, "", name)))
        dao.insertMedia(
            listOf(
                MediaFileEntity(
                    id = mediaId,
                    sourceId = sourceId,
                    folderId = folderId,
                    documentUri = "content://track/$collectionId",
                    documentId = "document-$collectionId",
                    relativePath = "Artist/Album/Track.mp3",
                    filename = "Track.mp3",
                    displayTitle = "Track",
                    mimeType = "audio/mpeg",
                    size = 2048,
                    durationMs = 180_000,
                    modifiedTimeMs = 99,
                    firstIndexedAt = 1234,
                    isAvailable = true,
                    metadataScanStatus = MetadataScanStatus.SUCCESS,
                    title = "Track",
                    artist = "Artist",
                    albumArtist = "Artist",
                    album = "Album",
                    discNumber = 1,
                    trackNumber = 2,
                    year = 2026,
                    genre = "Rock",
                    artworkUri = "content://art/$collectionId",
                    titleSource = null,
                    artistSource = null,
                    albumArtistSource = null,
                    albumSource = null,
                    discNumberSource = null,
                    trackNumberSource = null,
                    playCount = 12,
                    lastPlayedAt = 5678,
                    likeScore = 3
                )
            )
        )
        dao.insertHistory(listOf(PlaybackHistoryEntity("history-$collectionId", mediaId, "occurrence-$collectionId", 100, 200, 100, PlaybackHistoryResult.THRESHOLD_COUNTED, 200)))
        dao.insertPlaylists(listOf(PlaylistEntity(playlistId, collectionId, "Favorites", "favorites", 30, 40)))
        dao.insertPlaylistItems(listOf(PlaylistItemEntity(playlistId, mediaId, 1024, 30)))
        dao.insertQueues(listOf(SavedQueueEntity(queueId, collectionId, SavedQueueKind.EXPLICIT, null, null, null, 0, mediaId, "occurrence-$collectionId", 42, false, 1f, RepeatMode.OFF, 50, 60)))
        dao.insertQueueItems(listOf(SavedQueueItemEntity(queueId, 0, "queue-item-$collectionId", mediaId)))
        dao.insertCollectionPlaybackStates(listOf(CollectionPlaybackStateEntity(collectionId, queueId, 60)))
    }
}
