# Resn8 User Experience (UX) User Stories & Verification Matrix

This document defines user stories and manual UX verification workflows for Resn8. Use this matrix to test application behavior on-device or on an emulator across feature milestones.

---

## 1. Library Selection & Indexing (Milestones 0 – 2)

### US1.1 — Select a First Collection Folder
- **As a** user opening Resn8 for the first time,
- **I want to** select my music folder using Android's system document picker (`ACTION_OPEN_DOCUMENT_TREE`),
- **So that** Resn8 receives persistent access to my audio without requesting broad file system permissions.
- **Verification**:
  1. Launch app on a fresh install, choose Music or Audio Files, and tap the profile-specific folder action.
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

### US2.2 — Instant Search
- **As a** user looking for a specific track,
- **I want to** type in the search bar,
- **So that** the visible list filters instantly without lagging.
- **Verification**:
  1. Enter text in the search box (matches title, artist, album, filename).
  2. Verify the list updates reactively and clearing the query restores all indexed rows.

### US2.3 — Library Sorting
- **As a** user,
- **I want to** use context-appropriate sort fields and direction in Artists, Albums, and All Tracks,
- **So that** each Library section stays understandable and restores my preferred order.
- **Verification**:
  1. Open Artists and Albums -> confirm each sort sheet contains only Alphabetical plus Ascending/Descending and remains scrollable at large font scale.
  2. Open All Tracks -> confirm it contains Alphabetical, Artist, Album, Date Added, Play Count, Last Played, and Rating plus direction, with no Track, Unplayed, Availability, or Exclude Disliked controls.
  3. Exercise both directions for every field. Confirm unknown artist/album and never-played values remain last and ties stay deterministic.
  4. Choose different sort settings in all three tabs, switch among them, recreate the Activity, and relaunch -> confirm each tab restores its own selection and direction.

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
- **I want to** view profile-appropriate metadata, seek position, transport controls, and numeric rating actions without scrolling,
- **So that** I have full control over playback.
- **Verification**:
  1. Open Now Playing in portrait at default and increased font scale. Confirm the page has no vertical scroll and Previous, Play/Pause, Next, seek, Like, Dislike, score, and Add to Playlist are all visible above bottom navigation.
  2. Drag position slider -> confirm smooth seek to target `mm:ss` timestamp.
  3. Tap Previous / Next -> confirm track skipping.
  4. Repeat on a compact-height phone and in landscape. Confirm artwork shrinks, moves into the landscape artwork/metadata region, or disappears before any control is clipped; all actions retain usable touch targets.
  5. Trigger a recoverable playback notice and confirm its dismissible overlay does not move or hide the control layout.
  6. Play a Music item and confirm the emphasized title plus available artist/album remain visible. Play an Audio Files item with a sentence-length filename and confirm a smaller two-line title appears without artist or album.
  7. Start playback from a playlist and confirm `Playlist: <name>` appears opposite the collection selector, truncates visually when long while TalkBack announces the full label, and opens Playlist Detail with the current membership revealed near the top.
  8. Start playback from Library or Folders and confirm no playlist action appears. Confirm Now Playing never displays **View Queue**.

Milestone 4 delivered the visible rating and playlist action seams. Milestone 5 makes Like/Dislike durable and reactive; Milestone 6 implements Add to Playlist.

### US3.4 — Queue Compatibility & Item Jump
- **As a** user,
- **I want** an already-restored Queue destination to remain compatible,
- **So that** removing the redundant Now Playing entry does not invalidate saved navigation or queue behavior.
- **Verification**:
  1. Restore a previously persisted Queue destination and confirm the Queue screen still opens; confirm there is no **View Queue** entry on Now Playing.
  2. Confirm the current playing occurrence is highlighted.
  3. Tap another row in the queue -> confirm the player skips directly to that item occurrence.

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
- **I want** a play count to increment after one minute of cumulative active listening or genuine completion,
- **So that** partial listening is recognized consistently without manual navigation manufacturing false play counts.
- **Verification**:
  1. On a track at least one minute long, listen for 59,999 ms and then 60,000 ms -> confirm only the latter qualifies. Repeat with an unknown-duration fixture.
  2. Seek forward/backward and pause/resume -> confirm skipped and paused time adds nothing, previously accumulated active time remains, and the traversal increments at most once.
  3. Pause, force buffering or an audio-focus interruption, then resume -> confirm excluded time does not advance qualification.
  4. Background the app or lock the screen while audio plays -> confirm active listening continues to accumulate.
  5. Recreate the Activity/controller during playback -> confirm the current traversal continues rather than resetting or double-counting.

### US4.3 — Completion and Playback Occurrence Identity
- **As a** user replaying or moving through a queue,
- **I want** each real traversal to be counted independently while retries within one traversal remain idempotent,
- **So that** duplicate tracks, repeats, and automatic transitions produce trustworthy history.
- **Verification**:
  1. Let a short track automatically advance, repeat an item through its end, and let the final queue item reach completion; confirm each qualifying completion is recorded once.
  2. Start midway or seek near the end and allow playback to advance through completion -> confirm it counts. Seek directly to the exact endpoint without subsequent playback -> confirm it does not.
  3. Exercise short, one-minute, longer, and unknown-duration fixtures; confirm short known tracks only time-qualify by completion while the others qualify after 60 active seconds or completion.
  4. Put the same media into two queue entries; confirm each entry has distinct queue-item identity and each traversal receives a distinct history occurrence.
  5. Return to or repeat a queue item; confirm a genuine new traversal may count again while repeated threshold/completion signals for one traversal do not.
  6. Use manual next/previous, a direct jump, queue replacement, stop, and a playback failure after partial listening -> confirm none is recorded as completion.
  7. Verify history/play-count results with a deterministic most/least-played fixture or Database Inspector when no direct play-count field is visible.

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
  3. While filtered, confirm reorder actions are unavailable. Clear the filter, then long-press a track number and drag it upward/downward, including across a viewport edge. Confirm lifted-row feedback and autoscroll, then use overflow options ("Move to Top", "Move Up", "Move Down", "Move to Bottom") and verify both paths produce the same persisted order.
  4. Tap "Remove from Playlist" on a track -> verify track is removed from playlist while source file remains intact.
  5. Restart after repeated reorders -> confirm the exact order persists without duplicate/missing rows or lost added timestamps.
  6. Tap an available filtered result -> confirm playback starts the full available-only playlist in manual order at that track. Confirm **Play All** also means the full playlist, not only filtered rows.
  7. Start item 30 in a 50+ item playlist, return to that Playlist Detail, and confirm row 30 keeps its one-based position and has a visible, TalkBack-announced current-track state. Pause playback and confirm the row remains marked as the current paused track.
  8. Scroll away and press **Jump to current track** -> confirm the list animates to row 30. Filter the current row out and press the action again -> confirm search clears and the current row becomes visible.
  9. Advance to the next item while Playlist Detail is open -> confirm the marker moves without forcing the list to follow. Open another playlist containing the same media, or play it from the library -> confirm that playlist shows no current marker or jump action.
  10. Place unavailable memberships before the current item -> confirm jump and row numbering use the live playlist position rather than the shorter available-only queue index. Remove the current membership -> confirm the marker and jump action disappear while the active queue continues unchanged.

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

### US6.2 — Startup Restoration & Compact Index Feedback (Milestone 8)
- **As a** user reopening Resn8 after a scan or setup completion,
- **I want** the app to wait for authoritative state, restore my last valid destination or repair stale onboarding sessions cleanly, and show a compact success summary without repeating historical completion screens,
- **So that** relaunch feels seamless and non-intrusive.
- **Verification**:
  1. **Clean Cold Launch**: Launch configured app -> confirm neutral loading screen displays briefly and navigates directly to the saved destination (Library, Playlist, Settings, or Now Playing) without flashing Onboarding or replaying historical scan summaries.
  2. **Stale Onboarding Repair with Active Queue**: Launch app with saved route `onboarding` + active queue -> confirm app opens directly to Now Playing in a paused state at saved position.
  3. **Stale Onboarding Repair without Queue**: Launch app with saved route `onboarding` + no active queue -> confirm app opens directly to Library.
  4. **Compact Scan Completion**: Perform a fresh music folder scan -> confirm `Library ready` headline displays track count, concise change summary, `Open Library` button, and a collapsible `View scan details` toggle.
  5. **Settings Re-Index Acknowledgement**: Perform manual re-index from Settings -> confirm user remains in Settings with a brief transient completion message while persistent last-scan info updates in the source card.
  6. **Bottom Nav Re-selection**: While viewing a playlist detail screen, tap the Playlists bottom tab -> confirm backstack pops back to the top-level Playlists list view.

---

## 7. Multiple Collections and Folder-First Audio (Milestone 9)

### US7.0 — Choose the First Collection Profile
- **As a** new user with either music or general audio files,
- **I want to** classify my first collection before selecting its folder,
- **So that** indexing and navigation match the content from the first scan onward.
- **Verification**:
  1. Clear Resn8 storage and launch -> confirm only Onboarding is available, Music is preselected, and the action says `Select Music Folder`.
  2. Cancel the folder picker and naming dialog, rotate the device, and retry permission -> confirm the chosen profile remains selected.
  3. Complete Music setup -> confirm the scan workflow is unchanged, completion offers `Open Library`, and bottom navigation immediately becomes Settings, Library, Folders, and Playlists without restarting.
  4. Clear storage again, select Audio Files, and confirm the action changes exactly to `Select Audio Files Folder` with filename/folder-oriented explanatory copy.
  5. Complete Audio Files setup -> confirm the collection is `FLAT`, completion offers `Open Folders`, and bottom navigation immediately becomes Settings, Folders, and Playlists without exposing Library or Onboarding.
  6. Index untagged files and restart -> confirm no artist/album/path metadata is invented and the app restores Folders for the Audio Files collection.

### US7.1 — Create and Maintain Collections
- **As a** user with different kinds of local audio,
- **I want to** create uniquely named Music or Audio Files collections from separate folders,
- **So that** each library can be indexed and maintained independently.
- **Verification**:
  1. In Settings, create a Music collection and an Audio Files collection; confirm each accepts exactly one SAF folder and persists across restart.
  2. Attempt a blank or case-insensitive duplicate name and confirm creation/rename remains open with an actionable error.
  3. Attempt to assign the same selected folder to another collection and confirm it is rejected without cross-linking sources.
  4. Rename, re-index, and repair permission for each collection independently; confirm scan state and indexed media never leak to the other collection.

### US7.2 — Browse Filename-Oriented Audio
- **As a** user with downloaded non-music MP3 files,
- **I want** a folder-first view that does not pretend folders are artists or albums,
- **So that** the files are presented using the information they actually contain.
- **Verification**:
  1. Index an untagged flat MP3 folder as Audio Files and confirm cleaned filenames are displayed without invented artist, album, disc, or track metadata.
  2. Confirm Audio Files opens in Folders, uses the collection name as the top breadcrumb, and does not expose Library, Artists, or Albums.
  3. Play an Audio Files item and confirm the row, mini-player, Now Playing, queue, and system metadata do not show synthetic `Unknown Artist` or `Unknown Album` labels. On Now Playing, confirm artist and album are omitted even if those strings reach presentation state.
  4. Confirm Music retains its existing tag/path/filename fallback and metadata-oriented Library surfaces.
  5. Open Folders and a playlist containing sentence-length Audio Files names; confirm each cleaned title appears once, wraps to no more than two lines, and then uses end ellipsis instead of repeating the raw filename below it.
  6. At default and increased font scale, open Playlists and its detail view; confirm each page toolbar begins directly below the persistent collection selector without a second status-bar-sized gap, all controls remain reachable, and TalkBack announces the complete untruncated title.

### US7.3 — Switch Collections Safely
- **As a** user moving between collections,
- **I want** each collection to remember where its playback stopped,
- **So that** content and playlists from separate collections never become confused.
- **Verification**:
  1. Start different queues in two collections, seek to distinct positions, and switch between them from the app bar.
  2. Confirm the outgoing queue checkpoints and stops, then the target collection opens Now Playing with its own item and position prepared but paused.
  3. Confirm switching never autoplays and never chooses a queue merely because it was updated most recently.
  4. Switch to a collection that has never played anything and confirm Music opens Library while Audio Files opens Folders with no stale mini-player.
  5. Relaunch and confirm the selected collection and its active queue restore while stale browse, search, filter, sort, selection, and scroll context do not cross collections.

### US7.4 — Select All Available Audio
- **As a** user building playlists from folders or albums,
- **I want** Select All to operate on the complete applicable set,
- **So that** paging and subfolders do not produce surprising playlist contents.
- **Verification**:
  1. In a folder containing available files, unavailable files, and subfolders, choose Select All and confirm only available files directly inside the current folder are selected.
  2. Confirm unloaded paging rows are included while subfolders and every descendant file remain excluded.
  3. Explicitly select a folder checkbox and confirm the established descendant-expansion behavior still works independently.
  4. In Album Detail, choose Select All and confirm every available song across paging is selected while unavailable memberships are skipped.
  5. Toggle Select All again to deselect the applicable set, then add the selection through the shared collection-scoped playlist selector.

---

## 8. MVP Polish & Release Readiness (Milestone 10 — Upcoming)

### US8.1 — Screen Reader, Adaptive Layout & Interaction Polish
- **As a** user relying on accessibility services,
- **I want** clear content descriptions, minimum 48dp touch targets, font scaling support, and high contrast,
- **So that** the app is fully usable with TalkBack or hardware switches.
- **Verification**:
  1. Complete the core first-run, collection management, browse, playback, rating, playlist, restoration, and Select All flows with TalkBack or switch/keyboard navigation.
  2. Repeat core surfaces in portrait, landscape, and supported large-font settings -> confirm controls remain reachable and state survives configuration changes.
  3. Review loading, empty, success, and recoverable error feedback -> confirm long-running actions never appear inert and no essential action relies on gesture or color alone.

### US8.2 — Collection Settings and Playback-Surface Polish
- **As a** daily listener managing multiple libraries,
- **I want** predictable collection tools and space-efficient playback/browse screens,
- **So that** common maintenance and listening actions remain clear on a phone.
- **Verification**:
  1. Open Settings, enter Collections, create a collection through the editor, and confirm indexing progress and the final scan summary occupy the same detail-page area. Confirm type is read-only after creation.
  2. From both a collection card overflow and its detail page, rename, re-index, and reselect its folder. Confirm the concise action label and recoverable error feedback.
  3. Delete a non-active collection and cancel once before confirming. Verify the confirmation names all removed Resn8 data, source files remain present, and playlist deletion still requires confirmation.
  4. Delete the active collection and confirm the next collection restores paused or opens its profile home. Delete the final collection and confirm onboarding returns.
  5. Start a track and Play All from Playlist Detail and confirm both remain on the playlist while the mini-player updates. Open Now Playing, tap its playlist link, and confirm Playlist Detail reveals the current row with upcoming tracks below it.
  6. Confirm Now Playing has no redundant mini-player, instructional subtitle, or **View Queue** action. Verify every full-size control remains simultaneously reachable without scrolling in portrait, compact height, landscape, and large text while artwork shrinks or disappears as needed.
  7. In Folders, Album Detail, and All Tracks, select items and confirm the list does not jump, the bottom action tray stacks above the mini-player, the last row remains reachable, counts stay concise, and checkbox rows have no redundant Add overflow.
  8. Confirm Library offers Artists, Albums, and All Tracks only, and long row titles use compact accessible typography while Audio Files titles remain single-instance and at most two lines.

### US8.3 — Now Playing Context, Rating, Seeking, and Artwork
- **As a** listener moving between browsing and playback,
- **I want** the player to retain its source context and balanced controls,
- **So that** I can return to what I was browsing and adjust playback quickly.
- **Verification**:
  1. Start playback separately from a playlist, album, artist track list, folder, and All Tracks. Confirm the collection selector is upper-right and the upper-left action uses the matching icon and label.
  2. Open each origin action and confirm it returns to the exact source. In particular, Artist → Album → Track returns to that album, while a direct artist-track start returns to the artist. Queue jumps, mini-player reopening, and relaunch restoration retain the original context.
  3. Confirm Add to Playlist appears at the right above the artwork and the Dislike/score/Like cluster remains centered without shifting at scores `-1`, `0`, and positive values.
  4. Repeatedly Dislike a neutral track and confirm it stops at `-1` with no number shown. Like it through `0` and above, confirm only positive `+N` values appear, then Dislike a liked track and confirm each tap subtracts exactly one.
  5. Double-tap the left and right halves around the artwork and confirm position changes by ten seconds, clamps at the beginning/end, and shows a brief outward motion on the tapped side. Confirm the seek slider remains operable with touch and accessibility navigation.
  6. Play tracks with an external folder cover, embedded artwork, and no usable artwork. Confirm artwork loads asynchronously without interrupting playback, cached art returns promptly, rapid track changes never show the prior track's late result, and the stable placeholder remains for missing/corrupt art.

---

## Randomized Sorting

### RS1 — Rewrite a Playlist from Current Metadata
- Open Playlist Detail, choose each Randomized Sorting method, and confirm the complete playlist—not only search-visible rows—is reordered with equal metadata values randomized.
- Confirm Least/Most Played order exact play-count groups, Most Liked orders non-negative score groups, and Recently Added groups only exactly equal `firstIndexedAt` timestamps.

### RS2 — Remove Disliked Memberships Safely
- Include liked, neutral, disliked, and unavailable tracks. Apply a method and confirm the menu disclosed removal, every disliked membership was permanently deleted, unavailable non-disliked tracks remained, and success feedback reported the removed count.
- Confirm a persistence failure leaves both membership and order unchanged and provides an actionable error.

### RS3 — Synchronize Only Matching Playback
- While this playlist is playing, apply Randomized Sorting and confirm playback immediately restarts at its new first available track with a fresh traversal occurrence. Pause and repeat; confirm the new first track is prepared at zero while remaining paused.
- Confirm a disliked current track skips immediately, an all-disliked/no-playable result stops and clears this playlist's queue, and playback from another playlist or Library/Folder source remains untouched.
- Confirm the list refreshes and scrolls to the top, Play All and track taps use the persisted new order, and relaunch restoration observes the replacement explicit queue.
- Saving a generated queue as a playlist creates unique manual membership in visible order rather than a dynamic rule.

---

## Backup & Restore

### BR1 — Export Selected Collection Metadata
- Open Settings → Backup & Restore and confirm every current collection is selected by default.
- Use the all/none icon in both states and confirm its TalkBack description announces the action that will occur.
- Select a subset, save the suggested dated JSON file through the system document picker, and confirm the completion summary reports the exported collections, tracks, playlists, and history records.
- Confirm the JSON contains no source audio, artwork binaries, or portable SAF permission grant and that canceling the picker changes no app data.

### BR2 — Validate and Select an Import
- Choose malformed JSON, a non-Resn8 JSON file, a checksum-modified backup, and a newer unsupported format; confirm each is rejected before collection selection with a specific, recoverable explanation.
- Choose a valid backup and confirm no collection is selected initially. Exercise the all/none icon and individual checkboxes, including large-font and TalkBack navigation.
- Confirm each row summarizes profile, tracks, playlists, and history. For an ID or normalized-name conflict, confirm the local collection defaults to safe **Skip** behavior and **Replace existing collection** is explicit.
- Review the final summary, cancel once, then confirm import. Verify source audio remains unchanged and a failure rolls back every database change.

### BR3 — Restore and Reconnect Source Folders
- Import a full backup into a clean app database and verify collection names/profiles, indexed metadata, ratings, play counts, last-played values, history, playlists/order, saved queues, queue-item identities, and applicable session state are restored.
- Confirm restored files remain unavailable until current folder access is verified. Use each **Select folder** action, grant the original library folder, and allow re-indexing to finish.
- Verify conservatively matched files become playable without losing restored metadata. Keep some source files absent or ambiguous and confirm they remain unavailable in playlists/history/queues and are reported rather than deleted.
- Repeat with only a subset of collections and confirm unrelated local collections and global session state remain unchanged.

---

## Home-Screen Playback Widget

### W1 — Place and Resize the Responsive Player
- On a disposable Android 14, 15, or 16 target, open the launcher widget picker and confirm one **Resn8 Player** entry has a readable preview and description.
- Place it at approximately 4x2 cells. Confirm the compact layout shows current title, profile-appropriate secondary text, explicit rating state, and Dislike, Previous, Play/Pause, Next, and Like controls without clipping.
- Confirm the compact metadata/control group is centered horizontally and vertically. At wider/taller compact sizes, verify controls grow from 48dp to 56dp targets without overlap.
- Resize it to approximately 4x4 cells. Confirm current artwork or the stable placeholder appears and **Up next** shows no more than three playable occurrences.
- Repeat with long titles, large font, light/dark wallpaper themes, and both Music and Audio Files collections. Confirm Audio Files never shows synthetic artist/album labels.

### W2 — Control Playback and Rating Safely
- Start a queue containing duplicate media occurrences. Use every compact transport control and confirm the existing `Resn8MediaService` session responds without creating a second player.
- Tap Like and Dislike rapidly from the widget, including while the foreground app is already connected. Confirm the action applies to the track current at execution time, Dislike clamps at `-1`, and the widget and app show the same authoritative score.
- Confirm positive scores appear centered on Like (`+1` through `+99`, then `99+`). Confirm no count appears at neutral `0` or disliked `-1`; with TalkBack, verify the full uncapped score or rating state is still announced in both the widget and Now Playing.
- Pause playback, tap one of the upcoming rows, and confirm the exact displayed queue occurrence starts at zero. Unavailable items must not appear and the list must not wrap at queue end.
- Background, lock, restart the launcher, and recreate the app process. Confirm the widget restores the selected collection's active queue through `UiSessionState.activeQueueId` and never autoplays merely because it refreshed.

### W3 — Navigate and Recover
- Tap active track content from both sizes and confirm a cold or already-running app opens Now Playing exactly once.
- Clear active playback while the selected collection has playlists; tap the friendly empty state and confirm Playlists opens. Delete all playlists and repeat, confirming Folders opens. With no collection configured, confirm Onboarding opens.
- Temporarily make the playback service unavailable and confirm the widget shows a bounded retry state without replacing or deleting the saved queue. Restore availability, tap retry, and confirm normal state returns.
- With TalkBack, verify logical focus order, explicit enabled/disabled action labels, artwork semantics, upcoming-row titles, 48dp targets, and rating meaning that does not rely on color.
