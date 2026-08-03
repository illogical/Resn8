package com.app.resn8.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
data class LibraryRoute(val tab: String = "artists")

@Serializable
data class ArtistDetailRoute(val collectionId: String, val artistKeySerialized: String)

@Serializable
data class AlbumDetailRoute(
    val collectionId: String,
    val albumKeySerialized: String,
    val albumArtistKeySerialized: String? = null
)

@Serializable
data class FoldersRoute(val folderId: String? = null)

@Serializable
object PlaylistsRoute

@Serializable
data class PlaylistDetailRoute(val playlistId: String)

@Serializable
object QueueRoute

@Serializable
object NowPlayingRoute

@Serializable
object SettingsRoute

