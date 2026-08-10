package com.arcx.feature.discover

import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.WorkflowCategory

/**
 * Display copy for the model enums. The builder in :feature:workflow has its own longer
 * version with per-option explanations; the gallery only ever needs the short name, and a
 * feature module borrowing UI strings from another feature would couple the two for nothing.
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
        InputSource.SELECTED_TEXT -> "Selected text"
        InputSource.CLIPBOARD -> "Clipboard"
        InputSource.SCREEN_TEXT -> "Screen text"
        InputSource.SHARE_INTENT -> "Shared item"
        InputSource.MANUAL -> "Typed in"
        InputSource.IMAGE -> "An image"
        InputSource.PDF -> "A PDF"
        InputSource.CAMERA -> "A photo"
        InputSource.AUDIO -> "Audio"
        InputSource.SCREENSHOT -> "Screenshot"
        InputSource.NONE -> "No input"
    }

internal val OutputTarget.label: String
    get() = when (this) {
        OutputTarget.POPUP -> "Popup"
        OutputTarget.BOTTOM_SHEET -> "Bottom sheet"
        OutputTarget.CLIPBOARD -> "Clipboard"
        OutputTarget.REPLACE_SELECTION -> "Replaces the selection"
        OutputTarget.SHARE -> "Share sheet"
        OutputTarget.SAVE_MARKDOWN -> "Markdown file"
        OutputTarget.SAVE_PDF -> "PDF file"
        OutputTarget.NOTIFICATION -> "Notification"
    }
