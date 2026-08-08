package com.app.resn8.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal sealed interface PlaybackWidgetAction {
    data class Command(val command: PlaybackWidgetCommand) : PlaybackWidgetAction
    data class Jump(val queueItemId: String) : PlaybackWidgetAction
    data object Refresh : PlaybackWidgetAction
}

internal object PlaybackWidgetActionContract {
    private const val ACTION_COMMAND = "com.app.resn8.widget.action.COMMAND"
    private const val ACTION_JUMP = "com.app.resn8.widget.action.JUMP"
    private const val ACTION_REFRESH = "com.app.resn8.widget.action.REFRESH"
    private const val EXTRA_COMMAND = "playback_command"
    private const val EXTRA_QUEUE_ITEM_ID = "queue_item_id"

    fun commandIntent(context: Context, command: PlaybackWidgetCommand): Intent =
        Intent(context, PlaybackWidgetActionReceiver::class.java)
            .setAction(ACTION_COMMAND)
            .setData(uniqueData("command", command.name))
            .putExtra(EXTRA_COMMAND, command.name)

    fun jumpIntent(context: Context, queueItemId: String): Intent =
        Intent(context, PlaybackWidgetActionReceiver::class.java)
            .setAction(ACTION_JUMP)
            .setData(uniqueData("jump", queueItemId))
            .putExtra(EXTRA_QUEUE_ITEM_ID, queueItemId)

    fun refreshIntent(context: Context): Intent =
        Intent(context, PlaybackWidgetActionReceiver::class.java)
            .setAction(ACTION_REFRESH)
            .setData(uniqueData("refresh", "all"))

    fun parse(intent: Intent): PlaybackWidgetAction? = when (intent.action) {
        ACTION_COMMAND -> intent.getStringExtra(EXTRA_COMMAND)
            ?.let { value -> runCatching { PlaybackWidgetCommand.valueOf(value) }.getOrNull() }
            ?.let(PlaybackWidgetAction::Command)

        ACTION_JUMP -> intent.getStringExtra(EXTRA_QUEUE_ITEM_ID)
            ?.takeIf(String::isNotBlank)
            ?.let(PlaybackWidgetAction::Jump)

        ACTION_REFRESH -> PlaybackWidgetAction.Refresh
        else -> null
    }

    private fun uniqueData(kind: String, value: String): Uri = Uri.Builder()
        .scheme("resn8")
        .authority("playback-widget")
        .appendPath(kind)
        .appendPath(value)
        .build()
}

class PlaybackWidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = PlaybackWidgetActionContract.parse(intent)
        if (action == null) {
            Log.w(TAG, "Ignoring malformed widget action")
            return
        }

        val pendingResult = goAsync()
        val appContext = context.applicationContext
        receiverScope.launch {
            try {
                val succeeded = withTimeout(COMMAND_TIMEOUT_MS) {
                    when (action) {
                        is PlaybackWidgetAction.Command -> PlaybackWidgetController(appContext)
                            .execute(action.command)

                        is PlaybackWidgetAction.Jump -> PlaybackWidgetController(appContext)
                            .jumpTo(action.queueItemId)

                        PlaybackWidgetAction.Refresh -> true
                    }
                }
                if (!succeeded) Log.w(TAG, "Widget action was unavailable or failed: ${action.logName()}")
            } catch (error: Throwable) {
                Log.e(TAG, "Widget action failed: ${action.logName()}", error)
            } finally {
                try {
                    PlaybackWidgetUpdater.updateAll(appContext)
                } catch (error: Throwable) {
                    Log.e(TAG, "Widget refresh after action failed", error)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }

    private fun PlaybackWidgetAction.logName(): String = when (this) {
        is PlaybackWidgetAction.Command -> command.name
        is PlaybackWidgetAction.Jump -> "JUMP"
        PlaybackWidgetAction.Refresh -> "REFRESH"
    }

    private companion object {
        const val TAG = "PlaybackWidgetAction"
        const val COMMAND_TIMEOUT_MS = 5_000L
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
}
