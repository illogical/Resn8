package com.app.resn8.fixtures

import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.FolderNodeEntity
import com.app.resn8.data.database.entity.MediaFileEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MetadataValueSource

object LargeLibraryFixture {

    suspend fun seedLargeLibrary(
        db: Resn8Database,
        collectionId: String = "MUSIC",
        sourceId: String = "source_large_1",
        itemCount: Int = 25000
    ) {
        val now = System.currentTimeMillis()
        val collection = CollectionEntity(
            id = collectionId,
            name = "My Music",
            profile = CollectionProfile.MUSIC,
            createdAt = now,
            updatedAt = now
        )
        val source = RootSourceEntity(
            id = sourceId,
            collectionId = collectionId,
            treeUri = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
            displayName = "Music",
            isAvailable = true,
            lastScanStatus = "COMPLETED",
            lastScannedAt = now,
            lastScanStartedAt = now,
            lastScanCompletedAt = now,
            lastScanSummary = null
        )

        val folderDao = db.folderDao()
        val mediaDao = db.mediaFileDao()
        val collectionDao = db.collectionDao()

        collectionDao.insertCollection(collection)
        collectionDao.insertRootSource(source)

        val rootFolder = FolderNodeEntity(
            id = "folder_root",
            sourceId = sourceId,
            parentId = null,
            relativePath = "",
            displayName = "Music"
        )
        folderDao.insertFolderNode(rootFolder)

        val folders = mutableListOf<FolderNodeEntity>()
        for (f in 1..50) {
            folders.add(
                FolderNodeEntity(
                    id = "folder_$f",
                    sourceId = sourceId,
                    parentId = "folder_root",
                    relativePath = "Folder_$f",
                    displayName = "Folder $f"
                )
            )
        }
        folders.forEach { folderDao.insertFolderNode(it) }

        val batchSize = 1000
        val mediaBatch = mutableListOf<MediaFileEntity>()

        for (i in 1..itemCount) {
            val artistName = if (i % 25 == 0) null else "Artist_${(i % 1200) + 1}"
            val albumName = if (i % 30 == 0) null else "Album_${(i % 500) + 1}"
            val albumArtistName = if (artistName != null && i % 5 == 0) "Various Artists" else artistName
            val folderId = "folder_${(i % 50) + 1}"

            val title = when {
                i % 100 == 0 -> "Special%Track_$i"
                i % 150 == 0 -> "Escaped_Track_$i"
                else -> "Track Title $i"
            }

            val entity = MediaFileEntity(
                id = "media_$i",
                sourceId = sourceId,
                folderId = folderId,
                documentUri = "content://storage/doc_$i",
                documentId = "doc_$i",
                relativePath = "Folder_${(i % 50) + 1}/track_$i.mp3",
                filename = "track_$i.mp3",
                displayTitle = title,
                mimeType = "audio/mpeg",
                size = 3_000_000L + (i * 100),
                durationMs = 180_000L + (i % 60) * 1000L,
                modifiedTimeMs = now - (i * 1000L),
                firstIndexedAt = now - (i * 500L),
                isAvailable = i % 20 != 0,
                metadataScanStatus = MetadataScanStatus.SUCCESS,
                title = title,
                artist = artistName,
                albumArtist = albumArtistName,
                album = albumName,
                discNumber = if (i % 20 == 0) null else (i % 2) + 1,
                trackNumber = if (i % 10 == 0) null else (i % 15) + 1,
                year = if (i % 8 == 0) null else 2000 + (i % 24),
                genre = if (i % 12 == 0) null else "Genre_${i % 10}",
                artworkUri = if (i % 4 == 0) "content://artwork/album_${(i % 500) + 1}" else null,
                titleSource = MetadataValueSource.TAG,
                artistSource = MetadataValueSource.TAG,
                albumArtistSource = MetadataValueSource.TAG,
                albumSource = MetadataValueSource.TAG,
                discNumberSource = MetadataValueSource.TAG,
                trackNumberSource = MetadataValueSource.TAG,
                playCount = if (i % 3 == 0) 0 else (i % 50) + 1,
                lastPlayedAt = if (i % 3 == 0) null else now - (i * 200L),
                likeScore = when {
                    i % 10 == 0 -> -1
                    i % 5 == 0 -> 1
                    else -> 0
                }
            )

            mediaBatch.add(entity)

            if (mediaBatch.size >= batchSize) {
                mediaDao.insertMediaFiles(mediaBatch)
                mediaBatch.clear()
            }
        }

        if (mediaBatch.isNotEmpty()) {
            mediaDao.insertMediaFiles(mediaBatch)
            mediaBatch.clear()
        }
    }
}
