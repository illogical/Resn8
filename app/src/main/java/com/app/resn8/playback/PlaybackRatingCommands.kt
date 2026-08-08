package com.app.resn8.playback

import android.os.Bundle
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand

internal val LIKE_CURRENT_COMMAND = SessionCommand(
    "com.app.resn8.command.LIKE_CURRENT",
    Bundle.EMPTY
)

internal val DISLIKE_CURRENT_COMMAND = SessionCommand(
    "com.app.resn8.command.DISLIKE_CURRENT",
    Bundle.EMPTY
)

internal val RATING_CHANGED_EVENT = SessionCommand(
    "com.app.resn8.event.RATING_CHANGED",
    Bundle.EMPTY
)

internal const val RATING_RESULT_MEDIA_ID = "rating_media_id"
internal const val RATING_RESULT_SCORE = "rating_score"

internal fun isTrustedResn8Controller(
    applicationPackageName: String,
    controller: MediaSession.ControllerInfo
): Boolean = controller.packageName == applicationPackageName

internal fun ratingResultExtras(mediaId: String, score: Int): Bundle = Bundle().apply {
    putString(RATING_RESULT_MEDIA_ID, mediaId)
    putInt(RATING_RESULT_SCORE, score)
}
