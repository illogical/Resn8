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

- [x] **T006 — Implement the Room schema.** Reconcile Milestone 0 domain contracts, then create the version-1 entities, relations, converters, indexes, focused DAOs, and exported schema for collections, roots, folders, isolated scan staging, media identity/metadata/statistics, playback occurrences, playlists/items, saved queues/items, and UI session state. Include first-indexed time, metadata provenance, separate queue-item and current playback-occurrence identities, complete restoration fields, and restrictive source/media deletion semantics.
- [x] **T007 — Implement repository transactions.** Preserve test fakes while adding Room-backed production repositories and atomic APIs for bounded scan staging/publication, availability changes, signed-score updates, idempotent meaningful-play commits, collision-free playlist membership/order, explicit queue snapshot replacement, playback checkpoints, and UI-session restoration state.
- [x] **T008 — Test persistence invariants.** Verify identity uniqueness, first-indexed retention, enum/JSON compatibility, atomic scan publication, unique playlist membership, non-negative statistics, concurrent signed-score updates, exactly-once meaningful plays, restrictive/cascading behavior, unavailable-file retention, stable playlist ranks, atomic queue ordering/checkpoints, schema export, close/reopen, and available on-device process restoration.

**Exit:** An in-memory and on-device database can represent every MVP state without destructive migrations.

## Milestone 2 — Select and Index a Music Folder (P0)

- [x] **T009 — Implement folder onboarding.** Launch `ACTION_OPEN_DOCUMENT_TREE`, take persistent read permission, create the default `MUSIC` collection/root, and handle cancellation or failed grants.
- [x] **T010 — Build recursive enumeration.** Traverse document-provider children off the main thread, admit supported audio only, stream bounded batches, expose progress/cancellation, and tolerate inaccessible/corrupt individual documents.
- [x] **T011 — Extract and normalize metadata.** Read duration, MIME type, embedded MP3/music tags, artwork reference, and source facts; apply tag → music path/filename → cleaned filename precedence without inventing stored unknown values.
- [x] **T012 — Implement filename/path fallback parsing.** Support `Artist/Album` inference and common disc/track prefixes; record which source supplied each result and summarize unrecognized naming patterns for sample-library analysis.
- [x] **T013 — Implement idempotent re-indexing.** Stage bounded scan batches, match provider ID/URI then relative path, apply unique conservative signature recovery, preserve first-indexed time and all user data, mark missing files unavailable, restore returned files, and publish the resolved scan as one atomic canonical snapshot.
- [x] **T014 — Build indexing UI states.** Show first-run explanation, collection naming, progress, cancel/retry, final counts, empty folders, permission loss, removable-storage absence, and corrupt/unsupported-file summaries.
- [x] **T015 — Test real provider behavior.** Cover internal shared storage and a removable/document-provider equivalent; verify persisted access and re-index behavior across process/device restart.

**Exit:** A user can select one nested music root, restart the app, inspect a durable indexed library, and manually re-index it safely.

## Milestone 3 — Browse the Indexed Library (P0)

- [x] **T016 — Implement indexed library queries.** Provide reactive/paged artist, album, all-track, and folder-tree queries with availability, filter, search-text, and deterministic sort parameters.
- [x] **T017 — Build Artist and Album views.** Drill from artists to their albums/tracks and from albums to tracks; order by disc/track with title/filename fallbacks and clear unknown-metadata grouping.
- [x] **T018 — Build the folder browser.** Mirror indexed relative folders, show indexed/unavailable files, support file/folder multi-selection, and resolve folder selections to unique descendant media IDs.
- [x] **T019 — Add sort/filter controls.** Support artist, album, title/filename, track, recently added/indexed, most/least played, unplayed, most/least recent, most liked, and filter-out-disliked where appropriate.
- [x] **T020 — Verify large-library behavior.** Benchmark seeded datasets up to 25,000 media rows; remove main-thread I/O and avoid loading artwork/full lists eagerly.

**Exit:** Users can efficiently find and select music by metadata or original folder structure.

## Milestone 4 — Deliver Reliable Playback (P0)

- [x] **T021 — Complete the existing playback service.** Finish the single ExoPlayer/MediaSession owner in `Resn8MediaService`; retain the required foreground playback declarations, use Media3's session-backed notification/foreground lifecycle, and supply system-visible metadata.
- [x] **T022 — Connect UI through MediaController.** Add an application-scoped controller connection with observable player/queue state, available-command gating, bounded progress polling, retryable connection state, and deterministic listener/future cleanup; never construct a player in the Activity or ViewModels.
- [x] **T023 — Build queue creation from library contexts.** Starting a track from an artist, album, folder, all-tracks result, or persisted playlist must snapshot the deterministic available-only order, assign stable queue-item IDs, atomically persist the explicit queue before playback, and begin at the validated selected item. Playback traversal occurrence IDs remain distinct from queue-item IDs.
- [x] **T024 — Build now-playing, queue, and mini-player UI.** Replace the existing placeholders with live artwork fallback, metadata, seek position, Previous, Play/Pause, Next, numeric score, and queue-item-aware controls. Keep visible, accessible Like/Dislike and Add to Playlist seams while M5 owns rating persistence and M6 owns playlist workflows.
- [x] **T025 — Integrate Android playback behavior.** Support background/lock-screen session controls, audio focus, Bluetooth/headset media keys, pause-on-headphone-disconnect, and bounded error/skip behavior that reports failures, retains the saved queue, and cannot loop forever when every remaining item fails.
- [x] **T026 — Test live playback lifecycle.** Verify Activity/controller recreation, backgrounding, screen lock, playing/paused task removal, service cleanup/recreation, audio focus, unplug events, hardware/system controls, unsupported/corrupt/unavailable items, and single-player ownership with API 34+ device coverage. Exact process-death queue/position restoration remains T043-T046.

**Exit:** Local audio plays reliably through the app and Android system surfaces from a persisted explicit queue, with live lifecycle/controller resilience and bounded failure handling. Periodic checkpoints, exact relaunch restoration, and Android playback resumption remain Milestone 8.

## Milestone 5 — Track Ratings and Meaningful Plays (P0)

- [x] **T027 — Complete atomic signed rating actions.** Make Like an atomic `+1` and Dislike an atomic `-1`; return or reactively expose the authoritative updated score to Now Playing, mini-player, and paged library state. Define invalid-delta and missing-media behavior, prevent lost updates under rapid/concurrent taps, avoid playback/queue mutation, and verify restart durability.
- [x] **T028 — Implement service-owned listening accumulation.** In `Resn8MediaService`, track active listened duration with an injected monotonic clock and a playback traversal occurrence ID distinct from `queueItemId`. Initialize and finalize occurrences correctly across initial entry, next/previous, direct jump, repeat/replay, background playback, and automatic transitions while excluding pause, buffer, audio-focus interruption, errors, and seek jumps; preserve the occurrence across UI/controller recreation.
- [x] **T029 — Qualify and commit meaningful plays.** At `min(50% of known duration, 4 minutes)` or after four active minutes for unknown duration, atomically write one history result, increment play count, and set last-played time. Recognize both automatic item transitions and final `STATE_ENDED` as natural completion, require positive active listening for completion qualification, and use the traversal occurrence ID for exactly-once idempotency.
- [x] **T030 — Verify rating and meaningful-play reliability.** Add deterministic tracker, repository, UI-state, and Media3 transition/lifecycle tests covering threshold boundaries, seeks, pause/resume, buffering/focus interruption, wall-clock changes, duplicate queue media, repeat/new occurrences, short and unknown durations, automatic/final completion, background playback, UI recreation, service/process interruption, rollback, and concurrent UI/service commits; complete API 34+ manual checks.

**Exit:** Ratings and listening statistics remain atomic, occurrence-correct, and trustworthy during foreground/background playback and UI/controller recreation, making them safe inputs for sorting and generation. Full process-death queue/occurrence restoration remains Milestone 8.

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

- [ ] **T043 — Add queue and position checkpoints.** Save queue ID, current playback occurrence ID, index/media ID, position, play intent, playback speed, and repeat state periodically and on pause, transition, backgrounding, task removal, and service shutdown without excessive database writes.
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
