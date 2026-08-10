package com.arcx.feature.runner.ui

import com.arcx.core.model.AiError
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.WorkflowCategory
import java.util.Locale

/**
 * User-facing wording for every failure the executor can hand back. Each one names what went
 * wrong and, where the user can do something about it, where to go — an error the user cannot
 * act on is just a dead end with nicer typography.
 */
internal fun AiError.title(): String = when (this) {
    is AiError.MissingKey -> "No API key for $providerLabel"
    is AiError.InvalidKey -> "$providerLabel rejected the key"
    is AiError.RateLimited -> "Too many requests"
    is AiError.Network -> "Couldn't reach the provider"
    is AiError.ContentBlocked -> "Request was blocked"
    is AiError.NoProvider -> "No provider connected"
    is AiError.NoInput -> "Nothing to work on"
    is AiError.Server -> "The provider had a problem"
    is AiError.Unknown -> "Something went wrong"
}

internal fun AiError.body(): String = when (this) {
    is AiError.MissingKey -> "Add an API key in Settings."
    is AiError.InvalidKey -> "Open Settings and paste the key again."
    is AiError.RateLimited -> retryAfterSeconds
        ?.let { "The provider asked to wait about $it seconds." }
        ?: "Wait a moment, then try again."

    is AiError.Network -> "Check your connection."
    is AiError.ContentBlocked -> reason
    is AiError.NoProvider -> "Connect a provider first."
    is AiError.NoInput ->
        "Select or copy some text first, then run this workflow again."

    is AiError.Server -> "Returned HTTP $code. Trying again usually works."
    is AiError.Unknown -> message ?: "Try again."
}

internal fun WorkflowCategory.label(): String =
    name.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.getDefault()) }

internal fun OutputTarget.label(): String = when (this) {
    OutputTarget.POPUP -> "Popup"
    OutputTarget.BOTTOM_SHEET -> "Sheet"
    OutputTarget.CLIPBOARD -> "Copy"
    OutputTarget.REPLACE_SELECTION -> "Replace"
    OutputTarget.SHARE -> "Share"
    OutputTarget.SAVE_MARKDOWN -> "Markdown"
    OutputTarget.SAVE_PDF -> "PDF"
    OutputTarget.NOTIFICATION -> "Notification"
}

internal fun formatDuration(millis: Long): String =
    if (millis < 1_000) "${millis}ms"
    else String.format(Locale.getDefault(), "%.1fs", millis / 1_000f)
