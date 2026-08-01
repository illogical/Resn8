# MVP Requirements

- Simple player in the center
    - Standard music playback controls
        - Additionally Like/Dislike buttons
            - Will store likes as an integer and will add 1 for like and -1 for dislike. This can be treated like a score because the more times I like something, the higher its priority for later features regarding randomization algorithms which will be a critical longer-term feature set.
    - Double clicking in whitespace area or a 2-finger tap could slide-in additional controls to be able to
        - “Add to playlist…” button
            - Re-useable Playlist selection component
                - Ideally would display first all of the playlists the file is already added to then display the list of available playlists that the file could be added to
                - A checkbox when checked the file is added to that playlist and if unchecked then the file is removed from that playlist.
                - The selection menu should stay open until dismissed so that the user can select one or more playlists (or remove from one or more playlists)
    - Metadata may differ for 2 different types of media imports/indexing. Help me decide if they can share a data shema.
    - For the first scenario to cover, focus on my organized music library that I would copy to the hard drive or sd card of the Android device. I can manage the files so that is out of scope.
        - Need an elegant/performant way to display audio files filtered and/or sorted by
            - Artist
            - Album
            - Would also want to be able to select an Artist to filter to only its albums
                - And be able to select an album to filter to only view its songs
- Media Management
    - Selecting and indexing media folders
        - 3 intial use-cases for selecting a folder on the Android device (either in shared storage on the internal drive or on an SD card). The MVP can just be selecting a single folder, that may or may not contain subfolders]
            - Selecting the root folder containing an organized music collection where the folder structure can inform the critical metadata that I would like to be able to sort/filter by: Artist→Albums→Songs [and Track #] where often the song files are MP3s with a naming convention that contains the album’s orginal track numbers. Expect to need to parse the filenames and we may need a script to detect if there are more than 1 naming convention used when we index the first sample directory.
                - What would it take to access additional MP3 metadata? Could there be information stored in the MP3 file?
            - Selecting the root folder that may contain audio files or a contextual folder structure containing subfolders that contain audio files. We can assume the subfolders can instruct categories that we could sort and filter files by.
                - Example
                    - Collection name example: Podcasts
                    - I use the built-in folder selector to select a folder that contains subfolders such as Software Development, AI, Psychology and each of those folders contains MP3 files to index into a database for fast sorting/filtering
                        - Maybe instead of Artist→Album→Song names, these folders structures could be Context→Category→File names
            - Selecting a folder that contains audio files and in this case has no subdirectories. There would be no folder structure to categorize by. We will need a way to display these files when it comes time to implement playlist management features.
        - Will need to take into account a button to re-index a selected folder or selected folders in a collection to take into account me manually adding new files (or very rarely removing or renaming files to clean up) to the Android device’s shared storage.
            - Scheduled re-indexing can be a longer-term goal
- Playlist schema for serialization
    - When indexing files for the first time, I will assume this is the overall data structure and can envision so far: Collections→1 or more selected folders→Optional Subfolders as relative paths→Audio Files
    - Need a couple of methods of adding audio files to a playlist
        - Easiest, I assume is by a button the main player interface to open a playlist selector that displays all existing playlists, in the current collection, and checkboxes whether or not the currently-playing audio file is in each playlist.
        - Need to be able to view the indexed folder structure to select multi-select audio files and a button to open that same playlist selector to bulk-add files to a playlist
            - Should be able to select a sub-folder of a collection
- File browser
    - Need a UI for viewing a collection’s folder/file structure
        - To be able to review what subfolders and audio files are currently indexed
        - To be able to select folders and files to add them to playlists
        - How to sort albums that have track numbers for folders containing ripped music?
        - Fallback to alphabetical for audio-only rips from videos
            - Would not have as much metadata as music files, I assume.
- Playlist browser
    - Simple manual play ordering and adding/removing files
        - Easy drag and drop but also buttons for Move to Top and Move to Bottom for each file
    - Create new playlists and delete w/ confirmation

## Randomization

I want to support playlist generation randomization algorithms with the goal of surfacing least-played audio files and ignoring disliked audio files (where the like count is negative).

Media will be viewed based upon a selected folder path, a file type filter (images or videos), and optional applied tags for filtering. The randomization options should be able to be applied to the current media file filters (randomize just the currently-filtered lists of content).

### Support for Starting from Where the User Left Off

This randomization needs to support being serialized so that it can be remembered where the user had left off. We want to be able to start the app in the context of the last-viewed media file.

### Playlist Randomization Methods

- Ignore All Disliked
    - Creates a random playlist containing all files in the collection except for any that are disliked (like count is negative)
- These will all assume disliked files are excluded as well:
    - Ability to generate a randomized playlist of all files that have not been played yet
    - Ability to generate  a playlist prioritizing the least-played file(s)
        - Creates a randomized list starting with files with the lowest play count
    - Ability to generate  a playlist prioritizing the most-liked files
        - The like button will be stored as an integer where each time the like button is clicked for an media file, the integer will be incremented.
    - Ability to generate a playlist that prioritizes the most-played files

## Supported Sort Orders

 Appropriate views should have an option to sort by:

- Most viewed
- Least viewed
- Unviewed
- Most recently viewed
- Least recently viewed
- Most liked

# Available Filters

- Filter out disliked
    - When filtered by dislikes, provide an option to “delete” (or move) all that are marked as disliked

## UI Views Brainstorm

- Main Music Player
    - Previous
    - Play/Pause
    - Next
    - Like
    - Dislike
    - Add to Playlist
        - Checklist of all playlists in this collection
- Playlist Selector
    - A re-useable context menu or modal that
- Media collections
    - View which collections exist and/or creating new collections or deleting existing ones
    - File browser to view the folder raw folder structure that was indexed for a collection
        - We can begin with a single collection and a single root folder selection to keep an MVP simple and make future plans to support multiple collections and multiple selected root folders.
        - Need to be able to multi-select indexed files to add via the Playlist Selector
        - Need to be able to view the indexed folder structure to multi-select audio files [or a folder] and a button too add all selected files to one or more playlists via the Playlist Selector
            - Should be able to select a sub-folder of a collection to add all files in it to one or more playlists via the Playlist selector
                - Need to be able to view the indexed folder structure to select multi-select audio files and a button to open that same playlist selector to bulk-add files to a playlist
                - Should be able to select a sub-folder of a collection
- Playlist browser
    - View existing playlists and abilities to create new playlists and delete existing ones
    - View the playlist’s file list
        - Be able to view it in queue order
        - Simple search filter to be able to confirm if a file is already included in a playlist
            
            

## Schema Brainstorm

- Collections
    - One or more folders that contain audio file libraries (needs to account for detecting files in subfolders)
        - Store the selected paths. These are the files we will want to index/keep a cache of
    - Playlists
    - View by folder paths
    - LastPlayed metadata (to support starting from where the user had left off)
        - Which playlist was list opened/selected
        - Index of which file was playing
        - Ideally also know how far into the file the user had made it to start the player from that same place in the duration of the file that was being listened to
        - Anything else necessary for continuing a playlist from where the user had left off
    - MediaFiles
        - ID
        - RelativeFolderPath
        - Filename
        - PlayCount
        - LastPlayed (timestamp)
        - LikeCount (0 means it has not yet been rated, negative means disliked, and positive means liked)

# Phase 2

- Continue where I left off…
    - When I reopen the app, it should be able to automatically return me to the same context as when I last shut down or deactivated the app
        - Needs to return the user to the same selected Playlist, the same order the playlist was sorted in, the index of the last playing file (so it technically can restore the play history and upcoming
    - Needs to track which audio file is currently playing
    - Track index of which playlist was selected
- Playlist generation
    - Be able to create playlists based upon randomization algorthms
        - 
- Configure playback speed
- Tracking where the user left off
    - Which playlist?
    - Which video?
- Scheduled re-indexing
- Search inspired by Raycast/Alfred search

# Longer-Term Goals

- Support for horizontal and vertical screen layouts