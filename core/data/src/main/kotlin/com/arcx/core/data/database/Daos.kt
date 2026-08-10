package com.arcx.core.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
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

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<RunEntity>>

    @Query("SELECT * FROM runs WHERE id = :id")
    suspend fun get(id: String): RunEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: RunEntity)

    @Query("DELETE FROM runs")
    suspend fun clear()
}
