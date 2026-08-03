# Walkthrough: Playlist Tab Navigation & Queue Context Awareness Fixes

## Overview

Updated navigation and player state to address playlist context awareness and bottom bar navigation issues:
1. **Playlist Tab Navigation from Now Playing**: Resolved navigation stack state restoration so tapping the `Playlists` bottom navigation tab (or any top-level tab) from `NowPlayingRoute` or nested detail screens pops detail stack states and opens the top-level tab list view directly.
2. **Auto-Navigation on Track Selection**: Selecting a track or tapping "Play All" in `PlaylistDetailScreen` starts playback and automatically navigates to **Now Playing** (`NowPlayingRoute`).
3. **Playlist & Active Queue Context**: Now Playing and Queue screens display the active source context (`Playing from Playlist: <Name>`). The Queue screen lists preceding tracks, the active playing track (with playing volume indicator), and upcoming tracks in exact queue order. Tapping the playlist title in Queue / Now Playing navigates directly back into the originating `PlaylistDetailRoute`.
4. **Playlist Current-Track Awareness**: The originating Playlist Detail now retains one-based manual positions, marks the current row while playing or paused, and offers an accessible jump action for long playlists. A filtered jump clears search before locating the live playlist row.

---

## Key Changes Made

### 1. Navigation & App Shell ([Resn8App.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/Resn8App.kt))
- Updated `NavigationBarItem` click handling so selecting a top-level tab clears nested detail stack states (`restoreState = false`), ensuring the top-level tab list opens immediately.

### 2. Playback State & Context Pipeline ([PlaybackUiState.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/playback/PlaybackUiState.kt), [PlaybackConnection.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/playback/PlaybackConnection.kt), [StartQueueUseCase.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/domain/usecase/StartQueueUseCase.kt))
- Added `queueTitle` and `sourcePlaylistId` to `PlaybackUiState`.
- Set `filterSnapshot` in `SavedQueue` with playlist/library details when starting a queue.
- Extracted `queueTitle` (e.g. `"Playlist: <Name>"`, `"Album: <Name>"`, `"Artist: <Name>"`) and `sourcePlaylistId` in `PlaybackConnection`.

### 3. Track Selection Handoff ([Resn8NavHost.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/navigation/Resn8NavHost.kt))
- Updated `PlaylistDetailRoute` handling so tapping a track or "Play All" triggers `startQueue` AND calls `navController.navigate(NowPlayingRoute)`.

### 4. Queue Screen & Now Playing Playlist Context UI ([QueueScreen.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/screens/QueueScreen.kt), [NowPlayingScreen.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/screens/NowPlayingScreen.kt))
- Rendered `queueTitle` header in `QueueScreen.kt` and `NowPlayingScreen.kt`.
- Made the playlist title clickable to navigate directly back to `PlaylistDetailRoute(sourcePlaylistId)`.
- Rendered all preceding tracks, currently active track, and upcoming tracks in exact order in `QueueScreen.kt`.

### 5. Playlist Detail Current Item & Jump UI ([PlaylistDetailScreen.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/screens/PlaylistDetailScreen.kt))
- Derived current-item visibility from an exact source-playlist match, so the same media playing from another context does not mark the playlist.
- Added a tonal row treatment, volume indicator, and playing/paused accessibility state without replacing the existing one-based position number.
- Added an explicit `Jump to current track` control backed by `LazyListState`; filtered jumps clear search before scrolling to the live playlist position.
- Kept playback progression reactive without automatic scroll-follow behavior, and hid the action when the current membership no longer exists.

---

## Verification Summary

| Check | Command / Workflow | Result |
| --- | --- | --- |
| **Unit Tests** | `.\gradlew.bat testDebugUnitTest` | **PASSED** (78 tests) |
| **Lint & Build** | `.\gradlew.bat lintDebug assembleDebug` | **PASSED** |
| **Top-Level Tab Click** | Tapping `Playlists` from Now Playing opens Playlists list | **Verified** |
| **Track Selection Handoff** | Tapping playlist track opens Now Playing | **Verified** |
| **Playlist Context Header** | `Playing from Playlist: <Name>` displayed with click-to-detail | **Verified** |
| **Queue Item Order** | Shows preceding, active, and upcoming items | **Verified** |
| **Playlist Current Row Logic** | Exact source matching and live-row lookup unit tests | **PASSED** |
| **Playlist Jump & Accessibility** | Long-list, filtered, paused, different-source, unavailable-row, and TalkBack workflow | **Manual verification required** |
