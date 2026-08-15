package com.arcx.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.arcx.core.model.RunStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface WorkflowDao {
    @Query("SELECT * FROM workflows ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE isFavorite = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeFavorites(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE isPinned = 1 ORDER BY sortOrder ASC, name ASC")
    fun observePinned(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE lastRunAt IS NOT NULL ORDER BY lastRunAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE id = :id")
    suspend fun get(id: String): WorkflowEntity?

    @Query("SELECT lastRunAt FROM workflows WHERE id = :id")
    suspend fun lastRunAt(id: String): Long?

    @Query("SELECT COUNT(*) FROM workflows")
    suspend fun count(): Int

    @Upsert
    suspend fun upsert(workflow: WorkflowEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(workflows: List<WorkflowEntity>)

    @Query("DELETE FROM workflows WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE workflows SET isFavorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean, now: Long)

    @Query("UPDATE workflows SET isPinned = :pinned, updatedAt = :now WHERE id = :id")
    suspend fun setPinned(id: String, pinned: Boolean, now: Long)

    @Query("UPDATE workflows SET lastRunAt = :at WHERE id = :id")
    suspend fun touchLastRun(id: String, at: Long)
}

@Dao
interface ProviderDao {
    @Query("SELECT * FROM providers ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProviderEntity>>

    @Query("SELECT * FROM providers WHERE id = :id")
    suspend fun get(id: String): ProviderEntity?

    @Upsert
    suspend fun upsert(provider: ProviderEntity)

    @Query("DELETE FROM providers WHERE id = :id")
    suspend fun delete(id: String)
}

/** The list projection. `SELECT *` here would carry two preview columns nothing draws. */
data class RunSummaryRow(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val workflowIcon: String,
    val startedAt: Long,
    val durationMs: Long,
    val providerLabel: String,
    val model: String,
    val status: RunStatus,
    val error: String?,
    val hasScreenshot: Boolean,
)

data class RunOutcomeRow(val durationMs: Long, val status: RunStatus)

data class WorkflowAverageRow(val workflowId: String, val averageMs: Double)

@Dao
interface RunDao {

    /**
     * Newest first, capped. Unbounded, this was the most expensive query in the app: it read
     * every run ever recorded — previews included — on every insert, and two screens collected
     * it. [com.arcx.core.model.RunRecord.HISTORY_LIMIT] keeps the table itself in the same
     * range, so the cap here is a guard rather than a window onto something larger.
     */
    @Query(
        """
        SELECT id, workflowId, workflowName, workflowIcon, startedAt, durationMs,
               providerLabel, model, status, error, (screenshotPath IS NOT NULL) AS hasScreenshot
        FROM runs ORDER BY startedAt DESC LIMIT :limit
        """,
    )
    fun observeRecent(limit: Int): Flow<List<RunSummaryRow>>

    /** Bounded by a timestamp, not by history size — "today" is a few dozen rows however old the app is. */
    @Query("SELECT durationMs, status FROM runs WHERE startedAt >= :since")
    fun observeSince(since: Long): Flow<List<RunOutcomeRow>>

    /**
     * Averaged in SQLite rather than in Kotlin. The same number used to be computed by loading
     * every run and folding over it on the main thread; this returns one row per workflow and
     * never materialises a preview.
     */
    @Query("SELECT workflowId, AVG(durationMs) AS averageMs FROM runs WHERE status = :status GROUP BY workflowId")
    fun observeAverageDurations(status: RunStatus): Flow<List<WorkflowAverageRow>>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: String): RunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: RunEntity)

    /**
     * Trims to the newest [keep] rows. The subquery returns null when there are fewer than that,
     * and `startedAt < NULL` matches nothing — so under the cap this costs one indexed lookup
     * and deletes nothing.
     */
    @Query("DELETE FROM runs WHERE startedAt < (SELECT startedAt FROM runs ORDER BY startedAt DESC LIMIT 1 OFFSET :keep)")
    suspend fun prune(keep: Int)

    /** Read before [prune] so the images of the rows about to go can be deleted with them. */
    @Query(
        """
        SELECT screenshotPath FROM runs
        WHERE screenshotPath IS NOT NULL
          AND startedAt < (SELECT startedAt FROM runs ORDER BY startedAt DESC LIMIT 1 OFFSET :keep)
        """,
    )
    suspend fun screenshotsBeyond(keep: Int): List<String>

    @Query("DELETE FROM runs")
    suspend fun clear()
}
