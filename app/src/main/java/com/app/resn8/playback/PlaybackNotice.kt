package com.app.resn8.playback

data class PlaybackNotice(
    val id: String = java.util.UUID.randomUUID().toString(),
    val queueItemId: String? = null,
    val mediaId: String? = null,
    val message: String,
    val isFatal: Boolean = false
)
