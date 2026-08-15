package com.arcx.core.model

import kotlinx.serialization.Serializable

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
    val icon: String = "auto_awesome",
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
