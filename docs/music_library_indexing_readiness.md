# Music Library Indexing Readiness and First-Collection Findings

Date: 2026-08-02

## Purpose and safety boundary

This document records the implementation completed to prepare Resn8 for its first real collection, the read-only analysis of:

`This PC\TCL NXTPAPER 70 Pro\Internal shared storage\Music`

The source library is read-only throughout this work. Neither the PC audit nor the Android indexing code creates, renames, moves, edits, or deletes anything in the selected tree. Android persists only `FLAG_GRANT_READ_URI_PERMISSION`. Database staging, scan summaries, audit reports, and artwork caches live in app-owned or PC-local storage.

No music or artwork samples were copied during the audit. The only generated audit artifacts are under the gitignored `local-audits/music/` directory.

## Music library findings

The Windows MTP namespace became accessible after the earlier empty-device result, and a complete recursive audit succeeded in about 12 seconds.

### Inventory

| Measure | Result |
| --- | ---: |
| Folders | 896 |
| Files of all types | 8,901 |
| Maximum relative depth | 4 |
| Audio admitted by the implemented policy | 6,584 |
| Unsupported/audio-like exclusions | 5 |
| Preferred external artwork candidates | 621 |
| Files with a usable size reported by Windows MTP | 0 |

Windows Explorer's MTP objects did not provide file sizes for any item, so the PC audit cannot independently confirm the estimated 70 GB total. Android's Storage Access Framework may provide sizes; the scanner preserves unknown size as `-1` and distinguishes it from a confirmed zero-byte file.

The baseline structure fingerprint is:

`bbe283d186fe55db23b2b992921556196af3f800f8305a4da082bf1195813c05`

This SHA-256 value is calculated from sorted relative folder and file paths. Re-running the audit after Android indexing should produce the same value, demonstrating that the source structure was unchanged.

### File formats

| Extension | Count | Indexing treatment |
| --- | ---: | --- |
| `.mp3` | 6,508 | Admit, except four `._*.mp3` AppleDouble sidecars |
| `.m4a` | 78 | Admit |
| `.flac` | 2 | Admit |
| `.wma` | 1 | Exclude because it is outside the initial Media3 admission set |
| `.jpg` | 2,104 | Never index as audio; inspect only as possible artwork |
| `.png` | 14 | Never index as audio; inspect only as possible artwork |
| `.zip` | 109 | Exclude |
| `.wpl` | 26 | Exclude; playlist import is not part of indexing |
| Other documents, archives, videos, metadata, and temporary files | 59 | Exclude |

The five audio-like exclusions are four AppleDouble metadata sidecars and one WMA track:

- `Bass/._Flute (Clark Kent & Dead Robot Remix).mp3`
- `Bass/._Out of My Mind (Clark Kent Remix).mp3`
- `Bass/._Work by Iggy Azalea (Clark Kent & Jauz Remix) - TrapMusic.NET Premiere.mp3`
- `Dizzee Rascal/._Dizzee Rascal - Bassline Junkie.mp3`
- `Yellowcard/When_Were_Old_Men.wma`

Ordinary dot-prefixed audio remains eligible. Only the recognized `._` AppleDouble sidecar convention is explicitly rejected.

### Naming and folder conventions

| Filename classification | Count |
| --- | ---: |
| No recognized numeric prefix | 3,707 |
| Track prefix such as `01 - Song` | 1,990 |
| Separated disc/track prefix such as `1-01 Song` | 873 |
| Compact disc/track prefix such as `101 Song` | 14 |

The library contains all layouts the fallback parser needs to support:

- Root-level tracks with no artist or album path.
- `Artist/Album/Track` directories.
- One-directory collections where the directory is best treated as an album.
- Multidisc album names and disc/track filename prefixes.
- Mashups, live recordings, punctuation, underscores, bracketed featured artists, explicit markers, and Unicode characters.

Valid embedded tags must therefore remain authoritative per field. Folder inference is useful for the conventional `Artist/Album` majority but cannot safely replace tags for root-level tracks, compilation-style directories, mashups, or loosely organized folders.

### Artwork

The tree contains 2,118 visible JPEG/PNG files. Of those, 621 use preferred conventional names such as `Folder.jpg`, `Cover.jpg`, or `cover.jpg`; examples occur at root, artist, and album levels. This is a strong basis for external album art.

The implemented selection order is deterministic: `cover`, `folder`, `front`, `album`, then `albumart`, with JPEG preferred over PNG and WebP when names are otherwise equivalent. The remaining images are not automatically treated as album covers because arbitrary image filenames could represent booklets, back covers, artist photos, or unrelated material.

## Implemented changes

### Read-only PC audit

- Added `tools/audit-mtp-music.ps1` to traverse the Windows Shell/MTP namespace without treating the Explorer label as a filesystem path.
- Added aggregate extension, naming, folder-depth, unsupported-audio, and artwork reporting.
- Added a structure fingerprint for before/after source verification.
- Added explicit diagnostics for a locked phone, disabled File Transfer mode, or missing MTP authorization.
- Added an opt-in bounded local sample-copy mode. It is disabled by default and can only copy from the phone into `local-audits/music/samples/`; it has no source-write operation.
- Added `/local-audits/` to `.gitignore`.

### Admission, traversal, and metadata

- Added one centralized `AudioAdmissionPolicy` shared by scanner behavior and tests.
- Supported MIME types are authoritative. Extension fallback is allowed only when the provider MIME type is blank, generic, or unknown.
- Confirmed zero-byte files, AppleDouble sidecars, images, playlists, archives, videos, and specifically unsupported MIME types are excluded.
- Unknown file size is retained as unknown rather than misclassified as zero.
- Scanner traversal now detects repeated directory IDs, keeps ordinary hidden audio eligible, counts inspected/unsupported/artwork/error categories, and treats root access failures as fatal without publishing an empty library.
- Metadata extraction remains read-only and records failed extraction accurately while retaining filename/path fallbacks.
- Embedded artwork bytes are not read during indexing.

### Large-library staging and identity

- Removed full staged-folder, staged-track, and canonical-media object graphs from `ScanOrchestrator`.
- Added paged Room staging reads and indexed canonical lookups for provider ID, URI, relative path, and complete unique signature.
- Added deterministic stable folder IDs based on source ID and relative path.
- Added one-to-one media identity claiming and preservation of first-indexed time, play count, last-played time, rating, and existing app-owned artwork URI.
- Added transactional missing-file availability updates, terminal scan counts, root summary publication, and staging cleanup.
- A newly started scan marks any abandoned active scan `INTERRUPTED` and removes only that abandoned scan's app-owned staging rows.
- Kept the prior canonical snapshot visible until complete publication.

### Durable execution, timing, and logs

- Added one unique foreground WorkManager job per root source, declared as `dataSync` work.
- Added a progress notification, Cancel action, notification-permission request, process-interruption recovery, and UI observation across activity recreation.
- Expanded `ScanProgress` and the additive/versioned `ScanResult` with folder, inspected-document, audio, unsupported, unreadable, metadata-failure, and artwork-candidate counts.
- Added a live elapsed timer and formatted completion duration to onboarding.
- Added aggregate `Resn8Indexer` Logcat milestones for start, periodic progress, completion, cancellation, and failure.
- Logs contain only opaque scan/source IDs, aggregate counts, failure category, and elapsed time. They never include filenames, relative paths, tags, tree URIs, or exception dumps.

### Artwork preparation

- Added a small app-owned artwork-candidate index written only after successful canonical publication.
- External preferred artwork is copied lazily and read-only into the app cache when a track row needs it.
- Embedded artwork is the lazy fallback when no preferred external image exists.
- Artwork reads are capped at 20 MiB and use atomic app-cache writes.
- The source image/audio and the selected `Music` tree are never modified.

## Verification completed

- The read-only MTP audit completed successfully with no sample-copy option.
- Source inspection found no Android write grant, document creation, deletion, rename, move, or output-stream calls against the selected provider.
- Room/KSP generation and merged manifest processing passed.
- Main Kotlin compilation passed after the other session completed its concurrent playback changes.
- Current generated test results show 47 tests across 17 suites with zero failures, including:
  - Audio admission edge cases and AppleDouble rejection.
  - Additive decoding of older persisted scan-result JSON.
  - Stable staged publication and re-index identity.
  - Deterministic artwork candidate selection.
  - A 25,000-row staged publication benchmark, which completed in approximately 50 seconds under Robolectric.
  - Existing database, browsing, queue, playlist, migration, and playback suites.

The worktree also contains substantial concurrent Milestone 4 playback changes. Indexing changes must be reviewed and committed without discarding or overwriting that unrelated work.

## Remaining work and recommended next plan

1. **Refresh verification after the final metadata-extractor cleanup.** Re-run `testDebugUnitTest`, `lintDebug`, `assembleDebug`, and `compileDebugAndroidTestKotlin` with Android Studio's bundled JBR. Avoid simultaneous Gradle sessions because earlier runs encountered transient locks on generated `R.jar` and `classes.jar`.
2. **Connect the physical phone through ADB.** MTP now works, but earlier ADB discovery showed only an offline emulator. Enable USB debugging, authorize this PC, and confirm the TCL device with `adb devices -l`.
3. **Run the first Android onboarding scan.** Select `Internal shared storage/Music` in the system document picker and name the collection `Music`. Do not grant or request write access.
4. **Capture real indexing evidence.** Record the completion duration, admitted audio count, unsupported count, unreadable/metadata-failure count, artwork-candidate count, WorkManager notification behavior, and aggregate `Resn8Indexer` Logcat output.
5. **Validate lifecycle behavior.** During controlled retry scans, test rotation, backgrounding, screen lock, cancellation, process interruption, and unchanged re-indexing. Confirm the previous canonical library remains intact after every cancelled or failed run.
6. **Inspect metadata quality.** Compare representative root-level, `Artist/Album`, multidisc, mashup, compilation, M4A, and FLAC entries with their source naming and embedded tags. Add parser cases only for patterns demonstrated by the real results.
7. **Validate artwork presentation.** Confirm conventional external covers and embedded fallback display on track rows. Album and artist summary queries still depend on persisted `artworkUri`; decide in a future artwork-specific slice whether cached representative URIs should be persisted or exposed through a dedicated artwork state/repository.
8. **Prove source immutability.** Re-run the PC audit after the device scan and compare the folder/file counts and structure fingerprint with the baseline above. Any mismatch must be investigated before indexing is considered read-only verified end to end.
9. **Record the device acceptance result.** Update this document with the actual scan timing, Android provider counts, metadata/artwork samples, ADB/Logcat evidence, and post-scan fingerprint.

## Commands for the next session

```powershell
# Read-only PC audit; writes only a gitignored local report.
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\audit-mtp-music.ps1

# Verification using Android Studio's bundled JBR.
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
.\gradlew.bat compileDebugAndroidTestKotlin

# Physical-device discovery and privacy-safe indexing logs.
& "$((Get-Content local.properties | Where-Object { $_ -like 'sdk.dir=*' }).Substring(8).Replace('\:', ':'))\platform-tools\adb.exe" devices -l
adb logcat -s Resn8Indexer
```

Do not run the audit with `-CopySamples` unless a later investigation explicitly requires local sample copies. Never use MTP or Android APIs that request write access to the source library.
