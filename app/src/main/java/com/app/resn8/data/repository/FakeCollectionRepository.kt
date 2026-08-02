package com.app.resn8.data.repository

import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import java.util.UUID

class FakeCollectionRepository(
    initialCollections: List<Collection> = emptyList(),
    initialRootSources: List<RootSource> = emptyList()
) : CollectionRepository {

    private val _collections = MutableStateFlow(initialCollections)
    private val _rootSources = MutableStateFlow(initialRootSources)

    override fun getCollectionsFlow(): Flow<List<Collection>> = _collections

    override suspend fun getCollectionById(id: String): Collection? {
        return _collections.value.find { it.id == id }
    }

    override suspend fun createCollection(name: String): Collection {
        val newCollection = Collection(
            id = UUID.randomUUID().toString(),
            name = name,
            profile = CollectionProfile.MUSIC
        )
        _collections.value = _collections.value + newCollection
        return newCollection
    }

    override fun getRootSourcesFlow(collectionId: String): Flow<List<RootSource>> {
        return _rootSources.map { sources -> sources.filter { it.collectionId == collectionId } }
    }

    override suspend fun addRootSource(collectionId: String, treeUri: String, displayName: String): RootSource {
        val newSource = RootSource(
            id = UUID.randomUUID().toString(),
            collectionId = collectionId,
            treeUri = treeUri,
            displayName = displayName,
            isAvailable = true
        )
        _rootSources.value = _rootSources.value + newSource
        return newSource
    }

    override suspend fun updateRootSourceAvailability(sourceId: String, isAvailable: Boolean) {
        _rootSources.value = _rootSources.value.map {
            if (it.id == sourceId) it.copy(isAvailable = isAvailable) else it
        }
    }

    override suspend fun updateRootScanState(
        sourceId: String,
        status: String,
        startedAt: Long?,
        completedAt: Long?,
        summary: ScanResult?
    ) {
        _rootSources.value = _rootSources.value.map {
            if (it.id == sourceId) {
                it.copy(
                    lastScanStatus = status,
                    lastScanStartedAt = startedAt ?: it.lastScanStartedAt,
                    lastScanCompletedAt = completedAt ?: it.lastScanCompletedAt,
                    lastScannedAt = completedAt ?: it.lastScannedAt,
                    lastScanSummary = summary ?: it.lastScanSummary
                )
            } else it
        }
    }
}
