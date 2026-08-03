package com.app.resn8.playback

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.QueueStartRequest
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.usecase.StartQueueUseCase
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resumeWithException

private suspend fun <T> ListenableFuture<T>.awaitFuture(): T =
    suspendCancellableCoroutine { continuation ->
        addListener({
            try {
                continuation.resume(get(), onCancellation = null)
            } catch (e: Throwable) {
                continuation.resumeWithException(e.cause ?: e)
            }
        }, MoreExecutors.directExecutor())
    }

class PlaybackConnection(
    private val context: Context,
    private val container: AppContainer,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) {
    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var pollingJob: Job? = null
    private var activeQueueJob: Job? = null

    private val attemptedFailedItems = mutableSetOf<String>()
    private var activeQueue: SavedQueue? = null

    private val playerListener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            updateUiState()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                attemptedFailedItems.clear()
            }
            updateUiState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateUiState()
            if (isPlaying) {
                startPollingPosition()
            } else {
                stopPollingPosition()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateUiState()
        }

        override fun onPlayerError(error: PlaybackException) {
            handlePlaybackError(error)
        }
    }

    init {
        connect()
        observeActiveQueue()
    }

    fun connect() {
        if (controller != null || _uiState.value.connectionStatus == PlaybackConnectionStatus.CONNECTING) {
            return
        }

        _uiState.update {
            it.copy(
                connectionStatus = PlaybackConnectionStatus.CONNECTING,
                connectionError = null
            )
        }

        scope.launch {
            try {
                val sessionToken = SessionToken(
                    context,
                    ComponentName(context, Resn8MediaService::class.java)
                )
                val future = MediaController.Builder(context, sessionToken).buildAsync()
                controllerFuture = future
                val ctrl = future.awaitFuture()
                controller = ctrl
                ctrl.addListener(playerListener)

                _uiState.update {
                    it.copy(
                        connectionStatus = PlaybackConnectionStatus.CONNECTED,
                        connectionError = null
                    )
                }
                updateUiState()
                if (ctrl.isPlaying) {
                    startPollingPosition()
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        connectionStatus = PlaybackConnectionStatus.ERROR,
                        connectionError = e.localizedMessage ?: "Failed to connect to media service"
                    )
                }
            }
        }
    }

    private fun observeActiveQueue() {
        activeQueueJob?.cancel()
        activeQueueJob = scope.launch {
            container.uiSessionRepository.getUiSessionStateFlow().collectLatest { session ->
                val queueId = session.activeQueueId
                if (queueId != null) {
                    container.queueRepository.getQueueByIdFlow(queueId).collectLatest { queue ->
                        activeQueue = queue
                        updateQueueUiState(queue)
                    }
                } else {
                    activeQueue = null
                    updateQueueUiState(null)
                }
            }
        }
    }

    private fun updateQueueUiState(queue: SavedQueue?) {
        if (queue == null) {
            _uiState.update {
                it.copy(
                    activeQueueId = null,
                    queueItems = emptyList(),
                    queueTitle = null,
                    sourcePlaylistId = null
                )
            }
            return
        }

        scope.launch {
            val mediaFiles = container.mediaRepository.getMediaFilesByIdsPreservingOrder(queue.orderedMediaIds)
            val fileMap = mediaFiles.associateBy { it.id }

            val itemStates = queue.items.map { item ->
                val mediaFile = fileMap[item.mediaId]
                PlaybackQueueItemState(
                    queueItemId = item.queueItemId,
                    mediaId = item.mediaId,
                    title = mediaFile?.displayTitle ?: item.mediaId,
                    artist = mediaFile?.artist ?: "Unknown Artist",
                    album = mediaFile?.album ?: "Unknown Album",
                    isAvailable = mediaFile?.isAvailable ?: true,
                    isCurrent = item.queueItemId == _uiState.value.currentQueueItemId
                )
            }

            val filter = queue.filterSnapshot
            val sourcePlaylistId = filter?.playlistId
            val queueTitle = when {
                filter?.playlistName != null -> "Playlist: ${filter.playlistName}"
                filter?.album != null -> "Album: ${filter.album}"
                filter?.artist != null -> "Artist: ${filter.artist}"
                filter?.searchQuery != null -> "Search: ${filter.searchQuery}"
                else -> "Active Playback Queue"
            }

            _uiState.update {
                it.copy(
                    activeQueueId = queue.id,
                    queueItems = itemStates,
                    queueTitle = queueTitle,
                    sourcePlaylistId = sourcePlaylistId
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun updateUiState() {
        val ctrl = controller ?: return
        val currentItem = ctrl.currentMediaItem
        val currentQueueItemId = currentItem?.mediaId
        val currentMediaFileId = currentItem?.requestMetadata?.extras?.getString(RESN8_MEDIA_FILE_ID) ?: currentItem?.mediaId

        val mediaMetadata = ctrl.mediaMetadata
        val title = mediaMetadata.title?.toString() ?: ""
        val artist = mediaMetadata.artist?.toString() ?: ""
        val album = mediaMetadata.albumTitle?.toString() ?: ""
        val artworkUri = mediaMetadata.artworkUri?.toString()

        val pos = ctrl.currentPosition.coerceAtLeast(0L)
        val dur = ctrl.duration
        val isUnknownDuration = dur == C.TIME_UNSET || dur <= 0L

        val isPlaying = ctrl.isPlaying
        val isBuffering = ctrl.playbackState == Player.STATE_BUFFERING
        val currentIndex = ctrl.currentMediaItemIndex

        val updatedQueueItems = _uiState.value.queueItems.map {
            it.copy(isCurrent = it.queueItemId == currentQueueItemId)
        }

        scope.launch {
            val mediaFile = if (currentMediaFileId != null) container.mediaRepository.getMediaFileById(currentMediaFileId) else null
            val likeScore = mediaFile?.likeScore ?: 0

            _uiState.update {
                it.copy(
                    currentQueueItemId = currentQueueItemId,
                    currentMediaId = currentMediaFileId,
                    currentIndex = currentIndex,
                    title = title,
                    artist = artist,
                    album = album,
                    artworkUri = artworkUri,
                    likeScore = likeScore,
                    positionMs = pos,
                    durationMs = if (isUnknownDuration) 0L else dur,
                    isDurationUnknown = isUnknownDuration,
                    isPlaying = isPlaying,
                    isBuffering = isBuffering,
                    canPlayPause = ctrl.isCommandAvailable(Player.COMMAND_PLAY_PAUSE),
                    canSeek = ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM),
                    canSkipPrevious = ctrl.hasPreviousMediaItem(),
                    canSkipNext = ctrl.hasNextMediaItem(),
                    queueItems = updatedQueueItems
                )
            }
        }
    }

    private fun startPollingPosition() {
        stopPollingPosition()
        pollingJob = scope.launch {
            while (isActive) {
                val ctrl = controller
                if (ctrl != null && ctrl.isPlaying) {
                    val pos = ctrl.currentPosition.coerceAtLeast(0L)
                    val dur = ctrl.duration
                    val isUnknownDuration = dur == C.TIME_UNSET || dur <= 0L
                    _uiState.update {
                        it.copy(
                            positionMs = pos,
                            durationMs = if (isUnknownDuration) 0L else dur,
                            isDurationUnknown = isUnknownDuration
                        )
                    }
                }
                delay(500L)
            }
        }
    }

    private fun stopPollingPosition() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun handlePlaybackError(error: PlaybackException) {
        val ctrl = controller ?: return
        val currentItemId = _uiState.value.currentQueueItemId ?: ctrl.currentMediaItem?.mediaId
        if (currentItemId != null) {
            attemptedFailedItems.add(currentItemId)
        }

        val notice = PlaybackNotice(
            queueItemId = currentItemId,
            mediaId = _uiState.value.currentMediaId,
            message = "Playback error: ${error.localizedMessage ?: "File unreadable or unavailable"}",
            isFatal = false
        )

        _uiState.update { it.copy(notice = notice) }

        if (ctrl.hasNextMediaItem() && (currentItemId == null || !attemptedFailedItems.containsAll(_uiState.value.queueItems.map { it.queueItemId }))) {
            ctrl.seekToNextMediaItem()
            ctrl.prepare()
            ctrl.play()
        } else {
            ctrl.pause()
        }
    }

    fun play() {
        controller?.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun togglePlayPause() {
        val ctrl = controller ?: return
        if (ctrl.isPlaying) {
            ctrl.pause()
        } else {
            ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val ctrl = controller ?: return
        if (ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) {
            ctrl.seekTo(positionMs)
        }
    }

    fun skipToPrevious() {
        val ctrl = controller ?: return
        if (ctrl.hasPreviousMediaItem()) {
            ctrl.seekToPreviousMediaItem()
        }
    }

    fun skipToNext() {
        val ctrl = controller ?: return
        if (ctrl.hasNextMediaItem()) {
            ctrl.seekToNextMediaItem()
        }
    }

    fun skipToQueueItem(queueItemId: String) {
        val ctrl = controller ?: return
        val index = _uiState.value.queueItems.indexOfFirst { it.queueItemId == queueItemId }
        if (index >= 0) {
            attemptedFailedItems.clear()
            ctrl.seekTo(index, 0L)
            ctrl.prepare()
            ctrl.play()
        }
    }

    fun likeTrack(mediaId: String? = _uiState.value.currentMediaId) {
        if (mediaId == null) return
        scope.launch {
            val result = container.mediaRepository.updateLikeScore(mediaId, +1)
            result.onSuccess { newScore ->
                if (_uiState.value.currentMediaId == mediaId) {
                    _uiState.update { it.copy(likeScore = newScore) }
                }
            }
        }
    }

    fun dislikeTrack(mediaId: String? = _uiState.value.currentMediaId) {
        if (mediaId == null) return
        scope.launch {
            val result = container.mediaRepository.updateLikeScore(mediaId, -1)
            result.onSuccess { newScore ->
                if (_uiState.value.currentMediaId == mediaId) {
                    _uiState.update { it.copy(likeScore = newScore) }
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun startQueue(request: QueueStartRequest, onComplete: (Result<SavedQueue>) -> Unit = {}) {
        scope.launch {
            attemptedFailedItems.clear()
            val useCase = StartQueueUseCase(
                mediaRepository = container.mediaRepository,
                playlistRepository = container.playlistRepository,
                queueRepository = container.queueRepository,
                uiSessionRepository = container.uiSessionRepository
            )
            val result = useCase(request)
            result.onSuccess { savedQueue ->
                val ctrl = controller
                if (ctrl != null) {
                    val mediaFiles = container.mediaRepository.getMediaFilesByIdsPreservingOrder(savedQueue.orderedMediaIds)
                    val fileMap = mediaFiles.associateBy { it.id }

                    val mediaItems = savedQueue.items.map { item ->
                        val mediaFile = fileMap[item.mediaId]
                        val extras = Bundle().apply {
                            putString(RESN8_QUEUE_ID, savedQueue.id)
                            putString(RESN8_MEDIA_FILE_ID, item.mediaId)
                            putString(RESN8_QUEUE_ITEM_ID, item.queueItemId)
                        }

                        MediaItem.Builder()
                            .setMediaId(item.queueItemId)
                            .setRequestMetadata(
                                MediaItem.RequestMetadata.Builder()
                                    .setExtras(extras)
                                    .build()
                            )
                            .apply {
                                if (mediaFile != null) {
                                    setUri(mediaFile.documentUri)
                                }
                            }
                            .build()
                    }

                    ctrl.setMediaItems(mediaItems, savedQueue.currentIndex, savedQueue.positionMs)
                    ctrl.prepare()
                    if (savedQueue.playWhenReadyIntent) {
                        ctrl.play()
                    }
                }
            }.onFailure { ex ->
                _uiState.update {
                    it.copy(
                        notice = PlaybackNotice(
                            message = ex.message ?: "Failed to start playback queue"
                        )
                    )
                }
            }
            onComplete(result)
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    fun release() {
        stopPollingPosition()
        activeQueueJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }
}
