package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile

@Entity(
    tableName = "collections",
    indices = [Index(value = ["normalizedName"], unique = true)]
)
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profile: CollectionProfile,
    val createdAt: Long,
    val updatedAt: Long,
    val normalizedName: String = com.app.resn8.domain.model.normalizeCollectionName(name)
)

fun CollectionEntity.toDomain() = Collection(
    id = id,
    name = name,
    profile = profile,
    createdAt = createdAt,
    updatedAt = updatedAt,
    normalizedName = normalizedName
)

fun Collection.toEntity() = CollectionEntity(
    id = id,
    name = name,
    profile = profile,
    createdAt = createdAt,
    updatedAt = updatedAt,
    normalizedName = normalizedName
)
