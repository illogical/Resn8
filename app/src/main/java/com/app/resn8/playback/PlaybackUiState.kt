package com.app.resn8.playback

enum class PlaybackConnectionStatus {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    ERROR
}

data class PlaybackQueueItemState(
    val queueItemId: String,
    val mediaId: String,
    val title: String,
    val artist: String,
    val album: String,
    val isAvailable: Boolean = true,
    val isCurrent: Boolean = false
)

data class PlaybackUiState(
    val connectionStatus: PlaybackConnectionStatus = PlaybackConnectionStatus.DISCONNECTED,
    val connectionError: String? = null,
    val activeQueueId: String? = null,
    val currentQueueItemId: String? = null,
    val currentMediaId: String? = null,
    val currentIndex: Int = -1,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val artworkUri: String? = null,
    val likeScore: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isDurationUnknown: Boolean = false,
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val canPlayPause: Boolean = false,
    val canSeek: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val queueItems: List<PlaybackQueueItemState> = emptyList(),
    val notice: PlaybackNotice? = null
)
