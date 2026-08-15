package com.arcx.core.domain.usecase

import com.arcx.core.common.prompt.PromptTemplate
import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.domain.capture.ScreenContextProvider
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.model.AiChunk
import com.arcx.core.model.AiError
import com.arcx.core.model.AiRequest
import com.arcx.core.model.Attachment
import com.arcx.core.model.InputSource
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.RunStatus
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import javax.inject.Inject

/**
 * The one execution path. Share sheet, text selection, bubble, widget and shortcut all end up
 * here, so provider resolution, variable expansion, history and error mapping only exist once.
 *
 * [ResolveWorkflowInputUseCase] and [RecordRunUseCase] are two halves of this class that were
 * lifted out to keep it readable; they are its implementation details and are documented as such.
 * Nothing else may hold either of them — an entry point that resolved its own input or wrote its
 * own history row would be the second path this class exists to prevent.
 */
class ExecuteWorkflowUseCase @Inject constructor(
    private val providers: ProviderRepository,
    private val registry: AiProviderRegistry,
    private val screen: ScreenContextProvider,
    private val resolveInput: ResolveWorkflowInputUseCase,
    private val recordRun: RecordRunUseCase,
    private val time: TimeSource,
) {

    /**
     * [recordHistory] false is for the builder's "try it" button, which fires a workflow that
     * has not been saved and may never be. Recording it would leave rows in History pointing at
     * a workflow id that does not exist. Everything else about the path is identical — this is
     * still the only place a workflow runs.
     */
    operator fun invoke(
        workflow: Workflow,
        input: WorkflowInput,
        recordHistory: Boolean = true,
    ): Flow<ExecutionState> = flow {
        val startedAt = time.nowMillis()
        emit(ExecutionState.Preparing)

        val config = providers.resolve(workflow.providerId)
        if (config == null) {
            fail(
                workflow, null, "", startedAt, input.text.orEmpty(),
                AiError.NoProvider(), recordHistory = recordHistory,
            )
            return@flow
        }

        val model = workflow.model ?: config.defaultModel
        val apiKey = providers.apiKey(config.id)
        if (!config.type.isLocal && apiKey.isNullOrBlank()) {
            fail(
                workflow, config, model, startedAt, input.text.orEmpty(),
                AiError.MissingKey(config.label), recordHistory = recordHistory,
            )
            return@flow
        }

        val provider = registry[config.type]
        if (provider == null) {
            fail(
                workflow, config, model, startedAt, input.text.orEmpty(),
                AiError.NoProvider(), recordHistory = recordHistory,
            )
            return@flow
        }

        val inputText = resolveInput.text(workflow, input)

        // A screenshot workflow's input is the picture. Only a launch that brought no image of its
        // own asks for one — a shared photo is what the user chose to act on, and overriding it
        // with the screen would send something they never meant to send.
        val screenshot = if (workflow.input == InputSource.SCREENSHOT && input.attachments.isEmpty()) {
            readScreenshot()
        } else {
            null
        }
        val attachments = input.attachments + listOfNotNull(
            screenshot?.let { Attachment(mimeType = JPEG, bytes = it) },
        )

        // What History stores is whatever picture of the screen was actually sent, whoever took it.
        // The runner now grabs the frame itself — it is the only layer that can blank its own
        // window first — so keying this off the local capture above silently stopped recording the
        // image for every entry point except the bubble.
        val storedImage = screenshot ?: attachments
            .firstOrNull { workflow.input == InputSource.SCREENSHOT && it.mimeType == JPEG }
            ?.bytes

        // A screenshot run records no input text, and that is correct: the picture is the input,
        // and it is stored. History once kept the screen's text alongside it, which meant the one
        // thing ArcX stored that was never sent to the provider was a full text dump of the
        // screen — a privacy cost with no feature behind it, since History has no search.
        //
        // Before the provider, deliberately: an imageless vision prompt costs a paid call to come
        // back with nothing useful. [needsText] never covers this — SCREENSHOT is not a text source.
        if (workflow.input == InputSource.SCREENSHOT && attachments.isEmpty()) {
            fail(
                workflow, config, model, startedAt, inputText,
                AiError.NoScreenshot(captureAvailable = screen.canScreenshot()),
                recordHistory = recordHistory,
            )
            return@flow
        }

        if (inputText.isBlank() && attachments.isEmpty() && workflow.needsText) {
            fail(
                workflow, config, model, startedAt, inputText,
                AiError.NoInput(), recordHistory = recordHistory,
            )
            return@flow
        }

        // Only what the prompt actually asks for. Resolving the full set meant every run —
        // including a rewrite whose prompt is just {{input}} — walked the accessibility tree
        // for {{screen_text}} and read the clipboard, before a single byte was sent.
        val used = PromptTemplate.variablesIn(workflow.prompt) +
            PromptTemplate.variablesIn(workflow.systemPrompt.orEmpty())
        val vars = resolveInput.variables(used.distinct(), inputText, input)
        val request = AiRequest(
            model = model,
            userPrompt = PromptTemplate.render(workflow.prompt, vars),
            systemPrompt = workflow.systemPrompt?.let { PromptTemplate.render(it, vars) },
            attachments = attachments,
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
            fail(
                workflow, config, model, startedAt, inputText, error,
                answer.toString(), storedImage, recordHistory,
            )
        } else {
            val text = answer.toString()
            recordRun(
                workflow, config, model, startedAt, inputText,
                RunStatus.SUCCESS, text, null, storedImage, recordHistory,
            )
            emit(ExecutionState.Success(text, time.nowMillis() - startedAt))
        }
    }

    /** Sources that are meaningless without text; attachment-based ones are not. */
    private val Workflow.needsText: Boolean
        get() = input in TEXT_SOURCES

    /**
     * Capture is permission-gated and can be revoked between runs, so an unavailable or empty
     * frame is an ordinary outcome the caller turns into [AiError.NoScreenshot], not a crash.
     */
    private suspend fun readScreenshot(): ByteArray? = try {
        if (screen.canScreenshot()) screen.screenshot()?.takeIf { it.isNotEmpty() } else null
    } catch (e: Exception) {
        null
    }

    private suspend fun FlowCollector<ExecutionState>.fail(
        workflow: Workflow,
        config: ProviderConfig?,
        model: String,
        startedAt: Long,
        inputText: String,
        error: AiError,
        partial: String = "",
        screenshot: ByteArray? = null,
        recordHistory: Boolean = true,
    ) {
        recordRun(
            workflow, config, model, startedAt, inputText,
            RunStatus.FAILED, partial.ifEmpty { null }, error, screenshot, recordHistory,
        )
        emit(ExecutionState.Failed(error))
    }

    private companion object {
        const val JPEG = "image/jpeg"
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
