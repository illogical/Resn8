# Milestone 0 Foundation Improvements Plan

## Purpose

This document records improvements identified during review of `milestone_0_foundation.md`. It is a handoff plan for a future coding assistant. Implement these improvements as part of Milestone 0 before beginning database, indexing, or playback feature work.

This plan does not authorize implementation of later milestones. Do not implement the Room schema, folder indexing, real playback, playlists, rating persistence, or smart queue generation here. Milestone 0 should establish contracts, boundaries, test infrastructure, placeholder navigation, and a reproducible build only.

## Required Changes

### 1. Make the build and dependency set reproducible

Update the version catalog and Gradle configuration with exact, mutually compatible stable versions for:

- AndroidX Media3 ExoPlayer and Session
- Room runtime and compiler support through KSP
- Navigation Compose and Kotlin Serialization
- Lifecycle ViewModel Compose and lifecycle-aware state collection
- Kotlin coroutines and coroutine test utilities
- Compose UI/instrumentation test dependencies
- Any architecture-test library selected below

Account explicitly for the repository's existing AGP 9.3.1 built-in Kotlin setup, Kotlin 2.2.10, Gradle 9.5, and configured JDK 25 daemon criteria. Do not add the legacy `org.jetbrains.kotlin.android` plugin. Confirm that the selected KSP and Serialization plugins support the active Kotlin/AGP toolchain.

Record the supported command-line JDK setup in a short development document. The Gradle wrapper currently requires `JAVA_HOME` or `java` on `PATH` before daemon toolchain selection can occur. Use the Android Studio JBR as the documented Windows default when installed, without committing a machine-specific absolute path.

Define the Java and Kotlin compilation targets explicitly and consistently. Keep all dependency aliases and plugin versions centralized in `gradle/libs.versions.toml`. Do not introduce dynamic dependency versions.

### 2. Enforce architectural boundaries

Use a small pure Kotlin `:core:domain` module to make the business model and algorithms independent of Android. This module must not depend on Android SDK, Compose, Room, Media3, or Storage Access Framework types.

Keep Android-specific UI, navigation, storage adapters, persistence implementations, and playback components in `:app` for now. Additional feature modules are not required during Milestone 0.

Use these dependency directions:

```text
:app UI / Android adapters
        |
        v
:core:domain models, rules, and interfaces
```

- Domain models use stable value types or strings for persisted identifiers; they do not expose `android.net.Uri`.
- UI code depends on domain contracts or screen state, not Room DAOs or storage APIs.
- Repository implementations and Android adapters depend on domain interfaces.
- Activities, services, and composables do not become sources of application data.
- Add an automated architecture test or equivalent build-time check that fails if forbidden Android/framework dependencies enter `:core:domain`.

### 3. Define complete domain contracts

Create pure Kotlin contracts covering the concepts already approved in `SPECIFICATION.md`. These are domain contracts, not Room entities.

Required model areas:

- Stable IDs for collections, root sources, folders, media files, playlists, saved queues, and queue occurrences
- `CollectionProfile` values `MUSIC`, `CONTEXTUAL`, and `FLAT`
- `Collection`, `RootSource`, `FolderNode`, and `MediaFile`
- Nullable music metadata separate from presentation fallbacks such as `Unknown Artist`
- Listening statistics, signed like score, playback-history result, and queue occurrence
- `Playlist` and ordered playlist membership
- Saved manual/generated queues and their explicit ordered items
- UI session context needed for later restoration
- Library filters, deterministic sort definitions, and normalized filter snapshots
- Smart queue generation modes and stored generation metadata
- Scan progress, completion summary, cancellation, and typed failure states

Required behavioral contracts:

- Signed score adjustment by `+1` and `-1`
- Meaningful-play qualification: 60 seconds of cumulative active listening for known one-minute-or-longer and unknown-duration tracks, with genuine completion qualifying any duration
- Availability state for removable/revoked sources and media
- Explicit queue order as the authoritative restored order
- Injectable `Clock` and seeded random source abstractions
- Injectable coroutine dispatcher provider at Android/data boundaries

Define repository interfaces by aggregate responsibility rather than one oversized repository. At minimum, separate library/source access, playlist access, listening statistics, and saved queue/session state. Use `Flow` for observable state and `suspend` functions for actions. Do not expose mutable collections or Android framework types.

Do not create placeholder methods whose semantics are not established in `SPECIFICATION.md`. If a later milestone owns an operation, define only the minimum stable interface needed to prevent architectural coupling.

### 4. Constrain manual dependency injection

Implement a small application-scoped `AppContainer` in `:app` with these rules:

- The custom `Application` owns one production container.
- Dependencies use constructor injection; domain classes and reusable composables must not look up the container directly.
- Screen-level ViewModels receive dependencies through explicit factories.
- Expensive components are lazy. Starting the application must not open the production database, enumerate storage, or construct ExoPlayer merely to build the dependency graph.
- Clocks, random sources, coroutine dispatchers, and repositories are replaceable with deterministic fakes.
- The playback service will later obtain its dependencies from the same application-scoped graph without creating a second graph.

For Milestone 0, use placeholder or in-memory implementations where a concrete later-milestone implementation does not yet exist. Name fakes and defaults clearly; do not disguise unfinished implementations as production persistence.

### 5. Complete the typed navigation shell

Replace the starter greeting with `Resn8App`, a top-level scaffold, and a typed navigation graph. Define serializable route objects/classes for:

- Onboarding
- Library landing
- Artists
- Albums
- All Tracks
- Folder Browser
- Playlists
- Playlist Detail with playlist ID
- Queue
- Now Playing

Pass only stable identifiers through routes. Do not pass media objects, database entities, URIs, or large serialized state through route arguments.

Add placeholder screens with stable semantic test tags and accessible labels. Define navigation callbacks at screen boundaries so individual screens do not own or reach into the `NavController` unnecessarily.

Provide a persistent mini-player slot in the app scaffold. Its placeholder visibility must be driven by injectable playback-summary state: hidden when no active item exists and visible when a fake active item is supplied. Do not create Media3/ExoPlayer playback in this milestone.

Define and test the initial route policy through a small abstraction: onboarding when no configured collection exists, otherwise the library. Use a fake setup-state source until collection persistence is implemented.

### 6. Establish deterministic test infrastructure

Replace the generated example tests with useful tests. Prefer fakes over mocking frameworks.

Create reusable test support for:

- Deterministic domain model builders with explicit defaults
- Fake repositories for each repository contract
- Fake `Clock`
- Seeded/fake random source
- Standard and test coroutine dispatchers
- Fake configured/unconfigured application state
- Nested document-tree descriptions that do not require Android storage APIs in local tests

Create license-safe audio fixtures for later scanner/metadata tests:

- Tagged and untagged short audio
- Missing and partial metadata
- Common filename patterns
- Corrupt content
- Unsupported content
- Duplicate filenames in different relative folders

Use either checked-in tiny generated fixtures with documented provenance or a checked-in deterministic generator. Do not rely on an undocumented scratch script, copyrighted media, network access, or an undeclared FFmpeg installation during tests. Record each fixture's expected metadata and a checksum so accidental changes are visible.

## Required Unit and UI Tests

### Pure JVM unit tests

- Every strong ID/value type has correct equality and invalid-input behavior.
- Collection profiles serialize or map without conflating Podcasts with a profile; Podcasts is a possible contextual collection.
- Signed score adjustment crosses zero correctly and does not mutate the input model.
- Meaningful-play thresholds are correct for short, long, zero/invalid, and unknown durations.
- Filter snapshots normalize consistently and preserve all future queue-generation inputs.
- Saved queue models preserve explicit item order, repeated media occurrences where allowed, index, and position.
- Fake clocks and random sources produce repeatable results.
- Repository fakes expose immutable observable state and deterministic mutations.
- The application dependency graph accepts fakes and does not eagerly create expensive components.
- Architecture checks prove `:core:domain` has no Android, Compose, Room, Media3, or storage-framework dependencies.

Do not implement or test the final smart-queue algorithms during Milestone 0; only validate their mode and input/output contracts. Algorithm behavior belongs to Milestone 7.

### Compose/instrumentation tests

- The configured and unconfigured states choose Library and Onboarding respectively.
- Every declared route resolves to the correct placeholder screen.
- Playlist Detail route arguments survive serialization/deserialization and activity recreation.
- Forward navigation and Back return to the expected destinations.
- The mini-player is hidden without an active item and remains visible across navigation when fake playback state is active.
- Placeholder screens expose stable semantics and meaningful accessibility labels.
- The test application/container can replace production dependencies without global state leaking between tests.

## Verification Commands

The future coding assistant must run and report all applicable commands from the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug
.\gradlew.bat assembleDebug
.\gradlew.bat assembleDebugAndroidTest
```

When an emulator or device is connected, also run:

```powershell
.\gradlew.bat connectedDebugAndroidTest
```

Run the combined build/test command twice and confirm the second invocation reuses the Gradle configuration cache without new warnings attributable to the milestone changes. If the environment cannot run connected tests, compilation of the instrumentation APK remains mandatory and the handoff must identify the unexecuted device tests explicitly.

Do not claim success from `assembleDebug` alone. Report the number of local and connected tests executed, any skipped tests, lint findings, and the exact JDK used.

## Completion Criteria

Milestone 0 foundation improvements are complete only when:

- The build is reproducible from the documented command-line setup with exact dependency versions.
- `:core:domain` compiles and runs unit tests without Android dependencies.
- All approved domain concepts have stable, documented contracts without prematurely implementing later features.
- Production and fake dependencies can be supplied through explicit constructor/factory injection.
- All typed placeholder destinations are reachable and their route behavior is covered by tests.
- Mini-player placement and visibility behavior are verified without creating a real player.
- Generated template tests have been replaced by meaningful domain, graph, navigation, and UI tests.
- Unit tests, lint, debug assembly, and instrumentation-test assembly pass; connected tests pass when a device is available.
- No Room schema, storage scanner, MediaSessionService, ExoPlayer, playlist feature, or smart-generation algorithm has been implemented early.

## Handoff Expectations

The implementing assistant should preserve unrelated working-tree changes, update `milestone_0_foundation.md` or `TASKS.md` only when needed to keep completed work and verification status accurate, and provide a final list of changed files plus test/build evidence. Any necessary departure from these boundaries must be raised before implementation rather than silently expanding Milestone 0.
