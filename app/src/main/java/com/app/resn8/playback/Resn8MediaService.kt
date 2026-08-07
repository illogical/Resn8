package com.app.resn8.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.app.resn8.domain.model.RepeatMode
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.app.resn8.MainActivity
import com.app.resn8.Resn8Application
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.usecase.SavedQueueLoader
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

const val RESN8_QUEUE_ID = "resn8_queue_id"
const val RESN8_MEDIA_FILE_ID = "resn8_media_file_id"
const val RESN8_QUEUE_ITEM_ID = "resn8_queue_item_id"

class Resn8MediaService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var tracker: MeaningfulPlayTracker? = null
    private var checkpointCoordinator: CheckpointCoordinator? = null
    private var activeQueueId: String? = null
    private var tickerJob: Job? = null
    private var restorationJob: Job? = null
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

        if (container != null) {
            val coordinator = CheckpointCoordinator(
                scope = serviceScope,
                queueRepository = container.queueRepository,
                mediaRepository = container.mediaRepository
            )
            checkpointCoordinator = coordinator

            serviceScope.launch {
                container.uiSessionRepository.getUiSessionStateFlow().collect { session ->
                    val newActiveQueueId = session.activeQueueId
                    if (activeQueueId != newActiveQueueId) {
                        activeQueueId = newActiveQueueId
                        if (exoPlayer.mediaItemCount == 0 && !newActiveQueueId.isNullOrEmpty()) {
                            restorePlaybackContext(newActiveQueueId, container, playWhenReady = false)
                        }
                    }
                }
            }
        }

        val playerListener = object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val mediaId = mediaItem?.requestMetadata?.extras?.getString(RESN8_MEDIA_FILE_ID) ?: mediaItem?.mediaId
                playTracker.onMediaItemTransition(
                    mediaId = mediaId,
                    durationMs = exoPlayer.duration,
                    positionMs = exoPlayer.currentPosition,
                    previousItemCompleted = reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO ||
                        reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
                )
                checkpointCoordinator?.triggerCheckpoint(exoPlayer, playTracker, activeQueueId)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                playTracker.onPlaybackStateChanged(playbackState, exoPlayer.duration, exoPlayer.currentPosition)
                checkpointCoordinator?.triggerCheckpoint(exoPlayer, playTracker, activeQueueId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                playTracker.onIsPlayingChanged(isPlaying, exoPlayer.duration, exoPlayer.currentPosition)
                if (isPlaying) {
                    startTicker(playTracker, exoPlayer)
                    checkpointCoordinator?.startPeriodicCheckpoints(exoPlayer, playTracker, activeQueueId)
                } else {
                    stopTicker()
                    checkpointCoordinator?.stopPeriodicCheckpoints()
                    checkpointCoordinator?.triggerCheckpoint(exoPlayer, playTracker, activeQueueId)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                when (reason) {
                    Player.DISCONTINUITY_REASON_SEEK -> playTracker.onSeek(
                        oldPositionMs = oldPosition.positionMs,
                        newPositionMs = newPosition.positionMs
                    )
                    Player.DISCONTINUITY_REASON_AUTO_TRANSITION ->
                        playTracker.observePosition(oldPosition.positionMs)
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                checkpointCoordinator?.triggerCheckpoint(exoPlayer, playTracker, activeQueueId)
            }

            override fun onPlayerError(error: PlaybackException) {
                playTracker.onTick(exoPlayer.duration)
                checkpointCoordinator?.triggerCheckpoint(exoPlayer, playTracker, activeQueueId)
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

            @OptIn(UnstableApi::class)
            override fun onPlaybackResumption(
                mediaSession: MediaSession,
                controller: MediaSession.ControllerInfo
            ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
                if (container == null) {
                    return Futures.immediateFailedFuture(IllegalStateException("No container available"))
                }
                val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
                serviceScope.launch {
                    try {
                        val sessionState = container.uiSessionRepository.getUiSessionStateFlow().firstOrNull()
                        val qId = sessionState?.activeQueueId
                        if (qId.isNullOrEmpty()) {
                            future.setException(IllegalStateException("No active queue to resume"))
                            return@launch
                        }

                        val loader = SavedQueueLoader(container.queueRepository, container.mediaRepository)
                        val load = loader.loadSavedQueue(qId)
                        if (load == null || load.mediaItems.isEmpty()) {
                            future.setException(IllegalStateException("Active queue load failed"))
                            return@launch
                        }

                        val result = MediaSession.MediaItemsWithStartPosition(
                            load.mediaItems,
                            load.startIndex,
                            load.startPositionMs
                        )
                        future.set(result)
                    } catch (t: Throwable) {
                        future.setException(t)
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

    private fun restorePlaybackContext(
        queueId: String,
        container: AppContainer,
        playWhenReady: Boolean
    ) {
        restorationJob?.cancel()
        restorationJob = serviceScope.launch {
            val loader = SavedQueueLoader(container.queueRepository, container.mediaRepository)
            val load = loader.loadSavedQueue(queueId) ?: return@launch
            val exo = player ?: return@launch
            val playTracker = tracker ?: return@launch

            val activeSession = container.uiSessionRepository.getUiSessionStateFlow().firstOrNull()
            if (activeSession?.activeQueueId != queueId) {
                return@launch
            }

            if (exo.mediaItemCount > 0) return@launch

            exo.setMediaItems(load.mediaItems, load.startIndex, load.startPositionMs)
            exo.playbackParameters = exo.playbackParameters.withSpeed(load.savedQueue.playbackSpeed)
            exo.repeatMode = when (load.savedQueue.repeatMode) {
                RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            }

            val savedOccurrenceId = load.savedQueue.currentOccurrenceId
            val savedMediaId = load.savedQueue.currentMediaId
            if (!savedOccurrenceId.isNullOrEmpty() && !savedMediaId.isNullOrEmpty()) {
                val existingHistory = container.mediaRepository.getPlaybackHistoryByOccurrenceId(savedOccurrenceId)
                playTracker.hydrate(
                    occurrenceId = savedOccurrenceId,
                    mediaId = savedMediaId,
                    durationMs = load.startMediaFile?.durationMs ?: 0L,
                    accumulatedListenedMs = existingHistory?.accumulatedListenedDurationMs ?: 0L,
                    occurrenceStartedAtEpochMs = existingHistory?.startedAt ?: System.currentTimeMillis(),
                    hasCommitted = existingHistory?.countedAt != null,
                    currentPositionMs = load.startPositionMs
                )
            }

            exo.prepare()
            exo.playWhenReady = playWhenReady
        }
    }

    private fun startTicker(tracker: MeaningfulPlayTracker, exoPlayer: ExoPlayer) {
        stopTicker()
        tickerJob = serviceScope.launch {
            while (isActive) {
                tracker.onTick(exoPlayer.duration, exoPlayer.currentPosition)
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

        val queueId = mediaItems.firstNotNullOfOrNull { item ->
            item.requestMetadata.extras?.getString(RESN8_QUEUE_ID)
        }
        val isFlat = queueId?.let { id ->
            val queue = container.queueRepository.getQueueByIdFlow(id).firstOrNull()
            queue?.let { container.collectionRepository.getCollectionById(it.collectionId) }
        }?.profile == com.app.resn8.domain.model.CollectionProfile.FLAT

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
                    .setArtist(if (isFlat) null else mediaFile.artist ?: "Unknown Artist")
                    .setAlbumTitle(if (isFlat) null else mediaFile.album ?: "Unknown Album")
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

    override fun onTaskRemoved(rootIntent: Intent?) {
        val exo = player
        val trk = tracker
        val actQueueId = activeQueueId
        if (exo != null && trk != null && actQueueId != null) {
            checkpointCoordinator?.triggerCheckpoint(exo, trk, actQueueId, isPlayWhenReadyIntent = exo.playWhenReady)
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stopTicker()
        val exo = player
        val trk = tracker
        val actQueueId = activeQueueId
        if (exo != null && trk != null && actQueueId != null) {
            checkpointCoordinator?.triggerCheckpoint(exo, trk, actQueueId, isPlayWhenReadyIntent = exo.playWhenReady)
        }
        tracker?.resetState()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        player = null
        tracker = null
        checkpointCoordinator = null
        super.onDestroy()
    }
}
