package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.LibraryFilterSnapshot
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.QueueFilterSnapshot
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.UiSessionState

@Entity(
    tableName = "ui_session_state",
    foreignKeys = [
        ForeignKey(
            entity = CollectionEntity::class,
            parentColumns = ["id"],
            childColumns = ["selectedCollectionId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = FolderNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["selectedFolderId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["selectedPlaylistId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = SavedQueueEntity::class,
            parentColumns = ["id"],
            childColumns = ["activeQueueId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        androidx.room.Index(value = ["selectedCollectionId"]),
        androidx.room.Index(value = ["selectedFolderId"]),
        androidx.room.Index(value = ["selectedPlaylistId"]),
        androidx.room.Index(value = ["activeQueueId"])
    ]
)
data class UiSessionStateEntity(
    @PrimaryKey val id: Int = 1,
    val currentRoute: String,
    val selectedCollectionId: String?,
    val selectedSourceId: String? = null,
    val selectedFolderId: String?,
    val selectedArtist: String?,
    val selectedAlbum: String?,
    val selectedAlbumArtist: String? = null,
    val selectedArtistKey: MetadataGroupKey? = null,
    val selectedAlbumKey: MetadataGroupKey? = null,
    val selectedAlbumArtistKey: MetadataGroupKey? = null,
    val selectedPlaylistId: String?,
    val activeQueueId: String?,
    val activeSearchQuery: String?,
    val activeSort: SortOrder,
    val activeSurface: LibrarySurface = LibrarySurface.ARTISTS,
    val libraryFilterSnapshot: LibraryFilterSnapshot? = LibraryFilterSnapshot(),
    val activeFilterSnapshot: QueueFilterSnapshot?
)

fun UiSessionStateEntity.toDomain() = UiSessionState(
    currentRoute = currentRoute,
    selectedCollectionId = selectedCollectionId,
    selectedSourceId = selectedSourceId,
    selectedFolderId = selectedFolderId,
    selectedArtistKey = selectedArtistKey ?: selectedArtist?.let { MetadataGroupKey.Known(it) },
    selectedAlbumKey = selectedAlbumKey ?: selectedAlbum?.let { MetadataGroupKey.Known(it) },
    selectedAlbumArtistKey = selectedAlbumArtistKey ?: selectedAlbumArtist?.let { MetadataGroupKey.Known(it) },
    selectedPlaylistId = selectedPlaylistId,
    activeQueueId = activeQueueId,
    activeSearchQuery = activeSearchQuery,
    activeSort = activeSort,
    activeSurface = activeSurface,
    libraryFilterSnapshot = libraryFilterSnapshot ?: LibraryFilterSnapshot(),
    activeFilterSnapshot = activeFilterSnapshot
)

fun UiSessionState.toEntity() = UiSessionStateEntity(
    id = 1,
    currentRoute = currentRoute,
    selectedCollectionId = selectedCollectionId,
    selectedSourceId = selectedSourceId,
    selectedFolderId = selectedFolderId,
    selectedArtist = (selectedArtistKey as? MetadataGroupKey.Known)?.value,
    selectedAlbum = (selectedAlbumKey as? MetadataGroupKey.Known)?.value,
    selectedAlbumArtist = (selectedAlbumArtistKey as? MetadataGroupKey.Known)?.value,
    selectedArtistKey = selectedArtistKey,
    selectedAlbumKey = selectedAlbumKey,
    selectedAlbumArtistKey = selectedAlbumArtistKey,
    selectedPlaylistId = selectedPlaylistId,
    activeQueueId = activeQueueId,
    activeSearchQuery = activeSearchQuery,
    activeSort = activeSort,
    activeSurface = activeSurface,
    libraryFilterSnapshot = libraryFilterSnapshot,
    activeFilterSnapshot = activeFilterSnapshot
)
