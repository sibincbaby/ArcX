package com.arcx.feature.workflow

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.designsystem.component.DEFAULT_WORKFLOW_ICON
import com.arcx.core.domain.ai.AiProviderRegistry
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.domain.repository.ProviderRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.ExecuteWorkflowUseCase
import com.arcx.core.domain.usecase.SaveWorkflowUseCase
import com.arcx.core.model.InputSource
import com.arcx.core.model.ModelInfo
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory
import com.arcx.core.model.WorkflowInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the model dropdown can currently offer. */
internal sealed interface ModelListState {
    /** Nothing asked for yet — the list is only fetched when the field is opened. */
    data object Idle : ModelListState
    data object Loading : ModelListState
    data class Loaded(val models: List<ModelInfo>) : ModelListState

    /**
     * No key, no network, or a provider that does not publish a catalogue. The field degrades
     * to free text: not knowing the model list must never stop someone saving a workflow.
     */
    data class Unavailable(val reason: String) : ModelListState
}

/** The builder's "try it" panel. A real run, on the unsaved form, recorded nowhere. */
internal sealed interface TestRunState {
    data object Idle : TestRunState
    data object Running : TestRunState
    data class Done(val text: String, val durationMs: Long, val model: String) : TestRunState
    data class Failed(val message: String) : TestRunState
}

internal data class WorkflowEditorState(
    val loading: Boolean = true,
    val name: String = "",
    val icon: String = DEFAULT_WORKFLOW_ICON,
    val category: WorkflowCategory = WorkflowCategory.CUSTOM,
    val input: InputSource = InputSource.SELECTED_TEXT,
    val prompt: TextFieldValue = TextFieldValue(""),
    val systemPrompt: String = "",
    val providerId: String? = null,
    val model: String = "",
    val output: OutputTarget = OutputTarget.BOTTOM_SHEET,
    val temperature: Float? = null,
    val maxTokens: String = "",
    val providers: List<ProviderConfig> = emptyList(),
    val models: ModelListState = ModelListState.Idle,
    /** Text the "try it" panel runs against. Not part of the workflow, so never saved. */
    val sampleText: String = "",
    val test: TestRunState = TestRunState.Idle,
    val nameError: Boolean = false,
    val promptError: Boolean = false,
    val saving: Boolean = false,
    /** True when the workflow being edited ships with the app, so saving forks a copy. */
    val forksBuiltIn: Boolean = false,
    val isNew: Boolean = true,
)

/**
 * Display name of the chosen model when the catalogue says outright that it cannot accept
 * images, and the workflow is about to send it one. Null whenever we do not actually know —
 * an unfetched list, a provider without a catalogue, or the provider's own default model —
 * because a warning under a model that works fine is worse than no warning at all.
 */
internal val WorkflowEditorState.modelWithoutVision: String?
    get() {
        if (input != InputSource.SCREENSHOT) return null
        val loaded = models as? ModelListState.Loaded ?: return null
        val chosen = loaded.models.firstOrNull { it.id == model } ?: return null
        return if (chosen.supportsVision) null else chosen.displayName
    }

/**
 * Form fields only. Dirty tracking compares this rather than the whole UI state, which also
 * carries the provider list, the model list and validation flags — none of which the user
 * changed.
 */
private data class FormSnapshot(
    val name: String,
    val icon: String,
    val category: WorkflowCategory,
    val input: InputSource,
    val prompt: String,
    val systemPrompt: String,
    val providerId: String?,
    val model: String,
    val output: OutputTarget,
    val temperature: Float?,
    val maxTokens: String,
)

private fun WorkflowEditorState.snapshot() = FormSnapshot(
    name, icon, category, input, prompt.text, systemPrompt,
    providerId, model, output, temperature, maxTokens,
)

@HiltViewModel
internal class WorkflowEditorViewModel @Inject constructor(
    private val workflows: WorkflowRepository,
    private val providers: ProviderRepository,
    private val registry: AiProviderRegistry,
    private val saveWorkflow: SaveWorkflowUseCase,
    private val execute: ExecuteWorkflowUseCase,
) : ViewModel() {

    /**
     * Compose state rather than a StateFlow. The prompt field is a [TextFieldValue] whose
     * selection has to survive the round trip from keystroke to recomposition intact, and a
     * flow hop is exactly where a cursor position gets lost.
     */
    var state by mutableStateOf(WorkflowEditorState())
        private set

    private var original: Workflow? = null
    private var baseline = WorkflowEditorState().snapshot()
    private var started = false
    private var modelJob: Job? = null
    private var testJob: Job? = null

    private val savedEvents = Channel<Unit>(Channel.CONFLATED)
    val saved: Flow<Unit> = savedEvents.receiveAsFlow()

    val isDirty: Boolean get() = state.snapshot() != baseline

    /** Idempotent: the route calls this from a `LaunchedEffect` that may run again on config change. */
    fun start(workflowId: String?) {
        if (started) return
        started = true

        viewModelScope.launch {
            val existing = workflowId?.let { workflows.get(it) }
            original = existing
            // The provider list may already have arrived from the collector below; carry it over.
            state = existing?.toEditorState()?.copy(providers = state.providers)
                ?: state.copy(loading = false)
            baseline = state.snapshot()
        }
        viewModelScope.launch {
            providers.observeAll().collect { state = state.copy(providers = it) }
        }
    }

    fun setName(value: String) {
        state = state.copy(name = value, nameError = false)
    }

    fun setIcon(value: String) {
        state = state.copy(icon = value)
    }

    fun setCategory(value: WorkflowCategory) {
        state = state.copy(category = value)
    }

    fun setInput(value: InputSource) {
        state = state.copy(input = value)
    }

    fun setPrompt(value: TextFieldValue) {
        state = state.copy(prompt = value, promptError = false)
    }

    fun setSystemPrompt(value: String) {
        state = state.copy(systemPrompt = value)
    }

    fun setOutput(value: OutputTarget) {
        state = state.copy(output = value)
    }

    fun setTemperature(value: Float?) {
        state = state.copy(temperature = value)
    }

    fun setMaxTokens(value: String) {
        state = state.copy(maxTokens = value.filter { it.isDigit() })
    }

    /** Changing provider invalidates both the chosen model and the catalogue it came from. */
    fun setProvider(providerId: String?) {
        modelJob?.cancel()
        state = state.copy(providerId = providerId, model = "", models = ModelListState.Idle)
    }

    fun setModel(value: String) {
        state = state.copy(model = value)
    }

    /**
     * Inserts `{{name}}` where the cursor is, replacing the selection if there is one. Appending
     * to the end instead would quietly ruin any prompt whose variable belongs mid-sentence.
     */
    fun insertVariable(variable: PromptVariable) {
        val current = state.prompt
        val start = current.selection.min
        val end = current.selection.max
        val token = variable.token
        val text = current.text.replaceRange(start, end, token)
        state = state.copy(
            prompt = TextFieldValue(text = text, selection = TextRange(start + token.length)),
            promptError = false,
        )
    }

    fun applyTemplate(template: PromptTemplateOption) {
        state = state.copy(
            prompt = TextFieldValue(
                text = template.prompt,
                selection = TextRange(template.prompt.length),
            ),
            systemPrompt = template.systemPrompt ?: state.systemPrompt,
            promptError = false,
        )
    }

    /** Fetches the catalogue the first time the model field is opened, and not before. */
    fun loadModels() {
        if (state.models != ModelListState.Idle) return
        state = state.copy(models = ModelListState.Loading)
        modelJob = viewModelScope.launch {
            val result = runCatching {
                val config = providers.resolve(state.providerId)
                    ?: error("Connect a provider in Settings first")
                val provider = registry[config.type]
                    ?: error("${config.label} is not supported in this build yet")
                provider.listModels(config, providers.apiKey(config.id))
            }
            state = state.copy(
                models = result.fold(
                    onSuccess = { models ->
                        if (models.isEmpty()) {
                            ModelListState.Unavailable("This provider does not publish a model list")
                        } else {
                            ModelListState.Loaded(models)
                        }
                    },
                    onFailure = {
                        ModelListState.Unavailable(it.message ?: "Could not reach the provider")
                    },
                ),
            )
        }
    }

    fun setSampleText(value: String) {
        state = state.copy(sampleText = value, test = TestRunState.Idle)
    }

    /**
     * Runs the form as it stands, against the sample text, through the same use case every
     * entry point uses — so what comes back here is what will come back on the phone, provider
     * errors and all. Nothing is saved and nothing is recorded in History.
     */
    fun runTest() {
        val prompt = state.prompt.text.trim()
        if (prompt.isBlank()) {
            state = state.copy(promptError = true)
            return
        }
        testJob?.cancel()
        state = state.copy(test = TestRunState.Running)

        // MANUAL because the sample text is typed in here; the real input source would send the
        // test looking at a selection or the screen, neither of which exists in the builder.
        val draft = state.toWorkflow(null).copy(input = InputSource.MANUAL)
        testJob = viewModelScope.launch {
            var answer = ""
            execute(draft, WorkflowInput(text = state.sampleText), recordHistory = false)
                .collect { execution ->
                    when (execution) {
                        is ExecutionState.Streaming -> answer = execution.text
                        is ExecutionState.Success -> state = state.copy(
                            test = TestRunState.Done(
                                text = execution.text,
                                durationMs = execution.durationMs,
                                model = draft.model ?: "provider default",
                            ),
                        )

                        is ExecutionState.Failed -> state = state.copy(
                            test = TestRunState.Failed(
                                execution.error.message ?: "That did not come back.",
                            ),
                        )

                        else -> Unit
                    }
                }
            if (state.test is TestRunState.Running) {
                // Cancelled or ended without a terminal state; keep whatever streamed in.
                state = state.copy(test = TestRunState.Done(answer, 0L, draft.model.orEmpty()))
            }
        }
    }

    fun save() {
        val name = state.name.trim()
        val prompt = state.prompt.text.trim()
        if (name.isBlank() || prompt.isBlank()) {
            state = state.copy(nameError = name.isBlank(), promptError = prompt.isBlank())
            return
        }
        if (state.saving) return

        state = state.copy(saving = true)
        viewModelScope.launch {
            runCatching { saveWorkflow(state.toWorkflow(original)) }
            state = state.copy(saving = false)
            baseline = state.snapshot()
            savedEvents.send(Unit)
        }
    }
}

private fun Workflow.toEditorState() = WorkflowEditorState(
    loading = false,
    name = name,
    icon = icon,
    category = category,
    input = input,
    prompt = TextFieldValue(text = prompt, selection = TextRange(prompt.length)),
    systemPrompt = systemPrompt.orEmpty(),
    providerId = providerId,
    model = model.orEmpty(),
    output = output,
    temperature = temperature,
    maxTokens = maxTokens?.toString().orEmpty(),
    forksBuiltIn = isBuiltIn,
    isNew = false,
)

/**
 * A blank id tells [SaveWorkflowUseCase] to mint one, which is also how a built-in gets forked:
 * the user keeps their edits, the shipped original stays intact underneath.
 */
private fun WorkflowEditorState.toWorkflow(original: Workflow?): Workflow {
    val forking = original?.isBuiltIn == true
    return Workflow(
        id = if (forking) "" else original?.id.orEmpty(),
        name = name.trim(),
        icon = icon.trim().ifBlank { DEFAULT_WORKFLOW_ICON },
        category = category,
        input = input,
        prompt = prompt.text.trim(),
        systemPrompt = systemPrompt.trim().ifBlank { null },
        providerId = providerId,
        model = model.trim().ifBlank { null },
        output = output,
        temperature = temperature,
        maxTokens = maxTokens.trim().toIntOrNull(),
        isPinned = if (forking) false else original?.isPinned == true,
        isFavorite = if (forking) false else original?.isFavorite == true,
        isBuiltIn = false,
        sortOrder = original?.sortOrder ?: 0,
        createdAt = if (forking) 0L else original?.createdAt ?: 0L,
        updatedAt = 0L,
    )
}
