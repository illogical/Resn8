# Milestone 2 Implementation Plan: Select and Index a Music Folder

## Objective

Implement folder selection onboarding, recursive DocumentProvider enumeration, metadata extraction and normalization, filename/path fallback parsing, idempotent staging and snapshot re-indexing, indexing UI states, and SAF provider tests as specified in [SPECIFICATION.md](../SPECIFICATION.md) and [TASKS.md](../TASKS.md). 

Building upon the Room persistence foundation established in [Milestone 1](milestone_1_persistence.md), Milestone 2 enables Resn8 to onboard a user-selected local root folder via Storage Access Framework (`ACTION_OPEN_DOCUMENT_TREE`), persist tree permissions, recursively discover supported audio files off the main thread, extract embedded tags with fallback heuristics, and publish resolved library snapshots without disturbing user statistics, ratings, or existing playlists.

---

## Inputs from Milestone 0 and Milestone 1

Milestone 0 established domain model contracts, repository interfaces, fake repositories, and placeholder navigation. Milestone 1 created the complete version-1 Room persistence schema, entity relations, DAOs, and atomic transaction primitives.

Milestone 2 directly utilizes the following Milestone 1 persistence entities and APIs:
- **`RootSourceEntity` (`root_sources`)**: Stores persistent tree URI, display name, availability (`isAvailable`), and scan execution facts (`lastScanStatus`, `lastScanStartedAt`, `lastScanCompletedAt`, `lastScanSummaryJson`).
- **`FolderNodeEntity` (`folder_nodes`)**: Persists indexed relative directory hierarchy (`relativePath`, `parentId`) scoped to a `sourceId`.
- **`MediaFileEntity` (`media_files`)**: Canonical media table storing document URIs/IDs, relative paths, metadata, provenance (`MetadataValueSource`), availability (`isAvailable`), user statistics (`playCount`, `likeScore`, `lastPlayedAt`), and initial timestamp (`firstIndexedAt`).
- **`ScanRunEntity` (`scan_runs`)**, **`StagedFolderEntity` (`staged_folders`)**, and **`StagedMediaEntity` (`staged_media`)**: Isolated persistent staging schema that allows scans to write bounded batches off the main thread, perform identity matching, survive process recreation, and fail or cancel without exposing partial scan results to library readers.
- **`ScanDao` & Repository Snapshot Publication (`publishResolvedScan`)**: Atomic transaction primitive that publishes resolved staging records, inserts new media (`firstIndexedAt = now`), refreshes existing source metadata/availability while preserving ratings and history, marks absent media as unavailable (`isAvailable = false`), and cleans up staging rows.

---

## Responsibility Boundaries

| Milestone | Responsibility |
| --- | --- |
| **Milestone 1 (Completed)** | Room schema, entity mappings, identity constraints, converters, atomic DAOs, repository transaction primitives, and database persistence tests. |
| **Milestone 2 (This Plan)** | Folder selection (`ACTION_OPEN_DOCUMENT_TREE`), persistent Uri grants, recursive tree traversal, audio filtering, metadata extraction & normalization, path/filename fallback parsing, scan summary generation, identity candidate matching, scan orchestration, indexing UI states, and provider tests. |
| **Milestone 3 (Next)** | Reactive library browsing (Artist, Album, All Tracks, Folder tree), search, sort/filter controls, and 25k-track performance optimization. |
| **Milestone 4–5** | Playback service (Media3 / ExoPlayer), Now Playing UI, audio focus, listening duration tracking, and meaningful-play commits. |
| **Milestones 6–8** | Manual playlists, smart randomized queue generation, context/queue restoration, and final MVP acceptance. |

Milestone 2 relies on Milestone 1 repositories and DAOs for staging and atomic snapshot publication. It must not duplicate database schema definitions, alter foreign key constraints, or bypass the established persistence layer.

---

## Proposed Changes

### T009 — Implement Folder Onboarding

#### 1. Launch SAF Folder Picker and Persist Read Permission
- Integrate Jetpack Compose launcher for `ActivityResultContracts.OpenDocumentTree()` in the onboarding workflow.
- Upon receiving a valid root tree `Uri`:
  ```kotlin
  val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
  context.contentResolver.takePersistableUriPermission(treeUri, takeFlags)
  ```
- Retrieve folder display name using `DocumentFile.fromTreeUri(context, treeUri)?.name` or ContentResolver document query fallback.

#### 2. Initialize Collection and RootSource
- Wire repository calls to create the default `MUSIC` collection (`CollectionProfile.MUSIC`) if one does not already exist:
  - `Collection`: stable ID (e.g. `"col_music_default"`), display name (user-configured or defaulted to root folder name), `profile = CollectionProfile.MUSIC`.
- Register the root source in Room via `CollectionRepository` / `RootSourceEntity`:
  - `RootSource`: `id = UUID`, `collectionId`, `treeUri = treeUri.toString()`, `displayName`, `isAvailable = true`, `lastScanStatus = ScanStatus.IDLE`.

#### 3. Handle Permission Losses and User Cancellation
- If the user cancels the picker, remain cleanly in the onboarding explanation state without showing an error or persisting partial entities.
- Provide a utility to verify active persisted permissions via `context.contentResolver.persistedUriPermissions`. If permission is missing or revoked on app launch, update `RootSource.isAvailable = false` and prompt the user to reselect or re-grant access.

---

### T010 — Build Recursive Enumeration

#### 1. Non-Blocking Tree Traversal
- Create `DocumentTreeScanner` under `com.app.resn8.storage.indexer` running strictly on `Dispatchers.IO`.
- Avoid slow recursive `DocumentFile.listFiles()` calls which make individual synchronous IPC queries per document.
- Use low-level `ContentResolver.query` on `DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)`:
  - Query columns: `Document.COLUMN_DOCUMENT_ID`, `Document.COLUMN_DISPLAY_NAME`, `Document.COLUMN_MIME_TYPE`, `Document.COLUMN_SIZE`, `Document.COLUMN_LAST_MODIFIED`, `Document.COLUMN_FLAGS`.
  - Maintain a queue/stack of folder document IDs to traverse nested subdirectories recursively.
  - Build relative paths (e.g., `""` for root, `"Artist/Album"` for nested subfolders).

#### 2. Audio File Filtering and Format Admission
- Filter candidate documents against supported audio formats:
  - **MIME types**: `audio/mpeg`, `audio/mp4`, `audio/aac`, `audio/flac`, `audio/ogg`, `audio/x-wav`, `audio/opus`, `audio/x-matroska`.
  - **Extension fallbacks**: `.mp3`, `.m4a`, `.aac`, `.flac`, `.ogg`, `.wav`, `.opus`, `.oga`, `.mka`.
- Exclude hidden files (starting with `.`), non-audio documents, and zero-byte files.

#### 3. Streaming Bounded Staging Batches
- Stream admitted audio items and discovered folders in bounded batches (e.g., 100 items per batch).
- Write batches to `ScanDao` persistent staging tables: `staged_folders` and `staged_media` tied to a unique `scanId`.
- Emit live `ScanProgress` updates (`scannedFolderCount`, `discoveredAudioCount`, `currentRelativePath`, `status = SCANNING`).

#### 4. Fault Tolerance and Cancellation
- Wrap individual document inspection in try-catch blocks to catch `SecurityException`, `FileNotFoundException`, or corrupt directory nodes. Skip unreadable items, record error details into the scan run summary, and continue scanning remaining documents.
- Respect coroutine cancellation (`coroutineContext.ensureActive()`). On cancellation, mark `ScanRunEntity.status = CANCELLED`, clean up staging rows, and leave the prior canonical library snapshot intact.

---

### T011 — Extract and Normalize Metadata

#### 1. Off-Main-Thread Metadata Extraction
- Create `AudioMetadataExtractor` under `com.app.resn8.storage.indexer` using `MediaMetadataRetriever` (or Media3 format utilities) on `Dispatchers.IO`.
- Extract embedded tags for each admitted audio document:
  - `METADATA_KEY_TITLE`
  - `METADATA_KEY_ARTIST`
  - `METADATA_KEY_ALBUMARTIST`
  - `METADATA_KEY_ALBUM`
  - `METADATA_KEY_DISC_NUMBER`
  - `METADATA_KEY_CD_TRACK_NUMBER`
  - `METADATA_KEY_YEAR`
  - `METADATA_KEY_GENRE`
  - `METADATA_KEY_DURATION`
  - Embedded artwork existence / byte reference.

#### 2. Metadata Precedence Rules
For each track in a `MUSIC` collection, resolve display fields using this strict precedence:
1. **Valid embedded ID3/audio tags**: If present and non-blank, use the tag value and record provenance as `MetadataValueSource.TAG`.
2. **Path and Filename Inference**: If embedded tags are missing, infer artist/album from relative folder structure (`Artist/Album/...`) and track number from leading filename numbers (see T012). Record provenance as `MetadataValueSource.PATH` or `MetadataValueSource.FILENAME`.
3. **Cleaned Filename Fallback**: If title is missing, clean the filename (remove extension, replace underscores with spaces, trim). Record provenance as `MetadataValueSource.FILENAME`.

#### 3. Database Sanitization and Nullability Rules
- **DO NOT** store string fallbacks like `"Unknown Artist"`, `"Unknown Album"`, or `"Unknown Title"` in database columns. Store `null` when a value cannot be extracted or inferred. Fallback strings are applied only at presentation UI boundaries.
- Parse and sanitize numeric track/disc values (e.g., parse `"1/12"` to `trackNumber = 1`, `"2"` to `discNumber = 2`).
- Store unknown duration as `null` (not `0` or `-1`).

---

### T012 — Implement Filename and Path Fallback Parsing

#### 1. Relative Path Parsing for Music Collections
- Inspect relative path components relative to the selected root:
  - Path `Artist/Album/01 - Track.mp3`:
    - Component 2 levels up (`Artist`) -> inferred `artist` if embedded artist tag is absent.
    - Component 1 level up (`Album`) -> inferred `album` if embedded album tag is absent.
  - Path `Album/01 - Track.mp3` (single directory level):
    - Component 1 level up (`Album`) -> inferred `album` if tag absent. `artist` remains `null`.

#### 2. Filename Prefix Matching Heuristics
Parse leading track and disc patterns from filenames:
- **Disc and Track prefix**: `^(\d{1,2})[-._\s]+(\d{1,2})[-._\s]+(.+)$` (e.g., `1-01 Song Title.mp3` -> Disc 1, Track 1, Title `"Song Title"`).
- **Track number prefix**: `^(\d{1,2})[-._\s]+(.+)$` or `^(\d{1,2})\.\s*(.+)$` (e.g., `01 - Song Title.mp3`, `01. Song Title.mp3` -> Track 1, Title `"Song Title"`).
- **Combined Disc-Track prefix**: `^(\d{3,4})[-._\s]+(.+)$` (e.g., `101 Song Title.mp3` -> Disc 1, Track 01).

#### 3. Metadata Provenance & Pattern Summary
- Track exact provenance (`MetadataValueSource`) per derived field:
  - `TAG`: Derived from embedded metadata tags.
  - `PATH`: Inferred from folder hierarchy.
  - `FILENAME`: Inferred from filename track/title parsing.
- Compute scan metrics and build versioned `ScanSummary` JSON for `lastScanSummaryJson`:
  - `totalDiscoveredFiles`
  - `tagDerivedCount`
  - `pathDerivedCount`
  - `filenameDerivedCount`
  - `unrecognizedPatternCount`
  - `unreadableFileCount`
  - `unsupportedFileCount`
  - List of unrecognized path/filename patterns for diagnostic analysis.

---

### T013 — Implement Idempotent Re-indexing

#### 1. Scan Staging and Identity Candidate Matching
During enumeration, staging rows (`staged_media`) are populated with document URIs, document IDs, relative paths, file size, modified time, duration, and extracted metadata.

Before atomic snapshot publication, perform three-tier candidate matching to map each `staged_media` item to a canonical `media_files.id`:
1. **Tier 1 — Document ID / URI Match**: Match `staged_media.documentUri` or `documentId` against existing `media_files.documentUri` / `documentId` within the same `sourceId`.
2. **Tier 2 — Relative Path Match**: For unmatched staged items, match `staged_media.relativePath` against existing `media_files.relativePath` within the same `sourceId`.
3. **Tier 3 — Signature Recovery Match**: For remaining unmatched staged items, attempt conservative rename recovery by matching `(size, modifiedTimeMs, durationMs)` uniquely against unmatched canonical `media_files` in the same `sourceId`. If exactly one canonical record matches, map the staged item to that existing `media_id` (recovering renamed files).

#### 2. Atomic Snapshot Publication (`publishResolvedScan`)
Execute snapshot publication inside one database transaction via `ScanDao` / repository:
- **New Files**: Insert new `MediaFileEntity` rows with `firstIndexedAt = System.currentTimeMillis()`, `playCount = 0`, `likeScore = 0`, `lastPlayedAt = null`, and `isAvailable = true`.
- **Existing Files**: Refresh document URI, relative path, file size, modified time, extracted metadata, provenance, folder ID, and `isAvailable = true`. **STRICTLY PRESERVE** `firstIndexedAt`, `playCount`, `likeScore`, `lastPlayedAt`, and playlist memberships.
- **Missing Files**: For any canonical `media_files` in the source not present in the published scan staging, set `isAvailable = false`. **DO NOT** delete media rows or cascade-delete playlists/history.
- **Folder Hierarchy**: Update `folder_nodes` to match the newly published directory tree.
- **Root Source State**: Update `RootSourceEntity` (`lastScanStatus = SUCCESS`, `lastScanCompletedAt = System.currentTimeMillis()`, `lastScanSummaryJson = serializedSummary`).
- **Staging Cleanup**: Purge `staged_media` and `staged_folders` rows for the completed `scanId`.

#### 3. Error and Cancellation Handling
- If a scan fails or is cancelled, update `ScanRunEntity.status` (`FAILED` or `CANCELLED`), delete its staging rows, and leave the prior canonical library snapshot unchanged.
- Re-indexing must be completely idempotent: running a scan twice on an unchanged folder produces identical database state without duplicate rows or altered timestamps.

---

### T014 — Build Indexing UI States

#### 1. UI Navigation and Screen Architecture
Create onboarding and indexing Compose UI components under `com.app.resn8.ui.onboarding` and `com.app.resn8.ui.indexing`, integrated into the app navigation graph (`NavGraph.kt`).

#### 2. State Representation
Support all indexing UI states via a unified `IndexingUiState`:
- **`FirstRun`**: Explains local-only privacy, SAF folder selection, and features a prominent "Select Music Folder" button.
- **`FolderNamingModal`**: Allows the user to review and customize the collection display name before indexing begins.
- **`Scanning`**: Displays real-time scan progress:
  - Progress indicator / spinner.
  - Live counters: Discovered audio tracks, scanned folders.
  - Current relative folder path being processed.
  - "Cancel Scan" action button.
- **`CompleteSummary`**: Shows a summary card upon scan completion:
  - Total tracks added, updated, and missing/unavailable.
  - Counts of tag-derived vs. path/filename-derived metadata.
  - Unrecognized patterns or unreadable file summary (if any).
  - "Continue to Library" action button.
- **`EmptyFolder`**: Rendered when no supported audio files are found in the selected root. Offers a clear explanation and "Select Different Folder" button.
- **`PermissionRevoked` / `StorageUnavailable`**: Rendered when root folder permission is lost or removable storage is unmounted. Displays "Grant Access" and "Retry" actions.
- **`ScanError`**: Rendered on fatal scan failure with error details and a "Retry Indexing" action.

#### 3. Accessibility and Adaptive Layout
- Include semantic content descriptions for progress bars, counters, and status icons.
- Ensure layouts adjust gracefully across phone portrait, landscape, and tablet screen dimensions.

---

### T015 — Test Real Provider Behavior

#### 1. Unit & Integration Test Suite
Add test coverage under `app/src/test/java/com/app/resn8/storage/` and `app/src/androidTest/java/com/app/resn8/storage/`:
- **Folder Onboarding Tests**: Verify persistent Uri permission requests, collection creation, and cancellation handling.
- **Recursive Enumeration Tests**: Verify directory traversal with nested subfolders, audio format filtering, non-audio exclusion, and bounded batching.
- **Metadata Extraction & Fallback Tests**:
  - Test embedded ID3 tag extraction.
  - Test `Artist/Album/Track - Title.mp3` path & filename fallback parsing.
  - Test common track prefix regexes (`01 - Title`, `1-01 Title`, `101 Title`).
  - Test provenance recording (`TAG`, `PATH`, `FILENAME`).
  - Test nullability rules (verify `"Unknown Artist"` is NOT stored in DB).
- **Idempotent Re-indexing Tests**:
  - Initial scan -> verify tracks inserted with `firstIndexedAt = now` and `isAvailable = true`.
  - Second scan with updated tags -> verify metadata updated, `firstIndexedAt` and user ratings preserved.
  - Second scan with deleted file -> verify track marked `isAvailable = false`, playlist membership preserved.
  - Second scan with renamed file -> verify signature recovery re-links canonical `media_id`, preserving ratings & play count.
  - Cancelled scan -> verify staging purged and canonical DB untouched.
- **Removable Storage & Provider Tests**: Simulate missing/unmounted tree Uris and verify `isAvailable = false` updates across app restarts.

---

## Verification Commands

Execute the following commands from the repository root to verify Milestone 2 implementation:

```powershell
# Run unit tests for enumeration, metadata parsing, fallback regexes, and scan orchestration
.\gradlew.bat testDebugUnitTest

# Confirm debug build compilation and KSP/Room schema generation
.\gradlew.bat assembleDebug

# Run instrumentation tests for SAF provider behavior and in-memory/on-device re-indexing
.\gradlew.bat connectedDebugAndroidTest
```

---

## Exit Criteria

Milestone 2 is complete only when:

1. **Folder Onboarding**: A user can select a root folder via SAF `ACTION_OPEN_DOCUMENT_TREE`, persistent read permission is taken, and a default `MUSIC` collection/root is registered.
2. **Recursive Enumeration**: Nested subfolders are traversed off the main thread, non-audio files are filtered out, and admitted tracks are streamed in bounded staging batches.
3. **Metadata Extraction & Normalization**: Embedded ID3/audio tags are extracted off the main thread; display title precedence (Tag -> Path/Filename -> Cleaned Filename) and metadata provenance (`TAG`, `PATH`, `FILENAME`) are recorded accurately.
4. **Data Hygiene**: Missing metadata is stored as `null` in Room (never string fallbacks like `"Unknown Artist"`), and unknown duration is stored as `null`.
5. **Fallback Parsing**: Path patterns (`Artist/Album`) and track/disc prefixes (`01 - Title`, `1-01 Title`) infer missing metadata fields and generate diagnostic scan summaries.
6. **Idempotent Re-indexing**: Scans utilize isolated persistent staging, candidate identity matching (Document ID/URI -> Relative Path -> Conservative Signature), and atomic snapshot publication (`publishResolvedScan`).
7. **Preservation of User State**: Re-indexing preserves `firstIndexedAt`, `playCount`, `likeScore`, `lastPlayedAt`, and playlist memberships for existing or returning files.
8. **Fault Tolerance & Availability**: Missing files are marked `isAvailable = false` without row deletion. Unreadable/corrupt files do not fail the overall scan. Cancelled scans purge staging without corrupting canonical data.
9. **UI States**: The UI handles first-run explanation, folder naming, live scan progress, completion summaries, empty folders, permission loss, and error retry states.
10. **Verification**: All unit, build, and provider instrumentation tests pass.

---

## Reference Files

- [SPECIFICATION.md](../SPECIFICATION.md): Sections 2.1, 2.3, 2.4, 3.1, 4.1, 4.2, 4.4, and 5.
- [TASKS.md](../TASKS.md): Tasks T009–T015.
- [BRAINSTORM.md](../BRAINSTORM.md): Library shapes, folder selection, re-indexing, fallback parsing.
- [milestone_1_persistence.md](milestone_1_persistence.md): Room schema baseline, isolated scan staging (`ScanRunEntity`, `StagedMediaEntity`, `StagedFolderEntity`), atomic snapshot publication (`publishResolvedScan`).
- [milestone_0_foundation.md](milestone_0_foundation.md): Application layer package structure and dependency container.
- **Domain Models**:
  - [MediaFile.kt](../../app/src/main/java/com/app/resn8/domain/model/MediaFile.kt)
  - [Collection.kt](../../app/src/main/java/com/app/resn8/domain/model/Collection.kt)
  - [ScanProgress.kt](../../app/src/main/java/com/app/resn8/domain/model/ScanProgress.kt)
  - [ScanResult.kt](../../app/src/main/java/com/app/resn8/domain/model/ScanResult.kt)
  - [MetadataValueSource.kt](../../app/src/main/java/com/app/resn8/domain/model/MetadataValueSource.kt)
- **Database & DAOs**:
  - [Resn8Database.kt](../../app/src/main/java/com/app/resn8/data/database/Resn8Database.kt)
  - [ScanDao.kt](../../app/src/main/java/com/app/resn8/data/database/dao/ScanDao.kt)
  - [MediaFileDao.kt](../../app/src/main/java/com/app/resn8/data/database/dao/MediaFileDao.kt)
  - [FolderDao.kt](../../app/src/main/java/com/app/resn8/data/database/dao/FolderDao.kt)
  - [CollectionDao.kt](../../app/src/main/java/com/app/resn8/data/database/dao/CollectionDao.kt)
- **Repositories & Wiring**:
  - [RoomCollectionRepository.kt](../../app/src/main/java/com/app/resn8/data/repository/RoomCollectionRepository.kt)
  - [RoomMediaRepository.kt](../../app/src/main/java/com/app/resn8/data/repository/RoomMediaRepository.kt)
  - [AppContainer.kt](../../app/src/main/java/com/app/resn8/di/AppContainer.kt)
