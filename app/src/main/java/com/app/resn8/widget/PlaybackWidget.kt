package com.app.resn8.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider as GlanceColorProvider
import com.app.resn8.R

class PlaybackWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlaybackWidgetStateLoader(context).load()
        provideContent {
            PlaybackWidgetContent(snapshot)
        }
    }

    companion object {
        val COMPACT_SIZE = DpSize(250.dp, 110.dp)
        val EXPANDED_SIZE = DpSize(250.dp, 250.dp)
    }
}

private data class WidgetColors(
    val primaryText: GlanceColorProvider,
    val secondaryText: GlanceColorProvider,
    val accent: GlanceColorProvider,
    val disabled: GlanceColorProvider,
    val surface: GlanceColorProvider
)

private val FallbackWidgetColors = WidgetColors(
    primaryText = ColorProvider(Color(0xFF1D1B20), Color(0xFFE6E1E5)),
    secondaryText = ColorProvider(Color(0xFF49454F), Color(0xFFCAC4D0)),
    accent = ColorProvider(Color(0xFF6750A4), Color(0xFFD0BCFF)),
    disabled = ColorProvider(Color(0xFF79747E), Color(0xFF938F99)),
    surface = ColorProvider(Color(0xFFE8DEF8), Color(0xFF332D41))
)

private val LocalWidgetColors = staticCompositionLocalOf { FallbackWidgetColors }

private val PrimaryText: GlanceColorProvider
    @Composable get() = LocalWidgetColors.current.primaryText
private val SecondaryText: GlanceColorProvider
    @Composable get() = LocalWidgetColors.current.secondaryText
private val Accent: GlanceColorProvider
    @Composable get() = LocalWidgetColors.current.accent
private val Disabled: GlanceColorProvider
    @Composable get() = LocalWidgetColors.current.disabled

@Composable
private fun PlaybackWidgetContent(snapshot: PlaybackWidgetSnapshot) {
    val isExpanded = LocalSize.current.height >= 180.dp
    val colors = dynamicWidgetColors(LocalWidgetContext.current)
    CompositionLocalProvider(LocalWidgetColors provides colors) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(colors.surface)
                .padding(12.dp)
        ) {
            when (snapshot.status) {
                PlaybackWidgetStatus.READY -> ReadyContent(snapshot, isExpanded)
                PlaybackWidgetStatus.EMPTY -> EmptyContent(snapshot)
                PlaybackWidgetStatus.ERROR -> ErrorContent(snapshot)
            }
        }
    }
}

private fun dynamicWidgetColors(context: Context): WidgetColors {
    val isNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
        Configuration.UI_MODE_NIGHT_YES
    fun colorProvider(resourceId: Int): GlanceColorProvider {
        val color = Color(context.getColor(resourceId))
        return ColorProvider(color, color)
    }
    return WidgetColors(
        primaryText = colorProvider(if (isNight) android.R.color.system_neutral1_50 else android.R.color.system_neutral1_900),
        secondaryText = colorProvider(if (isNight) android.R.color.system_neutral2_200 else android.R.color.system_neutral2_700),
        accent = colorProvider(if (isNight) android.R.color.system_accent1_200 else android.R.color.system_accent1_700),
        disabled = colorProvider(if (isNight) android.R.color.system_neutral2_500 else android.R.color.system_neutral2_500),
        surface = colorProvider(if (isNight) android.R.color.system_accent1_900 else android.R.color.system_accent1_100)
    )
}

@Composable
private fun ReadyContent(snapshot: PlaybackWidgetSnapshot, expanded: Boolean) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        if (expanded) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(widgetNavigationIntent(LocalWidgetContext.current, WidgetDestination.NOW_PLAYING))),
                verticalAlignment = Alignment.Vertical.CenterVertically
            ) {
                if (snapshot.artwork != null) {
                    Image(
                        provider = ImageProvider(snapshot.artwork),
                        contentDescription = "Artwork for ${snapshot.title}",
                        contentScale = ContentScale.Crop,
                        modifier = GlanceModifier.size(64.dp).cornerRadius(12.dp)
                    )
                } else {
                    Image(
                        provider = ImageProvider(R.drawable.ic_widget_music),
                        contentDescription = "No artwork",
                        modifier = GlanceModifier.size(64.dp).padding(16.dp),
                        colorFilter = ColorFilter.tint(Accent)
                    )
                }
                Spacer(GlanceModifier.width(10.dp))
                TrackText(snapshot, GlanceModifier.fillMaxWidth())
            }
        } else {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(actionStartActivity(widgetNavigationIntent(LocalWidgetContext.current, WidgetDestination.NOW_PLAYING)))
            ) {
                TrackText(snapshot, GlanceModifier.fillMaxWidth())
            }
        }

        TransportControls(snapshot)

        if (expanded) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = "Up next",
                style = TextStyle(color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 13.sp),
                maxLines = 1
            )
            if (snapshot.upcoming.isEmpty()) {
                Text(
                    text = "End of queue",
                    style = TextStyle(color = SecondaryText, fontSize = 12.sp),
                    maxLines = 1
                )
            } else {
                snapshot.upcoming.forEach { row -> UpcomingRow(row) }
            }
        }
    }
}

@Composable
private fun TrackText(snapshot: PlaybackWidgetSnapshot, modifier: GlanceModifier) {
    Column(modifier = modifier) {
        Text(
            text = snapshot.title,
            style = TextStyle(color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 17.sp),
            maxLines = 1
        )
        val detail = listOf(snapshot.secondaryText, ratingLabel(snapshot.likeScore))
            .filter { it.isNotBlank() }
            .joinToString(" · ")
        Text(
            text = detail,
            style = TextStyle(color = SecondaryText, fontSize = 13.sp),
            maxLines = 1
        )
    }
}

@Composable
private fun TransportControls(snapshot: PlaybackWidgetSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(48.dp),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        ControlIcon(R.drawable.ic_widget_dislike, "Dislike current track", snapshot.canRate, PlaybackWidgetCommand.DISLIKE)
        ControlIcon(R.drawable.ic_widget_previous, "Previous track", snapshot.canSkipPrevious, PlaybackWidgetCommand.PREVIOUS)
        ControlIcon(
            if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            if (snapshot.isPlaying) "Pause" else "Play",
            snapshot.canPlayPause,
            PlaybackWidgetCommand.TOGGLE_PLAY_PAUSE
        )
        ControlIcon(R.drawable.ic_widget_next, "Next track", snapshot.canSkipNext, PlaybackWidgetCommand.NEXT)
        ControlIcon(R.drawable.ic_widget_like, "Like current track", snapshot.canRate, PlaybackWidgetCommand.LIKE)
    }
}

@Composable
private fun ControlIcon(
    drawable: Int,
    description: String,
    enabled: Boolean,
    command: PlaybackWidgetCommand
) {
    var modifier = GlanceModifier.size(48.dp).padding(12.dp)
    if (enabled) {
        modifier = modifier.clickable(
            actionRunCallback<PlaybackWidgetCommandAction>(
                actionParametersOf(CommandKey to command.name)
            )
        )
    }
    Image(
        provider = ImageProvider(drawable),
        contentDescription = if (enabled) description else "$description unavailable",
        modifier = modifier,
        colorFilter = ColorFilter.tint(if (enabled) Accent else Disabled)
    )
}

@Composable
private fun UpcomingRow(row: PlaybackWidgetQueueRow) {
    val text = if (row.secondaryText.isBlank()) row.title else "${row.title} · ${row.secondaryText}"
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(
                actionRunCallback<PlaybackWidgetJumpAction>(
                    actionParametersOf(QueueItemKey to row.queueItemId)
                )
            ),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            style = TextStyle(color = PrimaryText, fontSize = 12.sp),
            maxLines = 1
        )
    }
}

@Composable
private fun EmptyContent(snapshot: PlaybackWidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity(widgetNavigationIntent(LocalWidgetContext.current, snapshot.emptyDestination))),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_music),
            contentDescription = null,
            modifier = GlanceModifier.size(40.dp).padding(8.dp),
            colorFilter = ColorFilter.tint(Accent)
        )
        Text(
            text = snapshot.message ?: "Choose something to play",
            style = TextStyle(color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center),
            maxLines = 2
        )
        Text(
            text = when (snapshot.emptyDestination) {
                WidgetDestination.PLAYLISTS -> "Open Playlists"
                WidgetDestination.FOLDERS -> "Open Folders"
                WidgetDestination.ONBOARDING -> "Set up Resn8"
                WidgetDestination.NOW_PLAYING -> "Open Resn8"
            },
            style = TextStyle(color = SecondaryText, fontSize = 12.sp, textAlign = TextAlign.Center),
            maxLines = 1
        )
    }
}

@Composable
private fun ErrorContent(snapshot: PlaybackWidgetSnapshot) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RefreshPlaybackWidgetAction>()),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = "Playback unavailable",
            style = TextStyle(color = PrimaryText, fontWeight = FontWeight.Bold, fontSize = 15.sp, textAlign = TextAlign.Center),
            maxLines = 1
        )
        Text(
            text = snapshot.message ?: "Tap to retry",
            style = TextStyle(color = SecondaryText, fontSize = 12.sp, textAlign = TextAlign.Center),
            maxLines = 2
        )
    }
}

private val CommandKey = ActionParameters.Key<String>("playback_command")
private val QueueItemKey = ActionParameters.Key<String>("queue_item_id")

class PlaybackWidgetCommandAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val command = parameters[CommandKey]?.let { runCatching { PlaybackWidgetCommand.valueOf(it) }.getOrNull() }
            ?: return
        PlaybackWidgetController(context).execute(command)
        PlaybackWidgetUpdater.updateAll(context)
    }
}

class PlaybackWidgetJumpAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val queueItemId = parameters[QueueItemKey] ?: return
        PlaybackWidgetController(context).jumpTo(queueItemId)
        PlaybackWidgetUpdater.updateAll(context)
    }
}

class RefreshPlaybackWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        PlaybackWidgetUpdater.updateAll(context)
    }
}
