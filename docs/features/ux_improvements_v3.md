# UX Improvements v3 — Library Sorting and Playlist Reordering

**Status:** Implemented; manual on-device verification pending

**Milestone:** 10 — Polish and Finish the MVP

**Backlog item:** `POLISH05`

## Summary

This iteration replaces the oversized combined Library sort/filter sheet with context-specific field sorting and adds the missing gesture path for manual playlist ordering. Artists and Albums expose Alphabetical ordering; All Tracks exposes Alphabetical, Artist, Album, Date Added, Play Count, Last Played, and Rating. Every surface has an independent persisted Ascending/Descending choice.

Playlist Detail now uses the one-based track number as a long-press drag handle. Dragging is optimistic and persists once on drop through the existing repository-owned collision-safe reorder transaction. Move Up/Down/Top/Bottom remain available as non-gesture accessibility alternatives.

## Implementation Changes

### Library sorting

- Replace `LibraryFilterSheet` behavior with a vertically scrollable `LibrarySortSheet`. Remove Availability, Exclude Disliked, and Done controls.
- Default Artists, Albums, and All Tracks to Alphabetical ascending and remember each surface independently across tab changes, recreation, and process restart.
- Map All Tracks fields to deterministic Room and fake-repository ordering. Direction reverses only the selected primary field; artist/album group contents retain canonical album/disc/track order. Unknown artist, album, and last-played values remain last, with normalized title and stable media ID tie-breakers.
- Reset legacy Library filter snapshots to `ALL` availability with disliked tracks included, preventing an upgraded installation from retaining an invisible filter. Keep the domain filter contracts for folders, queue snapshots, and future smart generation.

### Persistence and compatibility

- Add `LibrarySortField`, `SortDirection`, `LibrarySortSelection`, and versioned `LibrarySortPreferences` contracts.
- Add nullable serialized `librarySortPreferences` to `UiSessionState` with an explicit Room 5→6 migration. Retain `activeSort` for backward compatibility and map legacy values into the new field/direction model.
- Normalize removed Track and Unplayed selections to Alphabetical ascending. Map Least Played and Least Recent to Play Count ascending and Last Played ascending respectively.
- Keep `LibraryQuery.sort` compatible with existing detail/folder callers while adding an explicit direction propagated identically to paged queries and visible-media snapshots.

### Playlist drag reorder

- Expand the displayed track-number region to a 48dp semantic drag handle without changing its visible number. A normal row tap still starts playback; long press followed by movement initiates reorder.
- Maintain an optimistic list while dragging, show lifted-row feedback, and autoscroll near viewport edges. Persist only the final media ID and target index when the gesture ends.
- Revert to the authoritative repository flow when a gesture is cancelled or persistence fails, and prevent overlapping drags while a drop is being saved.
- Disable gesture and overflow reordering while search is active. Preserve manual numbering, unavailable membership, current-track state, `addedAt`, stable positions, and active saved-queue isolation.

## Public Interfaces and Data Contracts

- `LibraryQuery` adds `sortDirection`, defaulted from the legacy `SortOrder` so existing non-Library callers retain their prior ordering.
- `UiSessionState` and `UiSessionStateEntity` add `librarySortPreferences`; `Converters` adds its versioned JSON conversion.
- `PlaylistDetailViewModel.reorderTrack` becomes a suspend result-returning operation so the screen can distinguish success from failure and reconcile optimistic state.
- No playback-service, MediaSession, saved-queue, playlist schema, source-file, or occurrence-identity contracts change.

## Test Plan

- Verify context-specific labels, direction controls, removed filter controls, and legacy sort normalization.
- Verify both directions for every All Tracks field, null-last behavior, stable ties, per-surface preference isolation, persistence mapping, and legacy-filter reset.
- Verify the number handle semantics, gesture-only target, upward/downward movement, edge autoscroll, cancellation/failure reconciliation, search disabling, and equivalence with overflow actions.
- Verify playlist reordering remains durable and does not mutate an already active playlist-derived queue.
- Run `testDebugUnitTest`, `lintDebug assembleDebug`, and `assembleDebugAndroidTest`. Connected-device execution remains subject to the inventory, backup, and explicit-approval requirements in `AGENTS.md`.

## Assumptions

- Alphabetical means artist name on Artists, album name on Albums, and display title/filename on All Tracks.
- Date Added descending means newest first; Play Count descending means most played; Last Played descending means most recent; Rating descending means highest score.
- Unknown metadata and unplayed timestamps remain last in either direction.
- No third-party reorder dependency is introduced.

## Verification Results

- `compileDebugKotlin`: passed.
- `testDebugUnitTest`: passed, including fake/Room bidirectional sorting, preference mapping, hidden-filter reset, and Room 5→6 migration coverage.
- `assembleDebugAndroidTest`: passed; the instrumentation APK and Compose coverage compiled without installing or mutating a device.
- `lintDebug assembleDebug`: passed.
- Manual compact-layout, large-font, TalkBack, and long-playlist drag/autoscroll verification remains pending on a safe API 34+ target.
