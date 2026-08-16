package com.arcx.core.data.repository

import com.arcx.core.data.database.RunDao
import com.arcx.core.data.database.RunEntity
import com.arcx.core.data.database.RunOutcomeRow
import com.arcx.core.data.database.RunSummaryRow
import com.arcx.core.data.database.WorkflowAverageRow
import com.arcx.core.data.database.WorkflowDao
import com.arcx.core.data.database.WorkflowEntity
import com.arcx.core.data.screenshot.ScreenshotStoreImpl
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunStatus
import com.arcx.core.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class HistoryRepositoryImplTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val runDao = FakeRunDao()
    private val screenshots by lazy {
        ScreenshotStoreImpl(File(temp.root, "screenshots"), Dispatchers.Unconfined)
    }

    private fun repository() =
        HistoryRepositoryImpl(runDao, FakeWorkflowDao(), FakeSettings(), screenshots)

    /**
     * The reason this is worth a test: dropping the rows while leaving the images on disk is a
     * privacy leak with no UI left to clean it up, and nothing about deleting rows would fail.
     */
    @Test
    fun `clearing history deletes the stored screenshots too`() = runTest {
        val path = screenshots.save("r1", byteArrayOf(1, 2, 3))!!
        repository().record(run(id = "r1", screenshotPath = path))
        assertTrue(File(path).exists())

        repository().clear()

        assertTrue(runDao.rows.isEmpty())
        assertFalse(File(path).exists())
    }

    // Wiping the directory, not just the paths on the rows, is what makes that guarantee hold.
    @Test
    fun `clearing history also removes an image whose row was already gone`() = runTest {
        val orphan = screenshots.save("orphaned", byteArrayOf(9))!!

        repository().clear()

        assertFalse(File(orphan).exists())
    }

    /**
     * The cap is what keeps every history read bounded, and dropping rows without their images
     * is the same privacy leak as clearing without them — except this one happens silently, in
     * the background, on the thousand-and-first run.
     */
    @Test
    fun `passing the retention cap drops the oldest run and its screenshot`() = runTest {
        val doomed = screenshots.save("oldest", byteArrayOf(1))!!
        val repository = repository()
        repository.record(run(id = "oldest", screenshotPath = doomed, startedAt = 1L))

        repeat(RunRecord.HISTORY_LIMIT) { index ->
            repository.record(run(id = "r$index", screenshotPath = null, startedAt = index + 2L))
        }

        assertEquals(RunRecord.HISTORY_LIMIT, runDao.rows.size)
        assertFalse(runDao.rows.any { it.id == "oldest" })
        assertFalse(File(doomed).exists())
    }

    /** Under the cap nothing is touched — the delete has to be a no-op, not a slow no-op. */
    @Test
    fun `staying under the cap keeps every run`() = runTest {
        val repository = repository()
        repeat(5) { repository.record(run(id = "r$it", screenshotPath = null, startedAt = it.toLong())) }

        assertEquals(5, runDao.rows.size)
    }

    private fun run(id: String, screenshotPath: String?, startedAt: Long = 1L) = RunRecord(
        id = id,
        workflowId = "w1",
        workflowName = "Explain this screen",
        workflowIcon = "✨",
        startedAt = startedAt,
        durationMs = 2L,
        providerLabel = "Gemini",
        model = "gemini-3-pro",
        status = RunStatus.SUCCESS,
        inputPreview = "",
        screenshotPath = screenshotPath,
    )
}

/** Reproduces the DAO's ordering and cap semantics so the repository's use of them is real. */
private class FakeRunDao : RunDao {
    val rows = mutableListOf<RunEntity>()

    override fun observeRecent(limit: Int): Flow<List<RunSummaryRow>> =
        MutableStateFlow(newestFirst().take(limit).map { it.toSummaryRow() })

    override fun observeSince(since: Long): Flow<List<RunOutcomeRow>> =
        MutableStateFlow(
            rows.filter { it.startedAt >= since }.map { RunOutcomeRow(it.durationMs, it.status) },
        )

    override fun observeAverageDurations(status: RunStatus): Flow<List<WorkflowAverageRow>> =
        MutableStateFlow(
            rows.filter { it.status == status }
                .groupBy { it.workflowId }
                .map { (id, runs) -> WorkflowAverageRow(id, runs.map { it.durationMs }.average()) },
        )

    override suspend fun get(id: String): RunEntity? = rows.firstOrNull { it.id == id }

    override suspend fun insert(run: RunEntity) {
        rows += run
    }

    override suspend fun prune(keep: Int) {
        val survivors = newestFirst().take(keep).mapTo(mutableSetOf()) { it.id }
        rows.retainAll { it.id in survivors }
    }

    override suspend fun screenshotsBeyond(keep: Int): List<String> {
        val survivors = newestFirst().take(keep).mapTo(mutableSetOf()) { it.id }
        return rows.filter { it.id !in survivors }.mapNotNull { it.screenshotPath }
    }

    override suspend fun clear() = rows.clear()

    private fun newestFirst() = rows.sortedByDescending { it.startedAt }

    private fun RunEntity.toSummaryRow() = RunSummaryRow(
        id = id,
        workflowId = workflowId,
        workflowName = workflowName,
        workflowIcon = workflowIcon,
        startedAt = startedAt,
        durationMs = durationMs,
        providerLabel = providerLabel,
        model = model,
        status = status,
        error = error,
        hasScreenshot = screenshotPath != null,
    )
}

/** Only [touchLastRun] is on the path under test; the rest would be noise if it were called. */
private class FakeWorkflowDao : WorkflowDao {
    override fun observeAll(): Flow<List<WorkflowEntity>> = unused()
    override fun observeEnabled(): Flow<List<WorkflowEntity>> = unused()
    override fun observeFavorites(): Flow<List<WorkflowEntity>> = unused()
    override fun observePinned(): Flow<List<WorkflowEntity>> = unused()
    override fun observeRecent(limit: Int): Flow<List<WorkflowEntity>> = unused()
    override suspend fun get(id: String): WorkflowEntity? = unused()
    override suspend fun lastRunAt(id: String): Long? = unused()
    override suspend fun count(): Int = unused()
    override suspend fun upsert(workflow: WorkflowEntity) = unused()
    override suspend fun insertAll(workflows: List<WorkflowEntity>) = unused()
    override suspend fun delete(id: String) = unused()
    override suspend fun setFavorite(id: String, favorite: Boolean, now: Long) = unused()
    override suspend fun setPinned(id: String, pinned: Boolean, now: Long) = unused()
    override suspend fun setEnabled(id: String, enabled: Boolean) = unused()
    override suspend fun touchLastRun(id: String, at: Long) = Unit

    private fun unused(): Nothing = error("not part of this test")
}

private class FakeSettings : SettingsRepository {
    override val settings: Flow<UserSettings> = MutableStateFlow(UserSettings())
    override suspend fun current(): UserSettings = UserSettings()
    override suspend fun update(transform: (UserSettings) -> UserSettings) = Unit
}
