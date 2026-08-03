# Milestone 8 Follow-up: Playlist Current-Track Awareness

## Purpose

Playlist Detail must make the active item from its own playback queue easy to identify and locate without changing the user's browsing position automatically. This follow-up builds on the Milestone 8 playlist-context pipeline and preserves manual playlist order, unavailable memberships, and saved-queue snapshot isolation.

## Implementation

- Derive the current playlist media ID only when the viewed playlist ID exactly matches `PlaybackUiState.sourcePlaylistId`.
- Pass that derived identity and `isPlaying` into `PlaylistDetailScreen`; do not give `PlaylistDetailViewModel` ownership of or a dependency on playback.
- Keep the existing one-based manual position on every row. Give the matching row a visible icon, tonal background, and an accessible current/paused state.
- Show a 48dp `Jump to current track` icon button only when the current media still belongs to the live playlist. Use the live playlist index rather than the available-only playback queue index.
- Preserve scroll position while playback advances. On explicit jump, clear playlist search if it hides the target, wait for the full list to become visible, and animate the target row into view.
- If the current item is removed from the playlist, remove the row marker and jump action without changing the already-saved active queue.

## Boundaries

- No database or domain-schema change is required.
- The active item remains identified while playback is paused, including safe paused restoration.
- A matching media file from a library, generated queue, or different playlist must not mark the viewed playlist.
- Playlist membership is unique by media ID, while queue occurrence identity remains unchanged and authoritative for playback/history.

## Verification

- Unit-test exact source matching and live-row lookup, including removed membership and unavailable rows before the current item.
- Verify one-based positions remain stable during search and reorder.
- With a 50+ item playlist and item 30 current, verify the marker, explicit jump, filtered jump, paused state, next/previous updates, rotation, and TalkBack descriptions.
- Run `testDebugUnitTest`, `lintDebug`, and `assembleDebug` with Android Studio's bundled JDK.
