package com.arcx.core.domain.usecase

import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.Workflow
import java.util.UUID
import javax.inject.Inject

/** Saves a new or edited workflow, keeping the timestamps the builder never sets itself. */
class SaveWorkflowUseCase @Inject constructor(
    private val workflows: WorkflowRepository,
    private val time: TimeSource,
) {
    suspend operator fun invoke(workflow: Workflow): Workflow {
        val now = time.nowMillis()
        val saved = workflow.copy(
            id = workflow.id.ifBlank { UUID.randomUUID().toString() },
            createdAt = if (workflow.createdAt == 0L) now else workflow.createdAt,
            updatedAt = now,
        )
        workflows.upsert(saved)
        return saved
    }
}

class DeleteWorkflowUseCase @Inject constructor(
    private val workflows: WorkflowRepository,
) {
    suspend operator fun invoke(id: String) = workflows.delete(id)
}

class ToggleFavoriteUseCase @Inject constructor(
    private val workflows: WorkflowRepository,
) {
    suspend operator fun invoke(workflow: Workflow) =
        workflows.setFavorite(workflow.id, !workflow.isFavorite)
}

class TogglePinnedUseCase @Inject constructor(
    private val workflows: WorkflowRepository,
) {
    suspend operator fun invoke(workflow: Workflow) =
        workflows.setPinned(workflow.id, !workflow.isPinned)
}

/**
 * Switches a workflow on or off everywhere it is offered to be run.
 *
 * Deliberately shaped like the two above rather than like a delete: it writes one column, and the
 * workflow, its runs and its screenshots are all still there afterwards.
 */
class ToggleEnabledUseCase @Inject constructor(
    private val workflows: WorkflowRepository,
) {
    suspend operator fun invoke(workflow: Workflow) =
        workflows.setEnabled(workflow.id, !workflow.enabled)
}

/**
 * Built-ins are read-only, so "edit" on one really means "clone it and edit the clone".
 * Returns null when the source is gone.
 */
class DuplicateWorkflowUseCase @Inject constructor(
    private val workflows: WorkflowRepository,
    private val save: SaveWorkflowUseCase,
) {
    suspend operator fun invoke(id: String): Workflow? {
        val source = workflows.get(id) ?: return null
        return save(
            source.copy(
                id = "",
                name = "${source.name} Copy",
                isBuiltIn = false,
                isPinned = false,
                isFavorite = false,
                // A copy the user just asked for arrives switched on even when its source is off —
                // the alternative is a new workflow that appears in no picker and never explains why.
                enabled = true,
                createdAt = 0L,
                updatedAt = 0L,
            ),
        )
    }
}
