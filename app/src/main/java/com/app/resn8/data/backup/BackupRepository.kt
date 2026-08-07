package com.app.resn8.data.backup

import android.content.Context
import androidx.room.withTransaction
import com.app.resn8.data.database.Converters
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
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MetadataValueSource
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.SmartQueueMode
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.normalizeCollectionName
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

interface BackupRepository {
    suspend fun exportBackup(collectionIds: Set<String>, output: OutputStream): BackupExportResult
    suspend fun inspectBackup(input: InputStream): ValidatedBackup
    suspend fun importBackup(
        backup: ValidatedBackup,
        selectedCollectionIds: Set<String>,
        replaceImportedCollectionIds: Set<String>
    ): BackupImportResult
}

class RoomBackupRepository(
    private val context: Context,
    private val db: Resn8Database
) : BackupRepository {
    private val dao = db.backupDao()
    private val converters = Converters()
    private val canonicalJson = Json { encodeDefaults = true; explicitNulls = false }
    private val fileJson = Json { encodeDefaults = true; explicitNulls = false; prettyPrint = true }

    override suspend fun exportBackup(
        collectionIds: Set<String>,
        output: OutputStream
    ): BackupExportResult {
        require(collectionIds.isNotEmpty()) { "Select at least one collection" }
        val payload = db.withTransaction {
            val allCollections = dao.getAllCollections()
            val selected = dao.getCollections(collectionIds.sorted())
            require(selected.size == collectionIds.size) { "One or more selected collections no longer exist" }
            val selectedIds = selected.map { it.id }
            val sources = dao.getSources(selectedIds)
            val sourceIds = sources.map { it.id }
            val folders = if (sourceIds.isEmpty()) emptyList() else dao.getFolders(sourceIds)
            val media = if (sourceIds.isEmpty()) emptyList() else dao.getMedia(sourceIds)
            val mediaIds = media.map { it.id }
            val history = if (mediaIds.isEmpty()) emptyList() else dao.getHistory(mediaIds)
            val playlists = dao.getPlaylists(selectedIds)
            val playlistIds = playlists.map { it.id }
            val playlistItems = if (playlistIds.isEmpty()) emptyList() else dao.getPlaylistItems(playlistIds)
            val queues = dao.getQueues(selectedIds)
            val queueIds = queues.map { it.id }
            val queueItems = if (queueIds.isEmpty()) emptyList() else dao.getQueueItems(queueIds)
            val playbackStates = dao.getCollectionPlaybackStates(selectedIds).associateBy { it.collectionId }

            val bundles = selected.map { collection ->
                val collectionSources = sources.filter { it.collectionId == collection.id }
                val collectionSourceIds = collectionSources.mapTo(mutableSetOf()) { it.id }
                val collectionMedia = media.filter { it.sourceId in collectionSourceIds }
                val collectionMediaIds = collectionMedia.mapTo(mutableSetOf()) { it.id }
                val collectionPlaylists = playlists.filter { it.collectionId == collection.id }
                val collectionPlaylistIds = collectionPlaylists.mapTo(mutableSetOf()) { it.id }
                val collectionQueues = queues.filter { it.collectionId == collection.id }
                val collectionQueueIds = collectionQueues.mapTo(mutableSetOf()) { it.id }
                BackupCollectionBundle(
                    collection = collection.toBackup(),
                    sources = collectionSources.map { it.toBackup() },
                    folders = folders.filter { it.sourceId in collectionSourceIds }.map { it.toBackup() },
                    media = collectionMedia.map { it.toBackup() },
                    history = history.filter { it.mediaId in collectionMediaIds }.map { it.toBackup() },
                    playlists = collectionPlaylists.map { it.toBackup() },
                    playlistItems = playlistItems.filter { it.playlistId in collectionPlaylistIds }.map { it.toBackup() },
                    queues = collectionQueues.map { it.toBackup() },
                    queueItems = queueItems.filter { it.queueId in collectionQueueIds }.map { it.toBackup() },
                    playbackState = playbackStates[collection.id]?.toBackup()
                )
            }
            val includesEverything = selectedIds.toSet() == allCollections.mapTo(mutableSetOf()) { it.id }
            BackupPayload(
                scope = if (includesEverything) "ALL_COLLECTIONS" else "SELECTED_COLLECTIONS",
                collections = bundles,
                uiSession = if (includesEverything) dao.getUiSession()?.toBackup() else null
            )
        }
        val envelope = BackupEnvelope(
            format = RESN8_BACKUP_FORMAT,
            version = RESN8_BACKUP_VERSION,
            createdAt = System.currentTimeMillis(),
            appVersion = context.packageManager
                .getPackageInfo(context.packageName, 0)
                .versionName ?: "unknown",
            payloadSha256 = checksum(payload),
            payload = payload
        )
        output.bufferedWriter(Charsets.UTF_8).use { it.write(fileJson.encodeToString(envelope)) }
        return BackupExportResult(
            collectionCount = payload.collections.size,
            mediaCount = payload.collections.sumOf { it.media.size },
            playlistCount = payload.collections.sumOf { it.playlists.size },
            historyCount = payload.collections.sumOf { it.history.size }
        )
    }

    override suspend fun inspectBackup(input: InputStream): ValidatedBackup {
        val text = input.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val envelope = try {
            fileJson.decodeFromString<BackupEnvelope>(text)
        } catch (_: Exception) {
            throw BackupValidationException("This file is not valid Resn8 backup JSON.")
        }
        if (envelope.format != RESN8_BACKUP_FORMAT) {
            throw BackupValidationException("This JSON file is not a Resn8 metadata backup.")
        }
        if (envelope.version !in 1..RESN8_BACKUP_VERSION) {
            throw BackupValidationException(
                if (envelope.version > RESN8_BACKUP_VERSION) {
                    "This backup was created by a newer version of Resn8."
                } else {
                    "This backup version is no longer supported."
                }
            )
        }
        if (!checksum(envelope.payload).equals(envelope.payloadSha256, ignoreCase = true)) {
            throw BackupValidationException("The backup integrity check failed. The file may be incomplete or changed.")
        }
        validatePayload(envelope.payload)

        val existing = dao.getAllCollections()
        val previews = envelope.payload.collections.map { bundle ->
            val imported = bundle.collection
            val conflicts = existing.filter {
                it.id == imported.id || it.normalizedName == imported.normalizedName
            }.mapTo(linkedSetOf()) { it.id }
            BackupCollectionPreview(
                id = imported.id,
                name = imported.name,
                profile = imported.profile,
                mediaCount = bundle.media.size,
                playlistCount = bundle.playlists.size,
                historyCount = bundle.history.size,
                conflictingCollectionIds = conflicts
            )
        }
        return ValidatedBackup(envelope, previews)
    }

    override suspend fun importBackup(
        backup: ValidatedBackup,
        selectedCollectionIds: Set<String>,
        replaceImportedCollectionIds: Set<String>
    ): BackupImportResult {
        require(selectedCollectionIds.isNotEmpty()) { "Select at least one collection" }
        val payloadById = backup.envelope.payload.collections.associateBy { it.collection.id }
        require(selectedCollectionIds.all { it in payloadById }) { "The import selection is invalid" }
        val previewById = backup.collections.associateBy { it.id }
        val restorableIds = selectedCollectionIds.filterTo(linkedSetOf()) { id ->
            previewById.getValue(id).conflictingCollectionIds.isEmpty() || id in replaceImportedCollectionIds
        }
        val replacementTargets = restorableIds.flatMapTo(linkedSetOf()) { id ->
            if (id in replaceImportedCollectionIds) previewById.getValue(id).conflictingCollectionIds else emptySet()
        }
        val permissionUris = context.contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .mapTo(hashSetOf()) { it.uri.toString() }
        val needsFolders = mutableListOf<String>()
        val reindexSources = mutableListOf<String>()

        db.withTransaction {
            val existingSession = dao.getUiSession()
            replacementTargets.forEach(::deleteCollectionGraph)
            restorableIds.forEach { importedId ->
                val bundle = payloadById.getValue(importedId)
                val permissionBySource = bundle.sources.associate { source ->
                    source.id to (source.treeUriHint in permissionUris)
                }
                if (permissionBySource.values.any { it }) {
                    reindexSources += permissionBySource.filterValues { it }.keys
                }
                if (permissionBySource.values.none { it }) needsFolders += importedId
                insertBundle(bundle, permissionBySource)
            }

            val restoreGlobalSession = backup.envelope.payload.uiSession != null &&
                restorableIds.size == backup.envelope.payload.collections.size
            if (restoreGlobalSession) {
                dao.upsertUiSession(backup.envelope.payload.uiSession!!.toEntity())
            } else if (existingSession?.selectedCollectionId in replacementTargets) {
                val fallback = restorableIds.firstOrNull()?.let(payloadById::getValue)
                if (fallback != null) dao.upsertUiSession(existingSession!!.safeHomeFor(fallback))
            }
        }

        return BackupImportResult(
            restoredCollectionIds = restorableIds.toList(),
            replacedCollectionCount = replacementTargets.size,
            skippedCollectionCount = selectedCollectionIds.size - restorableIds.size,
            unresolvedMediaCount = restorableIds.sumOf { payloadById.getValue(it).media.size },
            needsFolderCollectionIds = needsFolders,
            reindexSourceIds = reindexSources
        )
    }

    private fun checksum(payload: BackupPayload): String = MessageDigest.getInstance("SHA-256")
        .digest(canonicalJson.encodeToString(payload).toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun validatePayload(payload: BackupPayload) {
        if (payload.collections.isEmpty()) throw BackupValidationException("This backup contains no collections.")
        if (payload.scope !in setOf("ALL_COLLECTIONS", "SELECTED_COLLECTIONS")) invalid("The backup scope is invalid.")
        val collectionIds = mutableSetOf<String>()
        val collectionNames = mutableSetOf<String>()
        val allOccurrences = mutableSetOf<String>()
        val allSourceIds = mutableSetOf<String>()
        val allFolderIds = mutableSetOf<String>()
        val allPlaylistIds = mutableSetOf<String>()
        val allQueueIds = mutableSetOf<String>()
        payload.collections.forEach { bundle ->
            val collection = bundle.collection
            if (collection.id.isBlank() || collection.name.isBlank()) invalid("A collection has a missing ID or name.")
            if (collection.normalizedName != normalizeCollectionName(collection.name)) invalid("Collection name data is inconsistent.")
            if (!collectionIds.add(collection.id) || !collectionNames.add(collection.normalizedName)) invalid("The backup contains duplicate collections.")
            enumValue<CollectionProfile>(collection.profile, "collection profile")
            if (bundle.sources.any { it.collectionId != collection.id }) invalid("A source belongs to the wrong collection.")
            if (bundle.sources.map { it.id }.toSet().size != bundle.sources.size) invalid("A source ID is duplicated.")
            val sourceIds = bundle.sources.mapTo(hashSetOf()) { it.id }
            allSourceIds += sourceIds
            val folderIds = bundle.folders.mapTo(hashSetOf()) { it.id }
            allFolderIds += folderIds
            if (folderIds.size != bundle.folders.size || bundle.folders.any { it.sourceId !in sourceIds || it.parentId != null && it.parentId !in folderIds }) {
                invalid("Folder relationships are inconsistent.")
            }
            val mediaIds = bundle.media.mapTo(hashSetOf()) { it.id }
            if (mediaIds.size != bundle.media.size) invalid("A media ID is duplicated.")
            bundle.media.forEach { media ->
                if (media.sourceId !in sourceIds || media.folderId !in folderIds) invalid("A media item has an invalid source or folder.")
                if (media.playCount < 0 || media.likeScore < -1 || media.size < 0 || (media.durationMs ?: 0) < 0) invalid("A media item has invalid statistics.")
                enumValue<MetadataScanStatus>(media.metadataScanStatus, "metadata status")
                listOf(media.titleSource, media.artistSource, media.albumArtistSource, media.albumSource, media.discNumberSource, media.trackNumberSource)
                    .filterNotNull().forEach { enumValue<MetadataValueSource>(it, "metadata provenance") }
            }
            if (bundle.history.map { it.id }.toSet().size != bundle.history.size) invalid("A history ID is duplicated.")
            bundle.history.forEach { history ->
                if (history.mediaId !in mediaIds || history.accumulatedListenedDurationMs < 0) invalid("A history record is invalid.")
                if (!allOccurrences.add(history.sessionOccurrenceId)) invalid("A playback occurrence is duplicated.")
                enumValue<PlaybackHistoryResult>(history.result, "history result")
            }
            val playlistIds = bundle.playlists.mapTo(hashSetOf()) { playlist ->
                if (playlist.collectionId != collection.id) invalid("A playlist belongs to the wrong collection.")
                playlist.id
            }
            allPlaylistIds += playlistIds
            if (playlistIds.size != bundle.playlists.size) invalid("A playlist ID is duplicated.")
            if (bundle.playlistItems.any { it.playlistId !in playlistIds || it.mediaId !in mediaIds || it.position < 0 }) invalid("Playlist membership is inconsistent.")
            bundle.playlistItems.groupBy { it.playlistId }.values.forEach { rows ->
                if (rows.map { it.mediaId }.toSet().size != rows.size || rows.map { it.position }.toSet().size != rows.size) invalid("Playlist membership is duplicated.")
            }
            val queueIds = bundle.queues.mapTo(hashSetOf()) { queue ->
                if (queue.collectionId != collection.id || queue.currentIndex < 0 || queue.positionMs < 0 || queue.playbackSpeed <= 0f) invalid("A saved queue is invalid.")
                enumValue<SavedQueueKind>(queue.kind, "queue kind")
                queue.mode?.let { enumValue<SmartQueueMode>(it, "queue mode") }
                enumValue<RepeatMode>(queue.repeatMode, "repeat mode")
                if (queue.filterSnapshotJson != null && converters.toQueueFilterSnapshot(queue.filterSnapshotJson) == null) invalid("A saved queue filter is invalid.")
                queue.id
            }
            allQueueIds += queueIds
            if (queueIds.size != bundle.queues.size) invalid("A saved queue ID is duplicated.")
            if (bundle.queueItems.any { it.queueId !in queueIds || it.mediaId !in mediaIds || it.itemIndex < 0 }) invalid("Saved queue membership is inconsistent.")
            bundle.queueItems.groupBy { it.queueId }.forEach { (queueId, rows) ->
                val sorted = rows.sortedBy { it.itemIndex }
                if (sorted.map { it.itemIndex } != sorted.indices.toList() || rows.map { it.queueItemId }.toSet().size != rows.size) invalid("Saved queue ordering is invalid.")
                val queue = bundle.queues.first { it.id == queueId }
                if (sorted.isNotEmpty() && queue.currentIndex !in sorted.indices) invalid("A saved queue current item is invalid.")
                if (sorted.isEmpty() && queue.currentIndex != 0) invalid("An empty saved queue has an invalid current index.")
                if (queue.currentMediaId != null && sorted.getOrNull(queue.currentIndex)?.mediaId != queue.currentMediaId) invalid("A saved queue current media pointer is invalid.")
            }
            bundle.playbackState?.let { state ->
                if (state.collectionId != collection.id || state.activeQueueId != null && state.activeQueueId !in queueIds) invalid("Collection playback state is inconsistent.")
            }
        }
        payload.uiSession?.let { session ->
            enumValue<SortOrder>(session.activeSort, "session sort")
            enumValue<LibrarySurface>(session.activeSurface, "library surface")
            if (session.selectedCollectionId != null && session.selectedCollectionId !in collectionIds) invalid("The session points outside the backup.")
            if (session.selectedSourceId != null && session.selectedSourceId !in allSourceIds) invalid("The session source pointer is invalid.")
            if (session.selectedFolderId != null && session.selectedFolderId !in allFolderIds) invalid("The session folder pointer is invalid.")
            if (session.selectedPlaylistId != null && session.selectedPlaylistId !in allPlaylistIds) invalid("The session playlist pointer is invalid.")
            if (session.activeQueueId != null && session.activeQueueId !in allQueueIds) invalid("The session queue pointer is invalid.")
            if (session.selectedArtistKey != null && converters.toMetadataGroupKey(session.selectedArtistKey) == null) invalid("The session artist key is invalid.")
            if (session.selectedAlbumKey != null && converters.toMetadataGroupKey(session.selectedAlbumKey) == null) invalid("The session album key is invalid.")
            if (session.selectedAlbumArtistKey != null && converters.toMetadataGroupKey(session.selectedAlbumArtistKey) == null) invalid("The session album artist key is invalid.")
            if (session.librarySortPreferencesJson != null && converters.toLibrarySortPreferences(session.librarySortPreferencesJson) == null) invalid("Session sort preferences are invalid.")
            if (session.libraryFilterSnapshotJson != null && converters.toLibraryFilterSnapshot(session.libraryFilterSnapshotJson) == null) invalid("Session filters are invalid.")
            if (session.activeFilterSnapshotJson != null && converters.toQueueFilterSnapshot(session.activeFilterSnapshotJson) == null) invalid("Session queue filters are invalid.")
        }
    }

    private inline fun <reified T : Enum<T>> enumValue(value: String, label: String): T =
        enumValues<T>().firstOrNull { it.name == value } ?: invalid("Unsupported $label value.")

    private fun invalid(message: String): Nothing = throw BackupValidationException(message)

    private fun deleteCollectionGraph(collectionId: String) {
        val sql = db.openHelper.writableDatabase
        val args = arrayOf<Any>(collectionId)
        sql.execSQL("DELETE FROM playback_history WHERE mediaId IN (SELECT mf.id FROM media_files mf INNER JOIN root_sources rs ON rs.id = mf.sourceId WHERE rs.collectionId = ?)", args)
        sql.execSQL("DELETE FROM playlists WHERE collectionId = ?", args)
        sql.execSQL("DELETE FROM saved_queues WHERE collectionId = ?", args)
        sql.execSQL("DELETE FROM media_files WHERE sourceId IN (SELECT id FROM root_sources WHERE collectionId = ?)", args)
        sql.execSQL("DELETE FROM folder_nodes WHERE sourceId IN (SELECT id FROM root_sources WHERE collectionId = ?)", args)
        sql.execSQL("DELETE FROM scan_runs WHERE sourceId IN (SELECT id FROM root_sources WHERE collectionId = ?)", args)
        sql.execSQL("DELETE FROM root_sources WHERE collectionId = ?", args)
        sql.execSQL("DELETE FROM collection_playback_state WHERE collectionId = ?", args)
        sql.execSQL("DELETE FROM collections WHERE id = ?", args)
    }

    private suspend fun insertBundle(bundle: BackupCollectionBundle, permissionBySource: Map<String, Boolean>) {
        dao.insertCollections(listOf(bundle.collection.toEntity()))
        dao.insertSources(bundle.sources.map { it.toEntity(permissionBySource[it.id] == true) })
        dao.insertFolders(bundle.folders.sortedBy { it.relativePath.count { char -> char == '/' } }.map { it.toEntity() })
        dao.insertMedia(bundle.media.map { it.toEntity() })
        dao.insertHistory(bundle.history.map { it.toEntity() })
        dao.insertPlaylists(bundle.playlists.map { it.toEntity() })
        dao.insertPlaylistItems(bundle.playlistItems.map { it.toEntity() })
        dao.insertQueues(bundle.queues.map { it.toEntity() })
        dao.insertQueueItems(bundle.queueItems.map { it.toEntity() })
        bundle.playbackState?.let { dao.insertCollectionPlaybackStates(listOf(it.toEntity())) }
    }

    private fun CollectionEntity.toBackup() = BackupCollection(id, name, normalizedName, profile.name, createdAt, updatedAt)
    private fun RootSourceEntity.toBackup() = BackupSource(id, collectionId, treeUri, displayName, lastScanStatus, lastScannedAt, lastScanStartedAt, lastScanCompletedAt, converters.fromScanResult(lastScanSummary))
    private fun FolderNodeEntity.toBackup() = BackupFolder(id, sourceId, parentId, relativePath, displayName)
    private fun MediaFileEntity.toBackup() = BackupMedia(id, sourceId, folderId, documentUri, documentId, relativePath, filename, displayTitle, mimeType, size, durationMs, modifiedTimeMs, firstIndexedAt, metadataScanStatus.name, title, artist, albumArtist, album, discNumber, trackNumber, year, genre, titleSource?.name, artistSource?.name, albumArtistSource?.name, albumSource?.name, discNumberSource?.name, trackNumberSource?.name, playCount, lastPlayedAt, likeScore)
    private fun PlaybackHistoryEntity.toBackup() = BackupHistory(id, mediaId, sessionOccurrenceId, startedAt, endedAt, accumulatedListenedDurationMs, result.name, countedAt)
    private fun PlaylistEntity.toBackup() = BackupPlaylist(id, collectionId, name, normalizedName, createdAt, updatedAt)
    private fun PlaylistItemEntity.toBackup() = BackupPlaylistItem(playlistId, mediaId, position, addedAt)
    private fun SavedQueueEntity.toBackup() = BackupQueue(id, collectionId, kind.name, mode?.name, converters.fromQueueFilterSnapshot(filterSnapshot), seed, currentIndex, currentMediaId, currentOccurrenceId, positionMs, playWhenReadyIntent, playbackSpeed, repeatMode.name, createdAt, updatedAt)
    private fun SavedQueueItemEntity.toBackup() = BackupQueueItem(queueId, itemIndex, queueItemId, mediaId)
    private fun CollectionPlaybackStateEntity.toBackup() = BackupCollectionPlaybackState(collectionId, activeQueueId, updatedAt)
    private fun UiSessionStateEntity.toBackup() = BackupUiSession(currentRoute, selectedCollectionId, selectedSourceId, selectedFolderId, selectedArtist, selectedAlbum, selectedAlbumArtist, converters.fromMetadataGroupKey(selectedArtistKey), converters.fromMetadataGroupKey(selectedAlbumKey), converters.fromMetadataGroupKey(selectedAlbumArtistKey), selectedPlaylistId, activeQueueId, activeSearchQuery, activeSort.name, activeSurface.name, converters.fromLibrarySortPreferences(librarySortPreferences), converters.fromLibraryFilterSnapshot(libraryFilterSnapshot), converters.fromQueueFilterSnapshot(activeFilterSnapshot))

    private fun BackupCollection.toEntity() = CollectionEntity(id, name, CollectionProfile.valueOf(profile), createdAt, updatedAt, normalizedName)
    private fun BackupSource.toEntity(hasPermission: Boolean) = RootSourceEntity(id, collectionId, if (hasPermission) treeUriHint else "resn8-unbound://source/$id", displayName, hasPermission, lastScanStatus, lastScannedAt, lastScanStartedAt, lastScanCompletedAt, converters.toScanResult(lastScanSummaryJson))
    private fun BackupFolder.toEntity() = FolderNodeEntity(id, sourceId, parentId, relativePath, displayName)
    private fun BackupMedia.toEntity() = MediaFileEntity(id, sourceId, folderId, documentUriHint, documentIdHint, relativePath, filename, displayTitle, mimeType, size, durationMs, modifiedTimeMs, firstIndexedAt, false, MetadataScanStatus.valueOf(metadataScanStatus), title, artist, albumArtist, album, discNumber, trackNumber, year, genre, null, titleSource?.let(MetadataValueSource::valueOf), artistSource?.let(MetadataValueSource::valueOf), albumArtistSource?.let(MetadataValueSource::valueOf), albumSource?.let(MetadataValueSource::valueOf), discNumberSource?.let(MetadataValueSource::valueOf), trackNumberSource?.let(MetadataValueSource::valueOf), playCount, lastPlayedAt, likeScore)
    private fun BackupHistory.toEntity() = PlaybackHistoryEntity(id, mediaId, sessionOccurrenceId, startedAt, endedAt, accumulatedListenedDurationMs, PlaybackHistoryResult.valueOf(result), countedAt)
    private fun BackupPlaylist.toEntity() = PlaylistEntity(id, collectionId, name, normalizedName, createdAt, updatedAt)
    private fun BackupPlaylistItem.toEntity() = PlaylistItemEntity(playlistId, mediaId, position, addedAt)
    private fun BackupQueue.toEntity() = SavedQueueEntity(id, collectionId, SavedQueueKind.valueOf(kind), mode?.let(SmartQueueMode::valueOf), converters.toQueueFilterSnapshot(filterSnapshotJson), seed, currentIndex, currentMediaId, currentOccurrenceId, positionMs, playWhenReadyIntent, playbackSpeed, RepeatMode.valueOf(repeatMode), createdAt, updatedAt)
    private fun BackupQueueItem.toEntity() = SavedQueueItemEntity(queueId, itemIndex, queueItemId, mediaId)
    private fun BackupCollectionPlaybackState.toEntity() = CollectionPlaybackStateEntity(collectionId, activeQueueId, updatedAt)
    private fun BackupUiSession.toEntity() = UiSessionStateEntity(1, currentRoute, selectedCollectionId, selectedSourceId, selectedFolderId, selectedArtist, selectedAlbum, selectedAlbumArtist, converters.toMetadataGroupKey(selectedArtistKey), converters.toMetadataGroupKey(selectedAlbumKey), converters.toMetadataGroupKey(selectedAlbumArtistKey), selectedPlaylistId, activeQueueId, activeSearchQuery, SortOrder.valueOf(activeSort), LibrarySurface.valueOf(activeSurface), converters.toLibrarySortPreferences(librarySortPreferencesJson), converters.toLibraryFilterSnapshot(libraryFilterSnapshotJson), converters.toQueueFilterSnapshot(activeFilterSnapshotJson))

    private fun UiSessionStateEntity.safeHomeFor(bundle: BackupCollectionBundle): UiSessionStateEntity {
        val isFlat = bundle.collection.profile == CollectionProfile.FLAT.name
        return copy(
            currentRoute = if (isFlat) "folders" else "library",
            selectedCollectionId = bundle.collection.id,
            selectedSourceId = bundle.sources.singleOrNull()?.id,
            selectedFolderId = null,
            selectedArtist = null,
            selectedAlbum = null,
            selectedAlbumArtist = null,
            selectedArtistKey = null,
            selectedAlbumKey = null,
            selectedAlbumArtistKey = null,
            selectedPlaylistId = null,
            activeQueueId = null,
            activeSearchQuery = null,
            activeSort = if (isFlat) SortOrder.TITLE else SortOrder.ARTIST,
            activeSurface = if (isFlat) LibrarySurface.FOLDERS else LibrarySurface.ARTISTS,
            librarySortPreferences = null,
            libraryFilterSnapshot = null,
            activeFilterSnapshot = null
        )
    }
}
