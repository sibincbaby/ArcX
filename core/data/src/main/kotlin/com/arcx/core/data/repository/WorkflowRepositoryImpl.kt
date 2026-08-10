package com.arcx.core.data.repository

import android.content.Context
import com.arcx.core.common.di.IoDispatcher
import com.arcx.core.common.time.TimeSource
import com.arcx.core.data.database.WorkflowDao
import com.arcx.core.data.mapper.toEntity
import com.arcx.core.data.mapper.toModel
import com.arcx.core.data.seed.readStarterWorkflows
import com.arcx.core.data.seed.toWorkflow
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.Workflow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class WorkflowRepositoryImpl @Inject constructor(
    private val dao: WorkflowDao,
    private val time: TimeSource,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) : WorkflowRepository {

    override fun observeAll(): Flow<List<Workflow>> =
        dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeFavorites(): Flow<List<Workflow>> =
        dao.observeFavorites().map { rows -> rows.map { it.toModel() } }

    override fun observePinned(): Flow<List<Workflow>> =
        dao.observePinned().map { rows -> rows.map { it.toModel() } }

    override fun observeRecent(limit: Int): Flow<List<Workflow>> =
        dao.observeRecent(limit).map { rows -> rows.map { it.toModel() } }

    override suspend fun get(id: String): Workflow? = dao.get(id)?.toModel()

    override suspend fun upsert(workflow: Workflow) {
        val now = time.nowMillis()
        // The caller works with a Workflow, which knows nothing about lastRunAt, so an edit
        // would otherwise wipe the row's run history out of the "Recent" ordering.
        val lastRunAt = dao.lastRunAt(workflow.id)
        val stamped = workflow.copy(
            createdAt = workflow.createdAt.takeIf { it > 0L } ?: now,
            updatedAt = now,
        )
        dao.upsert(stamped.toEntity(lastRunAt = lastRunAt))
    }

    override suspend fun delete(id: String) = dao.delete(id)

    override suspend fun setFavorite(id: String, favorite: Boolean) =
        dao.setFavorite(id, favorite, time.nowMillis())

    override suspend fun setPinned(id: String, pinned: Boolean) =
        dao.setPinned(id, pinned, time.nowMillis())

    /**
     * Emptiness, not a "seeded" flag, is the trigger: a user who deletes every starter should not
     * have them reappear on the next launch, and one who wipes app data should get them back.
     */
    override suspend fun seedBuiltInsIfEmpty() {
        if (dao.count() > 0) return
        val now = time.nowMillis()
        val starters = withContext(io) { readStarterWorkflows(context) }
        dao.insertAll(starters.map { it.toWorkflow(now).toEntity() })
    }
}
