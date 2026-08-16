package com.arcx.core.model

import kotlinx.serialization.Serializable

/**
 * A workflow as it travels: [Workflow] minus the three fields the device owns — `id`, `createdAt`,
 * `updatedAt`. Dropping the id is what lets the same shape serve the bundled starters, the bundled
 * gallery, an export and someone else's file all at once: a spec read on another phone gets a fresh
 * local id rather than colliding with whatever is already there.
 *
 * This is the *only* declaration of the format. It used to exist twice — once in `:core:data` for
 * the starters and once in `:feature:discover` for import/export — with two different `icon`
 * defaults, so the same file read through the two paths produced two different workflows.
 *
 * It lives in `:core:model` because it is drawn: Discover holds three `List<WorkflowSpec>` fields
 * in its UI state and passes specs into composables, so it needs the stability declaration that
 * `compose-stability.conf` makes for this package. That declaration is a promise, and this type
 * keeps it — every property is a `val` holding a primitive, a String or an enum.
 *
 * The envelope that carries a list of these, and the `Json` that reads it, deliberately do **not**
 * live here: they are wire concerns that nothing ever draws, and putting a type holding a `List`
 * in this package would widen the stability promise for no benefit. They are in `:core:data`,
 * beside the repository that owns parsing.
 *
 * **[Workflow.enabled] is deliberately not here**, so a bundle cannot carry it in either direction.
 * A file that could arrive switched off would install something the importer cannot see in any
 * picker and was never told about — the strongest version of the problem `sanitisedForImport`
 * exists for, since unlike a bad `sortOrder` there is nothing on screen to notice. Leaving the
 * field out of the format is stronger than stripping it on the way in: there is no key to honour
 * later by mistake. A hand-written file that sets `"enabled": false` anyway is dropped by
 * `ignoreUnknownKeys` and imports switched on, which `WorkflowBundleTest` pins.
 *
 * Nothing is lost the format did not already lose: `isPinned`, `isFavorite` and `sortOrder` travel
 * but are cleared on import for the same reason, so an export has never restored the author's
 * arrangement of their own library.
 */
@Serializable
data class WorkflowSpec(
    val name: String,
    val icon: String = DEFAULT_WORKFLOW_ICON,
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
