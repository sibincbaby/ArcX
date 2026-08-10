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
) {
    companion object {
        /** History is a convenience, not an archive; keep rows small. */
        const val PREVIEW_LIMIT = 2000
    }
}
