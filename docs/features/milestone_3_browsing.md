# Milestone 3 Implementation Plan: Browse the Indexed Library

## Objective

Implement T016-T020 from [TASKS.md](../TASKS.md): provide collection-scoped reactive and paged Artist, Album, All Tracks, and Folder browsing; deterministic search, sort, and availability/rating filters; accessible file/folder multi-selection; unique descendant-media resolution; durable browsing state; and measured responsiveness with at least 25,000 indexed media rows.

The user-facing result must match the organized-music experience described in [SPECIFICATION.md](../SPECIFICATION.md), the local-first product promises and technical direction in [README.md](../../README.md), and the original folder/metadata browsing intent in [BRAINSTORM.md](../BRAINSTORM.md). The specification is normative when the brainstorm is exploratory or broader than the MVP.

Milestone 3 reads the canonical Room snapshot produced by [Milestone 2](milestone_2_indexing.md). It does not read source files, decode full artwork eagerly, create an ExoPlayer, start playback, mutate ratings, manage playlists, or generate smart queues. It must, however, expose stable scope and selection contracts that Milestones 4, 6, 7, and 8 can consume without reconstructing library semantics.

---

## Inputs from Prior Milestones

- **Milestone 0** established pure domain/repository seams, fakes, the dependency container, and typed Navigation Compose destinations.
- **Milestone 1** established Room as the source of truth, stable collection/source/folder/media IDs, nullable music metadata, restrictive retention, sortable statistics, and persisted UI session state.
- **Milestone 2** established one `MUSIC` collection/root, stable folder and media identity, nullable semantic metadata with non-null `displayTitle`, unavailable-media retention, per-field provenance, and atomic scan publication.

Milestone 3 consumes these invariants:

- A collection may span multiple root sources in the conceptual model even though the MVP UI exposes one. Every query must therefore be collection-scoped through `root_sources`; “there is only one root today” is not permission to omit scope.
- `FolderNode.id` and `MediaFile.id` remain stable across re-indexing. Folder nodes referenced by unavailable media may remain after their source path disappears.
- `artist`, `albumArtist`, and `album` are nullable. `Unknown Artist` and `Unknown Album` are presentation groups, never stored metadata values.
- `displayTitle` is usable, while `title` may be null. Track ordering falls back to normalized `displayTitle`/filename and stable media ID.
- Unavailable media remains queryable and retains ratings, history, playlists, and saved queue references.
- Canonical Room changes invalidate browser streams only after an atomic scan publication, so UI must handle paging invalidation without duplicate or mixed snapshots.

---

## Required Preflight: Reconcile Existing Browsing Contracts

Before building screens, reconcile the current Milestone 0/1 placeholder APIs with T016-T020. Update interfaces, Room implementations, fakes, converters, tests, and version-catalog dependencies together.

The current implementation requires at least these corrections:

- `MediaRepository.getMediaFilesFlow(collectionId, ...)` returns `Flow<List<MediaFile>>`, while the Room implementation does not apply `collectionId`. Replace the unbounded API with collection-scoped paging/read contracts; do not retain a production path that silently ignores collection scope.
- `MediaFileDao.getMediaFilesFlow` is unscoped, searches only some required fields, has incomplete sort behavior, and lacks stable final tie-breakers.
- `SortOrder` lacks `RECENTLY_ADDED` even though the DAO contains a string branch for it. Reconcile the enum, Room converter, UI labels, fake comparator, and tests.
- `UNPLAYED` must mean `playCount == 0`, not merely place unplayed rows first while still returning played media.
- Existing folder APIs emit an entire source tree as `Flow<List<FolderNode>>`. Replace browser usage with direct-child/breadcrumb queries and paged current-folder media.
- Existing `LibraryRoute(tab: String)` and nullable artist/album session fields cannot safely distinguish “no filter” from the Unknown group. Introduce typed tabs/routes and explicit known-versus-unknown group keys.
- Existing `UiSessionState.activeFilterSnapshot` uses `QueueFilterSnapshot`, whose default dislike-exclusion semantics belong to future smart queues. Introduce a versioned `LibraryFilterSnapshot` for browsing and define an explicit later conversion to queue eligibility.
- Paging dependencies are not currently present. Add compatible, version-catalog-pinned Paging runtime/Compose and Room-Paging artifacts rather than implementing manual offset lists in ViewModels.

If these changes require a Room schema update, increment the database version, export the schema, add a non-destructive migration, and preserve all existing library/user data. Never use destructive migration fallback.

---

## Responsibility and Downstream Boundaries

| Milestone | Responsibility |
| --- | --- |
| **Milestones 1-2 (completed baseline)** | Durable canonical library, stable identity/folder hierarchy, metadata normalization, availability, and atomic re-index publication. |
| **Milestone 3 (this plan)** | Scoped/paged read models, metadata and folder browsers, deterministic search/sort/filter semantics, selection resolution, browsing-state persistence, and 25,000-row validation. |
| **Milestone 4** | Convert a visible ordered library context plus selected media ID into an explicit saved playback queue and send commands through `MediaController`. |
| **Milestone 5** | Mutate ratings/play statistics; Milestone 3 queries reactively refresh without changing their semantics. |
| **Milestone 6** | Feed file/folder selections into the reusable playlist selector and preserve unavailable memberships. |
| **Milestone 7** | Restore the typed route, selected group/folder, search text, sort, filters, and safe fallback when a target no longer exists. |
| **Milestone 8** | Correct startup readiness/route restoration integration and simplify index-completion feedback. |
| **Milestone 9** | Snapshot the current visible scope, force available-only eligibility, and exclude disliked media by default before seeded generation. |
| **Post-MVP** | Multiple-root management UI, contextual/flat presentation profiles, global command search, and source-file maintenance. |

Milestone 3 must not conflate these concepts:

- **Browsing visibility:** unavailable and disliked media are visible by default so retained records remain inspectable.
- **Playback eligibility:** a later queue-start action uses available media only.
- **Smart-generation eligibility:** a later generator uses available media and excludes `likeScore < 0` by default.
- **Playlist membership:** a later manual playlist may retain unavailable media.

---

## Implementation Order

Implement in this dependency order:

1. Reconcile paging, sort/filter, group-key, route, repository, fake, and session-state contracts.
2. Implement scoped Room projections and paged queries with correctness tests.
3. Build shared `LibraryQuery` normalization and deterministic ordering policy.
4. Build Artist, Album, All Tracks, and Folder ViewModels/screens.
5. Build accessible selection and descendant-resolution contracts for downstream actions.
6. Persist meaningful browsing context and handle empty/unavailable states.
7. Seed 25,000-row fixtures, inspect query plans, measure paging/UI behavior, and optimize from evidence.

Each slice must compile and have focused tests before the next slice depends on it.

---

## Shared Browsing Contracts

### 1. Typed scope, groups, filters, and sort

Define pure domain/read contracts, with naming adjusted only to match project conventions:

```kotlin
@Serializable
enum class LibrarySurface { ARTISTS, ALBUMS, ALL_TRACKS, FOLDERS }

@Serializable
sealed interface MetadataGroupKey {
    @Serializable data class Known(val value: String) : MetadataGroupKey
    @Serializable data object Unknown : MetadataGroupKey
}

@Serializable
enum class AvailabilityFilter { ALL, AVAILABLE_ONLY, UNAVAILABLE_ONLY }

@Serializable
data class LibraryFilterSnapshot(
    val version: Int = 1,
    val availability: AvailabilityFilter = AvailabilityFilter.ALL,
    val excludeDisliked: Boolean = false,
)

data class LibraryQuery(
    val collectionId: String,
    val sourceId: String? = null,
    val folderId: String? = null,
    val includeFolderDescendants: Boolean = false,
    val artist: MetadataGroupKey? = null,
    val album: MetadataGroupKey? = null,
    val albumArtist: MetadataGroupKey? = null,
    val searchText: String = "",
    val sort: SortOrder,
    val filters: LibraryFilterSnapshot = LibraryFilterSnapshot(),
)
```

- `collectionId` is required for every library query. `sourceId` is required for a folder surface and optional for collection-wide metadata surfaces.
- `null` artist/album query key means no group filter; `MetadataGroupKey.Unknown` means `IS NULL`. Never use a magic `"Unknown Artist"` value as a database predicate.
- Normalize search once: trim, collapse repeated whitespace, use null/empty to mean no search, and escape `%`, `_`, and the chosen SQLite `LIKE ... ESCAPE` character so user text is literal.
- Keep the user's display casing in projection fields. Define search/sort collation behavior in tests; SQLite `NOCASE` is not full Unicode case folding, so do not promise unsupported locale semantics.
- Debounce text search in the ViewModel (approximately 250-300 ms), use `distinctUntilChanged`, and cancel stale queries with `flatMapLatest`.

### 2. Read projections

Use query-specific projections instead of hydrating full `MediaFileEntity` rows when a card/list does not need every column:

- `ArtistSummary`: explicit group key, display name, total track count, available track count, album-group count, and deterministic representative artwork if implemented.
- `AlbumSummary`: explicit album key, effective album-artist key/display label, representative contributing artist, total/available track counts, deterministic year, deterministic representative media/artwork reference.
- `TrackListItem`: media ID, source/folder IDs, display title, filename, artist, album artist, album, disc/track, duration, artwork thumbnail reference, availability, play count, last played, and like score.
- `FolderListItem`: folder ID, source ID, parent ID, relative path, display name, direct child-folder count, direct media count, and descendant media count only if it can be computed without an N+1 query.
- `FolderBreadcrumb`: stable folder ID plus display name for one ancestor segment.

Projection types belong at the domain/read boundary; Room-specific projection rows stay in the data layer and map explicitly.

### 3. Paging contract

- Expose `PagingSource<Int, ...>` from Room DAOs and `Flow<PagingData<...>>` from repositories for artist summaries, album summaries, and track lists.
- Use a shared `Pager` configuration with an initial page size around 50, bounded prefetch distance, placeholders disabled unless item counts prove reliable, and `cachedIn(viewModelScope)` at the screen ViewModel.
- Query only direct subfolders for the current folder. Folder media uses paging; breadcrumbs use a bounded ancestor query.
- Room invalidation must create a new paging generation after scan publication or rating/play updates. Compose lists use stable IDs/group keys as item keys and must tolerate invalidation without retaining stale selection objects.
- Selection state stores stable IDs/group keys, never `LazyPagingItems` positions or entity instances.

---

## Proposed Changes

### T016 — Implement Indexed Library Queries

#### 1. Scope every query correctly

- Join `media_files.sourceId` to `root_sources.id` and require `root_sources.collectionId = :collectionId` for collection-wide queries.
- When `sourceId` is supplied, require it in addition to collection scope. Validate that a requested folder belongs to that source/collection before returning content.
- Apply availability, dislike, metadata-group, and search predicates before grouping so summary counts and empty groups reflect the visible result set.
- Search track fields: `displayTitle`, `title`, `filename`, `artist`, `albumArtist`, and `album`. Artist and album summary search must use the same normalized literal search semantics.
- Avoid N+1 lookups. Summary rows, child counts, and list items must be returned by set-based queries or bounded follow-up queries independent of visible row count.

#### 2. Define artist and album identity

- The Artists surface groups by the nullable `artist` field. All null artists form one `MetadataGroupKey.Unknown` card.
- Album presentation uses `albumArtist ?: artist` as the effective grouping artist without writing that fallback into storage.
- An Album surface group is the composite `(nullable album, nullable effectiveAlbumArtist)`. This prevents two artists' albums with the same title from being merged.
- Within an Artist detail scope, show albums containing tracks whose track artist matches the selected Artist key. Preserve each album's effective album-artist key for navigation.
- Unknown album and unknown artist are selectable groups. Routes and queries must distinguish them from an unfiltered scope.
- Define deterministic summary values:
  - Year is the minimum non-null year within the filtered album group, or null.
  - Representative media/artwork is the first available track with non-null artwork according to album track order and stable ID; otherwise use the first track by that order and the stable placeholder.
  - Album counts include the Unknown Album bucket instead of relying on `COUNT(DISTINCT album)`, which ignores null.

#### 3. Define deterministic track ordering

Centralize SQL and fake-repository semantics for every `SortOrder`. All orders end with normalized display title and stable media ID unless those fields already appear later in the specified chain.

Use one documented sort-title expression equivalent to:

`lower(trim(coalesce(nullif(displayTitle, ''), filename)))`

| Sort | Primary semantics before final `sortTitle ASC, id ASC` |
| --- | --- |
| `ARTIST` | Known artist before unknown; artist ASC; effective album artist ASC; known album before unknown; album ASC; known disc/track before unknown; disc ASC; track ASC. |
| `ALBUM` | Known album before unknown; album ASC; effective album artist ASC; known disc/track before unknown; disc ASC; track ASC. |
| `TITLE` | No additional primary key. |
| `TRACK` | Known disc before unknown; disc ASC; known track before unknown; track ASC. |
| `RECENTLY_ADDED` | `firstIndexedAt DESC`. |
| `MOST_PLAYED` | `playCount DESC`. |
| `LEAST_PLAYED` | `playCount ASC`. |
| `UNPLAYED` | Predicate `playCount = 0`; then deterministic title order. |
| `MOST_RECENT` | Played before unplayed; `lastPlayedAt DESC`; unplayed final. |
| `LEAST_RECENT` | Unplayed first; then `lastPlayedAt ASC`. |
| `MOST_LIKED` | `likeScore DESC`; negative rows remain unless Exclude Disliked is enabled. |

Album detail defaults to `TRACK`; unknown disc/track values sort after numbered values and then by title/filename. Normal sorts never randomize ties.

#### 4. Implement folder queries and recursive resolution

- Query the source root node explicitly (`relativePath = ""`), direct child folders by `parentId`, direct media by `folderId`, and ancestors for breadcrumbs.
- Implement descendant folder/media resolution with a source-scoped recursive CTE rooted at the selected folder ID. Include direct media in the selected folder itself.
- Add a batch resolver that accepts selected file IDs plus selected folder IDs, validates they belong to the active collection/source, expands folders, and returns unique media IDs. Overlapping folder/file selections must not duplicate an ID.
- Make availability an explicit resolver parameter:
  - `INCLUDE_UNAVAILABLE` for future playlist membership/inspection.
  - `AVAILABLE_ONLY` for future playback and smart generation.
- Return deterministic order when order matters. For selection membership, stable ID order is sufficient; for queue creation, expose a separate `snapshotVisibleMediaIds(LibraryQuery)` operation that uses the exact active filter and sort order.
- Do not pass thousands of IDs through navigation routes or save them in Compose state. Resolve at the repository/use-case boundary when an action is invoked.

#### 5. Update repositories and fakes

- Add focused read methods rather than one nullable-parameter “do everything” query.
- Keep write/scan methods intact while evolving the read side.
- Make `FakeMediaRepository` model collection-to-source ownership so scope-isolation tests are meaningful.
- Apply the same unknown grouping, composite album identity, filter defaults, search normalization, and sort tie-breakers in fakes. Tests should run shared conformance cases against fake and Room implementations where practical.

---

### T017 — Build Artist and Album Views

#### 1. Add typed destinations

- Replace raw tab strings with a serializable `LibrarySurface` (or equivalent enum).
- Expose Artists, Albums, Folders, and All Tracks as equal top-level library destinations, matching the specification and README. Folders may use its focused typed route/screen, but it must remain a visible top-level library choice rather than a hidden secondary action.
- Add typed destinations for Artist detail and Album detail. Carry collection ID plus serialized known/unknown group keys; do not carry display labels as identity.
- Folder destinations carry source ID and a stable folder ID, including the actual indexed root folder ID rather than using null as an implicit global root.
- Route arguments contain only IDs/small group keys. Lists, filters, and media objects remain in repositories/session state.

#### 2. Build one reactive query-state pipeline

- `LibraryViewModel` combines selected surface, collection, search text, sort, and filters into `LibraryQuery`.
- Artist/Album detail ViewModels receive typed route keys and combine them with persisted browsing controls.
- Use lifecycle-aware Flow collection in Compose and expose immutable UI state plus paged content.
- Persist meaningful state after normalization/debounce; do not write session state on every recomposition.

#### 3. Artists surface and detail

- Show paged artist cards/list rows with display name (`Unknown Artist` only at presentation), visible track/album counts, and placeholder/representative artwork when available.
- Stable list key is the serialized group key, not the nullable display name.
- Artist selection opens a detail screen containing:
  - Artist header.
  - Paged/filter-aware album summaries for that artist.
  - Paged tracks by that artist using the active deterministic sort, defaulting to Album/Track semantics.
- The Unknown Artist card drills into only `artist IS NULL`; it must not accidentally mean all artists.

#### 4. Albums surface and detail

- Show paged album cards with album label, effective album artist label, counts, year, and lazy artwork/placeholder.
- Stable card key is the composite album/effective-artist group key.
- Album detail queries that exact composite group and shows tracks ordered by `TRACK` by default: disc, track, normalized display title/filename, then media ID.
- The Unknown Album card is explicit and does not collide with an unfiltered album query.
- If the selected group disappears after re-index/filter change, show a clear empty state with Back/Clear Filters rather than navigating to unrelated content.

#### 5. All Tracks surface

- Add a paged All Tracks list using `TrackListItem`, all supported sorts, search, availability indicator, dislike indicator/score where appropriate, and stable media ID keys.
- Row selection during this milestone exposes a callback/context contract for Milestone 4 but does not instantiate or command a player.
- Avoid reading or decoding artwork until a row becomes visible; use bounded thumbnails and a stable placeholder.

---

### T018 — Build the Folder Browser

#### 1. Mirror the indexed hierarchy

- Enter through the registered source's actual root `FolderNode` and query only its direct children/current-folder media.
- Render accessible breadcrumbs from root to current folder. Each breadcrumb carries a stable folder ID and can navigate directly without reconstructing a path string.
- List subfolders before files. Sort subfolders deterministically by normalized display name then folder ID; apply the selected track sort to direct files.
- Show both available and unavailable files by default. Use an icon, text label, and semantics such as “Unavailable”; do not rely on dimmed color alone.
- Retained folders that contain only unavailable media remain inspectable.

#### 2. Handle folder and filter empty states

Distinguish:

- Indexed collection/root has no media.
- Current folder has no direct children/media.
- Search/filter has no matches.
- All matching media is unavailable while Available Only is active.
- Root permission/storage is unavailable.

Offer context-appropriate Clear Search/Filters, Back, Retry Access, and Re-index actions. Do not show a generic blank list.

#### 3. Build accessible multi-selection

- Support an explicit selection control/check box on every selectable row. Long-press may enter selection mode but cannot be the only accessible path.
- Store selected file IDs and folder IDs separately. Selection survives paging refresh/configuration change in the ViewModel but is cleared when collection/source changes.
- Show selected folder/file counts and, after repository resolution, unique total and available media counts.
- Selecting both a folder and one of its descendants yields one media ID per track.
- Provide Select All Visible only if it operates on the complete filtered query through the repository, not merely the currently loaded Compose page; otherwise omit it.
- Milestone 3 may expose action callbacks/placeholders for Play and Add to Playlist, but Milestones 4 and 6 own those mutations and UI workflows.

---

### T019 — Add Search, Sort, Filter, and Session State

#### 1. Define control applicability

Use reusable search/sort/filter components, but show only controls meaningful to the current surface:

| Surface | Search | Sort | Availability/dislike filters |
| --- | --- | --- | --- |
| Artists/Albums summaries | Artist/album/track-derived search | Deterministic group-name order; optional count sorts only if explicitly added/tested | Yes; summary groups/counts derive from filtered tracks. |
| Artist/Album detail tracks | Required fields | All track sorts; Album defaults to Track | Yes. |
| All Tracks | Required fields | All specified track sorts | Yes. |
| Folder direct files | Required fields within folder | All applicable track sorts | Yes. |

T019 requires `ARTIST`, `ALBUM`, `TITLE`, `TRACK`, `RECENTLY_ADDED`, `MOST_PLAYED`, `LEAST_PLAYED`, `UNPLAYED`, `MOST_RECENT`, `LEAST_RECENT`, and `MOST_LIKED`. Do not silently omit Unplayed or Recently Added.

#### 2. Define filter defaults and visible scope

- Normal browsing defaults to `AvailabilityFilter.ALL` and `excludeDisliked = false`, preserving the README promise that unavailable files remain represented.
- Available Only and Unavailable Only are mutually exclusive states of one availability filter.
- Exclude Disliked removes `likeScore < 0`; neutral and positive rows remain.
- Group summaries disappear when no track remains after active filters.
- `LibraryQuery` is the canonical description of the currently visible result set. Later queue/smart actions snapshot it, then apply their stricter eligibility policy without changing the browsing controls behind the user's back.

#### 3. Persist browsing context safely

- Evolve `UiSessionState` to persist a versioned browsing snapshot containing surface, collection ID, optional source/folder, explicit artist/album known-or-unknown group keys, normalized search text, sort, and `LibraryFilterSnapshot`.
- Preserve current queue/playlist/session fields while migrating. Do not overload `QueueFilterSnapshot` with UI-only availability or surface state.
- Save meaningful changes after debounce and on navigation transitions. Keep transient selection sets, paging keys, loaded pages, and scroll item objects out of Room.
- Validate restored IDs/keys. If a collection/folder/group no longer exists, fall back to the nearest valid library surface and show an explanation where appropriate. Milestone 7 completes full route/scroll/playback restoration using these contracts, and Milestone 8 closes the startup integration gaps found during device acceptance.

#### 4. Accessibility and adaptive layout

- Provide labeled search clear, sort, filter, selection, and navigation controls with minimum touch targets and logical focus order.
- Announce result-count/empty-state changes without announcing every paging load.
- Support phone portrait and landscape. Use adaptive list/grid sizing where it improves readability, but do not claim tablet-specific optimization beyond the MVP specification.
- Dynamic text must not hide search, sorting, selection exit, or primary navigation actions.

---

### T020 — Verify Large-Library Behavior

#### 1. Build deterministic seeded data

- Create a reusable fixture generator for 0, 1, small representative, and 25,000-media libraries.
- The large fixture must include multiple sources/collections, at least 1,000 artists, repeated album names across artists, null artist/album groups, album artists, numbered and unnumbered tracks, deep folders, unavailable rows, disliked/neutral/liked scores, played/unplayed rows, duplicate sort values, and searchable wildcard characters.
- Use deterministic IDs, timestamps, and seeds so query output and performance comparisons are reproducible.
- Seed Room in bounded transactions outside measured sections.

#### 2. Prove correctness at page boundaries

Test every query/filter/sort against an independent expected model:

- No cross-collection/source leakage.
- Correct known/unknown grouping and composite album identity.
- Correct literal search escaping and all required search fields.
- Correct null placement and stable media-ID tie-breakers for every sort.
- No duplicate/missing rows across paging loads for a stable database snapshot.
- Paging invalidates and refreshes after atomic re-index/rating/stat updates.
- Recursive folder resolution handles deep trees, overlapping selections, and availability policy.
- Fakes and Room agree on shared conformance cases.

#### 3. Measure rather than invent a universal threshold

- Do not use a hard `<100 ms` Robolectric assertion: host load and JVM warm-up make it brittle and it does not prove device UI responsiveness.
- Use JVM/Robolectric tests for deterministic correctness and query-plan inspection, not universal latency claims.
- On a controlled API 34+ emulator/device, record warm median and p95 for first page, next page, artist/album grouping, debounced search, complex sorts, direct-folder load, and descendant resolution. Record device/emulator, build type, database size, page size, iteration count, and whether caches were warm.
- Establish regression thresholds from the recorded baseline with reasonable tolerance. A regression test must compare like-for-like environments.
- Use `EXPLAIN QUERY PLAN` for representative queries. Eliminate full-table scans where an applicable selective predicate/order can use an index; do not add speculative indexes that the measured plan does not use.

#### 4. Verify UI/memory behavior

- Scroll Artists, Albums, All Tracks, and a large folder in Compose tests/manual profiling without loading the entire result set.
- Confirm ViewModels expose `PagingData`, not 25,000-item lists, and `LazyColumn`/`LazyVerticalGrid` uses stable keys.
- Confirm search uses cancellation/debounce and rapid typing does not queue obsolete database work.
- Load only bounded visible artwork thumbnails. If no image loader exists, add one pinned compatible Compose image-loader dependency through the version catalog after compatibility verification; do not decode full embedded artwork on the main thread.
- Use Android Studio profiler or an instrumentation memory check to verify that repeated navigation/rotation does not retain pages, bitmaps, screens, or ViewModels.
- Room suspend/Flow/Paging queries already execute off the main thread; do not wrap the whole paging pipeline in redundant `Dispatchers.IO`. Verify that no custom blocking DAO/file/artwork work runs on the main thread.

---

## File-Level Handoff

The coding assistant should expect to create or modify files in these areas. Inspect each current file before editing and preserve unrelated worktree changes.

- `gradle/libs.versions.toml` and `app/build.gradle.kts`: pinned Paging/Room-Paging, test, and optional thumbnail-loader dependencies.
- `domain/model/SortOrder.kt`: complete required sort enum.
- New domain/read models for `LibrarySurface`, `MetadataGroupKey`, `LibraryFilterSnapshot`, `LibraryQuery`, summaries, track/folder list items, and selection-resolution results.
- `domain/model/UiSessionState.kt`: versioned browsing context without replacing queue/playlist fields.
- `domain/repository/MediaRepository.kt`: paged scoped reads, folder/breadcrumb queries, selection expansion, and ordered visible-scope snapshot.
- `data/database/dao/MediaFileDao.kt` and `FolderDao.kt`: scoped projections, paging sources, deterministic SQL, breadcrumbs, and recursive CTEs.
- `data/database/entity/UiSessionStateEntity.kt`, `Converters.kt`, `Resn8Database.kt`, exported schemas, and migration tests if persisted browsing state/schema changes.
- `data/repository/RoomMediaRepository.kt`, `FakeMediaRepository.kt`, and `RoomUiSessionRepository.kt`: mapping, Pager construction, semantic parity, and persistence.
- `di/AppContainer.kt`: injectable read repositories/ViewModel factories as needed.
- `ui/navigation/Resn8Destinations.kt` and `Resn8NavHost.kt`: typed surfaces and explicit Artist/Album/Folder routes.
- Existing library/folder placeholders plus focused `ui/library/` and `ui/folders/` ViewModels/components for summaries, tracks, breadcrumbs, controls, selection, and empty/error states.
- `src/test` and `src/androidTest`: query conformance, Room paging, ViewModel/Flow, Compose navigation/accessibility, large-library, and controlled-device measurements.

Do not create references to planned source files until they are actually added, and do not move existing classes solely to match this document's illustrative package names.

---

## Verification Commands

Run from the repository root, using the wrapper/toolchain described in [README.md](../../README.md):

```powershell
# Query, repository, ViewModel, paging, and large-fixture correctness tests
.\gradlew.bat testDebugUnitTest

# Static checks and debug compilation, including Room/KSP schema output
.\gradlew.bat lintDebug assembleDebug

# Compile Compose/instrumentation browsing tests without requiring a device
.\gradlew.bat compileDebugAndroidTestKotlin

# Run Compose, paging, navigation, accessibility, and controlled-device measurements
.\gradlew.bat connectedDebugAndroidTest
```

Also:

- Inspect exported Room schema/migration diffs for every entity/index/converter change.
- Capture representative `EXPLAIN QUERY PLAN` output and the controlled-device benchmark record for the 25,000-row fixture.
- Record any device-only test that could not run. A missing connected target or empty benchmark suite is not a passing result.

---

## Exit Criteria

Milestone 3 is complete only when all of the following are demonstrated:

1. Every Artist, Album, All Tracks, and Folder query is collection-scoped, reactive, paged/lazily streamed, and covered against cross-source leakage.
2. Known and Unknown Artist/Album groups are distinct, navigable query identities; same-named albums from different effective album artists do not merge.
3. Artist drill-down shows that artist's albums/tracks, and Album detail uses deterministic disc/track/title/ID ordering with unnumbered tracks handled explicitly.
4. All required sorts—including Recently Added and Unplayed—plus literal search, availability, and dislike filters follow the documented null/tie/default semantics.
5. The folder browser mirrors stable indexed hierarchy, exposes breadcrumbs/direct children, visibly labels unavailable media, and distinguishes all required empty/error states.
6. File/folder multi-selection is accessible, survives paging refresh/configuration change, and resolves overlapping selections to unique stable media IDs with an explicit availability policy.
7. The exact visible ordered scope can be snapshotted for later playback/queue work without passing large lists through routes or Compose state.
8. Typed routes and versioned session state preserve surface, collection, folder/group identity, search, sort, and filters without conflating Unknown with unfiltered state.
9. A deterministic 25,000-row suite proves query/paging correctness; controlled-device evidence shows responsive paging/search/scrolling without eager full-list or artwork loading, main-thread blocking, or retained bitmap/page leaks.
10. Unit, migration/schema, lint/build, instrumentation compilation, and applicable device tests pass, with device/environment measurements and limitations recorded.

---

## Reference Files

- [SPECIFICATION.md](../SPECIFICATION.md): Sections 1, 2.2-2.4, 2.7, 3.1, 3.4, 4.1-4.4, and 5.
- [README.md](../../README.md): Browse/index MVP, local-first/unavailable-media promises, accessibility principles, technical direction, requirements, and wrapper commands.
- [TASKS.md](../TASKS.md): T016-T020 plus downstream T023, T032-T033, T037-T041, T045-T048, and T050-T051.
- [BRAINSTORM.md](../BRAINSTORM.md): Organized-music hierarchy, original folder browser, selection/playlist intent, sorting/filtering, and visible-scope generation.
- [milestone_1_persistence.md](milestone_1_persistence.md): Stable schema/IDs, query indexes, restrictive retention, session state, fakes, and migration requirements.
- [milestone_2_indexing.md](milestone_2_indexing.md): Stable folder/media identity, nullable metadata/display fallbacks, unavailable retention, atomic publication, and downstream contracts.
- [MediaFile.kt](../../app/src/main/java/com/app/resn8/domain/model/MediaFile.kt)
- [Collection.kt](../../app/src/main/java/com/app/resn8/domain/model/Collection.kt)
- [SortOrder.kt](../../app/src/main/java/com/app/resn8/domain/model/SortOrder.kt)
- [SavedQueue.kt](../../app/src/main/java/com/app/resn8/domain/model/SavedQueue.kt)
- [UiSessionState.kt](../../app/src/main/java/com/app/resn8/domain/model/UiSessionState.kt)
- [MediaRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/MediaRepository.kt)
- [UiSessionRepository.kt](../../app/src/main/java/com/app/resn8/domain/repository/UiSessionRepository.kt)
- [MediaFileDao.kt](../../app/src/main/java/com/app/resn8/data/database/dao/MediaFileDao.kt)
- [FolderDao.kt](../../app/src/main/java/com/app/resn8/data/database/dao/FolderDao.kt)
- [RoomMediaRepository.kt](../../app/src/main/java/com/app/resn8/data/repository/RoomMediaRepository.kt)
- [FakeMediaRepository.kt](../../app/src/main/java/com/app/resn8/data/repository/FakeMediaRepository.kt)
- [Resn8Destinations.kt](../../app/src/main/java/com/app/resn8/ui/navigation/Resn8Destinations.kt)
- [Resn8NavHost.kt](../../app/src/main/java/com/app/resn8/ui/navigation/Resn8NavHost.kt)
- [LibraryScreen.kt](../../app/src/main/java/com/app/resn8/ui/screens/LibraryScreen.kt)
- [FoldersScreen.kt](../../app/src/main/java/com/app/resn8/ui/screens/FoldersScreen.kt)
