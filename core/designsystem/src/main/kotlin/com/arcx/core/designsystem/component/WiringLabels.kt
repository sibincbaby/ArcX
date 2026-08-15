package com.arcx.core.designsystem.component

import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget

/**
 * One word per end of a workflow's wiring, for [WiringChips].
 *
 * These live beside the component that draws them rather than in a feature, because four
 * surfaces now state a workflow's wiring the same way — home, library, the picker and the
 * bubble — and four private copies of "Selection" would drift apart on the first rename.
 *
 * The builder in :feature:workflow keeps its own longer, explanatory labels. That is a
 * different job: these name a choice already made, those help someone make it.
 */
val InputSource.shortLabel: String
    get() = when (this) {
        InputSource.SELECTED_TEXT -> "Selection"
        InputSource.CLIPBOARD -> "Clipboard"
        InputSource.SCREEN_TEXT -> "Screen text"
        InputSource.SHARE_INTENT -> "Share"
        InputSource.MANUAL -> "Typed in"
        InputSource.IMAGE -> "Image"
        InputSource.PDF -> "PDF"
        InputSource.CAMERA -> "Photo"
        InputSource.AUDIO -> "Audio"
        InputSource.SCREENSHOT -> "Screenshot"
        InputSource.NONE -> "No input"
    }

val OutputTarget.shortLabel: String
    get() = when (this) {
        OutputTarget.POPUP -> "Popup"
        OutputTarget.BOTTOM_SHEET -> "Sheet"
        OutputTarget.CLIPBOARD -> "Clipboard"
        OutputTarget.REPLACE_SELECTION -> "Replace"
        OutputTarget.SHARE -> "Share"
        OutputTarget.SAVE_MARKDOWN -> "Markdown"
        OutputTarget.SAVE_PDF -> "PDF"
        OutputTarget.NOTIFICATION -> "Notification"
    }
