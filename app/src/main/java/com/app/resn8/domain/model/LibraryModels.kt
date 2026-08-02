package com.app.resn8.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LibrarySurface { ARTISTS, ALBUMS, ALL_TRACKS, FOLDERS }

@Serializable
sealed interface MetadataGroupKey {
    @Serializable
    data class Known(val value: String) : MetadataGroupKey

    @Serializable
    data object Unknown : MetadataGroupKey

    fun serialize(): String = when (this) {
        is Known -> "KNOWN:$value"
        is Unknown -> "UNKNOWN"
    }

    companion object {
        fun deserialize(raw: String?): MetadataGroupKey? {
            if (raw == null) return null
            return when {
                raw == "UNKNOWN" -> Unknown
                raw.startsWith("KNOWN:") -> Known(raw.removePrefix("KNOWN:"))
                else -> Known(raw)
            }
        }
    }
}

@Serializable
enum class AvailabilityFilter { ALL, AVAILABLE_ONLY, UNAVAILABLE_ONLY }

@Serializable
data class LibraryFilterSnapshot(
    val version: Int = 1,
    val availability: AvailabilityFilter = AvailabilityFilter.ALL,
    val excludeDisliked: Boolean = false,
)

data class LibraryQuery(
    val collectionId: String,
    val sourceId: String? = null,
    val folderId: String? = null,
    val includeFolderDescendants: Boolean = false,
    val artist: MetadataGroupKey? = null,
    val album: MetadataGroupKey? = null,
    val albumArtist: MetadataGroupKey? = null,
    val searchText: String = "",
    val sort: SortOrder = SortOrder.ARTIST,
    val filters: LibraryFilterSnapshot = LibraryFilterSnapshot(),
) {
    fun normalizedSearchText(): String? {
        val trimmed = searchText.trim()
        return if (trimmed.isEmpty()) null else trimmed
    }

    fun escapedSearchPattern(): String? {
        val norm = normalizedSearchText() ?: return null
        val escaped = norm.replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_")
        return "%$escaped%"
    }
}

data class ArtistSummary(
    val key: MetadataGroupKey,
    val displayName: String,
    val totalTrackCount: Int,
    val availableTrackCount: Int,
    val albumCount: Int,
    val representativeArtworkUri: String? = null
)

data class AlbumSummary(
    val albumKey: MetadataGroupKey,
    val albumDisplayName: String,
    val effectiveAlbumArtistKey: MetadataGroupKey,
    val effectiveAlbumArtistDisplayName: String,
    val totalTrackCount: Int,
    val availableTrackCount: Int,
    val minYear: Int? = null,
    val representativeMediaId: String? = null,
    val representativeArtworkUri: String? = null
) {
    val compositeKey: String
        get() = "${albumKey.serialize()}||${effectiveAlbumArtistKey.serialize()}"
}

data class TrackListItem(
    val mediaFile: MediaFile
)

data class FolderListItem(
    val folder: FolderNode,
    val childFolderCount: Int,
    val directMediaCount: Int,
    val descendantMediaCount: Int? = null
)

data class FolderBreadcrumb(
    val id: String,
    val displayName: String
)

data class SelectionResolutionResult(
    val uniqueMediaIds: List<String>,
    val totalCount: Int,
    val availableCount: Int
)
