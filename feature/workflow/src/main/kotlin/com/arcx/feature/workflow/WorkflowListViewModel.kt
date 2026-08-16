package com.arcx.feature.workflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.di.DefaultDispatcher
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.DeleteWorkflowUseCase
import com.arcx.core.domain.usecase.DuplicateWorkflowUseCase
import com.arcx.core.domain.usecase.SaveWorkflowUseCase
import com.arcx.core.domain.usecase.ToggleFavoriteUseCase
import com.arcx.core.domain.usecase.TogglePinnedUseCase
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

/** One read of the library: the rows, or the reason there are none of them. */
private data class Library(
    val workflows: List<Workflow> = emptyList(),
    val error: String? = null,
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
    /**
     * Set when the library could not be read at all. Without it a Room failure draws the same
     * blank list as a fresh install, and "you have no workflows yet" is the one thing it does
     * not mean — the user's workflows are still there, unreachable.
     */
    val error: String? = null,
    /** False on launchers that refuse pinned shortcuts; the menu item is hidden rather than dead. */
    val canPinShortcut: Boolean = false,
) {
    val isFiltered: Boolean get() = query.isNotBlank() || category != null
    val isEmpty: Boolean get() = sections.all { it.workflows.isEmpty() }
}

@HiltViewModel
internal class WorkflowListViewModel @Inject constructor(
    workflows: WorkflowRepository,
    private val surfaces: SystemSurfaces,
    private val favorite: ToggleFavoriteUseCase,
    private val pinned: TogglePinnedUseCase,
    private val remove: DeleteWorkflowUseCase,
    private val restore: SaveWorkflowUseCase,
    private val clone: DuplicateWorkflowUseCase,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val category = MutableStateFlow<WorkflowCategory?>(null)
    private val sort = MutableStateFlow(LibrarySort.RECENT)

    /**
     * Asked once, in [init], rather than inside the fold below. It is a binder call into
     * ShortcutManager and its answer cannot change while the screen is open — the launcher would
     * have to be swapped underneath it — but as part of the combine it re-ran on every keystroke
     * and every edit to any workflow.
     */
    private val canPinShortcut = MutableStateFlow(false)

    /** Copies made from a read-only built-in open straight into the editor; nothing else navigates. */
    private val editRequests = Channel<String>(Channel.BUFFERED)
    val editCopyRequests: Flow<String> = editRequests.receiveAsFlow()

    /**
     * The workflow each delete removed, carried out to whoever offers the undo.
     *
     * The whole [Workflow] travels rather than its id because the row is gone by the time this is
     * read — there is nothing left in the database to look it up from. It is deliberately not held
     * in a field here: the collector owns it for exactly as long as its snackbar is on screen and
     * drops it when that returns, so nothing survives a screen the user has walked away from.
     */
    private val deletions = Channel<Workflow>(Channel.BUFFERED)
    val deletedWorkflows: Flow<Workflow> = deletions.receiveAsFlow()

    /**
     * Room's Flow throws into whoever collects it, which would cancel [uiState] and freeze the
     * screen on whatever it last drew. Catching it here turns a failed read into something the
     * screen can say out loud instead of an empty list that looks like an empty library.
     */
    private val library: Flow<Library> = workflows.observeAll()
        .map { Library(workflows = it) }
        .catch { emit(Library(error = "Your workflows could not be read.")) }

    init {
        viewModelScope.launch {
            canPinShortcut.value = withContext(defaultDispatcher) { surfaces.canPinShortcut() }
        }
    }

    val uiState: StateFlow<WorkflowListUiState> =
        combine(
            library,
            query,
            category,
            sort,
            canPinShortcut,
        ) { shelf, text, filter, order, canPin ->
            val all = shelf.workflows
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
                error = shelf.error,
                canPinShortcut = canPin,
            )
        }
            // Off the main thread. `stateIn(viewModelScope)` collects on Main.immediate, so
            // without this the sort, the grouping and a count per category were all UI-thread
            // work over the whole library — repeated on every keystroke.
            .flowOn(defaultDispatcher)
            .stateIn(
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

    /**
     * Hands the workflow to the launcher as its own icon. The launcher shows its own confirmation
     * and owns the outcome, so there is nothing to report back — and nothing changes in ArcX, which
     * is why this touches no repository.
     */
    fun addToHomeScreen(workflow: Workflow) {
        surfaces.pinWorkflowShortcut(workflow)
    }

    fun delete(workflow: Workflow) {
        viewModelScope.launch {
            remove(workflow.id)
            deletions.send(workflow)
        }
    }

    /**
     * Puts a deleted workflow back, **with the id it had before**.
     *
     * That is the whole point of restoring the object rather than rebuilding one. A pinned
     * home-screen shortcut and the favourites widget both fire `arcx://run/{id}`, and a launcher
     * keeps a pinned shortcut whether or not the workflow behind it still exists — so a new id
     * here would leave every one of them pointing at nothing, permanently, with no way for the
     * user to tell what broke. [SaveWorkflowUseCase] only mints a UUID when the id is blank, so
     * handing it the original workflow is enough; `WorkflowUseCasesTest` pins that behaviour.
     *
     * Two things do not come back with it. `updatedAt` is restamped — the repository sets it on
     * every write — so a restored workflow surfaces at the top of "Recently updated". And
     * `lastRunAt` is storage bookkeeping the model never carries, so the workflow is absent from
     * Home's "Recent" row until it is next run. History rows are not involved either way: nothing
     * cascades from a workflow delete, so they were never gone.
     */
    fun undoDelete(workflow: Workflow) {
        viewModelScope.launch { restore(workflow) }
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
