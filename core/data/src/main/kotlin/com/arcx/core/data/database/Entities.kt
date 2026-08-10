package com.arcx.core.data.database

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
)
