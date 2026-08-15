package com.arcx.feature.discover

import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The bundled gallery, an exported library and an imported file are all the same envelope —
 * the one :core:data uses for the starter workflows. Sharing one shape means a workflow
 * exported from one phone can be imported on another, or dropped into the gallery, untouched.
 *
 * The declaration is duplicated here rather than reached for across module boundaries: a
 * feature does not depend on :core:data, and this is eleven fields, not a library.
 */
@Serializable
internal data class WorkflowBundle(
    val version: Int = 1,
    val workflows: List<WorkflowSpec>,
)

/** [Workflow] minus the three fields the device owns: `id`, `createdAt`, `updatedAt`. */
@Serializable
internal data class WorkflowSpec(
    val name: String,
    val icon: String = "✨",
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

/**
 * Both decoding flags are what makes a file written by a newer build still import on an older one,
 * and each covers a different half of it.
 *
 * `ignoreUnknownKeys` drops fields this build has never heard of. It does **not** cover unknown
 * *enum values*, which is the half that used to be missing: one `"input": "VIDEO"` threw, and since
 * the file is decoded in a single call the user was told the whole thing "does not look like an
 * ArcX workflow file" — losing every workflow in it, not just the one field.
 * `coerceInputValues` falls back to the property's declared default instead, which is the safe
 * direction here: SELECTED_TEXT is the least invasive input source and BOTTOM_SHEET only shows the
 * answer. Coercion needs a default to fall back to, so `name` and `prompt` still fail correctly.
 */
internal val bundleJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    prettyPrint = true
}

/**
 * A blank id makes `SaveWorkflowUseCase` mint a fresh one, so importing the same file twice
 * gives two workflows rather than silently overwriting the first. `isBuiltIn` is forced off:
 * anything that arrives from a file or the gallery belongs to the user and stays editable.
 */
internal fun WorkflowSpec.toWorkflow(): Workflow = Workflow(
    id = "",
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
    isPinned = false,
    isFavorite = false,
    isBuiltIn = false,
    sortOrder = sortOrder,
)

/**
 * Exports drop the provider id — it names a connection that only exists on this device — and
 * clear `isBuiltIn`, so a starter shared with someone else arrives as a workflow they own.
 */
internal fun Workflow.toSpec(): WorkflowSpec = WorkflowSpec(
    name = name,
    icon = icon,
    category = category,
    input = input,
    prompt = prompt,
    systemPrompt = systemPrompt,
    providerId = null,
    model = model,
    output = output,
    temperature = temperature,
    maxTokens = maxTokens,
    isPinned = isPinned,
    isFavorite = isFavorite,
    isBuiltIn = false,
    sortOrder = sortOrder,
)
