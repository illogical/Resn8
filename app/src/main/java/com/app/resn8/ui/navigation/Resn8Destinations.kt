package com.app.resn8.ui.navigation

import kotlinx.serialization.Serializable

@Serializable
object OnboardingRoute

@Serializable
data class LibraryRoute(val tab: String = "artists")

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
