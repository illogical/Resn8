package com.app.resn8.domain.model

data class UiSessionState(
    val currentRoute: String = "onboarding",
    val selectedCollectionId: String? = null,
    val selectedFolderId: String? = null,
    val selectedArtist: String? = null,
    val selectedAlbum: String? = null,
    val selectedPlaylistId: String? = null,
    val activeQueueId: String? = null,
    val activeSearchQuery: String? = null,
    val activeSort: SortOrder = SortOrder.ARTIST,
    val activeFilterSnapshot: QueueFilterSnapshot? = null
)
