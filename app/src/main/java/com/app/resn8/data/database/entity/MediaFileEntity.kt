package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MetadataValueSource

@Entity(
    tableName = "media_files",
    foreignKeys = [
        ForeignKey(
            entity = RootSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = FolderNodeEntity::class,
            parentColumns = ["id"],
            childColumns = ["folderId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sourceId", "documentUri"], unique = true),
        Index(value = ["sourceId", "relativePath"], unique = true),
        Index(value = ["sourceId"]),
        Index(value = ["folderId"]),
        Index(value = ["artist"]),
        Index(value = ["album"]),
        Index(value = ["album", "discNumber", "trackNumber"]),
        Index(value = ["firstIndexedAt"]),
        Index(value = ["playCount"]),
        Index(value = ["lastPlayedAt"]),
        Index(value = ["likeScore"]),
        Index(value = ["isAvailable"])
    ]
)
data class MediaFileEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val folderId: String,
    val documentUri: String,
    val documentId: String?,
    val relativePath: String,
    val filename: String,
    val displayTitle: String,
    val mimeType: String,
    val size: Long,
    val durationMs: Long?,
    val modifiedTimeMs: Long,
    val firstIndexedAt: Long,
    val isAvailable: Boolean,
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
    val playCount: Int,
    val lastPlayedAt: Long?,
    val likeScore: Int
)

fun MediaFileEntity.toDomain() = MediaFile(
    id = id,
    sourceId = sourceId,
    folderId = folderId,
    documentUri = documentUri,
    documentId = documentId,
    relativePath = relativePath,
    filename = filename,
    displayTitle = displayTitle,
    mimeType = mimeType,
    size = size,
    durationMs = durationMs,
    modifiedTimeMs = modifiedTimeMs,
    firstIndexedAt = firstIndexedAt,
    isAvailable = isAvailable,
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
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    likeScore = likeScore
)

fun MediaFile.toEntity() = MediaFileEntity(
    id = id,
    sourceId = sourceId,
    folderId = folderId,
    documentUri = documentUri,
    documentId = documentId,
    relativePath = relativePath,
    filename = filename,
    displayTitle = displayTitle,
    mimeType = mimeType,
    size = size,
    durationMs = durationMs,
    modifiedTimeMs = modifiedTimeMs,
    firstIndexedAt = firstIndexedAt,
    isAvailable = isAvailable,
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
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    likeScore = likeScore
)
