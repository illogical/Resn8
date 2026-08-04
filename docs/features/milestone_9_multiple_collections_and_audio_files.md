# Milestone 9 Implementation Plan: Multiple Collections and Folder-First Audio

## Goal

Milestone 9 expands the MVP from one music collection to multiple named collections, each backed by exactly one persisted SAF folder. Users can create and switch between `MUSIC` and `FLAT` collections. Music retains metadata-oriented Library browsing; Audio Files uses a filename-oriented Folders experience without invented artist or album labels.

Smart randomized queues, multiple roots within one collection, collection deletion, and user-facing `CONTEXTUAL` collections remain P2 work.

## Checked-in baseline

- Room version 3 already separates collections, root sources, media, playlists, queues, and UI session state by stable IDs, but collection names are not normalized or unique.
- `CollectionRepository.createCollection` always creates `MUSIC`, and Settings assumes the first collection and first source.
- `ScanOrchestrator` always applies music path inference, even though `CollectionProfile.FLAT` already exists in domain and Room contracts.
- `ActiveCollectionViewModel` can resolve a persisted collection ID but cannot switch collections, and the app shell always exposes Library.
- Folder multi-selection exists, but there is no database-backed Select All for direct available files. Album detail has a one-shot bulk-add action rather than selection state.

## Domain, persistence, and indexing

1. Add `normalizedName` to `Collection` persistence with a unique index and a non-destructive version 3-to-4 migration. Backfill existing rows with trimmed lowercase names; abort migration on an actual normalized collision instead of discarding data.
2. Change collection creation to accept a `CollectionProfile`, permit only `MUSIC` and `FLAT` through the user-facing flow, and add atomic rename with the same normalized uniqueness rules. Keep existing callers source-compatible through a default `MUSIC` profile.
3. Expose source lookup by ID. Enforce one source per collection and reject a tree URI already owned by any collection; never return a source belonging to a different collection.
4. Resolve the source and collection profile from persisted IDs inside indexing. `MUSIC` keeps tag, path, and filename precedence. `FLAT` keeps extracted tags in nullable shared fields but does not infer artist, album, disc, or track from its path/filename; its display title is tag title or cleaned filename.
5. Preserve collection/source/media identities, statistics, playlist memberships, saved queues, and scan publication behavior across migration and re-indexing.

## Collection management and switching

1. Make the top app bar title an accessible selector showing the active collection. Settings owns creation, rename, re-index, and permission reselection.
2. Creating a collection collects profile, normalized-unique name, and one SAF tree grant before enqueueing that source's scan. On success it becomes active and routes to Library/Artists for `MUSIC` or Folders for `FLAT`.
3. Switching first pauses/stops the MediaController and checkpoints through the service, then atomically saves the new selected collection/source while clearing `activeQueueId` and collection-specific browse fields. The saved queue row and listening data remain intact.
4. Switching clears the app's visible queue/mini-player and resets navigation to the target profile home. Music exposes Settings, Library, Folders, and Playlists; Audio Files omits Library. Invalid restored Library/artist/album routes for `FLAT` fall back to Folders.
5. All playlist reads and mutations remain scoped to the selected or payload-owning collection, and cross-collection media IDs continue to fail validation.

## Folder-first presentation and selection

1. Replace user-facing `Root` copy with the collection name or `collection folder`. Keep the internal `RootSource` model for future multiple-root support.
2. Render the top folder breadcrumb as the collection name. In `FLAT`, audio rows and playback surfaces omit synthetic artist/album labels and use audio-file terminology.
3. Add a repository snapshot query for all available media directly in a folder. Folder Select All uses this query across paging, excludes unavailable rows and every subfolder/descendant, toggles the complete direct-file set, and clears on folder/collection changes.
4. Add album selection state and a Select All toggle backed by the full available album query. Reuse the standard selection summary and playlist selector; toggling again deselects the album set.
5. Preserve explicit folder-checkbox behavior, which intentionally expands selected folders to unique indexed descendants.

## Verification

- Migration tests cover version 3 data preservation, normalized-name backfill, and uniqueness.
- Repository tests cover create/rename conflicts, one source per collection, duplicate tree rejection, and collection isolation.
- Parser/indexer tests prove `FLAT` does not invent music hierarchy while `MUSIC` behavior remains unchanged.
- ViewModel and UI tests cover creation, switching, profile-aware destinations, retained saved queues, hidden Library navigation, collection-name breadcrumbs, and selection clearing.
- Folder and album tests prove Select All includes available rows beyond the loaded page and excludes unavailable/direct-child folder descendants as specified.
- Run `testDebugUnitTest`, then `lintDebug assembleDebug` with Android Studio's bundled JDK. Complete API 34+ SAF creation/reselection, collection switching during playback, restart restoration, and TalkBack checks manually.

## Exit criteria

A user can maintain multiple uniquely named single-folder collections, index and browse a filename-only Audio Files collection without false music metadata, switch collections without losing listening data, and add all available direct folder files or album songs to playlists. Existing music, playback, playlist, and restoration behavior remains regression-safe.
