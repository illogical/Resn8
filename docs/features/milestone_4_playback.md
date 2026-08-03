# Milestone 4 Implementation Plan: Deliver Reliable Playback

## Objective

Implement T021-T026 from [TASKS.md](../TASKS.md) by completing the existing AndroidX Media3 playback foundation: one service-owned player/session, an application-scoped controller connection, explicit saved queues created from deterministic library or playlist contexts, functional transport/seek/queue UI, Android system integration, and live lifecycle reliability.

The result must satisfy the playback and queue requirements in [SPECIFICATION.md](../SPECIFICATION.md) while preserving the local-first principles in [README.md](../../README.md) and the player intent in [BRAINSTORM.md](../BRAINSTORM.md). The specification is normative when the brainstorm is exploratory.

Milestone 4 deliberately does not complete:

- durable Like/Dislike mutations or meaningful-play accounting, which belong to T027-T030 in Milestone 5;
- playlist creation, membership editing, reordering, or the reusable playlist selector, which belong to T031-T036 in Milestone 6; or
- periodic playback checkpoints, process-death restoration, browsing restoration, unavailable-target recovery, or Android playback resumption, which belong to T043-T046 in Milestone 7.

---

## Verified Baseline and Required Preflight

Before implementing playback, reconcile this plan with the checked-in baseline rather than creating parallel placeholders or speculative persistence:

| Area | Implemented baseline | Milestone 4 action |
| --- | --- | --- |
| Playback service | `Resn8MediaService` already extends `MediaSessionService` and creates a basic `ExoPlayer`/`MediaSession`. | Complete this service; do not add a renamed `Resn8PlaybackService`. |
| Manifest | The service, `mediaPlayback` foreground type, MediaSessionService intent action, and both required foreground-service permissions already exist. | Verify and retain them; do not add broad storage permission or require `POST_NOTIFICATIONS` for the exempt media-session notification. |
| Player UI | `NowPlayingScreen`, `MiniPlayer`, and `QueueScreen` already exist as placeholders and typed routes are registered. | Replace their placeholder state and callbacks; do not create duplicate screens or routes. |
| Queue model | `SavedQueueKind` contains `EXPLICIT` and `GENERATED`; `SavedQueueItem` contains stable `queueItemId` and `mediaId`. | Use `EXPLICIT`; do not invent `MANUAL_LIBRARY` or `MANUAL_PLAYLIST` enum values. Preserve occurrence identity. |
| Queue persistence | `QueueRepository.replaceQueueSnapshot()` atomically replaces the queue header/items and assigns unique item IDs. | Build queue creation on this transaction and resolve the authoritative queue through `UiSessionState.activeQueueId`. |
| Browsing | `LibraryQuery`, `LibraryFilterSnapshot`, `AvailabilityFilter`, and `snapshotVisibleMediaIds()` provide an exact deterministic visible-order snapshot. | Copy and normalize the originating query, force available-only eligibility, and preserve its order. |
| Media data | `MediaFile` retains content/file URI, availability, nullable metadata, artwork, duration, and rating/statistics fields. | Resolve queue media in batches off the main thread and build complete Media3 metadata with stable fallbacks. |
| Dependencies | Media3 is pinned to `1.5.1`; the app targets/compiles against the repository's existing SDK configuration. | Implement against the pinned API; do not upgrade Media3 as part of this milestone. |

The preflight must also correct the current meaning of “active queue.” `SavedQueueDao.getActiveQueueFlow()` currently chooses the most recently updated queue, while `UiSessionState.activeQueueId` is the durable authoritative selection. Add an ID-based queue observation/read contract and have playback observe the queue referenced by session state. Do not let later writes to an older queue silently make it active.

### Persistence baseline

Room schema version 2 already persists:

- queue ID, collection ID, `SavedQueueKind`, optional smart mode/filter/seed;
- current index, current media ID, current occurrence ID, position, play-when-ready intent, playback speed, repeat mode, and timestamps;
- ordered queue items keyed by queue ID and item index, with a unique stable `queueItemId` plus canonical media ID; and
- `UiSessionState.activeQueueId`.

No schema migration is expected for Milestone 4. If implementation proves a schema change necessary, increment the database version, export the schema, add a non-destructive migration and migration test, and preserve queues, playlists, ratings, and listening history. Never enable destructive fallback.

---

## Responsibility and Downstream Boundaries

| Milestone | Responsibility |
| --- | --- |
| Milestones 1-3 | Durable queue/session/library foundations; content URI and availability tracking; exact scoped, filtered, deterministic media snapshots. |
| Milestone 4 | Initial explicit queue persistence; live playback/session/controller state; transport, seek, queue and system controls; bounded active-playback error handling. |
| Milestone 5 | Atomic signed rating actions and one meaningful-play result per stable queue occurrence. |
| Milestone 6 | Playlist management and the reusable Add to Playlist workflow. |
| Milestone 7 | Periodic/final checkpoints, exact process-death restoration, non-autoplay launch behavior, route restoration, missing-source recovery, and Media3 playback resumption. |
| Milestone 8 | Correct startup readiness/route restoration integration and simplify index-completion feedback. |
| Milestone 9 | Generated queue rules, seeds, eligibility, and explicit generated snapshots. |

Milestone 4 must maintain the seams later milestones need:

- Each player timeline item represents one saved queue occurrence, even when the same media file appears more than once.
- The now-playing UI displays the current signed score and always contains accessible Like, Dislike, and Add to Playlist controls. Their composable/ViewModel contracts accept action handlers, but M5 and M6 provide the durable behavior. Until those handlers are available, controls must expose a clear disabled/unavailable semantic rather than silently doing nothing.
- A playlist queue is supported from existing repository data and seeded tests. M6 later makes playlist creation and editing fully user-accessible; editing a playlist must never mutate an already saved active queue.
- M4 persists the initial queue/current item before playback and exposes the live current item from MediaController. It does not claim periodic position/index persistence or exact relaunch restoration.
- Activity or controller recreation reconnects to a still-running service and reflects its current timeline. If the service is recreated, tests prove clean initialization/release and no duplicate player; M7 owns rebuilding a previous timeline from Room.

---

## Implementation Order

Implement in this dependency order:

1. Reconcile service, manifest, active-queue, batch media lookup, session-state, and placeholder UI contracts.
2. Complete `Resn8MediaService` and its MediaSession callbacks.
3. Add the application-scoped `PlaybackConnection` and observable `PlaybackUiState`.
4. Implement explicit queue creation and wire all-track, artist, album, folder, and persisted-playlist start actions.
5. Replace mini-player, now-playing, and queue placeholders with live state and commands.
6. Add bounded playback-failure handling and verify Android lifecycle/system behavior.

Each slice must compile and have focused tests before the next slice depends on it.

---

## Architecture and Shared Contracts

```text
Compose screens / ViewModels
        |
        | StateFlow<PlaybackUiState> and command methods
        v
Application-scoped PlaybackConnection
        |
        | MediaController / SessionToken
        v
Resn8MediaService
        |
        +-- one ExoPlayer
        +-- one MediaSession
        +-- MediaSession.Callback resolves requested queue occurrences
        +-- Player.Listener publishes bounded errors/transitions
        |
        v
Room repositories -> content/file URI -> Android storage provider
```

### 1. Player and controller ownership

- `Resn8MediaService` is the only owner of `ExoPlayer` and `MediaSession`.
- Activities, ViewModels, and composables never instantiate, retain, or directly release an `ExoPlayer`.
- `PlaybackConnection` is application-scoped through `AppContainer`. It owns the asynchronous `MediaController.Builder(...).buildAsync()` future, installs/removes one `Player.Listener`, and calls `MediaController.releaseFuture()` when the connection is disposed.
- Connection state distinguishes connecting, connected, disconnected, and failed/retryable. Commands are gated by connection plus Media3 available commands; unsupported commands do not crash or falsely update UI.
- Listener callbacks update discrete state. Because Media3 does not emit ordinary playback-progress callbacks, poll position/duration at a bounded interval only while a controller is connected and a loaded item is visible or playing. Cancel polling when no longer needed.

### 2. Playback UI state

Document one immutable `PlaybackUiState` containing at least:

- connection state and retryable connection error;
- active queue ID, current queue item ID/occurrence, canonical media ID, and timeline index;
- title, artist, album, artwork URI/fallback state, and signed score;
- elapsed position, duration/unknown-duration state, buffering/ready/ended state, and `isPlaying`;
- availability of play, pause, seek, previous, next, and direct queue-item commands;
- ordered queue rows and the highlighted live occurrence; and
- a consumable structured `PlaybackNotice` for missing/unavailable/corrupt items and command failures.

The connection exposes play, pause, toggle, seek, previous, next, direct queue selection, retry, and start-queue commands. It never reports optimistic playback state that contradicts the controller.

### 3. Queue-start requests

Use a typed request rather than loosely related context strings:

```kotlin
sealed interface QueueStartRequest {
    val startingMediaId: String

    data class Library(
        val query: LibraryQuery,
        override val startingMediaId: String,
    ) : QueueStartRequest

    data class Playlist(
        val playlistId: String,
        override val startingMediaId: String,
    ) : QueueStartRequest
}
```

Library requests use a normalized copy of the exact originating `LibraryQuery` with `filters.availability = AvailabilityFilter.AVAILABLE_ONLY`. This applies to All Tracks, artist, album, and current folder; retain artist/album known-versus-unknown keys, folder-descendant choice, search, sort, and other active filters.

Playlist requests load the playlist's persisted manual order, retain the playlist snapshot independently of later edits, and exclude currently unavailable items for playback eligibility. Seed playlist repository data in M4 tests; do not pull M6 management UI into this milestone.

For both request types:

1. Resolve the ordered eligible media IDs off the main thread.
2. Fail visibly when the context is empty/all-unavailable or the selected ID is absent/unavailable; do not silently start a different item.
3. Create a `SavedQueue(kind = SavedQueueKind.EXPLICIT)` whose initial index/media identify the selected item and whose position is zero.
4. Call `QueueRepository.replaceQueueSnapshot()`; use its returned items so the generated `queueItemId` values are authoritative.
5. Save `UiSessionState.activeQueueId`.
6. Only after both persistence operations succeed, hand the saved occurrences to `PlaybackConnection`, set the Media3 timeline/start index, prepare, and play.
7. On any persistence/materialization failure, do not begin playback; retain the previous live queue and show a structured error.

`replaceQueueSnapshot()` remains the atomic queue-header/item transaction. If saving session state fails afterward, an unreferenced saved queue may remain for later cleanup, but it must not play or replace the authoritative active queue.

### 4. Occurrence identity and media resolution

Add an order-preserving batch repository contract such as:

```kotlin
suspend fun getMediaFilesByIdsPreservingOrder(mediaIds: List<String>): List<MediaFile>
```

The Room implementation queries unique IDs in bounded chunks below SQLite bind limits, maps them by ID, then reconstructs the caller's order including duplicates. It reports any missing IDs instead of silently shortening or reordering the result. The fake implementation follows identical ordering/missing-ID semantics. Queue creation/materialization must remain linear or `O(n log n)` and must not perform one database query per item.

For each saved queue item:

- set Media3 `MediaItem.mediaId` to the stable `queueItemId`, not the canonical media-file ID;
- put the queue ID and canonical media-file ID into `MediaItem.RequestMetadata.extras` using shared constants such as `RESN8_QUEUE_ID` and `RESN8_MEDIA_FILE_ID`; and
- retain the occurrence media ID across metadata enrichment so M5 can count listening per occurrence.

`MediaSession.Callback.onAddMediaItems()` batch-resolves ID-only requested items to playable `MediaItem` values containing the indexed content/file URI, MIME type where useful, title fallback, artist, album, artwork URI, disc number, and track number. The callback performs repository work off the main thread and completes promptly. Invalid request metadata fails with a nonfatal session error rather than playing an unrelated file.

### 5. Android service and notification behavior

- Configure `ExoPlayer` with music `AudioAttributes`, using `setAudioAttributes(audioAttributes, true)` for Media3 audio-focus handling and `setHandleAudioBecomingNoisy(true)` for automatic pause when output is rerouted from headphones.
- Build the session with a `PendingIntent` that opens the app at the player/current context and a callback that resolves media items. Return this single session from `onGetSession()`.
- Release the player and session exactly once in `onDestroy()`; clear references and cancel service-owned coroutines/listeners.
- Retain `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `foregroundServiceType="mediaPlayback"`, and the MediaSessionService intent action already in the manifest.
- Use MediaSessionService's default session-backed notification and foreground transitions first. It automatically updates metadata/actions and, by default, keeps the service when playback is ongoing after task removal and stops it otherwise. Override notification/task behavior only when a focused test demonstrates a product requirement the default cannot meet.
- Do not require `POST_NOTIFICATIONS` for the media-session notification. Android does not require it to launch a foreground service, and media-session notifications are exempt from the Android 13 permission behavior. Add/request it only if a later feature posts non-exempt notifications.
- Supply system title, artist, album, artwork, and appropriate previous/play-pause/next preferences through the session/player metadata and commands, especially on API 33+.

Primary references:

- [Background playback with MediaSessionService](https://developer.android.com/media/media3/session/background-playback)
- [MediaSession.Callback API](https://developer.android.com/reference/androidx/media3/session/MediaSession.Callback)
- [MediaController API](https://developer.android.com/reference/androidx/media3/session/MediaController)
- [Android notification runtime permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission)

---

## Proposed Changes

### T021 — Complete the Existing Playback Service

1. Extend `Resn8MediaService`; retain one private player and one private session.
2. Obtain repositories/application dependencies without constructing a second database/container per controller. Use a service-owned coroutine scope for asynchronous item resolution and cancel it on destroy.
3. Configure audio attributes/focus and noisy-output handling before building the player.
4. Add the session activity and callback for occurrence resolution; leave default command/media-key handling intact unless a test requires customization.
5. Populate Media3 metadata from `MediaFile`, using `displayTitle`/filename and a stable artwork placeholder when nullable metadata is absent.
6. Keep the existing manifest service and required permissions. Use the default Media3 notification/foreground lifecycle.
7. Test create/get-session/destroy idempotence, one-player ownership, callback resolution, invalid requests, metadata fallback, and cleanup.

### T022 — Add PlaybackConnection and Observable State

1. Add application-scoped `PlaybackConnection` and immutable `PlaybackUiState`; expose the connection through `AppContainer` so screens/ViewModels share one controller.
2. Build asynchronously from a `SessionToken` targeting `Resn8MediaService`. Convert connection exceptions into retryable state.
3. Register one listener after connection and remove/release it exactly once. Reconnecting must not duplicate listeners, polling jobs, commands, or player instances.
4. Project controller timeline/current item/metadata/playback state/commands into state flows. Resolve queue rows/media metadata with the batch repository contract.
5. Poll position/duration at a bounded cadence only when needed; handle unknown/unset duration without negative slider ranges.
6. Dispatch commands only when supported and connected. Surface failures as notices.
7. Test delayed success, failure/retry, disposal before future completion, configuration recreation, command gating, polling cancellation, item transitions, and unknown duration.

### T023 — Create Explicit Queues From Library Contexts

1. Add the typed `QueueStartRequest` and a queue coordinator/use case that depends on repositories and `PlaybackConnection`, not Compose or Room entities.
2. Snapshot available-only IDs in the exact visible order for All Tracks, artist, album, and folder queries. For albums, retain the established disc/track/title/ID order; for folders, preserve the current folder/descendant and filter semantics.
3. Snapshot persisted playlist items in manual order, filtering unavailable items for a newly playable queue without changing playlist membership.
4. Validate nonempty eligibility and selected membership before writing.
5. Save `SavedQueueKind.EXPLICIT`, use returned stable item occurrences, set `activeQueueId`, then start at the occurrence matching the selected media ID.
6. Wire existing empty track-row callbacks in library/detail/folder screens and the persisted-playlist detail path. Do not pass full queues through typed routes or retain paged item objects.
7. Test every context, known/unknown groups, active search/sort/filter preservation, duplicate playlist occurrences where repository data permits them, selected-index correctness, empty/all-unavailable/missing-selected failures, persistence-before-play, later playlist edits, and 25,000-item performance.

### T024 — Replace Player and Queue UI Placeholders

1. Update the existing `MiniPlayer`:
   - hide it when no current item is loaded;
   - show artwork fallback, title, artist/album fallback, and live play/pause state;
   - provide play/pause and next when commands are available; and
   - open the typed now-playing route without creating another controller.
2. Update the existing `NowPlayingScreen`:
   - show artwork/fallback, title, artist, album, elapsed/duration, buffering/error state, and numeric score;
   - provide accessible Previous, Play/Pause, Next, and drag-to-seek behavior;
   - clamp seek input, avoid issuing a seek on every recomposition, and reconcile the slider with controller updates after drag;
   - expose Queue and visible Like, Dislike, and Add to Playlist controls; and
   - accept rating/playlist action seams while leaving durable behavior to M5/M6.
3. Update the existing `QueueScreen`:
   - display the saved explicit occurrence order with stable keys;
   - retain duplicate media occurrences as separate rows;
   - highlight/announce the live occurrence rather than merely matching media ID; and
   - seek to a tapped occurrence only when the command is available.
4. Hoist state/events through ViewModels or presentation adapters; keep composables previewable/testable without a real service.
5. Validate minimum touch targets, content descriptions, focus order, non-color selection cues, supported font scales, portrait, and landscape. No core action may depend on an undiscoverable gesture.

### T025 — Integrate Android Playback and Bounded Error Handling

1. Verify background and screen-lock playback using the default foreground transition/session notification.
2. Verify play/pause/previous/next from notification, lock screen, Bluetooth/headset buttons, and other authorized controllers.
3. Verify audio focus gain/loss behavior and pause on `AUDIO_BECOMING_NOISY`; do not implement a parallel broadcast receiver if ExoPlayer's configured handling satisfies the requirement.
4. Convert active playback failures into a structured notice containing the failed queue occurrence, canonical media ID when known, user-safe reason, and whether a next candidate exists.
5. Keep failed/newly unavailable occurrences in the saved queue. Do not delete, compact, or rewrite the saved snapshot because playback failed.
6. Advance to the next candidate at most once per occurrence during the current recovery pass. Clear the attempted set after a successful READY transition or explicit user selection.
7. When the remaining queue is exhausted/all invalid, pause/stop advancing, retain the queue/current context, and show retry/skip/source-unavailable guidance. Never loop indefinitely or clear the timeline merely to dismiss the notification.
8. Avoid logging user paths, content URI details, or metadata beyond what is needed for a privacy-safe diagnostic category.

### T026 — Verify Live Playback Lifecycle

Automated coverage:

- service/session create/destroy, callback resolution, cleanup, and single-player ownership;
- controller connect/fail/retry/release, listener and polling cleanup, activity/configuration recreation, and state projection;
- explicit queue order, stable occurrence IDs, selected index, duplicates, available-only contexts, empty/all-unavailable cases, and persistence-before-play;
- mini-player visibility, now-playing transport/seek, queue highlighting/direct selection, structured errors, accessibility semantics, font scaling, portrait, and landscape;
- corrupt/unreadable content, item becoming unavailable after queue creation, consecutive failures, last-item failure, and all-invalid termination; and
- a deterministic 25,000-item queue proving bounded repository access and no main-thread scan/materialization.

Device/emulator coverage on API 34+:

- real indexed content-URI playback;
- activity recreation and controller reconnection while service playback continues;
- backgrounding, screen lock, task removal while playing, and task removal while paused;
- notification/lock-screen metadata and transport controls;
- audio-focus interruption/recovery, wired/Bluetooth media keys, and headphone disconnect;
- service teardown/recreation without leaked or duplicate players; and
- user-visible handling of missing, unavailable, unsupported, and corrupt media.

Service/process death is not an M4 restoration acceptance test. Record that the explicit queue remains in Room, then defer reconstruction, non-autoplay launch, MediaButtonReceiver, and `onPlaybackResumption()` to T043-T046.

---

## Verification Commands

```powershell
# Unit, Robolectric, repository, queue, controller, and presentation tests
.\gradlew.bat testDebugUnitTest

# Static checks and debug build
.\gradlew.bat lintDebug assembleDebug

# Compile instrumentation/Compose/service lifecycle sources without requiring a device
.\gradlew.bat compileDebugAndroidTestKotlin

# Run API 34+ connected tests when a target is available
.\gradlew.bat connectedDebugAndroidTest
```

Record device model/API level, storage/provider type, headset/Bluetooth test availability, and any environment-limited checks. A Robolectric pass alone cannot close notification, lock-screen, audio-focus, noisy-output, hardware-key, or task-removal acceptance.

---

## Exit Criteria

Milestone 4 is complete only when all of the following are demonstrated:

1. `Resn8MediaService` owns exactly one player/session, releases them correctly, and uses the existing manifest foreground playback declaration.
2. One application-scoped controller connection survives Activity/configuration recreation without duplicate players, listeners, pollers, or commands.
3. All-track, artist, album, folder, and persisted-playlist actions create available-only `EXPLICIT` queue snapshots in deterministic order, preserve stable occurrence IDs, persist before playing, and start at the selected occurrence.
4. Queue materialization is batched/off-main-thread and handles 25,000 items without per-item database queries or quadratic behavior.
5. Mini-player, now-playing, and queue screens reflect live controller state and meet the required metadata, artwork fallback, seek, transport, score, queue, visible rating/playlist seam, accessibility, and adaptive-layout requirements.
6. Playback continues in background/lock screen, responds to system/hardware controls, handles audio focus/headphone disconnect, and uses correct system metadata.
7. Missing/unavailable/corrupt items produce a visible nonfatal explanation, retain the saved queue, advance without infinite loops, and stop safely when no candidate works.
8. Automated build/tests pass and applicable API 34+ device checks are recorded.
9. The handoff does not claim M5 rating/listening behavior, M6 playlist workflows, or M8 exact checkpoint/restoration/resumption behavior.

---

## Reference Files

- [SPECIFICATION.md](../SPECIFICATION.md): Sections 2.2, 3.2, 3.4, 4.1-4.4, and 5.
- [BRAINSTORM.md](../BRAINSTORM.md): Main-player controls, signed-score intent, playlist entry point, and queue/restoration ideas.
- [TASKS.md](../TASKS.md): T021-T026 plus downstream T027-T036 and T043-T046.
- [milestone_1_persistence.md](milestone_1_persistence.md): Queue occurrence identity, repository transactions, session state, and migration requirements.
- [milestone_2_indexing.md](milestone_2_indexing.md): Content URIs, availability, metadata/artwork fallbacks, and storage error categories.
- [milestone_3_browsing.md](milestone_3_browsing.md): `LibraryQuery`, exact visible snapshots, availability distinctions, typed routes, and downstream playback boundary.
- [SavedQueue.kt](../../app/src/main/java/com/app/resn8/domain/model/SavedQueue.kt)
- [QueueRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/QueueRepository.kt)
- [MediaRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/MediaRepository.kt)
- [UiSessionState.kt](../../app/src/main/java/com/app/resn8/domain/model/UiSessionState.kt)
- [Resn8MediaService.kt](../../app/src/main/java/com/app/resn8/playback/Resn8MediaService.kt)
- [AndroidManifest.xml](../../app/src/main/AndroidManifest.xml)
