package com.arcx.feature.workflow

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.VerticalAlignBottom
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.WebAsset
import androidx.compose.ui.graphics.vector.ImageVector
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
        InputSource.SCREENSHOT -> "A picture of my screen"
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
        InputSource.SCREENSHOT ->
            "Sends what you are looking at as an image. Fire it from the floating bubble or the " +
                "accessibility button — from inside ArcX the only thing on screen is ArcX. Needs " +
                "screen reading turned on, and a model that can see."
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

/**
 * A glyph per choice, so the trigger and output cards can be recognised before they are read.
 * Deliberately plain Material icons rather than the emoji a workflow carries: these name a
 * mechanism, and a mechanism should look the same in everybody's library.
 */
internal val InputSource.icon: ImageVector
    get() = when (this) {
        InputSource.SELECTED_TEXT -> Icons.Outlined.TextFields
        InputSource.CLIPBOARD -> Icons.Outlined.ContentPaste
        InputSource.SCREEN_TEXT -> Icons.Outlined.Visibility
        InputSource.SHARE_INTENT -> Icons.Outlined.Share
        InputSource.MANUAL -> Icons.Outlined.Keyboard
        InputSource.IMAGE -> Icons.Outlined.Image
        InputSource.PDF -> Icons.Outlined.Description
        InputSource.CAMERA -> Icons.Outlined.PhotoCamera
        InputSource.AUDIO -> Icons.Outlined.Mic
        InputSource.SCREENSHOT -> Icons.Outlined.PhoneAndroid
        InputSource.NONE -> Icons.Outlined.Bolt
    }

internal val OutputTarget.icon: ImageVector
    get() = when (this) {
        OutputTarget.POPUP -> Icons.Outlined.WebAsset
        OutputTarget.BOTTOM_SHEET -> Icons.Outlined.VerticalAlignBottom
        OutputTarget.CLIPBOARD -> Icons.Outlined.ContentCopy
        OutputTarget.REPLACE_SELECTION -> Icons.Outlined.FindReplace
        OutputTarget.SHARE -> Icons.Outlined.Share
        OutputTarget.SAVE_MARKDOWN -> Icons.Outlined.Description
        OutputTarget.SAVE_PDF -> Icons.Outlined.PictureAsPdf
        OutputTarget.NOTIFICATION -> Icons.Outlined.Notifications
    }

/**
 * Sentence fragments for the builder's closing summary — the one place the whole workflow is
 * stated back in plain English before it is saved. Someone who cannot recognise their own
 * workflow in that sentence has built the wrong thing, and this is the last cheap moment to
 * find out.
 */
internal val InputSource.summaryPhrase: String
    get() = when (this) {
        InputSource.SELECTED_TEXT -> "text you select in any app"
        InputSource.CLIPBOARD -> "whatever you last copied"
        InputSource.SCREEN_TEXT -> "the text on your screen"
        InputSource.SHARE_INTENT -> "something you share to ArcX"
        InputSource.MANUAL -> "text you type in"
        InputSource.IMAGE -> "an image you pick"
        InputSource.PDF -> "a PDF you pick"
        InputSource.CAMERA -> "a photo you take"
        InputSource.AUDIO -> "an audio clip"
        InputSource.SCREENSHOT -> "a picture of your screen"
        InputSource.NONE -> "no input at all"
    }

internal val OutputTarget.summaryPhrase: String
    get() = when (this) {
        OutputTarget.POPUP -> "shows the answer in a floating popup"
        OutputTarget.BOTTOM_SHEET -> "shows the answer in a sheet"
        OutputTarget.CLIPBOARD -> "copies the answer to your clipboard"
        OutputTarget.REPLACE_SELECTION -> "replaces what you selected"
        OutputTarget.SHARE -> "hands the answer to the share sheet"
        OutputTarget.SAVE_MARKDOWN -> "saves the answer as a Markdown file"
        OutputTarget.SAVE_PDF -> "saves the answer as a PDF"
        OutputTarget.NOTIFICATION -> "delivers the answer as a notification"
    }
