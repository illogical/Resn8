# Investigation and Fix Plan: Playlist Tab Navigation & Multi-Track Playlist Oddities

## Problem Summary
When a user creates a new playlist and adds a single track from the Now Playing / Music Player view:
- Navigating to the Playlists section, viewing the playlist contents, and tapping **Play All** works as expected.
- However, after adding a **second track** to the playlist, tapping the **Playlists** tab in the bottom navigation bar appears unresponsive (nothing happens when tapped), even though adding to the playlist continues to accurately report "1 of 2" tracks in the selection sheet.

---

## Investigation Hypotheses

### Hypothesis 1: Navigation Compose Backstack & State Restoration Trap
- **Mechanism**: In `Resn8App.kt`, bottom navigation items use `saveState = true` and `restoreState = true`.
- **Behavior**: When viewing a single-item playlist, the navigation stack under the Playlists tab becomes `[PlaylistsRoute, PlaylistDetailRoute]`. When the user navigates away (or returns to Now Playing) and then taps the **Playlists** tab again, `restoreState = true` attempts to restore `PlaylistDetailRoute`. If the detail route has stale state, incomplete parameters, or crashes during recomposition, tapping the Playlists tab feels broken or unresponsive.
- **Remediation**: Tapping an already selected or active bottom navigation tab should pop back to the top-level route (`PlaylistsRoute`), resetting any nested detail state.

### Hypothesis 2: Compose `LazyColumn` Key Collision in `PlaylistDetailScreen`
- **Mechanism**: `PlaylistDetailScreen.kt` currently renders track rows using `key = { _, item -> item.mediaFile.id }`.
- **Behavior**: If the media repository returns duplicate media file IDs, fallback/unavailable media items with default IDs, or if identical tracks are added to a playlist, Compose throws an unhandled `IllegalArgumentException: Key was already used in this LazyColumn` during list layout. This silently breaks rendering and freezes user interaction on the screen.
- **Remediation**: Use a unique composite key combining rank position and item identity, e.g. `key = { _, item -> "${item.originalIndex}_${item.mediaFile.id}" }`.

### Hypothesis 3: Room Flow Emission or `GROUP BY` Aggregation Edge Case
- **Mechanism**: `PlaylistDao.getPlaylistsWithItemCountFlow` uses a `LEFT JOIN` and `GROUP BY p.id` with `COUNT(pi.mediaId)`.
- **Behavior**: When items are added or positions updated, Room emits a new snapshot. We must verify whether multiple items or rapid sequential updates cause flow cancellation or unhandled UI state transitions in `PlaylistsViewModel`.
- **Remediation**: Audit DAO queries and add unit tests verifying `RoomPlaylistRepository` and `PlaylistsViewModel` state emissions with 2+ items.

---

## Proposed Changes

### 1. Navigation & App Shell ([Resn8App.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/Resn8App.kt))
- Update bottom navigation tab click handlers to pop back to the root tab route when re-selecting the active top-level tab (or when navigating back to Playlists from a detail view).

### 2. Playlist Detail UI ([PlaylistDetailScreen.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/screens/PlaylistDetailScreen.kt))
- Update `LazyColumn` item key strategy in `PlaylistDetailScreen` to prevent duplicate key crashes when multiple or unavailable items exist in a playlist.

### 3. Repository & ViewModel Tests ([RoomPlaylistRepositoryTest.kt](file:///c:/LocalDev/Projects/Resn8/app/src/test/java/com/app/resn8/data/RoomPlaylistRepositoryTest.kt))
- Add automated unit tests covering multi-item playlist operations: adding 2+ tracks, reordering, deleting, and verifying `getPlaylistsWithItemCountFlow` emissions.

---

## Verification Plan

### Automated Tests
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```
- Verify repository unit tests pass for single and multi-track playlists.
- Verify navigation and viewmodel state restoration tests pass.

### Manual Verification Workflow
1. Create a new playlist.
2. Add track #1 from Now Playing -> Open Playlists tab -> Open Playlist Detail -> Verify track #1 displays and "Play All" works.
3. Return to Now Playing -> Add track #2 to the playlist.
4. Tap the **Playlists** tab in the bottom bar -> Verify top-level Playlists screen opens with item count = 2.
5. Tap into Playlist Detail -> Verify both tracks render, order controls work, and "Play All" enqueues both tracks.
