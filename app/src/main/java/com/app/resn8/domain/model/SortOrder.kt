package com.app.resn8.domain.model

enum class SortOrder {
    ARTIST,
    ALBUM,
    TITLE,
    TRACK,
    RECENTLY_ADDED,
    MOST_PLAYED,
    LEAST_PLAYED,
    UNPLAYED,
    MOST_RECENT,
    LEAST_RECENT,
    MOST_LIKED
}

fun SortOrder.defaultDirection(): SortDirection = when (this) {
    SortOrder.RECENTLY_ADDED,
    SortOrder.MOST_PLAYED,
    SortOrder.MOST_RECENT,
    SortOrder.MOST_LIKED -> SortDirection.DESCENDING
    else -> SortDirection.ASCENDING
}

fun SortOrder.toLibrarySortSelection(): LibrarySortSelection = when (this) {
    SortOrder.ARTIST -> LibrarySortSelection(LibrarySortField.ARTIST)
    SortOrder.ALBUM -> LibrarySortSelection(LibrarySortField.ALBUM)
    SortOrder.TITLE -> LibrarySortSelection()
    SortOrder.TRACK,
    SortOrder.UNPLAYED -> LibrarySortSelection()
    SortOrder.RECENTLY_ADDED -> LibrarySortSelection(LibrarySortField.DATE_ADDED, SortDirection.DESCENDING)
    SortOrder.MOST_PLAYED -> LibrarySortSelection(LibrarySortField.PLAY_COUNT, SortDirection.DESCENDING)
    SortOrder.LEAST_PLAYED -> LibrarySortSelection(LibrarySortField.PLAY_COUNT, SortDirection.ASCENDING)
    SortOrder.MOST_RECENT -> LibrarySortSelection(LibrarySortField.LAST_PLAYED, SortDirection.DESCENDING)
    SortOrder.LEAST_RECENT -> LibrarySortSelection(LibrarySortField.LAST_PLAYED, SortDirection.ASCENDING)
    SortOrder.MOST_LIKED -> LibrarySortSelection(LibrarySortField.RATING, SortDirection.DESCENDING)
}

fun LibrarySortSelection.toLegacySortOrder(): SortOrder = when (field) {
    LibrarySortField.ALPHABETICAL -> SortOrder.TITLE
    LibrarySortField.ARTIST -> SortOrder.ARTIST
    LibrarySortField.ALBUM -> SortOrder.ALBUM
    LibrarySortField.DATE_ADDED -> SortOrder.RECENTLY_ADDED
    LibrarySortField.PLAY_COUNT -> SortOrder.MOST_PLAYED
    LibrarySortField.LAST_PLAYED -> SortOrder.MOST_RECENT
    LibrarySortField.RATING -> SortOrder.MOST_LIKED
}
