package com.arcx.feature.discover

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.domain.repository.WorkflowBundleRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.WorkflowCategory
import com.arcx.core.model.WorkflowSpec
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

internal const val EXPORT_FILE_NAME = "arcx-workflows.json"

internal data class DiscoverUiState(
    val loading: Boolean = true,
    val query: String = "",
    val category: WorkflowCategory? = null,
    /** Everything in the bundled gallery, before filtering. */
    val gallery: List<WorkflowSpec> = emptyList(),
    val galleryError: String? = null,
    /**
     * Names already in the library, so a row can say "Added" instead of offering an install
     * that would quietly make a second copy. Matched by name because an installed workflow is
     * a copy with its own id — there is nothing else linking it back to the gallery entry.
     */
    val installedNames: Set<String> = emptySet(),
    val selected: WorkflowSpec? = null,
    val busy: Boolean = false,
    val message: String? = null,
    /**
     * [gallery] narrowed by [query] and [category]. A plain field, not a computed one: as a
     * getter the whole gallery was filtered again on every recomposition, which is every frame
     * of a scroll and every keystroke, rather than once per change.
     */
    val visible: List<WorkflowSpec> = emptyList(),
    /**
     * The gallery's opening picks — the first few entries the user has not already taken. It
     * empties itself as they install things, which is the point: a "start here" shelf that
     * still says start here after twenty workflows is furniture.
     */
    val startHere: List<WorkflowSpec> = emptyList(),
    /** Only the categories the bundled gallery actually contains, with their sizes. */
    val categoryCounts: List<Pair<WorkflowCategory, Int>> = emptyList(),
)

private const val START_HERE_COUNT = 3

/**
 * Fills in the three derived lists from what they are derived from. Every write to the state
 * goes through here, so a field cannot be left describing an older gallery or an older query.
 */
private fun DiscoverUiState.withDerived(): DiscoverUiState = copy(
    visible = gallery.filter { spec ->
        (category == null || spec.category == category) &&
            (
                query.isBlank() ||
                    spec.name.contains(query, ignoreCase = true) ||
                    spec.prompt.contains(query, ignoreCase = true)
                )
    },
    startHere = if (query.isNotBlank() || category != null) {
        emptyList()
    } else {
        gallery.filterNot { it.name in installedNames }.take(START_HERE_COUNT)
    },
    categoryCounts = WorkflowCategory.entries
        .map { it to gallery.count { spec -> spec.category == it } }
        .filter { it.second > 0 },
)

/**
 * The gallery, and the file end of the library.
 *
 * Reading the bundled gallery, parsing an imported file and writing an export all belong to
 * [WorkflowBundleRepository] — this used to inject `@ApplicationContext Context` and do its own
 * `contentResolver` IO, which made it the only ViewModel in the app that did either.
 */
@HiltViewModel
internal class DiscoverViewModel @Inject constructor(
    private val workflows: WorkflowRepository,
    private val bundles: WorkflowBundleRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    /**
     * Emits the id of a freshly installed workflow so the screen can open it — only from the
     * detail sheet, where the user has read the prompt and is plausibly about to change it.
     * The list's own Install button stays put and flips to "Added": being thrown into the
     * editor after tapping a button in a list is not what that button looked like it did.
     */
    private val installs = Channel<String>(Channel.BUFFERED)
    val installed: Flow<String> = installs.receiveAsFlow()

    init {
        viewModelScope.launch {
            val result = runCatching { bundles.readGallery() }
            updateState { state ->
                result.fold(
                    onSuccess = { state.copy(loading = false, gallery = it) },
                    onFailure = { state.copy(loading = false, galleryError = "The bundled gallery could not be read.") },
                )
            }
        }
        viewModelScope.launch {
            workflows.observeAll().collect { library ->
                updateState { state ->
                    state.copy(installedNames = library.mapTo(mutableSetOf()) { it.name })
                }
            }
        }
    }

    /**
     * The one way into [_uiState]. Deriving on the way in — rather than in getters the UI reads —
     * is what keeps the filtering and the counting to once per change instead of once per frame.
     */
    private fun updateState(transform: (DiscoverUiState) -> DiscoverUiState) {
        _uiState.update { transform(it).withDerived() }
    }

    fun setQuery(value: String) = updateState { it.copy(query = value) }

    fun setCategory(value: WorkflowCategory?) = updateState { it.copy(category = value) }

    fun clearFilters() = updateState { it.copy(query = "", category = null) }

    fun select(spec: WorkflowSpec?) = updateState { it.copy(selected = spec) }

    fun dismissMessage() = updateState { it.copy(message = null) }

    fun install(spec: WorkflowSpec, openEditor: Boolean = false) {
        viewModelScope.launch {
            updateState { it.copy(busy = true) }
            val saved = runCatching { bundles.install(listOf(spec)).firstOrNull() }.getOrNull()
            updateState {
                it.copy(
                    busy = false,
                    selected = null,
                    message = if (saved != null) {
                        "${spec.name} added to your workflows"
                    } else {
                        "Could not add ${spec.name}"
                    },
                )
            }
            if (openEditor) saved?.let { installs.send(it.id) }
        }
    }

    /**
     * Reading and installing are two calls, not one, and the gap between them is the point: the
     * file is fully parsed and sanitised before anything is written, which is where a review sheet
     * goes. Nothing shows that gap to the user yet — it exists so that it can.
     *
     * A truncated or unrelated JSON file still has to produce a sentence the user can act on, not a
     * crash and not a half-populated library.
     */
    fun import(uri: Uri) {
        viewModelScope.launch {
            updateState { it.copy(busy = true) }
            val message = runCatching { bundles.read(uri) }.fold(
                onSuccess = { specs ->
                    if (specs.isEmpty()) {
                        "That file has no workflows in it"
                    } else {
                        runCatching { bundles.install(specs) }.fold(
                            onSuccess = { added ->
                                "Imported ${added.size} ${if (added.size == 1) "workflow" else "workflows"}"
                            },
                            onFailure = { "Those workflows could not be added" },
                        )
                    }
                },
                onFailure = { "That does not look like an ArcX workflow file" },
            )
            updateState { it.copy(busy = false, message = message) }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            updateState { it.copy(busy = true) }
            val count = runCatching {
                val all = workflows.observeAll().first()
                bundles.write(uri, all)
                all.size
            }
            updateState {
                it.copy(
                    busy = false,
                    message = count.fold(
                        onSuccess = { n -> "Exported $n ${if (n == 1) "workflow" else "workflows"}" },
                        onFailure = { "Could not write that file" },
                    ),
                )
            }
        }
    }
}
