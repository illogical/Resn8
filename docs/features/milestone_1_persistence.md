# Milestone 1 Implementation Plan: Persist the Library Model

This plan details the implementation of Room local relational persistence for Resn8, transforming the domain contracts established in Milestone 0 into durable SQLite tables, DAOs, atomic transactions, and persistence tests matching `docs/SPECIFICATION.md` (Section 2.2).

## Proposed Changes

### T006 — Implement the Room Schema

Create Room entities, converters, DAOs, and database configuration under package `com.app.resn8.data.database`:

#### 1. Entities (`com.app.resn8.data.database.entity`)
- **`CollectionEntity.kt`** (`collections`): `id` (PK), `name`, `profile`, `createdAt`, `updatedAt`.
- **`RootSourceEntity.kt`** (`root_sources`): `id` (PK), `collectionId` (FK -> `collections.id` ON DELETE CASCADE), `treeUri`, `displayName`, `isAvailable`, `lastScanStatus`, `lastScannedAt`.
- **`FolderNodeEntity.kt`** (`folder_nodes`): `id` (PK), `sourceId` (FK -> `root_sources.id` ON DELETE CASCADE), `parentId` (FK self-referential -> `folder_nodes.id`), `relativePath`, `displayName`.
- **`MediaFileEntity.kt`** (`media_files`):
  - Primary Key: `id`
  - Foreign Keys: `sourceId` -> `root_sources.id` (CASCADE), `folderId` -> `folder_nodes.id` (RESTRICT)
  - Fields: `documentUri`, `relativePath`, `filename`, `displayTitle`, `mimeType`, `size`, `durationMs`, `modifiedTimeMs`, `isAvailable`, `title`, `artist`, `albumArtist`, `album`, `discNumber`, `trackNumber`, `year`, `genre`, `artworkUri`, `playCount`, `lastPlayedAt`, `likeScore`.
  - Indexes: `sourceId`, `folderId`, `artist`, `album`, `playCount`, `lastPlayedAt`, `likeScore`, `isAvailable`.
- **`PlaylistEntity.kt`** (`playlists`): `id` (PK), `collectionId` (FK -> `collections.id` CASCADE), `name`, `createdAt`, `updatedAt`. Unique index on `(collectionId, name)`.
- **`PlaylistItemEntity.kt`** (`playlist_items`):
  - Composite Primary Key: `(playlistId, mediaId)`
  - Foreign Keys: `playlistId` -> `playlists.id` (CASCADE), `mediaId` -> `media_files.id` (CASCADE)
  - Fields: `position` (Double for flexible reordering), `addedAt`.
  - Index: `(playlistId, position)`.
- **`PlaybackHistoryEntity.kt`** (`playback_history`): `id` (PK), `mediaId` (FK -> `media_files.id` CASCADE), `sessionOccurrenceId`, `startedAt`, `endedAt`, `accumulatedListenedDurationMs`, `isMeaningfulPlay`.
- **`SavedQueueEntity.kt`** (`saved_queues`): `id` (PK), `collectionId` (FK -> `collections.id` CASCADE), `kind`, `mode`, `filterSnapshotJson`, `seed`, `currentIndex`, `positionMs`, `isPlaying`, `updatedAt`.
- **`SavedQueueItemEntity.kt`** (`saved_queue_items`):
  - Composite Primary Key: `(queueId, itemIndex)`
  - Foreign Keys: `queueId` -> `saved_queues.id` (CASCADE), `mediaId` -> `media_files.id` (CASCADE).
- **`UiSessionStateEntity.kt`** (`ui_session_state`): `id` (PK, single row `1`), `currentRoute`, `selectedCollectionId`, `selectedFolderId`, `selectedArtist`, `selectedAlbum`, `selectedPlaylistId`, `activeSearchQuery`, `activeSort`.

#### 2. Type Converters (`com.app.resn8.data.database.Converters.kt`)
- `CollectionProfile`, `SavedQueueKind`, `SmartQueueMode`, `SortOrder` enum to String mappings.
- `QueueFilterSnapshot` JSON serialization/deserialization.

#### 3. Data Access Objects (`com.app.resn8.data.database.dao`)
- **`CollectionDao.kt`**: Flow & CRUD queries for collections and root sources.
- **`FolderDao.kt`**: Recursive folder hierarchy queries.
- **`MediaFileDao.kt`**: Reactive queries with sorting (`SortOrder`), filtering by artist/album/search/folder, scan upserts (`@Upsert`), rating score updates, play count incrementing.
- **`PlaylistDao.kt`**: CRUD, unique membership insertion (`OnConflictStrategy.IGNORE`), item removal, manual position reordering.
- **`SavedQueueDao.kt`**: Active queue snapshot save and position checkpointing.
- **`PlaybackHistoryDao.kt`**: Meaningful play history logging.
- **`UiSessionDao.kt`**: Relaunch browsing context checkpoint saving and loading.

#### 4. Database Class (`com.app.resn8.data.database.Resn8Database.kt`)
- `RoomDatabase` definition listing all 10 entities and version `1`.
- Configured schema export to `app/schemas` for automated migration testing.

---

### T007 — Implement Repository Transactions

Replace Milestone 0 fakes with Room-backed repository implementations under `com.app.resn8.data.repository`:

- **`RoomMediaRepository.kt`**: Implements [MediaRepository](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/MediaRepository.kt).
  - Translates domain models to/from Room entities.
  - `@Transaction` for atomic scan upserts (inserting new files, updating modified metadata, marking missing files `isAvailable = false`).
  - Atomic `updateLikeScore(mediaId, delta)` adding signed integer delta (`+1` for Like, `-1` for Dislike).
  - Atomic `recordPlay(...)` writing history and incrementing `playCount`.
- **`RoomCollectionRepository.kt`**: Implements [CollectionRepository](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/CollectionRepository.kt).
- **`RoomPlaylistRepository.kt`**: Implements [PlaylistRepository](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/PlaylistRepository.kt).
  - Handles drag-and-drop position reordering and compaction.
- **`RoomQueueRepository.kt`**: Implements [QueueRepository](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/QueueRepository.kt).
  - Saves explicit ordered media ID snapshot and playback checkpoint state.
- **`AppContainer.kt` Update**: Add Room database builder in [DefaultAppContainer](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/di/AppContainer.kt) to supply production Room repositories when initialized with application Context.

---

### T008 — Test Persistence Invariants

Add unit and instrumentation tests under `app/src/test/java/com/app/resn8/database/` using Robolectric / Room in-memory database (`Room.inMemoryDatabaseBuilder`):

- **`PlaylistPersistenceTest.kt`**:
  - Test unique `(playlistId, mediaId)` membership (duplicate adds are ignored).
  - Test stable manual position ordering across reorder operations.
- **`MediaRatingPersistenceTest.kt`**:
  - Test signed like scores crossing zero (`0` -> `+1` -> `+2` -> `+1` -> `0` -> `-1` -> `-2`).
  - Test non-negative `playCount` constraint.
- **`UnavailableFileRetentionTest.kt`**:
  - Test missing files marked `isAvailable = false` retain rating, play count, history, and playlist membership.
- **`CascadeBehaviorTest.kt`**:
  - Test deleting a playlist cascades to `playlist_items` but leaves `media_files` intact.
  - Test deleting a root source cascades to descendant folders/files.
- **`SavedQueuePersistenceTest.kt`**:
  - Test explicit queue saving and index/position checkpointing.

---

## Reference Files

- **Specification**: [SPECIFICATION.md](file:///c:/LocalDev/Projects/Resn8/docs/SPECIFICATION.md) — Section 2.2 (Shared Media Schema), 2.5 (Rating Semantics), 2.6 (Meaningful Play Semantics).
- **Task Backlog**: [TASKS.md](file:///c:/LocalDev/Projects/Resn8/docs/TASKS.md) — Milestone 1 (T006, T007, T008).
- **Domain Models**:
  - [MediaFile.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/model/MediaFile.kt)
  - [Collection.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/model/Collection.kt)
  - [Playlist.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/model/Playlist.kt)
  - [SavedQueue.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/model/SavedQueue.kt)
  - [PlaybackHistory.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/model/PlaybackHistory.kt)
- **Domain Repositories**:
  - [MediaRepository.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/MediaRepository.kt)
  - [CollectionRepository.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/CollectionRepository.kt)
  - [PlaylistRepository.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/PlaylistRepository.kt)
  - [QueueRepository.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/repository/QueueRepository.kt)
- **Dependency Container**: [AppContainer.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/di/AppContainer.kt)
- **Test Utilities**: [TestFixtures.kt](file:///c:/LocalDev/Projects/Resn8/app/src/test/java/com/app/resn8/fixtures/TestFixtures.kt)

---

## Verification Plan

### Automated Tests
- `./gradlew.bat testDebugUnitTest`: Run Room in-memory database tests verifying all persistence invariants.
- `./gradlew.bat assembleDebug`: Confirm Room entity code generation via KSP and successful compilation.

### Manual Verification
- Deploy to emulator and inspect SQLite database tables using Android Studio Database Inspector.
- Verify ratings and play history persist across app process kill and restart.
