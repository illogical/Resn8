# UX Improvements Implementation Plan

**Status:** Completed

**Milestone:** 10 — Polish and Finish the MVP

**Backlog item:** `POLISH01`

## Summary

This iteration improves collection switching, Settings organization, Now Playing, playlist handoff, selectable track lists, and dense text presentation without changing player ownership or source-file safety. `Resn8MediaService` remains the only owner of ExoPlayer and MediaSession; UI code continues to act through the application-scoped `PlaybackConnection`.

The central behavior change is per-collection playback restoration. Switching collections checkpoints and stops the outgoing queue, then restores the incoming collection's last valid queue, item, and position in a paused state and opens Now Playing. Browse route, search, filter, sort, and scroll state are intentionally not remembered per collection. If the incoming collection has never played anything, it opens Library for Music or Folders for Audio Files.

## Implementation Changes

### 1. Persist and restore the last queue per collection

- Add a Room `collection_playback_state` table keyed by `collectionId`, with nullable `activeQueueId` and `updatedAt`. Use foreign keys that remove the state with its collection and set the queue pointer to null if that queue is deleted. Add the repository/domain operations needed to observe, upsert, clear, and delete this mapping, including fake-repository parity.
- Add an explicit non-destructive database migration from version 4 to version 5. Seed the selected collection's mapping from the existing `UiSessionState.activeQueueId` when the referenced queue belongs to that collection; do not infer a queue from update timestamps.
- Whenever a new queue becomes active, update both the global `UiSessionState.activeQueueId` and that queue's collection mapping. Saved queues remain explicit snapshots with their existing stable `queueItemId` and traversal occurrence identities.
- Replace the current collection-switch sequence with one coordinated operation: checkpoint the outgoing queue immediately, pause/stop and clear the service player, resolve the target collection/source and stored queue, update global session selection, and ask the service to prepare the target queue at its persisted item/position with autoplay disabled. A newer switch or newly started queue must supersede an older in-flight restore.
- For a valid target queue, navigate to Now Playing and keep the restored occurrence identity/listened duration intact. For no stored queue, clear the global active queue and open the profile home. For a missing or structurally invalid stored queue, clear only that stale pointer, open the profile home, and show a recoverable explanation. Preserve unavailable saved items and use the existing unavailable-media recovery behavior rather than deleting or silently rebuilding the queue.
- Update the normative specification and UX workflow that currently require collection switches to detach and forget `activeQueueId`; the new invariant is one resumable paused queue per collection, with only the selected collection mirrored into global `UiSessionState.activeQueueId`.

### 2. Reorganize Settings and collection management

- Make Settings a small menu with two rows: **Collections** and **About**. Add typed nested routes and standard top app bars/back navigation so the hierarchy reads Settings → Collections → collection name, and Settings → About. Move the existing local-first About copy unchanged to the About page.
- Present Collections as cards modeled on Playlists: collection name, Music/Audio Files type, indexed track count, unavailable count when nonzero, and an overflow menu. The menu provides Rename, Re-index, Reselect Collection Folder, and Delete; clicking the card opens its detail page. Row actions and detail-page actions call the same ViewModel operations.
- The `+` action opens a reusable collection editor in create mode. Keep the draft in UI/ViewModel state and create no database row until a normalized-unique name, a Music or Audio Files type, and a SAF folder are all present. After creation, enqueue indexing and remain on the new detail page so live progress replaces the summary area and the final scan summary appears in the same location.
- In existing-collection mode, allow renaming, re-indexing, and folder reselection. Show collection type read-only: profile changes after creation are out of scope because they would reinterpret indexed metadata and navigation. Label the repair action **Reselect Collection Folder**.
- Add transactional collection deletion despite the current restrictive foreign keys. Cancel that source's indexing work; stop playback if the collection is active; delete its collection-scoped queue items/queues, playlist memberships/playlists, history, indexed/staged media and folders, scan state, source, and playback mapping in dependency-safe order; then delete the collection. Never touch source audio. Release the persisted SAF grant only after database deletion succeeds, on a best-effort basis.
- Require a confirmation dialog that names the collection and warns that Resn8's index, ratings, play history, playlists, saved queue, and folder access for it will be removed while source files remain untouched. If the active collection is deleted, select the next collection in the list and apply the normal paused restore/home fallback. Deleting the final collection clears session/playback state and returns to first-collection onboarding.
- Preserve the existing playlist deletion confirmations and add regression coverage for both list and detail entry points.

### 3. Improve Now Playing and playlist handoff

- Do not render the mini-player while `NowPlayingRoute` is active; retain the bottom navigation bar. Use the recovered height for the dedicated seek and transport controls and for a larger, constraint-bounded artwork surface. Keep transport targets at least 48dp and the primary play/pause target visually dominant.
- Remove “Tap to open playlist detail.” Reduce unused top spacing by replacing `SpaceBetween` distribution with explicit adaptive spacing and weighted artwork. On compact-height/landscape layouts, allow the content to scroll or use a two-region arrangement instead of shrinking controls below usable size.
- Keep `Playlist: <name>` as an accessible button/link. Extend the typed Playlist Detail route with a one-shot `revealCurrentTrack` intent. When opened from Now Playing, clear any Playlist Detail search that hides the current membership, await the live rows, and scroll the current row near the top so upcoming tracks are visible. Reuse the existing current-row highlight and manual-position logic; if the membership was removed, open the playlist normally and provide a concise notice instead of scrolling to a different match.
- Preserve stable source-playlist identity: only reveal a row when the active queue originated from that exact playlist. Duplicate queue occurrences must not change playlist membership identity or manual order.

### 4. Simplify selectable track lists

- Remove the Library's Folders sub-tab and render only Artists, Albums, and All Tracks. The bottom Folders destination remains the sole folder browser; retain compatibility mapping for any persisted legacy `LibrarySurface.FOLDERS` state.
- Introduce one reusable compact selection action tray for Folders, Library All Tracks, and Album Detail. Position it as a fixed overlay at the bottom of each screen's content, immediately above the outer mini-player, so selection never moves the list. Keep the mini-player visible and add list bottom padding equal to the tray height so the last row remains reachable.
- Use a two-row layout: a concise count such as `1 file selected` or `3 files • 2 folders`, plus **Clear**, followed by a full-width **Add to Playlist** action. Omit the redundant “unique audio files” line; accessibility semantics may still announce the resolved available-media count when it differs because selected folders expand to descendants.
- Remove each track row's overflow menu anywhere the same row exposes a selection checkbox and its only action is Add to Playlist. Single-item addition becomes checkbox then shared action. Keep overflow menus where they provide nonredundant actions, including playlist reorder/removal and collection management.
- Keep paging-independent Select All behavior unchanged: album selection includes every available song; folder Select All includes available direct files only; explicit folder checkboxes continue to expand descendants through selection resolution.

### 5. Tighten browse typography

- Add shared compact row typography/presentation used by track/file titles and album names across Library, artist/album detail, Folders, playlist selection, queue, and playlist rows. Use Material typography tokens at approximately `bodyMedium` for primary row text and `bodySmall` for metadata rather than screen-heading styles.
- Music rows use one primary line with end ellipsis and one compact artist/album line. Audio Files retain their specified single-instance title wrapping to at most two lines. Album and long dynamic names must expose their full text to accessibility services even when visually truncated.
- Do not reduce Now Playing's primary title as part of this change. Verify the compact rows at default and increased font scales rather than hard-coding fixed-height text containers.

## Public Interfaces and Data Contracts

- Room schema version becomes 5 with the new per-collection playback-state table and exported schema/migration fixture.
- `CollectionRepository` (or a focused collection-playback repository) gains last-queue read/update/clear operations and transactional collection deletion; fake and Room implementations must have matching behavior.
- Collection list data exposes indexed and unavailable media counts as a Flow-backed aggregate rather than loading media rows into memory.
- Playlist Detail's typed destination gains `revealCurrentTrack: Boolean = false`; ordinary playlist navigation remains unchanged.
- The shared selectable-track row no longer accepts a single-item playlist callback when selection controls are present. A shared selection-tray model/callback contract owns Add and Clear actions.

## Test Plan

- **Room and repository:** migrate a version-4 database with and without an active queue; preserve all existing data; verify per-collection queue mappings, constraint handling, aggregate counts, fake parity, dependency-ordered collection deletion, and final-collection deletion without destructive fallback.
- **Playback and switching:** alternate between two collections with different queues/items/positions; verify the outgoing checkpoint wins over older writes, incoming playback is prepared paused, occurrence identity is preserved, the mini-player reflects only the selected collection, rapid switches cannot install a stale queue, and starting a new queue replaces that collection's mapping. Cover no queue, missing queue, all-unavailable queue, process restart, and deleting the active collection.
- **Navigation and UI:** verify Settings breadcrumbs/pages/editor validation/progress/errors; read-only existing profile; deletion confirmations; no mini-player on Now Playing; compact-height controls; playlist-link reveal and removed-membership fallback; three Library tabs; fixed selection tray without list jump; tray stacked above the mini-player; reachable final rows; no redundant per-row overflow; and concise singular/plural counts.
- **Typography and accessibility:** verify long Music and Audio Files titles, album names, TalkBack labels/focus order, 48dp actions, high contrast, default and supported large fonts, portrait, landscape, and configuration changes.
- Run `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest` and `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat lintDebug assembleDebug`. Compile instrumentation with `assembleDebugAndroidTest`; do not install or run connected tests on a data-bearing physical device without the separate inventory, backup, and explicit approval required by `AGENTS.md`.

## Documentation and Completion

- Update `SPECIFICATION.md` restoration, collection-management, acceptance, and MVP-boundary language to match resumable per-collection queues and confirmed collection deletion.
- Update `UX.md` with manual workflows for collection switching, collection creation/index progress/deletion, playlist reveal, selectable-list behavior, compact typography, and adaptive Now Playing.
- When implementation and verification are complete, mark `POLISH01` complete in `TASKS.md`; update README roadmap wording only if the Milestone 10 exit criteria as a whole are complete.

## Verification Results

- `testDebugUnitTest`: passed, including version 4-to-5 migration, collection deletion, repository parity, and per-collection queue restoration coverage.
- `lintDebug assembleDebug`: passed.
- `assembleDebugAndroidTest`: passed (instrumentation APK compilation only; no app installation or connected-device mutation was performed).
- Manual on-device workflows are documented in `docs/UX.md` for subsequent device verification.

## Assumptions

- Collection switching never starts audio automatically; pressing Play is always required after the paused restore.
- Per-collection browse route, search, filter, sort, selection, and scroll position are not restored. The target opens Now Playing only when it has a valid saved queue, otherwise its profile home.
- Collection type is chosen during creation and read-only afterward.
- Selection state remains transient and is cleared on navigation or collection switching.
- Source audio is never renamed, moved, modified, or deleted by collection deletion.
