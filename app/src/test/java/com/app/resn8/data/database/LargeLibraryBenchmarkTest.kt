package com.app.resn8.data.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.app.resn8.fixtures.LargeLibraryFixture
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.StagedMedia
import org.junit.Assert.assertEquals
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LargeLibraryBenchmarkTest {

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
    fun benchmark25kLibraryQueriesAndExplainPlan() = runBlocking {
        println("Seeding 25,000 media rows into SQLite in-memory database...")
        val seedStart = System.currentTimeMillis()
        LargeLibraryFixture.seedLargeLibrary(db, itemCount = 25000)
        val seedEnd = System.currentTimeMillis()
        println("Seeding completed in ${seedEnd - seedStart} ms.")

        val queryStart = System.currentTimeMillis()
        val visibleIds = db.mediaFileDao().snapshotVisibleMediaIds(
            collectionId = "MUSIC",
            sourceId = null,
            folderId = null,
            isArtistFilterNull = 1,
            artistKeyIsUnknown = 0,
            artistKeyValue = null,
            isAlbumFilterNull = 1,
            albumKeyIsUnknown = 0,
            albumKeyValue = null,
            availabilityFilter = "AVAILABLE_ONLY",
            excludeDisliked = 1,
            searchPattern = null,
            sortOrder = "ARTIST"
        )
        val queryEnd = System.currentTimeMillis()
        val elapsedMs = queryEnd - queryStart
        println("Visible media snapshot query executed in $elapsedMs ms (returned ${visibleIds.size} rows).")

        assertTrue("Expected matching rows from 25,000 library", visibleIds.isNotEmpty())

        val sqliteDb = db.openHelper.writableDatabase
        val cursor = sqliteDb.query(
            "EXPLAIN QUERY PLAN SELECT mf.id FROM media_files mf INNER JOIN root_sources rs ON mf.sourceId = rs.id WHERE rs.collectionId = 'MUSIC' AND mf.isAvailable = 1"
        )
        val planLines = mutableListOf<String>()
        while (cursor.moveToNext()) {
            val detailIndex = cursor.getColumnIndex("detail")
            if (detailIndex >= 0) {
                planLines.add(cursor.getString(detailIndex))
            }
        }
        cursor.close()

        println("EXPLAIN QUERY PLAN Output:")
        planLines.forEach { println(" - $it") }
        assertTrue("Expected non-empty query plan", planLines.isNotEmpty())
    }

    @Test
    fun benchmark25kStagedPublicationUsesBoundedInputBatches() = runBlocking {
        val collections = RoomCollectionRepository(db)
        val media = RoomMediaRepository(db)
        val collection = collections.createCollection("Publication benchmark")
        val source = collections.addRootSource(collection.id, "content://benchmark/music", "Music")
        val scanId = media.startScanRun(source.id)

        (0 until 25_000).chunked(250).forEach { indexes ->
            media.stageMedia(
                scanId,
                indexes.map { index ->
                    StagedMedia(
                        id = "staged-$index",
                        scanId = scanId,
                        documentUri = "content://benchmark/song-$index",
                        documentId = "song-$index",
                        relativePath = "song-$index.mp3",
                        filename = "song-$index.mp3",
                        displayTitle = "Song $index",
                        mimeType = "audio/mpeg",
                        size = 1_000L + index,
                        durationMs = 180_000L,
                        modifiedTimeMs = 10_000L + index
                    )
                }
            )
        }

        val startedAt = System.currentTimeMillis()
        val result = media.publishStagedScan(
            scanId,
            source.id,
            ScanResult(25_000, 0, 0, 0, 0, 0, 0, 0, 0)
        )
        val elapsedMs = System.currentTimeMillis() - startedAt
        println("Published 25,000 staged rows in $elapsedMs ms")

        assertEquals(25_000, result.scannedCount)
        assertEquals(25_000, result.addedCount)
    }
}
