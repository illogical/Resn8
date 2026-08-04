# Fix Plan: First-Collection Onboarding Profile and Settings Handoff

## Goal

Correct first-run setup so the user chooses whether the first collection is Music or Audio Files before selecting its folder. The chosen profile must control collection persistence, indexing semantics, completion copy, the initial destination, and the live app-shell destinations. Preserve the existing WorkManager scan progress, cancellation, completion summary, and error behavior.

## Confirmed causes

- Onboarding called `createCollection` without a profile, so its default `MUSIC` value classified every first collection as music.
- The onboarding action and explanatory copy were hard-coded for a music folder.
- Scan completion resolved the repository's first collection and always persisted/opened Library rather than using the collection and source created by the active onboarding attempt.
- The app shell decided whether setup was complete from the one-time startup snapshot. Creating the first collection did not update that snapshot, so Onboarding remained in bottom navigation until process restart.

## Implementation

### Profile-aware first-run input

1. Show visible Music and Audio Files choices, with Music selected initially.
2. Keep the choice in the onboarding ViewModel so picker cancellation, naming cancellation, permission retry, and configuration changes do not reset it.
3. Use `Select Music Folder` for Music and `Select Audio Files Folder` for Audio Files. Keep surrounding description, permission, progress, and completion copy free of synthetic music terms for Audio Files.

### Atomic creation and indexing identity

1. Create the first collection and source through `createCollectionWithSource`, passing the selected `CollectionProfile` explicitly.
2. Persist the returned collection and source IDs in `UiSessionState` before enqueueing `IndexingWorker`.
3. Continue observing the existing unique WorkManager request and preserve progress, cancellation, empty-result retry, completion details, and error presentation.
4. Resolve completion from the stored source ID and its owning collection. Never substitute the repository's first collection.
5. Rely on the repository transaction so source-creation failure cannot leave an orphan collection.

### Profile-aware completion

- Music persists route `library`, surface Artists, and artist sorting, then offers `Open Library`.
- Audio Files persists route `folders`, surface Folders, and title sorting, then offers `Open Folders`.
- The handoff clears stale folder, artist, album, playlist, queue, search, filter, and incompatible sort/surface state before navigation.

### Live shell readiness

1. Derive collection-management availability and the collection-selector enabled state from the live collections flow.
2. With no collections, expose only Onboarding.
3. As soon as the first Music collection exists, expose Settings, Library, Folders, and Playlists.
4. As soon as the first Audio Files collection exists, expose Settings, Folders, and Playlists.
5. Leave `AppStartupCoordinator` responsible for cold-start restoration. Do not turn it into a continuous collection observer.

## Boundaries

- Do not change an existing collection's profile or attempt to repair an incorrectly created Music collection in place.
- Do not alter the underlying indexing worker or completion-detail model beyond profile-aware inputs and copy.
- Do not run connected Gradle tests against a personal physical device. Use a disposable emulator for automated instrumentation.

## Automated verification

- Unit tests cover profile copy, exact initial route/surface/sort/IDs, stale-state clearing, atomic creation rollback, and FLAT cold-start fallback from stale Library state to Folders.
- Compose tests cover Music as the default, switching to Audio Files, exact folder-action labels, the naming flow, and profile-appropriate completion action.
- App-shell tests cover the live destination sets for zero collections, Music, and Audio Files.
- Run `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `assembleDebugAndroidTest` with Android Studio's bundled JDK.

## Device reset and acceptance

Immediately before resetting, inventory attached devices, identify the exact physical-device serial, and confirm package `com.app.resn8`. Announce that clearing storage removes collections, Room data, preferences, WorkManager state, playlists, queues, history, statistics, and persisted SAF grants. Source audio files are not deleted.

After the target is verified, use:

```text
adb -s <verified-serial> shell pm clear com.app.resn8
```

Manual alternative: Android Settings -> Apps -> Resn8 -> Storage -> Clear storage.

Then verify:

1. A clean launch exposes only Onboarding and preselects Music.
2. Music uses `Select Music Folder`, creates a Music collection, retains the current scan flow, offers `Open Library`, and replaces Onboarding with Settings immediately.
3. After a second approved reset, Audio Files uses `Select Audio Files Folder`, creates a `FLAT` collection, indexes without path-derived music metadata, offers/opens Folders, and immediately exposes Settings, Folders, and Playlists.
4. Restart preserves the selected profile and restores a valid profile-specific destination.

## Exit criteria

The first selected profile is the profile stored and indexed; its navigation and copy are valid before and after restart; the app shell graduates from Onboarding without requiring a relaunch; and no first-run path can create an orphan collection/source pair.
