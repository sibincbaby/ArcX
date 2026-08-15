package com.arcx.core.designsystem.format

import java.util.Locale

// How ArcX writes a duration, and how it writes "when". One of each, deliberately.
//
// There used to be three duration formatters and two relative-time formatters, one per screen,
// and they had drifted: the same 380ms run read "380ms" in Activity and "0.4s" on Home, and the
// same row read "just now" in Home's "Recent runs" and "Just now" in Activity — then diverged
// completely after a day, where only one of the two stayed relative. Two lists drawing the same
// history in different words reads as a bug, because it is one.

/**
 * Milliseconds, then seconds, then minutes — the most informative of the three variants this
 * replaces.
 *
 * Sub-second runs are the ones a user judges the app on, and rounding them to "0.4s" throws away
 * the difference between a cached answer and a real round trip. Past a minute the seconds-only
 * form is worse still: nobody reads "83.4s" as "a minute and a half".
 */
fun formatDuration(durationMs: Long): String = when {
    durationMs < 1_000L -> "${durationMs}ms"
    durationMs < 60_000L -> String.format(Locale.getDefault(), "%.1fs", durationMs / 1_000.0)
    else -> "${durationMs / 60_000L}m ${(durationMs % 60_000L) / 1_000L}s"
}

/**
 * Relative the whole way down, including past a day.
 *
 * The variant this replaces fell back to a clock time ("14:02") after twenty-four hours, which
 * only means something if you already know which day it belongs to. Every list that calls this
 * already sits under a day heading, so the date is the one thing a row never has to repeat —
 * "3d ago" is the part the heading does not give you.
 *
 * Capitalised because it always opens the row's subtitle line on both screens.
 *
 * A negative elapsed — a clock the user moved backwards, or a row written a moment ahead —
 * falls into the first branch rather than printing "-1h ago".
 */
fun relativeTime(startedAt: Long, nowMillis: Long): String {
    val elapsed = nowMillis - startedAt
    return when {
        elapsed < 60_000L -> "Just now"
        elapsed < 3_600_000L -> "${elapsed / 60_000L}m ago"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L}h ago"
        else -> "${elapsed / 86_400_000L}d ago"
    }
}
