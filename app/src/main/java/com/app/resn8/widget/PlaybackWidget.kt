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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionSendBroadcast
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
        setOf(COMPACT_SIZE, STANDARD_COMPACT_SIZE, EXPANDED_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = PlaybackWidgetStateLoader(context).load()
        provideContent {
            PlaybackWidgetContent(snapshot)
        }
    }

    companion object {
        val COMPACT_SIZE = DpSize(250.dp, 110.dp)
        val STANDARD_COMPACT_SIZE = DpSize(300.dp, 130.dp)
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
private val Surface: GlanceColorProvider
    @Composable get() = LocalWidgetColors.current.surface

@Composable
private fun PlaybackWidgetContent(snapshot: PlaybackWidgetSnapshot) {
    val widgetSize = LocalSize.current
    val isExpanded = widgetSize.height >= 180.dp
    val useLargeCompactControls = !isExpanded &&
        widgetSize.width >= PlaybackWidget.STANDARD_COMPACT_SIZE.width &&
        widgetSize.height >= PlaybackWidget.STANDARD_COMPACT_SIZE.height
    val colors = dynamicWidgetColors(LocalWidgetContext.current)
    CompositionLocalProvider(LocalWidgetColors provides colors) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .cornerRadius(24.dp)
                .background(colors.surface)
                .padding(
                    horizontal = if (useLargeCompactControls) 8.dp else 12.dp,
                    vertical = if (useLargeCompactControls) 8.dp else 12.dp
                )
        ) {
            when (snapshot.status) {
                PlaybackWidgetStatus.READY -> ReadyContent(
                    snapshot = snapshot,
                    expanded = isExpanded,
                    controlSize = if (useLargeCompactControls) 56.dp else 48.dp
                )
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
private fun ReadyContent(
    snapshot: PlaybackWidgetSnapshot,
    expanded: Boolean,
    controlSize: androidx.compose.ui.unit.Dp
) {
    if (expanded) {
        Column(modifier = GlanceModifier.fillMaxSize()) {
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
                TrackText(snapshot, GlanceModifier.fillMaxWidth(), centered = false)
            }
            TransportControls(snapshot, controlSize)
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
    } else {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .clickable(
                        actionStartActivity(
                            widgetNavigationIntent(LocalWidgetContext.current, WidgetDestination.NOW_PLAYING)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                TrackText(snapshot, GlanceModifier.fillMaxWidth(), centered = true)
            }
            TransportControls(snapshot, controlSize)
        }
    }
}

@Composable
private fun TrackText(snapshot: PlaybackWidgetSnapshot, modifier: GlanceModifier, centered: Boolean) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) {
            Alignment.Horizontal.CenterHorizontally
        } else {
            Alignment.Horizontal.Start
        }
    ) {
        Text(
            text = snapshot.title,
            style = TextStyle(
                color = PrimaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            ),
            maxLines = 1
        )
        Text(
            text = snapshot.secondaryText,
            style = TextStyle(
                color = SecondaryText,
                fontSize = 13.sp,
                textAlign = if (centered) TextAlign.Center else TextAlign.Start
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun TransportControls(snapshot: PlaybackWidgetSnapshot, controlSize: androidx.compose.ui.unit.Dp) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(controlSize),
        horizontalAlignment = Alignment.Horizontal.CenterHorizontally,
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        RatingControlIcon(
            drawable = R.drawable.ic_widget_dislike,
            description = ratingContentDescription("Dislike current track", snapshot.likeScore),
            enabled = snapshot.canRate,
            command = PlaybackWidgetCommand.DISLIKE,
            overlay = "",
            controlSize = controlSize
        )
        ControlIcon(
            R.drawable.ic_widget_previous,
            "Previous track",
            snapshot.canSkipPrevious,
            PlaybackWidgetCommand.PREVIOUS,
            controlSize
        )
        ControlIcon(
            if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            if (snapshot.isPlaying) "Pause" else "Play",
            snapshot.canPlayPause,
            PlaybackWidgetCommand.TOGGLE_PLAY_PAUSE,
            controlSize
        )
        ControlIcon(
            R.drawable.ic_widget_next,
            "Next track",
            snapshot.canSkipNext,
            PlaybackWidgetCommand.NEXT,
            controlSize
        )
        RatingControlIcon(
            drawable = R.drawable.ic_widget_like,
            description = ratingContentDescription("Like current track", snapshot.likeScore),
            enabled = snapshot.canRate,
            command = PlaybackWidgetCommand.LIKE,
            overlay = likeOverlayLabel(snapshot.likeScore),
            controlSize = controlSize
        )
    }
}

@Composable
private fun ControlIcon(
    drawable: Int,
    description: String,
    enabled: Boolean,
    command: PlaybackWidgetCommand,
    controlSize: androidx.compose.ui.unit.Dp
) {
    var modifier = GlanceModifier.size(controlSize)
    if (enabled) {
        modifier = modifier.clickable(
            actionSendBroadcast(
                PlaybackWidgetActionContract.commandIntent(LocalWidgetContext.current, command)
            )
        )
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(drawable),
            contentDescription = if (enabled) description else "$description unavailable",
            modifier = GlanceModifier.size(if (controlSize >= 56.dp) 32.dp else 28.dp),
            colorFilter = ColorFilter.tint(if (enabled) Accent else Disabled)
        )
    }
}

@Composable
private fun RatingControlIcon(
    drawable: Int,
    description: String,
    enabled: Boolean,
    command: PlaybackWidgetCommand,
    overlay: String,
    controlSize: androidx.compose.ui.unit.Dp
) {
    var modifier = GlanceModifier.size(controlSize)
    if (enabled) {
        modifier = modifier.clickable(
            actionSendBroadcast(
                PlaybackWidgetActionContract.commandIntent(LocalWidgetContext.current, command)
            )
        )
    }
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(drawable),
            contentDescription = if (enabled) description else "$description unavailable",
            modifier = GlanceModifier.size(if (controlSize >= 56.dp) 32.dp else 28.dp),
            colorFilter = ColorFilter.tint(if (enabled) Accent else Disabled)
        )
        if (overlay.isNotEmpty()) {
            Text(
                text = overlay,
                style = TextStyle(
                    color = Surface,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (overlay.length >= 3) 9.sp else 10.sp,
                    textAlign = TextAlign.Center
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun UpcomingRow(row: PlaybackWidgetQueueRow) {
    val text = if (row.secondaryText.isBlank()) row.title else "${row.title} · ${row.secondaryText}"
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(32.dp)
            .clickable(
                actionSendBroadcast(
                    PlaybackWidgetActionContract.jumpIntent(LocalWidgetContext.current, row.queueItemId)
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
            .clickable(
                actionSendBroadcast(
                    PlaybackWidgetActionContract.refreshIntent(LocalWidgetContext.current)
                )
            ),
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
