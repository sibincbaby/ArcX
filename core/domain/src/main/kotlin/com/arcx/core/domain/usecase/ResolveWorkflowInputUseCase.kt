package com.arcx.core.domain.usecase

import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.capture.ClipboardAccess
import com.arcx.core.domain.capture.ScreenContextProvider
import com.arcx.core.model.InputSource
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowInput
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * What a run acts on: the input text, and the values its placeholders expand to.
 *
 * **An implementation detail of [ExecuteWorkflowUseCase], which is the only thing that may hold
 * one.** It was split out to keep that class readable, not to be reused. The fallback order below
 * and the rule about which placeholders get resolved are part of the one execution path; a second
 * caller resolving input for itself is a second path, with its own quiet drift. Do not inject this
 * anywhere else.
 */
class ResolveWorkflowInputUseCase @Inject constructor(
    private val screen: ScreenContextProvider,
    private val clipboard: ClipboardAccess,
    private val time: TimeSource,
) {

    /**
     * A shortcut, widget, tile or bubble launch carries only a workflow id — no text. Falling
     * back to the clipboard is what makes one-tap launching useful at all: copy something,
     * then tap. Without it every such launch would send an empty prompt to the provider.
     */
    suspend fun text(workflow: Workflow, input: WorkflowInput): String {
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

    /**
     * Resolves exactly the placeholders [used] names, and nothing else.
     *
     * Two of these are not free — the clipboard is an IPC and screen text is an accessibility
     * snapshot — and they used to be fetched for every run whether the prompt mentioned them or
     * not, on the path between the user's tap and the first token. A name this does not know
     * resolves to an empty string, which is what [PromptTemplate.render] does with an absent
     * key anyway, so an unknown placeholder behaves exactly as before.
     */
    suspend fun variables(
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

    private fun currentApp(input: WorkflowInput): String =
        input.sourcePackage ?: try {
            screen.currentPackage().orEmpty()
        } catch (e: Exception) {
            ""
        }

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
