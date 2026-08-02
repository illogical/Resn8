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
        UiSessionStateEntity::class
    ],
    version = 1,
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

        fun buildDatabase(context: Context): Resn8Database {
            return Room.databaseBuilder(
                context.applicationContext,
                Resn8Database::class.java,
                DB_NAME
            ).build()
        }

        fun buildInMemoryDatabase(context: Context): Resn8Database {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                Resn8Database::class.java
            ).allowMainThreadQueries()
                .build()
        }
    }
}
