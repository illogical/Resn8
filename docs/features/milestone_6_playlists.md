# Milestone 6 Implementation Plan: Manual Playlists

## Objective and exit condition

Implement T031-T036 from [TASKS.md](../TASKS.md) so a user can create and maintain durable, manually ordered playlists and update membership from every required media context. Milestone 6 is complete only when playlist actions use the persisted active collection identity, unavailable membership is preserved and explained, reorder operations cannot collide, playlist-derived playback is an isolated saved-queue snapshot, and the automated and API 34+ manual checks in this plan pass.

This plan is governed by [SPECIFICATION.md](../SPECIFICATION.md). [BRAINSTORM.md](../BRAINSTORM.md) supplies product intent where the specification is silent, and [README.md](../../README.md) supplies the local-first product boundary.

## Baseline and staged-implementation assessment

The checked-in Milestone 1-5 baseline already provides the Room playlist tables and unique constraints, collection and UI-session persistence, selection resolution, explicit saved queues, application-scoped `PlaybackConnection`, and service-owned Media3 player. The staged Milestone 6 implementation adds useful foundations:

- Room and fake repository membership operations, tri-state projection, and playlist-derived queue collection propagation;
- playlist list/detail screens, create/rename/delete dialogs, text filtering, and accessible move commands;
- a reusable selector sheet and entry points from track rows, album/artist detail, folder selection, queue, and Now Playing;
- initial Room tests for membership state, duplicate addition, folder expansion, and saved-queue isolation.

These changes should be corrected in place rather than discarded, but they do not yet meet the milestone exit condition:

1. `Resn8NavHost`, `PlaylistsViewModel`, library/folder queue requests, and selector operations use literal IDs such as `"MUSIC"`. Onboarding creates a persisted collection with its own stable ID, and `UiSessionState.selectedCollectionId` is authoritative. A literal profile name is not a collection primary key and can produce empty queries or foreign-key failures.
2. Reordering is split between a ViewModel rank calculation and repository writes. When adjacent ranks have no midpoint, the position update can violate unique `(playlistId, position)` before compaction runs. Drag reorder is also absent.
3. The staged tests do not cover normalized-name conflicts, delete safety, unavailable rows, rank exhaustion/compaction, restart persistence, selector failure states, UI behavior, or drag/accessibility behavior. T036 therefore remains open.
4. Playlist detail does not visibly distinguish unavailable membership, and the list screen does not yet expose the planned membership count. Mutation failures and inline-create failures are not presented reliably.
5. Required entry-point coverage and semantics are not yet explicit or fully verified: mini-player, folder-descendant selection, queue duplicate collapse, selector mixed-state behavior, and filtered playlist playback need defined contracts.
6. The selector projection observes every playlist's complete item list. Replace this N-flow/full-row approach with aggregate membership/count queries before treating it as suitable for large libraries.

## Architectural invariants

- `Resn8MediaService` remains the only owner of ExoPlayer and `MediaSession`. Playlist UI starts playback only through `PlaybackConnection` and `StartQueueUseCase`.
- Resolve the current collection from `UiSessionState.selectedCollectionId` (or a validated single-collection bootstrap result). Never substitute a collection profile label, display name, or fallback string for its database ID.
- A `PlaylistItem` is unique by `(playlistId, mediaId)`. Re-adding an existing media ID is an idempotent no-op; adding an ordered payload appends only missing IDs in first-occurrence order.
- Playlist `position` values are stable, unique `Long` ranks. All reorder and compaction decisions occur in one repository transaction.
- Playlist membership survives source unavailability and re-indexing. The UI shows unavailable items in their manual position and disables direct playback for them; it does not silently remove them.
- Creating, renaming, deleting, reordering, or changing playlist membership never modifies source audio.
- Starting from a playlist first persists an explicit available-only queue snapshot with fresh stable `queueItemId` values. Playlist edits or deletion cannot mutate that queue. Playback traversal occurrence IDs remain separate from queue-item IDs.
- UI state is unidirectional. Screens emit intents; ViewModels/state holders expose loading, success, empty, and recoverable error state. Composables do not call repositories directly.
- Room mutations execute off the main thread and report failure. No destructive database fallback is introduced.

## Domain and repository contracts

### Active collection resolution

Expose one application-level active-collection flow derived from `UiSessionState.selectedCollectionId`. If it is null, validate the sole MVP collection and persist that ID before enabling collection-scoped actions. Empty, missing, or deleted selections produce a recoverable UI state rather than a fabricated ID.

Every selector request carries:

- `collectionId`;
- a distinct, ordered list of `mediaId` values;
- a source/context label for user feedback;
- optional queue occurrence metadata only when the caller needs to explain duplicate collapse.

Validate that every target media row belongs to the request collection. Reject cross-collection or missing IDs as a reported partial/total failure.

### Playlist names

- Trim leading/trailing whitespace, reject blank names, and store the display name separately from its normalized key.
- Normalize with `Locale.ROOT` case folding so behavior does not vary by device locale.
- Enforce unique `(collectionId, normalizedName)` in Room. Repository pre-checks improve messages, but the unique index remains authoritative under concurrent requests.
- Map constraint conflicts, missing playlists, and unexpected database failures to stable domain errors used by both create and rename UI.

### Ordered batch membership

Add and remove operations are atomic per target playlist:

- de-duplicate the incoming payload while retaining first occurrence;
- append only missing media after the current maximum rank;
- guard `Long` overflow and compact before allocating ranks when required;
- update `Playlist.updatedAt` only if membership actually changed;
- return counts for added, removed, unchanged, and failed IDs so the selector can show accurate feedback.

For saving an active queue as a playlist, manual-playlist uniqueness means duplicate queue occurrences collapse to the first media occurrence. Explain the resulting unique track count before saving; do not imply that queue occurrence duplicates can be preserved in a manual playlist.

### Membership projection

Use collection-scoped aggregate DAO queries that return, per playlist, total item count and selected-match count. Derive:

- `ALL` when a non-empty target set is fully present;
- `SOME` when at least one but not all targets are present;
- `NONE` when no targets are present.

Order `ALL` first as required by the specification; use a documented stable secondary order (normalized playlist name, then playlist ID). Mixed and none may share the remaining section or be ordered `SOME` then `NONE`, but tests must lock the selected behavior. The flow must react to playlist creation/deletion and membership mutations without opening one independent full-item flow per playlist.

### Transactional reorder API

Expose intent-based repository operations such as `moveBefore`, `moveAfter`, `moveToTop`, and `moveToBottom`; do not accept an unvalidated rank calculated by the UI. Within one transaction:

1. load the ordered membership and validate playlist/media identity;
2. determine neighboring ranks for the requested destination;
3. compact first if the gap is less than two, arithmetic would overflow, or a collision is otherwise possible;
4. assign the new rank and update `Playlist.updatedAt`;
5. return the authoritative order.

Drag reorder and Move Up/Down/Top/Bottom call the same repository path. Reordering is disabled while text filtering is active because hidden rows make the destination ambiguous. Compaction must not delete and reinsert membership; update ranks using a collision-safe two-phase strategy so observers never see membership disappear and `addedAt` values remain intact.

## UI behavior and entry points

### Playlist management

`PlaylistsScreen` shows name, unique track count, and an empty state. It supports create, rename, and confirmation-delete. Delete copy explicitly says that playlist membership is removed while source files remain untouched. Disable repeated submits while a mutation is running, preserve dialog input on failure, and surface normalized-name conflicts inline.

`PlaylistDetailScreen` shows the full manual order and distinguishes available and unavailable rows. Search matches title/display title, artist, album, and filename and changes presentation only. Removing a row requires an explicit action and offers recoverable feedback; it never removes the source file.

When search is active:

- row numbers represent their underlying manual positions, not filtered-list indexes;
- reorder actions are hidden/disabled;
- tapping an available result starts the full available-only playlist snapshot at that media item;
- `Play All` means the full playlist in manual order, not only filtered results, and its label/helper text makes that clear.

### Reusable selector

The selector header summarizes the target count and source. Rows expose a semantic tri-state control and playlist item count:

- `ALL` -> user activation removes all target memberships;
- `NONE` -> user activation adds all targets;
- `SOME` -> user activation adds the missing targets, producing `ALL`.

The sheet remains open across mutations, disables only the affected row while it is saving, and announces success or failure accessibly. Inline playlist creation keeps the dialog open on validation/database failure; after success it creates the playlist, adds the target payload atomically from the user's perspective, and shows the new row as checked. An empty target never opens the sheet.

### Required entry-point matrix

| Context | Required payload and behavior |
| --- | --- |
| Now Playing | Current queue item's `mediaId`; action remains visible and controller recreation-safe. |
| Mini-player | Current queue item's `mediaId`; visible action or accessible overflow, not a hidden gesture. |
| Single track row | That row's media ID from All Tracks, album, artist, search, or folder results. |
| Multi-selection | Distinct ordered resolution of selected files and selected folder descendants. Selection state stays intact after opening/dismissing the sheet. |
| Folder row/selection | All indexed descendant audio, including nested folders. If per-child exclusion is offered, model exclusions explicitly; do not claim deselection semantics that union-only resolution cannot provide. |
| Album/artist bulk action | Snapshot the complete current collection-scoped group in deterministic disc/track/title order, independent of Paging's loaded window. |
| Queue | One queue row or all queue rows. Explain first-occurrence de-duplication when repeated media IDs exist. |

Album/artist bulk actions and queue saving are valuable Milestone 6 extensions. They must not displace the normative Now Playing, single-row, multi-file, and folder-descendant flows.

## Implementation sequence

### T031 - Playlist management and collection correctness

1. Replace literal collection IDs with the authoritative active collection flow.
2. Harden normalized naming and domain error mapping.
3. Add reactive playlist counts and unavailable-item presentation.
4. Complete create/rename/delete loading, error, and confirmation states.

### T032 - Selector state and atomic membership

1. Introduce a selector request/state holder owned above individual screens.
2. Replace per-playlist item observation with aggregate membership queries.
3. Return mutation outcomes and keep the sheet/dialog open through failures.
4. Define and implement mixed-state and inline-create behavior.

### T033 - Context integrations

1. Wire Now Playing, mini-player, single rows, multi-selection, and folder descendants.
2. Verify album/artist snapshots are complete rather than limited to loaded Paging rows.
3. Define queue duplicate collapse and validate collection ownership for all payloads.
4. Preserve selections through rotation and selector dismissal where appropriate.

### T034 - Detail, search, removal, and playback snapshot

1. Render all memberships in rank order with unavailable status.
2. Preserve underlying positions and row numbering during search.
3. Start full available-only playlist snapshots through `PlaybackConnection`.
4. Verify edits/deletion cannot mutate an already saved active queue.

### T035 - Collision-free reorder

1. Move rank calculation into one transactional repository API.
2. Implement drag handles plus accessible Move Up/Down/Top/Bottom actions.
3. Compact before collision/overflow using collision-safe updates.
4. Verify restart persistence, `addedAt` preservation, and filtered-state disabling.

### T036 - Verification and milestone closeout

Complete the automated and manual matrix below. Only then check T031-T036 and the README roadmap entry.

## Verification matrix

### Repository and Room tests

- create/rename reject blank and normalized duplicates, including concurrent attempts and locale-sensitive casing;
- delete cascades only playlist membership and leaves media, history, ratings, and source rows intact;
- ordered bulk add is idempotent, retains first-occurrence order, reports counts, and updates timestamps only on change;
- bulk remove and selector transitions cover `NONE -> ALL`, `SOME -> ALL`, and `ALL -> NONE`;
- aggregate membership results and stable ordering react to mutations;
- unavailable media remains a ranked member;
- every move direction, repeated moves, one/two-item lists, exhausted gaps, collision avoidance, overflow protection, compaction, and close/reopen persistence;
- playlist-derived queue contains only available items, uses the playlist's real collection ID, assigns unique queue-item IDs, and remains unchanged after playlist reorder/removal/deletion;
- fake repositories obey the same reactive and atomic semantics as Room.

### ViewModel and Compose tests

- active collection loading/missing/error states and no literal-ID fallback;
- create/rename validation preserves user input and surfaces repository errors;
- selector row loading, tri-state semantics, persistent multi-playlist toggles, inline-create success/failure, and accessible announcements;
- required entry points emit the exact ordered payload, including nested folder resolution and queue duplicate collapse;
- playlist search keeps manual numbering, disables reorder, and does not alter stored order;
- unavailable rows are labeled, remain removable, and cannot be selected as a playback start;
- drag and accessible reorder controls produce identical persisted results;
- delete confirmation copy and source-file safety.

### Performance checks

- Open the selector with a representative large target set and many playlists without one full item query per playlist.
- Add/remove a large folder payload transactionally without blocking the main thread.
- Reorder and search a long playlist without eager artwork work or quadratic rank rewrites.

### Manual API 34+ flow

1. Create, duplicate-create, rename, and delete a playlist; confirm error copy and source-file safety.
2. Add one playing track from Now Playing and mini-player, then toggle it across multiple playlists without closing the selector.
3. Select nested folders plus individual files, verify the resolved unique count, add them, and confirm exact membership.
4. Add complete album and artist snapshots whose lists exceed the loaded Paging window.
5. Exercise all reorder controls and drag reorder, restart, and confirm order persists.
6. Filter playlist detail, confirm stored row numbers/order remain stable, and start playback from a filtered result.
7. Start a playlist, then reorder/remove items and delete the playlist; confirm the active queue does not change.
8. Mark storage unavailable and confirm membership remains visible, playback skips unavailable items with explanation, and availability restoration recovers the row.
9. Save a queue containing repeated media occurrences and confirm the UI explains unique-membership collapse.
10. Rotate, background, and recreate the Activity while dialogs/sheets are open; confirm no duplicate mutation or lost active collection.

Run the required verification on Windows PowerShell:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

## Milestone 7 boundary

Milestone 6 owns durable manual membership and playlist-to-explicit-queue snapshotting. It does not implement dynamic playlist rules or smart ordering. Milestone 7 may reuse the active-collection resolver, immutable visible-scope snapshot, seeded random abstraction, queue persistence, and playback start path, but generated queues remain explicit snapshots and are not written into manual playlist membership unless the user explicitly saves them through the Milestone 6 flow.
