package com.arcx.core.data.repository

import com.arcx.core.data.database.RunDao
import com.arcx.core.data.database.RunEntity
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

    private fun run(id: String, screenshotPath: String?) = RunRecord(
        id = id,
        workflowId = "w1",
        workflowName = "Explain this screen",
        workflowIcon = "✨",
        startedAt = 1L,
        durationMs = 2L,
        providerLabel = "Gemini",
        model = "gemini-3-pro",
        status = RunStatus.SUCCESS,
        inputPreview = "",
        screenshotPath = screenshotPath,
    )
}

private class FakeRunDao : RunDao {
    val rows = mutableListOf<RunEntity>()

    override fun observeAll(): Flow<List<RunEntity>> = MutableStateFlow(rows.toList())
    override suspend fun get(id: String): RunEntity? = rows.firstOrNull { it.id == id }
    override suspend fun insert(run: RunEntity) {
        rows += run
    }

    override suspend fun clear() = rows.clear()
}

/** Only [touchLastRun] is on the path under test; the rest would be noise if it were called. */
private class FakeWorkflowDao : WorkflowDao {
    override fun observeAll(): Flow<List<WorkflowEntity>> = unused()
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
    override suspend fun touchLastRun(id: String, at: Long) = Unit

    private fun unused(): Nothing = error("not part of this test")
}

private class FakeSettings : SettingsRepository {
    override val settings: Flow<UserSettings> = MutableStateFlow(UserSettings())
    override suspend fun current(): UserSettings = UserSettings()
    override suspend fun update(transform: (UserSettings) -> UserSettings) = Unit
}
