package com.app.resn8.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.app.resn8.R

class PlaybackWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlaybackWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        PlaybackWidgetUpdater.request(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            runCatching {
                AppWidgetManager.getInstance(context).setWidgetPreview(
                    ComponentName(context, PlaybackWidgetReceiver::class.java),
                    AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN,
                    RemoteViews(context.packageName, R.layout.widget_preview)
                )
            }
        }
    }
}
