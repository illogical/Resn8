# Resn8 Software Specification

## 1. Product Summary

Resn8 is an offline-first Android audio player for audio files stored in user-selected folders on internal shared storage or removable SD cards. Its distinguishing feature is durable listening and rating data that can generate queues which surface unheard or least-played files, prioritize liked files, and exclude disliked files.

The first usable release supports multiple named collections, each under one user-selected folder. A collection is either organized music or filename-oriented audio files. The shared data model also supports later contextual-folder collections (for example, `Podcasts/AI`) and multiple source folders without requiring a database redesign.

### Goals

- Select a folder through Android's system picker and recursively index supported audio files.
- Switch between multiple local collections and browse organized music by artist, album, track, and source folder or browse general audio by filename and folder.
- Play audio reliably in the foreground and background with Android system controls.
- Track meaningful plays, last-played time, current position, and a signed like score.
- Create ordered manual playlists and add one file, many files, or a folder's descendants.
- Generate and persist randomized queues from the current collection or filtered result set.
- Restore the exact queue, item, position, and browsing context after process death or restart.
- Keep all library data local; Resn8 does not upload audio, metadata, or listening history.

### MVP Boundaries

The MVP includes multiple uniquely named collections with one selected folder each. User-facing profiles are `MUSIC` and `FLAT` (shown as Audio Files), with recursive manual re-indexing, profile-appropriate browsing, background playback, rating, manual playlists, and playback-state restoration. The schema and domain interfaces retain room for multiple roots and contextual collections, but those management experiences are post-MVP.

Scheduled indexing, smart randomized queues, advanced search, playback-speed control, multiple roots per collection, contextual collection UI, moving or deleting disliked source files, tag editing, cloud playback, casting, lyrics, equalization, and Android Auto-specific browsing are post-MVP.

Images and videos mentioned in the brainstorm are out of scope. Resn8 indexes playable audio only.

## 2. Core Concepts and Rules

### 2.1 Collection and Source Access

- A **Collection** is a logical audio library with a stable ID, normalized-unique display name, and profile: `MUSIC`, `CONTEXTUAL`, or `FLAT`. The MVP creation UI exposes Music and Audio Files (`FLAT`) only.
- A **Root Source** is a folder selected with `ACTION_OPEN_DOCUMENT_TREE`. Resn8 takes and stores persistent read permission for its tree URI.
- The MVP allows multiple collections with exactly one root source per collection. A persisted tree URI may belong to only one collection. APIs and tables continue to use collection/source identifiers so later releases can add multiple roots without migration of the conceptual model.
- Resn8 never requires broad storage permission and never modifies source audio during normal indexing or playback.
- If permission is revoked or an SD card is unavailable, the source and its affected files are shown as unavailable. Statistics and playlist membership are retained.

### 2.2 Shared Media Schema

All audio types use one `MediaFile` entity. Common fields are required; music-specific metadata is nullable. This avoids parallel schemas while allowing each collection profile to present a different hierarchy.

| Entity | Required fields and behavior |
| --- | --- |
| `Collection` | Stable ID, name, normalized unique name, profile, created/updated timestamps |
| `RootSource` | Stable ID, collection ID, persisted tree URI, display name, availability, last scan status/timestamps |
| `FolderNode` | Stable ID, source ID, parent ID, relative path, display name; represents the indexed hierarchy |
| `MediaFile` | Stable ID, source ID, folder ID, document URI/ID, relative path, filename, display title, MIME type, size, duration, modified time, first-indexed time, availability, metadata scan status |
| Music metadata | Nullable title, artist, album artist, album, disc number, track number, year, genre, artwork reference |
| Listening statistics | `playCount >= 0`, nullable `lastPlayedAt`, signed `likeScore` defaulting to `0` |
| `Playlist` | Stable ID, collection ID, unique name within collection, created/updated timestamps |
| `PlaylistItem` | Playlist ID, media ID, unique membership, stable manual position, added timestamp |
| `PlaybackHistory` | Media ID, playback traversal occurrence ID (`sessionOccurrenceId`), start/qualification or end times, accumulated listened duration, completion/counting result |
| `SavedQueue` | Queue ID, collection ID, kind, optional generation rule/filter/seed, explicit ordered items with stable `queueItemId` values, current index/media and distinct `currentOccurrenceId`, position, playback state, timestamps |
| `UiSessionState` | Last route plus selected collection, folder, artist, album, playlist, and active filter/sort identifiers |

Room is the source of truth for indexed metadata, relationships, statistics, playlists, saved queues, and session state. Source audio remains addressed through content URIs. Foreign keys and indexes cover collection/source/folder membership, artist, album, track/disc number, first-indexed time, play count, last played, like score, playlist position, and availability.

### 2.3 Metadata Resolution

During indexing, Resn8 reads supported embedded metadata, including MP3 ID3 tags when present. `MUSIC` display values use this precedence:

1. Valid embedded title, artist/album artist, album, disc, and track fields.
2. For a `MUSIC` collection, relative folder structure interpreted as `Artist/Album/...` and a leading filename track number where the tag is absent.
3. A cleaned filename without its extension.
4. `Unknown Artist` and `Unknown Album` only at presentation boundaries; missing values remain null in storage.

The parser accepts common track prefixes such as `01 Title`, `01 - Title`, and `1-01 Title`. It must not overwrite valid embedded tags. A scan summary records counts of tag-derived, path-derived, unrecognized, unreadable, and unsupported files so real sample libraries can inform later parser improvements.

For `FLAT` collections, valid embedded common metadata may remain in nullable shared fields, but path and filename parsing must not invent artist, album, disc, or track metadata. The folder-first UI uses tag title or cleaned filename and does not show synthetic `Unknown Artist` or `Unknown Album` labels. Folder and playlist rows render that display title once, wrapping it to at most two lines with end ellipsis instead of repeating the raw filename as a subtitle. For future `CONTEXTUAL` collections, relative folders become a category hierarchy and music fields remain optional.

### 2.4 Re-indexing and File Identity

- A scan recursively enumerates the selected tree, filters for decoder-supported audio MIME types/extensions, extracts metadata off the main thread, and upserts results in bounded batches.
- Existing records are matched by provider document ID/URI first and relative path second. A conservative size/duration/modified-time signature may recover a renamed item only when the match is unique.
- New files are inserted with neutral statistics and a first-indexed timestamp. Changed or returning files preserve that timestamp while refreshing source and extracted metadata without resetting ratings, history, or playlist membership.
- Missing files are marked unavailable rather than deleted. A later matching scan restores them. Permanently removing retained records is a separate confirmed maintenance action outside MVP.
- A scan is cancellable, reports progress and a final summary, survives configuration changes, and never exposes a partially replaced library snapshot.
- Duplicate content at different source URIs is treated as distinct media unless a later deduplication feature explicitly links it.

### 2.5 Rating Semantics

- `likeScore` is a signed integer with default `0`.
- Every explicit Like action atomically adds `1`; every explicit Dislike action atomically subtracts `1`.
- A score greater than zero is liked, zero is neutral, and less than zero is disliked.
- The UI displays the current numeric score and provides both actions; pressing the opposite action is how a user adjusts the score back toward neutral.
- Rating a playing item does not automatically skip it or remove it from a manual playlist. Negative items are excluded from newly generated smart queues by default.

### 2.6 Meaningful Play Semantics

`playCount` increments at most once for each playback traversal occurrence after accumulated active listening reaches:

`min(50% of known duration, 4 minutes)`

- `queueItemId` identifies a stable entry in a saved queue. `currentOccurrenceId`/history `sessionOccurrenceId` identifies one traversal of that entry and is the exactly-once key; it is never substituted with `queueItemId`.
- Duplicate media in one queue has distinct queue-item IDs. Initial entry, next/previous, a direct jump, repeat, or replay/re-entry creates a fresh traversal occurrence ID. Pause/resume, seeking, buffering, and UI/controller recreation preserve the current occurrence.
- The authoritative playback service observes active listening. Time advances only while audio is actually playing and is measured with a monotonic elapsed-time source; persisted history and last-played timestamps use epoch milliseconds. Paused, buffering, stopped/error, and audio-focus-interrupted time does not count.
- Seeking beyond the threshold does not count skipped time; only accumulated listened time qualifies.
- Pause/resume and seeking within the same occurrence do not create another play.
- Replaying through a new traversal occurrence can create another play.
- When duration is unknown, four minutes or natural completion qualifies. Both an automatic transition after an item completes and final-queue `STATE_ENDED` are natural-completion signals. Natural completion qualifies a shorter or partially heard file only when accumulated active listening is greater than zero, so seeking directly to the end cannot count.
- Finalize/qualify the prior occurrence before initializing the next one on a transition. Interrupted or failed playback cannot manufacture a count, and repeated commit attempts for one occurrence are idempotent.
- On qualification, `playCount`, `lastPlayedAt`, and the history record are committed atomically. Position and process-death restoration state are checkpointed separately and more frequently in Milestone 7.

### 2.7 Filters, Sorts, and Smart Queue Generation

A generation request operates on an immutable snapshot of currently visible, available media after collection, folder/descendant, artist, album, search, and other active filters are applied. Disliked files (`likeScore < 0`) are excluded by default from every smart mode.

Supported modes are:

| Mode | Ordering rule |
| --- | --- |
| Random eligible | Uniform shuffle of all eligible files |
| Unplayed | Keep `playCount == 0`, then shuffle |
| Least played | Ascending `playCount`; shuffle independently within each equal-count group |
| Most played | Descending `playCount`; shuffle independently within each equal-count group |
| Most liked | Positive `likeScore` groups descending, shuffled within each equal-score group; neutral files shuffled as the final group |
| Most recently played | Descending `lastPlayedAt`; unplayed files form a shuffled final group |
| Least recently played | Unplayed files shuffled first, then ascending `lastPlayedAt` with ties shuffled |

The user's target most-liked example is normative: scores `3, 3, 1, 0, 0, -1` produce the two score-3 files in random order, then score 1, then the two neutral files in random order; score -1 is absent.

Generation uses an injectable seeded random source. It saves the mode, normalized filter snapshot, seed, and explicit ordered media IDs. The explicit order is authoritative: ratings, statistics, filters, or new scans do not reshuffle an active queue. The user regenerates to incorporate changes. Unavailable items are skipped with a visible explanation, while the saved queue remains recoverable.

Top-level Music Library sorts are context-specific and field-based. Artists and Albums expose Alphabetical ordering; All Tracks exposes Alphabetical, Artist, Album, Date Added, Play Count, Last Played, and Rating. Every surface has an independently persisted Ascending/Descending choice. Unknown artist, album, and last-played values remain last in either direction. Normal sorts are deterministic, using normalized title and stable media ID as final tie-breakers. Disc/track ordering remains an internal canonical order for album, artist, folder, and playback snapshots rather than an All Tracks menu option.

## 3. User Experience

### 3.1 First Run and Library

1. Explain that Resn8 is local-only and request a Music collection folder through the system folder picker.
2. Ask for a collection name, defaulting to the selected folder name, and start recursive indexing.
3. Show progress, discovered/unsupported/error counts, and a usable empty/error state.
4. Open the library with top-level Artist, Album, Folder, and All Tracks views.
5. Artist selection shows that artist's albums and tracks; album selection shows tracks ordered by disc and track number, falling back to title/filename.

The folder browser mirrors indexed relative paths, uses the collection name for its top breadcrumb, shows file availability, supports file/folder multi-selection, and expands explicitly selected folders to all currently indexed descendant audio for bulk playlist operations. Its Select All action selects every available direct audio file in the current folder across paging, excluding unavailable files, subfolders, and all descendants of those subfolders. Album Select All similarly selects every available song in that album across paging.

Onboarding is a conditional first-run/recovery flow, not a permanent destination once a usable collection and source exist. After setup, its top-level navigation slot becomes Settings. Settings contains Collections and About subpages. Collections provides a list/detail editor for creation, rename, collection-folder status, manual re-indexing, permission reselection, live indexing progress, and confirmed deletion. Collection type is chosen during creation and remains read-only afterward. Deleting a collection removes only its Resn8 index, ratings, history, playlists, saved queues, session mapping, and persisted folder grant; source audio remains untouched. Deleting the final collection returns to onboarding. Multiple-root management remains post-MVP.

The active collection name is an app-bar selector and the app shell is the sole owner of system-bar insets. Nested page toolbars begin directly beneath it without reserving a second status-bar inset. `MUSIC` exposes Library, Folders, and Playlists; its Library tabs are Artists, Albums, and All Tracks. The Library sort sheet contains only context-valid sort fields plus direction and remains scrollable on compact or large-font layouts; top-level Availability and Exclude Disliked controls are not exposed. `FLAT` opens in Folders and omits Library/artist/album destinations while retaining collection-scoped playlists. Switching collections checkpoints and stops playback, clears transient browse/search/filter/selection state, and resolves the target collection's own last-queue pointer. A valid target queue is prepared at its saved item and position without autoplay and opens Now Playing; a collection without a restorable queue opens its profile home. The selected collection's pointer is mirrored into `UiSessionState.activeQueueId`; queue selection never falls back to update time.

Selectable folder, album, and all-track rows use checkboxes plus one fixed bottom action tray above the mini-player. The tray does not shift list content, reports concise file/folder counts, and supplies Add to Playlist and Clear. A row with a selection checkbox does not also expose a redundant single-item Add overflow action. List content includes sufficient bottom padding to remain reachable behind the tray.

### 3.2 Player and Queue

The now-playing surface is non-scrollable and always exposes title, profile-appropriate artist/album when available, elapsed/duration position, seek control, Previous, Play/Pause, Next, Like, Dislike, current score, and Add to Playlist. Controls claim layout space before artwork and retain at least 48dp targets; artwork uses a stable placeholder, shrinks within the remaining portrait or landscape region, and may be omitted when the available square would be smaller than 72dp. Audio Files use a compact two-line title and never show artist or album. Playlist-origin playback exposes a single-line `Playlist: <name>` app-bar action opposite the collection selector; it opens that exact playlist and reveals the current membership. Now Playing does not expose a separate View Queue action, though saved-queue playback and the compatibility Queue route remain intact.

Add to Playlist must remain available through a visible button or overflow action. Double-tap on unused space or a two-finger tap may be offered later as an optional shortcut, never as the only path because it is difficult to discover and use with accessibility services.

Playback continues when the activity is backgrounded or the screen locks. The media session supplies metadata and controls to the system notification, lock screen, Bluetooth/headset keys, and other authorized controllers. Resn8 handles audio focus, pauses on noisy-output events such as headphone disconnection, and reports playback errors without losing the queue.

### 3.3 Playlist Management

- Users can create, rename, and delete playlists; deletion requires confirmation and never deletes source files.
- The reusable playlist selector lists playlists containing every selected file first, then the remaining playlists. Each row uses checked, unchecked, or mixed state for bulk selection.
- Checking adds missing unique memberships; unchecking removes present memberships. The selector stays open until dismissed and reports partial failures.
- A playlist view supports search, removal, drag reorder, Move to Top, and Move to Bottom. Manual order is persisted with collision-free positions and compacted when needed.
- Starting playback from a playlist creates a saved manual queue snapshot so later playlist edits do not unexpectedly reorder the active queue.
- When the active queue originated from the viewed playlist, Playlist Detail preserves one-based manual position numbers, visibly and accessibly marks the current media row even while paused, and offers an explicit jump-to-current action for long lists. The action resolves against live playlist membership rather than the available-only queue index, clears a search that hides the target, and never marks matching media when playback came from another source context.

### 3.4 Restoration

Resn8 checkpoints the active saved queue, index, media ID, position, play/pause intent, playback speed, repeat state, and UI context. On relaunch it restores the same screen and queue and seeks to the saved position. Playback does not start audibly merely because the app was opened; the last item is ready in a paused state unless Android's explicit media-resumption path requested playback.

`Resn8MediaService`, as the sole player owner, is authoritative for playback checkpoints. Position is saved periodically while playing and immediately on pause, completed seek, item transition, task/background lifecycle events, and service shutdown. Writes are serialized and coalesced so a delayed older checkpoint cannot overwrite newer state. A checkpoint retains the stable queue-item ID and playback occurrence ID separately, together with the occurrence's accumulated active-listening duration and qualification state; restoring controller or Activity state does not create a new traversal occurrence or discard already accumulated listening.

Cold-start restoration waits for persisted session state before choosing a destination or installing media. It resolves the active queue only from `UiSessionState.activeQueueId`, rebuilds the stored explicit queue without minting replacement queue-item IDs, validates the saved index/media pair, bounds the seek position to the known duration when possible, and applies repeat/speed state before preparation. A newly selected queue always supersedes an in-flight restoration attempt.

Each collection also stores its own nullable last-queue pointer. Starting a queue updates that collection pointer and the selected collection's global active pointer. Collection switching checkpoints the outgoing queue before changing global session ownership, validates the incoming pointer against collection identity and non-empty occurrence data, and restores it paused. Missing or structurally invalid pointers are cleared without deleting unrelated saved data; unavailable items continue through the normal recovery path.

UI restoration persists typed destination state rather than an opaque back stack or display strings. It restores the last meaningful library surface, folder, artist, album, playlist, queue, or Now Playing destination together with the applicable collection, search, filter, and sort values. Transient dialogs, sheets, selection mode, and onboarding progress are not restored. If a destination no longer exists, Resn8 falls back deterministically through its valid parent and updates persisted session state. `MUSIC` ultimately falls back to Library; `FLAT` falls back to its collection-name root in Folders and never restores Library, artist, or album UI.

If the current item or source is unavailable, Resn8 keeps the queue and history context, identifies whether permission, storage, media, or saved state is missing, and offers reselect, retry, Settings, or skip-to-next-available actions as applicable. Merely detecting an unavailable item does not destructively rewrite the saved queue.

## 4. Architecture and Quality Requirements

### 4.1 Technical Architecture

- Kotlin and Jetpack Compose with unidirectional UI state, ViewModels, coroutines, and Flow.
- A layered package structure separating UI, playback, domain models/use cases, database repositories, and storage/indexing adapters.
- AndroidX Media3 ExoPlayer hosted by a `MediaSessionService`; the service also owns authoritative active-listening observation, while Compose connects through a `MediaController` rather than owning the player.
- Room for relational local persistence and migrations. Data access occurs through repositories so queue algorithms and view models can be unit tested without Android storage.
- Storage Access Framework content URIs with persisted read grants. Enumeration and metadata extraction run outside the main thread.
- Navigation destinations are typed and restorable; large object graphs and URI permission state are never passed directly through route strings.
- Dependency versions are centralized in the existing version catalog and pinned to mutually compatible stable releases during implementation.

### 4.2 Performance and Reliability

- Library queries are paged or lazily streamed and remain responsive for at least 25,000 indexed files and deeply nested folders.
- No file I/O, metadata extraction, database scan, or queue generation blocks the main thread.
- Batch scans are transactional at safe boundaries, resumable/retryable, and idempotent.
- Playback state is owned by one service/session; configuration changes must not create a second player.
- Queue generation for 25,000 eligible files completes without quadratic grouping/sorting behavior.
- Database migrations preserve listening statistics, ratings, playlists, and saved queues; destructive fallback is not used in production.

### 4.3 Accessibility and Layout

- Controls have meaningful content descriptions, minimum touch targets, logical focus order, and do not depend on color or gesture alone.
- Player and core browsers work in portrait and landscape using adaptive Compose layouts; tablet-specific optimization may follow MVP.
- Dynamic text does not obscure playback controls at supported font scales.

### 4.4 Error and Empty States

The UI distinguishes an empty folder, no matching filter results, revoked permission, unavailable removable storage, unsupported/corrupt audio, failed metadata extraction, playback failure, and an interrupted scan. Recoverable errors offer a direct retry, re-index, permission-reselection, or skip action. Corrupt individual files do not fail the entire scan.

## 5. Acceptance Criteria

The MVP is complete when all of the following are demonstrated on an API 34+ device/emulator and, for removable-storage behavior, a suitable physical device or test provider:

- Every collection folder retains access across app and device restart and indexes supported MP3/audio files without broad storage permission.
- Multiple normalized-unique Music and Audio Files collections can be created, renamed, switched, re-indexed, permission-repaired, and confirmation-deleted independently without cross-collection media or playlist leakage or modification of source audio.
- A filename-only `FLAT` MP3 is browsable and playable without invented artist/album values or a Library tab.
- Embedded MP3 tags win over path/filename fallbacks; untagged `Artist/Album/01 - Song.mp3` appears in the correct hierarchy and order.
- Re-index adds new files, refreshes changed metadata, marks missing files unavailable, and preserves ratings, play counts, history, and playlist membership.
- Artist, album, folder, and all-track browsing return correct filtered results for a representative library.
- Audio plays through Media3 in foreground/background, responds to notification and hardware controls, handles audio focus/headphone removal, and has only one active player.
- Like/Dislike updates are durable and atomic. Scores can cross zero in either direction.
- A play increments exactly once per playback traversal occurrence at the meaningful-listen threshold, cannot be earned by seeking, pause/buffer/interruption time, or wall-clock changes, and remains correct during background playback and UI/controller recreation. Automatic and final-item natural completion use the same occurrence-correct atomic commit.
- Manual playlists enforce unique membership, preserve user order, support single/bulk/folder addition, remain collection-scoped, and survive restart.
- Folder and album Select All resolve the complete available-only database set rather than only loaded pages; folder Select All excludes subfolders and their descendants.
- Killing and reopening the app restores the explicit queue, item, position, and screen in a non-autoplaying state; explicit Android media resumption remains functional.
- Switching repeatedly between collections restores each collection's own last queue, item, and position in a paused state; a never-played collection opens its profile home.
- Core UI tests cover first run, indexing states, collection switching, profile-aware browsing, library drill-down, player controls, playlist selector mixed state, Select All, and recovery from unavailable media.

## 6. Post-MVP Roadmap

1. Multiple roots per collection and user-facing `CONTEXTUAL` category-folder creation/browsing.
2. Full smart-playlist editor with saved dynamic rule definitions and compound filters.
3. Scheduled re-indexing with charging/storage constraints and change summaries.
4. Playback speed presets and per-collection/per-file speed memory for long-form audio.
5. Raycast/Alfred-style global search across metadata, folders, playlists, and history.
6. Smart randomized queue generation from immutable scoped snapshots.
7. Rating/history maintenance, including confirmed move/delete workflows for disliked files.
8. Export/import backup for playlists, ratings, history, and settings without bundling source audio.
9. Extend the existing player artwork seam with cached album artwork throughout library, album, artist, track, queue, and playlist surfaces when the current index exposes a usable artwork reference, with a stable placeholder when it does not.
