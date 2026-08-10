package com.arcx.feature.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.DeleteWorkflowUseCase
import com.arcx.core.domain.usecase.DuplicateWorkflowUseCase
import com.arcx.core.domain.usecase.ToggleFavoriteUseCase
import com.arcx.core.domain.usecase.TogglePinnedUseCase
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

internal data class WorkflowListUiState(
    val loading: Boolean = true,
    val query: String = "",
    val category: WorkflowCategory? = null,
    val workflows: List<Workflow> = emptyList(),
    /**
     * Whether the library has anything in it at all. Without this the screen cannot tell
     * "you have no workflows yet" from "your filter matched none", which need different copy.
     */
    val libraryIsEmpty: Boolean = false,
) {
    val isFiltered: Boolean get() = query.isNotBlank() || category != null
}

@HiltViewModel
internal class WorkflowListViewModel @Inject constructor(
    workflows: WorkflowRepository,
    private val favorite: ToggleFavoriteUseCase,
    private val pinned: TogglePinnedUseCase,
    private val remove: DeleteWorkflowUseCase,
    private val clone: DuplicateWorkflowUseCase,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<WorkflowCategory?>(null)

    /** Copies made from a read-only built-in open straight into the editor; nothing else navigates. */
    private val editRequests = Channel<String>(Channel.BUFFERED)
    val editCopyRequests: Flow<String> = editRequests.receiveAsFlow()

    val uiState: StateFlow<WorkflowListUiState> =
        combine(workflows.observeAll(), query, category) { all, text, filter ->
            WorkflowListUiState(
                loading = false,
                query = text,
                category = filter,
                workflows = all.filter { it.matches(text, filter) },
                libraryIsEmpty = all.isEmpty(),
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WorkflowListUiState(),
        )

    fun setQuery(value: String) {
        query.value = value
    }

    fun setCategory(value: WorkflowCategory?) {
        category.value = value
    }

    fun clearFilters() {
        query.value = ""
        category.value = null
    }

    fun toggleFavorite(workflow: Workflow) {
        viewModelScope.launch { favorite(workflow) }
    }

    fun togglePinned(workflow: Workflow) {
        viewModelScope.launch { pinned(workflow) }
    }

    fun delete(workflow: Workflow) {
        viewModelScope.launch { remove(workflow.id) }
    }

    /**
     * [openEditor] is what "Duplicate to edit" on a read-only built-in really means: clone it,
     * then take the user to the clone.
     */
    fun duplicate(workflow: Workflow, openEditor: Boolean) {
        viewModelScope.launch {
            val copy = clone(workflow.id)
            if (openEditor && copy != null) editRequests.send(copy.id)
        }
    }
}

private fun Workflow.matches(query: String, category: WorkflowCategory?): Boolean {
    if (category != null && this.category != category) return false
    if (query.isBlank()) return true
    // Prompts are searched too: people remember what a workflow says more often than its name.
    return name.contains(query, ignoreCase = true) || prompt.contains(query, ignoreCase = true)
}
