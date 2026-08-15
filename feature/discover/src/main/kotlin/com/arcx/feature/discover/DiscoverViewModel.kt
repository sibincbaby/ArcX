package com.arcx.feature.discover

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.di.IoDispatcher
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.SaveWorkflowUseCase
import com.arcx.core.model.WorkflowCategory
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal const val GALLERY_ASSET = "gallery.json"
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
) {
    val visible: List<WorkflowSpec>
        get() = gallery.filter { spec ->
            (category == null || spec.category == category) &&
                (
                    query.isBlank() ||
                        spec.name.contains(query, ignoreCase = true) ||
                        spec.prompt.contains(query, ignoreCase = true)
                    )
        }

    /**
     * The gallery's opening picks — the first few entries the user has not already taken. It
     * empties itself as they install things, which is the point: a "start here" shelf that
     * still says start here after twenty workflows is furniture.
     */
    val startHere: List<WorkflowSpec>
        get() = if (query.isNotBlank() || category != null) {
            emptyList()
        } else {
            gallery.filterNot { it.name in installedNames }.take(START_HERE_COUNT)
        }

    /** Only the categories the bundled gallery actually contains, with their sizes. */
    val categoryCounts: List<Pair<WorkflowCategory, Int>>
        get() = WorkflowCategory.entries
            .map { it to gallery.count { spec -> spec.category == it } }
            .filter { it.second > 0 }
}

private const val START_HERE_COUNT = 3

@HiltViewModel
internal class DiscoverViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workflows: WorkflowRepository,
    private val saveWorkflow: SaveWorkflowUseCase,
    @IoDispatcher private val io: CoroutineDispatcher,
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
            val result = runCatching {
                withContext(io) {
                    context.assets.open(GALLERY_ASSET)
                        .bufferedReader()
                        .use { bundleJson.decodeFromString(WorkflowBundle.serializer(), it.readText()) }
                        .workflows
                }
            }
            _uiState.update { state ->
                result.fold(
                    onSuccess = { state.copy(loading = false, gallery = it) },
                    onFailure = { state.copy(loading = false, galleryError = "The bundled gallery could not be read.") },
                )
            }
        }
        viewModelScope.launch {
            workflows.observeAll().collect { library ->
                _uiState.update { state ->
                    state.copy(installedNames = library.mapTo(mutableSetOf()) { it.name })
                }
            }
        }
    }

    fun setQuery(value: String) = _uiState.update { it.copy(query = value) }

    fun setCategory(value: WorkflowCategory?) = _uiState.update { it.copy(category = value) }

    fun clearFilters() = _uiState.update { it.copy(query = "", category = null) }

    fun select(spec: WorkflowSpec?) = _uiState.update { it.copy(selected = spec) }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun install(spec: WorkflowSpec, openEditor: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val saved = runCatching { saveWorkflow(spec.toWorkflow()) }
            _uiState.update {
                it.copy(
                    busy = false,
                    selected = null,
                    message = if (saved.isSuccess) {
                        "${spec.name} added to your workflows"
                    } else {
                        "Could not add ${spec.name}"
                    },
                )
            }
            if (openEditor) saved.getOrNull()?.let { installs.send(it.id) }
        }
    }

    /**
     * Import validates before it writes: a truncated or unrelated JSON file has to produce a
     * sentence the user can act on, not a crash and not a half-populated library.
     */
    fun import(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val specs = runCatching {
                withContext(io) {
                    val text = context.contentResolver.openInputStream(uri)
                        ?.bufferedReader()
                        ?.use { it.readText() }
                        ?: error("That file could not be opened")
                    val bundle = bundleJson.decodeFromString(WorkflowBundle.serializer(), text)
                    bundle.workflows.filter { it.name.isNotBlank() && it.prompt.isNotBlank() }
                }
            }

            val message = specs.fold(
                onSuccess = { imported ->
                    if (imported.isEmpty()) {
                        "That file has no workflows in it"
                    } else {
                        imported.forEach { saveWorkflow(it.toWorkflow()) }
                        "Imported ${imported.size} ${if (imported.size == 1) "workflow" else "workflows"}"
                    }
                },
                onFailure = { "That does not look like an ArcX workflow file" },
            )
            _uiState.update { it.copy(busy = false, message = message) }
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true) }
            val count = runCatching {
                val all = workflows.observeAll().first()
                val bundle = WorkflowBundle(workflows = all.map { it.toSpec() })
                withContext(io) {
                    context.contentResolver.openOutputStream(uri)
                        ?.bufferedWriter()
                        ?.use { it.write(bundleJson.encodeToString(WorkflowBundle.serializer(), bundle)) }
                        ?: error("That file could not be written")
                }
                all.size
            }
            _uiState.update {
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
