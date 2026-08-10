package com.arcx.feature.runner.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.MarkdownText
import com.arcx.core.designsystem.component.StreamingIndicator
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.model.Workflow

/**
 * Step two: the answer itself. Shared by the sheet and the popup card, which differ only in
 * how much vertical room they are willing to take.
 */
@Composable
internal fun RunContent(
    workflow: Workflow,
    execution: ExecutionState,
    stopped: Boolean,
    notice: String?,
    compact: Boolean,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = when (execution) {
        is ExecutionState.Streaming -> execution.text
        is ExecutionState.Success -> execution.text
        else -> null
    }
    val streaming = !stopped && execution !is ExecutionState.Success && execution !is ExecutionState.Failed
    val finished = stopped || execution is ExecutionState.Success

    Column(modifier.fillMaxWidth()) {
        Header(workflow, streaming, onStop, onDone)

        when {
            execution is ExecutionState.Failed -> ErrorCard(
                title = execution.error.title(),
                message = execution.error.body(),
                actionLabel = "Retry",
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = 20.dp),
            )

            text.isNullOrEmpty() && streaming -> WaitingRow("Working…")

            text.isNullOrEmpty() -> Text(
                text = "Stopped before anything came back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            else -> Answer(text, streaming, compact)
        }

        if (finished) {
            (execution as? ExecutionState.Success)?.let { success ->
                Text(
                    text = "Finished in ${formatDuration(success.durationMs)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (stopped && !text.isNullOrEmpty()) {
                Text(
                    text = "Stopped",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            ActionRow(onCopy = onCopy, onShare = onShare, onRetry = onRetry)
        }

        if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

/** Shown before the first token arrives, and while a run-by-id is still resolving. */
@Composable
internal fun WaitingRow(label: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.padding(horizontal = 20.dp, vertical = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreamingIndicator()
        Spacer(Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Header(
    workflow: Workflow,
    streaming: Boolean,
    onStop: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WorkflowIcon(workflow.icon, size = 36.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            text = workflow.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (streaming) {
            TextButton(onClick = onStop) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        } else {
            TextButton(onClick = onDone) { Text("Done") }
        }
    }
}

@Composable
private fun Answer(text: String, streaming: Boolean, compact: Boolean) {
    val scrollState = rememberScrollState()
    var follow by remember { mutableStateOf(true) }

    val followConnection = remember {
        object : NestedScrollConnection {
            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // A drag back up means the user is reading something they have already been
                // sent; nothing is more annoying than being yanked to the bottom mid-sentence.
                // Following resumes only once they return to the bottom themselves.
                if (consumed.y > 0f) follow = false
                else if (consumed.y < 0f && !scrollState.canScrollForward) follow = true
                return Offset.Zero
            }
        }
    }

    // maxValue only settles once the new tokens have been laid out, so reacting to the layout
    // is accurate where reacting to the text itself would scroll to a stale extent.
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.maxValue }.collect { max -> if (follow) scrollState.scrollTo(max) }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = if (compact) 280.dp else 420.dp)
            .nestedScroll(followConnection)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp),
    ) {
        // Selectable so a user can lift one paragraph out of a long answer.
        SelectionContainer { MarkdownText(text) }
        if (streaming) {
            Spacer(Modifier.height(8.dp))
            StreamingIndicator()
        }
    }
}

/**
 * Copy, Share and Retry are always here whatever the workflow's configured output is, so no
 * result is ever a dead end.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(onCopy: () -> Unit, onShare: () -> Unit, onRetry: () -> Unit) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ActionChip("Copy", Icons.Outlined.ContentCopy, onCopy)
        ActionChip("Share", Icons.Outlined.Share, onShare)
        ActionChip("Retry", Icons.Outlined.Refresh, onRetry)
    }
}

@Composable
private fun ActionChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(AssistChipDefaults.IconSize),
            )
        },
    )
}
