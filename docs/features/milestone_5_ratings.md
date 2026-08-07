# Milestone 5 Implementation Plan: Track Ratings and Meaningful Plays

## Objective

Implement T027-T030 from [TASKS.md](../TASKS.md) so signed ratings and listening statistics become durable, reactive, and trustworthy during foreground and background playback. Milestone 5 builds on the checked-in Milestone 4 player and the Room persistence contracts from Milestone 1; it must reconcile the preliminary working-tree implementation before adding further behavior.

The result must satisfy [SPECIFICATION.md](../SPECIFICATION.md), preserve the local-first principles in [README.md](../../README.md), and retain the product intent in [BRAINSTORM.md](../BRAINSTORM.md). The specification is normative.

Milestone 5 does not implement playlist editing (Milestone 6), periodic position checkpoints/process-death restoration/Media3 resumption (Milestone 7), startup-restoration integration corrections (Milestone 8), or smart queue generation (Milestone 9).

## Verified Baseline and Preflight

| Area | Verified baseline | Milestone 5 work |
| --- | --- | --- |
| Rating persistence | Room schema version 2 stores `media_files.likeScore`; the DAO performs an atomic SQL increment and the repository accepts only `+1` or `-1`. | Complete UI command wiring and authoritative score propagation; test rapid/concurrent actions, invalid deltas, missing media, and restart durability. |
| History persistence | `playback_history` has a unique `sessionOccurrenceId`; `commitMeaningfulPlay()` atomically writes history and updates `playCount`/`lastPlayedAt`, and rejects a second counted result for the same occurrence. | Feed already-qualified results from the playback tracker and strengthen concurrency, rollback, and result tests. |
| Queue identity | Saved queue items have stable `queueItemId` values, while the queue header already has a separate `currentOccurrenceId`. | Use a fresh playback occurrence ID for each traversal; never use `queueItemId` as the history idempotency key. |
| Playback | `Resn8MediaService` exclusively owns ExoPlayer/MediaSession. `PlaybackConnection` is application-scoped and exposes controller state and commands. | Put active-listening observation in a service-owned tracker. Keep the connection as the UI/controller boundary. |
| Preliminary M5 changes | The working tree wires Now Playing rating actions and contains controller-side, wall-clock accumulation. | Preserve useful rating seams, but replace controller-owned accounting with the service-owned design below. |
| Verification | The current `testDebugUnitTest` baseline passes. Persistence tests cover basic signed-score and idempotent commit behavior. | Add deterministic tracker and Media3 lifecycle/transition coverage; passing baseline tests alone does not complete M5. |

Before implementation, review the working tree and preserve unrelated staged/unstaged changes. No schema migration is expected. If a schema change proves necessary, increment the version, export the schema, add a non-destructive migration and migration test, and never enable destructive fallback.

## Required Behavior and Interfaces

### Identity

- `mediaId` identifies the canonical indexed track.
- `queueItemId` identifies one stable entry in a saved queue; duplicate tracks receive distinct queue-item IDs.
- `sessionOccurrenceId` (persisted as `currentOccurrenceId` for restoration) identifies one traversal of a queue item and is the unique key for meaningful-play history.
- Create a fresh occurrence ID on initial entry, next/previous, direct queue jump, repeat, or replay/re-entry. Pause, resume, buffering, seeking, and UI/controller recreation retain the current occurrence.
- Finalize the previous occurrence before starting the next. A repeated commit for one occurrence must be harmless; entering the same queue item later with a new occurrence may count again.

### T027 — Atomic Signed Ratings

- The original milestone shipped Like and Dislike as unbounded signed adjustments. UX Improvements v4 supersedes the lower-bound portion: Like atomically adds `1`, while Dislike subtracts `1` only until the score reaches `-1`.
- Keep repository validation that rejects any delta other than `+1` or `-1`, and define missing-media behavior explicitly rather than silently presenting a false successful update.
- Make the authoritative post-mutation score available by returning it from the atomic adjustment or exposing a media-by-ID `Flow`. Apply the same contract to Room and fake repositories.
- `PlaybackConnection` exposes Like/Dislike commands. Now Playing shows `+N` only for positive scores and hides the count at `0`/`-1`; the mini-player shows the current liked/disliked indicator; paged library/filter/sort state refreshes from Room invalidation or repository observation.
- Rapid or concurrent taps cannot lose increments or leave current-player state at a stale score.
- Rating never skips the item, interrupts playback, removes playlist membership, or rewrites/reorders the active saved queue. Do not add unsupported library-row rating buttons or notification score presentation as part of M5.

### T028 — Service-Owned Active Listening

- Add a small, independently testable meaningful-play tracker owned by `Resn8MediaService` and driven by the service's authoritative ExoPlayer events.
- Inject two time sources: monotonic elapsed time for accumulation and epoch time for persisted history timestamps. A device clock change must not manufacture or discard listened duration.
- Accumulate only while `playbackState == Player.STATE_READY` and `isPlaying == true`.
- Exclude paused, buffering, stopped/error, and audio-focus-interrupted time. Seeking forward or backward changes position only and never adds skipped time.
- Initialize tracking for the current item when playback is first prepared or recovered, including when a controller connects after playback has started.
- On every item transition, qualify/finalize the previous occurrence before resetting state for the new item. Preserve tracking across Activity or controller recreation while the service remains alive.
- Service/process interruption before qualification must not create a count. Periodic position/occurrence checkpointing and restoration after process death remain T043-T044.

### T029 — Meaningful-Play Qualification and Commit

- For a known duration of at least one minute, qualify after 60,000 ms of cumulative active listening. For a known duration shorter than one minute, do not time-qualify before completion.
- For an unknown/unset duration, qualify after 60,000 ms of cumulative active listening or genuine completion.
- Treat automatic and repeat item transitions caused by reaching the end, plus final-queue `STATE_ENDED`, as completion signals. Manual next/previous, direct jumps, queue replacement, stop, and failure are not completion signals.
- Genuine completion qualifies any track after playback has advanced since occurrence entry or the most recent seek. Starting midway or seeking near the end and then playing through the end counts; seeking directly to the exact endpoint without subsequent playback does not.
- "One minute" is cumulative active playback, not an uninterrupted streak. Pause, buffering, focus interruption, seeking, and process restoration preserve already accumulated active time while excluded downtime adds nothing.
- On qualification, atomically persist the history result, increment `playCount` once, and set `lastPlayedAt`. Preserve distinct `THRESHOLD_COUNTED` and `NATURAL_COMPLETION_COUNTED` results.
- Use the playback occurrence ID as `sessionOccurrenceId`. Room's unique constraint and transaction are the final defense against transition/threshold races or retry after UI/service concurrency.
- Persisted `startedAt`, qualification/end, `countedAt`, and `lastPlayedAt` values are epoch milliseconds; `accumulatedListenedDurationMs` comes from monotonic elapsed time.

## Implementation Changes

### Playback and domain

- Introduce the deterministic tracker and its event/result contract. It owns the active occurrence ID, canonical media ID, start timestamp, monotonic baseline, accumulated duration, known/unknown duration, and committed state.
- Attach it to player readiness, `isPlaying`, media transition, completion, error, and service shutdown events in `Resn8MediaService`.
- Carry canonical `mediaId` and stable `queueItemId` through MediaItem extras, but generate/use a distinct traversal occurrence ID for history.
- Remove meaningful-play state, wall-clock deltas, and commit decisions from `PlaybackConnection`. Its bounded progress polling remains UI-only.

### Repositories and presentation

- Reconcile `MediaRepository`, Room, and fake rating APIs so callers receive/observe the authoritative updated score and missing media is handled consistently.
- Preserve the existing idempotent meaningful-play transaction, adding validation and tests where needed rather than moving threshold policy into Room.
- Wire Now Playing actions through `PlaybackConnection`; collect authoritative current-media state so Now Playing and mini-player cannot be overwritten by stale asynchronous reads.
- Let Room/Paging invalidation refresh affected library rows, filters, and sorts. No direct play-count UI is required by M5.

## Verification Plan

### Automated tests

- **Ratings:** UX Improvements v4 normative sequence `0 -> 1 -> 2 -> 1 -> 0 -> -1 -> -1`, invalid deltas, missing media, rapid/concurrent updates, authoritative UI propagation, migration normalization, and file-backed close/reopen.
- **Tracker thresholds:** just below/at 60 seconds for known durations of at least one minute and unknown durations, short-file completion-only behavior, genuine completion after starting/seeking midway, and direct-to-end zero-play rejection.
- **Time accounting:** pause/resume, buffering, focus interruption, forward/backward seeks, wall-clock jumps, and monotonic elapsed accumulation.
- **Transitions and identity:** automatic completion before the next item is initialized, final `STATE_ENDED`, previous/next/direct jump, repeat/replay, duplicate media entries, new occurrence per traversal, and retry idempotency within one occurrence.
- **Lifecycle:** background/screen-lock playback, Activity/controller recreation, connection after playback begins, service interruption before qualification, and threshold/transition commit races.
- **Persistence:** atomic history plus statistics, rollback on injected failure, concurrent duplicate commits, distinct result values, and different occurrences for the same media counting independently.

Run from the repository root on Windows:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

### Manual API 34+ verification

- Repeatedly Like and Dislike the current track across zero; verify immediate Now Playing/mini-player state, library refresh, uninterrupted playback, unchanged queue order, and relaunch persistence.
- Seek without listening; verify no threshold count. Accumulate one minute of active listening across pause/resume and background playback; verify one count for a one-minute-or-longer or unknown-duration track.
- Exercise automatic and repeat completion, the final queue item, a short file, an unknown-duration fixture, duplicate media entries, direct jumps, and UI recreation. Verify manual transitions do not count, playback from a midway/near-end seek through completion does count, and a direct seek to the exact endpoint without playback does not. Use a deterministic sort/filter fixture or Database Inspector when history/play count is not directly visible.

## Exit Criteria

Milestone 5 is complete only when:

1. Like/Dislike mutations are atomic, durable, authoritative across UI surfaces, and correct under rapid/concurrent actions.
2. The service-owned tracker uses monotonic active-listening time and cannot be advanced by seeks, pauses, buffering, interruption, or wall-clock changes.
3. Stable queue entries and playback traversals use distinct identities; each occurrence counts at most once and genuine replay may count again.
4. The fixed one-minute threshold, short-track completion-only behavior, and automatic/repeat/final completion rules behave as specified during foreground and background playback and UI/controller recreation.
5. History, `playCount`, and `lastPlayedAt` commit atomically with no destructive migration or active-queue mutation.
6. Unit, lint, assemble, and required API 34+ manual/device checks pass and their results are recorded before T027-T030 or the README milestone are checked off.
