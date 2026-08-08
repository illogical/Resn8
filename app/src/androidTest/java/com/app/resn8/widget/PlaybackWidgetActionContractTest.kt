package com.app.resn8.widget

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackWidgetActionContractTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun commandIntentsAreUniqueAndRoundTrip() {
        val play = PlaybackWidgetActionContract.commandIntent(
            context,
            PlaybackWidgetCommand.TOGGLE_PLAY_PAUSE
        )
        val next = PlaybackWidgetActionContract.commandIntent(context, PlaybackWidgetCommand.NEXT)

        assertNotEquals(play.data, next.data)
        assertEquals(
            PlaybackWidgetAction.Command(PlaybackWidgetCommand.TOGGLE_PLAY_PAUSE),
            PlaybackWidgetActionContract.parse(play)
        )
        assertEquals(
            PlaybackWidgetAction.Command(PlaybackWidgetCommand.NEXT),
            PlaybackWidgetActionContract.parse(next)
        )
    }

    @Test
    fun occurrenceJumpPreservesExactQueueItemIdentity() {
        val intent = PlaybackWidgetActionContract.jumpIntent(context, "duplicate-occurrence-2")

        assertEquals(
            PlaybackWidgetAction.Jump("duplicate-occurrence-2"),
            PlaybackWidgetActionContract.parse(intent)
        )
    }

    @Test
    fun malformedOrUnrelatedIntentsAreRejected() {
        assertNull(PlaybackWidgetActionContract.parse(Intent("unrelated")))
        assertNull(
            PlaybackWidgetActionContract.parse(
                PlaybackWidgetActionContract.commandIntent(context, PlaybackWidgetCommand.LIKE)
                    .apply { removeExtra("playback_command") }
            )
        )
    }
}
