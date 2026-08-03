package com.app.resn8.ui.playlists

internal fun currentMediaIdForPlaylist(
    viewedPlaylistId: String,
    sourcePlaylistId: String?,
    currentMediaId: String?
): String? = currentMediaId?.takeIf { sourcePlaylistId == viewedPlaylistId }

internal fun currentPlaylistItemIndex(
    items: List<PlaylistItemUiModel>,
    currentMediaId: String?
): Int = if (currentMediaId == null) {
    -1
} else {
    items.indexOfFirst { it.mediaFile.id == currentMediaId }
}
