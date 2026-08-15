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

/** How the library orders itself inside each section. */
internal enum class LibrarySort(val label: String) {
    RECENT("Recently updated"),
    NAME("Name A–Z"),
}

/** A run of rows under one heading. A null [title] is the flat list a search produces. */
internal data class LibrarySection(
    val title: String?,
    val workflows: List<Workflow>,
)

/** One filter pill: the category, its display name, and how many rows are behind it. */
internal data class CategoryCount(
    val category: WorkflowCategory,
    val label: String,
    val count: Int,
)

internal data class WorkflowListUiState(
    val loading: Boolean = true,
    val query: String = "",
    val category: WorkflowCategory? = null,
    val sort: LibrarySort = LibrarySort.RECENT,
    val total: Int = 0,
    val counts: List<CategoryCount> = emptyList(),
    val sections: List<LibrarySection> = emptyList(),
    /**
     * Whether the library has anything in it at all. Without this the screen cannot tell
     * "you have no workflows yet" from "your filter matched none", which need different copy.
     */
    val libraryIsEmpty: Boolean = false,
) {
    val isFiltered: Boolean get() = query.isNotBlank() || category != null
    val isEmpty: Boolean get() = sections.all { it.workflows.isEmpty() }
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
    private val sort = MutableStateFlow(LibrarySort.RECENT)

    /** Copies made from a read-only built-in open straight into the editor; nothing else navigates. */
    private val editRequests = Channel<String>(Channel.BUFFERED)
    val editCopyRequests: Flow<String> = editRequests.receiveAsFlow()

    val uiState: StateFlow<WorkflowListUiState> =
        combine(workflows.observeAll(), query, category, sort) { all, text, filter, order ->
            val visible = all.filter { it.matches(text, filter) }
            WorkflowListUiState(
                loading = false,
                query = text,
                category = filter,
                sort = order,
                total = all.size,
                counts = WorkflowCategory.entries
                    .map { CategoryCount(it, it.label, all.count { w -> w.category == it }) }
                    .filter { it.count > 0 },
                sections = sectionsOf(visible, order, grouped = text.isBlank()),
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

    fun setSort(value: LibrarySort) {
        sort.value = value
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

/**
 * Pinned first, then one section per category that has anything in it.
 *
 * A search skips the grouping entirely: someone who typed three letters is looking for one
 * workflow, and headings between the two results only add distance to it.
 */
private fun sectionsOf(
    workflows: List<Workflow>,
    sort: LibrarySort,
    grouped: Boolean,
): List<LibrarySection> {
    val ordered = when (sort) {
        LibrarySort.NAME -> workflows.sortedBy { it.name.lowercase() }
        LibrarySort.RECENT -> workflows.sortedByDescending { it.updatedAt }
    }
    if (!grouped) return listOf(LibrarySection(title = null, workflows = ordered))

    val pinned = ordered.filter { it.isPinned }
    val rest = ordered.filterNot { it.isPinned }
    return buildList {
        if (pinned.isNotEmpty()) add(LibrarySection("Pinned", pinned))
        WorkflowCategory.entries.forEach { category ->
            val inCategory = rest.filter { it.category == category }
            if (inCategory.isNotEmpty()) add(LibrarySection(category.label, inCategory))
        }
    }
}

private fun Workflow.matches(query: String, category: WorkflowCategory?): Boolean {
    if (category != null && this.category != category) return false
    if (query.isBlank()) return true
    // Prompts are searched too: people remember what a workflow says more often than its name.
    return name.contains(query, ignoreCase = true) || prompt.contains(query, ignoreCase = true)
}
