package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MetadataValueSource
import com.app.resn8.domain.model.StagedMedia

@Entity(
    tableName = "staged_media",
    foreignKeys = [
        ForeignKey(
            entity = ScanRunEntity::class,
            parentColumns = ["id"],
            childColumns = ["scanId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["scanId", "documentUri"], unique = true),
        Index(value = ["scanId", "documentId"]),
        Index(value = ["scanId", "relativePath"])
    ]
)
data class StagedMediaEntity(
    @PrimaryKey val id: String,
    val scanId: String,
    val documentUri: String,
    val documentId: String?,
    val relativePath: String,
    val filename: String,
    val displayTitle: String,
    val mimeType: String,
    val size: Long,
    val durationMs: Long?,
    val modifiedTimeMs: Long,
    val metadataScanStatus: MetadataScanStatus,
    val title: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val year: Int?,
    val genre: String?,
    val artworkUri: String?,
    val titleSource: MetadataValueSource?,
    val artistSource: MetadataValueSource?,
    val albumArtistSource: MetadataValueSource?,
    val albumSource: MetadataValueSource?,
    val discNumberSource: MetadataValueSource?,
    val trackNumberSource: MetadataValueSource?,
    val resolvedMediaId: String?,
    val resolvedFolderId: String?
)

fun StagedMediaEntity.toDomain() = StagedMedia(
    id = id,
    scanId = scanId,
    documentUri = documentUri,
    documentId = documentId,
    relativePath = relativePath,
    filename = filename,
    displayTitle = displayTitle,
    mimeType = mimeType,
    size = size,
    durationMs = durationMs,
    modifiedTimeMs = modifiedTimeMs,
    metadataScanStatus = metadataScanStatus,
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    discNumber = discNumber,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    artworkUri = artworkUri,
    titleSource = titleSource,
    artistSource = artistSource,
    albumArtistSource = albumArtistSource,
    albumSource = albumSource,
    discNumberSource = discNumberSource,
    trackNumberSource = trackNumberSource,
    resolvedMediaId = resolvedMediaId,
    resolvedFolderId = resolvedFolderId
)

fun StagedMedia.toEntity() = StagedMediaEntity(
    id = id,
    scanId = scanId,
    documentUri = documentUri,
    documentId = documentId,
    relativePath = relativePath,
    filename = filename,
    displayTitle = displayTitle,
    mimeType = mimeType,
    size = size,
    durationMs = durationMs,
    modifiedTimeMs = modifiedTimeMs,
    metadataScanStatus = metadataScanStatus,
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    discNumber = discNumber,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    artworkUri = artworkUri,
    titleSource = titleSource,
    artistSource = artistSource,
    albumArtistSource = albumArtistSource,
    albumSource = albumSource,
    discNumberSource = discNumberSource,
    trackNumberSource = trackNumberSource,
    resolvedMediaId = resolvedMediaId,
    resolvedFolderId = resolvedFolderId
)
