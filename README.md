# Resn8

**Your library. Your listening history. Your next great queue.**

Resn8 is an offline-first Android audio player for people who keep their own music and audio files. Point it at a folder on internal storage or an SD card, and Resn8 will turn that library into something easy to browse, play, rate, organize, and rediscover without uploading your media or listening history.

> [!IMPORTANT]
> Resn8 is a working early-stage Android application with local indexing and browsing, persisted explicit queues, Media3 background playback, ratings/meaningful-play accounting, manual playlists, full playback/browsing context restoration, and playlist Randomized Sorting implemented. Final MVP acceptance and release polish remain incomplete.

## Why Resn8?

Large personal libraries have a discovery problem: familiar tracks keep resurfacing while unheard files disappear into folders. Resn8 is designed to remember how you actually listen and use that history to build better queues.

- **Bring your own library.** Select an organized music folder with Android's system picker—no broad storage permission required.
- **Keep everything local.** Audio, metadata, ratings, playlists, and listening history stay on your device.
- **Rediscover what you own.** Randomize a playlist by least played, most played, most liked, or recently added while retaining variety among tracks with equal metadata.
- **Shape playback directly.** Like raises a durable score without a product cap, while Dislike lowers it to a minimum of `-1`; applying Randomized Sorting permanently removes disliked tracks from that playlist.
- **Resume without rebuilding context.** Restore the exact queue, track, position, filters, and screen after the app or process restarts.

## Planned MVP

The first usable release supports multiple named collections, each beneath one selected folder. Music collections keep metadata-oriented Library browsing; Audio Files collections provide a filename-oriented Folders experience.

### Browse and index

- Recursively index playable audio from internal shared storage or removable SD cards.
- Read embedded metadata such as MP3 ID3 tags, with sensible `Artist/Album/Track` path and filename fallbacks.
- Browse by artist, album, folder, or all tracks.
- Switch between Music and folder-first Audio Files collections without mixing their playlists or indexed media.
- Re-index safely while preserving ratings, play counts, history, and playlist membership.
- Keep missing or temporarily unavailable files represented instead of silently discarding their data.

### Listen and remember

- Play in the foreground or background with notification, lock-screen, headset, and Bluetooth controls.
- Track meaningful plays after one minute of cumulative active listening or genuine end-of-track completion.
- Rate tracks with an incrementing positive score and a single disliked state at `-1` instead of a binary favorite.
- Restore the active queue and listening position in a safe, paused state on normal relaunch.

### Organize and rediscover

- Create durable, manually ordered playlists.
- Add one track, multiple selected tracks, or every indexed track beneath a folder through one reusable playlist selector.
- Apply Randomized Sorting to a playlist using Least Played, Most Played, Most Liked, or Recently Added. Equal-value tracks are shuffled, disliked memberships are removed, and the resulting order becomes the playlist's durable order.
- If that playlist is currently loaded, replace its explicit playback snapshot at the new first playable track without affecting playback from another source.

## Product principles

1. **Local first.** Source audio and personal listening data remain on the device.
2. **Your files are the source.** Resn8 reads through Android's Storage Access Framework and does not modify source audio during normal indexing or playback.
3. **Listening data should be trustworthy.** Ratings are atomic, plays have a clear qualification rule, and seeking alone cannot manufacture play counts while playback that genuinely reaches the end is recognized.
4. **Randomization should be explainable.** Playlist modes have explicit metadata-group ordering rules with fresh randomization inside exact-value ties.
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
10. [ ] **Milestone 9**: multiple single-folder collections and folder-first audio
11. [ ] **Milestone 10**: accessibility, adaptive-layout, acceptance, and release polish

Post-MVP goals include multiple source roots within a collection, contextual-folder profiles, richer use of indexed album artwork across library surfaces, saved dynamic smart playlists, scheduled indexing, playback-speed memory, global command search, safe disliked-file maintenance, and metadata/history backup and restore. Settings is the home for collection management and later user-configurable capabilities.

For the complete product decisions and acceptance criteria, see:

- [Product specification](docs/SPECIFICATION.md)
- [Implementation tasks](docs/TASKS.md)
- [UX stories and manual verification](docs/UX.md)
- [Feature implementation plans](docs/features/)
- [Original brainstorm](docs/BRAINSTORM.md)

## Contributing

Resn8 is advancing through dependency-ordered milestones, so the specification, task backlog, UX verification matrix, and relevant feature plan are the best starting points for proposed changes. Before implementing a feature, check its completed baseline and milestone prerequisites and preserve the MVP boundaries documented in the specification.

Bug reports, design feedback, test-library edge cases, and focused pull requests are welcome.
