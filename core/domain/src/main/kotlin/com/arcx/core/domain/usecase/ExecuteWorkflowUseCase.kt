package com.arcx.core.domain.usecase

import com.arcx.core.common.prompt.PromptTemplate
import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.domain.capture.ClipboardAccess
import com.arcx.core.domain.capture.ScreenContextProvider
import com.arcx.core.domain.capture.ScreenshotStore
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.AiChunk
import com.arcx.core.model.AiError
import com.arcx.core.model.AiRequest
import com.arcx.core.model.Attachment
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
    private val screenshots: ScreenshotStore,
    private val clipboard: ClipboardAccess,
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

        val inputText = resolveText(workflow, input)

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
        val vars = variables(used.distinct(), inputText, input)
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
            record(
                workflow, config, model, startedAt, inputText,
                RunStatus.SUCCESS, text, null, storedImage, recordHistory,
            )
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

            // Launched from the bubble, a shortcut or a tile there is no selection to read, so
            // the content has to come from somewhere. The clipboard first, because copying is
            // deliberate and beats guessing; then the screen, because "act on what I am looking
            // at" is the entire point of tapping the bubble while reading something. Without
            // this the bubble refuses to work on the article filling the screen behind it.
            InputSource.SELECTED_TEXT,
            InputSource.SHARE_INTENT,
            -> readClipboard().ifBlank { readScreenText() }

            // These two name their source, so silently substituting another would be a lie.
            InputSource.CLIPBOARD, InputSource.MANUAL -> readClipboard()

            else -> ""
        }
    }

    /** Sources that are meaningless without text; attachment-based ones are not. */
    private val Workflow.needsText: Boolean
        get() = input in TEXT_SOURCES

    /**
     * Resolves exactly the placeholders [used] names, and nothing else.
     *
     * Two of these are not free — the clipboard is an IPC and screen text is an accessibility
     * snapshot — and they used to be fetched for every run whether the prompt mentioned them or
     * not, on the path between the user's tap and the first token. A name this does not know
     * resolves to an empty string, which is what [PromptTemplate.render] does with an absent
     * key anyway, so an unknown placeholder behaves exactly as before.
     */
    private suspend fun variables(
        used: List<String>,
        text: String,
        input: WorkflowInput,
    ): Map<String, String> {
        if (used.isEmpty()) return emptyMap()
        val moment = Instant.ofEpochMilli(time.nowMillis()).atZone(ZoneId.systemDefault())
        return used.associateWith { name ->
            when (name) {
                PromptVariable.SELECTED_TEXT.name,
                PromptVariable.INPUT.name,
                PromptVariable.SHARE_TEXT.name,
                -> text

                PromptVariable.CLIPBOARD.name -> readClipboard()
                PromptVariable.SCREEN_TEXT.name -> readScreenText()
                PromptVariable.CURRENT_APP.name -> currentApp(input)
                PromptVariable.TODAY.name -> moment.format(DateTimeFormatter.ISO_LOCAL_DATE)
                PromptVariable.NOW.name -> moment.format(TIME_FORMAT)
                PromptVariable.SHARE_SUBJECT.name -> input.shareSubject.orEmpty()
                else -> ""
            }
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

    /**
     * Capture is permission-gated and can be revoked between runs, so an unavailable or empty
     * frame is an ordinary outcome the caller turns into [AiError.NoScreenshot], not a crash.
     */
    private suspend fun readScreenshot(): ByteArray? = try {
        if (screen.canScreenshot()) screen.screenshot()?.takeIf { it.isNotEmpty() } else null
    } catch (e: Exception) {
        null
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
        inputText: String,
        error: AiError,
        partial: String = "",
        screenshot: ByteArray? = null,
        recordHistory: Boolean = true,
    ) {
        record(
            workflow, config, model, startedAt, inputText,
            RunStatus.FAILED, partial.ifEmpty { null }, error, screenshot, recordHistory,
        )
        emit(ExecutionState.Failed(error))
    }

    private suspend fun record(
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

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
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
