STATUS: PLANNING

Update the TASKS.md's polish section with some observations based upon the following brainstorm:

1) Most important is a plan for a fix regarding swappaing collections and the app forgetting where the user had left off.
Instead it should remember the context where the user had left off when the user changes a collection so that when the user returns to the collection, they can continue playing from where they had left off.

2) Initial UI Suggestions
## Main Music Player View
* After the user starts playing a song from the Playlist tab and the user is viewing the main music player view/screen, there is a useful link "Playlist: <PLAYLIST_NAME>" above the album art placeholder. Take the following into account:
  * When the user clicks the link and they are taken to the Playlist tab in the context of the playlist that the current song is playing from, take the user to the currently-playing song in the playlist. Most often when the user uses that link from the main music player, they are wanting to check the upcoming song(s)/audio tracks.
  * Remove the line "Top to open playlist detail" instructions to save vertical space
  * Hide the mini player in the main player to provide larger, dedicated controls and give more room for the album art. The mini player is redundant here and the main player's audio controls are only a couple pixels tall in their current state. This can be improved.
  * Can we recover any of that vertical space below the top collection name/selector and above the the "Playlist: Rock" text/link? That currently feels like some wasted space.
## Settings tab's layout
* Regarding the Settings tab, It is currently chaotic and I think a standard settings menu with sub-menus would be useful as there may be other settings categories we want to add in the future. The first Settings submenus could be "Collections" and "About". Add breadcrumbs to get back to the main settings. The "About" should be straight forward, move the current About section into this as a settings page. As for the new Collections sub-menu:
  * Let's completely restructure the current collections functionality's layout. I am open to suggestions but my initial thought is to display a list of the current collections similar to the way this app's Playlists are presented. Instead of the playlist name, it would be the collection name. It could display the number of tracks in the collection underneath similar to the Playlists tab displays track counts for each playlist. I also like the Playlist tab's "+" button for opening a modal to give the new playlist a name. That could work for Collections as well via a page/view for an individual collection could provide the collection index summary section and the inputs for the collection name, the Music vs. Audio Files option, and the button to select a folder and a cancel button that would take them back to the list of collections. I assume this view could be mostly reused for the "+" for creating a new collection.
    * Upon selecting a folder to index it, can the indexing information be shown on this same individual collection settings view/page? It could display the index progress in the area where the final index summary information will be displayed.
  * The current "Library Actions" section could be removed and the 2 buttons ("Manual Re-index Library" and "Reselect Collection Folder...") could become hamburger menu options for each listed collection. The hamburger menu I am refering to is currently on each playlist in the Playlists tab where the rename and delete options currently reside. Would be nice to have options to rename and delete collections similar to how they are presented for Playlists.
    * Rename "Reselect Collection Folder (SAF Permission)" and make it shorter to at least "Reselect Collection Folder"
* Deleting playlists or Collections should provide a confirmation modal before continuing with the option to cancel.
## Fonts are too large
* Font sizes for song/audio file names and album names can be reduced to prevent so many names from being cut off
## Any views displaying selection toggles foe individaul items do not need hamburger menus.
* The folders view is an example where the left-side hamburger menu for each item is redundant and could provide back some horizontal space. Instead, just rely on the checkboxes to select one or more files and the "Add to Playlist" summary section has a button for adding to a playlist so the one and only hamburger menu option is the redundant option to add a single item a playlist. I don't mind it now taking 2 taps to add a single item (one to toggle the item as checked and the other to click the "Add to Playlist" button) since more often than not, a user will be selecting more than 1 item at a time.
* I like the selection summary section/area shown when one or more files are selected. The text in it currently wraps too much so help me decide how to improve its layout. Can the clear link be moved below the Add to Playlist button to save some horizontal space? I am open to options if you can imagine a better/concise layout.
  * Also the summary shows "Selected: x files" and "x Folders". Remove the "x unique audio files" text to save some space.
  * Move the selection summary area and its contents to the bottom of the list instead of the top of the list because it makes the list jump down when it is currently displayed at the top of the list when the first item is selected.
## Library tab
* Remove the "Folders" option next to "All Tracks" because it is redundant to the Folders tab at the bottom. This also should leave more horizontal space for the other tab titles (Artists, Albums, and All Tracks).
* Provide the same improvements when viewing any list of tracks where the checkboxes can be used to select one or more items so there is no need for the hamburger menus for each item that only display a single, redundant "Add to Playlist" option. Also move the improved selection summary container from the top of the list to the bottom of the list.
