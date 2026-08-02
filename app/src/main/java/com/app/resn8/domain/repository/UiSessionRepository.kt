package com.app.resn8.domain.repository

import com.app.resn8.domain.model.UiSessionState
import kotlinx.coroutines.flow.Flow

interface UiSessionRepository {
    fun getUiSessionStateFlow(): Flow<UiSessionState>
    suspend fun saveUiSessionState(state: UiSessionState)
}
