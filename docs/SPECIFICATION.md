# Resn8 Software Specification

## 1. Product Summary

Resn8 is an offline-first Android audio player for audio files stored in user-selected folders on internal shared storage or removable SD cards. Its distinguishing feature is durable listening and rating data that can generate queues which surface unheard or least-played files, prioritize liked files, and exclude disliked files.

The first usable release focuses on one organized music collection under one user-selected root folder. The data model must also support later contextual-folder collections (for example, `Podcasts/AI`) and flat folders without requiring a database redesign.

### Goals

- Select a folder through Android's system picker and recursively index supported audio files.
- Browse an organized music library by artist, album, track, and source folder.
- Play audio reliably in the foreground and background with Android system controls.
- Track meaningful plays, last-played time, current position, and a signed like score.
- Create ordered manual playlists and add one file, many files, or a folder's descendants.
- Generate and persist randomized queues from the current collection or filtered result set.
- Restore the exact queue, item, position, and browsing context after process death or restart.
- Keep all library data local; Resn8 does not upload audio, metadata, or listening history.

### MVP Boundaries

The MVP includes one `MUSIC` collection with one root folder, recursive manual re-indexing, library browsing, background playback, rating, manual playlists, and playback-state restoration. The schema and domain interfaces support multiple roots and collection profiles, but their management UI is post-MVP.

Scheduled indexing, advanced search, playback-speed control, multiple collections, generic contextual/flat collection UI, moving or deleting disliked source files, tag editing, cloud playback, casting, lyrics, equalization, and Android Auto-specific browsing are post-MVP.

Images and videos mentioned in the brainstorm are out of scope. Resn8 indexes playable audio only.

## 2. Core Concepts and Rules

### 2.1 Collection and Source Access

- A **Collection** is a logical audio library with a name and profile: `MUSIC`, `CONTEXTUAL`, or `FLAT`.
- A **Root Source** is a folder selected with `ACTION_OPEN_DOCUMENT_TREE`. Resn8 takes and stores persistent read permission for its tree URI.
- The MVP allows one collection and one root source. APIs and tables use collection/source identifiers so later releases can add more without migration of the conceptual model.
- Resn8 never requires broad storage permission and never modifies source audio during normal indexing or playback.
- If permission is revoked or an SD card is unavailable, the source and its affected files are shown as unavailable. Statistics and playlist membership are retained.

### 2.2 Shared Media Schema

All audio types use one `MediaFile` entity. Common fields are required; music-specific metadata is nullable. This avoids parallel schemas while allowing each collection profile to present a different hierarchy.

| Entity | Required fields and behavior |
| --- | --- |
| `Collection` | Stable ID, name, profile, created/updated timestamps |
| `RootSource` | Stable ID, collection ID, persisted tree URI, display name, availability, last scan status/timestamps |
| `FolderNode` | Stable ID, source ID, parent ID, relative path, display name; represents the indexed hierarchy |
| `MediaFile` | Stable ID, source ID, folder ID, document URI/ID, relative path, filename, display title, MIME type, size, duration, modified time, availability, metadata scan status |
| Music metadata | Nullable title, artist, album artist, album, disc number, track number, year, genre, artwork reference |
| Listening statistics | `playCount >= 0`, nullable `lastPlayedAt`, signed `likeScore` defaulting to `0` |
| `Playlist` | Stable ID, collection ID, unique name within collection, created/updated timestamps |
| `PlaylistItem` | Playlist ID, media ID, unique membership, stable manual position, added timestamp |
| `PlaybackHistory` | Media ID, session/queue occurrence ID, start/end times, accumulated listened duration, completion/counting result |
| `SavedQueue` | Queue ID, collection ID, kind, optional generation rule/filter/seed, explicit ordered media IDs, current index, position, playback state, timestamps |
| `UiSessionState` | Last route plus selected collection, folder, artist, album, playlist, and active filter/sort identifiers |

Room is the source of truth for indexed metadata, relationships, statistics, playlists, saved queues, and session state. Source audio remains addressed through content URIs. Foreign keys and indexes cover collection/source/folder membership, artist, album, track/disc number, play count, last played, like score, playlist position, and availability.

### 2.3 Metadata Resolution

During indexing, Resn8 reads supported embedded metadata, including MP3 ID3 tags when present. Display values use this precedence:

1. Valid embedded title, artist/album artist, album, disc, and track fields.
2. For a `MUSIC` collection, relative folder structure interpreted as `Artist/Album/...` and a leading filename track number where the tag is absent.
3. A cleaned filename without its extension.
4. `Unknown Artist` and `Unknown Album` only at presentation boundaries; missing values remain null in storage.

The parser accepts common track prefixes such as `01 Title`, `01 - Title`, and `1-01 Title`. It must not overwrite valid embedded tags. A scan summary records counts of tag-derived, path-derived, unrecognized, unreadable, and unsupported files so real sample libraries can inform later parser improvements.

For `CONTEXTUAL` collections, relative folders are the category hierarchy and music fields are optional. For `FLAT` collections, files use display title/filename without invented artist or album values.

### 2.4 Re-indexing and File Identity

- A scan recursively enumerates the selected tree, filters for decoder-supported audio MIME types/extensions, extracts metadata off the main thread, and upserts results in bounded batches.
- Existing records are matched by provider document ID/URI first and relative path second. A conservative size/duration/modified-time signature may recover a renamed item only when the match is unique.
- New files are inserted with neutral statistics. Changed files refresh source and extracted metadata without resetting ratings, history, or playlist membership.
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

`playCount` increments at most once for each queue occurrence after accumulated active listening reaches:

`min(50% of known duration, 4 minutes)`

- Time advances only while audio is actually playing. Paused, buffering, and audio-focus-interrupted time does not count.
- Seeking beyond the threshold does not count skipped time; only accumulated listened time qualifies.
- Pause/resume and seeking within the same occurrence do not create another play.
- Replaying through a new queue occurrence can create another play.
- When duration is unknown, four minutes or natural completion qualifies. Natural completion qualifies even for a shorter or partially heard file.
- On qualification, `playCount`, `lastPlayedAt`, and the history record are committed atomically. Position is checkpointed separately and more frequently.

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

Manual library sorts support artist, album, disc/track, filename/title, most/least played, unplayed, most/least recently played, and most liked. Unlike smart queues, normal sorts are deterministic, using normalized title and stable media ID as final tie-breakers.

## 3. User Experience

### 3.1 First Run and Library

1. Explain that Resn8 is local-only and request a music root through the system folder picker.
2. Ask for a collection name, defaulting to the selected folder name, and start recursive indexing.
3. Show progress, discovered/unsupported/error counts, and a usable empty/error state.
4. Open the library with top-level Artist, Album, Folder, and All Tracks views.
5. Artist selection shows that artist's albums and tracks; album selection shows tracks ordered by disc and track number, falling back to title/filename.

The folder browser mirrors indexed relative paths, shows file availability, supports file/folder multi-selection, and expands selected folders to all currently indexed descendant audio for bulk playlist operations.

### 3.2 Player and Queue

The now-playing surface always exposes title, artist/album when available, elapsed/duration position, seek control, Previous, Play/Pause, Next, Like, Dislike, current score, queue access, and Add to Playlist. Album art is shown when available with a stable placeholder fallback.

Add to Playlist must remain available through a visible button or overflow action. Double-tap on unused space or a two-finger tap may be offered later as an optional shortcut, never as the only path because it is difficult to discover and use with accessibility services.

Playback continues when the activity is backgrounded or the screen locks. The media session supplies metadata and controls to the system notification, lock screen, Bluetooth/headset keys, and other authorized controllers. Resn8 handles audio focus, pauses on noisy-output events such as headphone disconnection, and reports playback errors without losing the queue.

### 3.3 Playlist Management

- Users can create, rename, and delete playlists; deletion requires confirmation and never deletes source files.
- The reusable playlist selector lists playlists containing every selected file first, then the remaining playlists. Each row uses checked, unchecked, or mixed state for bulk selection.
- Checking adds missing unique memberships; unchecking removes present memberships. The selector stays open until dismissed and reports partial failures.
- A playlist view supports search, removal, drag reorder, Move to Top, and Move to Bottom. Manual order is persisted with collision-free positions and compacted when needed.
- Starting playback from a playlist creates a saved manual queue snapshot so later playlist edits do not unexpectedly reorder the active queue.

### 3.4 Restoration

Resn8 checkpoints the active saved queue, index, media ID, position, play/pause intent, playback speed, repeat state, and UI context. On relaunch it restores the same screen and queue and seeks to the saved position. Playback does not start audibly merely because the app was opened; the last item is ready in a paused state unless Android's explicit media-resumption path requested playback.

Position is saved periodically while playing, on pause, item transition, task/background lifecycle events, and service shutdown. If the item is unavailable, Resn8 keeps the context, identifies the problem, and offers to skip to the next available item or reselect the source folder.

## 4. Architecture and Quality Requirements

### 4.1 Technical Architecture

- Kotlin and Jetpack Compose with unidirectional UI state, ViewModels, coroutines, and Flow.
- A layered package structure separating UI, playback, domain models/use cases, database repositories, and storage/indexing adapters.
- AndroidX Media3 ExoPlayer hosted by a `MediaSessionService`; Compose connects through a `MediaController` rather than owning the player.
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

- A selected root retains access across app and device restart and indexes nested MP3/audio files without broad storage permission.
- Embedded MP3 tags win over path/filename fallbacks; untagged `Artist/Album/01 - Song.mp3` appears in the correct hierarchy and order.
- Re-index adds new files, refreshes changed metadata, marks missing files unavailable, and preserves ratings, play counts, history, and playlist membership.
- Artist, album, folder, and all-track browsing return correct filtered results for a representative library.
- Audio plays through Media3 in foreground/background, responds to notification and hardware controls, handles audio focus/headphone removal, and has only one active player.
- Like/Dislike updates are durable and atomic. Scores can cross zero in either direction.
- A play increments exactly once per queue occurrence at the meaningful-listen threshold, cannot be earned by seeking alone, and persists across UI recreation.
- Manual playlists enforce unique membership, preserve user order, support single/bulk/folder addition, and survive restart.
- Smart-mode unit tests verify eligibility, dislike exclusion, primary ordering, randomized ties, neutral placement, seeded reproducibility, empty/single-item inputs, and unavailable items.
- The most-liked normative example produces grouped order `[3s randomized] -> [1s randomized] -> [0s randomized]`, excluding all negative scores.
- Killing and reopening the app restores the explicit queue, item, position, and screen in a non-autoplaying state; explicit Android media resumption remains functional.
- Core UI tests cover first run, indexing states, library drill-down, player controls, playlist selector mixed state, queue generation, and recovery from unavailable media.

## 6. Post-MVP Roadmap

1. Multiple collections and roots, plus `CONTEXTUAL` and `FLAT` collection creation/browsing.
2. Full smart-playlist editor with saved dynamic rule definitions and compound filters.
3. Scheduled re-indexing with charging/storage constraints and change summaries.
4. Playback speed presets and per-collection/per-file speed memory for long-form audio.
5. Raycast/Alfred-style global search across metadata, folders, playlists, and history.
6. Rating/history maintenance, including confirmed move/delete workflows for disliked files.
7. Export/import backup for playlists, ratings, history, and settings without bundling source audio.

