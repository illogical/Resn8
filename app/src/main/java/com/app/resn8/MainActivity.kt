package com.app.resn8

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.app.resn8.ui.Resn8App
import com.app.resn8.ui.theme.Resn8Theme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appContainer = (application as Resn8Application).container
        setContent {
            Resn8Theme {
                Resn8App(container = appContainer)
            }
        }
    }
}