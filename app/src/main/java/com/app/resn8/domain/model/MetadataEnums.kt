package com.app.resn8.domain.model

enum class MetadataScanStatus {
    PENDING,
    SUCCESS,
    FAILED,
    UNSUPPORTED
}

enum class MetadataValueSource {
    TAG,
    PATH,
    FILENAME
}

enum class PlaybackHistoryResult {
    IN_PROGRESS,
    DISCARDED,
    THRESHOLD_COUNTED,
    NATURAL_COMPLETION_COUNTED
}

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
