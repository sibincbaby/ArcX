package com.arcx.core.domain.execution

import com.arcx.core.model.AiError

/** Progress of a single workflow run, as consumed by the result sheet. */
sealed interface ExecutionState {
    /** Resolving provider, key and prompt variables. */
    data object Preparing : ExecutionState

    /** [text] is the full answer accumulated so far, not the latest delta. */
    data class Streaming(val text: String) : ExecutionState

    data class Success(val text: String, val durationMs: Long) : ExecutionState

    data class Failed(val error: AiError) : ExecutionState
}
