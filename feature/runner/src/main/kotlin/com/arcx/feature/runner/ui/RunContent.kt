package com.arcx.feature.runner.ui

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FindReplace
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.clip
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
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.model.Workflow

/**
 * Step two: the answer itself. Shared by the sheet and the popup card, which differ only in
 * how much vertical room they are willing to take.
 *
 * [onReplace] is null when the text ArcX was given did not come out of an editable field, which
 * is the only thing that makes replacing it possible. When it is available it leads the action
 * row: someone who selected a sentence and asked for a rewrite wants the rewrite in place, and
 * making that the obvious button is the difference between the sheet being a step and a stop.
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
    onReplace: (() -> Unit)? = null,
) {
    val text = when (execution) {
        is ExecutionState.Streaming -> execution.text
        is ExecutionState.Success -> execution.text
        else -> null
    }
    val streaming = !stopped && execution !is ExecutionState.Success && execution !is ExecutionState.Failed
    val finished = stopped || execution is ExecutionState.Success

    Column(modifier.fillMaxWidth()) {
        Header(
            workflow = workflow,
            meta = metaLine(workflow, execution, stopped),
            streaming = streaming,
            onStop = onStop,
            onDone = onDone,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)

        when {
            execution is ExecutionState.Failed -> ErrorCard(
                title = execution.error.title(),
                message = execution.error.body(),
                actionLabel = "Retry",
                onAction = onRetry,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            text.isNullOrEmpty() && streaming -> WaitingRow("Working…")

            text.isNullOrEmpty() -> Text(
                text = "Stopped before anything came back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )

            else -> Answer(text, streaming, compact)
        }

        // Grown in rather than switched on: the row lands under a still-settling answer, and
        // appearing in one frame under a moving thing is what makes a sheet feel jumpy.
        AnimatedVisibility(
            visible = finished,
            enter = expandVertically(tween(Motion.Emphasis, easing = Motion.Decelerate)) +
                fadeIn(tween(Motion.Medium, delayMillis = Motion.Handoff)),
            exit = fadeOut(tween(Motion.Fast)),
        ) {
            ActionRow(
                onReplace = onReplace,
                onCopy = onCopy,
                onShare = onShare,
                onRetry = onRetry,
            )
        }

        if (notice != null) {
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

/**
 * What the engine did, in mono, under the name: which model, how long. Present only once it is
 * actually known — a placeholder here would be a claim about a run that has not happened.
 */
private fun metaLine(workflow: Workflow, execution: ExecutionState, stopped: Boolean): String? {
    val parts = buildList {
        workflow.model?.takeIf { it.isNotBlank() }?.let(::add)
        (execution as? ExecutionState.Success)?.let { add(formatDuration(it.durationMs)) }
        if (stopped) add("stopped")
    }
    return parts.joinToString(" · ").takeIf { it.isNotBlank() }
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
    meta: String?,
    streaming: Boolean,
    onStop: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val tint = workflow.category.tint()
        WorkflowIcon(
            icon = workflow.icon,
            size = 38.dp,
            container = tint.container,
            content = tint.content,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = workflow.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (meta != null) {
                Text(
                    text = meta,
                    style = MetaTextStyle,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        if (streaming) {
            TextButton(onClick = onStop) {
                Icon(Icons.Outlined.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Stop")
            }
        } else {
            DoneButton(onClick = onDone)
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
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
 * result is ever a dead end. Replace joins them, first and filled, whenever the text can go
 * back where it came from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ActionRow(
    onReplace: (() -> Unit)?,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onRetry: () -> Unit,
) {
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onReplace != null) {
            FilledAction("Replace", Icons.Outlined.FindReplace, onReplace)
            OutlinedAction("Copy", Icons.Outlined.ContentCopy, onCopy)
        } else {
            FilledAction("Copy", Icons.Outlined.ContentCopy, onCopy)
        }
        OutlinedAction("Share", Icons.Outlined.Share, onShare)
        OutlinedAction(icon = Icons.Outlined.Refresh, onClick = onRetry, contentDescription = "Retry")
    }
}

@Composable
private fun FilledAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun OutlinedAction(
    label: String? = null,
    icon: ImageVector? = null,
    onClick: () -> Unit,
    contentDescription: String? = null,
) {
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .height(38.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick, onClickLabel = contentDescription)
            .padding(horizontal = if (label == null) 11.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(17.dp),
            )
        }
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The header's bare "Done": smaller than the action row, and the only way out that is not an action. */
@Composable
private fun DoneButton(onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Done",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
