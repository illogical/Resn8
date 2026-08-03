# Walkthrough — Milestone 6: Manual Playlists

We have completed **Milestone 6: Manual Playlists (T031–T036)** for Resn8. Users can now create, rename, delete, and maintain durable, manually ordered playlists with single-transaction rank reordering, tri-state membership selection, unavailable membership retention, and context entry points across the app.

---

## Key Changes Made

### Persistence & Data Layer
- **Aggregate DAO Queries**: Added `getPlaylistMembershipSummariesFlow` and `getPlaylistsWithItemCountFlow` in [PlaylistDao.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/data/database/dao/PlaylistDao.kt) to calculate total and matching target counts in a single reactive query (eliminating N-flow performance overhead).
- **Normalized Name Safety & Error Mapping**: Normalized playlist names using `Locale.ROOT` in [RoomPlaylistRepository.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/data/repository/RoomPlaylistRepository.kt) and [FakePlaylistRepository.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/data/repository/FakePlaylistRepository.kt). Creation and renaming catch `SQLiteConstraintException` to surface user-friendly domain errors on blank or duplicate normalized names.
- **Transactional Reorder API with Two-Phase Compaction**: Replaced raw UI position calculations with repository-owned intent APIs (`movePlaylistItem`, `movePlaylistItemToPosition`). Compaction uses a collision-safe two-phase update (`-index` -> `index * 1024L`) inside one transaction, preventing unique `(playlistId, position)` index collisions without deleting rows or losing `addedAt` timestamps.
- **`AddItemsResult` Accounting**: `addItemsToPlaylist` returns `AddItemsResult(addedCount, unchangedCount, failedCount)` and updates `Playlist.updatedAt` only when membership actually changes.

### UI & Context Integration
- **`PlaylistsScreen`**: Shows list of playlists with reactive item counts ("X tracks"). Delete confirmation explicitly reassures the user that source audio files on disk remain untouched.
- **`PlaylistDetailScreen`**:
  - Displays original manual position indexes (`1`, `2`, `3`...) even when text filtering is active.
  - Hides/disables reorder controls during text filtering to prevent ambiguous destination drops.
  - Visually marks unavailable items, disables direct playback clicks for unavailable rows, and allows removal.
  - `Play All` starts the full available-only playlist snapshot in manual order.
- **`PlaylistSelectorSheet` & `NewPlaylistDialog`**:
  - Displays sorted playlists: `ALL` -> `SOME` -> `NONE` (secondary sort by name, then ID).
  - Sheet stays open across toggles; disables only the row currently saving.
  - Inline playlist creation dialog preserves validation/duplicate errors on failure and checks the newly created playlist row on success.
  - Explains duplicate queue occurrence collapse when saving an active queue as a manual playlist.
- **Complete Entry Points**:
  - **Now Playing**: Added "Add to Playlist" action for current track.
  - **MiniPlayer**: Added visible "Add to Playlist" action button in [MiniPlayer.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/components/MiniPlayer.kt).
  - **Album & Artist Detail**: Bulk "Add Album to Playlist" and "Add Artist Songs to Playlist" actions snapshot complete disc/track/title order independent of loaded Paging windows.
  - **Queue**: "Save Queue as Playlist" in [QueueScreen.kt](file:///c:/LocalDev/Projects/Resn8/app/src/main/java/com/app/resn8/ui/screens/QueueScreen.kt) displays duplicate collapse explanation ("X unique tracks from Y queue items").

---

## Verification Results

### 1. Automated Unit Tests
Executed via PowerShell using Android Studio's bundled JDK:
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
```
**Result**: `BUILD SUCCESSFUL` (All tests in `PlaylistMilestone6Test`, `PlaylistPersistenceTest`, `MigrationTest`, `FileBackedDatabaseTest`, `PlaybackMilestone4Test` passed).

### 2. Static Analysis & Build Verification
```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat lintDebug assembleDebug
```
**Result**: `BUILD SUCCESSFUL`. APK compiled without warnings or static lint errors.

---

## Manual Verification Steps for On-Device Testing

1. **Playlist Management & Delete Safety**:
   - Open **Playlists** tab -> tap **+**. Create a playlist "Road Trip".
   - Try creating "road trip" or "  ROAD TRIP  " -> confirm inline validation error prevents duplicate normalized name.
   - Tap **Delete** on a playlist -> confirm dialog copy states: *"Audio files on disk will NOT be deleted."* Confirm files on storage remain intact.
2. **Context Entry Points & Selector**:
   - Play a song -> tap Add to Playlist on **MiniPlayer** and **Now Playing** -> confirm selector opens for current track.
   - Open **Album Detail** or **Artist Detail** -> tap Add Album/Artist to Playlist -> confirm all tracks are targeted.
   - Select nested folders in **Folders** -> confirm all descendant audio files are resolved.
   - In **Queue**, add duplicate tracks, tap Save Queue -> confirm selector displays subtitle *"X unique tracks from Y queue items (duplicates collapsed)"*.
3. **Selector Tri-State & Inline Creation**:
   - Verify playlists containing all tracks show `[✓]`, some show `[-]`, none show `[ ]`.
   - Tap "New" inside sheet -> create playlist -> confirm sheet creates playlist, adds target payload, and checks the new row.
4. **Detail, Filtering, and Reordering**:
   - Open Playlist Detail -> filter list by text -> confirm row numbers keep original manual positions (e.g. #5) and reorder controls are hidden.
   - Clear filter -> use "Move to Top", "Move Up", "Move Down", "Move to Bottom" -> restart app -> confirm exact manual position order persists.
5. **Unavailable Track Handling & Active Queue Snapshot**:
   - Make a track storage path unavailable -> open Playlist Detail -> confirm track is labeled `(Unavailable)` and direct click is disabled.
   - Tap **Play All** -> confirm explicit active queue starts with available tracks only. Reorder or remove tracks in the playlist -> confirm playing queue remains unchanged.
