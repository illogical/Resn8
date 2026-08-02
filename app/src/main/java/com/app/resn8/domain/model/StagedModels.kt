package com.app.resn8.domain.model

data class StagedFolder(
    val id: String,
    val scanId: String,
    val relativePath: String,
    val parentRelativePath: String? = null,
    val displayName: String,
    val resolvedFolderId: String? = null
)

data class StagedMedia(
    val id: String,
    val scanId: String,
    val documentUri: String,
    val documentId: String? = null,
    val relativePath: String,
    val filename: String,
    val displayTitle: String,
    val mimeType: String,
    val size: Long,
    val durationMs: Long? = null,
    val modifiedTimeMs: Long,
    val metadataScanStatus: MetadataScanStatus = MetadataScanStatus.PENDING,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val artworkUri: String? = null,
    val titleSource: MetadataValueSource? = null,
    val artistSource: MetadataValueSource? = null,
    val albumArtistSource: MetadataValueSource? = null,
    val albumSource: MetadataValueSource? = null,
    val discNumberSource: MetadataValueSource? = null,
    val trackNumberSource: MetadataValueSource? = null,
    val resolvedMediaId: String? = null,
    val resolvedFolderId: String? = null
)
