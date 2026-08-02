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
}
