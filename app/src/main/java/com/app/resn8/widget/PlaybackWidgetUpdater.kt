package com.app.resn8.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

object PlaybackWidgetUpdater {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val pending = AtomicBoolean(false)

    fun request(context: Context) {
        if (!pending.compareAndSet(false, true)) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                delay(150L)
                updateAll(appContext)
            } finally {
                pending.set(false)
            }
        }
    }

    suspend fun updateAll(context: Context) {
        PlaybackWidget().updateAll(context.applicationContext)
    }
}
