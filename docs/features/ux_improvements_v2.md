# UX Improvements v2 — Adaptive Now Playing

**Status:** Implemented; manual on-device verification pending

**Milestone:** 10 — Polish and Finish the MVP

**Backlog item:** `POLISH04`

## Summary

This iteration builds on the completed baseline in `docs/features/ux_improvements.md` and focuses only on the main Now Playing experience. The player is now non-scrollable: seek, transport, rating, score, and Add to Playlist controls claim their space before artwork, so all primary actions remain available without vertical scrolling. Artwork uses only the remaining space, shrinks responsively, and disappears when less than 72dp is available.

`Resn8MediaService` remains the sole owner of ExoPlayer and MediaSession. The UI continues to interact through the application-scoped `PlaybackConnection`; Room schemas, saved-queue identity, occurrence identity, and source-file behavior are unchanged.

## Implementation Changes

### 1. Move playlist context into the app bar

- Show `Playlist: <name>` as a single-line TextButton in the app bar's upper-right action area only while Now Playing displays a queue sourced from a playlist. It uses the same button typography as the collection selector, visually ellipsizes long names, and retains the full label in semantics.
- Navigate the action to `PlaylistDetailRoute(playlistId, revealCurrentTrack = true)` so Playlist Detail clears a hiding search, reveals the live current membership, and positions upcoming tracks below it using the completed v1 behavior.
- Remove playlist-source presentation and navigation callbacks from `NowPlayingScreen`; the app shell already observes playback context and owns both the collection selector and top app bar.

### 2. Make Now Playing adaptive and non-scrollable

- Replace the vertically scrolling content column with a `BoxWithConstraints` layout. Portrait uses one column. Short-wide/landscape windows use two regions: artwork and metadata on the left, seek/transport/rating controls on the right.
- Reserve fixed, accessible space for metadata, the elapsed/duration slider, Previous, Play/Pause, Next, Dislike, numeric score, Like, and Add to Playlist. Interactive controls remain at least 48dp, with a 64dp primary Play/Pause action.
- Measure artwork only inside the remaining weighted region. Render the largest square permitted up to 300dp, shrink it with the available height, and omit it below 72dp. Scale the fallback music-note icon with the artwork surface.
- Present playback notices as a dismissible overlay instead of inserting them into the measured content stack.

### 3. Remove redundant player queue access

- Remove the full-width **View Queue** button and `onQueueClicked` callback from Now Playing.
- Retain `QueueScreen`, `QueueRoute`, queue persistence, direct queue-item jumps, Save Queue as Playlist, and legacy typed-route restoration. This iteration removes only the Now Playing entry point.

### 4. Use collection-profile metadata presentation

- Replace `showUnknownArtist` on Now Playing with `showMusicMetadata`, derived from the active playback collection profile.
- Music keeps its emphasized `headlineSmall` title and shows artist/album presentation values when present.
- Audio Files uses a two-line, end-ellipsized `titleMedium` filename and never renders artist or album, including synthetic `Unknown Artist` or `Unknown Album` values.

## Public Interfaces and Data Contracts

- `NowPlayingScreen` removes `queueTitle`, `sourcePlaylistId`, `onOpenPlaylist`, and `onQueueClicked`; it adds explicit `showMusicMetadata` presentation input.
- The app shell owns the playlist app-bar action and constructs `PlaylistDetailRoute` with `revealCurrentTrack = true`.
- No persistence, repository, playback-service, queue, MediaSession, or database interfaces change.

## Test Plan

- Compose coverage constrains Now Playing to 360×640dp portrait, 360×480dp compact portrait, and 640×360dp landscape with increased font scale. Assert every seek, transport, rating, score, and playlist action remains inside the player bounds and no vertical scroll semantics exist.
- Verify artwork becomes smaller as height contracts and disappears below its minimum without displacing controls.
- Verify Audio Files exposes the full two-line title semantics without artist/album labels, while Music retains metadata.
- Verify the app-bar playlist action keeps its full label, appears only on Now Playing, returns the source playlist ID, and constructs a route with `revealCurrentTrack = true`.
- Verify **View Queue** is absent while the typed Queue route remains registered and restorable.
- Run `testDebugUnitTest`, `lintDebug assembleDebug`, and `assembleDebugAndroidTest`. Do not install or run connected tests on a data-bearing device without the inventory, backup, and explicit approval required by `AGENTS.md`.

## Documentation and Completion

- Update `SPECIFICATION.md` to require an always-visible, non-scrolling player control surface with adaptive optional artwork and profile-appropriate metadata; remove the obsolete Now Playing queue-access requirement.
- Update `UX.md` with compact portrait, landscape, large-font, playlist-link, Audio Files, artwork-collapse, and no-scroll workflows. Keep Queue UI compatibility explicit without presenting it as a standard Now Playing action.
- Keep `POLISH04` pending until the documented manual on-device workflows pass. Broader artwork use remains separately tracked by `POLISH03`; full-app adaptive/accessibility acceptance remains `T047`.

## Verification Results

- `compileDebugKotlin`: passed.
- `testDebugUnitTest`: passed, including the playlist reveal-route regression.
- `assembleDebugAndroidTest`: passed; instrumentation APK compiled without installing or mutating a device.
- `lintDebug assembleDebug`: passed.

## Assumptions

- Controls always take priority over artwork; artwork may disappear completely.
- Bottom navigation remains visible and the mini-player remains hidden on Now Playing.
- Removing **View Queue** does not remove saved queues or the compatibility Queue screen/route.
- This iteration does not expand album art to browsing surfaces.
