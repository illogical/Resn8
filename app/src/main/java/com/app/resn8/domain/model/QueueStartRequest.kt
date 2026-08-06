package com.app.resn8.domain.model

sealed interface QueueStartRequest {
    val startingMediaId: String

    data class Library(
        val query: LibraryQuery,
        override val startingMediaId: String
    ) : QueueStartRequest

    data class Playlist(
        val playlistId: String,
        override val startingMediaId: String,
        val playWhenReady: Boolean = true
    ) : QueueStartRequest
}
