package com.app.resn8.playback

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.QueueRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong

class CheckpointCoordinator(
    private val scope: CoroutineScope,
    private val queueRepository: QueueRepository,
    private val mediaRepository: MediaRepository
) {
    private val latestRevision = AtomicLong(0L)
    private var periodicJob: Job? = null
    private val writeMutex = Mutex()
    var lastError: Throwable? = null
        private set

    fun startPeriodicCheckpoints(player: ExoPlayer, tracker: MeaningfulPlayTracker, activeQueueId: String?) {
        stopPeriodicCheckpoints()
        periodicJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                delay(5000L)
                if (player.isPlaying) {
                    triggerCheckpoint(player, tracker, activeQueueId, isPlayWhenReadyIntent = true)
                }
            }
        }
    }

    fun stopPeriodicCheckpoints() {
        periodicJob?.cancel()
        periodicJob = null
    }

    fun triggerCheckpoint(
        player: ExoPlayer,
        tracker: MeaningfulPlayTracker,
        activeQueueId: String?,
        isPlayWhenReadyIntent: Boolean = player.playWhenReady
    ) {
        if (activeQueueId.isNullOrEmpty()) return
        val currentMediaItem = player.currentMediaItem ?: return

        val queueItemId = currentMediaItem.mediaId
        val mediaId = currentMediaItem.requestMetadata.extras?.getString(RESN8_MEDIA_FILE_ID) ?: queueItemId
        val currentIndex = player.currentMediaItemIndex
        val pos = player.currentPosition.coerceAtLeast(0L)
        val speed = player.playbackParameters.speed
        val repeat = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> RepeatMode.ONE
            Player.REPEAT_MODE_ALL -> RepeatMode.ALL
            else -> RepeatMode.OFF
        }

        val occurrenceId = tracker.currentOccurrenceId
        val accumulatedMs = tracker.accumulatedListenedMs
        val startedAt = tracker.occurrenceStartedAtEpochMs
        val hasCommitted = tracker.hasCommitted

        val reqRevision = latestRevision.incrementAndGet()

        scope.launch(Dispatchers.IO) {
            writeMutex.withLock {
                if (reqRevision < latestRevision.get()) {
                    return@withLock
                }

                try {
                    queueRepository.updatePlaybackCheckpoint(
                        queueId = activeQueueId,
                        currentIndex = currentIndex,
                        currentMediaId = mediaId,
                        currentOccurrenceId = occurrenceId,
                        positionMs = pos,
                        playWhenReadyIntent = isPlayWhenReadyIntent,
                        playbackSpeed = speed,
                        repeatMode = repeat
                    )

                    if (!occurrenceId.isNullOrEmpty() && !mediaId.isNullOrEmpty() && !hasCommitted) {
                        mediaRepository.commitMeaningfulPlay(
                            sessionOccurrenceId = occurrenceId,
                            mediaId = mediaId,
                            startedAt = startedAt,
                            endedAt = null,
                            accumulatedListenedDurationMs = accumulatedMs,
                            result = PlaybackHistoryResult.IN_PROGRESS
                        )
                    }
                    lastError = null
                } catch (t: Throwable) {
                    lastError = t
                }
            }
        }
    }
}
