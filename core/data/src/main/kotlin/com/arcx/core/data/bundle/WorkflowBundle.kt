package com.arcx.core.data.bundle

import com.arcx.core.model.DEFAULT_WORKFLOW_ICON
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowSpec
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * The envelope every `.json` bundle ArcX reads or writes is wrapped in — the bundled starters, the
 * bundled gallery, an export, and a file someone was sent.
 *
 * It lives beside the repository rather than in `:core:model` on purpose: it is a wire concern that
 * nothing ever draws, so the stability promise `compose-stability.conf` makes for `:core:model`
 * does not need to be widened to cover a type holding a `List`. `WorkflowSpec` — which *is* drawn —
 * is the half that lives there.
 */
@Serializable
data class WorkflowBundle(
    val version: Int = 1,
    val workflows: List<WorkflowSpec>,
)

/**
 * Both decoding flags are what makes a file written by a newer build still import on an older one,
 * and each covers a different half of it.
 *
 * `ignoreUnknownKeys` drops fields this build has never heard of. It does **not** cover unknown
 * *enum values*, which is the half that used to be missing: one `"input": "VIDEO"` threw, and since
 * the file is decoded in a single call the user was told the whole thing "does not look like an
 * ArcX workflow file" — losing every workflow in it, not just the one field. `coerceInputValues`
 * falls back to the property's declared default instead, which is the safe direction here:
 * SELECTED_TEXT is the least invasive input source and BOTTOM_SHEET only shows the answer.
 * Coercion needs a default to fall back to, so `name` and `prompt` still fail correctly.
 *
 * `prettyPrint` is for the writing end only. An export is a file a person opens, reads and emails.
 */
private val bundleJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    prettyPrint = true
}

fun parseWorkflowBundle(json: String): WorkflowBundle = bundleJson.decodeFromString(json)

fun encodeWorkflowBundle(bundle: WorkflowBundle): String = bundleJson.encodeToString(bundle)

/**
 * The sanitisation table for everything arriving from a file, applied once, on the way in.
 *
 * None of these fields means anything off the device it was written on, and four of them are
 * actively unsafe:
 *
 * - `providerId` names a row in the *author's* Room table. Ids are random UUIDs minted per device,
 *   so an imported one is a dangling reference with probability ~1 — and it fails silently, since
 *   `ProviderRepository.resolve` quietly falls through to the importer's default. The workflow then
 *   runs correctly while the editor shows it pinned to a provider that does not exist.
 * - `isBuiltIn` would let a file pose as first-party ArcX content *and* be uneditable — the library
 *   offers built-ins "Duplicate to edit" instead of Edit.
 * - `isPinned` puts the workflow on the importer's Home screen and in their widget; `isFavorite`
 *   does the same to Favourites. A file should not be able to place itself there.
 * - `sortOrder` orders every library query, so a value from the file decides where the workflow
 *   sits among the ones the user made themselves. Zero is what the editor gives anything the user
 *   creates, which is exactly what an imported workflow becomes.
 *
 * A blank `icon` becomes the key rather than being left empty, because an empty icon renders as the
 * ✨ glyph — the emoji `MIGRATION_2_3` was written to get out of this column, in a one-shot 2→3
 * migration that will never run again to clean it up. A non-empty icon is left completely alone,
 * emoji included: a user's own emoji surviving is deliberate.
 *
 * What is *not* here, deliberately: clamping `maxTokens`, `name` or prompt sizes, and whether
 * `model` survives at all. Those are product decisions (docs/workflow-sharing.md §8), not
 * refactoring.
 */
fun WorkflowSpec.sanitisedForImport(): WorkflowSpec = copy(
    icon = icon.ifBlank { DEFAULT_WORKFLOW_ICON },
    providerId = null,
    isPinned = false,
    isFavorite = false,
    isBuiltIn = false,
    sortOrder = 0,
)

/**
 * The seeding mapper: a starter shipped inside the app, arriving with the flags the asset asked
 * for and an id the caller chose.
 *
 * This is how "Rewrite Professionally" arrives pinned and read-only, and it is the reason there are
 * two mappers rather than one. Everything it keeps — `isBuiltIn`, `isPinned`, `isFavorite`,
 * `sortOrder`, `providerId` — is exactly what [toWorkflowAsImport] must strip. Collapsing them into
 * a single `toWorkflow` reopens that hole, which is why the difference is in the names.
 */
fun WorkflowSpec.toWorkflowAsStarter(
    now: Long,
    id: String = UUID.randomUUID().toString(),
): Workflow = Workflow(
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

/**
 * The import mapper: a workflow arriving from the gallery or from a file, becoming the user's own.
 *
 * A blank id makes `SaveWorkflowUseCase` mint a fresh one, so importing the same file twice gives
 * two workflows rather than silently overwriting the first.
 *
 * It runs [sanitisedForImport] itself rather than trusting the caller to have done it. The caller
 * *does* — the repository sanitises on read so the user is shown what they will actually get — but
 * this is the last thing between a file and Room, and the flags it clears are the ones that are
 * only unsafe when nobody remembered.
 */
fun WorkflowSpec.toWorkflowAsImport(): Workflow = sanitisedForImport().let { spec ->
    Workflow(
        id = "",
        name = spec.name,
        icon = spec.icon,
        category = spec.category,
        input = spec.input,
        prompt = spec.prompt,
        systemPrompt = spec.systemPrompt,
        providerId = spec.providerId,
        model = spec.model,
        output = spec.output,
        temperature = spec.temperature,
        maxTokens = spec.maxTokens,
        isPinned = spec.isPinned,
        isFavorite = spec.isFavorite,
        isBuiltIn = spec.isBuiltIn,
        sortOrder = spec.sortOrder,
    )
}

/**
 * The export mapper. Drops the provider id — it names a connection that only exists on this device
 * — and clears `isBuiltIn`, so a starter shared with someone else arrives as a workflow they own.
 *
 * `isPinned`, `isFavorite` and `sortOrder` still travel: they are the author's arrangement of their
 * own library, and this file is also the user's backup of it. Nothing downstream trusts them —
 * [sanitisedForImport] clears all three on the way back in.
 */
fun Workflow.toSpec(): WorkflowSpec = WorkflowSpec(
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
