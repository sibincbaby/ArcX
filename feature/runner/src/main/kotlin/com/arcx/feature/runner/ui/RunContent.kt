package com.arcx.feature.runner.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.format.formatDuration
import com.arcx.core.designsystem.component.ArcxAction
import com.arcx.core.designsystem.component.ArcxActionStyle
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.MarkdownText
import com.arcx.core.designsystem.component.StreamingIndicator
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.domain.execution.ExecutionState
import com.arcx.core.model.AiError
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
            execution is ExecutionState.Failed -> {
                // A switched-off workflow is the one failure retrying cannot fix: nothing changes
                // between one tap and the next, and the fix is on a screen that is not this one.
                // Offering the button anyway would be a dead end wearing an action's clothes.
                val retryable = execution.error !is AiError.Disabled
                ErrorCard(
                    title = execution.error.title(),
                    message = execution.error.body(),
                    actionLabel = "Retry".takeIf { retryable },
                    onAction = onRetry.takeIf { retryable },
                    modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Lg),
                )
            }

            text.isNullOrEmpty() && streaming -> WaitingRow("Working…")

            text.isNullOrEmpty() -> Text(
                text = "Stopped before anything came back.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Lg),
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
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
            )
        }

        Spacer(Modifier.height(Spacing.Md))
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
        modifier = modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StreamingIndicator()
        Spacer(Modifier.width(Spacing.Md))
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
            .padding(horizontal = Spacing.Gutter)
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
        Spacer(Modifier.width(Spacing.Md))
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
            // The only way out that is not an action, so it stays outlined rather than filled —
            // but in primary, because on a finished run it is the one thing left to tap.
            ArcxAction(
                onClick = onDone,
                label = "Done",
                contentColor = MaterialTheme.colorScheme.primary,
            )
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
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Lg),
    ) {
        // Selectable so a user can lift one paragraph out of a long answer.
        SelectionContainer { MarkdownText(text) }
        if (streaming) {
            Spacer(Modifier.height(Spacing.Sm))
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
            .padding(horizontal = Spacing.Gutter, vertical = Spacing.Xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
    ) {
        if (onReplace != null) {
            ArcxAction(
                onClick = onReplace,
                label = "Replace",
                icon = Icons.Outlined.FindReplace,
                style = ArcxActionStyle.Filled,
            )
            ArcxAction(onClick = onCopy, label = "Copy", icon = Icons.Outlined.ContentCopy)
        } else {
            ArcxAction(
                onClick = onCopy,
                label = "Copy",
                icon = Icons.Outlined.ContentCopy,
                style = ArcxActionStyle.Filled,
            )
        }
        ArcxAction(onClick = onShare, label = "Share", icon = Icons.Outlined.Share)
        ArcxAction(onClick = onRetry, icon = Icons.Outlined.Refresh, contentDescription = "Retry")
    }
}
