package com.app.resn8.di

import com.app.resn8.data.repository.FakeCollectionRepository
import com.app.resn8.data.repository.FakeMediaRepository
import com.app.resn8.data.repository.FakePlaylistRepository
import com.app.resn8.data.repository.FakeQueueRepository
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.PlaylistRepository
import com.app.resn8.domain.repository.QueueRepository

interface AppContainer {
    val mediaRepository: MediaRepository
    val collectionRepository: CollectionRepository
    val playlistRepository: PlaylistRepository
    val queueRepository: QueueRepository
}

class DefaultAppContainer : AppContainer {
    override val mediaRepository: MediaRepository by lazy {
        FakeMediaRepository()
    }
    override val collectionRepository: CollectionRepository by lazy {
        FakeCollectionRepository()
    }
    override val playlistRepository: PlaylistRepository by lazy {
        FakePlaylistRepository()
    }
    override val queueRepository: QueueRepository by lazy {
        FakeQueueRepository()
    }
}
