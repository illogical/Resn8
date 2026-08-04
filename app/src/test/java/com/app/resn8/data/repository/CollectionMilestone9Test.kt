package com.app.resn8.data.repository

import com.app.resn8.domain.model.CollectionNameConflictException
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.CollectionSourceConflictException
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class CollectionMilestone9Test {
    @Test
    fun createAndRenameUseNormalizedUniqueNames() = runTest {
        val repository = FakeCollectionRepository()
        val music = repository.createCollection("Music", CollectionProfile.MUSIC)
        val audio = repository.createCollection("Audio Files", CollectionProfile.FLAT)

        assertEquals(CollectionProfile.FLAT, audio.profile)
        try {
            repository.createCollection("  MUSIC  ", CollectionProfile.FLAT)
            fail("Expected duplicate collection name to fail")
        } catch (_: CollectionNameConflictException) {}
        try {
            repository.renameCollection(audio.id, "music")
            fail("Expected duplicate rename to fail")
        } catch (_: CollectionNameConflictException) {}
        assertEquals("My Music", repository.renameCollection(music.id, " My Music ").name)
    }

    @Test
    fun eachCollectionHasOneUniqueFolder() = runTest {
        val repository = FakeCollectionRepository()
        val first = repository.createCollectionWithSource(
            "Music", CollectionProfile.MUSIC, "content://music", "Music"
        )
        val second = repository.createCollection("Audio", CollectionProfile.FLAT)

        try {
            repository.addRootSource(first.first.id, "content://other", "Other")
            fail("Expected a second folder to fail")
        } catch (_: CollectionSourceConflictException) {}
        try {
            repository.addRootSource(second.id, "content://music", "Audio")
            fail("Expected a duplicate folder to fail")
        } catch (_: CollectionSourceConflictException) {}
    }

    @Test
    fun atomicCollectionSourceCreationDoesNotLeaveAnOrphanOnConflict() = runTest {
        val repository = FakeCollectionRepository()
        repository.createCollectionWithSource(
            "Music", CollectionProfile.MUSIC, "content://shared", "Music"
        )

        try {
            repository.createCollectionWithSource(
                "Audio", CollectionProfile.FLAT, "content://shared", "Audio"
            )
            fail("Expected duplicate source creation to fail")
        } catch (_: CollectionSourceConflictException) {}

        assertEquals(listOf("Music"), repository.getCollectionsFlow().first().map { it.name })
    }
}
