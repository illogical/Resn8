package com.app.resn8.playback

import androidx.media3.common.C
import androidx.media3.common.Player
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
    companion object {
        const val ONE_MINUTE_MS = 60_000L
    }

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
    private var lastObservedPositionMs: Long = 0L
    private var hasPlaybackAdvancedSinceEntryOrSeek: Boolean = false
    var hasCommitted: Boolean = false
        private set

    fun hydrate(
        occurrenceId: String,
        mediaId: String,
        durationMs: Long,
        accumulatedListenedMs: Long,
        occurrenceStartedAtEpochMs: Long,
        hasCommitted: Boolean,
        currentPositionMs: Long = 0L
    ) {
        this.currentOccurrenceId = occurrenceId
        this.currentMediaId = mediaId
        this.currentDurationMs = durationMs
        this.accumulatedListenedMs = accumulatedListenedMs
        this.occurrenceStartedAtEpochMs = occurrenceStartedAtEpochMs
        this.lastMonotonicTickMs = monotonicClock()
        this.hasCommitted = hasCommitted
        this.lastObservedPositionMs = currentPositionMs
        this.hasPlaybackAdvancedSinceEntryOrSeek = false
    }

    fun onMediaItemTransition(
        mediaId: String?,
        durationMs: Long,
        positionMs: Long = 0L,
        previousItemCompleted: Boolean = false
    ) {
        if (previousItemCompleted) {
            checkNaturalCompletion()
        }

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
        lastObservedPositionMs = positionMs
        hasPlaybackAdvancedSinceEntryOrSeek = false
    }

    fun onPlaybackStateChanged(
        state: Int,
        durationMs: Long = currentDurationMs,
        positionMs: Long = lastObservedPositionMs
    ) {
        onTick(durationMs, positionMs)
        playbackState = state
        if (state == Player.STATE_ENDED) {
            checkNaturalCompletion()
        }
    }

    fun onIsPlayingChanged(
        playing: Boolean,
        durationMs: Long = currentDurationMs,
        positionMs: Long = lastObservedPositionMs
    ) {
        onTick(durationMs, positionMs)
        isPlaying = playing
        lastMonotonicTickMs = monotonicClock()
    }

    fun onTick(
        durationMs: Long = currentDurationMs,
        positionMs: Long = lastObservedPositionMs
    ) {
        val nowMonotonic = monotonicClock()
        if (isPlaying && playbackState == Player.STATE_READY) {
            val delta = (nowMonotonic - lastMonotonicTickMs).coerceAtLeast(0L)
            accumulatedListenedMs += delta
        }
        observePosition(positionMs)
        lastMonotonicTickMs = nowMonotonic
        currentDurationMs = durationMs

        checkThresholdQualification()
    }

    fun onSeek(oldPositionMs: Long, newPositionMs: Long) {
        onTick(currentDurationMs, oldPositionMs)
        hasPlaybackAdvancedSinceEntryOrSeek = false
        lastObservedPositionMs = newPositionMs
        lastMonotonicTickMs = monotonicClock()
    }

    fun observePosition(positionMs: Long) {
        if (positionMs == C.TIME_UNSET || positionMs < 0L) return
        if (positionMs > lastObservedPositionMs) {
            hasPlaybackAdvancedSinceEntryOrSeek = true
        }
        lastObservedPositionMs = positionMs
    }

    private fun checkThresholdQualification() {
        val occurrenceId = currentOccurrenceId ?: return
        val mediaId = currentMediaId ?: return
        if (hasCommitted) return

        val hasKnownDuration = currentDurationMs > 0L && currentDurationMs != C.TIME_UNSET
        if (hasKnownDuration && currentDurationMs < ONE_MINUTE_MS) return

        if (accumulatedListenedMs >= ONE_MINUTE_MS) {
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
        if (hasCommitted || !hasPlaybackAdvancedSinceEntryOrSeek) return

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
        currentOccurrenceId = null
        currentMediaId = null
        accumulatedListenedMs = 0L
        hasCommitted = false
        isPlaying = false
        playbackState = 1 // Player.STATE_IDLE
        lastObservedPositionMs = 0L
        hasPlaybackAdvancedSinceEntryOrSeek = false
    }
}
