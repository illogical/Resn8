# T057 Implementation Plan: Collection Metadata Backup and Restore

## Goal

T057 adds a thoughtful **Backup & Restore** workflow under Settings. Users can save a portable, integrity-checked JSON backup of any selected combination of collections and later restore selected collections from that file. The backup protects Resn8 metadata—not source audio—and keeps playlists, ratings, play statistics, listening history, saved queues, indexed descriptive metadata, and applicable settings available while the user's music folders are reconnected.

## Checked-in baseline

- Room version 7 is the source of truth for collections, one SAF source per collection, folders, media metadata and statistics, playback history, playlists, saved queues, per-collection playback pointers, and global UI session state.
- Re-indexing already preserves stable media identity and user data while matching by document identity, relative path, then a unique complete file signature.
- Missing files are retained as unavailable, which provides the correct restoration model for imported metadata before SAF permissions are re-established.
- Settings currently contains Collections and About pages. It already owns folder selection/reselection and indexing progress, but has no backup service or document creation/opening actions.

## Portable backup contract

1. Define serializable backup DTOs independent from Room entities. The envelope contains a format identifier, format version, creation time, app version, canonical payload SHA-256 checksum, and payload. The payload contains the selected collection graphs plus optional global session state only when every exported collection is selected.
2. Each collection graph contains collection/profile data, source and folder identity facts, indexed media metadata and provenance, `firstIndexedAt`, availability-independent remapping facts, `playCount`, `lastPlayedAt`, `likeScore`, playback history, playlists and stable ordering, saved queues and queue occurrences, and collection playback state.
3. Do not include source audio, artwork binaries/cache files, WorkManager state, scan staging/runs, logs, or SAF permission grants. Tree/document URIs are non-authoritative remapping hints only; importing their strings never grants access or marks a source available.
4. Serialize deterministically, checksum the canonical payload bytes, verify the checksum before preview, and reject malformed, corrupt, internally inconsistent, or unsupported input without mutating Room. Version dispatch upgrades supported older payloads into the current in-memory contract; unknown future versions are rejected with a clear compatibility message.
5. Validate enum values, non-negative statistics, like-score bounds, ownership and references, collection/name uniqueness, playlist positions, queue indices and occurrence identities, and session pointers before offering import.

## Persistence and restoration workflow

1. Add an application-scoped backup repository/service to `AppContainer`. It snapshots selected collection graphs in one Room transaction, writes JSON through a caller-provided SAF stream, inspects an input stream without database writes, and applies a previously validated selection transactionally.
2. Add focused DAO snapshot and bulk-import operations. Preserve imported stable IDs and exact statistics rather than incrementing or recomputing them. Avoid a Room schema change unless a persistence requirement cannot be represented by existing unavailable source/media rows.
3. Export selection defaults to every collection selected. Provide a visible checkbox for each collection and a single accessible **Select all / Select none** toggle whose icon and description reflect the next action. Disable Save when none are selected.
4. After the user opens a JSON file, validate it before navigation to selection. Invalid files show specific, safe feedback—malformed JSON, wrong format, unsupported version, checksum mismatch, or inconsistent data—without exposing paths or backup contents.
5. A valid preview lists the backup creation/version summary and every contained collection with profile and media/playlist/history counts. Import selection defaults to none, uses collection checkboxes plus the same all/none toggle, and disables Continue until at least one collection is selected.
6. Classify selected imports as new, stable-ID conflict, or normalized-name conflict. Conflict rows default to **Skip**. The user may explicitly choose **Replace existing**, with a warning naming exactly which local collection graph will be deleted. Do not silently merge, rename, or overwrite data.
7. Before approved replacements, checkpoint and stop playback and cancel affected indexing. In one Room transaction delete only explicitly replaced graphs, insert all chosen non-conflicting/replacement graphs, and sanitize global session state if its referenced collection, queue, or browse target was not restored. Any failure rolls back the database operation.
8. Imported sources and media remain unavailable unless current persisted read permission is independently verified. Successful import reports restored, skipped, replaced, and unresolved counts, then presents **Select collection folder** actions for unavailable collections.
9. Folder reconnection occurs after metadata import. Reselection obtains a new SAF grant and runs the normal indexer. Conservative matching restores playability while retaining ratings, play counts, history, playlist membership/order, queue-item identities, and first-indexed time. Ambiguous or missing files remain unavailable and reported instead of being dropped.

## Settings UX

1. Add **Backup & Restore** to the Settings menu and a dedicated screen with concise privacy copy explaining that JSON contains personal listening metadata but no audio.
2. **Export Backup** opens an in-app collection selection step first, with all collections checked by default. After confirmation, launch `CreateDocument("application/json")` with a dated filename, show progress, and finish with collection/record counts or a clear write error.
3. **Import Backup** launches `OpenDocument` for JSON. Show validation progress, inline invalid-file feedback with a retry action, and only expose import controls for a validated preview.
4. The import preview starts with no collections checked. Selecting rows reveals conflict status and replacement controls where needed. A final confirmation summarizes new/replaced/skipped selections and the number of folders likely to require reconnection.
5. Prevent duplicate operations, preserve selection across configuration changes in the Settings ViewModel, make every checkbox/toggle accessible, and use compact summaries that remain readable with large fonts.

## Verification and maintenance

- Serialization tests cover deterministic current-version JSON, checksum verification, selected-collection boundaries, same-version round trips, supported older-version upgrades, and corrupt/unsupported/malformed rejection.
- Room tests cover exact clean-database restoration, subset export/import, default conflict skipping, explicit replacement isolation, global-session sanitization, and injected-failure rollback.
- Recovery tests cover unavailable placeholder retention, folder reselection and conservative remapping, partial-source recovery, stable playlist/queue occurrence identities, and paused restoration.
- ViewModel/UI tests cover export default-all and import default-none selection, all/none toggles, SAF launcher handoff, validation feedback, conflict confirmation, operation locking, result summaries, and folder-reselection actions.
- Run `testDebugUnitTest`, then `lintDebug assembleDebug` with Android Studio's bundled JDK. Compile instrumentation without device installation unless a disposable target and the required explicit approval are available.
- Update `docs/UX.md` with manual workflows. Once exit criteria pass, mark T057 complete in `docs/TASKS.md` and update the README backlog status.

## Exit criteria

A user can export any chosen combination of collections to one JSON file, validate and selectively import collections from that file, explicitly resolve conflicts without silent data loss, and reconnect available source folders so playlists, ratings, play counts, listening history, queues, and indexed metadata become usable again. Missing files remain represented as unresolved metadata, and a failed or invalid import leaves existing data unchanged.
