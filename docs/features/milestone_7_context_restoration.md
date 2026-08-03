# Milestone 7 Implementation Plan: Playback and Browsing Context Restoration

## Objective and exit condition

Implement T043-T046 from [TASKS.md](../TASKS.md) so returning to Resn8 reconstructs the user's last durable playback and browsing context without autoplay, duplicate traversal accounting, or destructive recovery. Milestone 7 is complete only when a normal cold launch restores the exact explicit queue, stable queue occurrence, current item, bounded position, and last valid screen in a paused state; Android's explicit media-resumption request still works; completed onboarding becomes a Settings entry point; and unavailable targets remain recoverable without deleting queue or history state.

This plan is governed by [SPECIFICATION.md](../SPECIFICATION.md). [BRAINSTORM.md](../BRAINSTORM.md) supplies the original “continue where I left off” intent, [UX.md](../UX.md) defines user-visible verification, and [README.md](../../README.md) supplies the local-first product boundary.

## Checked-in baseline

The Milestone 0-6 baseline already provides most persistence primitives:

- `SavedQueue` and `saved_queue` store current index/media, occurrence ID, position, play intent, speed, and repeat mode; `saved_queue_item` stores the authoritative explicit order and stable `queueItemId` values.
- `UiSessionState` points to `activeQueueId` and stores collection/source, folder, artist, album, playlist, library surface, search, filter, and sort fields.
- `RoomQueueRepository.updatePlaybackCheckpoint` validates index/media consistency and updates checkpoint fields transactionally.
- `StartQueueUseCase` persists a queue and then updates `UiSessionState.activeQueueId` before `PlaybackConnection` installs media in the controller.
- `Resn8MediaService` exclusively owns ExoPlayer, `MediaSession`, audio behavior, metadata resolution, and `MeaningfulPlayTracker`.
- Typed Compose destinations exist for Onboarding, Library, artist/album detail, Folders, Playlists/detail, Queue, and Now Playing.

The restoration path itself is not implemented:

1. `Resn8MediaService` does not write playback checkpoints. `PlaybackConnection` polls position only for presentation, and service destruction resets the tracker.
2. The service creates an empty player on cold start. Queue-to-`MediaItem` construction lives in `PlaybackConnection.startQueue`, so Android media resumption cannot reconstruct context without an Activity/controller.
3. `MeaningfulPlayTracker` keeps accumulated active-listening duration, start time, and committed state only in memory. Restoring only `currentOccurrenceId` would either lose listening credit or mint a new traversal after process death.
4. `Resn8App` always starts at `OnboardingRoute` and exposes Onboarding permanently in bottom navigation. The persisted `currentRoute` is not used to select or rebuild a destination.
5. The UI-session fields do not fully distinguish every typed route key, notably albums with the same title under different album artists. Route writes and target-validation/fallback rules are incomplete.
6. Unavailable media errors currently attempt a one-time forward skip in `PlaybackConnection`; there is no cold-restore recovery state that distinguishes permission, source, queue, and media failures.

These seams should be extended rather than replaced. No Activity, ViewModel, or Composable may become a player owner.

## Product decisions

### Normal launch versus explicit media resumption

- A normal launcher/deep-link app open restores the queue and seek position but prepares it paused, regardless of the previously persisted play intent.
- Persisted play intent is retained as historical intent and for supported Media3 resumption logic; it is not permission to autoplay on an ordinary launch.
- An explicit Android media-resumption request may request audible continuation through the Media3 session callback supported by the pinned dependency. It uses the same validated queue loader and does not create a second restoration implementation.
- Activity/controller recreation while the service is alive is not a cold restore and must not reinstall the queue, seek backward, or pause ongoing playback.

### Semantic context, not an opaque back stack

Persist one last meaningful destination plus the minimum typed keys needed to rebuild it. Do not serialize Navigation's internal back stack, transient dialogs/sheets, selection mode, snackbar state, or large media objects.

Restorable destinations are:

| Destination | Persisted context | Invalid-target fallback |
| --- | --- | --- |
| Library | collection/source, surface, search, filters, sort | First valid surface in active collection |
| Artist detail | collection and serialized artist key | Library Artists |
| Album detail | collection, album key, and album-artist key | Library Albums |
| Folder | collection/source and folder ID | Folder root, then Library |
| Playlists | collection | Library if collection is invalid |
| Playlist detail | collection and playlist ID | Playlists |
| Queue | `activeQueueId` | Now Playing if player context exists, else Library |
| Now Playing | `activeQueueId` | Queue or Library |
| Settings | configured collection/source when present | Onboarding when setup is incomplete |

List scroll offsets, open dialogs, pending text-field edits, and multi-selection are outside the MVP restoration contract. They may be added later without changing the semantic route model.

### Onboarding becomes Settings after setup

Startup has an explicit readiness gate; it does not briefly render Onboarding while Room loads.

- No usable collection/source: show Onboarding and its recovery actions.
- Usable configured collection/source: restore the last valid destination; the first top-level navigation item is Settings rather than Onboarding.
- Settings initially owns source/collection status, manual re-index, and SAF permission reselection. It is deliberately a shell for later multiple-collection management, scheduled indexing, playback preferences, backup/restore, and other configuration.
- Onboarding remains reachable only for first-run or recovery flows. It does not remain a normal post-setup tab.

### Stable identity and occurrence accounting

- `queueItemId` identifies the persisted position in an explicit queue; duplicate media IDs remain distinct.
- `sessionOccurrenceId` identifies the current traversal and survives pause, seek, controller recreation, and process restoration of that same traversal.
- Persist in-progress occurrence data keyed by `sessionOccurrenceId`: media ID, start epoch, accumulated active-listening duration, and qualification/commit state. The existing `PlaybackHistoryResult.IN_PROGRESS` model may be used if repository semantics are expanded to safely upsert progress without incrementing play count.
- Before an item transition, flush/finalize the old occurrence and checkpoint the new queue position only after ExoPlayer reports the transition. Never infer a traversal from seek position alone.
- If an occurrence was already counted before process death, restored tracking is marked committed. Repository uniqueness on `sessionOccurrenceId` remains the final idempotency guard.

## Architecture and contracts

### Shared saved-queue loader

Extract queue-to-media resolution from `PlaybackConnection.startQueue` into a playback-domain/service collaborator that:

1. reads the queue selected by `UiSessionState.activeQueueId` or an explicitly supplied queue ID;
2. loads `saved_queue_item` rows in stored order without replacing their IDs;
3. resolves media records in one order-preserving query;
4. builds Media3 items with queue ID, media ID, and queue-item ID extras plus resolved content URI/metadata;
5. reports available, unavailable, and missing rows without mutating the queue; and
6. returns a validated start item and bounded position.

Both new-queue playback and cold restoration use this mapper. Starting a newly created queue still persists first, updates `UiSessionState.activeQueueId`, and only then installs it in the service/controller.

### Service-owned checkpoint coordinator

Add a coordinator scoped to `Resn8MediaService`. It reads authoritative player/tracker state and writes through repositories on an IO dispatcher.

- Periodic interval while actively playing: choose and document a bounded cadence (initial target: every 5 seconds).
- Immediate triggers: pause, completed seek/discontinuity, media-item transition, repeat/speed change, app background/task removal notification, and service shutdown.
- Coalesce equivalent snapshots and allow only one database writer at a time.
- Assign each snapshot a monotonically increasing in-memory revision. Before committing, discard work older than the latest requested revision so delayed writes cannot overwrite a transition.
- Flush the old occurrence before initializing/checkpointing the next occurrence.
- Clamp negative positions to zero. On restore, clamp to known duration; for ended content or positions at the exact duration, use a documented safe near-end/zero policy tested with short files.
- Database failure is nonfatal to playback. Retain a concise service-side failure signal and retry on the next trigger without spamming UI or logs.

The checkpoint transaction updates the addressed saved queue only. It does not choose “the newest queue.” `UiSessionState.activeQueueId` remains the sole active-queue selector.

### Occurrence-progress repository

Add repository operations to load and upsert in-progress occurrence accounting. A safe transaction must:

- reject media/occurrence mismatches;
- monotonically retain the greatest accumulated active-listening duration for an occurrence;
- never downgrade a counted result to `IN_PROGRESS`;
- atomically change `IN_PROGRESS` to a counted result and increment `MediaFile.playCount` exactly once;
- allow interruption/finalization without deleting audit history; and
- retain epoch timestamps in Room while reinitializing the monotonic tick baseline after process start.

Extend `MeaningfulPlayTracker` with an explicit hydrate path. Hydration sets occurrence/media identity, accumulated duration, original start epoch, and committed state, but resets `lastMonotonicTickMs` to the current monotonic clock so downtime is never counted.

### Restoration state machine

Represent startup as observable state rather than scattered booleans:

`LoadingSession -> NoSetup | RestoringPlayback | Ready | RecoverableFailure`

Rules:

- Only one cold restoration attempt may own player initialization.
- Restoration captures the target queue ID/revision. Re-check `UiSessionState.activeQueueId` before installing media; abort if a newer queue has become active.
- Loading the same queue again is idempotent. Compare queue ID and queue-item IDs before replacing player media.
- Prepare media before seeking if required by Media3. Apply repeat mode and speed before exposing Ready.
- Normal launch forces `playWhenReady = false` after restoration. Explicit media resumption is a separate event with an auditable source.
- Publish restoration/recovery status through the application-scoped `PlaybackConnection` so Queue, Now Playing, mini-player, and Settings show one consistent state.

### UI-session persistence and navigation gate

Introduce a typed `RestorableDestination` mapper between Compose routes and persisted session fields. Stable route tags are versioned constants, not class names or user-visible labels.

Evolve `UiSessionState` only where exact reconstruction requires it, including a distinct album-artist key for album detail. Add an explicit non-destructive Room migration and migration test for every added column; existing rows receive safe defaults.

Persist route context after successful navigation and persist search/filter/sort changes with debounce where appropriate. Do not overwrite the last meaningful route with temporary startup, selector, dialog, or error destinations. Validate foreign-key-backed IDs and metadata keys before navigation, apply the fallback table above, then persist the corrected destination to avoid repeating a broken restore.

`Resn8App` waits for startup readiness before constructing the final `NavHost` start destination. Bottom navigation derives from readiness: Onboarding for incomplete setup/recovery; Settings for configured use.

### Unavailable recovery model

Expose stable recovery categories rather than parsing exception strings:

- `PermissionRevoked(sourceId)` -> reselect permission, retry, open Settings;
- `SourceUnavailable(sourceId)` -> retry, open Settings;
- `CurrentItemUnavailable(queueItemId)` -> retry or skip to next available;
- `NoPlayableItems(queueId)` -> retain queue, open Queue/Settings;
- `MissingQueue(queueId)` -> clear only the dangling active pointer after explanation, then fall back to Library;
- `InvalidCheckpoint(queueId)` -> repair index from matching queue-item/media identity when unambiguous, otherwise use the first available item and report the fallback.

Skip-to-next scans the persisted order once, does not loop forever, and persists a new current item only after a playable candidate is successfully prepared. Reselecting a source uses SAF and never requests broad storage permission.

## Implementation sequence

### T043 - Durable service-owned checkpoints

1. Add in-progress occurrence persistence and tracker hydration with repository/fake parity.
2. Extract current player/tracker state into an immutable checkpoint model.
3. Implement the serialized, revision-aware checkpoint coordinator in `Resn8MediaService`.
4. Wire periodic and immediate lifecycle/player triggers, including task removal and service shutdown.
5. Verify database errors remain nonfatal and later checkpoints retry.

### T044 - Safe playback reconstruction

1. Extract the shared saved-queue-to-Media3 mapper from `PlaybackConnection`.
2. Add the service startup restoration state machine and stale-restore guard.
3. Rebuild stable queue items, hydrate occurrence accounting, apply state, prepare, and seek paused.
4. Integrate the Media3 1.5.1 explicit playback-resumption callback through the same loader.
5. Make Activity/controller reconnect observe existing service state without reinstalling media.

### T045 - Browsing restoration and Settings shell

1. Define stable typed destination tags and a route/session mapper.
2. Add any required `UiSessionState` fields and an explicit Room migration.
3. Persist meaningful navigation plus library search/filter/sort context.
4. Add startup readiness gating, validate targets, and implement deterministic fallback.
5. Add `SettingsRoute`/screen, replace the post-setup Onboarding nav item, and move source status, re-index, and permission-reselection entry points into Settings while retaining conditional Onboarding.

### T046 - Recovery UX

1. Map permission, source, media, queue, and checkpoint failures to typed recovery state.
2. Implement retry, SAF reselection, Settings navigation, and bounded skip-to-next actions.
3. Preserve queue/history rows through every recoverable condition.
4. Add accessible copy and actions to startup, player, queue, and Settings surfaces.

## Verification matrix

### Unit and repository tests

- checkpoint model validation, position clamping, coalescing, revision ordering, and duplicate suppression;
- periodic versus immediate checkpoint scheduling with virtual time;
- occurrence hydration preserves ID/start/accumulated duration, resets the monotonic baseline, excludes downtime, and cannot double count;
- in-progress upserts are monotonic and counted history cannot regress;
- active queue selection uses `UiSessionState.activeQueueId`, never latest `updatedAt`;
- stable queue-item IDs and duplicate media occurrences survive close/reopen;
- invalid/missing index-media combinations follow documented repair/fallback rules;
- fake repositories match Room success, validation, and failure behavior;
- migrations preserve all existing library, rating, history, playlist, queue, and UI-session rows.

### Service and playback tests

- normal cold launch restores paused at the bounded position even when saved play intent is true;
- explicit Android media resumption can play through the same reconstruction path;
- Activity/controller recreation during live playback does not reinstall, seek, pause, or create another player;
- process death during an unqualified occurrence resumes accumulated active listening without counting downtime;
- process death after qualification cannot increment the occurrence again;
- transitions flush the prior occurrence before checkpointing the new one;
- a newer user-started queue defeats an older in-flight restoration;
- service shutdown/task removal requests a final best-effort checkpoint without blocking indefinitely;
- unavailable and corrupt items cannot create retry loops or destroy persisted order.

### ViewModel and Compose tests

- startup shows a neutral loading gate rather than flashing Onboarding;
- incomplete setup chooses Onboarding; complete setup restores a valid destination and shows Settings in top-level navigation;
- every restorable route persists and reconstructs exact typed keys, including same-title albums under different album artists;
- library surface, search, filter, and sort restore together;
- deleted/invalid folder, artist, album, and playlist targets use their documented parent fallback and persist the correction;
- transient dialogs, selector state, multi-selection, and onboarding progress do not restore;
- recovery state presents the correct accessible action set and preserves queue/history context.

### Manual API 34+ flow

1. Start a queue with duplicate media occurrences, move to the second occurrence, seek, kill the process, and confirm exact paused restoration.
2. Repeat while below the meaningful-play threshold, listen past the remaining threshold after restore, and confirm exactly one play; repeat after already qualifying and confirm no second increment.
3. Relaunch from Library tabs with filters/search/sorts and from artist, album, nested folder, playlist, Queue, and Now Playing destinations.
4. Delete or invalidate each selected target and confirm deterministic parent fallback without a startup loop.
5. Complete a fresh setup, relaunch, and confirm Settings replaces Onboarding without a flash; exercise re-index and permission reselection from Settings.
6. Revoke SAF access and detach/reattach removable storage; exercise retry, reselect, and skip-to-next while inspecting that queue/history remain intact.
7. Background, task-remove, rotate/recreate, and stop/restart the service at different playback states; confirm one player and a recent checkpoint.
8. Invoke Android media resumption separately from a normal launcher open and confirm only the explicit resumption path may start audio.

Run the required verification on Windows PowerShell:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

## Milestone boundaries and follow-on work

Milestone 7 owns restoration infrastructure, the Settings shell, and the recovery actions needed to regain access to the one MVP collection/source. It does not implement multiple-collection management, scheduled indexing, playback-speed controls, settings backup, or rich preference UI; those post-MVP features extend Settings later.

Milestone 8 owns smart generation. Its explicit saved queues must use the same queue loader, checkpoint coordinator, and restoration behavior delivered here.

Milestone 9 owns broad accessibility/adaptive-layout review, interaction polish, the complete acceptance/performance pass, and release hygiene. Indexed album-art presentation beyond the existing playback metadata/placeholder seam remains post-MVP T058 so it does not delay reliable context restoration.
