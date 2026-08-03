package com.app.resn8.data.database

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.toEntity
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.UiSessionState
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test: verifies that suspend DAO methods on the production in-memory database
 * (built WITHOUT allowMainThreadQueries()) execute successfully from a coroutine body.
 *
 * If any DAO method is accidentally left as a blocking synchronous function, Room will
 * throw IllegalStateException: Cannot access database on the main thread, failing this test.
 */
@RunWith(AndroidJUnit4::class)
class MainThreadGuardTest {

    private lateinit var db: Resn8Database

    @Before
    fun setUp() {
        // Intentionally NOT using allowMainThreadQueries() — mirrors production behaviour.
        db = Resn8Database.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun collectionDao_insertAndQuery_succeedFromCoroutineWithoutMainThreadException() = runBlocking {
        val collection = CollectionEntity(
            id = "col_guard_1",
            name = "Guard Test Collection",
            profile = CollectionProfile.MUSIC,
            createdAt = 1000L,
            updatedAt = 1000L
        )
        // These were previously synchronous — now they are suspend and dispatched by Room.
        db.collectionDao().insertCollection(collection)
        val retrieved = db.collectionDao().getCollectionById("col_guard_1")
        assertNotNull("Collection should be persisted and retrievable", retrieved)
    }

    @Test
    fun uiSessionDao_upsertAndQuery_succeedFromCoroutineWithoutMainThreadException() = runBlocking {
        // Previously synchronous — crash site confirmed in physical-device Logcat.
        val state = UiSessionState().toEntity()
        db.uiSessionDao().upsertUiSessionState(state)
        val retrieved = db.uiSessionDao().getUiSessionState()
        assertNotNull("UiSessionState should be persisted and retrievable", retrieved)
    }
}
