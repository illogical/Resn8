# Milestone 8 Implementation Plan: Startup Restoration and Index-Completion Feedback

## Purpose

Implement T059-T062 from [TASKS.md](../TASKS.md) by correcting the post-Milestone 7 launch experience before smart-queue work begins. A configured user who reopens Resn8 must not be sent through the old indexing-completion surface. The app must wait until persisted setup and UI-session state are known, restore the last valid destination, and open Now Playing when a legacy/stale onboarding route coexists with a valid active playback queue. Index completion should remain reassuring and useful without presenting scanner diagnostics as the primary success experience.

Milestone 8 closes the startup/navigation and scan-feedback integration gap discovered during Milestone 7 acceptance. It does not change queue ordering, playback ownership, meaningful-play accounting, index admission rules, or any Milestone 9 smart-generation behavior.

## User feedback and desired outcome

The verified playback behavior is correct: a previously selected track and bounded position are reconstructed in a paused state. The remaining problems are presentation and navigation:

1. Relaunch can initially select Onboarding and then display the last persisted scan summary as though indexing just completed.
2. The completion surface gives equal visual weight to track totals, internal traversal counts, metadata-source counters, rejection categories, and artwork diagnostics.

After this fix:

- Normal relaunch never flashes or settles on indexing-complete merely because a historical `lastScanSummary` exists.
- A configured session returns to its last valid screen. For affected legacy sessions whose saved route is still `onboarding`, a valid `UiSessionState.activeQueueId` and restorable current item resolve to Now Playing; without a valid active queue, the fallback is Library.
- Playback remains paused on a normal app launch. Navigating to Now Playing must not imply autoplay.
- A scan that completes while the user is actively observing it shows a compact, pleasant success result with one clear next action.
- Detailed scan evidence remains available on demand and in Settings, with warnings promoted only when they are actionable.

## Checked-in baseline and root cause

### Startup navigation is decided from placeholder data

`Resn8App.kt` currently collects `getRootSourcesFlow("MUSIC")` with `initial = emptyList()` and immediately derives both `isSetupComplete` and the `NavHost` start destination from that placeholder. The first composition therefore chooses `OnboardingRoute`. When Room later emits the configured source, recomposition changes the argument passed to `Resn8NavHost`, but an already-created navigation graph/controller is not a reliable mechanism for replacing the initial back stack.

This also bypasses the Milestone 7 destination model. `RestorableDestination.fromSessionState()` and `resolveValidDestination()` exist, but `Resn8App` does not use them to choose the launch destination. The current code consequently cannot fulfill the documented requirement to wait for session readiness and restore the last valid browsing surface.

The hard-coded `"MUSIC"` lookup is also an identity hazard: the implemented collection uses its persisted ID, while `MUSIC` is a profile concept. Startup setup resolution must use the same authoritative active-selection path as Library, Folders, Settings, playlists, and playback.

### A historical summary is treated as a new UI event

`OnboardingViewModel.checkExistingRoot()` maps any non-running root with `lastScanSummary` directly to `IndexingUiState.Complete`. Because scan summaries are intentionally durable, every recreation of this ViewModel can redisplay the completion card indefinitely. The model does not distinguish:

- a scan completion observed during the current UI session;
- historical last-scan information used for Settings/status;
- a completed setup whose route should no longer be Onboarding.

The durable summary should remain data, not a durable navigation command.

### The success hierarchy is too dense

`CompleteSummaryContent` currently renders nearly every field in `ScanResult`: track and document totals, duration, folders, add/update/missing counts, metadata provenance, parser fallbacks, unsupported categories, rejection reasons, and artwork candidates. These values are valuable for diagnostics but create an audit report where the user needs a simple success confirmation.

Settings already has a last-scan summary seam, making it the appropriate home for persistent status and optional details after onboarding is complete.

## Product decisions

### Launch precedence

Resolve launch state once, before constructing the navigation graph:

1. `Loading`: setup/session/playback readiness is not known. Show a neutral app loading surface; do not render Onboarding underneath it.
2. `NeedsSetup`: no usable configured collection/root exists. Start Onboarding.
3. `Ready(destination)`: validate the persisted typed destination and create the `NavHost` with that resolved start destination.
4. `RecoverableSetupProblem`: preserve the existing Settings/Onboarding recovery choices for unavailable source or revoked SAF access; do not pretend the library is unconfigured.

For a configured installation, resolve destinations as follows:

- Honor a valid persisted Library, artist, album, folder, playlist, Queue, Now Playing, or Settings destination according to the Milestone 7 fallback rules.
- Treat persisted `onboarding` as stale after setup is complete. If `UiSessionState.activeQueueId` addresses a saved queue with a valid/restorable current occurrence, normalize the destination to Now Playing. Otherwise normalize it to Library.
- Persist the normalized fallback so the same repair is not repeated on every launch.
- Select the queue only through `UiSessionState.activeQueueId`; never use the most recently updated queue.

This rule repairs the reported installation while preserving the broader promise that an intentional, valid browsing destination is restored. It does not force Now Playing over Settings or another valid last screen merely because a queue exists.

### Scan completion is an ephemeral event

`lastScanSummary` remains persisted for source status, diagnostics, and Settings. It must not by itself produce `IndexingUiState.Complete`.

- Initial setup: show completion only when the currently observed WorkManager run transitions to `SUCCEEDED` while the onboarding flow owns that run.
- Manual re-index from Settings: keep the user in Settings and show a brief completion acknowledgement there; never navigate to Onboarding.
- Process death after a successful scan but before initial handoff: derive a safe setup handoff from persisted collection/source/session state. Do not replay the full completion experience solely from historical summary data.
- Empty scan, permission loss, source unavailability, cancellation, and failure remain explicit states because they require user action or explain why setup cannot proceed.

### Compact success presentation

Replace the dense completion card's default content with a short hierarchy:

- Title: `Library ready`
- Primary result: `<count> tracks indexed`
- Optional change line when meaningful: `<added> added • <updated> updated • <missing> unavailable`
- Primary action during initial setup: `Open Library`
- Optional secondary disclosure: `View scan details`

The optional details region may retain the full privacy-safe `ScanResult` evidence, but it starts collapsed and groups fields under understandable labels rather than presenting a flat wall of counters. Zero-value issue rows are omitted. Metadata provenance, inspected-document totals, artwork candidates, individual rejection categories, and elapsed/folder diagnostics belong in this disclosure or Settings.

If unreadable branches, metadata failures, unavailable items, or unsupported audio-like files are nonzero, show one calm summary such as `Some files need attention` with a combined count and a `View details` action. Ignored non-audio documents are normal during a recursive music-folder scan and must not be styled as an error.

For Settings re-indexing, use an accessible transient acknowledgement such as `Library updated — 12 added, 1 unavailable`, backed by persistent concise last-scan status in the source card. Important warnings must remain inspectable after the transient message disappears.

## Architecture and implementation plan

### Phase 1: introduce an application startup coordinator

1. Add an application-scoped startup state holder/ViewModel that combines the authoritative active collection/source selection, `UiSessionState`, and the minimum playback restoration state needed to determine whether Now Playing is valid.
2. Model startup explicitly as `Loading`, `NeedsSetup`, `Ready`, and `RecoverableSetupProblem`; avoid using empty collection lists as both “still loading” and “no setup.”
3. Resolve the active collection by persisted IDs, with the existing sole-collection repair only when the session selection is empty or stale. Do not query a collection using the profile literal `"MUSIC"`.
4. Map `UiSessionState` through `RestorableDestination.fromSessionState()` and `resolveValidDestination()`. Extend the resolver so a configured stale-Onboarding destination follows the active-queue-to-Now-Playing/Library fallback defined above.
5. Map the resolved `RestorableDestination` to the typed Compose route used as the one-time `NavHost` start destination.
6. Render only a neutral loading gate until resolution finishes. Construct the navigation graph after the result is stable so Room's initial emissions cannot strand Onboarding in the back stack.
7. Persist any corrected destination/selection before or immediately after navigation using serialized session updates. A persistence failure is nonfatal but must be logged without media paths, URIs, titles, or other library details.

### Phase 2: make route persistence complete and observable

1. Wire meaningful successful navigation changes to `UiSessionState.currentRoute` and the associated typed selection fields. The existing `RestorableDestination` mapper must become production behavior rather than unused infrastructure.
2. Persist Now Playing when the user opens the full player, Queue when the queue screen opens, Settings when selected, and the existing typed keys for library/detail/folder/playlist screens.
3. Do not persist transient dialogs, playlist-selector sheets, loading gates, scan-detail expansion, or error overlays as destinations.
4. Ensure returning from the system background without Activity recreation does not rebuild the graph or reset the current back stack.
5. Keep normal playback reconstruction service-owned and paused. The startup coordinator observes restoration readiness but never installs media, creates a player, or changes `playWhenReady`.

### Phase 3: separate onboarding state from persisted scan history

1. Remove the `lastScanSummary -> IndexingUiState.Complete` branch from ordinary `OnboardingViewModel` initialization.
2. Track the WorkManager run ID/source ID owned by the current onboarding attempt. Emit `Complete` only for a terminal success belonging to that observed attempt.
3. On ViewModel recreation during an in-progress scan, reattach to the unique work and continue showing progress. On terminal success, decide between the ephemeral compact completion and direct handoff using explicit onboarding/session state rather than summary presence alone.
4. When a usable root already exists, leave Onboarding through the centralized startup/handoff logic. Do not offer a historical `Go to Library` gate on every launch.
5. Keep `lastScanSummary` intact in Room and continue refreshing it atomically with scan publication. No destructive migration or summary clearing is required.
6. Consolidate WorkManager observation where practical so Onboarding and Settings do not independently reinterpret the same old terminal work as a new event.

### Phase 4: redesign completion feedback

1. Replace `CompleteSummaryContent` with a compact success component following the hierarchy above.
2. Extract a reusable scan-summary formatter/presentation model that separates headline counts, actionable warnings, ordinary ignored files, and diagnostic details. This avoids duplicating counter interpretation between Onboarding and Settings.
3. Add an accessible expand/collapse control for details. Announce completion once through semantics; do not cause every counter to be read as an equally important alert.
4. Update `SettingsUiState` to expose the just-completed result separately from the persistent `activeSource.lastScanSummary`, enabling a one-time acknowledgement without losing durable details.
5. Clear/consume transient completion acknowledgement after it has been displayed, while leaving the persistent last-scan card available.
6. Preserve current empty-folder, failure, permission, and cancellation copy, reviewing only terminology needed to distinguish actionable audio issues from ordinary ignored documents.

## Verification plan

### Unit and ViewModel tests

- Startup remains `Loading` while collection/source/session flows have not emitted; an initial empty placeholder cannot select Onboarding.
- No collection/root resolves to `NeedsSetup` and Onboarding.
- A configured installation with each valid persisted destination restores that destination.
- Configured + stale `onboarding` + valid `activeQueueId` resolves to Now Playing and persists the correction.
- Configured + stale `onboarding` + missing/empty/unrestorable active queue resolves to Library without deleting the saved queue or history.
- Active queue resolution uses `UiSessionState.activeQueueId`, not queue update time.
- Invalid artist, album, folder, and playlist targets retain the established deterministic parent fallbacks.
- Historical `lastScanSummary` alone never emits a new completion event.
- An observed current WorkManager success emits completion exactly once; ViewModel recreation during running work reattaches without duplicating the terminal event.
- Manual Settings re-index produces one transient acknowledgement and updates persistent source status.
- Scan-summary presentation omits zero-value warnings, does not classify ignored non-audio documents as failures, and preserves every detailed counter behind disclosure.

### Compose/navigation tests

- Cold launch shows a neutral loading surface with no Onboarding or completion-card flash.
- The affected legacy state opens Now Playing with the restored title, queue occurrence, and bounded position visible and paused.
- A valid last Library/Folder/Playlist/Settings destination remains preferred over Now Playing even when an active queue exists.
- Initial indexing success renders `Library ready`, the indexed-track total, optional change/warning summary, `Open Library`, and a collapsed `View scan details` control.
- Expanding details exposes the retained privacy-safe metrics; collapsing them returns focus predictably.
- Rotation/recreation does not duplicate success announcements or navigate backward to Onboarding.
- Back navigation after restored startup does not reveal the loading gate or obsolete Onboarding destination.

### Service and repository regression tests

- Normal launcher open still restores the active queue/current occurrence/position paused, even if prior play intent was true.
- Explicit Android media resumption remains the only cold-start path allowed to resume audibly.
- Startup navigation observation cannot replace a newer user-started queue or mutate player ownership.
- Session correction and scan-summary reads preserve all library, rating, history, playlist, queue, and source rows.

Run the required Windows verification:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat compileDebugAndroidTestKotlin
```

### Manual API 34+ acceptance

1. Reproduce the current affected state: a completed root with historical scan summary, `currentRoute = onboarding`, and a valid active queue. Force-stop/relaunch and confirm there is no completion modal/card flash; Now Playing opens at the restored position and remains paused.
2. From Now Playing, navigate to Library, Settings, a playlist, and a nested folder in separate runs. Relaunch after each and confirm the valid last screen is restored rather than forcing the player.
3. Clear or invalidate only the active queue pointer while keeping the library configured. Relaunch and confirm the stale onboarding route falls back to Library without data loss or a loop.
4. Perform a fresh initial scan. Confirm the compact success result is understandable at a glance, details are collapsed, `Open Library` works once, and the next relaunch does not replay completion.
5. Run a manual re-index from Settings. Confirm the app stays in Settings, shows a brief completion acknowledgement, and retains expandable last-scan details.
6. Exercise scans with zero issues and with known unsupported/unreadable samples. Confirm zero warnings disappear, real issues remain discoverable, and ignored non-audio documents are not presented as failures.
7. Rotate, background/foreground, remove from Recents, and relaunch during scanning, immediately after completion, and while paused in Now Playing. Confirm no duplicate completion, autoplay, startup loop, or lost playback context.
8. Verify TalkBack focus/announcement order and enlarged text for the compact result and details disclosure.

## Acceptance criteria

- A historical `lastScanSummary` can never independently navigate to or recreate indexing completion.
- Resn8 waits for authoritative setup/session readiness before creating its navigation start destination; Onboarding does not flash on configured launch.
- The reported legacy/stale onboarding state opens Now Playing when `UiSessionState.activeQueueId` has a restorable current item, at the saved bounded position and paused.
- Valid intentional destinations still restore according to Milestone 7 rather than being overridden by the existence of a queue.
- Startup and active collection resolution use persisted collection/source IDs and never conflate the `MUSIC` profile with a collection identity.
- Initial scan completion presents a calm headline, track count, optional change/warning summary, one primary action, and collapsed details.
- Manual re-index completion stays in Settings and provides concise transient plus persistent feedback.
- Full privacy-safe scan evidence remains available without overwhelming the default success state.
- Automated verification and API 34+ manual acceptance pass before Milestone 9 implementation begins.

## Documentation and milestone boundary

After implementation and verification:

1. Add the corrected cold-launch, legacy-route repair, initial scan completion, and Settings re-index workflows to `docs/UX.md`.
2. Record implementation evidence and exact automated/manual results in a walkthrough under `docs/walkthroughs/`.
3. Mark Milestone 8 complete only after these regression acceptance criteria pass; do not modify Milestone 9 tasks or smart-generation behavior as part of this milestone.
4. Preserve the completed `docs/fixes/post_indexing_library_handoff.md` as historical context; this plan supersedes only its prior expectation that every successful relaunch may revisit the completion summary.

## Review gate

- [ ] Startup is gated on real persisted state rather than an initial empty Flow value.
- [ ] Valid last-screen restoration is preserved, with Now Playing used specifically to repair configured stale-Onboarding sessions that have an active queue.
- [ ] Normal launch remains paused and player ownership remains exclusively in `Resn8MediaService`.
- [ ] Durable scan history is separated from one-time completion events without clearing useful Room data.
- [ ] Default completion feedback is concise, while actionable warnings and full diagnostics remain accessible.
- [ ] Onboarding and Settings completion behavior are tested separately.
- [ ] The full fix is verified before work moves to Milestone 9.
