package com.arcx.feature.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.MarkdownText
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.model.RunRecord
import com.arcx.core.model.RunStatus
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val CardShape = RoundedCornerShape(20.dp)

@Composable
fun HistoryRoute(
    onRunWorkflow: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        state = state,
        onRunWorkflow = onRunWorkflow,
        onClearHistory = viewModel::onClearHistory,
        onEnableHistory = viewModel::onEnableHistory,
        onCopy = viewModel::onCopy,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistoryScreen(
    state: HistoryUiState,
    onRunWorkflow: (String) -> Unit,
    onClearHistory: () -> Unit,
    onEnableHistory: () -> Unit,
    onCopy: (String) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<RunRecord?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Clear history") },
                            enabled = state.days.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                confirmClear = true
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        when {
            // Being switched off is a different situation from having nothing to show, and
            // saying so beats an empty list that looks like a bug.
            !state.historyEnabled -> EmptyState(
                icon = Icons.Outlined.History,
                title = "History is off",
                body = "ArcX is not recording runs. You can turn it back on here, or any " +
                    "time under Settings › Privacy.",
                modifier = Modifier.padding(padding),
                actionLabel = "Turn history on",
                onAction = onEnableHistory,
            )

            !state.loading && state.days.isEmpty() -> EmptyState(
                icon = Icons.Outlined.History,
                title = "Nothing here yet",
                body = "Runs you fire from anywhere on your phone show up here, newest first.",
                modifier = Modifier.padding(padding),
            )

            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                state.days.forEach { day ->
                    item(key = "day-${day.label}") { SectionHeader(day.label) }
                    items(day.runs, key = { it.id }) { run ->
                        RunRow(
                            run = run,
                            nowMillis = state.nowMillis,
                            onClick = { detail = run },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("Every recorded run is deleted from this device. Your workflows stay.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onClearHistory()
                }) { Text("Clear") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("Cancel") }
            },
        )
    }

    detail?.let { run ->
        RunDetailSheet(
            run = run,
            onDismiss = { detail = null },
            onCopy = onCopy,
            onRunAgain = {
                detail = null
                onRunWorkflow(run.workflowId)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunRow(
    run: RunRecord,
    nowMillis: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkflowIcon(run.workflowIcon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = run.workflowName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${relativeTime(run.startedAt, nowMillis)} · ${formatDuration(run.durationMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${run.providerLabel} · ${run.model}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // An icon, not a thumbnail: the list must never decode a screen capture per row.
            if (run.screenshotPath != null) {
                Icon(
                    imageVector = Icons.Outlined.Image,
                    contentDescription = "Ran on a screenshot",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(10.dp))
            }
            StatusIcon(run.status)
        }
    }
}

@Composable
private fun StatusIcon(status: RunStatus, modifier: Modifier = Modifier) {
    Icon(
        imageVector = when (status) {
            RunStatus.SUCCESS -> Icons.Outlined.CheckCircle
            RunStatus.FAILED -> Icons.Outlined.ErrorOutline
            RunStatus.CANCELLED -> Icons.Outlined.Cancel
        },
        contentDescription = statusLabel(status),
        modifier = modifier.size(20.dp),
        tint = statusColor(status),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RunDetailSheet(
    run: RunRecord,
    onDismiss: () -> Unit,
    onCopy: (String) -> Unit,
    onRunAgain: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkflowIcon(run.workflowIcon)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(run.workflowName, style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "${statusLabel(run.status)} · ${formatDuration(run.durationMs)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor(run.status),
                    )
                }
                StatusIcon(run.status)
            }

            Spacer(Modifier.height(12.dp))
            Text(
                text = "${absoluteTime(run.startedAt)} · ${run.providerLabel} · ${run.model}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Above the input preview because for these runs the picture *is* what was sent;
            // the text below it is only the caption the runner recorded alongside it.
            run.screenshotPath?.let { path ->
                Spacer(Modifier.height(20.dp))
                Text("Screenshot", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                RunScreenshot(path)
            }

            Spacer(Modifier.height(20.dp))
            Text("Input", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    text = run.inputPreview.ifBlank { "(no input)" },
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            val error = run.error
            if (error != null) {
                ErrorCard(title = "Run failed", message = error)
            } else {
                Text("Output", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                MarkdownText(run.outputPreview.orEmpty().ifBlank { "(no output recorded)" })
            }

            Spacer(Modifier.height(24.dp))
            HorizontalDivider()
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = { onCopy(run.outputPreview ?: run.error.orEmpty()) },
                    enabled = !run.outputPreview.isNullOrBlank() || run.error != null,
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Copy")
                }
                Button(onClick = onRunAgain) {
                    Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Run again")
                }
            }
        }
    }
}

@Composable
private fun statusColor(status: RunStatus): Color = when (status) {
    RunStatus.SUCCESS -> MaterialTheme.colorScheme.primary
    RunStatus.FAILED -> MaterialTheme.colorScheme.error
    RunStatus.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun statusLabel(status: RunStatus): String = when (status) {
    RunStatus.SUCCESS -> "Succeeded"
    RunStatus.FAILED -> "Failed"
    RunStatus.CANCELLED -> "Cancelled"
}

private val CLOCK = DateTimeFormatter.ofPattern("HH:mm")
private val STAMP = DateTimeFormatter.ofPattern("d MMM, HH:mm")

/**
 * Relative only while it stays useful. Past a day "37h ago" is harder to place than a clock
 * time under a date heading, which the list already provides.
 */
internal fun relativeTime(startedAt: Long, nowMillis: Long): String {
    val elapsed = nowMillis - startedAt
    return when {
        elapsed < 0 -> absoluteClock(startedAt)
        elapsed < 60_000L -> "Just now"
        elapsed < 3_600_000L -> "${elapsed / 60_000L}m ago"
        elapsed < 86_400_000L -> "${elapsed / 3_600_000L}h ago"
        else -> absoluteClock(startedAt)
    }
}

internal fun formatDuration(durationMs: Long): String = when {
    durationMs < 1_000L -> "${durationMs}ms"
    durationMs < 60_000L -> String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)
    else -> "${durationMs / 60_000L}m ${(durationMs % 60_000L) / 1_000L}s"
}

private fun absoluteClock(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(CLOCK)

private fun absoluteTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(STAMP)
