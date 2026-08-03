# Investigation and Fix Plan: Playlist Tab Navigation & Playlist Context Awareness

## Problem Summary

1. **Unresponsive Bottom Navigation Tab from Now Playing**:
   - When a user selects a track from a playlist (or taps Play All), playback starts and the app opens or displays Now Playing (`NowPlayingRoute`).
   - From Now Playing, tapping the **Playlists** tab in the bottom navigation bar appears unresponsive or fails to return to the Playlists root screen because Navigation Compose's state restoration (`restoreState = true`) attempts to restore nested `PlaylistDetailRoute` state while `NowPlayingRoute` sits on top of the root backstack.

2. **Missing Playlist & Queue Context Awareness**:
   - When playback is initiated from a playlist, the player and active queue views do not clearly indicate that music is playing in the context of that specific playlist.
   - Users need a transparent view of the active queue's item order (preceding tracks, current track, and upcoming tracks) and a direct way to navigate back to the originating playlist detail screen.

---

## Investigation Hypotheses & Remediation

### Hypothesis 1: Bottom Navigation Bar State Restoration Trap
- **Mechanism**: In `Resn8App.kt`, tapping a top-level tab uses `restoreState = true`. When coming from `NowPlayingRoute` (which was opened on top of `PlaylistDetailRoute`), `restoreState = true` restores `PlaylistDetailRoute` instead of taking the user to the top-level `PlaylistsRoute`. If the user is looking for the Playlists root list, tapping the tab appears unresponsive because it restores the exact detail view they just left.
- **Remediation**:
  - Tapping any top-level tab in `NavigationBar` must pop all nested destinations (such as `PlaylistDetailRoute`) and navigate directly to the top-level route (`PlaylistsRoute`).
  - When a track is tapped in `PlaylistDetailScreen`, navigate to `NowPlayingRoute` explicitly while passing the playlist context (`playlistId` and `playlistName`) to `PlaybackUiState`.

### Hypothesis 2: Queue & Playlist Context Awareness
- **Mechanism**: `PlaybackUiState` currently exposes track title, artist, album, and queue items, but lacks explicit queue source context (e.g., `queueTitle = "Playlist: Summer Favorites"`, `sourcePlaylistId = "p1"`).
- **Remediation**:
  - Update `SavedQueue` and `PlaybackUiState` to include `queueTitle` (e.g., `Playlist: <Name>`) and `sourcePlaylistId`.
  - Update `QueueScreen.kt` to render the active queue title header (`Playing from Playlist: <Name>`) and list all items in queue order (preceding tracks, current track with playing indicator, and upcoming tracks).
  - Add a direct navigation link on the queue header in `QueueScreen` allowing users to jump back into `PlaylistDetailRoute(playlistId)`.

### Hypothesis 3: Compose `LazyColumn` Composite Keys
- **Mechanism**: Ensure `PlaylistDetailScreen.kt` uses composite keys `${item.originalIndex}_${index}_${item.mediaFile.id}` so item re-ordering and duplicate tracks do not crash `LazyColumn`.

---

## Proposed Changes

### 1. Navigation & App Shell ([Resn8App.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/Resn8App.kt))
- Update bottom navigation tab click handlers to pop back to the top-level root route (`PlaylistsRoute`) when selecting any tab, clearing nested detail routes.

### 2. Playlist Detail & Nav Host ([Resn8NavHost.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/navigation/Resn8NavHost.kt) & [PlaylistDetailScreen.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/screens/PlaylistDetailScreen.kt))
- When starting playback from a track or "Play All" in `PlaylistDetailScreen`, automatically navigate to `NowPlayingRoute`.
- Pass `sourcePlaylistId` and `playlistName` when initiating `QueueStartRequest.Playlist`.

### 3. Playback UI State & Queue Context ([PlaybackUiState.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/playback/PlaybackUiState.kt) & [QueueScreen.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/screens/QueueScreen.kt))
- Expose `queueTitle` and `sourcePlaylistId` in `PlaybackUiState`.
- Display `"Playing from Playlist: <Name>"` in `QueueScreen.kt` and `NowPlayingScreen.kt` with a clickable action to return to `PlaylistDetailRoute`.
- Render the full ordered list of queue items (preceding, current, and upcoming) in `QueueScreen.kt`.

---

## Verification Plan

### Automated Tests
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

### Manual Verification Workflow
1. Create a playlist and add 2+ tracks.
2. Open Playlist Detail -> tap track #1 to begin playing -> verify app navigates to Now Playing.
3. Tap the **Playlists** tab in the bottom bar -> verify the top-level Playlists list screen opens immediately.
4. Tap the **Queue** icon in Now Playing -> verify the screen shows `"Playing from Playlist: <Name>"` and lists preceding, current, and upcoming tracks in exact order.
5. Tap the playlist title in Queue / Now Playing -> verify app navigates directly to Playlist Detail.
