package com.app.resn8.data.repository

import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.repository.CollectionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class RoomCollectionRepository(
    private val db: Resn8Database
) : CollectionRepository {
    private val collectionDao = db.collectionDao()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    override fun getCollectionsFlow(): Flow<List<Collection>> {
        return collectionDao.getCollectionsFlow().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getCollectionById(id: String): Collection? {
        return collectionDao.getCollectionById(id)?.toDomain()
    }

    override suspend fun createCollection(name: String): Collection {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be blank" }
        val collection = Collection(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            profile = CollectionProfile.MUSIC,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        collectionDao.insertCollection(
            CollectionEntity(
                id = collection.id,
                name = collection.name,
                profile = collection.profile,
                createdAt = collection.createdAt,
                updatedAt = collection.updatedAt
            )
        )
        return collection
    }

    override fun getRootSourcesFlow(collectionId: String): Flow<List<RootSource>> {
        return collectionDao.getRootSourcesFlow(collectionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun addRootSource(collectionId: String, treeUri: String, displayName: String): RootSource {
        val existing = collectionDao.getRootSourceByTreeUri(treeUri)
        if (existing != null) {
            return existing.toDomain()
        }
        val rootSource = RootSource(
            id = UUID.randomUUID().toString(),
            collectionId = collectionId,
            treeUri = treeUri,
            displayName = displayName,
            isAvailable = true
        )
        collectionDao.insertRootSource(
            RootSourceEntity(
                id = rootSource.id,
                collectionId = rootSource.collectionId,
                treeUri = rootSource.treeUri,
                displayName = rootSource.displayName,
                isAvailable = rootSource.isAvailable,
                lastScanStatus = null,
                lastScannedAt = null,
                lastScanStartedAt = null,
                lastScanCompletedAt = null,
                lastScanSummary = null
            )
        )
        return rootSource
    }

    override suspend fun updateRootSourceAvailability(sourceId: String, isAvailable: Boolean) {
        collectionDao.updateRootSourceAvailability(sourceId, isAvailable)
    }

    override suspend fun updateRootScanState(
        sourceId: String,
        status: String,
        startedAt: Long?,
        completedAt: Long?,
        summary: ScanResult?
    ) {
        val summaryJson = summary?.let { json.encodeToString(it) }
        collectionDao.updateRootScanState(sourceId, status, startedAt, completedAt, summaryJson)
    }
}
