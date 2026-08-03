# Optional Next-Index Diagnostic Capture Plan

## Status and activation gate

This is an **optional contingency plan**. It is not part of the current post-indexing library handoff implementation and must not be implemented unless a subsequent physical-device index still produces an empty library, crashes during Library interaction, fails to begin playback, or otherwise leaves insufficient evidence for diagnosis.

The next index should first be exercised normally. Activate this plan only after the user explicitly requests it in response to another problematic index.

## Objective

Capture enough privacy-safe evidence from one complete indexing and Library journey to reproduce structural failures in automated tests, while retaining no music, raw Room database, artwork, filenames, paths, SAF URIs, titles, artists, albums, or other library metadata.

The diagnostic output has two tiers:

1. A gitignored, sanitized structural capture retained locally until the failures are resolved and verified.
2. A compact deterministic recipe derived from structural distributions and relationships, suitable for generating a checked-in synthetic library fixture.

The private capture is never committed. Only the synthetic recipe and fixture support code may become repository artifacts if this plan is later authorized and implemented.

## One-shot diagnostic journey

The capture covers one bounded journey:

1. Arm diagnostics before selecting or rescanning the music folder.
2. Run indexing to a terminal success or failure state.
3. Select **Go to Library**.
4. Visit Artists, Albums, All Tracks, and Folders.
5. Focus the search field and enter a query.
6. Clear or change the query and switch tabs repeatedly.
7. Select one track and construct its playback queue.
8. Continue until the media service reports that the track is playing, the app crashes, the user explicitly ends capture, or the capture expires.

The diagnostic state machine is:

`Disabled -> Armed -> Indexing -> LibraryVerification -> Complete | Expired`

State survives process crashes so a crash does not erase or prematurely finish the capture. A successful run completes only after one track reaches a confirmed playing state. The one-shot capture expires after 30 minutes to prevent accidental long-running logging.

## Privacy and data-handling requirements

The capture may contain only:

- App version, commit, build type, Room schema version, device/API version, display scale, and font scale.
- Scan timings and aggregate admission, rejection, discovery, insert, update, and missing counts.
- Aggregate Room table counts, foreign-key validation results, availability counts, metadata-source distributions, and query result counts.
- Structural artist, album, folder, media, and queue relationships represented by run-local ordinal aliases.
- Paging states and timings, sanitized user-action breadcrumbs, player states, and complete exception stack traces.

The capture must never contain:

- A raw or copied Room database.
- Audio or artwork content.
- Tree URIs, document URIs, filesystem paths, or filenames.
- Track titles, artist names, album names, folder names, tags, search text, or other user-controlled strings.
- Stable hashes of private strings; dictionary attacks can recover common music metadata.

Collection, source, folder, media, and queue identifiers are replaced with stable aliases scoped to the diagnostic run. Synthetic output uses generated labels and `content://seed/...` URIs that cannot resolve to the user's files.

## Proposed diagnostic implementation

### Debug-only capture controls

- Add a debug-only receiver or equivalent ADB-accessible control to arm, cancel, inspect, and finish the next capture.
- Exclude the control, export surface, diagnostic preference, and temporary logging implementation from release builds.
- Persist capture lifecycle independently of the indexed library so an application crash can be correlated after restart.
- Flush structured events incrementally; do not wait for graceful completion before writing evidence.

### Structured evidence

Record privacy-safe events around:

- Scan start, progress, admission decisions, database transaction completion, and terminal result.
- Active collection/source selection and restoration, including whether each aliased selection resolves to an existing row.
- Reconciliation between admitted tracks, persisted media, available media, and collection-scoped query totals.
- Paging loads for Artists, Albums, All Tracks, and Folders, including load type, result count, duration, and sanitized exception.
- Search focus and query-length changes without recording the query text.
- Tab-selection events immediately before the observed navigation crashes.
- Queue construction using distinct queue occurrence aliases and media aliases.
- `MediaController` commands, media-service preparation, file-open outcome, player state changes, playback errors, and the first confirmed playing state.

AndroidRuntime stack traces remain complete. Only message fragments containing user-controlled values are redacted.

### Local collection tooling

Add a PowerShell collector that:

- Requires exactly one connected Android device and a debuggable Resn8 build.
- Arms the one-shot capture and streams filtered Logcat into `local-audits/index-runs/<timestamp>/`.
- Exports only the sanitized app-produced evidence bundle.
- Records enough build and device context to associate the evidence with the tested APK.
- Never runs `pm clear`, uninstalls the app, revokes persisted SAF permissions, reads the raw database, or modifies the selected music source.
- Never invokes `connectedDebugAndroidTest` against the data-bearing physical installation.
- Uses `adb install -r` when an in-place debug APK update is necessary.

The gitignored bundle is retained until the empty-library, interaction-crash, and playback fixes pass the physical-device acceptance journey. Cleanup is an explicit user-authorized step after verification.

## Synthetic replay seed

Derive a compact recipe from the private structural capture rather than committing sanitized row dumps. The recipe preserves:

- Entity totals and relationship cardinality.
- Artist and album group-size histograms.
- Folder depth and fan-out distributions.
- Null, unknown, and fallback-metadata rates.
- Metadata-source and availability categories.
- MIME and duration buckets.
- Sort-key collision and representative text-shape categories, populated with generated values.

Extend the existing large-library fixture to consume the recipe and deterministically generate collections, sources, folders, media, artists, albums, and queue occurrences. Generated IDs, labels, URIs, and media are synthetic. A small bundled test-audio asset may be used for playback tests; the user's audio is never copied into the fixture.

## Verification plan

### Automated tests

- Verify that forbidden fields and user-controlled strings cannot enter the sanitized capture.
- Verify alias stability within one run and unlinkability between runs.
- Verify state persistence across simulated crashes, successful completion, explicit cancellation, and 30-minute expiry.
- Verify deterministic synthetic output from the same recipe.
- Load the generated fixture into an in-memory Room database and confirm admitted totals reconcile with persisted and queryable media totals.
- Confirm every active collection/source alias resolves to a seeded record.
- Confirm Artists, Albums, All Tracks, and Folders return nonempty first pages.
- Exercise search focus, filtering, query clearing, and repeated tab changes without exceptions.
- Confirm albums remain collection-scoped and folder relationships remain valid.
- Build a queue from a seeded track while preserving distinct queue occurrence identity.
- Exercise the `PlaybackConnection`/`MediaController` to `Resn8MediaService` path with a test audio asset; the service remains the sole owner of ExoPlayer and MediaSession.

### Physical-device acceptance

Perform the complete one-shot journey on the data-bearing device without running connected-test cleanup. Success requires:

- Index totals reconcile with persisted and collection-scoped query counts.
- Every Library tab renders indexed content.
- Search can gain focus, accept input, clear input, and be revisited without a crash.
- Repeated tab changes do not crash.
- Selecting a track constructs a queue and produces audible playback.
- The capture automatically completes after confirmed playback.

A crashing run is still a valid evidence capture when external Logcat contains the full stack trace and all structured events flushed before the failure.

## Acceptance criteria for using the seed

This contingency work is complete only when one of the following is true:

1. The synthetic replay reproduces the empty-library, search, tab, queue, or playback failure and supports an automated regression test; or
2. The synthetic replay passes and the evidence identifies a documented device/runtime-only condition that distinguishes the physical failure.

After the corrective implementation, both the automated replay and physical-device one-shot journey must pass. The private diagnostic bundle may then be deleted with explicit user approval; the privacy-safe synthetic regression fixture may remain checked in.

## Non-goals

- Implementing diagnostics before another problematic index.
- Shipping diagnostic capture in production builds.
- Copying or exporting the user's database or music library.
- Replacing the existing post-indexing handoff plan.
- Expanding broad-storage permissions or bypassing persisted SAF access.
