package com.app.resn8.di

import android.content.Context
import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.repository.FakeCollectionRepository
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.data.repository.FakePlaylistRepository
import com.app.resn8.data.repository.FakeQueueRepository
import com.app.resn8.data.repository.RoomCollectionRepository
import com.app.resn8.data.repository.RoomMediaRepository
import com.app.resn8.data.repository.RoomPlaylistRepository
import com.app.resn8.data.repository.RoomQueueRepository
import com.app.resn8.data.repository.RoomUiSessionRepository
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.PlaylistRepository
import com.app.resn8.domain.repository.QueueRepository
import com.app.resn8.domain.repository.UiSessionRepository

interface AppContainer {
    val database: Resn8Database?
    val mediaRepository: MediaRepository
    val collectionRepository: CollectionRepository
    val playlistRepository: PlaylistRepository
    val queueRepository: QueueRepository
    val uiSessionRepository: UiSessionRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    override val database: Resn8Database by lazy {
        Resn8Database.buildDatabase(context)
    }

    override val mediaRepository: MediaRepository by lazy {
        RoomMediaRepository(database)
    }
    override val collectionRepository: CollectionRepository by lazy {
        RoomCollectionRepository(database)
    }
    override val playlistRepository: PlaylistRepository by lazy {
        RoomPlaylistRepository(database)
    }
    override val queueRepository: QueueRepository by lazy {
        RoomQueueRepository(database)
    }
    override val uiSessionRepository: UiSessionRepository by lazy {
        RoomUiSessionRepository(database)
    }
}

class TestAppContainer(
    override val mediaRepository: MediaRepository = FakeMediaRepository(),
    override val collectionRepository: CollectionRepository = FakeCollectionRepository(),
    override val playlistRepository: PlaylistRepository = FakePlaylistRepository(),
    override val queueRepository: QueueRepository = FakeQueueRepository(),
    override val database: Resn8Database? = null
) : AppContainer {
    override val uiSessionRepository: UiSessionRepository by lazy {
        if (database != null) RoomUiSessionRepository(database) else object : UiSessionRepository {
            private val _state = kotlinx.coroutines.flow.MutableStateFlow(com.app.resn8.domain.model.UiSessionState())
            override fun getUiSessionStateFlow() = _state
            override suspend fun saveUiSessionState(state: com.app.resn8.domain.model.UiSessionState) {
                _state.value = state
            }
        }
    }
}
