package com.arcx.core.domain.repository

import android.net.Uri
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunOutcome
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunSummary
import com.arcx.core.model.UserSettings
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowSpec
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

/**
 * The file end of the library: the gallery that ships inside the app, a bundle someone sent, and
 * the user's own export.
 *
 * A `Uri` in a domain port is deliberate, and has precedent — `SystemSurfaces` already hands
 * `android.content.Intent` across this boundary. Both exist for the same reason: the alternative is
 * a ViewModel doing its own `contentResolver` IO, which is what this replaced.
 *
 * **[read] and [install] are separate calls on purpose.** They used to be one expression, which is
 * precisely why there was nowhere to put a confirmation: the file was parsed and written to the
 * library before anything could be shown. Splitting them is what makes an import review sheet
 * possible — [read] returns exactly what [install] would write, already sanitised, so what the user
 * is shown is what they get.
 */
interface WorkflowBundleRepository {
    /** The bundled gallery. Sanitised like any other bundle, so nothing shipped can be special. */
    suspend fun readGallery(): List<WorkflowSpec>

    /**
     * Parse and sanitise the file at [uri], dropping entries with no name or no prompt. Writes
     * nothing. Throws if the file cannot be opened or is not a bundle.
     */
    suspend fun read(uri: Uri): List<WorkflowSpec>

    /**
     * Saves [specs] as workflows of the user's own — fresh ids, no built-in flag, no pinning.
     * Returns what was written, because the caller that installed exactly one wants to open it.
     */
    suspend fun install(specs: List<WorkflowSpec>): List<Workflow>

    /**
     * Writes [workflows] to [uri] as a bundle.
     *
     * Takes `Workflow`, not `WorkflowSpec`, so that the mapping between the two — which is where
     * the provider id and the built-in flag are dropped — stays inside the module that owns the
     * format. A caller assembling specs itself would be a second place that decides what travels.
     */
    suspend fun write(uri: Uri, workflows: List<Workflow>)
}

interface SettingsRepository {
    val settings: Flow<UserSettings>
    suspend fun current(): UserSettings
    suspend fun update(transform: (UserSettings) -> UserSettings)
}
