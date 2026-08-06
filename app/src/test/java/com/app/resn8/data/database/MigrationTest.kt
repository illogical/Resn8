package com.app.resn8.data.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    private lateinit var db: SupportSQLiteDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test-migration.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `ui_session_state` (
                            `id` INTEGER NOT NULL PRIMARY KEY,
                            `currentRoute` TEXT NOT NULL,
                            `selectedCollectionId` TEXT,
                            `selectedFolderId` TEXT,
                            `selectedArtist` TEXT,
                            `selectedAlbum` TEXT,
                            `selectedPlaylistId` TEXT,
                            `activeQueueId` TEXT,
                            `activeSearchQuery` TEXT,
                            `activeSort` TEXT NOT NULL,
                            `activeFilterSnapshot` TEXT
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()
        val factory = FrameworkSQLiteOpenHelperFactory()
        db = factory.create(config).writableDatabase
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun migrate1To2PreservesDataAndAddsColumns() {
        db.execSQL(
            """
            INSERT INTO ui_session_state (id, currentRoute, selectedCollectionId, selectedFolderId, selectedArtist, selectedAlbum, selectedPlaylistId, activeQueueId, activeSearchQuery, activeSort, activeFilterSnapshot)
            VALUES (1, 'library', 'col_1', NULL, 'Artist 1', 'Album 1', NULL, NULL, NULL, 'ARTIST', NULL)
            """.trimIndent()
        )

        Resn8Database.MIGRATION_1_2.migrate(db)

        val cursor = db.query("SELECT * FROM ui_session_state WHERE id = 1")
        cursor.moveToFirst()
        val routeIndex = cursor.getColumnIndex("currentRoute")
        val surfaceIndex = cursor.getColumnIndex("activeSurface")
        val route = cursor.getString(routeIndex)
        val surface = cursor.getString(surfaceIndex)
        cursor.close()

        assertEquals("library", route)
        assertEquals("ARTISTS", surface)
    }

    @Test
    fun migrate3To4BackfillsNormalizedCollectionNamesAndAddsUniqueIndex() {
        db.execSQL(
            """
            CREATE TABLE collections (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                profile TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO collections VALUES ('col_1', '  My Audio  ', 'MUSIC', 1, 1)")

        Resn8Database.MIGRATION_3_4.migrate(db)

        val cursor = db.query("SELECT normalizedName FROM collections WHERE id = 'col_1'")
        cursor.moveToFirst()
        assertEquals("my audio", cursor.getString(0))
        cursor.close()

        val indexes = db.query("PRAGMA index_list('collections')")
        var foundUnique = false
        while (indexes.moveToNext()) {
            if (indexes.getString(indexes.getColumnIndexOrThrow("name")) == "index_collections_normalizedName") {
                foundUnique = indexes.getInt(indexes.getColumnIndexOrThrow("unique")) == 1
            }
        }
        indexes.close()
        assertEquals(true, foundUnique)
    }

    @Test
    fun migrate4To5SeedsOnlyTheSelectedCollectionsMatchingQueue() {
        db.execSQL(
            """
            CREATE TABLE collections (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                profile TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL,
                normalizedName TEXT NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE saved_queues (
                id TEXT NOT NULL PRIMARY KEY,
                collectionId TEXT NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("INSERT INTO collections VALUES ('col_1', 'Music', 'MUSIC', 1, 1, 'music')")
        db.execSQL("INSERT INTO saved_queues VALUES ('queue_1', 'col_1', 42)")
        db.execSQL("INSERT INTO ui_session_state (id, currentRoute, selectedCollectionId, selectedFolderId, selectedArtist, selectedAlbum, selectedPlaylistId, activeQueueId, activeSearchQuery, activeSort, activeFilterSnapshot) VALUES (1, 'now_playing', 'col_1', NULL, NULL, NULL, NULL, 'queue_1', NULL, 'ARTIST', NULL)")

        Resn8Database.MIGRATION_4_5.migrate(db)

        val cursor = db.query("SELECT collectionId, activeQueueId, updatedAt FROM collection_playback_state")
        cursor.moveToFirst()
        assertEquals("col_1", cursor.getString(0))
        assertEquals("queue_1", cursor.getString(1))
        assertEquals(42L, cursor.getLong(2))
        cursor.close()
    }

    @Test
    fun migrate5To6AddsSortPreferencesAndClearsHiddenLibraryFilters() {
        val columns = db.query("PRAGMA table_info('ui_session_state')")
        var hasLibraryFilters = false
        while (columns.moveToNext()) {
            if (columns.getString(columns.getColumnIndexOrThrow("name")) == "libraryFilterSnapshot") {
                hasLibraryFilters = true
            }
        }
        columns.close()
        if (!hasLibraryFilters) {
            db.execSQL("ALTER TABLE ui_session_state ADD COLUMN libraryFilterSnapshot TEXT DEFAULT NULL")
        }
        db.execSQL(
            """
            INSERT OR REPLACE INTO ui_session_state (
                id, currentRoute, selectedCollectionId, selectedFolderId, selectedArtist, selectedAlbum,
                selectedPlaylistId, activeQueueId, activeSearchQuery, activeSort, activeFilterSnapshot,
                libraryFilterSnapshot
            ) VALUES (
                1, 'library', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'LEAST_PLAYED', NULL,
                '{"availability":"UNAVAILABLE_ONLY","excludeDisliked":true}'
            )
            """.trimIndent()
        )

        Resn8Database.MIGRATION_5_6.migrate(db)

        val cursor = db.query(
            "SELECT activeSort, librarySortPreferences, libraryFilterSnapshot FROM ui_session_state WHERE id = 1"
        )
        cursor.moveToFirst()
        assertEquals("LEAST_PLAYED", cursor.getString(0))
        assertEquals(null, cursor.getString(1))
        assertEquals(null, cursor.getString(2))
        cursor.close()
    }
}
