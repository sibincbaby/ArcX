package com.arcx.core.domain.repository

import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunOutcome
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunSummary
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
    /**
     * Installs any bundled starter the user has not been offered before. Safe to call on every
     * launch: it adds nothing twice, and never resurrects a starter the user deleted.
     */
    suspend fun installNewBuiltIns()
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

/**
 * Run history, deliberately without a "give me everything" method.
 *
 * Every read here is bounded — by a row count, by a timestamp, or by being an aggregate the
 * database computes itself. The screens that show history are the ones a user opens most, and
 * an unbounded read is the one thing that makes them get slower the longer the app is owned.
 */
interface HistoryRepository {
    /** Newest first. Defaults to the whole retained window, which is itself capped. */
    fun observeRecent(limit: Int = RunRecord.HISTORY_LIMIT): Flow<List<RunSummary>>

    /** Runs started at or after [since]. For counting a day, not for listing one. */
    fun observeSince(since: Long): Flow<List<RunOutcome>>

    /** Mean duration of each workflow's successful runs, keyed by workflow id. Aggregated in SQL. */
    fun observeAverageDurations(): Flow<Map<String, Long>>

    /** The full row, previews included. Only the detail sheet needs this. */
    suspend fun get(id: String): RunRecord?

    suspend fun record(run: RunRecord)
    suspend fun clear()
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun current(): UserSettings
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
