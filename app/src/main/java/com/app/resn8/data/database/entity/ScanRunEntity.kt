package com.app.resn8.data.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "scan_runs",
    foreignKeys = [
        ForeignKey(
            entity = RootSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sourceId", "status"])
    ]
)
data class ScanRunEntity(
    @PrimaryKey val id: String,
    val sourceId: String,
    val status: String,
    val startedAt: Long,
    val completedAt: Long? = null,
    val scannedCount: Int = 0,
    val addedCount: Int = 0,
    val updatedCount: Int = 0,
    val unavailableCount: Int = 0,
    val errorSummary: String? = null
)
