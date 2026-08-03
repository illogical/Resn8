package com.app.resn8.storage.indexer

object AudioAdmissionPolicy {
    enum class RejectionReason {
        ZERO_BYTE,
        APPLEDOUBLE_SIDECAR,
        UNSUPPORTED_MIME,
        UNSUPPORTED_EXTENSION,
        MALFORMED_DOCUMENT
    }

    data class Decision(
        val isSupported: Boolean,
        val rejectionReason: RejectionReason? = null
    )

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
    private val knownAudioLikeExtensions = supportedExtensions + setOf("wma")

    fun isSupported(filename: String, mimeType: String?, size: Long?): Boolean {
        return evaluate(filename, mimeType, size).isSupported
    }

    fun evaluate(filename: String, mimeType: String?, size: Long?): Decision {
        if (size == 0L) return Decision(false, RejectionReason.ZERO_BYTE)
        if (filename.startsWith("._")) {
            return Decision(false, RejectionReason.APPLEDOUBLE_SIDECAR)
        }
        val normalizedMime = mimeType?.trim()?.lowercase().orEmpty()
        if (normalizedMime in supportedMimeTypes) return Decision(true)
        if (normalizedMime !in genericMimeTypes) {
            return Decision(false, RejectionReason.UNSUPPORTED_MIME)
        }
        return if (filename.substringAfterLast('.', "").lowercase() in supportedExtensions) {
            Decision(true)
        } else {
            Decision(false, RejectionReason.UNSUPPORTED_EXTENSION)
        }
    }

    fun isAudioLike(filename: String, mimeType: String?): Boolean =
        mimeType?.trim()?.lowercase()?.startsWith("audio/") == true ||
            filename.substringAfterLast('.', "").lowercase() in knownAudioLikeExtensions
}
