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
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import androidx.media3.session.SessionToken
import com.app.resn8.di.AppContainer
import com.app.resn8.domain.model.QueueStartRequest
import com.app.resn8.domain.model.SavedQueue
import com.app.resn8.domain.model.PlaylistRandomizationResult
import com.app.resn8.domain.model.CollectionProfile
import com.app.resn8.domain.model.RepeatMode
import com.app.resn8.domain.usecase.StartQueueUseCase
import com.app.resn8.domain.model.resolvePlaybackOrigin
import com.app.resn8.storage.artwork.ArtworkCache
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.Futures
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
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
    private var artworkJob: Job? = null
    private var artworkMediaId: String? = null
    private val artworkCache = ArtworkCache(context)

    private val attemptedFailedItems = mutableSetOf<String>()
    private var activeQueue: SavedQueue? = null

    private val controllerListener = object : MediaController.Listener {
        override fun onCustomCommand(
            controller: MediaController,
            command: SessionCommand,
            args: Bundle
        ): ListenableFuture<SessionResult> {
            if (command == RATING_CHANGED_EVENT) {
                val mediaId = args.getString(RATING_RESULT_MEDIA_ID)
                val score = args.getInt(RATING_RESULT_SCORE)
                if (mediaId != null && _uiState.value.currentMediaId == mediaId) {
                    _uiState.update { it.copy(likeScore = score) }
                }
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }
    }

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
                val future = MediaController.Builder(context, sessionToken)
                    .setListener(controllerListener)
                    .buildAsync()
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
                    currentQueueItemId = null,
                    currentMediaId = null,
                    currentIndex = -1,
                    title = "",
                    artist = "",
                    album = "",
                    artworkUri = null,
                    positionMs = 0L,
                    durationMs = 0L,
                    isPlaying = false,
                    isBuffering = false,
                    canPlayPause = false,
                    canSeek = false,
                    canSkipPrevious = false,
                    canSkipNext = false,
                    queueItems = emptyList(),
                    queueTitle = null,
                    sourcePlaylistId = null,
                    queueOrigin = null,
                    isFlatCollection = false
                )
            }
            artworkJob?.cancel()
            artworkMediaId = null
            return
        }

        scope.launch {
            val isFlat = container.collectionRepository.getCollectionById(queue.collectionId)?.profile == CollectionProfile.FLAT
            val mediaFiles = container.mediaRepository.getMediaFilesByIdsPreservingOrder(queue.orderedMediaIds)
            val fileMap = mediaFiles.associateBy { it.id }

            val itemStates = queue.items.map { item ->
                val mediaFile = fileMap[item.mediaId]
                PlaybackQueueItemState(
                    queueItemId = item.queueItemId,
                    mediaId = item.mediaId,
                    title = mediaFile?.displayTitle ?: item.mediaId,
                    artist = if (isFlat) "" else mediaFile?.artist ?: "Unknown Artist",
                    album = if (isFlat) "" else mediaFile?.album ?: "Unknown Album",
                    isAvailable = mediaFile?.isAvailable ?: true,
                    isCurrent = item.queueItemId == _uiState.value.currentQueueItemId
                )
            }

            val filter = queue.filterSnapshot
            val sourcePlaylistId = filter?.playlistId
            val queueOrigin = filter?.resolvePlaybackOrigin()
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
                    sourcePlaylistId = sourcePlaylistId,
                    queueOrigin = queueOrigin,
                    isFlatCollection = isFlat
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
            if (controller?.currentMediaItem?.mediaId != currentQueueItemId) return@launch
            val likeScore = mediaFile?.likeScore ?: 0
            val displayedArtworkUri = if (
                _uiState.value.currentMediaId == currentMediaFileId && artworkMediaId == currentMediaFileId
            ) {
                _uiState.value.artworkUri ?: artworkUri
            } else {
                artworkUri
            }

            _uiState.update {
                it.copy(
                    currentQueueItemId = currentQueueItemId,
                    currentMediaId = currentMediaFileId,
                    currentIndex = currentIndex,
                    title = title,
                    artist = artist,
                    album = album,
                    artworkUri = displayedArtworkUri,
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
            if (mediaFile != null && artworkMediaId != mediaFile.id) {
                artworkJob?.cancel()
                artworkMediaId = mediaFile.id
                artworkJob = scope.launch {
                    val resolvedArtwork = artworkCache.resolveEmbedded(mediaFile) ?: artworkUri
                    if (_uiState.value.currentMediaId == mediaFile.id && artworkMediaId == mediaFile.id) {
                        _uiState.update { it.copy(artworkUri = resolvedArtwork) }
                    }
                }
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

    fun seekBy(deltaMs: Long) {
        val ctrl = controller ?: return
        if (!ctrl.isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)) return
        val unboundedTarget = (ctrl.currentPosition.coerceAtLeast(0L) + deltaMs).coerceAtLeast(0L)
        val duration = ctrl.duration
        val target = if (duration != C.TIME_UNSET && duration > 0L) {
            unboundedTarget.coerceAtMost(duration)
        } else {
            unboundedTarget
        }
        ctrl.seekTo(target)
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

    fun likeTrack() {
        rateCurrentTrack(LIKE_CURRENT_COMMAND)
    }

    fun dislikeTrack() {
        rateCurrentTrack(DISLIKE_CURRENT_COMMAND)
    }

    private fun rateCurrentTrack(command: SessionCommand) {
        val ctrl = controller ?: return
        if (!ctrl.isSessionCommandAvailable(command)) return
        scope.launch {
            runCatching { ctrl.sendCustomCommand(command, Bundle.EMPTY).awaitFuture() }
                .getOrNull()
                ?.takeIf { it.resultCode == SessionResult.RESULT_SUCCESS }
                ?.extras
                ?.let { result ->
                    val mediaId = result.getString(RATING_RESULT_MEDIA_ID)
                    if (mediaId != null && _uiState.value.currentMediaId == mediaId) {
                        _uiState.update { it.copy(likeScore = result.getInt(RATING_RESULT_SCORE)) }
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
                container.collectionRepository.setCollectionActiveQueue(
                    savedQueue.collectionId,
                    savedQueue.id
                )
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
                    } else {
                        ctrl.pause()
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

    fun synchronizeRandomizedPlaylist(
        playlistId: String,
        result: PlaylistRandomizationResult,
        onComplete: (Result<Unit>) -> Unit = {}
    ) {
        scope.launch {
            val matchingQueue = activeQueue?.takeIf { shouldSynchronizeRandomizedPlaylist(it, playlistId) }
            if (matchingQueue == null) {
                onComplete(Result.success(Unit))
                return@launch
            }
            val wasPlaying = controller?.isPlaying == true
            val firstAvailableId = result.availableOrderedMediaIds.firstOrNull()
            if (firstAvailableId == null) {
                val clearResult = runCatching {
                    controller?.pause()
                    controller?.stop()
                    controller?.clearMediaItems()
                    container.collectionRepository.setCollectionActiveQueue(matchingQueue.collectionId, null)
                    val session = container.uiSessionRepository.getUiSessionStateFlow().first()
                    if (session.activeQueueId == matchingQueue.id) {
                        container.uiSessionRepository.saveUiSessionState(session.copy(activeQueueId = null))
                    }
                    activeQueue = null
                    updateQueueUiState(null)
                    stopPollingPosition()
                }
                onComplete(clearResult)
                return@launch
            }

            startQueue(
                QueueStartRequest.Playlist(
                    playlistId = playlistId,
                    startingMediaId = firstAvailableId,
                    playWhenReady = wasPlaying
                )
            ) { queueResult ->
                onComplete(queueResult.map { Unit })
            }
        }
    }

    fun clearNotice() {
        _uiState.update { it.copy(notice = null) }
    }

    suspend fun checkpointAndStopForCollectionSwitch() {
        attemptedFailedItems.clear()
        controller?.let { ctrl ->
            ctrl.pause()
            val queue = activeQueue
            val currentItem = ctrl.currentMediaItem
            if (queue != null && currentItem != null && ctrl.currentMediaItemIndex >= 0) {
                val currentMediaId = currentItem.requestMetadata.extras
                    ?.getString(RESN8_MEDIA_FILE_ID)
                    ?: queue.currentMediaId
                container.queueRepository.updatePlaybackCheckpoint(
                    queueId = queue.id,
                    currentIndex = ctrl.currentMediaItemIndex,
                    currentMediaId = currentMediaId,
                    currentOccurrenceId = queue.currentOccurrenceId,
                    positionMs = ctrl.currentPosition.coerceAtLeast(0L),
                    playWhenReadyIntent = false,
                    playbackSpeed = ctrl.playbackParameters.speed,
                    repeatMode = when (ctrl.repeatMode) {
                        Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                        Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                        else -> RepeatMode.OFF
                    }
                )
            }
            ctrl.stop()
            ctrl.clearMediaItems()
        }
        stopPollingPosition()
    }

    fun stopForCollectionSwitch() {
        scope.launch { checkpointAndStopForCollectionSwitch() }
    }

    fun release() {
        stopPollingPosition()
        activeQueueJob?.cancel()
        artworkJob?.cancel()
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }
}

internal fun shouldSynchronizeRandomizedPlaylist(queue: SavedQueue, playlistId: String): Boolean =
    queue.filterSnapshot?.playlistId == playlistId
