package com.app.resn8.data.database

import androidx.room.TypeConverter
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.MetadataScanStatus
import com.app.resn8.domain.model.MetadataValueSource
import com.app.resn8.domain.model.PlaybackHistoryResult
import com.app.resn8.domain.model.QueueFilterSnapshot
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.model.ScanResult
import com.app.resn8.domain.model.SmartQueueMode
import com.app.resn8.domain.model.SortOrder
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @TypeConverter
    fun fromCollectionProfile(profile: CollectionProfile?): String? = profile?.name

    @TypeConverter
    fun toCollectionProfile(value: String?): CollectionProfile? =
        value?.let { enumValueOf<CollectionProfile>(it) }

    @TypeConverter
    fun fromMetadataScanStatus(status: MetadataScanStatus?): String? = status?.name

    @TypeConverter
    fun toMetadataScanStatus(value: String?): MetadataScanStatus? =
        value?.let { enumValueOf<MetadataScanStatus>(it) }

    @TypeConverter
    fun fromMetadataValueSource(source: MetadataValueSource?): String? = source?.name

    @TypeConverter
    fun toMetadataValueSource(value: String?): MetadataValueSource? =
        value?.let { enumValueOf<MetadataValueSource>(it) }

    @TypeConverter
    fun fromPlaybackHistoryResult(result: PlaybackHistoryResult?): String? = result?.name

    @TypeConverter
    fun toPlaybackHistoryResult(value: String?): PlaybackHistoryResult? =
        value?.let { enumValueOf<PlaybackHistoryResult>(it) }

    @TypeConverter
    fun fromRepeatMode(mode: RepeatMode?): String? = mode?.name

    @TypeConverter
    fun toRepeatMode(value: String?): RepeatMode? =
        value?.let { enumValueOf<RepeatMode>(it) }

    @TypeConverter
    fun fromSavedQueueKind(kind: SavedQueueKind?): String? = kind?.name

    @TypeConverter
    fun toSavedQueueKind(value: String?): SavedQueueKind? =
        value?.let { enumValueOf<SavedQueueKind>(it) }

    @TypeConverter
    fun fromSmartQueueMode(mode: SmartQueueMode?): String? = mode?.name

    @TypeConverter
    fun toSmartQueueMode(value: String?): SmartQueueMode? =
        value?.let { enumValueOf<SmartQueueMode>(it) }

    @TypeConverter
    fun fromSortOrder(order: SortOrder?): String? = order?.name

    @TypeConverter
    fun toSortOrder(value: String?): SortOrder? =
        value?.let { enumValueOf<SortOrder>(it) }

    @TypeConverter
    fun fromScanResult(result: ScanResult?): String? =
        result?.let { json.encodeToString(it) }

    @TypeConverter
    fun toScanResult(value: String?): ScanResult? =
        value?.let { runCatching { json.decodeFromString<ScanResult>(it) }.getOrNull() }

    @TypeConverter
    fun fromQueueFilterSnapshot(snapshot: QueueFilterSnapshot?): String? =
        snapshot?.let { json.encodeToString(it) }

    @TypeConverter
    fun toQueueFilterSnapshot(value: String?): QueueFilterSnapshot? =
        value?.let { runCatching { json.decodeFromString<QueueFilterSnapshot>(it) }.getOrNull() }

    @TypeConverter
    fun fromLibrarySurface(surface: com.app.resn8.domain.model.LibrarySurface?): String? = surface?.name

    @TypeConverter
    fun toLibrarySurface(value: String?): com.app.resn8.domain.model.LibrarySurface? =
        value?.let { enumValueOf<com.app.resn8.domain.model.LibrarySurface>(it) }

    @TypeConverter
    fun fromMetadataGroupKey(key: com.app.resn8.domain.model.MetadataGroupKey?): String? = key?.serialize()

    @TypeConverter
    fun toMetadataGroupKey(value: String?): com.app.resn8.domain.model.MetadataGroupKey? =
        com.app.resn8.domain.model.MetadataGroupKey.deserialize(value)

    @TypeConverter
    fun fromAvailabilityFilter(filter: com.app.resn8.domain.model.AvailabilityFilter?): String? = filter?.name

    @TypeConverter
    fun toAvailabilityFilter(value: String?): com.app.resn8.domain.model.AvailabilityFilter? =
        value?.let { enumValueOf<com.app.resn8.domain.model.AvailabilityFilter>(it) }

    @TypeConverter
    fun fromLibraryFilterSnapshot(snapshot: com.app.resn8.domain.model.LibraryFilterSnapshot?): String? =
        snapshot?.let { json.encodeToString(it) }

    @TypeConverter
    fun toLibraryFilterSnapshot(value: String?): com.app.resn8.domain.model.LibraryFilterSnapshot? =
        value?.let { runCatching { json.decodeFromString<com.app.resn8.domain.model.LibraryFilterSnapshot>(it) }.getOrNull() }
}
