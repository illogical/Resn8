package com.app.resn8.widget

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.app.resn8.playback.DISLIKE_CURRENT_COMMAND
import com.app.resn8.playback.LIKE_CURRENT_COMMAND
import com.app.resn8.playback.Resn8MediaService
import com.google.common.util.concurrent.ListenableFuture

internal enum class PlaybackWidgetCommand {
    TOGGLE_PLAY_PAUSE,
    PREVIOUS,
    NEXT,
    LIKE,
    DISLIKE
}

internal class PlaybackWidgetController(private val context: Context) {
    suspend fun execute(command: PlaybackWidgetCommand): Boolean {
        var controllerFuture: ListenableFuture<MediaController>? = null
        return try {
            controllerFuture = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, Resn8MediaService::class.java))
            ).buildAsync()
            val controller = controllerFuture.awaitWidgetFuture()
            when (command) {
                PlaybackWidgetCommand.TOGGLE_PLAY_PAUSE -> {
                    if (!controller.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return false
                    if (controller.isPlaying) controller.pause() else controller.play()
                    true
                }
                PlaybackWidgetCommand.PREVIOUS -> {
                    if (!controller.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM) ||
                        !controller.hasPreviousMediaItem()
                    ) return false
                    controller.seekToPreviousMediaItem()
                    true
                }
                PlaybackWidgetCommand.NEXT -> {
                    if (!controller.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM) ||
                        !controller.hasNextMediaItem()
                    ) return false
                    controller.seekToNextMediaItem()
                    true
                }
                PlaybackWidgetCommand.LIKE,
                PlaybackWidgetCommand.DISLIKE -> {
                    val sessionCommand = if (command == PlaybackWidgetCommand.LIKE) {
                        LIKE_CURRENT_COMMAND
                    } else {
                        DISLIKE_CURRENT_COMMAND
                    }
                    if (!controller.isSessionCommandAvailable(sessionCommand)) return false
                    controller.sendCustomCommand(sessionCommand, Bundle.EMPTY)
                        .awaitWidgetFuture()
                        .resultCode == SessionResult.RESULT_SUCCESS
                }
            }
        } catch (_: Throwable) {
            false
        } finally {
            controllerFuture?.let(MediaController::releaseFuture)
        }
    }

    suspend fun jumpTo(queueItemId: String): Boolean {
        var controllerFuture: ListenableFuture<MediaController>? = null
        return try {
            controllerFuture = MediaController.Builder(
                context,
                SessionToken(context, ComponentName(context, Resn8MediaService::class.java))
            ).buildAsync()
            val controller = controllerFuture.awaitWidgetFuture()
            if (!controller.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) return false
            val index = (0 until controller.mediaItemCount).firstOrNull { index ->
                controller.getMediaItemAt(index).mediaId == queueItemId
            } ?: return false
            controller.seekTo(index, 0L)
            controller.prepare()
            controller.play()
            true
        } catch (_: Throwable) {
            false
        } finally {
            controllerFuture?.let(MediaController::releaseFuture)
        }
    }
}
