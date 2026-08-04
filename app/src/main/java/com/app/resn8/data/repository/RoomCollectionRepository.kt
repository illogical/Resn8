package com.app.resn8.data.repository

import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.CollectionEntity
import com.app.resn8.data.database.entity.RootSourceEntity
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.CollectionNameConflictException
import com.app.resn8.domain.model.CollectionSourceConflictException
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.normalizeCollectionName
import com.app.resn8.domain.repository.CollectionRepository
import androidx.room.withTransaction
import android.database.sqlite.SQLiteConstraintException
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

    override suspend fun createCollection(name: String, profile: CollectionProfile): Collection {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be blank" }
        require(profile == CollectionProfile.MUSIC || profile == CollectionProfile.FLAT) {
            "Only Music and Audio Files collections can be created"
        }
        val normalizedName = normalizeCollectionName(trimmed)
        if (collectionDao.getCollectionByNormalizedName(normalizedName) != null) {
            throw CollectionNameConflictException(trimmed)
        }
        val collection = Collection(
            id = UUID.randomUUID().toString(),
            name = trimmed,
            profile = profile,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis(),
            normalizedName = normalizedName
        )
        try {
            collectionDao.insertCollection(
                CollectionEntity(
                    id = collection.id,
                    name = collection.name,
                    profile = collection.profile,
                    createdAt = collection.createdAt,
                    updatedAt = collection.updatedAt
                )
            )
        } catch (_: SQLiteConstraintException) {
            throw CollectionNameConflictException(trimmed)
        }
        return collection
    }

    override suspend fun createCollectionWithSource(
        name: String,
        profile: CollectionProfile,
        treeUri: String,
        displayName: String
    ): Pair<Collection, RootSource> = db.withTransaction {
        if (collectionDao.getRootSourceByTreeUri(treeUri) != null) {
            throw CollectionSourceConflictException("That folder is already used by another collection")
        }
        val collection = createCollection(name, profile)
        val source = addRootSource(collection.id, treeUri, displayName)
        collection to source
    }

    override suspend fun renameCollection(collectionId: String, name: String): Collection {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Collection name cannot be blank" }
        val existing = collectionDao.getCollectionById(collectionId)
            ?: throw IllegalArgumentException("Collection does not exist")
        val normalizedName = normalizeCollectionName(trimmed)
        collectionDao.getCollectionByNormalizedName(normalizedName)?.let { conflict ->
            if (conflict.id != collectionId) throw CollectionNameConflictException(trimmed)
        }
        val updatedAt = System.currentTimeMillis()
        try {
            check(collectionDao.renameCollection(collectionId, trimmed, normalizedName, updatedAt) == 1) {
                "Collection rename failed"
            }
        } catch (_: SQLiteConstraintException) {
            throw CollectionNameConflictException(trimmed)
        }
        return existing.copy(name = trimmed, normalizedName = normalizedName, updatedAt = updatedAt).toDomain()
    }

    override fun getRootSourcesFlow(collectionId: String): Flow<List<RootSource>> {
        return collectionDao.getRootSourcesFlow(collectionId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getRootSourceById(sourceId: String): RootSource? =
        collectionDao.getRootSourceById(sourceId)?.toDomain()

    override suspend fun addRootSource(collectionId: String, treeUri: String, displayName: String): RootSource {
        require(collectionDao.getCollectionById(collectionId) != null) { "Collection does not exist" }
        if (collectionDao.getRootSourcesForCollection(collectionId).isNotEmpty()) {
            throw CollectionSourceConflictException("Each collection can use one collection folder")
        }
        val existing = collectionDao.getRootSourceByTreeUri(treeUri)
        if (existing != null) {
            throw CollectionSourceConflictException("That folder is already used by another collection")
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

    override suspend fun reselectRootSource(sourceId: String, treeUri: String): RootSource {
        val source = collectionDao.getRootSourceById(sourceId)
            ?: throw IllegalArgumentException("Collection folder does not exist")
        collectionDao.getRootSourceByTreeUri(treeUri)?.let { conflict ->
            if (conflict.id != sourceId) {
                throw CollectionSourceConflictException("That folder is already used by another collection")
            }
        }
        check(collectionDao.reselectRootSource(sourceId, treeUri) == 1) {
            "Collection folder reselection failed"
        }
        return source.copy(treeUri = treeUri, isAvailable = true).toDomain()
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
