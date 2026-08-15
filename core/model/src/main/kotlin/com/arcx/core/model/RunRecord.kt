package com.arcx.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class RunStatus { SUCCESS, FAILED, CANCELLED }

/** One execution, for the History screen. Truncated previews only — never the full payload. */
@Serializable
data class RunRecord(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val workflowIcon: String,
    val startedAt: Long,
    val durationMs: Long,
    val providerLabel: String,
    val model: String,
    val status: RunStatus,
    val inputPreview: String,
    val outputPreview: String? = null,
    val error: String? = null,
    /**
     * Absolute path to the JPEG this run was given, for screenshot workflows. Lives in app-internal
     * storage, is excluded from backup, and is deleted with the run — see [ScreenshotRetention].
     */
    val screenshotPath: String? = null,
) {
    companion object {
        /** History is a convenience, not an archive; keep rows small. */
        const val PREVIEW_LIMIT = 2000

        /**
         * How many runs are kept. Oldest go first, with their screenshots.
         *
         * Without a ceiling this table is the one thing in ArcX that grows forever: at four
         * kilobytes of preview text a row it reached 20 MB in testing, and every screen that
         * read it got slower in step. A convenience that degrades the app the more you use it
         * is not a convenience.
         */
        const val HISTORY_LIMIT = 1000
    }
}

/**
 * A run as a list shows it — everything except the two fields that carry the payload.
 *
 * Those are most of a row's bytes and no list draws them, so loading them to render a list was
 * pulling megabytes through the mapper to display a name and a duration. The detail sheet asks
 * for the full [RunRecord] by id when it actually needs one.
 */
data class RunSummary(
    val id: String,
    val workflowId: String,
    val workflowName: String,
    val workflowIcon: String,
    val startedAt: Long,
    val durationMs: Long,
    val providerLabel: String,
    val model: String,
    val status: RunStatus,
    /** First line only — the list has room for a reason, not a stack trace. */
    val error: String?,
    val hasScreenshot: Boolean,
)

/** Just enough of a run to count it and time it. For aggregates over a bounded window. */
data class RunOutcome(
    val durationMs: Long,
    val status: RunStatus,
)
