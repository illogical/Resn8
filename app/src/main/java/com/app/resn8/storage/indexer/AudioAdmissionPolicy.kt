package com.app.resn8.storage.indexer

object AudioAdmissionPolicy {
    private val supportedMimeTypes = setOf(
        "audio/mpeg",
        "audio/mp4",
        "audio/aac",
        "audio/flac",
        "audio/ogg",
        "audio/x-wav",
        "audio/wav",
        "audio/opus",
        "audio/x-matroska",
        "audio/m4a"
    )

    private val genericMimeTypes = setOf(
        "",
        "application/octet-stream",
        "application/x-unknown",
        "binary/octet-stream"
    )

    val supportedExtensions = setOf("mp3", "m4a", "aac", "flac", "ogg", "oga", "wav", "opus", "mka")

    fun isSupported(filename: String, mimeType: String?, size: Long?): Boolean {
        if (size == 0L) return false
        if (filename.startsWith("._")) return false // AppleDouble metadata sidecar, not playable audio.
        val normalizedMime = mimeType?.trim()?.lowercase().orEmpty()
        if (normalizedMime in supportedMimeTypes) return true
        if (normalizedMime !in genericMimeTypes) return false
        return filename.substringAfterLast('.', "").lowercase() in supportedExtensions
    }
}
