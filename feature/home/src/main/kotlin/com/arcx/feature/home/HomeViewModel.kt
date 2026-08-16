package com.arcx.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.di.DefaultDispatcher
import com.arcx.core.common.time.TimeSource
import com.arcx.core.common.time.startOfDay
import com.arcx.core.common.time.timeTicker
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.core.model.RunOutcome
import com.arcx.core.model.RunSummary
import com.arcx.core.model.Workflow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

private const val STOP_TIMEOUT_MS = 5_000L

/** Two full rows of three, plus the "add" cell. Beyond that the grid stops being scannable. */
private const val MAX_TILES = 8

/** Enough to answer "what did I just do", not enough to become a second Activity screen. */
private const val MAX_RECENT_RUNS = 3

/**
 * Said instead of showing an empty screen. Room and DataStore can both fail outright — a
 * database Android refused to open, a preferences file that did not survive a restore — and a
 * flow that throws simply stops, leaving Home holding whatever it had, which on launch is
 * nothing. "No workflows yet" then blames the user for the app's problem.
 *
 * Deliberately not the exception's own text: a `SQLiteDatabaseCorruptException` is not something
 * the person reading it can act on.
 */
private const val LOAD_FAILED =
    "ArcX couldn't read your workflows or your run history from this device. " +
        "Reopening the app usually fixes it."

/** A workflow as the grid draws it: the thing itself, plus what it usually costs to run. */
data class HomeTile(
    val workflow: Workflow,
    /** Mean duration of its successful runs, or null when it has never finished one. */
    val averageMs: Long?,
)

/** What a launch surface is currently doing, in the three states a chip can honestly report. */
enum class SurfaceState { LIVE, ALWAYS_ON, OFF }

data class SurfaceChip(val label: String, val state: SurfaceState)

/**
 * Home is the library's fast path plus a live report on the ways into it.
 *
 * The surface strip is the part that is not cosmetic: a bubble that Android stopped, or screen
 * reading that an OEM revoked while ArcX was in the background, used to be invisible until the
 * user went looking in Settings for why a workflow had gone quiet. Saying it on the first screen
 * is the whole point, which is also why [onResume] re-reads the permissions rather than
 * observing them — none of them emit anything when they change.
 */
data class HomeUiState(
    val greeting: String = "",
    val subtitle: String = "",
    val query: String = "",
    val tiles: List<HomeTile> = emptyList(),
    val recentRuns: List<RunSummary> = emptyList(),
    val surfaces: List<SurfaceChip> = emptyList(),
    /**
     * Pinned at emission so every row's "9m ago" is measured against the same instant, and
     * refreshed on a timer rather than only when something upstream moves — see the ticker stage
     * on [HomeViewModel.uiState].
     */
    val nowMillis: Long = 0L,
    /** Separates "you have nothing yet" from "nothing matches what you typed". */
    val libraryIsEmpty: Boolean = false,
    val loading: Boolean = true,
    /** Set when the reads behind this screen failed, so an empty Home can say which kind it is. */
    val error: String? = null,
) {
    val searching: Boolean get() = query.isNotBlank()
}

/** What history contributes to Home, combined once so the outer flow stays inside combine's arity. */
private data class Activity(
    val recent: List<RunSummary>,
    val averages: Map<String, Long>,
    val today: List<RunOutcome>,
)

/** The four slices of the library, combined once so the outer flow stays inside combine's arity. */
private data class Library(
    val all: List<Workflow>,
    val pinned: List<Workflow>,
    val favorites: List<Workflow>,
    val recent: List<Workflow>,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    workflows: WorkflowRepository,
    history: HistoryRepository,
    settings: SettingsRepository,
    private val surfaces: SystemSurfaces,
    private val time: TimeSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val permissions = MutableStateFlow(Permissions())

    // Fixed for the lifetime of the screen; re-deriving it on every emission would let the
    // greeting flip mid-scroll as the clock crosses noon.
    private val greeting = greetingFor(time.nowMillis())

    // Likewise fixed: the query is bounded by this instant, and a screen open across midnight
    // showing yesterday's count until it is reopened is better than re-querying on every tick.
    private val startOfToday = startOfDay(time.nowMillis())

    private val library = combine(
        workflows.observeAll(),
        workflows.observePinned(),
        workflows.observeFavorites(),
        workflows.observeRecent(),
        ::Library,
    )

    /**
     * Three bounded reads instead of one unbounded one. Home used to collect every run ever
     * recorded to show three of them and a mean — the mean is now a SQL aggregate, the count is
     * a day's worth of rows, and the list asks for exactly the three it draws.
     */
    private val activity = combine(
        history.observeRecent(MAX_RECENT_RUNS),
        history.observeAverageDurations(),
        history.observeSince(startOfToday),
        ::Activity,
    )

    val uiState: StateFlow<HomeUiState> = combine(
        library,
        activity,
        settings.settings,
        permissions,
        query,
    ) { shelf, runs, user, granted, text ->
        HomeUiState(
            greeting = greeting,
            subtitle = subtitleFor(shelf.all.size, runs.today.size),
            query = text,
            tiles = tileOrder(shelf).matching(text).take(MAX_TILES)
                .map { HomeTile(it, runs.averages[it.id]) },
            // Runs are the answer to "what did I just do", which a search over workflow names
            // is not; hide them rather than filter them into something meaningless.
            recentRuns = if (text.isBlank()) runs.recent else emptyList(),
            surfaces = surfaceChips(user.bubbleEnabled, granted),
            libraryIsEmpty = shelf.all.isEmpty(),
            loading = false,
        )
    }
        // The clock is combined in rather than read inside the fold above, for two reasons. The
        // fold is already at combine's five-flow typed arity — which is why `library` and
        // `activity` are grouped in the first place — and re-running it every minute would
        // re-sort, re-filter and re-map the whole library to move one word in a subtitle. `now`
        // is the only part of this state that goes stale on its own, so it is the only part a
        // tick touches. Placed above `catch` so a throwing upstream still ends the flow here.
        .combine(timeTicker(time)) { state, now -> state.copy(nowMillis = now) }
        // Above flowOn so the replacement state is built off the main thread with everything
        // else. A throwing flow is a finished flow, so this is the last thing Home will show
        // until the screen is reopened — which is exactly what the copy tells the user to do.
        .catch { emit(HomeUiState(greeting = greeting, loading = false, error = LOAD_FAILED)) }
        // Off the main thread. `stateIn(viewModelScope)` collects on Main.immediate, so before
        // this every fold above ran on the UI thread — which is how a row count turned into
        // dropped frames rather than just memory.
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HomeUiState(greeting = greeting),
        )

    /**
     * Called on every resume. Overlay and accessibility can both be taken away in system
     * Settings while ArcX is in the background, and a strip that still says "Bubble on" after
     * that is worse than no strip at all.
     */
    fun onResume() {
        permissions.value = Permissions(
            overlayGranted = surfaces.isOverlayGranted(),
            screenReadingLive = surfaces.isScreenReadingEnabled() && surfaces.isScreenReadingRunning(),
        )
    }

    fun onQueryChange(text: String) {
        query.value = text
    }

    private data class Permissions(
        val overlayGranted: Boolean = false,
        /** Switched on *and* bound. A service Android lists but is not hosting is not live. */
        val screenReadingLive: Boolean = false,
    )

    private fun surfaceChips(bubbleEnabled: Boolean, granted: Permissions) = listOf(
        SurfaceChip(
            label = if (bubbleEnabled && granted.overlayGranted) "Bubble on" else "Bubble off",
            state = if (bubbleEnabled && granted.overlayGranted) SurfaceState.LIVE else SurfaceState.OFF,
        ),
        // Both are declared in the manifest and cannot be switched off, so they never warn.
        SurfaceChip("Share", SurfaceState.ALWAYS_ON),
        SurfaceChip("Selection", SurfaceState.ALWAYS_ON),
        SurfaceChip(
            label = if (granted.screenReadingLive) "Screen text" else "Screen text off",
            state = if (granted.screenReadingLive) SurfaceState.LIVE else SurfaceState.OFF,
        ),
    )
}

private fun subtitleFor(workflowCount: Int, runsToday: Int): String {
    val workflows = "$workflowCount ${if (workflowCount == 1) "workflow" else "workflows"}"
    return "$workflows · $runsToday ${if (runsToday == 1) "run" else "runs"} today"
}

/**
 * Pinned first, then favourites, then whatever was run most recently, then the rest of the
 * library in its own order — so the grid fills up even on a fresh install, and settles into
 * what the user actually uses without ever being told to.
 */
private fun tileOrder(shelf: Library): List<Workflow> =
    (shelf.pinned + shelf.favorites + shelf.recent + shelf.all.sortedBy { it.sortOrder })
        .distinctBy { it.id }

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
