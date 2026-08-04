package com.app.resn8.domain.repository

import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.RootSource
import com.app.resn8.domain.model.ScanResult
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun getCollectionsFlow(): Flow<List<Collection>>
    suspend fun getCollectionById(id: String): Collection?
    suspend fun createCollection(name: String, profile: CollectionProfile = CollectionProfile.MUSIC): Collection
    suspend fun createCollectionWithSource(
        name: String,
        profile: CollectionProfile,
        treeUri: String,
        displayName: String
    ): Pair<Collection, RootSource>
    suspend fun renameCollection(collectionId: String, name: String): Collection
    fun getRootSourcesFlow(collectionId: String): Flow<List<RootSource>>
    suspend fun getRootSourceById(sourceId: String): RootSource?
    suspend fun addRootSource(collectionId: String, treeUri: String, displayName: String): RootSource
    suspend fun reselectRootSource(sourceId: String, treeUri: String): RootSource
    suspend fun updateRootSourceAvailability(sourceId: String, isAvailable: Boolean)
    suspend fun updateRootScanState(
        sourceId: String,
        status: String,
        startedAt: Long? = null,
        completedAt: Long? = null,
        summary: ScanResult? = null
    )
}
