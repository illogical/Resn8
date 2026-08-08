package com.app.resn8.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbDown
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun RatingActionButton(
    likeScore: Int,
    isLikeAction: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selected = if (isLikeAction) likeScore > 0 else likeScore < 0
    val actionLabel = if (isLikeAction) "Like" else "Dislike"
    val scoreLabel = when {
        likeScore > 0 -> "+$likeScore"
        likeScore < 0 -> "Disliked"
        else -> "Neutral"
    }
    val overlay = when {
        isLikeAction && likeScore > 99 -> "99+"
        isLikeAction && likeScore > 0 -> "+$likeScore"
        else -> ""
    }
    val selectedContainer = if (isLikeAction) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val selectedContent = if (isLikeAction) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    IconButton(
        onClick = onClick,
        colors = if (selected) {
            IconButtonDefaults.iconButtonColors(
                containerColor = selectedContainer,
                contentColor = selectedContent
            )
        } else {
            IconButtonDefaults.iconButtonColors()
        },
        modifier = modifier
            .size(48.dp)
            .testTag(if (isLikeAction) "rating-like-button" else "rating-dislike-button")
            .semantics(mergeDescendants = true) {
                contentDescription = "$actionLabel, current score $scoreLabel"
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = when {
                    isLikeAction && selected -> Icons.Filled.ThumbUp
                    isLikeAction -> Icons.Outlined.ThumbUp
                    selected -> Icons.Filled.ThumbDown
                    else -> Icons.Outlined.ThumbDown
                },
                contentDescription = null,
                modifier = Modifier.size(28.dp)
            )
            if (overlay.isNotEmpty()) {
                Text(
                    text = overlay,
                    color = selectedContainer,
                    fontSize = if (overlay.length >= 3) 8.sp else 9.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier.testTag("rating-like-overlay")
                )
            }
        }
    }
}
