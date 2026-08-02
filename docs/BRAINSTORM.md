# Resn8 Product Brainstorm

This document captures the product ideas, use cases, questions, and future directions that shaped Resn8. It is intentionally exploratory. For resolved MVP boundaries and implementation requirements, refer to [SPECIFICATION.md](SPECIFICATION.md); for execution order, refer to [TASKS.md](TASKS.md).

## Product Vision

Resn8 should make a locally managed audio library easy to browse, organize, play, and rediscover. The application should learn from durable listening data, including play counts, last-played timestamps, and a signed like score, so it can eventually generate useful randomized queues.

The first scenario should focus on an organized music library copied to an Android device's internal shared storage or SD card. The user will manage the source files outside Resn8, so general file management is out of scope for the initial experience.

## Library Shapes and Collection Model

Three folder-selection use cases should inform the design, even if the MVP supports only one collection with one selected root folder.

### 1. Organized music library

- The selected root contains an organized music collection.
- Folder structure can provide important metadata in the form `Artist -> Album -> Songs`.
- Filenames often include the album's original track number.
- Resn8 may need to parse filenames and detect whether the first sample library uses more than one naming convention.
- The library should support elegant, performant filtering and sorting by artist and album.
- Selecting an artist should narrow the view to that artist's albums; selecting an album should show only its songs.

### 2. Contextual folder collection

- The selected root may contain audio files arranged into meaningful category folders.
- Example collection: `Podcasts` with subfolders such as `Software Development`, `AI`, and `Psychology`.
- For this shape, the useful hierarchy may be `Context -> Category -> Filename` rather than `Artist -> Album -> Song`.
- Folder categories should be indexable for fast sorting and filtering.

### 3. Flat audio folder

- The selected folder contains audio files but no subdirectories.
- There is no folder hierarchy from which to infer categories.
- Resn8 still needs a useful way to display these files and add them to playlists.

### Conceptual hierarchy

The initial serialized structure can be thought of as:

`Collections -> one or more selected folders -> optional relative subfolders -> audio files`

The MVP can begin with a single collection and single root folder while leaving room for multiple collections and multiple roots later.

### Open schema and metadata questions

- Can organized music and contextual/flat audio imports share one media schema while presenting different metadata and hierarchies?
- What additional metadata is stored inside MP3 files, and what is required to access it?
- How many filename conventions exist in a representative sample library?
- How should albums with track-numbered files be sorted?
- Should files without useful music metadata, such as audio-only video rips, fall back to alphabetical order?

## Indexing and Media Management

### Folder selection and indexing

- Use Android's built-in folder selector for roots on internal shared storage or an SD card.
- Recursively find audio files when the selected root contains subfolders.
- Index or cache enough information to make browsing, sorting, and filtering fast.
- Let the user inspect which folders and files are currently indexed.

### Re-indexing

- Provide a manual re-index action for a selected folder or, later, selected folders in a collection.
- Re-indexing should account for files manually added to the device.
- It should also account for the rarer case in which files are removed or renamed during library cleanup.
- Scheduled re-indexing can be a later feature.

## Core User Experiences

### Main music player

The primary player should be simple and centered, with:

- Previous
- Play/Pause
- Next
- Like
- Dislike
- Add to Playlist

The Like and Dislike actions should modify an integer score:

- Like adds `1`.
- Dislike subtracts `1`.
- `0` means unrated or neutral.
- A negative value means disliked.
- A positive value means liked.
- Repeated likes can raise a file's priority in future randomization algorithms.

An optional double-click in whitespace or two-finger tap could slide in additional controls. This gesture was originally imagined as a path to Add to Playlist, but the action should also have a discoverable visible entry point.

### Library browsing

- Browse organized music by artist and album.
- Select an artist to see only its albums.
- Select an album to see only its songs.
- Browse the original indexed folder structure when metadata views are not enough.

### Collection browser

- View which media collections exist.
- Create new collections.
- Delete existing collections.
- Keep the MVP interface focused on one collection and one selected root while preserving these management ideas for later expansion.

### File browser

The file browser should:

- Mirror a collection's indexed folder/file structure.
- Show which subfolders and audio files are indexed.
- Allow selection of individual files.
- Allow selection of a folder or subfolder as all of its indexed descendant files.
- Support multi-selection for bulk playlist operations.

### Playlist browser

The playlist browser should:

- List existing playlists.
- Create playlists.
- Delete playlists with confirmation.
- Show a playlist's files in queue order.
- Provide a simple text filter so the user can confirm whether a file is already included.
- Support manual ordering through drag and drop.
- Also provide Move to Top and Move to Bottom controls for each file.
- Add and remove files without modifying the source audio.

### Reusable playlist selector

Use one reusable context menu, modal, or similar component from the player and file browser.

- From the main player, show every playlist in the current collection and whether the currently playing file belongs to it.
- From the file browser, apply the same selector to one file, multiple files, or all indexed descendants of a selected folder.
- List playlists containing the file first, followed by playlists to which it can be added.
- For bulk selections, preserve the intention to communicate existing membership, including partial membership when appropriate.
- Checking a playlist adds the selected file or files.
- Unchecking a playlist removes the selected file or files.
- Keep the selector open until dismissed so the user can update more than one playlist in one visit.

## Data and Serialization Brainstorm

### Collections

- Store one or more selected folder paths or durable folder references.
- Discover audio within nested subfolders.
- Retain an indexed/cacheable view of the library.
- Support viewing media by its original folder paths.
- Own playlists associated with that collection.

### Media files

Each indexed media file needs, at minimum:

- ID
- Relative folder path
- Filename
- Play count
- Last-played timestamp
- Like count / signed like score

Additional music metadata may include artist, album, song title, and track number when available from embedded tags, paths, or filenames.

### Playlists

- Serialize playlist membership.
- Serialize manual queue order.
- Allow a currently playing file, selected files, or a selected folder's descendants to be added or removed.

### Playback and session state

To continue where the user left off, retain enough state to answer:

- Which playlist or queue was last open or selected?
- In what order was it sorted?
- Which file was playing?
- What was its index in the queue?
- How far into the file had the user listened?
- What play history and upcoming queue context are required to resume correctly?
- What other state is necessary for a seamless continuation?

## Sorting and Filtering

Appropriate views should offer these sort orders:

- Most viewed / played
- Least viewed / played
- Unviewed / unplayed
- Most recently viewed / played
- Least recently viewed / played
- Most liked
- Recently added/indexed

Available filters should include:

- Filter out disliked files.
- When viewing disliked files, eventually provide an option to move or delete everything marked as disliked.

## Randomized Playlist and Queue Generation

### Goal

Randomization should help surface least-played or unheard audio while ignoring files with a negative like score. This is a critical longer-term feature area.

Generation should operate on the user's current scope rather than always using an entire collection. The original idea described scope as a selected folder path, media/file-type filter, and optional tag filters; the generated result should include only the currently filtered content.

The broader brainstorm mentioned images and videos when describing shared media filters. Those media types remain a possible cross-media exploration, while the current Resn8 MVP specification is audio-only.

### Generation methods

- **Ignore all disliked:** Create a random playlist containing all eligible files except those with a negative like score.
- **Unplayed:** Randomize all eligible files that have not yet been played.
- **Least played first:** Start with files having the lowest play count and randomize peers.
- **Most liked first:** Prioritize higher integer like scores; each Like action increases the score.
- **Most played first:** Prioritize files with the highest play count.

All priority modes should exclude disliked files by default.

### Persistence and restoration

- Generated order must be serializable.
- Resn8 should remember where the user stopped in that order.
- Reopening the app should be able to return to the last-viewed or last-playing media item and its queue context.

## Original Phase 2 Ideas

These ideas were originally grouped as Phase 2. Some may be promoted into the MVP by the specification, but their original intentions remain:

### Continue where I left off

- Reopen the app in the same context in which it was shut down or deactivated.
- Restore the selected playlist.
- Restore the playlist's sort/order.
- Restore the index and identity of the last-playing file.
- Restore enough play history and upcoming queue state to continue.
- Track the currently playing audio file.
- Track the selected playlist and its index.
- Track the listening position within the file.
- Preserve the open question of restoring equivalent context for video if Resn8 ever expands beyond audio.

### Listening history and playback

- Add logging that keeps a play history.
- Configure playback speed.

### Playlist generation

- Create playlists from the randomization algorithms described above.

### Library maintenance and discovery

- Schedule re-indexing.
- Add fast search inspired by Raycast or Alfred.

## Longer-Term Goals

- Support both horizontal and vertical screen layouts.
- Support multiple collections and multiple selected root folders.
- Expand contextual-folder and flat-folder collection experiences.
- Explore broader media filtering or video restoration only if the product later moves beyond its audio-first scope.
