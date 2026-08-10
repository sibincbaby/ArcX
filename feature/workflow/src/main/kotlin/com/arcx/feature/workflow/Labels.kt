package com.arcx.feature.workflow

import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.WorkflowCategory

/**
 * Display copy for the model enums. It lives here rather than on the enums themselves because
 * it is UI text, not domain data — and because the builder needs to say what each choice
 * *means* to someone who has never configured an AI tool.
 */

internal val WorkflowCategory.label: String
    get() = when (this) {
        WorkflowCategory.DEVELOPMENT -> "Development"
        WorkflowCategory.WRITING -> "Writing"
        WorkflowCategory.EDUCATION -> "Education"
        WorkflowCategory.RESEARCH -> "Research"
        WorkflowCategory.BUSINESS -> "Business"
        WorkflowCategory.PRODUCTIVITY -> "Productivity"
        WorkflowCategory.TRANSLATION -> "Translation"
        WorkflowCategory.CUSTOM -> "Custom"
    }

internal val InputSource.label: String
    get() = when (this) {
        InputSource.SELECTED_TEXT -> "Text I select"
        InputSource.CLIPBOARD -> "Whatever I copied"
        InputSource.SCREEN_TEXT -> "Text on screen"
        InputSource.SHARE_INTENT -> "Something I share to ArcX"
        InputSource.MANUAL -> "I'll type it each time"
        InputSource.IMAGE -> "An image I pick"
        InputSource.PDF -> "A PDF I pick"
        InputSource.CAMERA -> "A photo I take"
        InputSource.AUDIO -> "An audio clip"
        InputSource.SCREENSHOT -> "A screenshot"
        InputSource.NONE -> "Nothing — the prompt is enough"
    }

/** One plain line per input source, shown under the dropdown so the choice is never a guess. */
internal val InputSource.explanation: String
    get() = when (this) {
        InputSource.SELECTED_TEXT -> "Highlight text in any app and pick ArcX from the popup menu."
        InputSource.CLIPBOARD -> "Runs against the last thing you copied, wherever you copied it."
        InputSource.SCREEN_TEXT -> "Reads everything visible in the current app. Needs the accessibility service."
        InputSource.SHARE_INTENT -> "Send a page, message or note to ArcX through the share sheet."
        InputSource.MANUAL -> "ArcX asks you for the text when the workflow runs."
        InputSource.IMAGE -> "Pick a picture from your gallery. Needs a model that can see."
        InputSource.PDF -> "Pick a PDF and send its contents. Needs a model that reads documents."
        InputSource.CAMERA -> "Opens the camera so you can shoot what you want looked at."
        InputSource.AUDIO -> "Pick or record a clip to transcribe or summarise."
        InputSource.SCREENSHOT -> "Captures the current screen as a picture rather than as text."
        InputSource.NONE -> "No input at all — good for generators and daily prompts."
    }

internal val OutputTarget.label: String
    get() = when (this) {
        OutputTarget.POPUP -> "Floating popup"
        OutputTarget.BOTTOM_SHEET -> "Bottom sheet"
        OutputTarget.CLIPBOARD -> "Copy to clipboard"
        OutputTarget.REPLACE_SELECTION -> "Replace what I selected"
        OutputTarget.SHARE -> "Open the share sheet"
        OutputTarget.SAVE_MARKDOWN -> "Save as Markdown"
        OutputTarget.SAVE_PDF -> "Save as PDF"
        OutputTarget.NOTIFICATION -> "Notification"
    }

internal val OutputTarget.explanation: String
    get() = when (this) {
        OutputTarget.POPUP -> "A small window over whatever app you are in."
        OutputTarget.BOTTOM_SHEET -> "A panel you can read, scroll and copy from."
        OutputTarget.CLIPBOARD -> "Silently copies the answer so you can paste it."
        OutputTarget.REPLACE_SELECTION -> "Swaps your selection for the answer. Only works in editable fields."
        OutputTarget.SHARE -> "Hands the answer to another app."
        OutputTarget.SAVE_MARKDOWN -> "Writes a .md file you choose the location for."
        OutputTarget.SAVE_PDF -> "Writes a PDF you choose the location for."
        OutputTarget.NOTIFICATION -> "Arrives quietly in the shade — good for long jobs."
    }

/** "Writing · Text I select → Bottom sheet", the one-line identity of a workflow in a list. */
internal fun workflowSubtitle(
    category: WorkflowCategory,
    input: InputSource,
    output: OutputTarget,
): String = "${category.label} · ${input.label} → ${output.label}"
