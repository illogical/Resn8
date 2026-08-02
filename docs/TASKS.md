# Resn8 Implementation Tasks

This backlog is ordered by dependency and user value. Complete tasks top-to-bottom within a milestone unless a task explicitly states otherwise. `P0` blocks the first usable release; `P1` completes the MVP experience; `P2` follows the MVP.

## Milestone 0 — Establish the Foundation (P0)

- [x] **T001 — Pin architecture dependencies.** Add stable compatible versions for Media3, Room, KSP, Navigation Compose, lifecycle/ViewModel Compose, coroutines, and test utilities to the version catalog. Confirm `assembleDebug`, unit tests, and instrumentation test compilation.
- [x] **T002 — Create the application layers.** Establish UI/navigation, domain, data/database, storage/indexing, and playback packages; wire repository interfaces through a small dependency container so tests can substitute fakes.
- [x] **T003 — Define domain contracts.** Add collection profiles, media metadata, filter/sort definitions, rating and meaningful-play rules, queue-generation modes, saved queue state, scan progress/results, and repository interfaces matching the specification.
- [x] **T004 — Add navigation and app shell.** Replace the starter greeting with typed destinations for onboarding, library, folders, playlists, playlist detail, queue, and now playing; include persistent mini-player placement and restorable route state.
- [x] **T005 — Establish test fixtures.** Add tagged and untagged short audio fixtures, corrupt/unsupported samples, nested fake document trees, and deterministic fake clocks/random sources without committing copyrighted media.

**Exit:** The app builds, navigates between placeholder destinations, and domain behavior can be tested without Android framework dependencies.

## Milestone 1 — Persist the Library Model (P0)

- [ ] **T006 — Implement the Room schema.** Create entities, relations, converters, indexes, DAOs, and the initial migration for collections, root sources, folders, media files/statistics, playback history, playlists/items, saved queues/items, and UI session state.
- [ ] **T007 — Implement repository transactions.** Add atomic APIs for scan upserts, availability changes, signed-score updates, meaningful-play commits, playlist membership/order, queue snapshots, and restoration checkpoints.
- [ ] **T008 — Test persistence invariants.** Verify unique playlist membership, non-negative play counts, signed scores crossing zero, cascading behavior, unavailable-file retention, stable playlist ordering, queue item ordering, and migration export/schema checks.

**Exit:** An in-memory and on-device database can represent every MVP state without destructive migrations.

## Milestone 2 — Select and Index a Music Folder (P0)

- [ ] **T009 — Implement folder onboarding.** Launch `ACTION_OPEN_DOCUMENT_TREE`, take persistent read permission, create the default `MUSIC` collection/root, and handle cancellation or failed grants.
- [ ] **T010 — Build recursive enumeration.** Traverse document-provider children off the main thread, admit supported audio only, stream bounded batches, expose progress/cancellation, and tolerate inaccessible/corrupt individual documents.
- [ ] **T011 — Extract and normalize metadata.** Read duration, MIME type, embedded MP3/music tags, artwork reference, and source facts; apply tag → music path/filename → cleaned filename precedence without inventing stored unknown values.
- [ ] **T012 — Implement filename/path fallback parsing.** Support `Artist/Album` inference and common disc/track prefixes; record which source supplied each result and summarize unrecognized naming patterns for sample-library analysis.
- [ ] **T013 — Implement idempotent re-indexing.** Match provider ID/URI then relative path, apply unique conservative signature recovery, preserve user data, mark missing files unavailable, restore returned files, and publish an atomic scan result.
- [ ] **T014 — Build indexing UI states.** Show first-run explanation, collection naming, progress, cancel/retry, final counts, empty folders, permission loss, removable-storage absence, and corrupt/unsupported-file summaries.
- [ ] **T015 — Test real provider behavior.** Cover internal shared storage and a removable/document-provider equivalent; verify persisted access and re-index behavior across process/device restart.

**Exit:** A user can select one nested music root, restart the app, inspect a durable indexed library, and manually re-index it safely.

## Milestone 3 — Browse the Indexed Library (P0)

- [ ] **T016 — Implement indexed library queries.** Provide reactive/paged artist, album, all-track, and folder-tree queries with availability, filter, search-text, and deterministic sort parameters.
- [ ] **T017 — Build Artist and Album views.** Drill from artists to their albums/tracks and from albums to tracks; order by disc/track with title/filename fallbacks and clear unknown-metadata grouping.
- [ ] **T018 — Build the folder browser.** Mirror indexed relative folders, show indexed/unavailable files, support file/folder multi-selection, and resolve folder selections to unique descendant media IDs.
- [ ] **T019 — Add sort/filter controls.** Support artist, album, title/filename, track, most/least played, unplayed, most/least recent, most liked, and filter-out-disliked where appropriate.
- [ ] **T020 — Verify large-library behavior.** Benchmark seeded datasets up to 25,000 media rows; remove main-thread I/O and avoid loading artwork/full lists eagerly.

**Exit:** Users can efficiently find and select music by metadata or original folder structure.

## Milestone 4 — Deliver Reliable Playback (P0)

- [ ] **T021 — Create the playback service.** Host one ExoPlayer and MediaSession in a `MediaSessionService`; declare foreground playback permissions/service and supply system-visible metadata.
- [ ] **T022 — Connect UI through MediaController.** Expose observable player/queue state and commands without constructing a player in the activity or ViewModels.
- [ ] **T023 — Build queue creation from library contexts.** Starting a track from an artist, album, folder, all-tracks result, or playlist saves an explicit ordered queue snapshot and begins at the selected item.
- [ ] **T024 — Build now-playing and mini-player UI.** Add artwork fallback, metadata, seek position, Previous, Play/Pause, Next, Like, Dislike, numeric score, queue, and visible Add to Playlist actions.
- [ ] **T025 — Integrate Android playback behavior.** Support background/lock-screen notification controls, audio focus, Bluetooth/headset media keys, pause-on-headphone-disconnect, and clear error/skip behavior.
- [ ] **T026 — Test playback lifecycle.** Verify activity recreation, backgrounding, screen lock, task removal, service recreation, audio focus changes, unplug events, unsupported/corrupt items, and single-player ownership.

**Exit:** Local audio and its current queue play reliably through the app and Android system surfaces.

## Milestone 5 — Track Ratings and Meaningful Plays (P0)

- [ ] **T027 — Implement signed rating actions.** Make Like an atomic `+1` and Dislike an atomic `-1`; update player/library state immediately and persist across restart.
- [ ] **T028 — Implement listening accumulation.** Track active listened duration per queue occurrence, excluding pause/buffer/interruption time and seek jumps.
- [ ] **T029 — Commit meaningful plays.** At `min(50% duration, 4 minutes)` or natural completion, atomically write one history result, increment play count, and set last-played time; handle unknown durations.
- [ ] **T030 — Test threshold edge cases.** Cover seek past threshold, seek backward, pause/resume, repeat/new occurrence, short files, unknown duration, completion before threshold, process interruption, and concurrent UI/service updates.

**Exit:** Ratings and listening statistics are trustworthy inputs for sorting and generation.

## Milestone 6 — Implement Manual Playlists (P1)

- [ ] **T031 — Build playlist management.** Create, rename, list, and confirmation-delete playlists within the collection; never alter source files.
- [ ] **T032 — Build the reusable playlist selector.** Put playlists containing all selected files first; render checked/unchecked/mixed states; keep the selector open for multiple atomic add/remove operations.
- [ ] **T033 — Connect selection entry points.** Open the same selector from now playing, a single library row, multi-selected files, and selected folder descendants.
- [ ] **T034 — Build playlist detail and search.** Display membership in manual order, filter by text for inspection, remove items, and preserve the underlying order while filtered.
- [ ] **T035 — Implement reordering.** Support accessible drag reorder plus Move to Top/Bottom, with stable collision-free persisted positions and background compaction.
- [ ] **T036 — Test playlist consistency.** Cover duplicate additions, mixed bulk membership, nested-folder expansion, unavailable files, reorder persistence, edits while a playlist-derived queue is active, and delete confirmation.

**Exit:** Users can assemble and maintain durable ordered playlists from every specified context.

## Milestone 7 — Generate Smart Randomized Queues (P1)

- [ ] **T037 — Implement the eligibility pipeline.** Snapshot the active collection/folder/artist/album/search filters, keep available media, and exclude `likeScore < 0` by default.
- [ ] **T038 — Implement seeded tie shuffling.** Provide linear grouping plus ordered group traversal for random eligible, unplayed, least played, most played, most liked, and recent modes using an injectable random source.
- [ ] **T039 — Enforce most-liked semantics.** Emit positive score groups in descending order, randomize equal-score peers, append a randomized neutral group, and exclude negatives.
- [ ] **T040 — Persist generated queues.** Save rule, normalized filter snapshot, seed, and explicit media order; do not mutate an active queue when metadata/statistics change or a scan finds new files.
- [ ] **T041 — Build generation UI.** Let users generate from the current result set, preview mode/filter/exclusion and item count, handle zero eligible items, and regenerate explicitly.
- [ ] **T042 — Test algorithms exhaustively.** Cover the normative `3,3,1,0,0,-1` example, randomized ties, seeded reproducibility, non-identical seeds, empty/single inputs, all-disliked sets, unavailable files, filter scoping, and large-library performance.

**Exit:** Every specified smart mode creates a correct, explainable, durable queue from exactly the visible scope.

## Milestone 8 — Restore Context and Finish the MVP (P1)

- [ ] **T043 — Add queue and position checkpoints.** Save index/media ID/position periodically and on pause, transition, backgrounding, task removal, and service shutdown without excessive database writes.
- [ ] **T044 — Restore playback safely.** Rebuild the exact explicit queue and seek to position after process death/restart; default to paused on normal launch and support Android's explicit media-resumption request.
- [ ] **T045 — Restore browsing context.** Return to the last meaningful route with collection, folder, artist, album, playlist, filter, and sort state; fall back safely when a target no longer exists.
- [ ] **T046 — Handle unavailable restore targets.** Explain missing permission/storage/media and offer reselect, retry, or skip-to-next without discarding queue/history state.
- [ ] **T047 — Complete accessibility/adaptive layout QA.** Validate screen reader labels, focus order, touch targets, font scaling, contrast, keyboard/switch access, portrait, and landscape. Ensure no core action requires a hidden gesture.
- [ ] **T048 — Run MVP acceptance suite.** Execute unit, Room, Compose, instrumentation, service lifecycle, 25k-library, internal-storage, and removable-storage tests from `SPECIFICATION.md`; record any device-only limitations.
- [ ] **T049 — Prepare release hygiene.** Confirm no sensitive paths or user media enter logs/backups, define database backup behavior, enable release optimization deliberately, and produce a signed-test release checklist.

**Exit:** Every MVP acceptance criterion passes and the app can be used daily without losing its library, queue, ratings, playlists, or playback position.

## Post-MVP Backlog (P2)

- [ ] **T050 — Add multiple collections and multiple roots.** Include lifecycle, permission, merge, and per-source re-index controls.
- [ ] **T051 — Expose contextual and flat collection profiles.** Reuse the shared schema with category-path and filename-oriented presentation.
- [ ] **T052 — Add saved dynamic smart playlists.** Support editable compound rules distinct from immutable generated queue instances.
- [ ] **T053 — Add scheduled re-indexing.** Use durable background work with charging/storage constraints and user-visible change summaries.
- [ ] **T054 — Add playback speed.** Provide presets and optional per-file/collection memory for long-form audio.
- [ ] **T055 — Add global command search.** Search media, folders, artists, albums, playlists, history, and actions from one fast interface.
- [ ] **T056 — Add safe disliked-file maintenance.** Preview and confirm move/delete operations through providers that grant write access; keep database/history reconciliation explicit.
- [ ] **T057 — Add metadata/history backup and restore.** Export versioned app data without source audio and document identity/remapping behavior.

