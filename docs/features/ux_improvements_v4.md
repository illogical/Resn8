# UX Improvements v4 — Now Playing Context and Controls

**Status:** Implemented; manual on-device verification pending

**Milestone:** 10 — Polish and Finish the MVP

## Summary

This iteration moves collection selection to the upper-right app-bar action, replaces the playlist-only Now Playing link with a typed queue-origin action in the upper-left, rebalances the player actions, bounds Dislike at `-1`, adds optional double-tap ten-second seeking, and asynchronously resolves cached artwork for the full Now Playing surface.

`Resn8MediaService` remains the sole owner of ExoPlayer and MediaSession. Queue-item and playback-occurrence identity are unchanged.

## Implementation Changes

### Playback origin and app bar

- Persist a serializable Playlist, Album, Artist, Folder, or All Tracks origin in the existing queue context JSON. New queues always record an explicit origin; legacy queues derive the safest available origin from their older filter snapshot.
- Render the origin as the Now Playing app-bar title action with a context-specific icon and single-line label. Navigate to the exact playlist, album, artist, folder, or All Tracks destination without replacing the queue.
- Move the collection selector to the app bar's actions on every screen. Long labels ellipsize visually while retaining complete semantics.

### Player controls and rating

- Move Add to Playlist into a right-aligned action row above the adaptive artwork/content region.
- Center Dislike, a fixed score slot, and Like as one stable cluster. Show `+N` only for positive scores; keep the score slot blank for `0` and `-1`.
- Make Dislike atomically clamp at `-1`; Like remains an unbounded `+1` product action. Normalize older scores below `-1` in a non-destructive Room migration.

### Seeking and artwork

- Double-tap the left or right half of the artwork region to seek by -10 or +10 seconds through the MediaController, clamped to valid bounds. Keep the slider as the visible accessible control and show only a brief outward directional animation.
- Resolve the current media's external or embedded artwork through the existing `ArtworkCache` on background dispatchers. Deduplicate in-flight work and apply a result only while the same media remains current.
- Limit this artwork slice to full Now Playing. Broader album, artist, list, queue, playlist, and mini-player artwork remains tracked by `POLISH03`/`T058`.

## Public Interfaces and Data Contracts

- Add `PlaybackOrigin` and persist it in `QueueFilterSnapshot`, including exact album-artist and folder identity metadata.
- Add the origin to `QueueStartRequest.Library` and `PlaybackUiState`.
- Add `PlaybackConnection.seekBy(deltaMs)`.
- No MediaSession ownership, saved-queue item, traversal occurrence, playlist schema, or source-file contract changes.

## Test Plan

- Verify every queue entry point records and restores the correct origin and exact navigation keys.
- Verify the app-bar swap, origin icons/labels, Add placement, stable centered ratings, hidden neutral/disliked score, and adaptive layouts.
- Verify Room/fake rating parity, repeated dislike idempotency, positive-to-negative transitions, concurrency, and migration normalization.
- Verify double-tap direction, ten-second delta, bounds, disabled state, animation, and unchanged traversal identity.
- Verify cache hit, external/embedded artwork, missing/corrupt fallback, and stale-result protection.
- Run `testDebugUnitTest`, `lintDebug assembleDebug`, and `assembleDebugAndroidTest`; connected-device execution remains subject to `AGENTS.md` data-safety approval.

## Assumptions

- Context means the browse scope that created the queue. Album playback remains Album context even when reached through Artist browsing.
- Search and sorting modify a source scope but do not create new origin types.
- Queue jumps, restoration, media resumption, and mini-player navigation preserve the originating context.
- Double-tap feedback is visual-only; accessible seeking remains available through the slider.

## Verification Results

- `compileDebugKotlin`: passed.
- `testDebugUnitTest`: passed, including queue-origin, rating-floor, and Room 6→7 migration regressions.
- `lintDebug assembleDebug`: passed.
- `assembleDebugAndroidTest`: passed; instrumentation tests compiled without installing or mutating a device.
- Manual compact-layout, gesture, artwork, TalkBack, and exact-origin navigation verification remains pending on a safe API 34+ target.
