# Resn8

**Your library. Your listening history. Your next great queue.**

Resn8 is an offline-first Android audio player for people who keep their own music and audio files. Point it at a folder on internal storage or an SD card, and Resn8 will turn that library into something easy to browse, play, rate, organize, and rediscover without uploading your media or listening history.

> [!IMPORTANT]
> Resn8 is a working early-stage Android application through Milestone 7: local indexing and browsing, persisted explicit queues, Media3 background playback, ratings/meaningful-play accounting, manual playlists, and full playback/browsing context restoration are implemented. Smart queue generation and final MVP polish remain incomplete, so the feature lists below describe the intended finished MVP rather than the current release.

## Why Resn8?

Large personal libraries have a discovery problem: familiar tracks keep resurfacing while unheard files disappear into folders. Resn8 is designed to remember how you actually listen and use that history to build better queues.

- **Bring your own library.** Select an organized music folder with Android's system picker—no broad storage permission required.
- **Keep everything local.** Audio, metadata, ratings, playlists, and listening history stay on your device.
- **Rediscover what you own.** Generate queues that surface unplayed or least-played tracks, prioritize favorites, or simply shuffle eligible files.
- **Shape recommendations directly.** Like and Dislike actions adjust a durable signed score; disliked tracks are excluded from newly generated smart queues by default.
- **Resume without rebuilding context.** Restore the exact queue, track, position, filters, and screen after the app or process restarts.

## Planned MVP

The first usable release is focused on one organized music collection beneath one selected root folder.

### Browse and index

- Recursively index playable audio from internal shared storage or removable SD cards.
- Read embedded metadata such as MP3 ID3 tags, with sensible `Artist/Album/Track` path and filename fallbacks.
- Browse by artist, album, folder, or all tracks.
- Re-index safely while preserving ratings, play counts, history, and playlist membership.
- Keep missing or temporarily unavailable files represented instead of silently discarding their data.

### Listen and remember

- Play in the foreground or background with notification, lock-screen, headset, and Bluetooth controls.
- Track meaningful plays based on active listening—not seek position alone.
- Rate tracks with an incrementing/decrementing score instead of a single binary favorite.
- Restore the active queue and listening position in a safe, paused state on normal relaunch.

### Organize and rediscover

- Create durable, manually ordered playlists.
- Add one track, multiple selected tracks, or every indexed track beneath a folder through one reusable playlist selector.
- Generate reproducible smart queues from the currently visible library scope:
  - random eligible tracks;
  - unplayed tracks;
  - least- or most-played tracks;
  - most-liked tracks;
  - recent-listening modes.

Generated queues are saved as explicit snapshots, so a re-index or rating change will not unexpectedly reshuffle what is already playing.

## Product principles

1. **Local first.** Source audio and personal listening data remain on the device.
2. **Your files are the source.** Resn8 reads through Android's Storage Access Framework and does not modify source audio during normal indexing or playback.
3. **Listening data should be trustworthy.** Ratings are atomic, plays have a clear qualification rule, and seeking cannot manufacture play counts.
4. **Queues should be explainable.** Smart modes have deterministic eligibility and ordering rules, with seeded randomization for ties.
5. **Accessibility is foundational.** Core actions remain visible and usable without relying on hidden gestures, color alone, or one screen orientation.

## Technical direction

Resn8 is a native Android application built with:

- Kotlin and Jetpack Compose
- AndroidX Media3, ExoPlayer, and `MediaSessionService`
- Room for local relational persistence
- Coroutines and Flow
- Android's Storage Access Framework and persisted content-URI permissions
- Layered UI, playback, domain, persistence, and storage/indexing components

The architecture targets responsive libraries of at least 25,000 indexed files, a single authoritative playback service, idempotent background indexing, testable queue algorithms, and non-destructive database migrations.

## Getting started

### Requirements

- Android Studio with support for Android Gradle Plugin 9.3.1
- Android SDK 37
- An Android 14 / API 34 or newer emulator or device

The Gradle wrapper is included, and the configured Foojay resolver can provision the project's Java 25 daemon toolchain.

### Build and test

On macOS or Linux:

```sh
./gradlew assembleDebug
./gradlew test
```

On Windows:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

To exercise storage access, media playback, and removable-storage behavior, run the app from Android Studio on an API 34+ emulator or physical device. Some acceptance tests require a real device or suitable document provider.

## Project status and roadmap

Implementation is organized into dependency-ordered milestones:

1. [x] **Milestone 0**: application foundation and domain contracts
2. [x] **Milestone 1**: durable library persistence
3. [x] **Milestone 2**: folder selection and indexing
4. [x] **Milestone 3**: library and folder browsing
5. [x] **Milestone 4**: reliable background playback
6. [x] **Milestone 5**: ratings and meaningful-play tracking
7. [x] **Milestone 6**: manual playlists
8. [x] **Milestone 7**: full queue, playback-position, and browsing-context restoration
9. [x] **Milestone 8**: startup restoration and index-completion feedback corrections
10. [ ] **Milestone 9**: smart randomized queues
11. [ ] **Milestone 10**: accessibility, adaptive-layout, acceptance, and release polish

Post-MVP goals include multiple collections and source roots, contextual and flat-folder library profiles, richer use of indexed album artwork across library surfaces, saved dynamic smart playlists, scheduled indexing, playback-speed memory, global command search, safe disliked-file maintenance, and metadata/history backup and restore. The Milestone 7 Settings shell is intended to become the home for these later user-configurable capabilities.

For the complete product decisions and acceptance criteria, see:

- [Product specification](docs/SPECIFICATION.md)
- [Implementation tasks](docs/TASKS.md)
- [UX stories and manual verification](docs/UX.md)
- [Feature implementation plans](docs/features/)
- [Original brainstorm](docs/BRAINSTORM.md)

## Contributing

Resn8 is advancing through dependency-ordered milestones, so the specification, task backlog, UX verification matrix, and relevant feature plan are the best starting points for proposed changes. Before implementing a feature, check its completed baseline and milestone prerequisites and preserve the MVP boundaries documented in the specification.

Bug reports, design feedback, test-library edge cases, and focused pull requests are welcome.
