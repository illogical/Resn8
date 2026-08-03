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

        // Now total active time is 30,000ms (50% of 120,000ms is 60,000ms)
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
    fun `threshold is capped at 4 minutes for long tracks`() {
        // 10 minute track (600,000ms). 50% is 5 minutes, but threshold cap is 4 minutes (240,000ms)
        tracker.onMediaItemTransition("long_track", durationMs = 600_000L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 600_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 600_000L)

        fakeMonotonicTimeMs += 239_000L
        tracker.onTick(600_000L)
        assertTrue(qualifiedEvents.isEmpty())

        fakeMonotonicTimeMs += 2_000L
        tracker.onTick(600_000L)

        assertEquals(1, qualifiedEvents.size)
        assertEquals(PlaybackHistoryResult.THRESHOLD_COUNTED, qualifiedEvents.first().result)
    }

    @Test
    fun `unknown duration qualifies at 4 minutes or natural completion`() {
        tracker.onMediaItemTransition("stream_track", durationMs = 0L)
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 0L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 0L)

        fakeMonotonicTimeMs += 15_000L
        tracker.onTick(0L)
        assertTrue(qualifiedEvents.isEmpty())

        // Natural completion before 4 minutes
        tracker.onPlaybackStateChanged(Player.STATE_ENDED, durationMs = 0L)

        assertEquals(1, qualifiedEvents.size)
        val event = qualifiedEvents.first()
        assertEquals(PlaybackHistoryResult.NATURAL_COMPLETION_COUNTED, event.result)
        assertEquals(15_000L, event.accumulatedMs)
    }

    @Test
    fun `zero listening duration on natural completion does not qualify`() {
        tracker.onMediaItemTransition("short_track", durationMs = 10_000L)
        // Never played, seeked directly to end
        tracker.onPlaybackStateChanged(Player.STATE_ENDED, durationMs = 10_000L)

        assertTrue(qualifiedEvents.isEmpty())
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
        // Hydrate with 40,000ms accumulated out of 100,000ms threshold (50,000ms threshold)
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

        // Start playing and advance 10,000ms active listening -> total 50,000ms -> qualifies!
        tracker.onPlaybackStateChanged(Player.STATE_READY, durationMs = 100_000L)
        tracker.onIsPlayingChanged(playing = true, durationMs = 100_000L)
        fakeMonotonicTimeMs += 10_000L
        tracker.onTick(100_000L)

        assertEquals(1, qualifiedEvents.size)
        val event = qualifiedEvents.first()
        assertEquals("occ_hydrated", event.sessionOccurrenceId)
        assertEquals("track_hydrated", event.mediaId)
        assertEquals(50_000L, event.accumulatedMs)
    }
}
