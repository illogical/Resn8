package com.app.resn8.playback

import androidx.media3.common.C
import com.app.resn8.domain.model.PlaybackHistoryResult
import java.util.UUID

class MeaningfulPlayTracker(
    private val monotonicClock: () -> Long,
    private val epochClock: () -> Long = { System.currentTimeMillis() },
    private val onQualifyMeaningfulPlay: (
        sessionOccurrenceId: String,
        mediaId: String,
        startedAt: Long,
        endedAt: Long,
        accumulatedListenedDurationMs: Long,
        result: PlaybackHistoryResult
    ) -> Unit
) {
    var currentOccurrenceId: String? = null
        private set
    var currentMediaId: String? = null
        private set

    var currentDurationMs: Long = 0L
        private set
    var accumulatedListenedMs: Long = 0L
        private set
    var occurrenceStartedAtEpochMs: Long = 0L
        private set
    private var lastMonotonicTickMs: Long = 0L
    private var isPlaying: Boolean = false
    private var playbackState: Int = 1 // Player.STATE_IDLE
    var hasCommitted: Boolean = false
        private set

    fun hydrate(
        occurrenceId: String,
        mediaId: String,
        durationMs: Long,
        accumulatedListenedMs: Long,
        occurrenceStartedAtEpochMs: Long,
        hasCommitted: Boolean
    ) {
        this.currentOccurrenceId = occurrenceId
        this.currentMediaId = mediaId
        this.currentDurationMs = durationMs
        this.accumulatedListenedMs = accumulatedListenedMs
        this.occurrenceStartedAtEpochMs = occurrenceStartedAtEpochMs
        this.lastMonotonicTickMs = monotonicClock()
        this.hasCommitted = hasCommitted
    }

    fun onMediaItemTransition(mediaId: String?, durationMs: Long) {
        checkNaturalCompletion()

        if (mediaId == null) {
            resetState()
            return
        }

        currentOccurrenceId = UUID.randomUUID().toString()
        currentMediaId = mediaId
        currentDurationMs = durationMs
        accumulatedListenedMs = 0L
        occurrenceStartedAtEpochMs = epochClock()
        lastMonotonicTickMs = monotonicClock()
        hasCommitted = false
    }

    fun onPlaybackStateChanged(state: Int, durationMs: Long = currentDurationMs) {
        onTick(durationMs)
        playbackState = state
        if (state == 4) { // Player.STATE_ENDED == 4
            checkNaturalCompletion()
        }
    }

    fun onIsPlayingChanged(playing: Boolean, durationMs: Long = currentDurationMs) {
        onTick(durationMs)
        isPlaying = playing
        lastMonotonicTickMs = monotonicClock()
    }

    fun onTick(durationMs: Long = currentDurationMs) {
        val nowMonotonic = monotonicClock()
        if (isPlaying && playbackState == 3) { // Player.STATE_READY == 3
            val delta = (nowMonotonic - lastMonotonicTickMs).coerceAtLeast(0L)
            accumulatedListenedMs += delta
        }
        lastMonotonicTickMs = nowMonotonic
        currentDurationMs = durationMs

        checkThresholdQualification()
    }

    private fun checkThresholdQualification() {
        val occurrenceId = currentOccurrenceId ?: return
        val mediaId = currentMediaId ?: return
        if (hasCommitted) return

        val thresholdMs = if (currentDurationMs > 0L && currentDurationMs != C.TIME_UNSET) {
            minOf(currentDurationMs / 2, 240_000L)
        } else {
            240_000L
        }

        if (accumulatedListenedMs >= thresholdMs) {
            hasCommitted = true
            onQualifyMeaningfulPlay(
                occurrenceId,
                mediaId,
                occurrenceStartedAtEpochMs,
                epochClock(),
                accumulatedListenedMs,
                PlaybackHistoryResult.THRESHOLD_COUNTED
            )
        }
    }

    private fun checkNaturalCompletion() {
        val occurrenceId = currentOccurrenceId ?: return
        val mediaId = currentMediaId ?: return
        if (hasCommitted || accumulatedListenedMs <= 0L) return

        hasCommitted = true
        onQualifyMeaningfulPlay(
            occurrenceId,
            mediaId,
            occurrenceStartedAtEpochMs,
            epochClock(),
            accumulatedListenedMs,
            PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED
        )
    }

    fun resetState() {
        checkNaturalCompletion()
        currentOccurrenceId = null
        currentMediaId = null
        accumulatedListenedMs = 0L
        hasCommitted = false
        isPlaying = false
        playbackState = 1 // Player.STATE_IDLE
    }
}
