package com.app.resn8.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.app.resn8.data.database.dao.CollectionDao
import com.app.resn8.data.database.dao.FolderDao
import com.app.resn8.data.database.dao.MediaFileDao
import com.app.resn8.data.database.dao.PlaybackHistoryDao
import com.app.resn8.data.database.dao.PlaylistDao
import com.app.resn8.data.database.dao.SavedQueueDao
import com.app.resn8.data.database.dao.ScanDao
import com.app.resn8.data.database.dao.UiSessionDao
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
import com.app.resn8.data.database.entity.ScanRunEntity
import com.app.resn8.data.database.entity.StagedFolderEntity
import com.app.resn8.data.database.entity.StagedMediaEntity
import com.app.resn8.data.database.entity.UiSessionStateEntity

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CollectionEntity::class,
        RootSourceEntity::class,
        FolderNodeEntity::class,
        ScanRunEntity::class,
        StagedFolderEntity::class,
        StagedMediaEntity::class,
        MediaFileEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        PlaybackHistoryEntity::class,
        SavedQueueEntity::class,
        SavedQueueItemEntity::class,
        UiSessionStateEntity::class,
        CollectionPlaybackStateEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class Resn8Database : RoomDatabase() {
    abstract fun collectionDao(): CollectionDao
    abstract fun folderDao(): FolderDao
    abstract fun scanDao(): ScanDao
    abstract fun mediaFileDao(): MediaFileDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playbackHistoryDao(): PlaybackHistoryDao
    abstract fun savedQueueDao(): SavedQueueDao
    abstract fun uiSessionDao(): UiSessionDao

    companion object {
        private const val DB_NAME = "resn8_database.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ui_session_state ADD COLUMN selectedSourceId TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE ui_session_state ADD COLUMN selectedArtistKey TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE ui_session_state ADD COLUMN selectedAlbumKey TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE ui_session_state ADD COLUMN activeSurface TEXT NOT NULL DEFAULT 'ARTISTS'")
                db.execSQL("ALTER TABLE ui_session_state ADD COLUMN libraryFilterSnapshot TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE ui_session_state ADD COLUMN selectedAlbumArtist TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE ui_session_state ADD COLUMN selectedAlbumArtistKey TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE collections ADD COLUMN normalizedName TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE collections SET normalizedName = lower(trim(name))")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_collections_normalizedName ON collections(normalizedName)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS collection_playback_state (
                        collectionId TEXT NOT NULL,
                        activeQueueId TEXT,
                        updatedAt INTEGER NOT NULL,
                        PRIMARY KEY(collectionId),
                        FOREIGN KEY(collectionId) REFERENCES collections(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(activeQueueId) REFERENCES saved_queues(id) ON UPDATE NO ACTION ON DELETE SET NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_collection_playback_state_activeQueueId ON collection_playback_state(activeQueueId)")
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO collection_playback_state(collectionId, activeQueueId, updatedAt)
                    SELECT ui.selectedCollectionId, ui.activeQueueId, queue.updatedAt
                    FROM ui_session_state ui
                    INNER JOIN saved_queues queue ON queue.id = ui.activeQueueId
                    WHERE ui.selectedCollectionId IS NOT NULL
                      AND queue.collectionId = ui.selectedCollectionId
                    """.trimIndent()
                )
            }
        }

        fun buildDatabase(context: Context): Resn8Database {
            return Room.databaseBuilder(
                context.applicationContext,
                Resn8Database::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }

        fun buildInMemoryDatabase(context: Context): Resn8Database {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                Resn8Database::class.java
            ).build()
        }
    }
}
