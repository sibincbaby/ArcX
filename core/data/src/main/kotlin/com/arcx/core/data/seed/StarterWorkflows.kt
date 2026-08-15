package com.arcx.core.data.seed

import android.content.Context
import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

const val STARTER_WORKFLOWS_ASSET = "starter_workflows.json"

/**
 * Wire format for the bundled starters. It is deliberately [Workflow] minus the three fields the
 * device owns — `id`, `createdAt`, `updatedAt` — so the same file shape can serve as the
 * import/export format: an exported workflow re-imported on another device gets fresh local ids
 * rather than colliding with whatever is already there.
 */
@Serializable
data class WorkflowBundle(
    val version: Int = 1,
    val workflows: List<WorkflowSpec>,
)

@Serializable
data class WorkflowSpec(
    val name: String,
    val icon: String = "auto_awesome",
    val category: WorkflowCategory = WorkflowCategory.CUSTOM,
    val input: InputSource = InputSource.SELECTED_TEXT,
    val prompt: String,
    val systemPrompt: String? = null,
    val providerId: String? = null,
    val model: String? = null,
    val output: OutputTarget = OutputTarget.BOTTOM_SHEET,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isBuiltIn: Boolean = false,
    val sortOrder: Int = 0,
)

fun WorkflowSpec.toWorkflow(now: Long, id: String = UUID.randomUUID().toString()): Workflow =
    Workflow(
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
        sortOrder = sortOrder,
        createdAt = now,
        updatedAt = now,
    )

// Unknown keys are ignored so a bundle written by a newer build still imports on an older one.
private val bundleJson = Json { ignoreUnknownKeys = true }

fun parseWorkflowBundle(json: String): WorkflowBundle = bundleJson.decodeFromString(json)

/** Blocking asset read; callers are responsible for being off the main thread. */
fun readStarterWorkflows(context: Context): List<WorkflowSpec> =
    context.assets.open(STARTER_WORKFLOWS_ASSET)
        .bufferedReader()
        .use { parseWorkflowBundle(it.readText()).workflows }
