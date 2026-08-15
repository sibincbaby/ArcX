package com.arcx.core.domain.usecase

import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.capture.ScreenshotStore
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.model.AiError
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunStatus
import com.arcx.core.model.Workflow
import java.util.UUID
import javax.inject.Inject

/**
 * Writes a finished run — succeeded or failed — to History, together with the image it acted on.
 *
 * **An implementation detail of [ExecuteWorkflowUseCase], which is the only thing that may hold
 * one.** It was split out to keep that class readable, not to be reused. History rules that hold
 * only because every run goes through one writer — the row and its screenshot being written
 * together, failed runs being recorded too, the settings check — stop holding the moment something
 * else records a run of its own. Do not inject this anywhere else.
 */
class RecordRunUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val screenshots: ScreenshotStore,
    private val settings: SettingsRepository,
    private val time: TimeSource,
) {

    suspend operator fun invoke(
        workflow: Workflow,
        config: ProviderConfig?,
        model: String,
        startedAt: Long,
        inputText: String,
        status: RunStatus,
        output: String?,
        error: AiError?,
        screenshot: ByteArray?,
        recordHistory: Boolean,
    ) {
        if (!recordHistory || !settings.current().historyEnabled) return
        val id = UUID.randomUUID().toString()
        // Written only once there is a row to point at it, and under that row's id, so clearing
        // history reaches every image. A file saved for an unrecorded run would be invisible to
        // History and to "delete all local data" — a leak with no UI left to remove it. Failed
        // runs keep theirs: seeing what a run acted on is most of why it is worth storing.
        val screenshotPath = screenshot?.let { screenshots.save(id, it) }
        history.record(
            RunRecord(
                id = id,
                workflowId = workflow.id,
                workflowName = workflow.name,
                workflowIcon = workflow.icon,
                startedAt = startedAt,
                durationMs = time.nowMillis() - startedAt,
                providerLabel = config?.label.orEmpty(),
                model = model,
                status = status,
                // The text actually sent to the provider, not WorkflowInput.text — a bubble,
                // shortcut or tile launch carries no text of its own and resolves it from the
                // clipboard or the screen, so recording the raw input left history blank.
                // Previews only: the key never comes near this object, nor do attachment bytes.
                // An image-only run has no text to preview, which is fine and expected.
                inputPreview = inputText.preview(),
                outputPreview = output?.preview(),
                error = error?.message,
                screenshotPath = screenshotPath,
            ),
        )
    }

    private fun String.preview(): String = take(RunRecord.PREVIEW_LIMIT)
}
