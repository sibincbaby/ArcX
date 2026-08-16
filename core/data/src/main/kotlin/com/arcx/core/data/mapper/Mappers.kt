package com.arcx.core.data.mapper

import com.arcx.core.data.database.ProviderEntity
import com.arcx.core.data.database.RunEntity
import com.arcx.core.data.database.RunSummaryRow
import com.arcx.core.data.database.WorkflowEntity
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunSummary
import com.arcx.core.model.Workflow

fun WorkflowEntity.toModel(): Workflow = Workflow(
    id = id,
    name = name,
    icon = icon,
    category = category,
    input = input,
    prompt = prompt,
    systemPrompt = systemPrompt,
    providerId = providerId,
    model = model,
    output = output,
    temperature = temperature,
    maxTokens = maxTokens,
    isPinned = isPinned,
    isFavorite = isFavorite,
    isBuiltIn = isBuiltIn,
    enabled = enabled,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

/** [lastRunAt] has no home in the domain model, so callers pass through whatever the row held. */
fun Workflow.toEntity(lastRunAt: Long? = null): WorkflowEntity = WorkflowEntity(
    id = id,
    name = name,
    icon = icon,
    category = category,
    input = input,
    prompt = prompt,
    systemPrompt = systemPrompt,
    providerId = providerId,
    model = model,
    output = output,
    temperature = temperature,
    maxTokens = maxTokens,
    isPinned = isPinned,
    isFavorite = isFavorite,
    isBuiltIn = isBuiltIn,
    enabled = enabled,
    sortOrder = sortOrder,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastRunAt = lastRunAt,
)

fun ProviderEntity.toModel(): ProviderConfig = ProviderConfig(
    id = id,
    type = type,
    label = label,
    baseUrl = baseUrl,
    defaultModel = defaultModel,
    streaming = streaming,
    createdAt = createdAt,
)

fun ProviderConfig.toEntity(): ProviderEntity = ProviderEntity(
    id = id,
    type = type,
    label = label,
    baseUrl = baseUrl,
    defaultModel = defaultModel,
    streaming = streaming,
    createdAt = createdAt,
)

fun RunEntity.toModel(): RunRecord = RunRecord(
    id = id,
    workflowId = workflowId,
    workflowName = workflowName,
    workflowIcon = workflowIcon,
    startedAt = startedAt,
    durationMs = durationMs,
    providerLabel = providerLabel,
    model = model,
    status = status,
    inputPreview = inputPreview,
    outputPreview = outputPreview,
    error = error,
    screenshotPath = screenshotPath,
)

fun RunSummaryRow.toModel(): RunSummary = RunSummary(
    id = id,
    workflowId = workflowId,
    workflowName = workflowName,
    workflowIcon = workflowIcon,
    startedAt = startedAt,
    durationMs = durationMs,
    providerLabel = providerLabel,
    model = model,
    status = status,
    // First line only: the list gives this one row, and a provider's stack trace would take it.
    error = error?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() },
    hasScreenshot = hasScreenshot,
)

/** Previews are clamped on the way in: a run's full payload must never reach the database. */
fun RunRecord.toEntity(): RunEntity = RunEntity(
    id = id,
    workflowId = workflowId,
    workflowName = workflowName,
    workflowIcon = workflowIcon,
    startedAt = startedAt,
    durationMs = durationMs,
    providerLabel = providerLabel,
    model = model,
    status = status,
    inputPreview = inputPreview.take(RunRecord.PREVIEW_LIMIT),
    outputPreview = outputPreview?.take(RunRecord.PREVIEW_LIMIT),
    error = error?.take(RunRecord.PREVIEW_LIMIT),
    // A path, never the image itself: the bytes stay on disk where deleting a run can reach them.
    screenshotPath = screenshotPath,
)
