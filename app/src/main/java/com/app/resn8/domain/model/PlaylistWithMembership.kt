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
