package com.arcx.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcx.core.common.time.TimeSource
import com.arcx.core.domain.capture.ClipboardAccess
import com.arcx.core.domain.repository.HistoryRepository
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.usecase.ClearHistoryUseCase
import com.arcx.core.model.RunRecord
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val runs: List<RunRecord>,
)

data class HistoryUiState(
    val days: List<HistoryDay> = emptyList(),
    val historyEnabled: Boolean = true,
    /** Pinned at emission time so every row's "3h ago" is measured against the same instant. */
    val nowMillis: Long = 0L,
    val loading: Boolean = true,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val clearHistory: ClearHistoryUseCase,
    private val settings: SettingsRepository,
    private val clipboard: ClipboardAccess,
    private val time: TimeSource,
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        history.observeAll().map { runs -> runs.sortedByDescending { it.startedAt } },
        settings.settings.map { it.historyEnabled },
    ) { runs, enabled ->
        val now = time.nowMillis()
        HistoryUiState(
            days = groupByDay(runs, now),
            historyEnabled = enabled,
            nowMillis = now,
            loading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = HistoryUiState(),
    )

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

private val WITHIN_YEAR = DateTimeFormatter.ofPattern("EEEE, d MMMM")
private val OTHER_YEAR = DateTimeFormatter.ofPattern("d MMMM yyyy")

/**
 * Groups by local calendar day rather than by elapsed hours: "Yesterday" has to mean the day
 * before, not twenty-four hours ago, or a run at 01:00 lands under the wrong heading.
 */
internal fun groupByDay(runs: List<RunRecord>, nowMillis: Long): List<HistoryDay> {
    if (runs.isEmpty()) return emptyList()
    val zone = ZoneId.systemDefault()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()

    return runs
        .groupBy { Instant.ofEpochMilli(it.startedAt).atZone(zone).toLocalDate() }
        .map { (date, dayRuns) -> HistoryDay(dayLabel(date, today), dayRuns) }
}

private fun dayLabel(date: LocalDate, today: LocalDate): String = when {
    date == today -> "Today"
    date == today.minusDays(1) -> "Yesterday"
    date.year == today.year -> date.format(WITHIN_YEAR)
    else -> date.format(OTHER_YEAR)
}
