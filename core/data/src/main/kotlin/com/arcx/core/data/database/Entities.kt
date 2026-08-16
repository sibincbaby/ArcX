package com.arcx.core.data.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.ProviderType
import com.arcx.core.model.RunStatus
import com.arcx.core.model.WorkflowCategory

@Entity(tableName = "workflows")
data class WorkflowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val icon: String,
    val category: WorkflowCategory,
    val input: InputSource,
    val prompt: String,
    val systemPrompt: String?,
    val providerId: String?,
    val model: String?,
    val output: OutputTarget,
    val temperature: Float?,
    val maxTokens: Int?,
    val isPinned: Boolean,
    val isFavorite: Boolean,
    val isBuiltIn: Boolean,
    /**
     * See [com.arcx.core.model.Workflow.enabled]. Every picker query filters on it.
     *
     * The declared default is not decoration. `MIGRATION_3_4` has to add this column with
     * `DEFAULT 1` — that is what backfills every existing row as switched on — and Room validates
     * the migrated table against the one it would have created itself. Without the same default
     * here, a fresh install and an upgraded one describe the same column differently, which is
     * the shape of mismatch that only shows up on someone else's phone, months later.
     */
    @ColumnInfo(defaultValue = "1")
    val enabled: Boolean,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    /**
     * Not part of [com.arcx.core.model.Workflow] — it is storage bookkeeping that only exists
     * so the Home screen's "Recent" row has something to sort by. Null until the first run.
     */
    val lastRunAt: Long?,
)

/** The API key is deliberately absent; it lives in the Keystore-backed vault, keyed by [id]. */
@Entity(tableName = "providers")
data class ProviderEntity(
    @PrimaryKey val id: String,
    val type: ProviderType,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val streaming: Boolean,
    val createdAt: Long,
)

@Entity(tableName = "runs", indices = [Index("startedAt")])
data class RunEntity(
    @PrimaryKey val id: String,
    val workflowId: String,
    val workflowName: String,
    val workflowIcon: String,
    val startedAt: Long,
    val durationMs: Long,
    val providerLabel: String,
    val model: String,
    val status: RunStatus,
    val inputPreview: String,
    val outputPreview: String?,
    val error: String?,
    /** Absolute path of the run's screenshot in internal storage; null for every other run. */
    val screenshotPath: String?,
)
