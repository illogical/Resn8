package com.app.resn8.storage.indexer

import com.app.resn8.domain.model.MetadataValueSource
import com.app.resn8.domain.model.CollectionProfile

data class ExtractedTags(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null
)

data class NormalizedMetadata(
    val displayTitle: String,
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val discNumber: Int? = null,
    val trackNumber: Int? = null,
    val year: Int? = null,
    val genre: String? = null,
    val durationMs: Long? = null,
    val artworkUri: String? = null,
    val titleSource: MetadataValueSource? = null,
    val artistSource: MetadataValueSource? = null,
    val albumArtistSource: MetadataValueSource? = null,
    val albumSource: MetadataValueSource? = null,
    val discNumberSource: MetadataValueSource? = null,
    val trackNumberSource: MetadataValueSource? = null,
    val isPatternRecognized: Boolean = true
)

object FallbackParser {

    // Regex 1: Disc-Track prefix e.g. "1-01 - Title.mp3", "1-01. Title.mp3", "1-01 Title.mp3"
    private val discTrackRegex = Regex("""^(\d{1,2})[-.](\d{1,2})\s*[-._\s]+\s*(.+)$""")

    // Regex 2: Track prefix e.g. "01 - Title.mp3", "01. Title.mp3", "01_Title.mp3", "01 Title.mp3"
    private val trackRegex = Regex("""^(\d{1,2})\s*[-._\s]+\s*(.+)$""")

    // Regex 3: 3 or 4 digit Disc+Track prefix e.g. "101 Title.mp3" (Disc 1, Track 01), "205 - Title.mp3"
    private val compactDiscTrackRegex = Regex("""^(\d)(\d{2})\s*[-._\s]+\s*(.+)$""")

    fun normalize(
        relativePath: String,
        filename: String,
        tags: ExtractedTags,
        profile: CollectionProfile = CollectionProfile.MUSIC
    ): NormalizedMetadata {
        val cleanName = cleanFilename(filename)
        var title: String? = tags.title?.takeIf { it.isNotBlank() }
        var artist: String? = tags.artist?.takeIf { it.isNotBlank() }
        var albumArtist: String? = tags.albumArtist?.takeIf { it.isNotBlank() }
        var album: String? = tags.album?.takeIf { it.isNotBlank() }
        var discNumber: Int? = tags.discNumber
        var trackNumber: Int? = tags.trackNumber

        var titleSource: MetadataValueSource? = if (title != null) MetadataValueSource.TAG else null
        var artistSource: MetadataValueSource? = if (artist != null) MetadataValueSource.TAG else null
        var albumArtistSource: MetadataValueSource? = if (albumArtist != null) MetadataValueSource.TAG else null
        var albumSource: MetadataValueSource? = if (album != null) MetadataValueSource.TAG else null
        var discNumberSource: MetadataValueSource? = if (discNumber != null) MetadataValueSource.TAG else null
        var trackNumberSource: MetadataValueSource? = if (trackNumber != null) MetadataValueSource.TAG else null

        if (profile == CollectionProfile.FLAT) {
            val finalTitle = title ?: cleanName
            return NormalizedMetadata(
                displayTitle = finalTitle,
                title = finalTitle,
                artist = artist,
                albumArtist = albumArtist,
                album = album,
                discNumber = discNumber,
                trackNumber = trackNumber,
                year = tags.year,
                genre = tags.genre?.takeIf { it.isNotBlank() },
                durationMs = tags.durationMs?.takeIf { it > 0 },
                artworkUri = tags.artworkUri,
                titleSource = titleSource ?: MetadataValueSource.FILENAME,
                artistSource = artistSource,
                albumArtistSource = albumArtistSource,
                albumSource = albumSource,
                discNumberSource = discNumberSource,
                trackNumberSource = trackNumberSource,
                isPatternRecognized = true
            )
        }

        // 1. Music-only path inference for Artist/Album from directory hierarchy (excluding filename)
        val pathSegments = relativePath.split('/').filter { it.isNotBlank() }
        val folderSegments = if (pathSegments.isNotEmpty() && pathSegments.last() == filename) {
            pathSegments.dropLast(1)
        } else {
            pathSegments
        }

        if (folderSegments.size >= 2) {
            val potentialAlbum = folderSegments.last()
            val potentialArtist = folderSegments[folderSegments.size - 2]

            if (album == null && potentialAlbum.isNotBlank()) {
                album = potentialAlbum
                albumSource = MetadataValueSource.PATH
            }
            if (artist == null && potentialArtist.isNotBlank()) {
                artist = potentialArtist
                artistSource = MetadataValueSource.PATH
            }
        } else if (folderSegments.size == 1) {
            val potentialAlbum = folderSegments.first()
            if (album == null && potentialAlbum.isNotBlank()) {
                album = potentialAlbum
                albumSource = MetadataValueSource.PATH
            }
        }

        // 2. Filename prefix parsing for disc, track, title fallback
        var parsedTitleFromFilename: String? = null
        var patternRecognized = true

        val discTrackMatch = discTrackRegex.find(cleanName)
        val trackMatch = trackRegex.find(cleanName)
        val compactMatch = compactDiscTrackRegex.find(cleanName)

        when {
            discTrackMatch != null -> {
                val (discStr, trackStr, titleStr) = discTrackMatch.destructured
                if (discNumber == null) {
                    discNumber = discStr.toIntOrNull()
                    if (discNumber != null) discNumberSource = MetadataValueSource.FILENAME
                }
                if (trackNumber == null) {
                    trackNumber = trackStr.toIntOrNull()
                    if (trackNumber != null) trackNumberSource = MetadataValueSource.FILENAME
                }
                parsedTitleFromFilename = titleStr.trim()
            }
            compactMatch != null -> {
                val (discStr, trackStr, titleStr) = compactMatch.destructured
                if (discNumber == null) {
                    discNumber = discStr.toIntOrNull()
                    if (discNumber != null) discNumberSource = MetadataValueSource.FILENAME
                }
                if (trackNumber == null) {
                    trackNumber = trackStr.toIntOrNull()
                    if (trackNumber != null) trackNumberSource = MetadataValueSource.FILENAME
                }
                parsedTitleFromFilename = titleStr.trim()
            }
            trackMatch != null -> {
                val (trackStr, titleStr) = trackMatch.destructured
                if (trackNumber == null) {
                    trackNumber = trackStr.toIntOrNull()
                    if (trackNumber != null) trackNumberSource = MetadataValueSource.FILENAME
                }
                parsedTitleFromFilename = titleStr.trim()
            }
            else -> {
                parsedTitleFromFilename = cleanName
                patternRecognized = false
            }
        }

        // 3. Display title precedence: Tag title -> Inferred filename title -> Cleaned filename
        val finalTitle = title ?: parsedTitleFromFilename?.takeIf { it.isNotBlank() } ?: cleanName
        if (title == null) {
            title = finalTitle
            titleSource = MetadataValueSource.FILENAME
        }

        return NormalizedMetadata(
            displayTitle = finalTitle,
            title = title,
            artist = artist,
            albumArtist = albumArtist,
            album = album,
            discNumber = discNumber,
            trackNumber = trackNumber,
            year = tags.year,
            genre = tags.genre?.takeIf { it.isNotBlank() },
            durationMs = tags.durationMs?.takeIf { it > 0 },
            artworkUri = tags.artworkUri,
            titleSource = titleSource,
            artistSource = artistSource,
            albumArtistSource = albumArtistSource,
            albumSource = albumSource,
            discNumberSource = discNumberSource,
            trackNumberSource = trackNumberSource,
            isPatternRecognized = patternRecognized
        )
    }

    fun cleanFilename(filename: String): String {
        val nameWithoutExt = filename.substringBeforeLast('.')
        return nameWithoutExt.replace('_', ' ').trim()
    }
}
