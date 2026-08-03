# Fix Post-Indexing Library Handoff, Browsing Crashes, and Completion Evidence

## Purpose and scope

This plan covers the first interaction after a successful initial scan: interpreting the completion summary, entering the indexed collection, browsing every Library surface, and using search without a crash or misleading empty state.

This document began as the review gate for the implementation summarized below.

## Implementation status

Implemented on 2026-08-02. Production code no longer uses `"MUSIC"` or another profile/display-name literal as a collection identity. The sole persisted UUID collection repairs an empty legacy session, Library/folder/playlist/playback paths share that selection, session-write failures are nonfatal, Paging states are explicit, album composite keys remain scoped by effective album artist, and scan summaries now retain privacy-safe rejection categories.

Local unit, Robolectric, lint, debug assembly, and Android-test compilation pass. A physical-device interaction run remains pending after the connected-test cleanup removed the app-owned index; the music source was not modified, but the collection must be indexed again before the real-library playback acceptance case can be repeated.

## Observed physical-device behavior

The first completed Android scan reported:

| Measure | Android result | Readiness baseline | Assessment |
| --- | ---: | ---: | --- |
| Indexed audio | 6,583 | 6,584 | One expected track was not admitted on Android and needs a reason-level reconciliation. |
| Preferred artwork candidates | 621 | 621 | Exact match. |
| Unsupported files | 1,697 | 5 unsupported audio-like files | The labels describe different populations and must not be compared directly. Android counts every non-directory document that is neither admitted audio nor a preferred artwork candidate. |
| Total source files accounted for | 6,583 + 1,697 + 621 = 8,901 | 8,901 | Exact aggregate match. This strongly indicates complete file enumeration, with one expected audio file classified into the unsupported bucket rather than lost during traversal. |
| Tag-derived metadata | 6,409 | No directly equivalent inventory count | Not directly comparable. The current counter records tracks whose normalized title came from a tag. |
| Path/filename-derived metadata | 211 | No directly equivalent inventory count | Not mutually exclusive with tag-derived: a track can have a tag-derived title and a path-derived artist or album. The two counters must not be summed as a partition of the library. |
| Unrecognized patterns | 3,706 | 3,707 files with no recognized numeric prefix | Close but not definitionally identical. The one-count movement is consistent with the missing admitted track being a no-prefix file, but reason-level evidence is required before concluding that. |
| Elapsed time | 7m 21s | No physical-device baseline | Record as the first device baseline; the Robolectric 25,000-row publication benchmark is not an end-to-end SAF/metadata comparison. |

The aggregate identity `6,583 + 1,697 + 621 = 8,901` is important: the scan result roughly matches the readiness inventory and does not look like a truncated traversal. The remaining discrepancy is specifically admission/classification of one file.

The completion UI currently omits `scannedFolderCount` and `inspectedDocumentCount`, even though the result model retains them. It also hides zero unreadable and metadata-failure counts. Those omissions prevent the screenshot alone from proving all readiness expectations.

## Expected post-indexing behavior

1. Successful indexing remains on the completion summary until the user chooses **Go to Library**.
2. The completion summary identifies what was indexed, what was ignored, and whether any read or metadata failures occurred without exposing filenames, paths, tags, or the selected tree URI.
3. **Go to Library** selects the actual collection and root created during onboarding, replaces the completed onboarding destination, and opens a populated Artists surface.
4. Artists, Albums, All Tracks, and the indexed folder hierarchy all query the same active collection identity.
5. Search focus and text entry, sort/filter changes, and Library tab changes are nonfatal and preserve session state.
6. Empty, loading, query-failure, and genuinely no-match states are visually distinct. Paging's initial empty placeholder must not briefly claim that an indexed collection has no artists.
7. The search control remains compact at normal font scale while preserving a minimum accessible touch target and usable large-font behavior.
8. Relaunching after a successful initial scan retains a valid collection/source selection. Full route and playback restoration remains Milestone 8, but the app must not fall back to an invalid synthetic collection ID.

## Confirmed static findings and hypotheses to verify

### Confirmed: active collection identity is discarded

- `RoomCollectionRepository.createCollection()` assigns a random UUID.
- `OnboardingViewModel.startIndexing()` creates the collection and root but never saves their IDs to `UiSessionState`.
- The completed onboarding state carries only `ScanResult`; **Go to Library** navigates with `LibraryRoute()` and no collection identity.
- `LibraryViewModel` initializes `_collectionId` to the literal `"MUSIC"` and falls back to that literal when `UiSessionState.selectedCollectionId` is null.
- Artist, album, and track paging queries join through `root_sources.collectionId`, so querying `"MUSIC"` cannot return rows belonging to the UUID collection.
- Follow-on routes and consumers also contain literal `"MUSIC"` defaults, including artist/album routes, Library/folder playback requests, `FoldersViewModel`, playlist construction, and the playlist selector.

This fully explains the blank Library shown after a successful publication.

### High-confidence crash hypothesis: invalid session foreign key

Both reported Library crash triggers call `LibraryViewModel.saveSessionState()`:

- Search focus leads to text input and `setSearchText()`.
- Selecting Artists/Albums/All Tracks calls `setSurface()`.

The save copies the current literal `"MUSIC"` into `UiSessionState.selectedCollectionId`. `UiSessionStateEntity.selectedCollectionId` is a foreign key to `collections.id`, but no collection with ID `"MUSIC"` exists. The save runs in an unguarded `viewModelScope.launch`, so a Room/SQLite foreign-key exception would be uncaught and process-fatal.

The device stack trace must confirm or reject this hypothesis before implementation. The expected signature is `SQLiteConstraintException` / `FOREIGN KEY constraint failed` through `UiSessionDao_Impl.upsertUiSessionState`, `RoomUiSessionRepository.saveUiSessionState`, and `LibraryViewModel.saveSessionState`.

### Layout finding

`LibraryScreen` already sets `singleLine = true`, but its long placeholder wraps in the observed narrow effective text width and produces an unusually tall field. The implementation should first record device font/display scaling, then use a shorter visible prompt such as **Search library** with the detailed search scope supplied through accessibility semantics. It must not impose a fixed height that clips large text.

## Evidence needed from the failing build

Collect the following before source implementation. None of it requires copying media or revealing filenames, paths, tags, or content URIs.

### 1. Crash traces for both triggers

For each reproduction, clear Logcat, reproduce once, and save from roughly five seconds before the interaction through process death:

1. Open the completed scan, tap **Go to Library**, tap the search field, and enter one character if focus alone does not fail.
2. Relaunch, return to Library, and select Albums or All Tracks.

Capture `AndroidRuntime`, `SQLiteLog`, `Resn8Onboarding`, `Resn8Indexer`, and the full `Caused by` chain. Android Studio's Logcat export is sufficient. If using ADB, first confirm the physical device with `adb devices -l`, use `adb logcat -c`, reproduce, then export `adb logcat -d -v threadtime` immediately. Do not reduce the output to only the top exception line.

### 2. Privacy-safe database facts

Use Android Studio Database Inspector against the same app install and record only:

- The opaque `collections.id` and count of collections.
- The opaque `root_sources.id`, its `collectionId`, and `lastScanStatus`.
- Whether row `id = 1` exists in `ui_session_state`; its `selectedCollectionId`, `selectedSourceId`, `currentRoute`, `activeSurface`, and only the length of `activeSearchQuery`.
- `media_files` count grouped by the root's collection ID and availability.
- Artist and album group counts for that same collection ID.

Do not export `treeUri`, `documentUri`, relative path, filename, title, artist, album, or raw database files for this diagnosis.

### 3. Reproduction environment

Record app commit/version, device model, Android/API version, orientation, display size setting, font size setting, whether the app was reinstalled or upgraded over an existing database, and whether either crash happens immediately on tap or only after a character/state change.

### 4. One-track admission discrepancy

The current privacy-safe aggregate logs cannot identify why one expected file changed buckets. Add diagnostics only during implementation unless existing Logcat already has sufficient reason totals. The preferred evidence is aggregate admission rejection counts grouped by stable reason plus normalized MIME/extension category, never filename or path. Candidate reasons include provider-declared unsupported MIME overriding a supported extension, confirmed zero size, AppleDouble sidecar, unsupported extension/MIME, or malformed required provider fields.

Re-run the read-only PC audit after the Android scan and compare the 8,901 file count, 896 folder count, and structure fingerprint `bbe283d186fe55db23b2b992921556196af3f800f8305a4da082bf1195813c05`. This distinguishes an Android classification difference from a source change without reading or modifying media contents.

## Implementation plan

### Phase 1: make active collection resolution authoritative

1. Remove literal/profile-name collection IDs from post-onboarding execution paths. `CollectionProfile.MUSIC` describes behavior; it is not a database identity.
2. Add a single application/domain-level active-collection resolver backed by `UiSessionState.selectedCollectionId`, with an MVP fallback to the sole persisted collection only when the session has no valid selection. Never silently fall back when multiple collections exist.
3. After onboarding creates the collection and root, persist their real UUIDs as `selectedCollectionId` and `selectedSourceId`. Keep `currentRoute` as onboarding while work is incomplete; update it to Library only when the user chooses **Go to Library**.
4. Carry or resolve the same collection ID for `LibraryViewModel`, artist/album detail routes, folder browsing, queue-start requests, playlists, and the playlist selector. Route defaults must not manufacture `"MUSIC"`.
5. Treat a stale session reference as a recoverable state: clear/re-resolve it if its target was deleted, and show a setup/reselection state when no collection exists.
6. Serialize session mutations through one ViewModel state pipeline or repository update operation so rapid search/surface changes cannot overwrite newer fields with stale `Flow.first()` snapshots.
7. Catch persistence failures at the UI boundary, log the throwable class and stable operation only, and expose a nonfatal retryable state. Do not let session-state persistence crash browsing.

### Phase 2: make Library loading and errors truthful

1. Model active collection resolution as loading, ready, missing/stale, and failure states. Do not start paging with an invented collection ID.
2. Render Paging `LoadState.Loading`, `LoadState.Error`, true empty-library, and search/filter no-match states separately for Artists, Albums, and All Tracks.
3. Confirm artist/album aggregation queries and track paging return the published 6,583 rows for the resolved UUID collection, including unknown artist/album groups.
4. Make folder root resolution use the active source ID from the session/root relationship rather than `getRootSourcesFlow("MUSIC")`.
5. Preserve the current selected surface and filters across navigation without issuing redundant session writes during initialization.

### Phase 3: clarify completion evidence and reconcile the one track

1. Show admitted audio, inspected documents, scanned folders, preferred artwork candidates, ignored non-audio documents, unsupported audio-like files, unreadable branches, and metadata fallbacks with definitions that form understandable populations.
2. Rename the current broad **Unsupported Files** label or split its counter so users do not interpret 1,697 as 1,697 unplayable audio tracks.
3. Make tag/path/unrecognized labels state that metadata-source categories can overlap, or replace them with a mutually exclusive documented classification if that is the intended product metric.
4. Add privacy-safe rejection-reason aggregation to `AudioAdmissionPolicy`/`DocumentTreeScanner`, propagate it additively through versioned `ScanResult`, and retain backward decoding of older scan summaries.
5. Reconcile the single reclassified document using provider MIME/size/admission reason. Change the admission policy only if the file is actually decoder-supported and the existing policy incorrectly rejects it; do not weaken MIME authority merely to force the baseline count.
6. After verification, update `docs/music_library_indexing_readiness.md` with the actual device results, post-scan fingerprint, explanation of the one-track difference, and final Logcat/database evidence.

### Phase 4: correct the search layout without reducing accessibility

1. Shorten the visible placeholder and move detailed scope into content description/supporting semantics.
2. Keep the control single-line at normal font/display settings, a minimum 48 dp interaction target, and visually aligned with the filter action.
3. At large font scales, allow an intentional adaptive layout (for example, moving the filter action below or using a compact search label) rather than wrapping the placeholder unpredictably.
4. Add stable semantics/test tags for search, tabs, loading, errors, and result content.

## Verification plan

### Automated regression tests

1. Add a Room-backed onboarding-to-Library integration test that creates a UUID collection/root, publishes representative media, persists the active selection, constructs the Library state, and verifies non-empty artist/album/track queries.
2. Add a regression test proving search, surface, sort, and filter changes persist the real UUID and never write `"MUSIC"` or violate the `UiSessionState` foreign key.
3. Test missing, stale, and (future-facing) ambiguous multiple-collection resolution explicitly.
4. Test rapid consecutive session mutations so a debounced search update cannot restore an older surface/filter value.
5. Add Paging state tests for initial loading, populated data, true empty collection, no search results, and query error.
6. Add folder and queue-start tests proving they use the same active collection/source UUID as Library.
7. Add scanner-result tests for mutually accountable admission/rejection/artwork totals, additive old-summary decoding, and the privacy-safe rejection-reason categories.
8. Add Compose tests that focus and type into search, switch Artists/Albums/All Tracks, open Folders, and verify no crash plus visible seeded results. Exercise normal and enlarged font scales/narrow width without clipped controls.

Run with Android Studio's bundled JDK:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat compileDebugAndroidTestKotlin
```

### Physical-device acceptance

1. Install the reviewed debug APK without changing the source music tree. Test both an upgrade over the affected database and a clean install/scan.
2. Confirm completion totals and the rejection-reason reconciliation; re-run the PC fingerprint audit afterward.
3. Tap **Go to Library** and confirm Artists populate from the UUID collection. Switch repeatedly among Artists, Albums, All Tracks, Library Folders, the top-level Folders destination, and back.
4. Focus search, type, clear, rotate, background/foreground, change filters/sorts, and relaunch. Confirm no process death and valid session persistence.
5. Confirm album/artist drill-down and starting a track build a queue from the same active collection.
6. Verify normal and the device's reported font/display settings; confirm the search control is compact, readable, focusable, and does not clip at supported font scales.
7. Confirm Logcat has no `AndroidRuntime` fatal exception, foreign-key failure, main-thread Room access, or sensitive library data.

## Acceptance criteria

- The completion result accounts for all source documents with clearly defined counters, and the one-track admission discrepancy has an evidence-backed explanation.
- **Go to Library** opens the indexed UUID collection with visible artist data rather than a literal `"MUSIC"` collection.
- Artists, Albums, All Tracks, Folders, search, sort, and filters operate without a crash and retain one authoritative collection/source selection.
- No post-index route, ViewModel, repository call, playback request, or playlist entry point uses a profile/display-name literal as a persisted collection ID.
- Session persistence failures are nonfatal and visible; stale session references recover deterministically.
- Loading, true empty, no-match, and query-error states are distinguishable.
- Search is compact at normal settings and remains accessible at large font scales.
- Automated verification and the API 34+ physical-device workflow pass without modifying source media.

## Review gate

Before implementation begins, confirm this plan:

- [ ] Treats the blank Library as an active-collection handoff defect, not a failed scan publication.
- [ ] Keeps the foreign-key crash as a high-confidence hypothesis until the device stack trace confirms it.
- [ ] Replaces every relevant `"MUSIC"` identity seam, not only the first Library query.
- [ ] Preserves `UiSessionState` as the active selection authority and does not conflate profile with identity.
- [ ] Explains why 1,697 broad unsupported documents is not comparable to five unsupported audio-like files.
- [ ] Reconciles the one-track difference without logging or copying user media details.
- [ ] Adds post-indexing integration, session-concurrency, Paging-state, Compose interaction, and physical-device coverage.
- [ ] Defers all implementation and documentation-status updates until this plan is approved.
