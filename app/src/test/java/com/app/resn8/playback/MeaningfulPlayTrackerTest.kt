package com.app.resn8.playback

import androidx.media3.common.Player
import com.app.resn8.domain.model.PlaybackHistoryResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MeaningfulPlayTrackerTest {

    private var fakeMonotonicTimeMs: Long = 1000L
    private var fakeEpochTimeMs: Long = 1700000000000L

    private val qualifiedEvents = mutableListOf<QualifiedEvent>()

    data class QualifiedEvent(
        val sessionOccurrenceId: String,
        val mediaId: String,
        val startedAt: Long,
        val endedAt: Long,
        val accumulatedMs: Long,
        val result: PlaybackHistoryResult
    )

    private val tracker = MeaningfulPlayTracker(
        monotonicClock = { fakeMonotonicTimeMs },
        epochClock = { fakeEpochTimeMs },
        onQualifyMeaningfulPlay = { occId, mediaId, start, end, accum, res ->
            qualifiedEvents.add(QualifiedEvent(occId, mediaId, start, end, accum, res))
        }
    )

    @Test
    fun `accumulation advances only when playing and ready`() {
        tracker.onMediaItemTransition("track_1", durationMs = 120_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 120_000L)

        // Paused state by default: advancing time shouldn't accumulate
        fakeMonotonicTimeMs += 5000L
        tracker.onTick(120_000L)
        assertTrue(qualifiedEvents.isEmpty())

        // Start playing
        tracker.onIsPlayingChanged(playing = true, durationMs = 120_000L)
        fakeMonotonicTimeMs += 30_000L
        tracker.onTick(120_000L)

        // Now total active time is 30,000ms of the fixed 60,000ms threshold.
        assertTrue(qualifiedEvents.isEmpty())

        // Advance another 30,000ms -> total 60,000ms -> qualifies at threshold
        fakeMonotonicTimeMs += 30_000L
        tracker.onTick(120_000L)

        assertEquals(1, qualifiedEvents.size)
        val event = qualifiedEvents.first()
        assertEquals("track_1", event.mediaId)
        assertEquals(PlaybackHistoryResult.THRESHOLD_COUNTED, event.result)
        assertEquals(60_000L, event.accumulatedMs)
    }

    @Test
    fun `long tracks qualify at one minute`() {
        tracker.onMediaItemTransition("long_track", durationMs = 600_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 600_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 600_000L)

        fakeMonotonicTimeMs += 59_999L
        tracker.onTick(600_000L)
        assertTrue(qualifiedEvents.isEmpty())

        fakeMonotonicTimeMs += 1L
        tracker.onTick(600_000L)

        assertEquals(1, qualifiedEvents.size)
        assertEquals(PlaybackHistoryResult.THRESHOLD_COUNTED, qualifiedEvents.first().result)
    }

    @Test
    fun `exactly one minute track uses the one minute threshold`() {
        tracker.onMediaItemTransition("one_minute_track", durationMs = 60_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 60_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 60_000L)

        fakeMonotonicTimeMs += 60_000L
        tracker.onTick(durationMs = 60_000L, positionMs = 59_999L)

        assertEquals(1, qualifiedEvents.size)
        assertEquals(PlaybackHistoryResult.THRESHOLD_COUNTED, qualifiedEvents.first().result)
    }

    @Test
    fun `unknown duration qualifies at one minute`() {
        tracker.onMediaItemTransition("stream_track", durationMs = 0L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 0L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 0L)

        fakeMonotonicTimeMs += 59_999L
        tracker.onTick(0L)
        assertTrue(qualifiedEvents.isEmpty())

        fakeMonotonicTimeMs += 1L
        tracker.onTick(0L)

        assertEquals(1, qualifiedEvents.size)
        val event = qualifiedEvents.first()
        assertEquals(PlaybackHistoryResult.THRESHOLD_COUNTED, event.result)
        assertEquals(60_000L, event.accumulatedMs)
    }

    @Test
    fun `short track qualifies only when playback reaches the end`() {
        tracker.onMediaItemTransition("short_track", durationMs = 10_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 10_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 10_000L)

        fakeMonotonicTimeMs += 10_000L
        tracker.onPlaybackStateChanged(
            Player.STATE_ENDED,
            durationMs = 10_000L,
            positionMs = 10_000L
        )

        assertEquals(1, qualifiedEvents.size)
        assertEquals(PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED, qualifiedEvents.first().result)
    }

    @Test
    fun `short track cannot time qualify even after one minute of cumulative listening`() {
        tracker.onMediaItemTransition("short_track", durationMs = 10_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 10_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 10_000L)

        fakeMonotonicTimeMs += 60_000L
        tracker.onTick(durationMs = 10_000L, positionMs = 9_000L)

        assertTrue(qualifiedEvents.isEmpty())
    }

    @Test
    fun `seek directly to exact endpoint without playback does not qualify`() {
        tracker.onMediaItemTransition("track", durationMs = 120_000L)
        tracker.onSeek(oldPositionMs = 0L, newPositionMs = 120_000L)
        tracker.onPlaybackStateChanged(
            Player.STATE_ENDED,
            durationMs = 120_000L,
            positionMs = 120_000L
        )

        assertTrue(qualifiedEvents.isEmpty())
    }

    @Test
    fun `seeking near the end and playing through completion qualifies`() {
        tracker.onMediaItemTransition("track", durationMs = 120_000L)
        tracker.onSeek(oldPositionMs = 0L, newPositionMs = 119_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 120_000L, positionMs = 119_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 120_000L, positionMs = 119_000L)

        fakeMonotonicTimeMs += 1_000L
        tracker.onPlaybackStateChanged(
            Player.STATE_ENDED,
            durationMs = 120_000L,
            positionMs = 120_000L
        )

        assertEquals(1, qualifiedEvents.size)
        assertEquals(PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED, qualifiedEvents.first().result)
    }

    @Test
    fun `manual transition after partial playback does not qualify previous item`() {
        tracker.onMediaItemTransition("track_1", durationMs = 120_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 120_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 120_000L)
        fakeMonotonicTimeMs += 10_000L
        tracker.onTick(durationMs = 120_000L, positionMs = 10_000L)

        tracker.onMediaItemTransition(
            mediaId = "track_2",
            durationMs = 120_000L,
            previousItemCompleted = false
        )

        assertTrue(qualifiedEvents.isEmpty())
    }

    @Test
    fun `pause and seek preserve accumulated active listening without adding downtime`() {
        tracker.onMediaItemTransition("track", durationMs = 120_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 120_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 120_000L)
        fakeMonotonicTimeMs += 30_000L
        tracker.onTick(durationMs = 120_000L, positionMs = 30_000L)

        tracker.onIsPlayingChanged(playing = false, durationMs = 120_000L, positionMs = 30_000L)
        fakeMonotonicTimeMs += 20_000L
        tracker.onTick(durationMs = 120_000L, positionMs = 30_000L)
        tracker.onSeek(oldPositionMs = 30_000L, newPositionMs = 60_000L)

        tracker.onIsPlayingChanged(playing = true, durationMs = 120_000L, positionMs = 60_000L)
        fakeMonotonicTimeMs += 30_000L
        tracker.onTick(durationMs = 120_000L, positionMs = 90_000L)

        assertEquals(1, qualifiedEvents.size)
        assertEquals(60_000L, qualifiedEvents.first().accumulatedMs)
    }

    @Test
    fun `automatic or repeat transition qualifies a genuinely completed item once`() {
        tracker.onMediaItemTransition("track_1", durationMs = 120_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 120_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 120_000L)
        fakeMonotonicTimeMs += 10_000L
        tracker.onTick(durationMs = 120_000L, positionMs = 120_000L)

        tracker.onMediaItemTransition(
            mediaId = "track_2",
            durationMs = 120_000L,
            previousItemCompleted = true
        )

        assertEquals(1, qualifiedEvents.size)
        assertEquals(PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED, qualifiedEvents.first().result)
    }

    @Test
    fun `fresh session occurrence ID assigned on transition`() {
        tracker.onMediaItemTransition("t1", durationMs = 60_000L)
        val occ1 = tracker.currentOccurrenceId
        assertNotNull(occ1)

        tracker.onMediaItemTransition("t2", durationMs = 60_000L)
        val occ2 = tracker.currentOccurrenceId
        assertNotNull(occ2)
        assertNotEquals(occ1, occ2)

        // Re-entering t1 generates a fresh occurrence ID
        tracker.onMediaItemTransition("t1", durationMs = 60_000L)
        val occ3 = tracker.currentOccurrenceId
        assertNotNull(occ3)
        assertNotEquals(occ1, occ3)
        assertNotEquals(occ2, occ3)
    }

    @Test
    fun `hydrate restores in-progress occurrence state without double-counting downtime`() {
        // Hydrate with 40,000ms accumulated toward the fixed 60,000ms threshold.
        tracker.hydrate(
            occurrenceId = "occ_hydrated",
            mediaId = "track_hydrated",
            durationMs = 100_000L,
            accumulatedListenedMs = 40_000L,
            occurrenceStartedAtEpochMs = 1700000000000L,
            hasCommitted = false
        )

        assertEquals("occ_hydrated", tracker.currentOccurrenceId)
        assertEquals("track_hydrated", tracker.currentMediaId)
        assertEquals(40_000L, tracker.accumulatedListenedMs)

        // Advance 10,000ms offline/downtime while not playing -> should not accumulate
        fakeMonotonicTimeMs += 10_000L
        tracker.onTick(100_000L)
        assertEquals(40_000L, tracker.accumulatedListenedMs)
        assertTrue(qualifiedEvents.isEmpty())

        // Start playing and advance 20,000ms active listening -> total 60,000ms -> qualifies.
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 100_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 100_000L)
        fakeMonotonicTimeMs += 20_000L
        tracker.onTick(100_000L)

        assertEquals(1, qualifiedEvents.size)
        val event = qualifiedEvents.first()
        assertEquals("occ_hydrated", event.sessionOccurrenceId)
        assertEquals("track_hydrated", event.mediaId)
        assertEquals(60_000L, event.accumulatedMs)
    }
}
