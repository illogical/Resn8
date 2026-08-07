package com.app.resn8.domain.model

import kotlinx.serialization.Serializable

@Serializable
sealed interface PlaybackOrigin {
    @Serializable
    data class Playlist(val playlistId: String, val playlistName: String) : PlaybackOrigin

    @Serializable
    data class Album(
        val albumKeySerialized: String,
        val albumArtistKeySerialized: String?,
        val albumName: String
    ) : PlaybackOrigin

    @Serializable
    data class Artist(val artistKeySerialized: String, val artistName: String) : PlaybackOrigin

    @Serializable
    data class Folder(
        val sourceId: String?,
        val folderId: String,
        val folderName: String
    ) : PlaybackOrigin

    @Serializable
    data object AllTracks : PlaybackOrigin
}

enum class SavedQueueKind {
    EXPLICIT,
    GENERATED
}

enum class SmartQueueMode {
    RANDOM_ELIGIBLE,
    UNPLAYED,
    LEAST_PLAYED,
    MOST_PLAYED,
    MOST_LIKED,
    MOST_RECENTLY_PLAYED,
    LEAST_RECENTLY_PLAYED
}

@Serializable
data class QueueFilterSnapshot(
    val collectionId: String? = null,
    val sourceId: String? = null,
    val folderId: String? = null,
    val folderName: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val albumArtist: String? = null,
    val searchQuery: String? = null,
    val excludeDisliked: Boolean = true,
    val playlistId: String? = null,
    val playlistName: String? = null,
    val origin: PlaybackOrigin? = null
)

fun QueueFilterSnapshot.resolvePlaybackOrigin(): PlaybackOrigin? = origin ?: when {
    playlistId != null && playlistName != null -> PlaybackOrigin.Playlist(playlistId, playlistName)
    album != null -> PlaybackOrigin.Album(
        albumKeySerialized = MetadataGroupKey.Known(album).serialize(),
        albumArtistKeySerialized = albumArtist?.let { MetadataGroupKey.Known(it).serialize() },
        albumName = album
    )
    artist != null -> PlaybackOrigin.Artist(MetadataGroupKey.Known(artist).serialize(), artist)
    folderId != null -> PlaybackOrigin.Folder(sourceId, folderId, folderName ?: "Folder")
    collectionId != null -> PlaybackOrigin.AllTracks
    else -> null
}

data class SavedQueueItem(
    val queueItemId: String,
    val mediaId: String
)

data class SavedQueue(
    val id: String,
    val collectionId: String,
    val kind: SavedQueueKind,
    val mode: SmartQueueMode? = null,
    val filterSnapshot: QueueFilterSnapshot? = null,
    val seed: Long? = null,
    val orderedMediaIds: List<String> = emptyList(),
    val items: List<SavedQueueItem> = emptyList(),
    val currentIndex: Int = 0,
    val currentMediaId: String? = null,
    val currentOccurrenceId: String? = null,
    val positionMs: Long = 0L,
    val isPlaying: Boolean = false,
    val playWhenReadyIntent: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
