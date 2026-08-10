package com.arcx.core.domain.usecase

import com.arcx.core.common.prompt.PromptTemplate
import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.domain.capture.ClipboardAccess
import com.arcx.core.domain.capture.ScreenContextProvider
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.AiChunk
import com.arcx.core.model.AiError
import com.arcx.core.model.AiRequest
import com.arcx.core.model.InputSource
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunStatus
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

/**
 * The one execution path. Share sheet, text selection, bubble, widget and shortcut all end up
 * here, so provider resolution, variable expansion, history and error mapping only exist once.
 */
class ExecuteWorkflowUseCase @Inject constructor(
    private val workflows: WorkflowRepository,
    private val providers: ProviderRepository,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
    private val registry: AiProviderRegistry,
    private val screen: ScreenContextProvider,
    private val clipboard: ClipboardAccess,
    private val time: TimeSource,
) {

    operator fun invoke(workflow: Workflow, input: WorkflowInput): Flow<ExecutionState> = flow {
        val startedAt = time.nowMillis()
        emit(ExecutionState.Preparing)

        val config = providers.resolve(workflow.providerId)
        if (config == null) {
            fail(workflow, null, "", startedAt, input, AiError.NoProvider())
            return@flow
        }

        val model = workflow.model ?: config.defaultModel
        val apiKey = providers.apiKey(config.id)
        if (!config.type.isLocal && apiKey.isNullOrBlank()) {
            fail(workflow, config, model, startedAt, input, AiError.MissingKey(config.label))
            return@flow
        }

        val provider = registry[config.type]
        if (provider == null) {
            fail(workflow, config, model, startedAt, input, AiError.NoProvider())
            return@flow
        }

        val text = resolveText(workflow, input)
        if (text.isBlank() && input.attachments.isEmpty() && workflow.needsText) {
            fail(workflow, config, model, startedAt, input, AiError.NoInput())
            return@flow
        }

        val vars = variables(text, input)
        val request = AiRequest(
            model = model,
            userPrompt = PromptTemplate.render(workflow.prompt, vars),
            systemPrompt = workflow.systemPrompt?.let { PromptTemplate.render(it, vars) },
            attachments = input.attachments,
            temperature = workflow.temperature,
            maxTokens = workflow.maxTokens,
        )

        val answer = StringBuilder()
        var failure: AiError? = null
        // `catch` only sees upstream failures, so a cancellation or a downstream error is left
        // to propagate instead of being logged as a failed run.
        provider.generate(request, config, apiKey)
            .catch { failure = it as? AiError ?: AiError.Unknown(it) }
            .collect { chunk ->
                if (chunk is AiChunk.Text) {
                    answer.append(chunk.delta)
                    emit(ExecutionState.Streaming(answer.toString()))
                }
            }

        val error = failure
        if (error != null) {
            fail(workflow, config, model, startedAt, input, error, answer.toString())
        } else {
            val text = answer.toString()
            record(workflow, config, model, startedAt, input, RunStatus.SUCCESS, text, null)
            emit(ExecutionState.Success(text, time.nowMillis() - startedAt))
        }
    }

    /** Entry points launched from a shortcut, widget or tile only carry a workflow id. */
    fun byId(workflowId: String, input: WorkflowInput): Flow<ExecutionState> = flow {
        val workflow = workflows.get(workflowId)
        if (workflow == null) {
            emit(ExecutionState.Preparing)
            emit(ExecutionState.Failed(AiError.Unknown(IllegalStateException("Workflow $workflowId no longer exists"))))
        } else {
            emitAll(invoke(workflow, input))
        }
    }

    /**
     * A shortcut, widget, tile or bubble launch carries only a workflow id — no text. Falling
     * back to the clipboard is what makes one-tap launching useful at all: copy something,
     * then tap. Without it every such launch would send an empty prompt to the provider.
     */
    private suspend fun resolveText(workflow: Workflow, input: WorkflowInput): String {
        input.text?.takeIf { it.isNotBlank() }?.let { return it }
        return when (workflow.input) {
            InputSource.SCREEN_TEXT -> readScreenText()
            InputSource.SELECTED_TEXT,
            InputSource.SHARE_INTENT,
            InputSource.CLIPBOARD,
            InputSource.MANUAL,
            -> readClipboard()

            else -> ""
        }
    }

    /** Sources that are meaningless without text; attachment-based ones are not. */
    private val Workflow.needsText: Boolean
        get() = input in TEXT_SOURCES

    private suspend fun variables(text: String, input: WorkflowInput): Map<String, String> {
        val moment = Instant.ofEpochMilli(time.nowMillis()).atZone(ZoneId.systemDefault())
        return buildMap {
            put(PromptVariable.SELECTED_TEXT.name, text)
            put(PromptVariable.INPUT.name, text)
            put(PromptVariable.SHARE_TEXT.name, text)
            put(PromptVariable.CLIPBOARD.name, readClipboard())
            put(PromptVariable.SCREEN_TEXT.name, readScreenText())
            put(PromptVariable.CURRENT_APP.name, currentApp(input))
            put(PromptVariable.TODAY.name, moment.format(DateTimeFormatter.ISO_LOCAL_DATE))
            put(PromptVariable.NOW.name, moment.format(TIME_FORMAT))
            put(PromptVariable.SHARE_SUBJECT.name, input.shareSubject.orEmpty())
        }
    }

    /** The accessibility service is optional and may never be granted; missing it is not an error. */
    private suspend fun readScreenText(): String = try {
        if (screen.isAvailable()) screen.screenText().orEmpty() else ""
    } catch (e: Exception) {
        ""
    }

    private fun readClipboard(): String = try {
        clipboard.read().orEmpty()
    } catch (e: Exception) {
        ""
    }

    private fun currentApp(input: WorkflowInput): String =
        input.sourcePackage ?: try {
            screen.currentPackage().orEmpty()
        } catch (e: Exception) {
            ""
        }

    private suspend fun FlowCollector<ExecutionState>.fail(
        workflow: Workflow,
        config: ProviderConfig?,
        model: String,
        startedAt: Long,
        input: WorkflowInput,
        error: AiError,
        partial: String = "",
    ) {
        record(workflow, config, model, startedAt, input, RunStatus.FAILED, partial.ifEmpty { null }, error)
        emit(ExecutionState.Failed(error))
    }

    private suspend fun record(
        workflow: Workflow,
        config: ProviderConfig?,
        model: String,
        startedAt: Long,
        input: WorkflowInput,
        status: RunStatus,
        output: String?,
        error: AiError?,
    ) {
        if (!settings.current().historyEnabled) return
        history.record(
            RunRecord(
                id = UUID.randomUUID().toString(),
                workflowId = workflow.id,
                workflowName = workflow.name,
                workflowIcon = workflow.icon,
                startedAt = startedAt,
                durationMs = time.nowMillis() - startedAt,
                providerLabel = config?.label.orEmpty(),
                model = model,
                status = status,
                // Previews only: the key never comes near this object and attachment bytes stay out of it.
                inputPreview = input.text.orEmpty().preview(),
                outputPreview = output?.preview(),
                error = error?.message,
            ),
        )
    }

    private fun String.preview(): String = take(RunRecord.PREVIEW_LIMIT)

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}

/** Input sources that carry text; the rest are attachment- or context-based. */
private val TEXT_SOURCES = setOf(
    InputSource.SELECTED_TEXT,
    InputSource.SHARE_INTENT,
    InputSource.CLIPBOARD,
    InputSource.SCREEN_TEXT,
    InputSource.MANUAL,
)
