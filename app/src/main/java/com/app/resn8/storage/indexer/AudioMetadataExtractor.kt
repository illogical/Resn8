package com.app.resn8.storage.indexer

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AudioMetadataExtractor(
    private val context: Context
) {
    suspend fun extractTags(documentUri: Uri): ExtractedTags = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, documentUri)
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            val artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            val albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            val album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            val yearRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)
            val genre = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE)
            val durationRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val trackRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
            val discRaw = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DISC_NUMBER)

            val trackNumber = parseNumberPrefix(trackRaw)
            val discNumber = parseNumberPrefix(discRaw)
            val year = yearRaw?.trim()?.take(4)?.toIntOrNull()
            val durationMs = durationRaw?.toLongOrNull()

            ExtractedTags(
                title = title?.trim(),
                artist = artist?.trim(),
                albumArtist = albumArtist?.trim(),
                album = album?.trim(),
                discNumber = discNumber,
                trackNumber = trackNumber,
                year = year,
                genre = genre?.trim(),
                durationMs = durationMs
            )
        } catch (e: Exception) {
            ExtractedTags()
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {}
        }
    }

    private fun parseNumberPrefix(value: String?): Int? {
        if (value.isNullOrBlank()) return null
        val prefix = value.substringBefore('/').trim()
        return prefix.toIntOrNull()
    }
}
