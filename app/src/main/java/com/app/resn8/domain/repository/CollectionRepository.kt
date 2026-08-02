package com.app.resn8.domain.repository

import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.RootSource
import kotlinx.coroutines.flow.Flow

interface CollectionRepository {
    fun getCollectionsFlow(): Flow<List<Collection>>
    suspend fun getCollectionById(id: String): Collection?
    suspend fun createCollection(name: String): Collection
    fun getRootSourcesFlow(collectionId: String): Flow<List<RootSource>>
    suspend fun addRootSource(collectionId: String, treeUri: String, displayName: String): RootSource
}
