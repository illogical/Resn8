package com.app.resn8.storage

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.app.resn8.storage.artwork.ArtworkCandidateIndex
import com.app.resn8.storage.indexer.ArtworkCandidate
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ArtworkCandidateIndexTest {
    @Test
    fun candidateSelection_prefersCoverThenStableFilenameOrder() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val index = ArtworkCandidateIndex(context)
        index.record(ArtworkCandidate(Uri.parse("content://art/front"), "Artist/Album/front.png", "front.png", 10, 1))
        index.record(ArtworkCandidate(Uri.parse("content://art/folder"), "Artist/Album/Folder.jpg", "Folder.jpg", 10, 1))
        index.record(ArtworkCandidate(Uri.parse("content://art/cover"), "Artist/Album/cover.jpg", "cover.jpg", 10, 1))

        index.publish("artwork-policy-test")

        assertEquals("content://art/cover", index.find("artwork-policy-test", "Artist/Album")?.documentUri)
    }
}
