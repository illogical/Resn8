package com.app.resn8.data.repository

import com.app.resn8.data.database.Resn8Database
import com.app.resn8.data.database.entity.toDomain
import com.app.resn8.data.database.entity.toEntity
import com.app.resn8.domain.model.UiSessionState
import com.app.resn8.domain.repository.UiSessionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomUiSessionRepository(
    private val db: Resn8Database
) : UiSessionRepository {
    private val uiSessionDao = db.uiSessionDao()

    override fun getUiSessionStateFlow(): Flow<UiSessionState> {
        return uiSessionDao.getUiSessionStateFlow().map { entity ->
            entity?.toDomain() ?: UiSessionState()
        }
    }

    override suspend fun saveUiSessionState(state: UiSessionState) {
        uiSessionDao.upsertUiSessionState(state.toEntity())
    }
}
