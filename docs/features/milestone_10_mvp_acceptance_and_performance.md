# Milestone 10 Implementation Plan: MVP Acceptance and Performance

**Status:** Planned; not implemented

**Task:** T048 — Run the complete MVP acceptance and performance suite

## Goal

Demonstrate every MVP acceptance criterion in `docs/SPECIFICATION.md` with reproducible automated or device evidence. Verification must cover correctness, Room persistence, Compose behavior, Media3 service lifecycle, process restoration, a 25,000-file library, internal shared storage, and removable storage without risking app data on a personal device.

This work verifies the checked-in product. It does not redesign the current layout, add post-MVP features, weaken SAF isolation, or move player ownership out of `Resn8MediaService`.

## Checked-in baseline

- JVM and Robolectric coverage already spans domain rules, Room persistence and migrations, scanning, browsing, playback accounting, playlists, collection switching, and startup restoration.
- Compose instrumentation covers first-collection onboarding, post-index interaction, randomized sorting, adaptive Now Playing layouts, and selected Milestone 9/10 UI regressions.
- `LargeLibraryBenchmarkTest` seeds 25,000 Room rows, exercises a representative library query, records its query plan, and publishes a 25,000-row staged scan. `PlaybackMilestone4Test` also exercises a 25,000-item queue path.
- The existing large-library tests print elapsed time but do not prove a scaling bound, exercise the full UI with 25,000 items, or establish device evidence.
- No checked-in acceptance ledger currently ties every specification criterion to a test, target, provider, result, and retained limitation.
- Device execution is environment-dependent and remains governed by the connected-device data-safety procedure in `AGENTS.md`.

## Acceptance evidence model

Create an acceptance ledger within this document during implementation. Each row must record:

| Field | Required value |
| --- | --- |
| Criterion | Exact behavior summarized from `SPECIFICATION.md` section 5 |
| Evidence type | JVM, Room/Robolectric, Compose instrumentation, emulator manual, physical-device manual, or provider |
| Test or workflow | Stable test name or numbered manual workflow |
| Environment | API level, device model/AVD, build variant, storage provider, and exact serial where applicable |
| Result | Pass, fail, blocked, or not run |
| Evidence | Command output reference, timing result, screenshot/log reference, or concise observation |
| Limitation | Device/provider caveat; blank only when none remains |

A criterion may cite several evidence rows. A unit test alone is insufficient for behavior that depends on MediaSession, notification, hardware control, process death, persisted SAF permission, or removable storage.

## Automated verification

### Host gates

Use Android Studio's bundled JDK and run these commands from the repository root:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

Record the command, date, result, test count, relevant report path, and any warnings. A rerun after a failure must retain the original failure in the ledger together with the corrective commit or worktree description.

### Coverage completion

Before adding tests, map the existing suite to every acceptance criterion and reuse authoritative coverage. Add narrowly scoped tests only where the matrix exposes a behavioral gap. Required automated coverage includes:

- Normalized-unique `MUSIC` and `FLAT` collection creation, rename, switching, deletion, permission repair, re-indexing, and collection isolation.
- Filename-only `FLAT` parsing without invented artist/album data and tagged/path-fallback precedence for `MUSIC`.
- Re-index add/update/missing behavior while preserving ratings, play counts, history, playlist membership, queue identity, and occurrence identity.
- Artist, album, folder, all-track, search, sorting, paging, complete available-only Select All, and direct-folder descendant exclusion.
- Single and bulk playlist membership, durable order, randomized sorting, disliked removal, transaction rollback, and matching-playback replacement isolation.
- Like/dislike atomicity and the complete meaningful-play state machine, including pause, seek, repeat, automatic transition, final-item completion, background operation, and controller recreation.
- Active queue resolution exclusively through `UiSessionState.activeQueueId`, stable `queueItemId` restoration, invalid-media fallback, per-collection paused queue restoration, and typed browsing destination restoration.

### Performance verification

Retain the 25,000-item fixtures and turn their output into repeatable regression evidence:

1. Measure representative library queries, staged scan publication, and queue generation at 5,000 and 25,000 items.
2. Run one warm-up followed by five measured iterations for each size in the same process and report the median.
3. Assert exact result counts and invariants independently of timing.
4. Fail the scaling check when the 25,000-item median exceeds ten times the 5,000-item median. This rejects quadratic growth while allowing normal Room, JVM, and host variability.
5. Retain `EXPLAIN QUERY PLAN` output for representative browse queries and investigate any new unbounded scan or temporary sort before accepting the result.
6. On the disposable emulator, populate a debug-only 25,000-item fixture and verify first content, scrolling, search, selection, and queue creation without an ANR or blocked input. The fixture and seeding entry point must remain unavailable to release builds.

Wall-clock numbers are evidence, not universal product promises. Responsiveness passes when long work remains off the main thread, the UI continues to accept input/render progress, and the scaling assertion passes.

## Device and storage verification

### Target isolation

1. Run a read-only `adb devices -l` inventory and record every visible serial.
2. Select exactly one online disposable API 34+ AVD for connected instrumentation. Set `ANDROID_SERIAL` explicitly and verify the target package before execution.
3. Never run a Gradle connected-device task when an unintended or offline target could broaden selection.
4. Compile instrumentation without installation when device execution is not essential.
5. Treat a physical device as data-bearing unless the user explicitly designates it disposable. Connected tests, instrumentation, APK replacement, package clearing, and uninstall require the immediate warning, backup verification, and approval defined by `AGENTS.md`.
6. After an approved mutating run, verify whether `com.app.resn8` and its app-private data remain present and report any reset immediately.

### Emulator suite

Run the complete Compose instrumentation suite on a clean disposable API 34+ AVD. Repeat critical smoke coverage on the target API when a target-level image is available. Verify:

- First run and initial collection creation for both profiles.
- Indexing progress, success, interruption, retry, empty library, corrupt/unsupported media, and unavailable-media recovery.
- Collection switching, Library/Folders/Playlists navigation, drill-down, search, sorting, selection, playlist selector mixed state, and Select All beyond the loaded page.
- Player controls, queue occurrence behavior, rotation, process recreation, process death, and paused cold-start restoration.
- The existing portrait, landscape, and large-font layouts as regressions only; this task does not redesign them.

### Internal and removable storage

Use representative tagged, untagged, duplicate, corrupt, nested, and filename-only files whose source copies can be safely discarded.

- Internal shared storage: select through `ACTION_OPEN_DOCUMENT_TREE`, index, restart the app and device, re-index changes, revoke and repair permission, remove and restore files, and confirm source files are never modified.
- Removable storage: repeat selection, restart, re-index, card removal, unavailable-state handling, reinsertion, and permission repair on a suitable physical device or storage test provider.
- Record the provider authority, filesystem/storage type, device/API, and whether persisted access survived each restart boundary.
- An emulator-only run cannot close removable-storage acceptance. If no suitable target/provider is available, record the limitation and leave T048 incomplete.

## Playback and restoration workflows

On the disposable target, verify all of the following with a queue containing duplicate occurrences:

1. Foreground playback and transport controls.
2. Background and locked-screen playback through the Media3 notification/session.
3. Notification, Bluetooth/headset, and other hardware transport controls.
4. Audio-focus loss/recovery and pause on noisy output such as headphone removal.
5. Activity recreation and repeated navigation without a second player or MediaSession.
6. Meaningful-play qualification while backgrounded, including exactly-once history/statistics commits.
7. Forced process death followed by restoration of the explicit queue, occurrence, item, bounded position, repeat/speed state, and typed destination without autoplay.
8. Explicit Android media resumption after process removal.
9. Repeated collection switching with independent paused queue/item/position restoration; a never-played collection opens its profile home.

## Reporting and exit criteria

- Preserve the completed acceptance ledger, performance medians/scaling ratios, automated command results, and device/provider inventory in this plan's verification-results section.
- Add any new reusable manual workflows to `docs/UX.md` only during implementation, not while creating this plan.
- Do not mark T048 complete until every specification acceptance criterion passes, the full debug verification gates pass, instrumentation has run on a disposable API 34+ target, and removable-storage behavior has been demonstrated on a suitable physical device/provider.
- A limitation may be documented without hiding it, but any limitation covering mandatory acceptance behavior keeps T048 open.

## Assumptions

- Current layout and interaction design are retained.
- API 34 is the minimum device baseline; a current target-API smoke run supplements rather than replaces it.
- Physical-device source audio is disposable test content, while existing Resn8 app-private data is presumed valuable.
- Performance thresholds guard algorithmic regression; they are not cross-device latency guarantees.

