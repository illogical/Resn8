# Milestone 11 Implementation Plan: Responsive Home-Screen Playback Widget

**Status:** Implemented; Android 14-16 launcher verification pending

**Tasks:** T069-T073

## Goal

Add one responsive Android home-screen playback widget for Android 14-16. The compact layout presents the current track, signed rating state, and Dislike, Previous, Play/Pause, Next, and Like controls. The expanded layout adds current artwork and up to three upcoming playable queue occurrences that can be selected directly.

This milestone does not create a second player, a lock-screen AppWidget, per-widget configuration, Assistant/App Actions, Wear OS widgets, or Android Auto browsing. `Resn8MediaService` remains the only ExoPlayer and MediaSession owner, while Android's existing system media surface remains responsible for notification and lock-screen playback controls.

## Checked-in baseline

- `Resn8MediaService` owns ExoPlayer, MediaSession, restoration, checkpoints, and meaningful-play accounting.
- The application-scoped `PlaybackConnection` exposes current metadata, artwork, rating, queue occurrence identity, and transport availability to Compose.
- `UiSessionState.activeQueueId` is the authoritative active-queue selector; saved queues preserve distinct `queueItemId` values for duplicate media.
- Like and Dislike currently write through `PlaybackConnection` to the atomic repository API. The widget requires an execution-time service command so a delayed action cannot rate a stale track.
- `MainActivity` currently has no one-shot external navigation contract.

## Platform and registration

- Add stable `androidx.glance:glance-appwidget:1.1.1` through the version catalog.
- Register one non-exported `GlanceAppWidgetReceiver` for `android.appwidget.action.APPWIDGET_UPDATE` with `widgetCategory="home_screen"` only.
- Provide a picker label, concise description, initial loading layout, preview artwork, responsive sizing metadata, and Material-style system colors.
- Use one responsive provider with compact and expanded height buckets rather than separate widget entries or a configuration activity.
- Treat widget UI as passive `RemoteViews`: do not poll playback position or schedule periodic refresh work.

## State and update architecture

Create an immutable `PlaybackWidgetSnapshot` containing connection/empty/error status, current queue-item/media identity, profile-appropriate title and secondary text, artwork, signed rating, playback and command availability, and up to three upcoming occurrence rows.

The snapshot loader must:

1. Resolve the selected collection and active queue only through persisted `UiSessionState` identifiers.
2. Connect to the existing MediaSession long enough to read current player state without starting playback.
3. Match the controller's current media item to the saved queue by stable `queueItemId`.
4. Select the next three available occurrences after the current index without wrapping; duplicate media remain separate rows.
5. Omit synthetic artist/album labels for Audio Files collections.
6. Resolve bounded artwork only for expanded rendering, with a stable placeholder on missing, unreadable, or stale artwork.

Request widget refreshes after placement/resizing, app upgrade, playback item/timeline/play-state/command changes, active-queue changes, rating completion, and widget actions. Coalesce redundant update requests; never update every second merely to animate progress.

## Commands and synchronization

Add explicit internal `LIKE_CURRENT` and `DISLIKE_CURRENT` MediaSession commands. Authorize them only for Resn8's own package, verify the caller again when handling the command, resolve the current media ID at execution time, apply the existing atomic `+1` or clamped `-1`, and return the authoritative media ID and score.

After a successful rating, broadcast a same-app rating-changed session event and request a widget refresh. `PlaybackConnection` uses the same commands and consumes the event so a widget-originated rating is reflected when the foreground UI is already connected.

Widget interactions use explicit intents targeting a non-exported same-package receiver. The receiver bounds each request to five seconds, uses a short-lived MediaController, checks the corresponding standard `Player` command, executes exactly one operation, and releases the controller future in `finally`. Direct upcoming-row selection locates the exact `queueItemId`, seeks to its index at zero, prepares, and starts playback. No widget component constructs or releases ExoPlayer.

## Responsive UX and navigation

### Compact bucket

- Target approximately 4x2 cells with a horizontally and vertically centered content group containing current title, profile-appropriate secondary text, and five controls.
- Retain 48dp targets at the constrained 250x110dp bucket. Use a 300x130dp compact bucket with 56dp targets and 32dp icons when the host provides the space.
- Overlay positive scores through `+99` on Like and display `99+` above that. Hide the count at neutral `0` and disliked `-1`; accessibility still exposes the full rating state, which never relies on color alone.
- Use disabled states when commands or a current item are unavailable. The clickable modifier belongs to the entire touch-target container, not only the icon image.
- Tapping track content opens Now Playing.

### Expanded bucket

- Target approximately 4x4 cells and retain every compact control.
- Add prominent bounded artwork and an **Up next** section containing zero to three upcoming playable occurrences.
- Each row identifies title and profile-appropriate secondary text, is independently accessible, and starts the exact occurrence when tapped.

### Empty and failure states

- With a configured collection but no current item, show a friendly choose-something-to-play state.
- Open Playlists when the active collection has at least one playlist; otherwise open Folders. If no collection exists, open Onboarding.
- A temporary controller or repository failure shows a retryable widget state and must not start playback, replace a queue, or discard persisted context.
- Add a one-shot widget destination extra handled on cold launch and `onNewIntent`. Consume it only after startup readiness, navigate with `launchSingleTop`, and allow the existing destination listener to persist the resulting route.

## Automated verification

- Unit-test snapshot mapping for no collection, zero/nonzero playlists, empty/paused/playing queues, queue end, unavailable entries, duplicate media occurrences, MUSIC/FLAT metadata, artwork failure, and command availability.
- Prove that snapshot creation never selects the most recently updated queue when it differs from `UiSessionState.activeQueueId`.
- Test rating authorization, current-item resolution under a track-change race, clamping, rapid actions, missing media, repository failure, returned score, session-event propagation, and refresh notification.
- Test transport and direct occurrence jumps while paused/playing, disconnected, and at queue boundaries.
- Test cold- and warm-activity navigation for Now Playing and the Playlists -> Folders -> Onboarding empty-state fallback.
- Render compact and expanded widgets with long text, large font, missing artwork, each rating state, disabled controls, and zero to three upcoming rows. Verify touch targets, descriptions, truncation, and no overlap.
- Verify every generated command and occurrence-jump intent has unique PendingIntent identity, round-trips through the action contract, and rejects malformed input.

Run from the repository root with Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

## Device verification and exit criteria

- On disposable Android 14, 15, and 16 targets, verify picker preview, placement, compact/expanded resizing, system colors, current metadata/artwork, every action, upcoming occurrence jumps, launcher restart, app force-stop/relaunch, and paused restoration without autoplay.
- Verify TalkBack labels/focus, large text, touch targets, rating semantics, and disabled actions.
- Do not run connected tests or install/replace an APK on a data-bearing physical device without the immediate inventory, warning, backup check, and approval required by `AGENTS.md`.
- Update `docs/UX.md` with the reusable manual workflow and record automated/device results here during implementation.
- Mark T069-T073 and the README milestone complete only after automated gates pass and the Android 14-16 launcher matrix is recorded. Missing device coverage keeps T073 and Milestone 11 open.

## Verification results — 2026-08-07

- `testDebugUnitTest`: passed, 135 tests across 39 suites with zero failures, errors, or skips.
- `lintDebug assembleDebug`: passed. The debug APK compiled successfully.
- `assembleDebugAndroidTest`: passed; the instrumentation APK compiled without installing or running it.
- `git diff --check`: passed; only the repository's existing Windows line-ending notices were emitted.
- No connected-device, APK installation, launcher placement, or manual Android 14-16 verification was run. T073 and the README milestone therefore remain unchecked.

## Widget feedback follow-up — 2026-08-08

- Launcher screenshots showed compact content anchored toward the top with substantial unused space and the signed rating duplicated in the metadata line.
- On-device navigation from artwork worked, but all Glance callback-backed transport and rating actions were inert. The active MediaSession remained healthy and advertised playback actions, isolating the failure to widget action delivery rather than player ownership or timeline state.
- The follow-up replaces callback actions with an explicit non-exported receiver, centers compact content, adds the larger compact control bucket, and consolidates positive scores into the Like control in both the widget and Now Playing.
- `testDebugUnitTest`: passed, 136 tests across 39 suites with zero failures, errors, or skips.
- `lintDebug assembleDebug`: passed; the debug APK compiled successfully. Existing lint warnings remain non-blocking.
- `assembleDebugAndroidTest`: passed; the new action-contract and rating UI instrumentation tests compile without installing or running an APK.
- `git diff --check`: passed; only the repository's existing Windows line-ending notices were emitted.
- The user-provided launcher screenshots establish the pre-fix compact/expanded baseline, but the repaired APK has not been installed or manually exercised. T073 and the README milestone remain unchecked.

## Assumptions

- This is a post-MVP P2 milestone and does not block unfinished Milestone 10 work.
- All widget instances follow the authoritative active playback context; there is no per-instance collection or playlist choice.
- Expanded mode shows up to three upcoming playable occurrences and does not wrap at the end of the queue.
- Like/Dislike on Android's notification or lock-screen media card is deferred to T074.
- No Room schema or normative MVP specification change is required.
