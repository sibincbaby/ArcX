package com.arcx.core.model

import kotlinx.serialization.Serializable

/**
 * The icon a workflow gets when nothing else is chosen.
 *
 * Declared here rather than in the design system because three modules that cannot see Compose
 * need it: [Workflow]'s own default, [WorkflowSpec]'s, and the import sanitiser in `:core:data`
 * that turns a blank icon back into a key. `:core:designsystem` re-exports this value under the
 * same name, so the drawing side and the storing side cannot drift apart.
 */
const val DEFAULT_WORKFLOW_ICON: String = "auto_awesome"

/**
 * A reusable AI action. This is the whole product in one type: the user builds one of these
 * once, then fires it from anywhere on the device.
 */
@Serializable
data class Workflow(
    val id: String,
    val name: String,
    /**
     * Icon key from the design system's set, shown in pickers, shortcuts and the widget.
     * Workflows made before that set existed hold an emoji instead, and still render as one.
     */
    val icon: String = DEFAULT_WORKFLOW_ICON,
    val category: WorkflowCategory = WorkflowCategory.CUSTOM,
    val input: InputSource = InputSource.SELECTED_TEXT,
    val prompt: String,
    val systemPrompt: String? = null,
    /** Null means "use the default provider from settings". */
    val providerId: String? = null,
    /** Null means "use the provider's default model". */
    val model: String? = null,
    val output: OutputTarget = OutputTarget.BOTTOM_SHEET,
    val temperature: Float? = null,
    val maxTokens: Int? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false,
    val isBuiltIn: Boolean = false,
    /**
     * Whether this workflow is offered anywhere the user picks something to run — the runner's
     * picker, the sidebar panel, the widget, the launcher's shortcut menu.
     *
     * Off is not delete. The row stays in the Library, visibly off and one tap from back on, and
     * nothing about it changes: not its history, not its screenshots, not the record itself. That
     * is the whole point — a starter the user deletes is gone for good, so "I don't use this" had
     * no answer short of destroying something.
     *
     * Defaults to true, and every path that creates a workflow leaves it that way: a migration or
     * an import that switched something off would hide it in every picker with nothing on screen
     * to explain why.
     */
    val enabled: Boolean = true,
    val sortOrder: Int = 0,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/** Where a workflow's content comes from. */
@Serializable
enum class InputSource {
    SELECTED_TEXT,
    CLIPBOARD,
    SCREEN_TEXT,
    SHARE_INTENT,
    MANUAL,
    IMAGE,
    PDF,
    CAMERA,
    AUDIO,
    SCREENSHOT,
    NONE,
}

/** What happens to the model's answer. */
@Serializable
enum class OutputTarget {
    POPUP,
    BOTTOM_SHEET,
    CLIPBOARD,
    REPLACE_SELECTION,
    SHARE,
    SAVE_MARKDOWN,
    SAVE_PDF,
    NOTIFICATION,
}

@Serializable
enum class WorkflowCategory {
    DEVELOPMENT,
    WRITING,
    EDUCATION,
    RESEARCH,
    BUSINESS,
    PRODUCTIVITY,
    TRANSLATION,
    CUSTOM,
}
