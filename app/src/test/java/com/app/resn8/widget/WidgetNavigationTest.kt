package com.app.resn8.widget

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class WidgetNavigationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun navigationIntentRoundTripsEveryDestination() {
        WidgetDestination.entries.forEach { destination ->
            val intent = widgetNavigationIntent(context, destination)
            assertEquals(destination, intent.widgetDestinationOrNull())
        }
    }

    @Test
    fun invalidDestinationIsIgnored() {
        val intent = widgetNavigationIntent(context, WidgetDestination.FOLDERS)
            .putExtra(EXTRA_WIDGET_DESTINATION, "NOT_A_DESTINATION")

        assertNull(intent.widgetDestinationOrNull())
    }
}
