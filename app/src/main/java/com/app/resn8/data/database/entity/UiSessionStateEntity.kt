package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
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
    val selectedFolderId: String?,
    val selectedArtist: String?,
    val selectedAlbum: String?,
    val selectedPlaylistId: String?,
    val activeQueueId: String?,
    val activeSearchQuery: String?,
    val activeSort: SortOrder,
    val activeFilterSnapshot: QueueFilterSnapshot?
)

fun UiSessionStateEntity.toDomain() = UiSessionState(
    currentRoute = currentRoute,
    selectedCollectionId = selectedCollectionId,
    selectedFolderId = selectedFolderId,
    selectedArtist = selectedArtist,
    selectedAlbum = selectedAlbum,
    selectedPlaylistId = selectedPlaylistId,
    activeQueueId = activeQueueId,
    activeSearchQuery = activeSearchQuery,
    activeSort = activeSort,
    activeFilterSnapshot = activeFilterSnapshot
)

fun UiSessionState.toEntity() = UiSessionStateEntity(
    id = 1,
    currentRoute = currentRoute,
    selectedCollectionId = selectedCollectionId,
    selectedFolderId = selectedFolderId,
    selectedArtist = selectedArtist,
    selectedAlbum = selectedAlbum,
    selectedPlaylistId = selectedPlaylistId,
    activeQueueId = activeQueueId,
    activeSearchQuery = activeSearchQuery,
    activeSort = activeSort,
    activeFilterSnapshot = activeFilterSnapshot
)
