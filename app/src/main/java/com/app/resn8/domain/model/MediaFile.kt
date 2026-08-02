package com.app.resn8.domain.model

data class MediaFile(
    val id: String,
    val sourceId: String,
    val folderId: String,
    val documentUri: String,
    val relativePath: String,
    val filename: String,
    val displayTitle: String,
    val mimeType: String,
    val size: Long,
    val durationMs: Long,
    val modifiedTimeMs: Long,
    val isAvailable: Boolean = true,
    // Music metadata (nullable)
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val artworkUri: String? = null,
    // Listening statistics & rating
    val playCount: Int = 0,
    val lastPlayedAt: Long? = null,
    val likeScore: Int = 0
)
