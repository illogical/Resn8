package com.app.resn8.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NowPlayingScreen(
    title: String = "No Track Playing",
    artist: String = "Unknown Artist",
    likeScore: Int = 0,
    isPlaying: Boolean = false,
    onPlayPauseToggle: () -> Unit = {},
    onLikeClicked: () -> Unit = {},
    onDislikeClicked: () -> Unit = {},
    onQueueClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = artist,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onDislikeClicked) {
                Text("-1 Dislike")
            }
            Text(
                text = "Score: $likeScore",
                style = MaterialTheme.typography.titleLarge
            )
            OutlinedButton(onClick = onLikeClicked) {
                Text("+1 Like")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(onClick = onPlayPauseToggle) {
                Text(if (isPlaying) "Pause" else "Play")
            }
            OutlinedButton(onClick = onQueueClicked) {
                Text("View Queue")
            }
        }
    }
}
