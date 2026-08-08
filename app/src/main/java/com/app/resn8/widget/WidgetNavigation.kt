package com.app.resn8.widget

import android.content.Context
import android.content.Intent
import com.app.resn8.MainActivity

enum class WidgetDestination {
    NOW_PLAYING,
    PLAYLISTS,
    FOLDERS,
    ONBOARDING
}

const val EXTRA_WIDGET_DESTINATION = "com.app.resn8.extra.WIDGET_DESTINATION"

fun widgetNavigationIntent(context: Context, destination: WidgetDestination): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = "com.app.resn8.action.OPEN_FROM_WIDGET"
        putExtra(EXTRA_WIDGET_DESTINATION, destination.name)
        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

fun Intent.widgetDestinationOrNull(): WidgetDestination? =
    getStringExtra(EXTRA_WIDGET_DESTINATION)?.let { value ->
        runCatching { WidgetDestination.valueOf(value) }.getOrNull()
    }
