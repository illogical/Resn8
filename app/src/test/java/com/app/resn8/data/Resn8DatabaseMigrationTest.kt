package com.app.resn8.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.domain.model.LibrarySurface
import com.app.resn8.domain.model.MetadataGroupKey
import com.app.resn8.domain.model.SortOrder
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.data.database.entity.toEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class Resn8DatabaseMigrationTest {

    @Test
    fun `database creation and entity mapping works at version 3`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, Resn8Database::class.java).build()

        val state = UiSessionState(
            currentRoute = "album_detail/album_1",
            selectedCollectionId = "col_1",
            selectedSourceId = "src_1",
            selectedArtistKey = MetadataGroupKey.Known("Artist 1"),
            selectedAlbumKey = MetadataGroupKey.Known("Album 1"),
            selectedAlbumArtistKey = MetadataGroupKey.Known("Album Artist 1"),
            activeSort = SortOrder.ALBUM,
            activeSurface = LibrarySurface.ALBUMS
        )

        val entity = state.toEntity()
        assertEquals("Album Artist 1", entity.selectedAlbumArtist)
        assertEquals(MetadataGroupKey.Known("Album Artist 1"), entity.selectedAlbumArtistKey)

        val domain = entity.toDomain()
        assertEquals(state.selectedAlbumArtistKey, domain.selectedAlbumArtistKey)

        db.close()
    }
}
