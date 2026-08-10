package com.arcx.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.domain.usecase.ToggleFavoriteUseCase
import com.arcx.core.domain.usecase.TogglePinnedUseCase
import com.arcx.core.model.Workflow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Below this many runs the user has barely started, so built-ins they have not touched are more
 * useful than a screen that is mostly whitespace. Above it, Home belongs to what they use.
 */
private const val SUGGEST_UNTIL_RUNS = 4
private const val MAX_SUGGESTIONS = 4

/**
 * Home is four slices of the same library, so every section is derived from the repository
 * rather than held separately: whatever the user pins, favourites or runs shows up here on the
 * next emission without Home having to be told it happened.
 */
data class HomeUiState(
    val greeting: String = "",
    val query: String = "",
    val pinned: List<Workflow> = emptyList(),
    val favorites: List<Workflow> = emptyList(),
    val recent: List<Workflow> = emptyList(),
    val suggestions: List<Workflow> = emptyList(),
    /** Separates "you have nothing yet" from "nothing matches what you typed". */
    val libraryIsEmpty: Boolean = false,
    val loading: Boolean = true,
) {
    val hasVisibleSections: Boolean
        get() = pinned.isNotEmpty() ||
            favorites.isNotEmpty() ||
            recent.isNotEmpty() ||
            suggestions.isNotEmpty()
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val workflows: WorkflowRepository,
    private val toggleFavorite: ToggleFavoriteUseCase,
    private val togglePinned: TogglePinnedUseCase,
    time: TimeSource,
) : ViewModel() {

    private val query = MutableStateFlow("")

    // Fixed for the lifetime of the screen; re-deriving it on every emission would let the
    // greeting flip mid-scroll as the clock crosses noon.
    private val greeting = greetingFor(time.nowMillis())

    val uiState: StateFlow<HomeUiState> = combine(
        workflows.observeAll(),
        workflows.observePinned(),
        workflows.observeFavorites(),
        workflows.observeRecent(),
        query,
    ) { all, pinned, favorites, recent, text ->
        HomeUiState(
            greeting = greeting,
            query = text,
            pinned = pinned.matching(text),
            favorites = favorites.matching(text),
            recent = recent.matching(text),
            suggestions = suggestionsFrom(all, recent).matching(text),
            libraryIsEmpty = all.isEmpty(),
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HomeUiState(greeting = greeting),
    )

    fun onQueryChange(text: String) {
        query.value = text
    }

    fun onToggleFavorite(workflow: Workflow) {
        viewModelScope.launch { toggleFavorite(workflow) }
    }

    fun onTogglePinned(workflow: Workflow) {
        viewModelScope.launch { togglePinned(workflow) }
    }
}

/**
 * Built-ins the user has never run — the only kind of suggestion ArcX can honestly make, since
 * there is no server watching behaviour. "Suggested" here means "bundled and still unused".
 */
private fun suggestionsFrom(all: List<Workflow>, recent: List<Workflow>): List<Workflow> {
    if (recent.size >= SUGGEST_UNTIL_RUNS) return emptyList()
    val alreadySurfaced = recent.mapTo(mutableSetOf()) { it.id }
    return all
        .filter { it.isBuiltIn && it.id !in alreadySurfaced && !it.isPinned && !it.isFavorite }
        .sortedBy { it.sortOrder }
        .take(MAX_SUGGESTIONS)
}

private fun List<Workflow>.matching(query: String): List<Workflow> {
    val trimmed = query.trim()
    if (trimmed.isEmpty()) return this
    return filter { workflow ->
        workflow.name.contains(trimmed, ignoreCase = true) ||
            workflow.category.name.contains(trimmed, ignoreCase = true)
    }
}

private fun greetingFor(nowMillis: Long): String =
    when (Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()).hour) {
        in 5..11 -> "Good morning"
        in 12..17 -> "Good afternoon"
        in 18..21 -> "Good evening"
        else -> "Hello"
    }
