package com.app.resn8.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.util.Locale

private val MinimumArtworkSize = 72.dp
private val MaximumArtworkSize = 300.dp

fun formatTimeMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}

@Composable
fun NowPlayingScreen(
    title: String = "No Track Playing",
    artist: String = "Unknown Artist",
    album: String = "",
    showMusicMetadata: Boolean = true,
    artworkUri: String? = null,
    likeScore: Int = 0,
    isPlaying: Boolean = false,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    isDurationUnknown: Boolean = false,
    canPlayPause: Boolean = true,
    canSeek: Boolean = true,
    canSkipPrevious: Boolean = true,
    canSkipNext: Boolean = true,
    noticeMessage: String? = null,
    onPlayPauseToggle: () -> Unit = {},
    onSeek: (Long) -> Unit = {},
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onLikeClicked: () -> Unit = {},
    onDislikeClicked: () -> Unit = {},
    onAddToPlaylistClicked: () -> Unit = {},
    onDismissNotice: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .testTag("now-playing-root")
    ) {
        val isShortWideLayout = maxWidth > maxHeight

        if (isShortWideLayout) {
            LandscapePlayerContent(
                title = title,
                artist = artist,
                album = album,
                showMusicMetadata = showMusicMetadata,
                artworkUri = artworkUri,
                likeScore = likeScore,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                isDurationUnknown = isDurationUnknown,
                canPlayPause = canPlayPause,
                canSeek = canSeek,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeek = onSeek,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onLikeClicked = onLikeClicked,
                onDislikeClicked = onDislikeClicked,
                onAddToPlaylistClicked = onAddToPlaylistClicked
            )
        } else {
            PortraitPlayerContent(
                title = title,
                artist = artist,
                album = album,
                showMusicMetadata = showMusicMetadata,
                artworkUri = artworkUri,
                likeScore = likeScore,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                isDurationUnknown = isDurationUnknown,
                canPlayPause = canPlayPause,
                canSeek = canSeek,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onPlayPauseToggle = onPlayPauseToggle,
                onSeek = onSeek,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext,
                onLikeClicked = onLikeClicked,
                onDislikeClicked = onDislikeClicked,
                onAddToPlaylistClicked = onAddToPlaylistClicked
            )
        }

        if (noticeMessage != null) {
            PlaybackNoticeOverlay(
                message = noticeMessage,
                onDismiss = onDismissNotice,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(8.dp)
            )
        }
    }
}

@Composable
private fun PortraitPlayerContent(
    title: String,
    artist: String,
    album: String,
    showMusicMetadata: Boolean,
    artworkUri: String?,
    likeScore: Int,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isDurationUnknown: Boolean,
    canPlayPause: Boolean,
    canSeek: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onLikeClicked: () -> Unit,
    onDislikeClicked: () -> Unit,
    onAddToPlaylistClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtworkRegion(
            title = title,
            artworkUri = artworkUri,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

        PlayerMetadata(
            title = title,
            artist = artist,
            album = album,
            showMusicMetadata = showMusicMetadata
        )
        Spacer(modifier = Modifier.height(4.dp))
        SeekControls(
            positionMs = positionMs,
            durationMs = durationMs,
            isDurationUnknown = isDurationUnknown,
            canSeek = canSeek,
            onSeek = onSeek
        )
        Spacer(modifier = Modifier.height(4.dp))
        TransportControls(
            isPlaying = isPlaying,
            canPlayPause = canPlayPause,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            onPlayPauseToggle = onPlayPauseToggle,
            onSkipPrevious = onSkipPrevious,
            onSkipNext = onSkipNext
        )
        Spacer(modifier = Modifier.height(4.dp))
        RatingControls(
            likeScore = likeScore,
            onLikeClicked = onLikeClicked,
            onDislikeClicked = onDislikeClicked,
            onAddToPlaylistClicked = onAddToPlaylistClicked
        )
    }
}

@Composable
private fun LandscapePlayerContent(
    title: String,
    artist: String,
    album: String,
    showMusicMetadata: Boolean,
    artworkUri: String?,
    likeScore: Int,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    isDurationUnknown: Boolean,
    canPlayPause: Boolean,
    canSeek: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeek: (Long) -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onLikeClicked: () -> Unit,
    onDislikeClicked: () -> Unit,
    onAddToPlaylistClicked: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(0.44f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ArtworkRegion(
                title = title,
                artworkUri = artworkUri,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
            PlayerMetadata(
                title = title,
                artist = artist,
                album = album,
                showMusicMetadata = showMusicMetadata
            )
        }

        Column(
            modifier = Modifier
                .weight(0.56f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            SeekControls(
                positionMs = positionMs,
                durationMs = durationMs,
                isDurationUnknown = isDurationUnknown,
                canSeek = canSeek,
                onSeek = onSeek
            )
            Spacer(modifier = Modifier.height(4.dp))
            TransportControls(
                isPlaying = isPlaying,
                canPlayPause = canPlayPause,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                onPlayPauseToggle = onPlayPauseToggle,
                onSkipPrevious = onSkipPrevious,
                onSkipNext = onSkipNext
            )
            Spacer(modifier = Modifier.height(4.dp))
            RatingControls(
                likeScore = likeScore,
                onLikeClicked = onLikeClicked,
                onDislikeClicked = onDislikeClicked,
                onAddToPlaylistClicked = onAddToPlaylistClicked
            )
        }
    }
}

@Composable
private fun ArtworkRegion(
    title: String,
    artworkUri: String?,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        val artworkSize = minOf(maxWidth, maxHeight, MaximumArtworkSize)
        if (artworkSize >= MinimumArtworkSize) {
            Artwork(
                title = title,
                artworkUri = artworkUri,
                size = artworkSize
            )
        }
    }
}

@Composable
private fun Artwork(
    title: String,
    artworkUri: String?,
    size: Dp
) {
    Surface(
        modifier = Modifier
            .size(size)
            .testTag("now-playing-artwork")
            .clip(RoundedCornerShape(16.dp)),
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (!artworkUri.isNullOrBlank()) {
                AsyncImage(
                    model = artworkUri,
                    contentDescription = "Artwork for ${title.ifEmpty { "current track" }}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = "Artwork unavailable",
                    modifier = Modifier.size((size * 0.32f).coerceIn(32.dp, 96.dp)),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PlayerMetadata(
    title: String,
    artist: String,
    album: String,
    showMusicMetadata: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = title.ifEmpty { "No Track Playing" },
            style = if (showMusicMetadata) {
                MaterialTheme.typography.headlineSmall
            } else {
                MaterialTheme.typography.titleMedium
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("now-playing-title")
        )
        if (showMusicMetadata && artist.isNotBlank()) {
            Text(
                text = artist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (showMusicMetadata && album.isNotBlank()) {
            Text(
                text = album,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SeekControls(
    positionMs: Long,
    durationMs: Long,
    isDurationUnknown: Boolean,
    canSeek: Boolean,
    onSeek: (Long) -> Unit
) {
    var isDraggingSlider by remember { mutableStateOf(false) }
    var dragPositionMs by remember { mutableFloatStateOf(0f) }
    val currentSliderValue = if (isDraggingSlider) dragPositionMs else positionMs.toFloat()
    val maxSliderValue = if (durationMs > 0L) durationMs.toFloat() else 1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = currentSliderValue.coerceIn(0f, maxSliderValue),
            onValueChange = {
                isDraggingSlider = true
                dragPositionMs = it
            },
            onValueChangeFinished = {
                isDraggingSlider = false
                onSeek(dragPositionMs.toLong())
            },
            valueRange = 0f..maxSliderValue,
            enabled = canSeek && !isDurationUnknown && durationMs > 0L,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Playback position" }
                .testTag("playback-position")
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTimeMs(if (isDraggingSlider) dragPositionMs.toLong() else positionMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (isDurationUnknown) "--:--" else formatTimeMs(durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    canPlayPause: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onSkipPrevious,
            enabled = canSkipPrevious,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous Track",
                modifier = Modifier.size(32.dp)
            )
        }

        IconButton(
            onClick = onPlayPauseToggle,
            enabled = canPlayPause,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            ),
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(40.dp)
            )
        }

        IconButton(
            onClick = onSkipNext,
            enabled = canSkipNext,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next Track",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun RatingControls(
    likeScore: Int,
    onLikeClicked: () -> Unit,
    onDislikeClicked: () -> Unit,
    onAddToPlaylistClicked: () -> Unit
) {
    val isLiked = likeScore > 0
    val isDisliked = likeScore < 0

    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDislikeClicked,
            colors = if (isDisliked) {
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            } else {
                IconButtonDefaults.iconButtonColors()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                contentDescription = "Dislike"
            )
        }

        Text(
            text = if (likeScore > 0) "+$likeScore" else "$likeScore",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(40.dp),
            textAlign = TextAlign.Center
        )

        IconButton(
            onClick = onLikeClicked,
            colors = if (isLiked) {
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            } else {
                IconButtonDefaults.iconButtonColors()
            },
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                contentDescription = "Like"
            )
        }

        IconButton(
            onClick = onAddToPlaylistClicked,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                contentDescription = "Add to Playlist"
            )
        }
    }
}

@Composable
private fun PlaybackNoticeOverlay(
    message: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        }
    }
}
