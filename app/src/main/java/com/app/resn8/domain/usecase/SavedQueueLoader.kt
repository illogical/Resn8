package com.app.resn8.domain.usecase

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.QueueRepository
import com.app.resn8.playback.RESN8_MEDIA_FILE_ID
import com.app.resn8.playback.RESN8_QUEUE_ID
import com.app.resn8.playback.RESN8_QUEUE_ITEM_ID
import kotlinx.coroutines.flow.firstOrNull

data class ResolvedQueueLoad(
    val savedQueue: SavedQueue,
    val mediaItems: List<MediaItem>,
    val availableIndices: List<Int>,
    val startIndex: Int,
    val startPositionMs: Long,
    val startMediaFile: MediaFile?
)

class SavedQueueLoader(
    private val queueRepository: QueueRepository,
    private val mediaRepository: MediaRepository
) {
    suspend fun loadSavedQueue(queueId: String): ResolvedQueueLoad? {
        val savedQueue = queueRepository.getQueueByIdFlow(queueId).firstOrNull() ?: return null
        if (savedQueue.items.isEmpty()) return null

        val mediaFiles = mediaRepository.getMediaFilesByIdsPreservingOrder(savedQueue.orderedMediaIds)
        val fileMap = mediaFiles.associateBy { it.id }

        val availableIndices = mutableListOf<Int>()
        val mediaItems = savedQueue.items.mapIndexed { index, item ->
            val mediaFile = fileMap[item.mediaId]
            if (mediaFile != null && mediaFile.isAvailable) {
                availableIndices.add(index)
            }

            val extras = Bundle().apply {
                putString(RESN8_QUEUE_ID, savedQueue.id)
                putString(RESN8_MEDIA_FILE_ID, item.mediaId)
                putString(RESN8_QUEUE_ITEM_ID, item.queueItemId)
            }

            val metadata = MediaMetadata.Builder()
                .setTitle(mediaFile?.displayTitle ?: item.mediaId)
                .setArtist(mediaFile?.artist ?: "Unknown Artist")
                .setAlbumTitle(mediaFile?.album ?: "Unknown Album")
                .setArtworkUri(mediaFile?.artworkUri?.let { Uri.parse(it) })
                .setTrackNumber(mediaFile?.trackNumber)
                .setDiscNumber(mediaFile?.discNumber)
                .build()

            val builder = MediaItem.Builder()
                .setMediaId(item.queueItemId)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setExtras(extras)
                        .build()
                )
                .setMediaMetadata(metadata)

            if (mediaFile != null && mediaFile.isAvailable) {
                builder.setUri(Uri.parse(mediaFile.documentUri))
            }

            builder.build()
        }

        var validatedIndex = savedQueue.currentIndex.coerceIn(0, savedQueue.items.size - 1)
        var startMediaFile = fileMap[savedQueue.items[validatedIndex].mediaId]

        // If current index item is unavailable, fallback to first available item if present
        if (startMediaFile == null || !startMediaFile.isAvailable) {
            val firstAvailable = availableIndices.firstOrNull()
            if (firstAvailable != null) {
                validatedIndex = firstAvailable
                startMediaFile = fileMap[savedQueue.items[validatedIndex].mediaId]
            }
        }

        val duration = startMediaFile?.durationMs ?: 0L
        val boundedPositionMs = if (duration > 0L) {
            savedQueue.positionMs.coerceIn(0L, duration)
        } else {
            savedQueue.positionMs.coerceAtLeast(0L)
        }

        return ResolvedQueueLoad(
            savedQueue = savedQueue,
            mediaItems = mediaItems,
            availableIndices = availableIndices,
            startIndex = validatedIndex,
            startPositionMs = boundedPositionMs,
            startMediaFile = startMediaFile
        )
    }
}
