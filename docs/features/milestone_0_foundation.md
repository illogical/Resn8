# Milestone 0 Implementation Plan: Establish the Foundation

This plan covers the initial setup for Resn8, ensuring the architecture is ready for the core features of audio indexing and playback.

## Proposed Changes

### T001 — Pin architecture dependencies
- **libs.versions.toml**: Add stable versions for:
  - Media3 (ExoPlayer, Session)
  - Room (Runtime, KSP, KTX)
  - Navigation Compose & Kotlinx Serialization
  - Lifecycle ViewModel Compose
- **app/build.gradle.kts**: Apply KSP and Serialization plugins; add library dependencies.

### T002 — Create the application layers
Establish the following package structure under `com.app.resn8`:
- `di`: Manual dependency container (`AppContainer`).
- `domain.model`: Pure Kotlin data classes.
- `domain.repository`: Interfaces for data access.
- `data.database`: Room Database and DAOs.
- `data.repository`: Implementations of domain interfaces.
- `playback`: `MediaSessionService` and playback logic.
- `ui`: Navigation, theme, and screen-specific packages.

### T003 — Define domain contracts
Completed core domain contracts matching `SPECIFICATION.md`, including collection profiles, media metadata, filter/sort definitions, rating and meaningful-play rules, queue-generation modes, saved queue state, scan progress/results, and repository interfaces:
- `MediaFile`: The central audio record.
- `RootSource`: Folder access metadata.
- `Collection`: Logical grouping using `MUSIC`, `CONTEXTUAL`, and `FLAT` profiles.
- `Playlist` & `PlaylistItem`.
- `SavedQueue`: Durable playback state.

### T004 — Add navigation and app shell
- Implement `Resn8App` Composable with a `Scaffold`.
- Add `Resn8NavHost` using typed routes (Serialization).
- Destinations: Onboarding, Library (Artists/Albums/Tracks), Folders, Playlists.
- Implement a persistent `MiniPlayer` slot above the bottom navigation.

### T005 — Establish test fixtures
- Add `test` utilities for generating deterministic `MediaFile` objects.
- Create a scratch script to generate small silent `.mp3` files with varying ID3 tags to verify the scanner.

## Verification Plan

### Automated Tests
- Run `./gradlew assembleDebug` to confirm dependency resolution.
- Run unit tests for `AppContainer` initialization.
- Verify `Resn8NavHost` destination mapping via Compose UI tests.

### Manual Verification
- Deploy to emulator/device.
- Confirm placeholder screens show correctly when navigating.
- Verify the `MiniPlayer` remains visible across navigation events.
