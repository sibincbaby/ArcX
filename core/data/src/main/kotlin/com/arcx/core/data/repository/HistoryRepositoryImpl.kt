package com.arcx.core.data.repository

import com.arcx.core.data.database.RunDao
import com.arcx.core.data.database.WorkflowDao
import com.arcx.core.data.mapper.toEntity
import com.arcx.core.data.mapper.toModel
import com.arcx.core.domain.capture.ScreenshotStore
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.RunOutcome
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunStatus
import com.arcx.core.model.RunSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

internal class HistoryRepositoryImpl @Inject constructor(
    private val runDao: RunDao,
    private val workflowDao: WorkflowDao,
    private val settings: SettingsRepository,
    private val screenshots: ScreenshotStore,
) : HistoryRepository {

    override fun observeRecent(limit: Int): Flow<List<RunSummary>> =
        runDao.observeRecent(limit).map { rows -> rows.map { it.toModel() } }

    override fun observeSince(since: Long): Flow<List<RunOutcome>> =
        runDao.observeSince(since).map { rows -> rows.map { RunOutcome(it.durationMs, it.status) } }

    override fun observeAverageDurations(): Flow<Map<String, Long>> =
        runDao.observeAverageDurations(RunStatus.SUCCESS)
            .map { rows -> rows.associate { it.workflowId to it.averageMs.toLong() } }

    override suspend fun get(id: String): RunRecord? = runDao.get(id)?.toModel()

    /**
     * Recency is tracked even when history is switched off: "don't keep a log of what I ran" is a
     * privacy choice about the History screen, not a request to break the Home screen's ordering.
     */
    override suspend fun record(run: RunRecord) {
        workflowDao.touchLastRun(run.workflowId, run.startedAt)
        if (!settings.current().historyEnabled) return
        runDao.insert(run.toEntity())
        prune()
    }

    /**
     * Drops the oldest rows past the cap, and their screenshots with them.
     *
     * The images have to go in the same breath: the retention sweep expires them by age, and a
     * user on "keep forever" would otherwise be left with pictures on disk that no row points
     * at and no screen can offer to delete — exactly the leak [ScreenshotStore] warns about.
     */
    private suspend fun prune() {
        val orphaned = runDao.screenshotsBeyond(RunRecord.HISTORY_LIMIT)
        runDao.prune(RunRecord.HISTORY_LIMIT)
        if (orphaned.isNotEmpty()) screenshots.delete(orphaned)
    }

    /**
     * The images go with the rows. Deleting only the rows would leave screenshots on disk that
     * History can no longer show and that no button in the app can ever reach — the whole reason
     * this wipes the directory rather than only the paths it knows about.
     */
    override suspend fun clear() {
        runDao.clear()
        screenshots.deleteAll()
    }
}
