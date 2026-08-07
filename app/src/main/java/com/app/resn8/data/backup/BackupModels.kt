package com.app.resn8.data.backup

import kotlinx.serialization.Serializable

const val RESN8_BACKUP_FORMAT = "com.app.resn8.metadata-backup"
const val RESN8_BACKUP_VERSION = 2

@Serializable
data class BackupEnvelope(
    val format: String,
    val version: Int,
    val createdAt: Long,
    val appVersion: String,
    val payloadSha256: String,
    val payload: BackupPayload
)

@Serializable
data class BackupPayload(
    val scope: String = "SELECTED_COLLECTIONS",
    val collections: List<BackupCollectionBundle>,
    val uiSession: BackupUiSession? = null
)

@Serializable
data class BackupCollectionBundle(
    val collection: BackupCollection,
    val sources: List<BackupSource>,
    val folders: List<BackupFolder>,
    val media: List<BackupMedia>,
    val history: List<BackupHistory>,
    val playlists: List<BackupPlaylist>,
    val playlistItems: List<BackupPlaylistItem>,
    val queues: List<BackupQueue>,
    val queueItems: List<BackupQueueItem>,
    val playbackState: BackupCollectionPlaybackState? = null
)

@Serializable
data class BackupCollection(
    val id: String,
    val name: String,
    val normalizedName: String,
    val profile: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupSource(
    val id: String,
    val collectionId: String,
    val treeUriHint: String,
    val displayName: String,
    val lastScanStatus: String? = null,
    val lastScannedAt: Long? = null,
    val lastScanStartedAt: Long? = null,
    val lastScanCompletedAt: Long? = null,
    val lastScanSummaryJson: String? = null
)

@Serializable
data class BackupFolder(
    val id: String,
    val sourceId: String,
    val parentId: String? = null,
    val relativePath: String,
    val displayName: String
)

@Serializable
data class BackupMedia(
    val id: String,
    val sourceId: String,
    val folderId: String,
    val documentUriHint: String,
    val documentIdHint: String? = null,
    val relativePath: String,
    val filename: String,
    val displayTitle: String,
    val mimeType: String,
    val size: Long,
    val durationMs: Long? = null,
    val modifiedTimeMs: Long,
    val firstIndexedAt: Long,
    val metadataScanStatus: String,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val titleSource: String? = null,
    val artistSource: String? = null,
    val albumArtistSource: String? = null,
    val albumSource: String? = null,
    val discNumberSource: String? = null,
    val trackNumberSource: String? = null,
    val playCount: Int,
    val lastPlayedAt: Long? = null,
    val likeScore: Int
)

@Serializable
data class BackupHistory(
    val id: String,
    val mediaId: String,
    val sessionOccurrenceId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val accumulatedListenedDurationMs: Long,
    val result: String,
    val countedAt: Long? = null
)

@Serializable
data class BackupPlaylist(
    val id: String,
    val collectionId: String,
    val name: String,
    val normalizedName: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupPlaylistItem(
    val playlistId: String,
    val mediaId: String,
    val position: Long,
    val addedAt: Long
)

@Serializable
data class BackupQueue(
    val id: String,
    val collectionId: String,
    val kind: String,
    val mode: String? = null,
    val filterSnapshotJson: String? = null,
    val seed: Long? = null,
    val currentIndex: Int,
    val currentMediaId: String? = null,
    val currentOccurrenceId: String? = null,
    val positionMs: Long,
    val playWhenReadyIntent: Boolean,
    val playbackSpeed: Float,
    val repeatMode: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupQueueItem(
    val queueId: String,
    val itemIndex: Int,
    val queueItemId: String,
    val mediaId: String
)

@Serializable
data class BackupCollectionPlaybackState(
    val collectionId: String,
    val activeQueueId: String? = null,
    val updatedAt: Long
)

@Serializable
data class BackupUiSession(
    val currentRoute: String,
    val selectedCollectionId: String? = null,
    val selectedSourceId: String? = null,
    val selectedFolderId: String? = null,
    val selectedArtist: String? = null,
    val selectedAlbum: String? = null,
    val selectedAlbumArtist: String? = null,
    val selectedArtistKey: String? = null,
    val selectedAlbumKey: String? = null,
    val selectedAlbumArtistKey: String? = null,
    val selectedPlaylistId: String? = null,
    val activeQueueId: String? = null,
    val activeSearchQuery: String? = null,
    val activeSort: String,
    val activeSurface: String,
    val librarySortPreferencesJson: String? = null,
    val libraryFilterSnapshotJson: String? = null,
    val activeFilterSnapshotJson: String? = null
)

data class BackupCollectionPreview(
    val id: String,
    val name: String,
    val profile: String,
    val mediaCount: Int,
    val playlistCount: Int,
    val historyCount: Int,
    val conflictingCollectionIds: Set<String> = emptySet()
)

data class ValidatedBackup(
    val envelope: BackupEnvelope,
    val collections: List<BackupCollectionPreview>
)

data class BackupExportResult(
    val collectionCount: Int,
    val mediaCount: Int,
    val playlistCount: Int,
    val historyCount: Int
)

data class BackupImportResult(
    val restoredCollectionIds: List<String>,
    val replacedCollectionCount: Int,
    val skippedCollectionCount: Int,
    val unresolvedMediaCount: Int,
    val needsFolderCollectionIds: List<String>,
    val reindexSourceIds: List<String>
)

class BackupValidationException(message: String) : IllegalArgumentException(message)
