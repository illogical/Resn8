package com.app.resn8.data.repository

import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.CollectionNameConflictException
import com.app.resn8.domain.model.CollectionSourceConflictException
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.normalizeCollectionName
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

    override suspend fun createCollection(name: String, profile: CollectionProfile): Collection {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be blank" }
        require(profile == CollectionProfile.MUSIC || profile == CollectionProfile.FLAT)
        if (_collections.value.any { it.normalizedName == normalizeCollectionName(trimmed) }) {
            throw CollectionNameConflictException(trimmed)
        }
        val newCollection = Collection(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            profile = profile
        )
        _collections.value = _collections.value + newCollection
        return newCollection
    }

    override suspend fun createCollectionWithSource(
        name: String,
        profile: CollectionProfile,
        treeUri: String,
        displayName: String
    ): Pair<Collection, RootSource> {
        if (_rootSources.value.any { it.treeUri == treeUri }) {
            throw CollectionSourceConflictException("That folder is already used by another collection")
        }
        val collection = createCollection(name, profile)
        return try {
            collection to addRootSource(collection.id, treeUri, displayName)
        } catch (error: Exception) {
            _collections.value = _collections.value.filterNot { it.id == collection.id }
            throw error
        }
    }

    override suspend fun renameCollection(collectionId: String, name: String): Collection {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be blank" }
        val normalized = normalizeCollectionName(trimmed)
        if (_collections.value.any { it.id != collectionId && it.normalizedName == normalized }) {
            throw CollectionNameConflictException(trimmed)
        }
        val existing = _collections.value.find { it.id == collectionId }
            ?: throw IllegalArgumentException("Collection does not exist")
        val updated = existing.copy(name = trimmed, normalizedName = normalized, updatedAt = System.currentTimeMillis())
        _collections.value = _collections.value.map { if (it.id == collectionId) updated else it }
        return updated
    }

    override fun getRootSourcesFlow(collectionId: String): Flow<List<RootSource>> {
        return _rootSources.map { sources -> sources.filter { it.collectionId == collectionId } }
    }

    override suspend fun getRootSourceById(sourceId: String): RootSource? =
        _rootSources.value.find { it.id == sourceId }

    override suspend fun addRootSource(collectionId: String, treeUri: String, displayName: String): RootSource {
        if (_rootSources.value.any { it.collectionId == collectionId }) {
            throw CollectionSourceConflictException("Each collection can use one collection folder")
        }
        if (_rootSources.value.any { it.treeUri == treeUri }) {
            throw CollectionSourceConflictException("That folder is already used by another collection")
        }
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

    override suspend fun reselectRootSource(sourceId: String, treeUri: String): RootSource {
        if (_rootSources.value.any { it.id != sourceId && it.treeUri == treeUri }) {
            throw CollectionSourceConflictException("That folder is already used by another collection")
        }
        val existing = _rootSources.value.find { it.id == sourceId }
            ?: throw IllegalArgumentException("Collection folder does not exist")
        val updated = existing.copy(treeUri = treeUri, isAvailable = true)
        _rootSources.value = _rootSources.value.map { if (it.id == sourceId) updated else it }
        return updated
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
