package com.arcx.feature.runner.ui

import com.arcx.core.model.AiError
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
    is AiError.NoScreenshot ->
        if (captureAvailable) "Nothing to look at" else "Couldn't capture the screen"
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

    is AiError.NoScreenshot ->
        if (captureAvailable) {
            "ArcX can only photograph the screen from the floating bubble or the accessibility " +
                "button — those fire while the app you are looking at is still the only thing on " +
                "screen. Started from anywhere else, the only thing to photograph is ArcX."
        } else {
            "Screen capture needs the screen reading permission, on Android 11 or newer. " +
                "Turn it on in Settings › Entry points."
        }

    is AiError.Server -> "Returned HTTP $code. Trying again usually works."
    is AiError.Unknown -> message ?: "Try again."
}

// The picker's own short labels for input and output now come from :core:designsystem, beside
// the WiringChips that draw them, so every surface states a workflow's wiring identically.

internal fun formatDuration(millis: Long): String =
    if (millis < 1_000) "${millis}ms"
    else String.format(Locale.getDefault(), "%.1fs", millis / 1_000f)
