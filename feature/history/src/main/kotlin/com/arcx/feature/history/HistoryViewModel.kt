package com.arcx.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.di.DefaultDispatcher
import com.arcx.core.common.time.TimeSource
import com.arcx.core.common.time.startOfDay
import com.arcx.core.domain.capture.ClipboardAccess
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.usecase.ClearHistoryUseCase
import com.arcx.core.model.RunOutcome
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunStatus
import com.arcx.core.model.RunSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** One day's worth of runs under a heading the user can read at a glance. */
data class HistoryDay(
    val label: String,
    val runs: List<RunSummary>,
)

/**
 * Today at a glance, above the list.
 *
 * Today rather than all time on purpose: a lifetime average is a number that stops moving, and
 * the question this screen answers is "is ArcX behaving right now" — a median that has doubled
 * or a failure count that is not zero is the thing worth noticing.
 */
data class RunStats(
    val runsToday: Int = 0,
    /** Median of today's successful runs. Median, not mean: one 30s timeout should not skew it. */
    val medianMs: Long = 0L,
    val failedToday: Int = 0,
)

data class HistoryUiState(
    val days: List<HistoryDay> = emptyList(),
    val stats: RunStats = RunStats(),
    val historyEnabled: Boolean = true,
    /** Pinned at emission time so every row's "3h ago" is measured against the same instant. */
    val nowMillis: Long = 0L,
    val loading: Boolean = true,
    /**
     * The full row behind the open detail sheet, fetched by id when the sheet opens. The list
     * carries [RunSummary], which has no previews in it — those are most of a row's bytes and
     * loading them for every row to show one was the reason this screen slowed down as history
     * grew.
     */
    val detail: RunRecord? = null,
    /** Set when the reads behind this screen failed, so an empty list can say which kind it is. */
    val error: String? = null,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val clearHistory: ClearHistoryUseCase,
    private val settings: SettingsRepository,
    private val clipboard: ClipboardAccess,
    private val time: TimeSource,
    @DefaultDispatcher private val defaultDispatcher: CoroutineDispatcher,
) : ViewModel() {

    // Bounded by the retention cap; the DAO already returns newest first, so no re-sort here.
    private val detail = MutableStateFlow<RunRecord?>(null)

    // Fixed at construction: bounding the stats query by a moving "now" would restart it on
    // every emission, and the number it feeds only has to be right for the session.
    private val startOfToday = startOfDay(time.nowMillis())

    val uiState: StateFlow<HistoryUiState> = combine(
        history.observeRecent(),
        history.observeSince(startOfToday),
        settings.settings.map { it.historyEnabled },
        detail,
    ) { runs, today, enabled, openRun ->
        val now = time.nowMillis()
        HistoryUiState(
            days = groupByDay(runs, now),
            stats = statsFor(today),
            historyEnabled = enabled,
            nowMillis = now,
            loading = false,
            detail = openRun,
        )
    }
        // Above flowOn so the replacement state is built off the main thread with everything
        // else. A throwing flow is a finished flow, so this is the last thing Activity will
        // show until the screen is reopened — which is what the copy tells the user to do.
        .catch { emit(HistoryUiState(loading = false, error = LOAD_FAILED)) }
        // Grouping a thousand rows by calendar day allocates a ZonedDateTime per row. That is
        // cheap once and ruinous on the UI thread, which is where stateIn(viewModelScope)
        // collects unless it is told otherwise.
        .flowOn(defaultDispatcher)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = HistoryUiState(),
        )

    fun onOpenRun(id: String) {
        viewModelScope.launch { detail.value = history.get(id) }
    }

    fun onCloseRun() {
        detail.value = null
    }

    fun onClearHistory() {
        viewModelScope.launch { clearHistory() }
    }

    /**
     * Offered from the empty state so a user who turned history off by accident is not sent on a
     * hunt through Settings to undo it.
     */
    fun onEnableHistory() {
        viewModelScope.launch { settings.update { it.copy(historyEnabled = true) } }
    }

    fun onCopy(text: String) {
        clipboard.write(label = "ArcX", text = text)
    }
}

private const val STOP_TIMEOUT_MS = 5_000L

/**
 * Said instead of showing an empty list. Room and DataStore can both fail outright, and a flow
 * that throws simply stops — leaving Activity on its last emission, which on launch is nothing
 * at all. "Nothing here yet" then claims the user has never run a workflow, which may be a lie.
 *
 * Deliberately not the exception's own text: a `SQLiteDatabaseCorruptException` is not something
 * the person reading it can act on.
 */
private const val LOAD_FAILED =
    "ArcX couldn't read your run history from this device. Reopening the app usually fixes it."

private val WITHIN_YEAR = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val OTHER_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * Groups by local calendar day rather than by elapsed hours: "Yesterday" has to mean the day
 * before, not twenty-four hours ago, or a run at 01:00 lands under the wrong heading.
 */
internal fun groupByDay(runs: List<RunSummary>, nowMillis: Long): List<HistoryDay> {
    if (runs.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    return runs
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .map { (date, dayRuns) -> HistoryDay(dayLabel(date, today), dayRuns) }
}

/**
 * [today] is already only today's runs — the query is bounded by a timestamp rather than the
 * whole table being loaded and filtered, which is what this used to do.
 */
internal fun statsFor(today: List<RunOutcome>): RunStats {
    val durations = today
        .filter { it.status == RunStatus.SUCCESS }
        .map { it.durationMs }
        .sorted()

    return RunStats(
        runsToday = today.size,
        medianMs = if (durations.isEmpty()) 0L else durations[durations.size / 2],
        failedToday = today.count { it.status == RunStatus.FAILED },
    )
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.year == today.year -> date.format(WITHIN_YEAR)
    else -> date.format(OTHER_YEAR)
}
