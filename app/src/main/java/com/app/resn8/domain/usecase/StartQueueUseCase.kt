package com.app.resn8.domain.usecase

import com.app.resn8.domain.model.AvailabilityFilter
import com.app.resn8.domain.model.QueueStartRequest
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.SavedQueueKind
import com.app.resn8.domain.repository.MediaRepository
import com.app.resn8.domain.repository.PlaylistRepository
import com.app.resn8.domain.repository.QueueRepository
import com.app.resn8.domain.repository.UiSessionRepository
import kotlinx.coroutines.flow.first
import java.util.UUID

class StartQueueUseCase(
    private val mediaRepository: MediaRepository,
    private val playlistRepository: PlaylistRepository,
    private val queueRepository: QueueRepository,
    private val uiSessionRepository: UiSessionRepository
) {
    suspend operator fun invoke(request: QueueStartRequest): Result<SavedQueue> {
        val orderedMediaIds = when (request) {
            is QueueStartRequest.Library -> {
                val availableOnlyQuery = request.query.copy(
                    filters = request.query.filters.copy(availability = AvailabilityFilter.AVAILABLE_ONLY)
                )
                mediaRepository.snapshotVisibleMediaIds(availableOnlyQuery)
            }
            is QueueStartRequest.Playlist -> {
                val items = playlistRepository.getPlaylistItems(request.playlistId)
                val mediaIds = items.map { it.mediaId }
                val mediaFiles = mediaRepository.getMediaFilesByIdsPreservingOrder(mediaIds)
                val availableIds = mediaFiles.filter { it.isAvailable }.map { it.id }.toSet()
                mediaIds.filter { availableIds.contains(it) }
            }
        }

        if (orderedMediaIds.isEmpty()) {
            return Result.failure(IllegalStateException("No available tracks to play in current selection."))
        }

        val startingIndex = orderedMediaIds.indexOf(request.startingMediaId)
        if (startingIndex < 0) {
            return Result.failure(IllegalArgumentException("Selected track is unavailable or not in current selection."))
        }

        val queueId = UUID.randomUUID().toString()
        val queueToSave = SavedQueue(
            id = queueId,
            collectionId = "DEFAULT_COLLECTION",
            kind = SavedQueueKind.EXPLICIT,
            currentIndex = startingIndex,
            currentMediaId = request.startingMediaId,
            positionMs = 0L,
            playWhenReadyIntent = true
        )

        val savedQueue = queueRepository.replaceQueueSnapshot(queueToSave, orderedMediaIds)
        val currentSession = uiSessionRepository.getUiSessionStateFlow().first()
        uiSessionRepository.saveUiSessionState(currentSession.copy(activeQueueId = savedQueue.id))
        return Result.success(savedQueue)
    }
}
