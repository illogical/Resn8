package com.app.resn8.ui.session

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.repository.CollectionRepository
import com.app.resn8.domain.repository.UiSessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ActiveCollectionSelection(
    val collectionId: String,
    val sourceId: String?
)

sealed interface ActiveCollectionState {
    data object Loading : ActiveCollectionState
    data object NoCollections : ActiveCollectionState
    data object SelectionRequired : ActiveCollectionState
    data class Ready(val selection: ActiveCollectionSelection) : ActiveCollectionState
    data class Error(val message: String) : ActiveCollectionState
}

/**
 * Resolves profile-independent database identities for every top-level surface.
 *
 * The MVP fallback to the sole collection repairs sessions created before active
 * collection persistence was wired. It deliberately refuses to guess when more
 * than one collection exists.
 */
class ActiveCollectionViewModel(
    private val collectionRepository: CollectionRepository,
    private val uiSessionRepository: UiSessionRepository
) : ViewModel() {
    private val _state = MutableStateFlow<ActiveCollectionState>(ActiveCollectionState.Loading)
    val state: StateFlow<ActiveCollectionState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                uiSessionRepository.getUiSessionStateFlow(),
                collectionRepository.getCollectionsFlow()
            ) { session, collections -> session to collections }
                .distinctUntilChanged()
                .collectLatest { (session, collections) ->
                    resolve(session, collections.map { it.id })
                }
        }
    }

    private suspend fun resolve(session: UiSessionState, collectionIds: List<String>) {
        if (collectionIds.isEmpty()) {
            _state.value = ActiveCollectionState.NoCollections
            return
        }

        val collectionId = when {
            session.selectedCollectionId in collectionIds -> session.selectedCollectionId
            collectionIds.size == 1 -> collectionIds.single()
            else -> null
        }
        if (collectionId == null) {
            _state.value = ActiveCollectionState.SelectionRequired
            return
        }

        try {
            val roots = collectionRepository.getRootSourcesFlow(collectionId).first()
            val sourceId = session.selectedSourceId
                ?.takeIf { selected -> roots.any { it.id == selected } }
                ?: roots.singleOrNull()?.id
                ?: roots.firstOrNull()?.id
            val selection = ActiveCollectionSelection(collectionId, sourceId)

            if (
                session.selectedCollectionId != selection.collectionId ||
                session.selectedSourceId != selection.sourceId
            ) {
                uiSessionRepository.saveUiSessionState(
                    session.copy(
                        selectedCollectionId = selection.collectionId,
                        selectedSourceId = selection.sourceId
                    )
                )
            }
            _state.value = ActiveCollectionState.Ready(selection)
        } catch (error: Exception) {
            Log.e(LOG_TAG, "active_collection_resolution_failed category=${error::class.simpleName}")
            _state.value = ActiveCollectionState.Error("Unable to open the indexed collection")
        }
    }

    companion object {
        private const val LOG_TAG = "Resn8Session"
    }
}
