# Fix Main-Thread Room Access and Indexing Failure Diagnostics

## Confirmed Problem

Resn8's domain repository methods are declared as Kotlin `suspend` functions, but their Room DAO calls are synchronous. A `suspend` repository function does not change coroutine dispatchers by itself, so callers launched by `viewModelScope` continue on the main thread unless the DAO is asynchronous or the caller explicitly changes context.

Two production paths confirm the defect:

1. `OnboardingViewModel.startIndexing()` runs in `viewModelScope` and calls `RoomCollectionRepository.createCollection()` and `addRootSource()`. Those methods currently call synchronous `CollectionDao` inserts and queries. Room rejects the first database access on the main thread, and onboarding's broad exception handler suppresses the throwable and shows the generic **Indexing Failed** state.
2. Physical-device Logcat captured `IllegalStateException: Cannot access database on the main thread` from `UiSessionDao_Impl.upsertUiSessionState()`, through `RoomUiSessionRepository.saveUiSessionState()` and `LibraryViewModel.saveSessionState()`. This independently demonstrates that the problem affects the persistence layer beyond onboarding.

The scan coordinator already performs enumeration and metadata work on `Dispatchers.IO`, but that does not protect repository calls made from ViewModels, playback callbacks, or other main-dispatched coroutines.

## Implementation Changes

### Pre-condition: Room version is already current

`gradle/libs.versions.toml` already pins `room = "2.8.4"` with KSP `2.2.10-2.0.2`. **No dependency version changes are required.** The fix is purely a DAO-signature change.

### Room DAO contracts

Convert every immediate, one-shot Room query or mutation to a Kotlin `suspend` function in all eight DAOs. Keep methods returning `Flow` or `PagingSource` non-suspending; Room supplies their asynchronous execution. Return inserted row IDs or affected-row counts from suspend mutations instead of `Unit`, which also makes persistence outcomes explicit. Do not enable `allowMainThreadQueries()` in the production database and do not add destructive migration fallback.

Specific `suspend` conversions per DAO:

**`CollectionDao.kt`**: `getCollectionById`, `insertCollection`, `getRootSourceById`, `getRootSourceByTreeUri`, `insertRootSource`, `updateRootSourceAvailability`, `updateRootScanState`. Keep `getCollectionsFlow`, `getRootSourcesFlow` as `Flow`.

**`FolderDao.kt`**: `getFolderNodeById`, `getFolderNodeByPath`, `insertFolderNode`, `updateFolderNode`, `resolveSelectionMediaIds`, `countAvailableMediaIds`. Keep `getRootFolderNode`, `getDirectChildFolders`, `getFolderBreadcrumbs`, `getFolderNodesFlow` as `Flow`.

**`MediaFileDao.kt`**: All immediate queries and mutations. Keep all `Flow`- and `PagingSource`-returning methods non-suspending.

**`PlaybackHistoryDao.kt`**: `getHistoryByOccurrenceId`, `insertHistory`, `updateHistory`, `getHistoryForMedia`.

**`PlaylistDao.kt`**: All immediate queries and mutations. Keep `Flow`-returning methods non-suspending.

**`SavedQueueDao.kt`**: `getSavedQueueById`, `getSavedQueueItems`, `upsertSavedQueue`, `insertSavedQueueItems`, `deleteSavedQueueItems`, `updatePosition`, `updateCheckpoint`. Keep `getActiveQueueFlow`, `getSavedQueueByIdFlow`, `getSavedQueueItemsFlow` as `Flow`.

**`ScanDao.kt`**: `getScanRunById`, `getActiveScanRun`, `insertScanRun`, `updateScanRunStatus`, `completeScanRun`, `insertStagedFolders`, `insertStagedMedia`, `getStagedFolders`, `getStagedMedia`, `countStagedMedia`, `getStagedMediaBatch`, `getStagedFolderBatch`, `setResolvedMedia`, `setResolvedFolder`, `deleteStagedFolders`, `deleteStagedMedia`.

**`UiSessionDao.kt`**: `getUiSessionState`, `upsertUiSessionState`. Keep `getUiSessionStateFlow` as `Flow`.

### Repository and test call sites

Update compilation-affected calls in the actual Room implementations:

- `RoomCollectionRepository.kt`
- `RoomMediaRepository.kt` (including playback-history persistence)
- `RoomPlaylistRepository.kt`
- `RoomQueueRepository.kt` (which uses `SavedQueueDao`)
- `RoomUiSessionRepository.kt`

The domain repository contracts and fake repository implementations are already suspending where immediate persistence is exposed, so they require only compilation-driven adjustments. There is no separate `RoomPlaybackHistoryRepository` or `RoomSavedQueueRepository`.

**Do not wrap repository methods in `withContext(Dispatchers.IO)`**. Room 2.8.x generated suspend DAO implementations dispatch blocking Room I/O internally. Adding redundant `withContext` wrappers would be incorrect and unnecessary.

Suspend Room DAO calls remain valid inside `RoomDatabase.withTransaction` blocks. Do not remove any existing transaction wrappers.

### `Resn8Database.buildInMemoryDatabase` — remove `allowMainThreadQueries()`

The in-memory builder used by Robolectric unit tests calls `.allowMainThreadQueries()`. Once DAOs are `suspend`, tests must call them from a coroutine body. Remove `allowMainThreadQueries()` so the main-thread guard fires in tests exactly as it does in production. All existing persistence tests (`ScanPersistenceTest`, `DatabaseSchemaTest`, `QueuePersistenceTest`, `PlaylistPersistenceTest`, `MediaPersistenceTest`) already wrap DAO calls in `runBlocking` test bodies and will compile without logic changes once DAO signatures are updated.

### `ScanOrchestrator` — already safe, no changes required

`ScanOrchestrator.executeScan()` wraps its entire body in `withContext(Dispatchers.IO)`, so all DAO calls it makes through `mediaRepository` and `collectionRepository` are already off the main thread. This file requires only compilation-driven updates if any repository method signatures change.

Update direct DAO calls in tests so they execute from coroutine test bodies as required by the new signatures. Production behavior must remain protected by Room's normal main-thread guard.

## Failure Diagnostics

Add structured, privacy-safe diagnostics at each failure boundary:

- `OnboardingViewModel`: replace the bare `catch (_: Exception)` in `startIndexing` with a named catch that logs the throwable class name and a stable phase (`CREATE_COLLECTION`, `ADD_ROOT_SOURCE`, or `ENQUEUE_WORK`) before showing the retryable setup error.
- `IndexingWorker`: add a `phase` variable that advances across preparation and execution stages. In the failure catch block, log the throwable class name, work ID, source ID, and phase; return the sanitized phase and category as `workDataOf` output using a new `KEY_ERROR_PHASE` constant alongside the existing `KEY_ERROR_CATEGORY`.
- `ScanOrchestrator`: add a `phase` variable (`INIT`, `TRAVERSAL`, `PUBLICATION`, `CLEANUP`) that advances as `executeScan` progresses; include it in the existing `scan_failed` log line and artwork-warning log. Preserve the existing behaviour of rethrowing the original scan error; if cleanup itself also fails, log that secondary failure separately.
- `OnboardingViewModel` work observation: in the `FAILED` branch of `observeWork`, read `KEY_ERROR_PHASE` from `info.outputData` to surface the phase in the user-facing error string. Keep the user-facing text concise and actionable; detailed stack traces remain in Logcat.

Never log collection names, persisted content URIs, relative paths, filenames, media metadata, or raw exception messages that could contain provider data. IDs, stable phase names, exception class categories, counts, and elapsed durations are permitted.

Failure cleanup must preserve the original exception. If marking a scan failed or updating root state also fails, log that cleanup failure separately and rethrow the original scan error.

## Verification Plan

### Automated verification

1. Add an Android instrumentation regression test using a Room database created without `allowMainThreadQueries()`.
2. From a main-dispatched coroutine, verify that `RoomCollectionRepository.createCollection()`, `addRootSource()`, and `RoomUiSessionRepository.saveUiSessionState()` complete and persist data without `IllegalStateException`.
3. Update repository, transaction, and direct-DAO tests for the suspend signatures and run:

   ```powershell
   $env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
   .\gradlew.bat testDebugUnitTest
   .\gradlew.bat lintDebug assembleDebug
   ```

4. Run the targeted connected-device instrumentation test on an API 34+ device.

### Physical-device verification

1. Install the verified debug APK and clear Resn8 app data for a clean onboarding state.
2. Select the representative music directory, accept persistent read access, and choose a collection name.
3. Confirm scan progress begins, completes, and reports expected counts without an **Indexing Failed** state or process crash.
4. Navigate among library surfaces and change a persisted filter/sort value; confirm UI-session state saves without a main-thread Room exception and survives relaunch.
5. Exercise or inject an onboarding setup failure and a worker/scan failure. Confirm Logcat includes the throwable plus sanitized phase/category, the UI remains retryable, and no selected URI, collection name, file path, filename, or metadata appears in the diagnostic messages.

## Review Gate

Before source implementation begins, confirm this plan:

- [ ] Identifies both the onboarding collection/root database path and the independently observed UI-session crash.
- [ ] Requires **no Room version changes** — project is already on 2.8.4.
- [ ] Converts all immediate Room DAO operations in all 8 DAOs while preserving `Flow` and `PagingSource` contracts.
- [ ] Removes `allowMainThreadQueries()` from `buildInMemoryDatabase` and updates affected test call sites.
- [ ] Does **not** add `withContext(Dispatchers.IO)` wrappers in repositories — Room's generated suspend implementations handle dispatch.
- [ ] Names only repository implementations present in the project (no `RoomPlaybackHistoryRepository` or `RoomSavedQueueRepository`).
- [ ] Notes that `ScanOrchestrator` is already safe and requires no dispatcher changes.
- [ ] Requires throwable-bearing, phase-specific diagnostics without exposing user library data.
- [ ] Includes a Robolectric regression test and physical-device acceptance cases that fail against the current synchronous DAO implementation.
