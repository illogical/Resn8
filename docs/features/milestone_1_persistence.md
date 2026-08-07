# Milestone 1 Implementation Plan: Persist the Library Model

## Objective

Implement Room-backed local persistence for every MVP state defined by [SPECIFICATION.md](../SPECIFICATION.md), while preserving the framework-independent domain and repository seams established in Milestone 0. At the end of this milestone, Resn8 must have a versioned schema, production repository wiring, atomic persistence primitives, and tests that prove the database can safely support indexing, playback, ratings, playlists, queue generation, and process restoration in later milestones.

This milestone establishes durable storage and transaction semantics. It does not implement Storage Access Framework enumeration, metadata parsing, re-index matching policy, playback, smart-queue algorithms, or production library screens.

## Inputs from Milestone 0

Milestone 0 delivered domain models, repository interfaces, fake repositories, dependency injection seams, test fixtures, and placeholder navigation. Before Room entities are finalized, reconcile those contracts with the complete persistence requirements:

- `MediaFile` needs provider `documentId`, nullable unknown duration, `firstIndexedAt`, metadata scan status, and metadata-source provenance.
- `PlaybackHistory` needs a durable queue occurrence identity and a result that distinguishes incomplete, uncounted, threshold-counted, and natural-completion-counted occurrences.
- `SavedQueue` needs the active media identity, play intent, playback speed, repeat state, and creation/update timestamps in addition to ordered media IDs and position.
- Each saved queue item needs a stable queue-item ID, while the active playback checkpoint needs a separate occurrence ID so process restoration cannot earn a second meaningful play and a genuine replay traversal can.
- `PlaylistItem.position` should use a collision-resistant integer rank rather than floating-point midpoint insertion.
- Repository contracts need atomic scan, availability, meaningful-play, playlist-order, queue-snapshot, playback-checkpoint, and UI-session operations.

These are intentional Milestone 1 refinements to the Milestone 0 contracts, not parallel database-only models. Domain models remain pure Kotlin and Room annotations remain confined to the data layer.

## Responsibility Boundaries

| Milestone | Responsibility |
| --- | --- |
| Milestone 1 | Schema, identity constraints, converters, basic reactive reads, atomic persistence primitives, repository mapping, production database wiring, and persistence tests. |
| Milestone 2 | Folder selection, provider enumeration, metadata extraction, identity-match selection, rename recovery, scan orchestration, and scan progress/results. |
| Milestone 3 | Paged artist/album/track browsing, recursive folder presentation, search, and the complete deterministic sort/filter query matrix. |
| Milestones 4–5 | Playback ownership, listened-time accumulation, and deciding when an occurrence qualifies as a meaningful play. |
| Milestones 7–8 | Smart-queue generation and lifecycle-driven queue/UI restoration checkpoints using the persistence APIs created here. |

Milestone 1 may test transaction primitives with synthetic inputs. It must not duplicate the later policy that decides which provider document matches which existing media record or when playback crosses the meaningful-listen threshold.

## Proposed Changes

### T006 — Implement the Room Schema

#### 1. Reconcile domain contracts

Update the models under `com.app.resn8.domain.model` and repository interfaces under `com.app.resn8.domain.repository` before implementing Room mappings. Preserve fake implementations and extend their behavior so framework-free tests and previews remain possible.

Use explicit enums for persisted states rather than free-form strings:

- `CollectionProfile`
- `ScanStatus`
- `MetadataScanStatus`
- `MetadataValueSource` (`TAG`, `PATH`, `FILENAME`, or absent when no value exists)
- `SavedQueueKind`
- `SmartQueueMode`
- `PlaybackHistoryResult`
- `RepeatMode`
- `SortOrder`, including `RECENTLY_ADDED`

Persist enum names as stable strings. Renaming a persisted enum value requires a migration or an explicit compatibility mapping.

#### 2. Create Room entities

Create entities under `com.app.resn8.data.database.entity`. IDs and timestamps use the same types as the reconciled domain models. Timestamp fields are epoch milliseconds in UTC.

##### `CollectionEntity` — `collections`

- Fields: `id` (PK), `name`, `profile`, `createdAt`, `updatedAt`.
- The specification does not require unique collection names. Normalize and reject blank names in the repository without adding an undocumented uniqueness constraint.
- A collection is not deleted as part of any normal MVP flow. Do not rely on collection deletion to clean up unavailable storage.

##### `RootSourceEntity` — `root_sources`

- Fields: `id` (PK), `collectionId`, `treeUri`, `displayName`, `isAvailable`, `lastScanStatus`, nullable `lastScanStartedAt`, nullable `lastScanCompletedAt`, and nullable versioned `lastScanSummaryJson`.
- FK: `collectionId -> collections.id` with `RESTRICT`/`NO ACTION` deletion behavior.
- Indexes: `collectionId`; unique `treeUri` to prevent accidentally registering the same durable tree twice.
- A revoked grant, unavailable SD card, or missing provider updates `isAvailable`; it does not delete the source.

##### `FolderNodeEntity` — `folder_nodes`

- Fields: `id` (PK), `sourceId`, nullable `parentId`, `relativePath`, `displayName`.
- FKs: `sourceId -> root_sources.id` and `parentId -> folder_nodes.id`, both restrictive for normal deletion.
- Indexes: `sourceId`, `parentId`; unique `(sourceId, relativePath)`.
- The indexed root folder uses `relativePath = ""` and `parentId = null`.

##### Scan-operation entities — `scan_runs`, `staged_folders`, and `staged_media`

Use isolated persistent staging so a scan can write bounded batches, survive activity recreation, and fail or cancel without exposing a partially replaced canonical library:

- `ScanRunEntity`: `id` (PK), `sourceId`, `status`, `startedAt`, nullable `completedAt`, progress counts, and nullable versioned summary/error details. Index `(sourceId, status)` and restrict source deletion.
- `StagedFolderEntity`: scan-owned folder facts keyed uniquely by `(scanId, relativePath)`, including parent relative path, display name, and nullable resolved canonical folder ID.
- `StagedMediaEntity`: scan-owned provider/source/metadata facts with unique `(scanId, documentUri)`, including document ID, relative path, metadata provenance, and nullable resolved canonical media/folder IDs. Index scan-scoped document ID and relative path for T013 matching.
- Staging rows cascade only when their owning scan run is explicitly discarded. They are operational inputs and never appear in normal library queries.

Milestone 2 writes staging rows in bounded transactions and resolves each staged candidate to a new or existing stable domain ID. One publication transaction applies the resolved staging set to canonical folder/media tables, marks absent canonical media unavailable, updates the source scan summary, and then exposes the completed result. Cancellation or failure leaves the previously published canonical library unchanged and permits safe cleanup or retry.

An interrupted in-progress scan reuses its scan ID and idempotently resumes or restarts enumeration into the same staging set. User cancellation marks the run cancelled and deletes its staging rows. A failed run retains its summary/error record but deletes staging after diagnostics are recorded; retry creates a new scan ID. Successful publication marks the run complete and removes its staging rows in the publication transaction.

##### `MediaFileEntity` — `media_files`

- Identity/source fields: `id` (PK), `sourceId`, `folderId`, `documentUri`, nullable `documentId`, `relativePath`, `filename`, and `displayTitle`.
- Source facts: `mimeType`, `size`, nullable `durationMs`, `modifiedTimeMs`, `firstIndexedAt`, and `isAvailable`.
- Metadata state: `metadataScanStatus`, nullable `title`, `artist`, `albumArtist`, `album`, `discNumber`, `trackNumber`, `year`, `genre`, and `artworkUri`.
- Provenance: nullable source fields for each derived value whose origin T012 must report, at least title, artist, album artist, album, disc number, and track number.
- User state: `playCount` default `0`, nullable `lastPlayedAt`, and `likeScore` default `0`.
- FKs: `sourceId -> root_sources.id` and `folderId -> folder_nodes.id`, both restrictive. Normal scans never delete media rows.
- Identity indexes: unique `(sourceId, documentUri)`, unique `(sourceId, documentId)` when a document ID is present, and unique `(sourceId, relativePath)`.
- Query indexes: `sourceId`, `folderId`, `artist`, `album`, `(album, discNumber, trackNumber)`, `firstIndexedAt`, `playCount`, `lastPlayedAt`, `likeScore`, and `isAvailable`.

`firstIndexedAt` is assigned only when a new media identity is inserted. Metadata refreshes, unavailable/available transitions, and uniquely recovered renames preserve it. Unknown duration is stored as `null`, not a sentinel such as `0` or `-1`.

##### `PlaylistEntity` — `playlists`

- Fields: `id` (PK), `collectionId`, `name`, `normalizedName`, `createdAt`, `updatedAt`.
- FK: `collectionId -> collections.id` with restrictive deletion behavior.
- Indexes: `collectionId`; unique `(collectionId, normalizedName)` so names differing only by surrounding whitespace or case cannot bypass collection-scoped uniqueness.
- The repository trims the display name, derives `normalizedName` with one documented locale-independent rule, rejects blank names, and translates uniqueness failures into a domain-level result.

##### `PlaylistItemEntity` — `playlist_items`

- Composite PK: `(playlistId, mediaId)` to enforce unique membership.
- Fields: `position` as `Long`, `addedAt`.
- FKs: `playlistId -> playlists.id` with `CASCADE`; `mediaId -> media_files.id` with restrictive deletion behavior.
- Indexes: `mediaId`; unique `(playlistId, position)`.
- Allocate ranks with gaps, such as increments of `1_024`, and compact transactionally when no safe gap remains. Ordering always adds `mediaId` as the final deterministic tie-breaker while repairing legacy or corrupted data.

Deleting a playlist may cascade to its owned membership rows. It must never delete media or source files.

##### `PlaybackHistoryEntity` — `playback_history`

- Fields: `id` (PK), `mediaId`, `sessionOccurrenceId`, `startedAt`, nullable `endedAt`, `accumulatedListenedDurationMs`, `result`, and nullable `countedAt`.
- FK: `mediaId -> media_files.id` with restrictive deletion behavior.
- Indexes: `mediaId`, `startedAt`; unique `sessionOccurrenceId`.
- Non-negative listened duration is enforced by validated repository input and mutation queries that only add non-negative active-listening deltas.

The unique occurrence identity is the database backstop for “at most once per queue occurrence.” `result` distinguishes an occurrence still in progress, an ended occurrence that did not count, one counted at the threshold, and one counted by natural completion.

##### `SavedQueueEntity` — `saved_queues`

- Fields: `id` (PK), `collectionId`, `kind`, nullable `mode`, nullable versioned `filterSnapshotJson`, nullable `seed`, `currentIndex`, nullable `currentMediaId`, nullable `currentOccurrenceId`, `positionMs`, `playWhenReadyIntent`, `playbackSpeed`, `repeatMode`, `createdAt`, and `updatedAt`.
- FK: `collectionId -> collections.id` with restrictive deletion behavior.
- Index: `collectionId`, `updatedAt`.
- Defaults: index and position `0`, play intent `false`, speed `1.0`, repeat mode off.

The explicit item order is authoritative. `currentMediaId` duplicates the item at `currentIndex` intentionally so checkpoint and restoration code can detect a stale or inconsistent index. `currentOccurrenceId` identifies the present traversal of that item and survives process restoration. Entering the item again through repeat or a later traversal creates a new occurrence ID. An ordinary app launch restores the item ready but paused even when prior play intent was true; only Android's explicit media-resumption path may act on that intent.

##### `SavedQueueItemEntity` — `saved_queue_items`

- Composite PK: `(queueId, itemIndex)`.
- Fields: `queueItemId`, `mediaId`.
- FKs: `queueId -> saved_queues.id` with `CASCADE`; `mediaId -> media_files.id` with restrictive deletion behavior.
- Indexes: `mediaId`; unique `queueItemId`.
- Queue indices must be contiguous from zero after each snapshot replacement. The same media ID may appear more than once, but every explicit queue entry receives a distinct stable queue-item ID.

Deleting a queue may cascade to its owned item rows. Media becoming unavailable leaves the saved queue unchanged so it remains recoverable.

##### `UiSessionStateEntity` — `ui_session_state`

- Singleton PK constrained by repository convention to `id = 1`.
- Fields: typed/restorable `currentRoute`, nullable selected collection/folder/artist/album/playlist IDs, nullable `activeQueueId`, `activeSearchQuery`, `activeSort`, and nullable versioned `activeFilterSnapshotJson`.
- Nullable FKs use `SET NULL` for `selectedCollectionId -> collections.id`, `selectedFolderId -> folder_nodes.id`, `selectedPlaylistId -> playlists.id`, and `activeQueueId -> saved_queues.id`. Artist and album selections remain nullable text values because they are not separate entities.
- Persist route identity and small arguments, never serialized object graphs or URI permission state.

#### 3. Define deletion and retention policy

Foreign-key behavior must reinforce product semantics:

- Permission revocation, removable-storage absence, scan misses, and playback failures change availability only.
- Playlist deletion cascades only to `playlist_items`.
- Saved-queue deletion cascades only to `saved_queue_items`.
- Media, folders, roots, history, ratings, memberships, and queue references are not cascade-deleted during MVP operation.
- Permanent record purging is a separate confirmed maintenance feature outside MVP and must use an explicit top-down transaction with a preview of affected data.
- Production database construction must not use destructive migration fallback.

#### 4. Add converters and versioned JSON

Add `Converters.kt` for persisted enums. Serialize `QueueFilterSnapshot`, scan summary, and UI filter snapshot using Kotlin serialization with an explicit payload version. Decoding must either tolerate additive fields or return a controlled incompatibility result; it must not crash database initialization.

JSON is used only for bounded rule/filter snapshots that are restored as a whole. IDs, ordering, user statistics, relationships, and fields used for filtering or sorting remain relational columns.

#### 5. Add focused DAOs

Create DAOs under `com.app.resn8.data.database.dao`:

- `CollectionDao`: basic collection/root reads and writes; availability and scan-state updates.
- `ScanDao`: scan-run lifecycle, bounded staging inserts, identity-candidate reads, resolution updates, and cleanup of abandoned staging.
- `FolderDao`: folder upsert and source-scoped lookup by ID or relative path. Full recursive browsing belongs to T016/T018.
- `MediaFileDao`: identity-candidate lookups, basic reactive reads, bounded upsert primitives, availability updates, atomic signed-score mutation, and statistics mutation used by a higher-level transaction.
- `PlaylistDao`: playlist CRUD, conflict-safe membership changes, ordered item reads, rank updates, and compaction support.
- `PlaybackHistoryDao`: occurrence lookup/insert/finalization and result updates.
- `SavedQueueDao`: queue header/items reads, transactional snapshot replacement helpers, and guarded checkpoint updates.
- `UiSessionDao`: singleton upsert and observation.

DAOs expose database operations; repositories own domain validation and multi-DAO transactions. Do not build the complete artist/album/search/paging query matrix in this milestone.

#### 6. Configure `Resn8Database`

- Define all entities in `Resn8Database.kt` at database version `1`.
- Enable `exportSchema = true` and configure KSP to write schemas to `app/schemas`.
- Commit the exported version-1 schema and make it available to migration tests.
- Enable foreign keys and verify them in tests.
- Provide one production database builder using the application context and a test-only in-memory builder.
- Use suspend/Flow DAO APIs and never enable main-thread queries.
- Do not call `fallbackToDestructiveMigration` in production.

Version 1 is the initial schema, not a runtime `0 -> 1` migration. Its exported schema becomes the immutable baseline for every later migration.

### T007 — Implement Repository Transactions

#### 1. Add Room-backed repositories without removing test doubles

Implement Room-backed repositories under `com.app.resn8.data.repository` and retain the Milestone 0 fakes:

- `RoomCollectionRepository`
- `RoomMediaRepository`
- `RoomPlaylistRepository`
- `RoomQueueRepository`
- A UI-session repository if it remains separate from queue/session ownership

Mapping functions between domain and entity models are centralized and tested. Room types must not leak through domain repository interfaces.

#### 2. Define atomic persistence operations

##### Scan persistence primitives

Provide bounded transaction inputs that let Milestone 2:

- Start or resume a scan run and write isolated folder/media staging batches.
- Record the stable IDs selected by Milestone 2 identity matching without publishing those candidates early.
- Atomically publish a fully resolved scan: insert genuinely new identities with neutral user state and `firstIndexedAt = now`; refresh existing source facts, metadata, provenance, folder relationship, and availability without overwriting retained state; mark absent canonical media unavailable; and commit source scan status/timestamps/summary.
- Discard or retain failed/cancelled staging according to the documented retry policy without changing the canonical snapshot.

Milestone 2 selects identity matches and rename recovery candidates. Milestone 1 guarantees that applying the selected changes is safe and idempotent.

Do not use a blind whole-row `@Upsert` for existing canonical media because it could reset user-owned columns. Use distinct new-row insertion and existing-row source/metadata refresh queries, then publish with set-based SQL inside one transaction so work is not quadratic in library size.

##### Rating mutation

Use one SQL update to atomically add exactly `+1` or `-1`. Reject other deltas at the repository boundary. Concurrent actions must not lose increments, and scores may cross zero in either direction.

##### Meaningful-play commit

Expose an idempotent transaction keyed by `sessionOccurrenceId` that:

1. Verifies or creates the matching history occurrence.
2. Returns without incrementing again if that occurrence is already counted.
3. Finalizes the accumulated listened duration/result.
4. Increments `MediaFile.playCount` once and sets `lastPlayedAt` from an injected clock.
5. Commits the history and media changes atomically.

Threshold calculation and active-listening accumulation remain T028–T030 responsibilities. The persistence transaction accepts an already-qualified threshold or natural-completion result.

##### Playlist membership and order

- Bulk add ignores existing memberships and assigns new ranks in deterministic input order.
- Bulk remove and membership changes are atomic per requested playlist.
- Reorder, Move to Top, Move to Bottom, and compaction run transactionally.
- Playlist `updatedAt` changes when its name, membership, or manual order changes.
- Filtered presentation never rewrites the underlying manual order.

##### Queue snapshot and checkpoint

- Replacing a queue snapshot atomically writes the queue header and a contiguous ordered item list with stable queue-item IDs.
- A failed replacement leaves the previous queue intact.
- Checkpoint updates validate queue ID, non-negative index/position, and `currentMediaId` against the saved item at the index.
- Checkpoints update index, media ID, current playback occurrence ID, position, play intent, playback speed, repeat mode, and `updatedAt` without rewriting queue items.
- Restoring within the same traversal preserves `currentOccurrenceId`; entering an item through a genuine replay or repeat traversal creates and checkpoints a new occurrence ID.
- Unavailable media remains in the explicit snapshot.
- UI-session updates can point to the active queue and current browsing context in the same higher-level operation when consistency requires it.

#### 3. Wire the production container

- Change `DefaultAppContainer` to accept application `Context` or a database/repository factory and lazily construct the singleton Room database.
- Update `Resn8Application` to pass `applicationContext`.
- Keep fake repositories and provide a test container or constructor-injected repository set.
- Ensure unit tests can construct domain/use-case code without Android or Room.
- Ensure application recreation does not create multiple open database instances within one process.

### T008 — Test Persistence Invariants

#### 1. Test infrastructure

Add the required test dependencies and configuration during implementation:

- Room testing library.
- Robolectric and AndroidX test core for JVM in-memory Room tests, or move Android-dependent tests to `src/androidTest`; do not label plain JVM tests as Room tests without an Android runtime.
- KSP schema output and migration-test schema assets.
- Deterministic clock/ID providers and existing test fixtures.

Use in-memory databases for fast transaction tests and at least one file-backed/on-device database for close/reopen verification.

#### 2. Schema and identity tests

- Foreign keys are enabled and every child FK has an appropriate index.
- Duplicate root tree URIs, source-relative folder paths, and media identity keys are rejected or resolved through the documented repository result.
- Nullable document IDs and unknown durations round-trip correctly.
- Metadata provenance and scan status round-trip correctly.
- Bounded staging batches remain invisible to canonical library queries until publication.
- Cancelling or failing a scan leaves the prior canonical snapshot unchanged; retry and abandoned-stage cleanup are idempotent.
- Publishing a resolved scan changes folders, media, availability, and source scan summary atomically.
- New media receives `firstIndexedAt`; metadata refresh, unavailable/available transitions, and recovered rename updates preserve it.
- Persisted enum and versioned JSON values round-trip; unsupported payload versions fail controllably.

#### 3. Rating and meaningful-play tests

- Ratings cross zero through the UX Improvements v4 normative sequence `0 -> 1 -> 2 -> 1 -> 0 -> -1 -> -1`; older values below `-1` are normalized non-destructively.
- Concurrent rating updates do not lose increments.
- Invalid rating deltas are rejected.
- Play counts never become negative through any exposed operation.
- One occurrence increments `playCount` and writes `lastPlayedAt` exactly once.
- Retrying the same occurrence is idempotent; a different occurrence for the same media may count again.
- Injected failure between history and media operations rolls back both.
- Threshold-counted and natural-completion-counted results persist distinctly.

#### 4. Playlist tests

- Duplicate membership additions are ignored without disturbing position.
- Playlist-name uniqueness follows the documented normalization rule within a collection.
- Bulk adds preserve input order and allocate unique ranks.
- Reorder, Move to Top/Bottom, repeated midpoint insertion, and compaction preserve collision-free stable order across close/reopen.
- Playlist deletion removes membership rows but leaves media, history, source access, and source audio untouched.
- An unavailable media row retains playlist membership and order.

#### 5. Queue and restoration tests

- Queue replacement persists the exact explicit order, including repeated media IDs with distinct queue-item IDs.
- Item indices are contiguous and ordered after replacement.
- Snapshot replacement is atomic under injected failure.
- Checkpointing preserves queue items while updating index, current media ID, active playback occurrence ID, position, play intent, speed, and repeat state.
- Inconsistent index/media checkpoints and negative positions are rejected without corrupting the prior checkpoint.
- Unavailable media remains in the saved queue.
- Queue deletion removes only its owned item rows.
- Close/reopen restores queue-item IDs, the active playback occurrence ID, checkpoint, and UI-session state without autoplaying as a side effect of database loading.

#### 6. Retention and deletion tests

- Marking a root or media unavailable does not delete folders, media, ratings, statistics, history, playlist membership, or queue items.
- Restoring availability exposes the same retained identity and `firstIndexedAt`.
- Accidental attempts to delete referenced roots, folders, or media are restricted.
- No production database builder enables destructive migration fallback.

#### 7. Schema export and device tests

- The checked-in version-1 Room schema matches generated entities.
- `MigrationTestHelper` can create and validate the version-1 baseline for future migrations.
- A file-backed database survives close/reopen.
- On an API 34+ emulator/device, ratings, history, playlists, queue state, and UI session survive process kill and relaunch.
- Database Inspector confirms expected tables, indexes, foreign keys, and representative rows without being the sole source of verification.

## Verification Commands

Run from the repository root after implementation:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
.\gradlew.bat connectedDebugAndroidTest
```

If connected instrumentation is unavailable, record that limitation explicitly; it does not replace the required on-device exit check.

## Exit Criteria

Milestone 1 is complete only when:

- The exported version-1 schema represents every MVP persistence state in the specification.
- Domain contracts and Room mappings agree on nullability, defaults, identities, enums, and timestamps.
- File identity supports T013 without sacrificing user state or `firstIndexedAt`.
- Bounded scan ingestion, cancellation, retry, and atomic snapshot publication are represented and tested without exposing staging rows to library readers.
- Rating, meaningful-play, playlist-order, queue-snapshot, and checkpoint mutations are atomic and tested.
- A repeated meaningful-play commit for one occurrence cannot increment twice.
- Unavailable storage/media retains all user data and saved context.
- Only owned join/detail rows use cascade deletion; destructive source/media purging is outside MVP.
- Production uses Room repositories while tests can still substitute fakes.
- The database survives close/reopen and an on-device process restart without destructive migration behavior.
- Full unit, build, schema, and available instrumentation verification results are recorded.

## Reference Files

- [SPECIFICATION.md](../SPECIFICATION.md): Sections 2.2, 2.4–2.7, 3.4, 4.1–4.2, and 5.
- [TASKS.md](../TASKS.md): T006–T008 and downstream consumers T013, T016, T029, T035, T040, and T043–T045.
- [milestone_0_foundation.md](milestone_0_foundation.md): completed architecture and domain-contract foundation.
- Domain models: [MediaFile.kt](../../app/src/main/java/com/app/resn8/domain/model/MediaFile.kt), [Collection.kt](../../app/src/main/java/com/app/resn8/domain/model/Collection.kt), [Playlist.kt](../../app/src/main/java/com/app/resn8/domain/model/Playlist.kt), [SavedQueue.kt](../../app/src/main/java/com/app/resn8/domain/model/SavedQueue.kt), [PlaybackHistory.kt](../../app/src/main/java/com/app/resn8/domain/model/PlaybackHistory.kt), and [UiSessionState.kt](../../app/src/main/java/com/app/resn8/domain/model/UiSessionState.kt).
- Repository contracts: [MediaRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/MediaRepository.kt), [CollectionRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/CollectionRepository.kt), [PlaylistRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/PlaylistRepository.kt), and [QueueRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/QueueRepository.kt).
- Wiring and fixtures: [AppContainer.kt](../../app/src/main/java/com/app/resn8/di/AppContainer.kt), [Resn8Application.kt](../../app/src/main/java/com/app/resn8/Resn8Application.kt), and [TestFixtures.kt](../../app/src/test/java/com/app/resn8/fixtures/TestFixtures.kt).
