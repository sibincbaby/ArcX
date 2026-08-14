package com.arcx.feature.runner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.ExecuteWorkflowUseCase
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowInput
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** One group of rows in the picker. */
data class PickerSection(
    /** Null while searching: hits are one flat list, and a header would only add noise. */
    val title: String?,
    val workflows: List<Workflow>,
)

data class RunnerUiState(
    val query: String = "",
    val sections: List<PickerSection> = emptyList(),
    /** False until the catalog's first emission, so the picker never flashes its empty state. */
    val catalogLoaded: Boolean = false,
    /**
     * Mirrors UserSettings.compactPicker. Null until the preference has actually been read — the
     * host picks a bottom sheet or a floating card from this, and guessing would show one and swap
     * it for the other a frame later.
     */
    val compactPicker: Boolean? = null,
    /** Null while the picker is up; non-null once a workflow is running or has finished. */
    val workflow: Workflow? = null,
    /**
     * A run by id is still looking its workflow up. Without this the picker would flash for
     * one frame on the fastest path in the product, which is exactly where it is most visible.
     */
    val resolving: Boolean = false,
    val execution: ExecutionState = ExecutionState.Preparing,
    /** The user pressed Stop. The partial answer is all there is, and that is not a failure. */
    val stopped: Boolean = false,
    /** One-shot latch so a configuration change cannot replay the output side effect. */
    val outputApplied: Boolean = false,
)

/** The two workflow queries the picker needs, kept together so they arrive as one emission. */
private data class Catalog(
    val all: List<Workflow> = emptyList(),
    val recent: List<Workflow> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class RunnerViewModel @Inject constructor(
    private val workflows: WorkflowRepository,
    private val executeWorkflow: ExecuteWorkflowUseCase,
    settings: SettingsRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val runState = MutableStateFlow(RunnerUiState())

    private var job: Job? = null
    private var started = false
    private var input = WorkflowInput()

    private val catalog = combine(
        workflows.observeAll(),
        workflows.observeRecent(),
    ) { all, recent -> Catalog(all, recent, loaded = true) }
        // An empty catalog up front stops a run-by-id from waiting on a database query whose
        // results it is never going to show.
        .onStart { emit(Catalog()) }

    private val compactPicker = settings.settings
        .map { it.compactPicker }
        .distinctUntilChanged()

    val uiState: StateFlow<RunnerUiState> =
        combine(catalog, query, runState, compactPicker) { catalog, query, run, compact ->
            run.copy(
                query = query,
                sections = if (run.workflow != null) emptyList() else sections(catalog, query),
                catalogLoaded = catalog.loaded,
                compactPicker = compact,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(SUBSCRIPTION_GRACE_MS), RunnerUiState())

    /**
     * Idempotent: a configuration change re-enters composition with the same request, and
     * re-firing the model at that point would bill the user twice for one tap.
     */
    fun start(request: RunRequest) {
        if (started) return
        started = true
        input = request.input
        val id = request.workflowId ?: return
        runState.update { it.copy(resolving = true) }
        viewModelScope.launch {
            // A shortcut or bubble can outlive the workflow it points at. Dropping the user
            // into the picker is friendlier than an error for something one tap can fix.
            val workflow = workflows.get(id)
            if (workflow != null) run(workflow) else runState.update { it.copy(resolving = false) }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun run(workflow: Workflow) {
        job?.cancel()
        runState.value = RunnerUiState(workflow = workflow)
        job = viewModelScope.launch {
            executeWorkflow(workflow, input).collect { state ->
                runState.update { it.copy(execution = state) }
            }
        }
    }

    fun retry() {
        runState.value.workflow?.let(::run)
    }

    /** Cancelling is a decision, not a failure, so the partial answer stays on screen. */
    fun stop() {
        job?.cancel()
        job = null
        runState.update { it.copy(stopped = true) }
    }

    fun onOutputApplied() {
        runState.update { it.copy(outputApplied = true) }
    }

    private fun sections(catalog: Catalog, query: String): List<PickerSection> {
        if (!catalog.loaded) return emptyList()

        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val hits = catalog.all.filter { it.matches(trimmed) }
            return if (hits.isEmpty()) emptyList() else listOf(PickerSection(null, hits))
        }

        val starred = catalog.all
            .filter { it.isPinned || it.isFavorite }
            .sortedWith(
                compareByDescending<Workflow> { it.isPinned }
                    .thenBy { it.sortOrder }
                    .thenBy { it.name },
            )
        // Each workflow appears once: the highest-priority group that claims it wins.
        val taken = starred.mapTo(mutableSetOf()) { it.id }
        val recent = catalog.recent.filterNot { it.id in taken }
        taken += recent.map { it.id }
        val rest = catalog.all.filterNot { it.id in taken }

        return listOf(
            PickerSection("Pinned & favourites", starred),
            PickerSection("Recent", recent),
            PickerSection("All workflows", rest),
        ).filter { it.workflows.isNotEmpty() }
    }

    private fun Workflow.matches(query: String): Boolean =
        name.contains(query, ignoreCase = true) || category.name.contains(query, ignoreCase = true)

    private companion object {
        /** Long enough to ride out a rotation without tearing down the catalog queries. */
        const val SUBSCRIPTION_GRACE_MS = 5_000L
    }
}
