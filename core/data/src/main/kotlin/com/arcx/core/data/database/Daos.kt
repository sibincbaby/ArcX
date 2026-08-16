package com.arcx.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import com.arcx.core.model.RunStatus
import kotlinx.coroutines.flow.Flow

/**
 * Every read here except [observeAll] is a **picker** feed — something the user chooses a workflow
 * to run from — so every one of them carries `enabled = 1`.
 *
 * The filter lives in SQL rather than in the callers on purpose. There are six surfaces reading
 * these queries across four modules, two of which cannot see each other, and "off" is only true if
 * *all* of them honour it; a filter each caller has to remember is one a seventh surface will
 * silently skip. [observeAll] is the deliberate exception — the Library has to show a switched-off
 * workflow, or there would be nowhere to switch it back on.
 */
@Dao
interface WorkflowDao {
    /** Everything, disabled included: the Library, the export, and "delete all local data". */
    @Query("SELECT * FROM workflows ORDER BY sortOrder ASC, name ASC")
    fun observeAll(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE enabled = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeEnabled(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE isFavorite = 1 AND enabled = 1 ORDER BY sortOrder ASC, name ASC")
    fun observeFavorites(): Flow<List<WorkflowEntity>>

    @Query("SELECT * FROM workflows WHERE isPinned = 1 AND enabled = 1 ORDER BY sortOrder ASC, name ASC")
    fun observePinned(): Flow<List<WorkflowEntity>>

    @Query(
        "SELECT * FROM workflows WHERE lastRunAt IS NOT NULL AND enabled = 1 " +
            "ORDER BY lastRunAt DESC LIMIT :limit",
    )
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

    /**
     * One column, like the two above it. A read-modify-write of the whole row from a `Workflow` the
     * caller is holding would carry every other field back with it — including the ones a list row
     * never loaded fresh — so a switch flipped from a stale list could undo an edit made elsewhere.
     *
     * **`updatedAt` is deliberately not touched, unlike [setFavorite] and [setPinned].** The
     * Library's default sort is "Recently updated", so stamping it pulls the row to the top of its
     * section the instant the switch moves — and this is the one control a user reaches for several
     * times in a row, on a screen full of starters they are pruning. Measured on device: switching
     * one off slid it above two rows that had not moved, on every tap. Pinning and starring earn
     * their stamp because moving the row *is* the visible result of the action; switching one off
     * changes nothing about where it belongs in the Library, only whether it is offered elsewhere.
     */
    @Query("UPDATE workflows SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)

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
