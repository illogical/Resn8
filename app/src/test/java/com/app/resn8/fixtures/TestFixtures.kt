package com.app.resn8.fixtures

import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MediaFile
import com.app.resn8.domain.model.Playlist
import com.app.resn8.domain.model.PlaylistItem
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.SmartQueueMode
import java.util.Random

class FakeClock(var currentTimeMs: Long = 1000000000000L) {
    fun advanceBy(ms: Long) {
        currentTimeMs += ms
    }
}

class FakeRandom(seed: Long = 42L) {
    private val random = Random(seed)

    fun nextInt(until: Int): Int = if (until > 0) random.nextInt(until) else 0
    fun <T> shuffle(list: List<T>): List<T> = list.shuffled(random)
}

fun createTestMediaFile(
    id: String = "media_1",
    sourceId: String = "source_1",
    folderId: String = "folder_1",
    documentUri: String = "content://com.android.providers.media/1",
    relativePath: String = "Artist/Album/01 - Track.mp3",
    filename: String = "01 - Track.mp3",
    displayTitle: String = "Track One",
    mimeType: String = "audio/mpeg",
    size: Long = 1024L * 1024L,
    durationMs: Long = 180000L,
    modifiedTimeMs: Long = 1000000L,
    isAvailable: Boolean = true,
    title: String? = "Track One",
    artist: String? = "Test Artist",
    albumArtist: String? = "Test Artist",
    album: String? = "Test Album",
    discNumber: Int? = 1,
    trackNumber: Int? = 1,
    year: Int? = 2026,
    genre: String? = "Rock",
    artworkUri: String? = null,
    playCount: Int = 0,
    lastPlayedAt: Long? = null,
    likeScore: Int = 0
): MediaFile = MediaFile(
    id = id,
    sourceId = sourceId,
    folderId = folderId,
    documentUri = documentUri,
    relativePath = relativePath,
    filename = filename,
    displayTitle = displayTitle,
    mimeType = mimeType,
    size = size,
    durationMs = durationMs,
    modifiedTimeMs = modifiedTimeMs,
    isAvailable = isAvailable,
    title = title,
    artist = artist,
    albumArtist = albumArtist,
    album = album,
    discNumber = discNumber,
    trackNumber = trackNumber,
    year = year,
    genre = genre,
    artworkUri = artworkUri,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    likeScore = likeScore
)

fun createTestCollection(
    id: String = "col_1",
    name: String = "Main Music",
    profile: CollectionProfile = CollectionProfile.MUSIC
): Collection = Collection(
    id = id,
    name = name,
    profile = profile
)

fun createTestRootSource(
    id: String = "source_1",
    collectionId: String = "col_1",
    treeUri: String = "content://com.android.externalstorage.documents/tree/primary%3AMusic",
    displayName: String = "Music"
): RootSource = RootSource(
    id = id,
    collectionId = collectionId,
    treeUri = treeUri,
    displayName = displayName
)

fun createTestPlaylist(
    id: String = "playlist_1",
    collectionId: String = "col_1",
    name: String = "My Favorites"
): Playlist = Playlist(
    id = id,
    collectionId = collectionId,
    name = name
)

fun createTestSavedQueue(
    id: String = "queue_1",
    collectionId: String = "col_1",
    kind: SavedQueueKind = SavedQueueKind.EXPLICIT,
    mode: SmartQueueMode? = null,
    orderedMediaIds: List<String> = listOf("media_1", "media_2")
): SavedQueue = SavedQueue(
    id = id,
    collectionId = collectionId,
    kind = kind,
    mode = mode,
    orderedMediaIds = orderedMediaIds
)
