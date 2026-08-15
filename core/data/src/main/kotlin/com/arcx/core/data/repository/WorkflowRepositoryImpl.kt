package com.arcx.core.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.arcx.core.common.di.IoDispatcher
import com.arcx.core.common.time.TimeSource
import com.arcx.core.data.database.WorkflowDao
import com.arcx.core.data.di.SettingsDataStore
import com.arcx.core.data.mapper.toEntity
import com.arcx.core.data.mapper.toModel
import com.arcx.core.data.bundle.toWorkflowAsStarter
import com.arcx.core.data.seed.readStarterWorkflows
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.Workflow
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class WorkflowRepositoryImpl @Inject constructor(
    private val dao: WorkflowDao,
    private val time: TimeSource,
    @ApplicationContext private val context: Context,
    @SettingsDataStore private val dataStore: DataStore<Preferences>,
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
    /**
     * Installs starters the user has never been offered.
     *
     * Not "seed once if the table is empty": that shipped every later starter only to people who
     * installed after it was written. Not "seed anything missing from the table" either — that
     * resurrects a built-in the moment someone deletes it. So we remember which starters have
     * ever been handed over, and only ever add names absent from that list. Deleting one keeps
     * it deleted; a genuinely new one arrives on the next launch.
     */
    override suspend fun installNewBuiltIns() {
        val starters = withContext(io) { readStarterWorkflows(context) }

        // An install made before this list was kept has starters in the table and nothing
        // recorded, so adopt whatever built-ins are already there. Without this the first launch
        // after upgrading would insert a second copy of every starter. A built-in deleted before
        // the upgrade is not in the table to adopt and so comes back once; there is no earlier
        // state to consult, and one reappearance beats duplicating the whole set.
        val alreadyOffered = dataStore.data.first()[SEEDED_STARTERS]
            ?: dao.observeAll().first().filter { it.isBuiltIn }.map { it.name }.toSet()

        val fresh = starters.filter { it.name !in alreadyOffered }
        if (fresh.isNotEmpty()) {
            val now = time.nowMillis()
            // The starter mapper, not the import one: a starter is meant to arrive pinned and
            // read-only, which is exactly what the import path has to strip.
            dao.insertAll(fresh.map { it.toWorkflowAsStarter(now).toEntity() })
        }
        dataStore.edit { prefs ->
            prefs[SEEDED_STARTERS] = alreadyOffered + starters.map { it.name }
        }
    }

    private companion object {
        val SEEDED_STARTERS = stringSetPreferencesKey("seeded_starter_names")
    }
}
