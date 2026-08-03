# Resn8 User Experience (UX) User Stories & Verification Matrix

This document defines user stories and manual UX verification workflows for Resn8. Use this matrix to test application behavior on-device or on an emulator across feature milestones.

---

## 1. Library Selection & Indexing (Milestones 0 – 2)

### US1.1 — Select a Music Root Folder
- **As a** user opening Resn8 for the first time,
- **I want to** select my music folder using Android's system document picker (`ACTION_OPEN_DOCUMENT_TREE`),
- **So that** Resn8 receives persistent access to my audio without requesting broad file system permissions.
- **Verification**:
  1. Launch app on a fresh install or tap "Select Music Folder" on Onboarding.
  2. Choose a folder containing audio files via Android's System File Picker.
  3. Confirm persistent read access is granted and indexing starts automatically.

### US1.2 — Indexing Progress & Summary
- **As a** user indexing my music,
- **I want to** see live progress (folder count, track count, errors) and a clear completion summary,
- **So that** I know my library has been indexed accurately.
- **Verification**:
  1. Observe the progress bar and item counters during scan.
  2. Confirm final track count matches the folder contents.
  3. Inspect scan summary for any corrupt or unsupported file reports.

### US1.3 — Safe Re-Indexing
- **As a** user who has added new music files to my selected folder,
- **I want to** trigger a re-index,
- **So that** new files appear in my library while my existing ratings, play counts, listening history, and playlist memberships remain intact.
- **Verification**:
  1. Add a new file to the indexed folder on storage.
  2. Trigger "Re-index Library" from settings/onboarding.
  3. Confirm new files are added, first-indexed timestamps are preserved for existing tracks, and ratings/history are unchanged.

### US1.4 — Enter and Play the Indexed Library
- **As a** user whose first scan completed successfully,
- **I want to** open the exact collection that was indexed and start a track,
- **So that** the scan immediately produces a usable music library.
- **Verification**:
  1. From the completion summary, confirm indexed-audio, folder, document, artwork, ignored non-audio, unsupported audio-like, unreadable, and metadata-fallback totals are visible.
  2. Tap **Go to Library** -> confirm Artists loads populated data rather than a transient or synthetic empty collection.
  3. Focus search, type and clear a query, then switch among Artists, Albums, and All Tracks -> confirm no process crash and no Room foreign-key error.
  4. Open Folders and confirm it resolves the same indexed source hierarchy.
  5. In All Tracks, tap one available song -> confirm an explicit queue is persisted for the active collection and audible playback begins through `Resn8MediaService`.
  6. Background the app and use the media notification to pause/resume, then return and confirm the same song and queue remain active.

---

## 2. Library & Folder Browsing (Milestone 3)

### US2.1 — Surface Navigation & Metadata Drilldown
- **As a** music listener,
- **I want to** browse my collection by **Artist**, **Album**, **All Tracks**, and **Folders**,
- **So that** I can find music by hierarchy or direct folder structure.
- **Verification**:
  1. Switch between tabs: Artists, Albums, All Tracks, Folders.
  2. Tap an Artist -> drill down to their Albums and Tracks.
  3. Tap an Album -> drill down to its Track list ordered by disc and track number.
  4. Tap a Folder -> navigate child subfolders with breadcrumb path navigation.

### US2.2 — Instant Search & Filter
- **As a** user looking for a specific track,
- **I want to** type in the search bar and apply availability/rating filters,
- **So that** the visible list filters instantly without lagging.
- **Verification**:
  1. Enter text in the search box (matches title, artist, album, filename).
  2. Open Filter Sheet: toggle Availability (All, Available Only, Unavailable Only) and Exclude Disliked (`likeScore >= 0`).
  3. Verify list updates reactively.

### US2.3 — Library Sorting
- **As a** user,
- **I want to** sort my tracks by Artist, Album, Title, Track Number, Recently Added, Most Played, Least Played, Unplayed, Most Recent, Least Recent, or Most Liked,
- **So that** I can organize my view for discovery.
- **Verification**:
  1. Select different sort options in the filter sheet.
  2. Confirm list order updates deterministically.

---

## 3. Playback & Transport Controls (Milestone 4)

### US3.1 — Start Queue from Library Context
- **As a** user browsing tracks,
- **I want to** tap any track in All Tracks, Artist Detail, Album Detail, or Folders,
- **So that** an explicit queue is created from the visible available order and playback begins immediately at that track.
- **Verification**:
  1. Tap a track in any view.
  2. Confirm playback starts immediately at the selected track.
  3. Confirm the active explicit queue contains only available tracks from that visible context.

### US3.2 — Persistent Mini-Player
- **As a** user navigating the app while music plays,
- **I want to** see a mini-player bar at the bottom with title, artist, rating indicator, play/pause, and next controls,
- **So that** I can control playback from any screen.
- **Verification**:
  1. Start playing a track.
  2. Navigate between Settings, Library, Folders, and Playlists after setup (and through Onboarding during a fresh/recovery flow).
  3. Confirm mini-player stays anchored above navigation bar with current track info.
  4. Tap Play/Pause or Next on mini-player -> confirm immediate response.
  5. Tap mini-player body -> confirm navigation to Now Playing screen.
  6. For a track with a positive or negative score, confirm the mini-player shows the corresponding liked or disliked indicator without relying on color alone.

### US3.3 — Now Playing Screen & Seek Bar
- **As a** listener on the Now Playing screen,
- **I want to** view artwork, metadata, seek position slider, transport controls, and numeric rating score,
- **So that** I have full control over playback.
- **Verification**:
  1. Open Now Playing screen.
  2. Drag position slider -> confirm smooth seek to target `mm:ss` timestamp.
  3. Tap Previous / Next -> confirm track skipping.
  4. View Like, Dislike, and Add to Playlist buttons.

Milestone 4 delivered the visible rating and playlist action seams. Milestone 5 makes Like/Dislike durable and reactive; Milestone 6 implements Add to Playlist.

### US3.4 — Queue Inspection & Item Jump
- **As a** user,
- **I want to** view the active queue list and jump to any queued track,
- **So that** I can preview what plays next and change tracks directly.
- **Verification**:
  1. Open Queue screen from Now Playing.
  2. Confirm current playing track is highlighted.
  3. Tap another row in the queue -> confirm player skips directly to that item occurrence.

### US3.5 — Background Playback & Notification Controls
- **As a** user,
- **I want to** switch apps or turn off my screen while music plays,
- **So that** playback continues smoothly with Android system controls.
- **Verification**:
  1. Background app or lock screen.
  2. Check Android media notification shade for artwork, title, artist, play/pause, and next/previous controls.
  3. Confirm play/pause from notification works.

### US3.6 — Audio Disconnect & Failure Resilience
- **As a** user listening over headphones or Bluetooth,
- **I want** playback to pause when audio is disconnected and nonfatal error notices to appear if a file is unreadable,
- **So that** music doesn't blast from speakers unexpectedly or crash on bad files.
- **Verification**:
  1. Unplug headphones during playback -> confirm automatic pause (`AUDIO_BECOMING_NOISY`).
  2. Play an unreadable file -> confirm nonfatal notification notice appears and player advances to next candidate once without infinite looping.

---

## 4. Ratings & Meaningful Play Accounting (Milestone 5 — Upcoming)

### US4.1 — Atomic Signed Score Ratings
- **As a** user rating music,
- **I want to** Like (+1) or Dislike (-1) a track,
- **So that** my preferences update immediately and persist across app restarts.
- **Verification**:
  1. Start at score `0`, then tap Like, Like, Dislike, Dislike, Dislike and confirm `0 -> 1 -> 2 -> 1 -> 0 -> -1` on Now Playing.
  2. Confirm the mini-player indicator and relevant library filter/sort results refresh from the authoritative score.
  3. Tap rapidly in both directions and confirm no action is lost or overwritten by stale UI state.
  4. Confirm rating never pauses/skips playback, removes the item, or changes the active queue order.
  5. Relaunch the app and confirm the score persists. Use Database Inspector only when validating the stored row directly.

### US4.2 — Trustworthy Play Count Accounting
- **As a** user,
- **I want** a play count to increment only when I actually listen to a track (e.g. `min(50% duration, 4 minutes)` or completion),
- **So that** seeking past a track doesn't manufacture false play counts.
- **Verification**:
  1. Seek past 50% duration -> confirm play count does NOT increment instantly.
  2. Seek backward, pause/resume, and cross the same threshold more than once -> confirm only actual accumulated listening counts and the traversal increments at most once.
  3. Pause, force buffering or an audio-focus interruption, then resume -> confirm excluded time does not advance qualification.
  4. Background the app or lock the screen while audio plays -> confirm active listening continues to accumulate.
  5. Recreate the Activity/controller during playback -> confirm the current traversal continues rather than resetting or double-counting.

### US4.3 — Completion and Playback Occurrence Identity
- **As a** user replaying or moving through a queue,
- **I want** each real traversal to be counted independently while retries within one traversal remain idempotent,
- **So that** duplicate tracks, repeats, and automatic transitions produce trustworthy history.
- **Verification**:
  1. Let a short track automatically advance to the next item and let the final queue item reach completion; confirm each qualifying completion is recorded once.
  2. Seek directly to the end without positive active listening -> confirm natural completion does not count.
  3. Exercise an unknown-duration fixture; confirm four active minutes or positive-listening natural completion qualifies.
  4. Put the same media into two queue entries; confirm each entry has distinct queue-item identity and each traversal receives a distinct history occurrence.
  5. Return to or repeat a queue item; confirm a genuine new traversal may count again while repeated threshold/completion signals for one traversal do not.
  6. Verify history/play-count results with a deterministic most/least-played fixture or Database Inspector when no direct play-count field is visible.

---

## 5. Manual Playlists (Milestone 6)

### US5.1 — Create and Manage Playlists
- **As a** user,
- **I want to** create, rename, and delete playlists with unique normalized names within my collection,
- **So that** I can assemble and maintain custom audio mixes without altering my source audio files on disk.
- **Verification**:
  1. Open Playlists tab -> tap **+** or "+ New Playlist".
  2. Create a playlist (e.g., "Road Trip"). Try creating a duplicate name ("road trip") -> confirm validation error prevents duplicate normalized names.
  3. Rename playlist to "Road Trip 2026" -> confirm updated title across app.
  4. Enter a blank name and a duplicate with different surrounding whitespace/case -> confirm the dialog preserves the entered value and explains the problem.
  5. Tap Delete -> confirm warning dialog explains that playlist membership will be removed while source audio files on disk remain safe and untouched.
  6. Confirm the list shows the playlist's unique track count and updates it after membership changes.
  7. Relaunch the app -> confirm the playlist remains under the persisted active collection rather than disappearing into a literal/default collection.

### US5.2 — Reusable Add-to-Playlist Workflow
- **As a** user,
- **I want to** add single tracks, multi-selected tracks, full albums, artist track lists, active queue tracks, or folder descendants using one reusable bottom sheet selector,
- **So that** adding music to playlists is fast, intuitive, and consistent from every screen.
- **Verification**:
  1. From **Now Playing**, **mini-player**, or any single **Track Row**, tap "Add to Playlist..." -> confirm the selector opens with the correct track and collection.
  2. From **Album Detail**, tap "Add Album to Playlist..." -> confirm all album tracks are targeted in deterministic disc/track/title order, including rows beyond the currently loaded Paging window.
  3. From **Artist Detail**, tap "Add Artist Songs to Playlist..." -> confirm all artist tracks are targeted, not only visible/loaded rows.
  4. From **Folder Browser**, select a folder -> confirm all indexed audio in nested descendants contributes once to the resolved target count. Add an overlapping child file/folder -> confirm it is not counted twice.
  5. From **Queue Screen**, save a queue that contains the same media more than once -> confirm Resn8 explains that manual playlists keep one membership per track and preserves the first occurrence's order.
  6. Inspect candidate playlists in `PlaylistSelectorSheet`: confirm playlists containing **all** selected tracks appear first with checked `[✓]` boxes, playlists containing **some** appear with `[-]` mixed boxes, and others appear with `[ ]` unchecked boxes.
  7. Activate an unchecked row -> confirm all targets are added. Activate a mixed row -> confirm missing targets are added and it becomes checked. Activate a checked row -> confirm all target memberships are removed.
  8. Toggle multiple playlists without dismissing the sheet and rotate/recreate the Activity -> confirm completed mutations are not repeated and the target collection/payload remains correct.
  9. Create a playlist inline -> confirm success adds the full payload and checks the new row; confirm duplicate-name or database failure keeps the dialog open and reports the error.

### US5.3 — Playlist Detail, Text Search & Reordering
- **As a** user,
- **I want to** view playlist items in manual order, filter items by text for inspection, reorder items, and remove items,
- **So that** I can maintain my custom playlist order.
- **Verification**:
  1. Open a playlist with multiple tracks -> verify items are rendered in manual position order (`1`, `2`, `3`...).
  2. Type text in the playlist search bar -> verify visible items filter instantly, retain their original manual row numbers, and do not mutate rank positions.
  3. While filtered, confirm reorder actions are unavailable. Clear the filter, then use drag reorder and overflow options ("Move to Top", "Move Up", "Move Down", "Move to Bottom") -> verify both paths produce the same order.
  4. Tap "Remove from Playlist" on a track -> verify track is removed from playlist while source file remains intact.
  5. Restart after repeated reorders -> confirm the exact order persists without duplicate/missing rows or lost added timestamps.
  6. Tap an available filtered result -> confirm playback starts the full available-only playlist in manual order at that track. Confirm **Play All** also means the full playlist, not only filtered rows.

### US5.4 — Unavailable Membership & Queue Isolation
- **As a** user whose removable storage may be temporarily absent,
- **I want** playlist membership and active queues to survive availability changes and playlist edits,
- **So that** organization and listening context are never silently destroyed.
- **Verification**:
  1. Add tracks from removable/test storage to a playlist, make that source unavailable, and reopen playlist detail -> confirm those rows remain in their manual positions and are labeled unavailable.
  2. Confirm an unavailable row can be removed from the playlist but cannot be chosen as a playback start.
  3. Start a playlist containing available and unavailable tracks -> confirm the saved queue contains the available snapshot, playback skips unavailable media with a visible explanation, and the playlist retains all memberships.
  4. Start an available playlist, then reorder/remove tracks and delete the playlist -> confirm the already active explicit queue and its queue-item IDs remain unchanged.
  5. Restore source availability/re-index -> confirm retained playlist rows become playable again without re-adding them.

## 6. Relaunch Restoration & Context Recovery (Milestone 7 — Upcoming)

### US6.1 — Seamless Relaunch Restoration
- **As a** user returning to Resn8 after process death or app restart,
- **I want** Resn8 to restore my exact queue, track, position (paused), and screen route,
- **So that** I never lose my listening context.
- **Verification**:
  1. Start a queue containing duplicate occurrences of one media file, play the second occurrence, seek to a recognizable position, then kill the app process.
  2. Relaunch normally -> confirm the exact saved queue and stable queue-item occurrence are rebuilt, the same item is prepared at the bounded saved position, and no audio starts automatically.
  3. Confirm restoring the Activity/controller does not mint a new playback traversal occurrence or duplicate meaningful-play credit.
  4. Change repeat/speed state through a test fixture or available control, restart, and confirm the saved values are applied before preparation even when their user-facing controls remain post-MVP.
  5. Start a different queue while restoration is resolving -> confirm the newer user action wins and the older restore cannot replace it.

### US6.2 — Durable, Efficient Checkpointing
- **As a** user whose app or service may be stopped at any time,
- **I want** recent listening progress saved without harming playback performance,
- **So that** restoration is accurate and reliable.
- **Verification**:
  1. While playing, wait across multiple checkpoint intervals and confirm persisted position advances at a bounded cadence rather than on every UI poll.
  2. Pause, complete a seek, change items, background the app, remove its task, and stop the service -> confirm each event requests an immediate checkpoint.
  3. Race a periodic write with an item transition -> confirm serialized writes leave the newer item/index/occurrence state authoritative.
  4. Force shutdown between periodic intervals -> confirm restoration loses no more than the documented checkpoint window.

### US6.3 — Browsing Context Restoration and Safe Fallback
- **As a** user browsing a large library,
- **I want** to return to the artist, album, folder, playlist, queue, player, or filtered library view I last used,
- **So that** I do not have to reconstruct my place after every relaunch.
- **Verification**:
  1. Relaunch separately from each meaningful destination and confirm its typed identifiers plus library surface, search, filters, and sort restore after session loading completes.
  2. Confirm transient dialogs, sheets, multi-selection, and intermediate onboarding progress do not reopen.
  3. Remove a selected playlist or make an artist/album/folder target invalid, then relaunch -> confirm deterministic fallback to its valid parent and persistence of the corrected destination.
  4. Confirm active queue resolution follows `UiSessionState.activeQueueId`, not whichever saved queue was most recently updated.

### US6.4 — Onboarding Graduation and Settings
- **As a** returning user with a configured library,
- **I want** the setup navigation item to become Settings,
- **So that** source maintenance and future preferences have a durable, discoverable home.
- **Verification**:
  1. Fresh-install launch -> confirm Onboarding is shown until a usable collection/source exists.
  2. Complete indexing and relaunch -> confirm the top-level slot is labeled Settings and normal startup does not flash or route through Onboarding.
  3. Open Settings -> confirm collection/source status, Re-index Library, and permission-reselection/recovery actions are available.
  4. Revoke source permission or remove the configured collection through a test fixture -> confirm recovery routes to an actionable Onboarding/Settings state rather than an empty library loop.

### US6.5 — Unavailable Playback Recovery
- **As a** user whose storage or permission is temporarily unavailable,
- **I want** an explanation and recovery choices without losing my queue,
- **So that** removable storage and permission problems do not erase listening context.
- **Verification**:
  1. Save progress on an item, revoke its source permission or detach its storage, and relaunch -> confirm Resn8 distinguishes the unavailable-source condition and preserves queue/history rows.
  2. Use Retry or reselect permission -> confirm the same item and position can recover when access returns.
  3. Choose Skip to Next Available -> confirm Resn8 advances once to a playable item and persists that replacement only after the action succeeds.
  4. Test missing queue row, invalid index/media pairing, and a queue with no available items -> confirm each has a bounded fallback and no crash or silent queue deletion.

---

## 7. Smart Queue Generation (Milestone 9 — Planned)

### US7.1 — Generate Smart Queues
- **As a** user,
- **I want to** generate Random Eligible, Unplayed, Least Played, Most Played, Most Liked, Most Recently Played, and Least Recently Played queues from the currently visible scope,
- **So that** I can rediscover hidden music in my library.
- **Verification**:
  1. Apply a collection/folder, artist, album, search, and filter combination -> open generation and confirm the preview describes that exact scope and eligible count.
  2. Change the visible UI after opening the preview -> confirm the pending request uses the immutable reviewed snapshot rather than silently adopting later changes.
  3. Generate each of the seven modes -> confirm the resulting Queue screen shows an explicit order and starts through the normal playback connection.
  4. Confirm Least/Most Played group by play count and randomize only equal-count peers.
  5. Confirm Most Recently Played puts played tracks newest-first with unplayed tracks randomized last; Least Recently Played puts unplayed tracks randomized first, followed by oldest-played groups.
  6. Confirm generation does not block navigation or the main thread for a representative large library.

### US7.2 — Disliked File Exclusion
- **As a** user,
- **I want** disliked tracks (`likeScore < 0`) excluded from smart queues by default,
- **So that** tracks I dislike aren't suggested automatically.
- **Verification**:
  1. Prepare scores `3, 3, 1, 0, 0, -1`, generate Most Liked, and confirm `[3s randomized] -> [1] -> [0s randomized]` with the negative item absent.
  2. Generate every other mode -> confirm negative-score and unavailable tracks are absent by default and the preview states both exclusions.
  3. Use a scope containing only disliked/unavailable tracks -> confirm a specific zero-eligible state appears and no empty queue replaces the active queue.

### US7.3 — Reproducible Explicit Snapshots
- **As a** user,
- **I want** a generated queue to remain stable until I explicitly regenerate it,
- **So that** ratings, re-indexing, or new files do not unexpectedly reorder what I am listening to.
- **Verification**:
  1. Generate twice from identical fixture data with the same injected seed -> confirm identical explicit media order and distinct queue-item identities per saved queue.
  2. Generate with a different seed -> confirm eligible membership remains equal while at least one randomized peer group can differ.
  3. After generation, change ratings/play counts, add a file, and re-index -> confirm the active saved queue order does not change.
  4. Tap **Regenerate** -> confirm a new request/seed is reviewed and a new explicit queue replaces the active queue only after successful persistence.
  5. Relaunch the app/database process -> confirm the saved mode, normalized filter snapshot, seed, and explicit order can be inspected unchanged through Milestone 7 restoration.

### US7.4 — Smart Queue Errors, Cancellation & Manual Save
- **As a** user,
- **I want** generation failures and edge cases to preserve my current listening context,
- **So that** experimenting with smart modes is safe.
- **Verification**:
  1. Cancel generation during a large request -> confirm no partial queue becomes active.
  2. Simulate query/persistence failure -> confirm the current queue remains active and a retryable error explains which phase failed.
  3. Generate empty and single-item scopes -> confirm empty produces no replacement and a single item persists/plays normally.
  4. From a generated queue, choose **Save as Playlist** -> confirm the Milestone 6 selector creates unique manual membership in visible queue order without turning the playlist into a dynamic rule.

---

## 8. MVP Polish & Release Readiness (Milestone 10 — Upcoming)

### US8.1 — Screen Reader, Adaptive Layout & Interaction Polish
- **As a** user relying on accessibility services,
- **I want** clear content descriptions, minimum 48dp touch targets, font scaling support, and high contrast,
- **So that** the app is fully usable with TalkBack or hardware switches.
- **Verification**:
  1. Complete the core first-run, browse, playback, rating, playlist, restoration, and smart-queue flows with TalkBack or switch/keyboard navigation.
  2. Repeat core surfaces in portrait, landscape, and supported large-font settings -> confirm controls remain reachable and state survives configuration changes.
  3. Review loading, empty, success, and recoverable error feedback -> confirm long-running actions never appear inert and no essential action relies on gesture or color alone.
