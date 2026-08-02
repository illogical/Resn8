# Milestone 2 Implementation Plan: Select and Index a Music Folder

## Objective

Implement T009-T015 from [TASKS.md](../TASKS.md): onboard one user-selected `MUSIC` root through the Storage Access Framework (SAF), recursively enumerate decoder-supported audio, extract and normalize metadata, apply deterministic path/filename fallbacks, stage and atomically publish an idempotent library snapshot, expose durable indexing UI states and manual re-indexing, and verify real `DocumentsProvider` behavior.

The result must satisfy [SPECIFICATION.md](../SPECIFICATION.md) while preserving the broader collection, folder, identity, playlist, queue, and restoration model described in [BRAINSTORM.md](../BRAINSTORM.md). A completed scan must never reset `firstIndexedAt`, ratings, listening history, playlist membership, or saved queue references, and a failed, cancelled, or interrupted scan must never expose a partial canonical snapshot.

Milestone 2 indexes playable audio only. It never requests broad storage permission, modifies source audio, schedules background indexing, or adds multiple-root management UI.

---

## Inputs from Milestone 0 and Milestone 1

Milestone 0 established pure domain contracts, repository seams, fake repositories, typed navigation, and the application container. Milestone 1 established the version-1 Room model and the retention rules that Milestone 2 must honor.

Milestone 2 builds on these existing types and APIs:

- **`RootSourceEntity` (`root_sources`)** stores the persisted tree URI, display name, availability, scan timestamps/status, and the versioned `lastScanSummary` value.
- **`FolderNodeEntity` (`folder_nodes`)** stores stable source-relative directory nodes. The selected root is represented by `relativePath = ""` and `parentId = null`.
- **`MediaFileEntity` (`media_files`)** stores stable media identity, content URI/document facts, relative path, nullable semantic metadata, non-null `displayTitle`, per-field provenance, availability, `firstIndexedAt`, and user-owned statistics.
- **`ScanRunEntity`, `StagedFolderEntity`, and `StagedMediaEntity`** isolate bounded scan writes from canonical library readers.
- **`MediaRepository` and `CollectionRepository`** expose scan lifecycle, staging/publication, root state, and availability operations. Production implementations use Room; unit tests retain framework-independent fakes.

### Required preflight: reconcile the implemented contracts

Before storage or UI work, compare the current Milestone 1 implementation with the contracts this milestone consumes. Update domain interfaces, Room implementations, converters, and fakes together where the existing implementation is narrower than the completed Milestone 1 design.

At minimum:

- Expand `ScanProgress` to represent `scanId`, phase/status, scanned folder count, inspected document count, discovered/admitted audio count, unsupported count, unreadable/error count, current relative path, and cancellation state. Totals that are unknowable during provider traversal must be nullable rather than fabricated.
- Expand `ScanResult` into an explicitly versioned, additive serialization payload containing scanned/admitted, added, updated, unavailable, tag-derived, path-derived, filename-derived, unrecognized, unreadable, unsupported, and elapsed-duration counts. Existing serialized summaries must decode safely when new fields are absent.
- Make scan status values typed at domain boundaries. Convert to persisted strings only in the Room layer if the schema still stores strings.
- Ensure `publishResolvedScan` uses its scan result and updates canonical folders/media, missing-media availability, `ScanRunEntity`, and `RootSourceEntity.lastScanStatus`/timestamps/summary in the same Room transaction.
- Add a source-scoped availability operation that atomically updates the root and every affected media row.
- Update `FakeMediaRepository`, `FakeCollectionRepository`, `AppContainer`, and test fixtures whenever these contracts change.
- Preserve Milestone 1 foreign keys and retention semantics. If correcting an implementation/schema mismatch is unavoidable, update the Room version, exported schema, and migration test rather than using destructive fallback.

Do not start the UI against speculative interfaces. Complete and test this contract reconciliation first.

---

## Responsibility and Downstream Boundaries

| Milestone | Responsibility |
| --- | --- |
| **Milestone 1 (completed baseline)** | Room entities, foreign keys, converters, repository seams, isolated staging, and atomic persistence primitives. |
| **Milestone 2 (this plan)** | Contract reconciliation, SAF onboarding, provider traversal, admission, metadata/fallback parsing, scan orchestration and recovery, deterministic identity resolution, atomic publication, indexing UI/manual re-indexing, and provider tests. |
| **Milestone 3** | Reactive/paged Artist, Album, All Tracks, and Folder browsing; search/sort/filter controls; 25,000-track query benchmarks. |
| **Milestones 4-8** | Playback, ratings/meaningful plays, playlists, generated queues, and context restoration. |
| **Post-MVP** | Multiple roots/collections, contextual/flat creation UI, scheduled re-indexing, source-file maintenance, and backup/restore. |

Milestone 2 must leave stable inputs for later tasks:

- Folder IDs remain stable by `(sourceId, relativePath)` so folder selection and restored UI context do not break after a scan.
- Media IDs remain stable across metadata refresh, temporary unavailability, and uniquely recovered renames so playlists, history, and saved queues remain valid.
- `artist`, `albumArtist`, `album`, disc, and track remain independently queryable; presentation fallbacks are not written into nullable semantic columns.
- `firstIndexedAt` changes only for genuinely new media and supports the future recently-indexed sort.
- Folder ancestry is complete and deterministic so selecting a folder can later resolve all currently indexed descendants.
- Publication must not require loading artwork or eagerly exposing large canonical lists. Keep the staging and resolver design suitable for at least 25,000 rows even though Milestone 3 owns query benchmarking.

---

## Implementation Order

Implement in this dependency order:

1. Reconcile scan/result/repository contracts and fakes.
2. Build pure admission, normalization, and fallback parsers with table-driven unit tests.
3. Build the SAF provider adapter and metadata extractor.
4. Build the scan coordinator, interruption recovery, and deterministic identity resolver.
5. Complete atomic snapshot publication and source-scoped availability operations.
6. Build onboarding, durable start routing, progress/recovery UI, and manual re-indexing.
7. Add provider-backed instrumentation, restart, and device verification.

Each slice must compile and have focused tests before the next slice depends on it.

---

## Proposed Changes

### T009 — Implement Folder Onboarding

#### 1. Launch the picker and validate durable access

- Integrate `ActivityResultContracts.OpenDocumentTree()` from a lifecycle-aware Compose host. Route the returned URI to an onboarding ViewModel/use case; do not perform persistence or scanning directly in a composable callback.
- Request only persistent read access:

  ```kotlin
  contentResolver.takePersistableUriPermission(
      treeUri,
      Intent.FLAG_GRANT_READ_URI_PERMISSION,
  )
  ```

- Catch `SecurityException` and validate the URI with a lightweight root-document query before creating database records. A returned URI is not considered usable until the grant exists in `ContentResolver.persistedUriPermissions` and the root can be queried.
- Resolve the default display name from the root document query. `DocumentFile.fromTreeUri(...).name` may be used only as a fallback convenience, not for recursive enumeration.
- If the picker is cancelled, remain in `FirstRun` without an error or partial database records.

#### 2. Name and register the single MVP collection/root

- After grant validation, show a collection-name prompt defaulted to the selected folder name. Trim input and reject a blank final name.
- On confirmation, create the single `MUSIC` collection if absent and register its one root source through repositories. Use repository-generated stable IDs; do not hard-code IDs in UI code.
- Prevent a second collection or second root from being registered through the MVP UI. Keep repository identifiers and APIs compatible with the post-MVP multiple-root task.
- Define cancellation of the naming prompt: create no collection/root, release the newly taken persistable permission when it is not already owned by a registered source, and return to `FirstRun`.
- Once a root is registered, retain it after an empty or failed initial scan so the user can retry or manually re-index without selecting it again.
- “Select Different Folder” is a narrowly scoped onboarding replacement permitted only before the first successful non-empty publication. Update the existing root's URI/display name in one repository transaction so its stable ID and the one-root invariant are preserved; clear prior operational scan rows, then release the old grant only after the new grant and database update succeed. If canonical media or user context already exists, use Re-grant/Retry instead and leave general root replacement for post-MVP management.

#### 3. Reconcile permission and storage availability

- On application start and before every scan, compare registered roots with current persisted grants and perform a lightweight provider query.
- Distinguish:
  - **Permission revoked:** no matching persisted read grant or a provider `SecurityException`.
  - **Storage/provider unavailable:** the grant exists but the provider/root cannot currently be opened.
- Atomically set `RootSource.isAvailable = false` and `MediaFile.isAvailable = false` for the source when either condition is confirmed. Preserve folders, media rows, statistics, history, playlists, and queues.
- Never publish an empty scan after a root-level access failure. An inaccessible root is an availability transition, not evidence that every file was removed.
- After access returns, set the source available only after validation and offer re-index. Matching media becomes available during successful publication.

---

### T010 — Build Recursive Enumeration

#### 1. Define a provider adapter

- Create a small injectable `DocumentTreeProvider` abstraction under `com.app.resn8.storage.indexer` around `ContentResolver`/`DocumentsContract`. Keep traversal tests independent of Android by testing against a fake implementation.
- Obtain the root ID with `DocumentsContract.getTreeDocumentId(treeUri)`.
- Query children with `DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)` and construct each document URI with `DocumentsContract.buildDocumentUriUsingTree(treeUri, childDocumentId)`.
- Query at least document ID, display name, MIME type, size, last modified, and flags. Treat provider values as untrusted: cursors may be null, columns or values may be absent, and size/modified time may be unknown.
- Close every cursor and file descriptor. Detect repeated directory document IDs within one traversal and skip/report them so a faulty provider cannot create a cycle.

#### 2. Traverse off the main thread

- Create `DocumentTreeScanner` using an iterative queue/stack on `Dispatchers.IO`; do not recursively call `DocumentFile.listFiles()`.
- Track folder paths relative to the selected root. Use `""` for the root and `/` as the canonical persisted separator regardless of provider/platform formatting.
- Store a media `relativePath` that includes its filename (for example, `Artist/Album/01 - Song.mp3`); store its parent directory separately through `resolvedFolderId`.
- Check `ensureActive()` before each provider query, while iterating large cursors, before metadata extraction, and before each staging write.
- Do not follow provider-specific links outside the granted tree.

#### 3. Centralize audio admission

- Implement one pure `AudioAdmissionPolicy`, shared by scanning and tests, based on the formats intended for Media3 playback.
- Initial MIME types: `audio/mpeg`, `audio/mp4`, `audio/aac`, `audio/flac`, `audio/ogg`, `audio/x-wav`, `audio/wav`, `audio/opus`, and `audio/x-matroska`.
- Initial case-insensitive extensions: `.mp3`, `.m4a`, `.aac`, `.flac`, `.ogg`, `.oga`, `.wav`, `.opus`, and `.mka`.
- Admit when a supported MIME type is present. When MIME is blank, generic (for example `application/octet-stream`), or provider-specific, allow the supported extension fallback. A specific unsupported MIME type is unsupported unless a tested provider exception is documented.
- Do not exclude dot-prefixed audio solely because it is hidden; the specification requires supported audio under the selected root.
- Exclude directories, known non-audio documents, and confirmed zero-byte files. Unknown/null size is not the same as zero and must not be rejected for that reason.
- Count unsupported documents without retaining raw filenames or paths in logs or persisted diagnostics.

#### 4. Stream bounded work with backpressure

- Use a bounded producer/consumer pipeline: enumerate document facts, extract/resolve metadata, then stage folders and media in batches. Default batch size may be 100 but must be configurable in tests.
- Do not accumulate the whole provider tree in UI state. Emit immutable aggregate `ScanProgress` snapshots.
- Stage a folder once per canonical relative path and make repeated writes idempotent for a scan ID.
- Report scanned folders, inspected documents, admitted audio, unsupported documents, unreadable/errors, and current relative path. Do not invent a percentage when the provider cannot supply a reliable total.

#### 5. Classify faults and cancellation

- A failed child query or unreadable document is recorded as a bounded diagnostic category and does not fail unrelated branches.
- A root query failure is fatal to that scan and follows the availability policy in T009.
- Cancellation marks the scan `CANCELLED`, purges its staging rows in a `NonCancellable` cleanup section, and preserves the prior canonical snapshot.
- Do not persist exception stack traces, content URIs, absolute paths, or user filenames in scan summaries or production logs.

---

### T011 — Extract and Normalize Metadata

#### 1. Extract off the main thread

- Create injectable `AudioMetadataExtractor` and Android `MediaMetadataRetriever` implementation under `com.app.resn8.storage.indexer`.
- Call `setDataSource(context, documentUri)` on `Dispatchers.IO`; release the retriever in `finally` for success, failure, and cancellation.
- Extract title, artist, album artist, album, disc, track, year, genre, duration, and embedded-artwork availability when supported.
- A readable admitted audio document whose metadata extraction fails remains indexable: set `MetadataScanStatus.FAILED`, keep duration nullable, apply filename/path fallbacks, and increment the metadata/unreadable diagnostic count as defined by `ScanResult`. A document that cannot be opened at all is skipped.

#### 2. Normalize without destroying display data

- Trim surrounding whitespace, convert blank strings to null, remove embedded NUL/control characters, and parse numeric values defensively.
- Parse values such as `1/12` as track `1`. Accept only positive, representable disc/track/year values; invalid values become null.
- Store unknown duration as null, never `0` or `-1`.
- Keep `artist` and `albumArtist` independent. Do not copy one into the other in storage. Milestone 3 may group albums with `albumArtist ?: artist` at the query/presentation boundary.
- Do not split or rewrite multi-artist tag text during this milestone.

#### 3. Apply per-field precedence

For each nullable semantic field, apply precedence independently so one valid tag does not block fallback for another missing field:

1. Valid embedded tag -> `MetadataValueSource.TAG`.
2. MUSIC path inference for artist/album -> `MetadataValueSource.PATH`.
3. Filename inference for disc/track/title -> `MetadataValueSource.FILENAME`.

`title` remains nullable semantic metadata. `displayTitle` is always populated with resolved title or, finally, the cleaned extensionless filename. Never store `Unknown Artist`, `Unknown Album`, or `Unknown Title`; those labels belong only to presentation.

#### 4. Define artwork behavior for later playback

- Do not store raw artwork bytes or transient `MediaMetadataRetriever` byte references in Room.
- For Milestone 2, set `artworkUri` only if artwork is copied to a deterministic app-private cache file keyed by stable media ID and written atomically. Otherwise leave `artworkUri = null` and record that embedded artwork extraction is deferred/on-demand.
- Delete or replace only app-owned cached artwork after successful publication. Never modify the source document.
- Tests must cover missing/corrupt artwork without requiring artwork for a successful media record.

---

### T012 — Implement Filename and Path Fallback Parsing

#### 1. Define root-relative MUSIC path semantics

Parse normalized directory components from the selected root, not from arbitrary absolute/provider paths:

| Relative media path | Inferred artist | Inferred album | Notes |
| --- | --- | --- | --- |
| `Artist/Album/01 - Song.mp3` | `Artist` | `Album` | Standard MUSIC layout. |
| `Artist/Album/Disc 1/01 - Song.mp3` | `Artist` | `Album` | First two root-relative components remain authoritative; deeper components are structural only. |
| `Album/01 - Song.mp3` | null | `Album` | Single directory is treated as album. |
| `01 - Song.mp3` | null | null | Root-level file uses filename fallback only. |

- Apply inferred values only when the corresponding embedded tag is absent/invalid.
- Empty, `.`/`..`, or otherwise invalid components produce no inferred metadata.
- Preserve the complete normalized folder hierarchy even when only the first two components supply MUSIC metadata.

#### 2. Parse filenames in deterministic order

Strip the final extension, trim the stem, then evaluate patterns in this order so a compact disc-track value is not consumed by a simpler track pattern:

1. **Separated disc and track:** `^(\d{1,2})[-._\s]+(\d{1,2})[-._\s]+(.+)$`, for example `1-01 Song` -> disc 1, track 1, title `Song`.
2. **Compact disc and track:** `^(\d{3,4})[-._\s]+(.+)$`, interpreting the last two digits as track and preceding digits as disc, for example `101 Song` -> disc 1, track 1 and `1201 Song` -> disc 12, track 1.
3. **Track only:** `^(\d{1,2})(?:[-._\s]+)(.+)$`, covering `01 Song`, `01 - Song`, and `01. Song`.
4. **No recognized prefix:** use the cleaned whole stem as `displayTitle`; semantic `title` may use the cleaned stem with `FILENAME` provenance when no tag title exists.

- Require positive disc/track values and non-blank remaining title text. If validation fails, continue to the next safe fallback rather than storing partial garbage.
- Replace separator underscores with spaces and collapse repeated whitespace for the cleaned display fallback, but otherwise preserve user-visible casing and punctuation.
- Keep parsing functions pure and table-driven.

#### 3. Record provenance and privacy-safe summaries

- Record provenance independently for title, artist, album artist, album, disc number, and track number.
- Use an explicitly versioned `ScanResult`/summary payload with aggregate counts.
- Do not persist a list of raw unrecognized paths or filenames. If diagnostics beyond counts are needed, store only bounded parser category identifiers such as `NO_PREFIX`, `INVALID_TRACK_ZERO`, or `AMBIGUOUS_COMPACT_PREFIX`.
- Do not include tree URIs, document IDs, filenames, relative paths, tags, or artwork in production logs or backup-oriented diagnostic payloads.

---

### T013 — Implement Idempotent Re-indexing

#### 1. Orchestrate one scan per source

- Create `IndexingCoordinator` (or equivalently named use case) that owns:

  `validate access -> start/recover scan -> enumerate/extract/stage -> resolve identities -> publish -> emit terminal result`

- Enforce at most one active scan per source with a process-local mutex plus a repository/database guard. A second request observes or returns the active scan rather than creating a competing run.
- Set root scan status/start time when the run starts. UI observes durable scan state plus in-process progress; composables do not own scan jobs.

#### 2. Recover interrupted scans safely

- On startup, inspect any `IN_PROGRESS` scan for the registered source.
- Reuse its scan ID as established by Milestone 1, but restart enumeration from the root because provider cursor/traversal position is not durably checkpointed.
- Before restarting, atomically clear that scan ID's staged rows and reset transient progress counts. Reusing the scan ID plus unique scan-scoped keys makes restart idempotent and prevents stale partial rows from being published when the tree changed during interruption.
- If access is no longer valid, retain the prior canonical snapshot, transition source/media availability per T009, and expose `PermissionRevoked` or `StorageUnavailable` with Retry/Reselect.
- User cancellation is terminal and creates a new scan ID on a later retry. Fatal scan failure records bounded error categories, removes staging, and creates a new scan ID on retry.

#### 3. Resolve stable folder IDs

- Match staged folders to canonical folders by `(sourceId, relativePath)`, including the root `""` node.
- Reuse matched folder IDs. Allocate IDs only for new paths, then resolve parent IDs from the same staged/canonical map.
- Retain any canonical folder referenced by retained unavailable media. Do not delete it merely because it was absent from the latest tree.
- Optional cleanup may delete only unreferenced stale folders, bottom-up, inside publication. Deferring such cleanup is acceptable for MVP; regenerating folder IDs is not.

#### 4. Resolve media identities deterministically

Resolve against one source-scoped pool containing both available and unavailable canonical media. A canonical ID may be claimed by at most one staged row.

1. **Provider identity:** match document URI and non-null document ID. If both independently match different canonical rows, record `IDENTITY_CONFLICT` and do not merge either record by guesswork; create a new identity only if uniqueness constraints permit, otherwise fail publication controllably.
2. **Relative path:** for still-unmatched rows, match the full canonical relative media path within the same source.
3. **Conservative signature recovery:** for still-unmatched rows, compare `(size, modifiedTimeMs, durationMs)` only when size and modified time are positive/known and duration is known and positive. Claim an unmatched canonical row only when exactly one candidate has the complete signature.
4. **New identity:** allocate a new media ID when no safe match exists.

- Remove each claimed canonical row from later candidate tiers so two staged rows cannot claim one identity.
- Never use title, artist, album, filename alone, or partial/unknown signatures for rename recovery.
- Duplicate content at different source URIs remains distinct unless the rules above recover one unique prior identity.
- A matching unavailable row is restored with the same ID and user data.

#### 5. Publish one canonical snapshot atomically

Perform all of the following in one Room transaction:

- Insert new folders/media in parent-before-child order. New media receives the injected clock value for `firstIndexedAt`, neutral statistics, and `isAvailable = true`.
- Refresh provider facts, path/folder, extracted metadata, provenance, metadata scan status, and availability for existing/returning media while preserving `firstIndexedAt`, `playCount`, `likeScore`, `lastPlayedAt`, history, playlist membership, and saved queue references.
- Mark unmatched canonical media for this source unavailable; never delete it.
- Retain folders required by unavailable media and stable UI references.
- Update scan-run terminal status/counts and `RootSource` availability/status/completion timestamps/versioned summary.
- Remove only the completed scan's staging rows.

Canonical library observers must see either the previous snapshot or the complete new snapshot. The repository may page/read staging internally; do not require UI or the coordinator to materialize multiple unbounded 25,000-row object graphs.

#### 6. Define idempotence precisely

Two successful scans of an unchanged tree must produce:

- No duplicate media or folders.
- The same stable media/folder IDs, `firstIndexedAt`, user statistics, memberships, history, and queue references.
- Equivalent canonical source/metadata/availability values.

Scan-run records, root scan timestamps, elapsed duration, and progress/summary execution facts are expected to change and are excluded from byte-for-byte idempotence.

---

### T014 — Build Indexing UI States

#### 1. Use unidirectional durable state

- Add onboarding/indexing ViewModels and use cases wired through `AppContainer`; inject provider, extractor, clock, and coordinator dependencies for tests.
- Keep typed routes and small IDs only. Never place a tree URI, permission state, scan object graph, or exception in a route.
- Derive application start routing from registered collection/root state, persisted grant validation, root availability, and scan state. Do not hard-code Library as the effective first-run destination.
- Keep scan work in the ViewModel/coordinator so configuration changes recreate UI without starting a second scan.

#### 2. Represent all required states

Use a sealed `IndexingUiState` (or equivalent state plus effects) covering:

- **`Loading`** while durable onboarding/source state is resolved.
- **`FirstRun`** with local-only privacy, one-root explanation, and “Select Music Folder”.
- **`FolderNaming`** with default name, validation, confirm, and cancel behavior.
- **`Scanning`** with indeterminate progress, phase, scanned folders, inspected documents, admitted audio, unsupported count, unreadable/error count, current relative path, and Cancel.
- **`CompleteSummary`** with added, updated, restored, unavailable, tag/path/filename-derived, unsupported, and unreadable counts plus “Continue to Library”.
- **`EmptyFolder`** retaining the registered root and offering Retry/Re-index and Select Different Folder.
- **`PermissionRevoked`** with Reselect/Grant Access.
- **`StorageUnavailable`** with Retry while retaining all context.
- **`Interrupted`** while an existing scan is safely restarted, or with an explicit Retry if automatic restart cannot begin.
- **`ScanError`** with a user-safe category/message and Retry; do not display raw provider paths or stack traces.

One-shot picker launch/navigation events must not be replayed after configuration changes.

#### 3. Add manual re-indexing

- Add a visible manual “Re-index” action from the library/source surface after onboarding, as required by the Milestone 2 exit criteria.
- Disable or convert it to “View progress” while that source already has an active scan.
- Show the prior canonical library during re-indexing and publish changes only at completion.
- Scheduled/background indexing remains post-MVP.

#### 4. Accessibility and adaptive behavior

- Give progress/status controls useful semantics and announce terminal state changes without repeatedly announcing every path update.
- Meet minimum touch targets, logical focus order, font scaling, and non-color-only error/status communication.
- Support phone portrait and landscape. Avoid claiming tablet-specific optimization beyond graceful adaptive sizing, which the specification leaves for later.

---

### T015 — Test Real Provider Behavior

#### 1. Pure unit tests

Add table-driven tests for:

- MIME/extension admission, generic MIME fallback, uppercase extensions, hidden audio, known zero-byte files, and unknown size.
- Tag normalization, blank/control-character handling, numeric parsing, nullable duration, independent per-field precedence, and metadata extraction failure fallback.
- Root-relative path cases including standard, deep disc folder, one-folder album, and root-level file.
- Filename patterns `01 Song`, `01 - Song`, `01. Song`, `1-01 Song`, `101 Song`, `1201 Song`, zero/invalid values, ambiguity, and no-prefix fallback.
- Per-field provenance and privacy-safe summary categories.
- Identity conflicts, unavailable return, one-to-one candidate claiming, ambiguous/partial signatures, and duplicate-content separation.
- Coordinator single-scan enforcement, cancellation, fatal root failure, interrupted restart, and immutable progress/state transitions.

#### 2. Room/repository integration tests

Verify:

- Bounded staging remains invisible until publication.
- Initial scan inserts stable folder/media IDs and neutral user values.
- An unchanged second scan preserves canonical identity and user-owned fields.
- Changed tags refresh only source/extracted fields.
- Missing media becomes unavailable while its folder, memberships, history, and queues remain intact.
- Returning and uniquely renamed media restores the canonical ID and user state.
- Stale folders referenced by unavailable media are retained; unreferenced cleanup, if implemented, is bottom-up.
- Root/media unavailability is atomic and a provider failure cannot publish an empty snapshot.
- Successful publication atomically updates folders, media, scan run, root status/summary, and staging cleanup.
- Cancellation/failure cleanup leaves the prior snapshot unchanged, including when cleanup begins from a cancelled coroutine.
- Interrupted restart reuses the scan ID, clears stale staging, and safely republishes.
- Versioned summaries round-trip with missing/additive fields.

#### 3. Provider-backed instrumentation tests

- Implement or configure a controllable test `DocumentsProvider`; do not claim real provider coverage from mocks alone.
- Cover nested queries, null/missing metadata columns, generic MIME types, inaccessible documents/subtrees, repeated document IDs, metadata-open failure, grant validation, and provider disappearance/reappearance.
- Verify activity recreation does not duplicate scans and app process recreation restarts an interrupted scan without exposing staging.
- Verify persisted access and a successful manual re-index after app restart.

#### 4. Device/provider acceptance matrix

Record results on API 34+ for:

| Environment | Required evidence |
| --- | --- |
| Emulator/test provider | Nested indexing, persisted grant after app restart, cancellation, provider failure, and re-index. |
| Internal shared storage provider | Real folder selection, nested tagged/untagged audio, app restart, and manual re-index. |
| Physical removable storage or equivalent provider | Device restart, unavailable media retention when removed/unmounted, and recovery after remount/reselection. |

If CI lacks a connected device or removable-storage equivalent, compilation is still required and the unexecuted device checks must be recorded explicitly; they cannot be reported as passing.

---

## File-Level Handoff

The coding assistant should expect to create or modify files in these areas. Exact class splitting may follow project conventions, but responsibilities must remain separated and injectable.

- `domain/model/ScanStatus.kt`: typed progress/result/status contracts and versioned summary semantics.
- `domain/repository/MediaRepository.kt` and `CollectionRepository.kt`: scan recovery/publication and source-scoped availability operations.
- `data/database/dao/ScanDao.kt`, `MediaFileDao.kt`, `FolderDao.kt`, and `CollectionDao.kt`: staging reads, matching support, availability updates, and atomic publication inputs.
- `data/repository/RoomMediaRepository.kt` and `RoomCollectionRepository.kt`: transaction and mapping implementation.
- `data/repository/FakeMediaRepository.kt` and `FakeCollectionRepository.kt`: behaviorally useful fakes, not empty scan no-ops.
- `storage/indexer/`: provider adapter, admission policy, scanner, extractor, normalizer/parser, identity resolver, and coordinator.
- `di/AppContainer.kt`: injectable production and test wiring.
- `ui/onboarding/`, `ui/indexing/`, and existing typed navigation/screen files: durable state, picker effects, progress/recovery, and manual re-index.
- `src/test` and `src/androidTest`: pure, Room, Compose, provider, lifecycle, and restart coverage.

Before editing a named file, inspect its current contents and preserve unrelated worktree changes.

---

## Verification Commands

Run from the repository root:

```powershell
# Pure unit, parser, coordinator, and Room/Robolectric tests
.\gradlew.bat testDebugUnitTest

# Static checks and debug compilation, including Room/KSP output
.\gradlew.bat lintDebug assembleDebug

# Ensure instrumentation sources compile even when no device is attached
.\gradlew.bat compileDebugAndroidTestKotlin

# Provider, Compose/lifecycle, and on-device tests; requires a connected API 34+ target
.\gradlew.bat connectedDebugAndroidTest
```

Also inspect the exported Room schema diff whenever entities, indexes, or converters change. Do not accept destructive migration fallback, skipped failing tests, or a connected-test command that passed only because no relevant tests existed.

---

## Exit Criteria

Milestone 2 is complete only when all of the following are demonstrated:

1. A first-run user can select one root, persist read access, name the default `MUSIC` collection, and restart the app without repeating onboarding.
2. Nested supported audio is enumerated and metadata-extracted off the main thread through bounded work; provider anomalies do not crash unrelated traversal.
3. Tag values win per field, MUSIC path/filename fallbacks are deterministic, missing semantic values remain null, and `displayTitle` is always usable.
4. Scan summaries are versioned, privacy-safe, and contain the counts required by the specification and UI.
5. Folder and media identities remain stable; ambiguous identity evidence never causes a guessed merge.
6. Successful publication is one atomic canonical snapshot and preserves all user-owned data and references.
7. Missing media and unavailable roots are retained with `isAvailable = false`; restored access/files recover without losing identity or context.
8. Cancellation, fatal failure, configuration change, and process interruption leave the prior snapshot intact and follow the documented cleanup/restart policy.
9. The UI covers first run, naming, progress with required counts, cancellation, completion, empty root, interruption, permission loss, unavailable storage, safe errors, and manual re-indexing.
10. Unit, Room, build/lint, instrumentation compilation, provider-backed tests, and the applicable device/provider acceptance matrix pass or have explicit recorded device-only limitations.

---

## Reference Files

- [SPECIFICATION.md](../SPECIFICATION.md): Sections 1, 2.1-2.4, 3.1, 4.1-4.4, and 5.
- [TASKS.md](../TASKS.md): T009-T020, T023, T040, T046, T048-T050, and T053 for current and downstream constraints.
- [BRAINSTORM.md](../BRAINSTORM.md): Library shapes, folder inspection, manual re-indexing, metadata questions, and downstream folder selection.
- [milestone_1_persistence.md](milestone_1_persistence.md): Version-1 schema, retention rules, scan staging/publication, stable IDs, and interrupted-scan policy.
- [milestone_0_foundation.md](milestone_0_foundation.md): Domain/repository seams, typed navigation, fakes, and dependency container.
- [ScanStatus.kt](../../app/src/main/java/com/app/resn8/domain/model/ScanStatus.kt)
- [StagedModels.kt](../../app/src/main/java/com/app/resn8/domain/model/StagedModels.kt)
- [MetadataEnums.kt](../../app/src/main/java/com/app/resn8/domain/model/MetadataEnums.kt)
- [MediaFile.kt](../../app/src/main/java/com/app/resn8/domain/model/MediaFile.kt)
- [Collection.kt](../../app/src/main/java/com/app/resn8/domain/model/Collection.kt)
- [MediaRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/MediaRepository.kt)
- [CollectionRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/CollectionRepository.kt)
- [RoomMediaRepository.kt](../../app/src/main/java/com/app/resn8/data/repository/RoomMediaRepository.kt)
- [RoomCollectionRepository.kt](../../app/src/main/java/com/app/resn8/data/repository/RoomCollectionRepository.kt)
- [ScanDao.kt](../../app/src/main/java/com/app/resn8/data/database/dao/ScanDao.kt)
- [AppContainer.kt](../../app/src/main/java/com/app/resn8/di/AppContainer.kt)
