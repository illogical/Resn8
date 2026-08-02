# AGENTS.md — Agent Guidelines & Technical Instructions for Resn8

This document defines core expectations, architectural invariants, and workflow procedures for AI agents working on the Resn8 repository.

---

## 1. Project Intentions, Progress, & Core Documents

When initiating or continuing work on Resn8, agents MUST review the following authoritative documents to understand project goals, architectural constraints, and current progress:

- [README.md](file:///c:/LocalDev/Projects/Resn8/README.md) — Product vision, local-first principles, technology stack, getting started, and overall roadmap.
- [docs/SPECIFICATION.md](file:///c:/LocalDev/Projects/Resn8/docs/SPECIFICATION.md) — **Normative** technical specification detailing domain contracts, Room schema semantics, queue occurrence rules, playback expectations, and restoration requirements.
- [docs/BRAINSTORM.md](file:///c:/LocalDev/Projects/Resn8/docs/BRAINSTORM.md) — Exploratory concepts and design intent (the specification takes precedence when in conflict).
- [docs/TASKS.md](file:///c:/LocalDev/Projects/Resn8/docs/TASKS.md) — Ordered implementation backlog. Tasks are completed top-to-bottom within milestones.
- [docs/UX.md](file:///c:/LocalDev/Projects/Resn8/docs/UX.md) — User stories and manual on-device UX verification workflows.

---

## 2. Milestone Iteration Workflow

For every milestone or major feature iteration, agents MUST follow this systematic workflow:

### Step 1: Create Feature Implementation Plan
- Before writing source code, create or update a dedicated implementation plan in `docs/features/milestone_X_<feature_name>.md`.
- Reference checked-in baseline implementations, Room schema contracts, and downstream milestone boundaries.

### Step 2: Implementation Guidelines
- **Single Player Ownership**: `Resn8MediaService` is the ONLY owner of `ExoPlayer` and `MediaSession`. Activities, ViewModels, and Composables MUST interact through the application-scoped `PlaybackConnection` / `MediaController`. Never instantiate or release ExoPlayer in Activities or ViewModels.
- **Active Queue Selection**: Active queue resolution MUST be driven by `UiSessionState.activeQueueId`, not simply the most recently updated queue in Room.
- **Occurrence Identity**: Each item in a queue has a unique, stable `queueItemId` distinct from its underlying `mediaId`. Duplicate tracks in a queue MUST maintain distinct occurrence identity for rating and history tracking.
- **No Destructive Database Fallback**: Room database migrations MUST be explicit and non-destructive. Never enable `fallbackToDestructiveMigration()`.
- **Local-First & Storage Access Framework**: Read audio files using SAF persisted content URIs (`ACTION_OPEN_DOCUMENT_TREE`). Do not require broad file storage permissions.

### Step 3: Verification Procedures
On Windows PowerShell, always set `JAVA_HOME` to Android Studio's bundled JDK before executing Gradle commands:

```powershell
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat testDebugUnitTest
.\gradlew.bat lintDebug assembleDebug
```

- Run unit tests to verify domain and repository logic.
- Run `lintDebug` and `assembleDebug` to verify static checks and APK compilation.

### Step 4: Maintenance & Documentation Updates
Upon completing milestone exit criteria, agents MUST:
1. Update [docs/TASKS.md](file:///c:/LocalDev/Projects/Resn8/docs/TASKS.md) to check off completed tasks (`[x]`).
2. Update [README.md](file:///c:/LocalDev/Projects/Resn8/README.md) roadmap status to check off completed milestones (`[x]`).
3. Update [docs/UX.md](file:///c:/LocalDev/Projects/Resn8/docs/UX.md) with any new manual verification workflows for on-device testing.
4. Produce a walkthrough summary for the user detailing changes made, automated test output, and manual verification steps.

---

## 3. Quick Reference for Environment & Tools

| Action | Command / Location |
| --- | --- |
| JDK Path (Windows) | `C:\Program Files\Android\Android Studio\jbr` |
| Run Unit Tests | `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat testDebugUnitTest` |
| Build & Lint | `$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"; .\gradlew.bat lintDebug assembleDebug` |
| Feature Plans | `docs/features/milestone_X_<name>.md` |
| Task Backlog | `docs/TASKS.md` |
| User Stories / UX | `docs/UX.md` |
