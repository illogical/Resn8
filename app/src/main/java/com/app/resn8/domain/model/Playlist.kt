package com.app.resn8.domain.model

data class Playlist(
    val id: String,
    val collectionId: String,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class PlaylistItem(
    val playlistId: String,
    val mediaId: String,
    val position: Long,
    val addedAt: Long = System.currentTimeMillis()
)
