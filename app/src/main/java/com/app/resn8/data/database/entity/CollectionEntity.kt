package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.app.resn8.domain.model.Collection
import com.app.resn8.domain.model.CollectionProfile

@Entity(tableName = "collections")
data class CollectionEntity(
    @PrimaryKey val id: String,
    val name: String,
    val profile: CollectionProfile,
    val createdAt: Long,
    val updatedAt: Long
)

fun CollectionEntity.toDomain() = Collection(
    id = id,
    name = name,
    profile = profile,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun Collection.toEntity() = CollectionEntity(
    id = id,
    name = name,
    profile = profile,
    createdAt = createdAt,
    updatedAt = updatedAt
)
