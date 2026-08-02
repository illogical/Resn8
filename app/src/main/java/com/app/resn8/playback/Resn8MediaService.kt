package com.app.resn8.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.app.resn8.MainActivity
import com.app.resn8.Resn8Application
import com.app.resn8.di.AppContainer
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val RESN8_QUEUE_ID = "resn8_queue_id"
const val RESN8_MEDIA_FILE_ID = "resn8_media_file_id"
const val RESN8_QUEUE_ITEM_ID = "resn8_queue_item_id"

class Resn8MediaService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var tracker: MeaningfulPlayTracker? = null
    private var tickerJob: Job? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        val app = application as? Resn8Application
        val container = app?.container

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exoPlayer

        val playTracker = MeaningfulPlayTracker(
            monotonicClock = { android.os.SystemClock.elapsedRealtime() }
        ) { occurrenceId, mediaId, startedAt, endedAt, accumulatedMs, result ->
            if (container != null) {
                serviceScope.launch {
                    container.mediaRepository.commitMeaningfulPlay(
                        sessionOccurrenceId = occurrenceId,
                        mediaId = mediaId,
                        startedAt = startedAt,
                        endedAt = endedAt,
                        accumulatedListenedDurationMs = accumulatedMs,
                        result = result
                    )
                }
            }
        }
        tracker = playTracker

        val playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.requestMetadata?.extras?.getString(RESN8_MEDIA_FILE_ID) ?: mediaItem?.mediaId
                playTracker.onMediaItemTransition(mediaId, exoPlayer.duration)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playTracker.onPlaybackStateChanged(playbackState, exoPlayer.duration)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playTracker.onIsPlayingChanged(isPlaying, exoPlayer.duration)
                if (isPlaying) {
                    startTicker(playTracker, exoPlayer)
                } else {
                    stopTicker()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                playTracker.onTick(exoPlayer.duration)
            }
        }
        exoPlayer.addListener(playerListener)

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val callback = object : MediaSession.Callback {
            override fun onAddMediaItems(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo,
                mediaItems: List<MediaItem>
            ): ListenableFuture<List<MediaItem>> {
                val future = SettableFuture.create<List<MediaItem>>()
                serviceScope.launch {
                    try {
                        val resolved = resolveMediaItems(mediaItems, container)
                        future.set(resolved)
                    } catch (e: Throwable) {
                        future.setException(e)
                    }
                }
                return future
            }
        }

        mediaSession = MediaSession.Builder(this, exoPlayer)
            .setSessionActivity(sessionActivityPendingIntent)
            .setCallback(callback)
            .build()
    }

    private fun startTicker(tracker: MeaningfulPlayTracker, exoPlayer: ExoPlayer) {
        stopTicker()
        tickerJob = serviceScope.launch {
            while (isActive) {
                tracker.onTick(exoPlayer.duration)
                delay(500L)
            }
        }
    }

    private fun stopTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private suspend fun resolveMediaItems(
        mediaItems: List<MediaItem>,
        container: AppContainer?
    ): List<MediaItem> {
        if (container == null) return mediaItems

        val mediaFileIds = mediaItems.mapNotNull { item ->
            item.requestMetadata.extras?.getString(RESN8_MEDIA_FILE_ID) ?: item.mediaId
        }

        val mediaFiles = container.mediaRepository.getMediaFilesByIdsPreservingOrder(mediaFileIds)
        val fileMap = mediaFiles.associateBy { it.id }

        return mediaItems.map { item ->
            val fileId = item.requestMetadata.extras?.getString(RESN8_MEDIA_FILE_ID) ?: item.mediaId
            val mediaFile = fileMap[fileId]

            if (mediaFile != null) {
                val metadata = MediaMetadata.Builder()
                    .setTitle(mediaFile.displayTitle)
                    .setArtist(mediaFile.artist ?: "Unknown Artist")
                    .setAlbumTitle(mediaFile.album ?: "Unknown Album")
                    .setArtworkUri(mediaFile.artworkUri?.let { Uri.parse(it) })
                    .setTrackNumber(mediaFile.trackNumber)
                    .setDiscNumber(mediaFile.discNumber)
                    .build()

                item.buildUpon()
                    .setUri(Uri.parse(mediaFile.documentUri))
                    .setMediaMetadata(metadata)
                    .build()
            } else {
                item
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stopTicker()
        tracker?.resetState()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        tracker = null
        super.onDestroy()
    }
}
