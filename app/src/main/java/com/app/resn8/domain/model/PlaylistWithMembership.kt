package com.app.resn8.domain.model

enum class PlaylistMembershipState {
    ALL,
    NONE,
    SOME
}

data class PlaylistWithMembership(
    val playlist: Playlist,
    val membershipState: PlaylistMembershipState,
    val itemCount: Int
)

data class PlaylistWithItemCount(
    val playlist: Playlist,
    val itemCount: Int
)

data class AddItemsResult(
    val addedCount: Int,
    val unchangedCount: Int,
    val failedCount: Int = 0
)

enum class MoveDirection {
    TOP,
    UP,
    DOWN,
    BOTTOM
}

