package com.app.resn8.domain.model

data class UiSessionState(
    val currentRoute: String = "onboarding",
    val selectedCollectionId: String? = null,
    val selectedSourceId: String? = null,
    val selectedFolderId: String? = null,
    val selectedArtistKey: MetadataGroupKey? = null,
    val selectedAlbumKey: MetadataGroupKey? = null,
    val selectedPlaylistId: String? = null,
    val activeQueueId: String? = null,
    val activeSearchQuery: String? = null,
    val activeSort: SortOrder = SortOrder.ARTIST,
    val activeSurface: LibrarySurface = LibrarySurface.ARTISTS,
    val libraryFilterSnapshot: LibraryFilterSnapshot = LibraryFilterSnapshot(),
    val activeFilterSnapshot: QueueFilterSnapshot? = null
)
