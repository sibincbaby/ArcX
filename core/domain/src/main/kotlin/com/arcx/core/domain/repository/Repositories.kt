package com.arcx.core.domain.repository

import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunRecord
import com.arcx.core.model.UserSettings
import com.arcx.core.model.Workflow
import kotlinx.coroutines.flow.Flow

interface WorkflowRepository {
    fun observeAll(): Flow<List<Workflow>>
    fun observeFavorites(): Flow<List<Workflow>>
    fun observePinned(): Flow<List<Workflow>>
    /** Most recently executed first; drives the Home screen's "Recent" row. */
    fun observeRecent(limit: Int = 8): Flow<List<Workflow>>
    suspend fun get(id: String): Workflow?
    suspend fun upsert(workflow: Workflow)
    suspend fun delete(id: String)
    suspend fun setFavorite(id: String, favorite: Boolean)
    suspend fun setPinned(id: String, pinned: Boolean)
    /** Seeds the bundled starter workflows. No-op once they have been installed. */
    suspend fun seedBuiltInsIfEmpty()
}

interface ProviderRepository {
    fun observeAll(): Flow<List<ProviderConfig>>
    suspend fun get(id: String): ProviderConfig?
    /** Resolves the workflow's provider, falling back to the user's default. */
    suspend fun resolve(providerId: String?): ProviderConfig?
    suspend fun upsert(config: ProviderConfig, apiKey: String?)
    suspend fun delete(id: String)
    /** Plaintext key, decrypted from the Keystore-backed vault. */
    suspend fun apiKey(id: String): String?
    suspend fun hasKey(id: String): Boolean
}

interface HistoryRepository {
    fun observeAll(): Flow<List<RunRecord>>
    suspend fun get(id: String): RunRecord?
    suspend fun record(run: RunRecord)
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun current(): UserSettings
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
