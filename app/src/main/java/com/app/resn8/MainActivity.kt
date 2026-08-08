package com.app.resn8

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.app.resn8.ui.Resn8App
import com.app.resn8.ui.theme.Resn8Theme
import com.app.resn8.widget.WidgetDestination
import com.app.resn8.widget.widgetDestinationOrNull
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val widgetDestination = MutableStateFlow<WidgetDestination?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        widgetDestination.value = intent.widgetDestinationOrNull()
        enableEdgeToEdge()
        val appContainer = (application as Resn8Application).container
        setContent {
            val requestedDestination by widgetDestination.collectAsState()
            Resn8Theme {
                Resn8App(
                    container = appContainer,
                    widgetDestination = requestedDestination,
                    onWidgetDestinationConsumed = { widgetDestination.value = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        widgetDestination.value = intent.widgetDestinationOrNull()
    }
}
