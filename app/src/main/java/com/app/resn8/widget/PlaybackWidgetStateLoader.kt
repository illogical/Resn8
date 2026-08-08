package com.app.resn8.widget

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.media3.common.Player
import androidx.core.net.toUri
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.app.resn8.Resn8Application
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.playback.DISLIKE_CURRENT_COMMAND
import com.app.resn8.playback.LIKE_CURRENT_COMMAND
import com.app.resn8.playback.Resn8MediaService
import com.app.resn8.storage.artwork.ArtworkCache
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class PlaybackWidgetStateLoader(private val context: Context) {
    suspend fun load(): PlaybackWidgetSnapshot {
        val container = (context.applicationContext as Resn8Application).container
        val session = container.uiSessionRepository.getUiSessionStateFlow().first()
        val collections = container.collectionRepository.getCollectionsFlow().first()
        val collection = collections.firstOrNull { it.id == session.selectedCollectionId }
            ?: collections.singleOrNull()
            ?: return PlaybackWidgetSnapshot(
                status = PlaybackWidgetStatus.EMPTY,
                emptyDestination = WidgetDestination.ONBOARDING,
                message = "Set up a collection to start listening"
            )

        val hasPlaylists = container.playlistRepository.getPlaylistsFlow(collection.id).first().isNotEmpty()
        val emptyDestination = if (hasPlaylists) WidgetDestination.PLAYLISTS else WidgetDestination.FOLDERS
        val queueId = session.activeQueueId
            ?: return PlaybackWidgetSnapshot(
                status = PlaybackWidgetStatus.EMPTY,
                emptyDestination = emptyDestination,
                message = "Choose something to play"
            )
        val queue = container.queueRepository.getQueueByIdFlow(queueId).first()
            ?: return PlaybackWidgetSnapshot(
                status = PlaybackWidgetStatus.EMPTY,
                emptyDestination = emptyDestination,
                message = "Choose something to play"
            )
        if (queue.items.isEmpty()) {
            return PlaybackWidgetSnapshot(
                status = PlaybackWidgetStatus.EMPTY,
                emptyDestination = emptyDestination,
                message = "Choose something to play"
            )
        }

        val mediaFiles = container.mediaRepository.getMediaFilesByIdsPreservingOrder(queue.items.map { it.mediaId })
        val mediaById = mediaFiles.associateBy { it.id }
        var controllerFuture: ListenableFuture<MediaController>? = null
        return try {
            controllerFuture = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, Resn8MediaService::class.java))
            ).buildAsync()
            val controller = controllerFuture.awaitWidgetFuture()
            val controllerIndex = controller.currentMediaItemIndex.takeIf { it >= 0 } ?: queue.currentIndex
            val currentQueueItemId = controller.currentMediaItem?.mediaId
                ?: queue.items.getOrNull(controllerIndex)?.queueItemId
            val currentMediaId = queue.items.firstOrNull { it.queueItemId == currentQueueItemId }?.mediaId
                ?: queue.items.getOrNull(controllerIndex)?.mediaId
            val artworkUri = currentMediaId
                ?.let(mediaById::get)
                ?.let { ArtworkCache(context).resolveEmbedded(it) ?: it.artworkUri }
            val artwork = artworkUri?.let { decodeWidgetArtwork(context, it) }

            buildPlaybackWidgetSnapshot(
                queue = queue,
                profile = collection.profile,
                mediaById = mediaById,
                playerState = PlaybackWidgetPlayerState(
                    currentQueueItemId = currentQueueItemId,
                    currentIndex = controllerIndex,
                    isPlaying = controller.isPlaying,
                    canPlayPause = controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
                    canSkipPrevious = controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM),
                    canSkipNext = controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM),
                    canRate = controller.isSessionCommandAvailable(LIKE_CURRENT_COMMAND) &&
                        controller.isSessionCommandAvailable(DISLIKE_CURRENT_COMMAND)
                ),
                artworkUri = artworkUri,
                artwork = artwork
            )
        } catch (error: Throwable) {
            PlaybackWidgetSnapshot(
                status = PlaybackWidgetStatus.ERROR,
                emptyDestination = emptyDestination,
                message = error.localizedMessage ?: "Playback controls are temporarily unavailable"
            )
        } finally {
            controllerFuture?.let(MediaController::releaseFuture)
        }
    }
}

private suspend fun decodeWidgetArtwork(context: Context, uriString: String): Bitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val uri = uriString.toUri()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (bounds.outWidth / sample > 256 || bounds.outHeight / sample > 256) sample *= 2
            val options = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrNull()
    }

internal suspend fun <T> ListenableFuture<T>.awaitWidgetFuture(): T =
    suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get())
            } catch (error: Throwable) {
                continuation.resumeWithException(error.cause ?: error)
            }
        }, MoreExecutors.directExecutor())
        continuation.invokeOnCancellation { cancel(true) }
    }
