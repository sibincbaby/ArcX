package com.arcx.feature.runner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.ArcxListRow
import com.arcx.core.designsystem.component.ArcxListRowIconSize
import com.arcx.core.designsystem.component.ArcxSearchField
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.LoadingState
import com.arcx.core.designsystem.component.PanelListMaxHeight
import com.arcx.core.designsystem.component.SectionLabel
import com.arcx.core.designsystem.component.WiringChips
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.component.WorkflowPanelCard
import com.arcx.core.designsystem.component.WorkflowPanelEmpty
import com.arcx.core.designsystem.component.WorkflowPanelRow
import com.arcx.core.designsystem.component.shortLabel
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.Workflow
import com.arcx.feature.runner.RunnerUiState

/**
 * Step one when the caller did not name a workflow. A tap runs immediately — an extra
 * confirmation would double the work of the fastest path in the product.
 */
@Composable
internal fun WorkflowPicker(
    state: RunnerUiState,
    onQueryChange: (String) -> Unit,
    onPick: (Workflow) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    if (compact) {
        CompactWorkflowPanel(state, onPick, modifier)
        return
    }

    Column(modifier.imePadding()) {
        Text(
            text = "Run a workflow",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(
                start = Spacing.Gutter,
                end = Spacing.Gutter,
                bottom = Spacing.Md,
            ),
        )

        // No spacer under it: the shared field carries its own gutter and vertical padding, and
        // adding one here is how this search box drifted from the other three in the first place.
        ArcxSearchField(query = state.query, onQueryChange = onQueryChange)

        when {
            // A spinner, not the streaming dots. The dots mean a model is producing tokens; this
            // wait is a Room read, and spending the one animation that means "provider" on a
            // database query is what makes a local read look like a network call.
            !state.catalogLoaded -> LoadingState()

            state.sections.isEmpty() && state.query.isNotBlank() -> EmptyState(
                icon = Icons.Outlined.Search,
                title = "Nothing matches",
                body = "Try a different word, or clear the search.",
            )

            state.sections.isEmpty() -> EmptyState(
                icon = Icons.Outlined.AutoAwesome,
                title = "No workflows yet",
                body = "Build one in ArcX and it will show up here.",
            )

            else -> LazyColumn(
                // Capped so the sheet stays an overlay on the app underneath rather than
                // creeping up into a full screen.
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
            ) {
                state.sections.forEach { section ->
                    section.title?.let { title ->
                        item(key = "header-$title") { SectionLabel(title) }
                    }
                    items(section.workflows, key = { it.id }) { workflow ->
                        WorkflowRow(workflow, onClick = { onPick(workflow) })
                    }
                }
            }
        }

        Spacer(Modifier.height(Spacing.Md))
    }
}

/** The picker's row is the app's row: [ArcxListRow] with the wiring chips as its subtitle. */
@Composable
private fun WorkflowRow(workflow: Workflow, onClick: () -> Unit) {
    ArcxListRow(
        title = workflow.name,
        leading = {
            val tint = workflow.category.tint()
            WorkflowIcon(
                icon = workflow.icon,
                size = ArcxListRowIconSize,
                container = tint.container,
                content = tint.content,
            )
        },
        subtitle = {
            WiringChips(
                input = workflow.input.shortLabel,
                output = workflow.output.shortLabel,
            )
        },
        trailing = {
            if (workflow.isPinned) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = "Pinned",
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(16.dp),
                )
            }
        },
        onClick = onClick,
    )
}

/**
 * The same card the bubble shows, for users who asked every entry point to look like it.
 *
 * No search box — not because a focused window cannot have one, but because the point of this
 * option is to match a panel that genuinely cannot. Sections collapse to one flat list for the
 * same reason: the bubble has no headers. [WorkflowPanelRow] rather than the [ArcxListRow] the
 * sheet above draws, for that same reason — this list has to be the bubble's list, not the app's.
 */
@Composable
private fun CompactWorkflowPanel(
    state: RunnerUiState,
    onPick: (Workflow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val workflows = state.sections.flatMap { it.workflows }
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        WorkflowPanelCard(title = "Run a workflow", modifier = Modifier.padding(Spacing.Md)) {
            when {
                !state.catalogLoaded -> LoadingState()

                workflows.isEmpty() -> WorkflowPanelEmpty(
                    "No workflows yet. Build one in ArcX and it will show up here.",
                )

                else -> LazyColumn(Modifier.heightIn(max = PanelListMaxHeight)) {
                    items(workflows, key = { it.id }) { workflow ->
                        WorkflowPanelRow(
                            icon = workflow.icon,
                            label = workflow.name,
                            subtitle = "${workflow.input.shortLabel} → ${workflow.output.shortLabel}",
                            container = workflow.category.tint().container,
                            content = workflow.category.tint().content,
                            onClick = { onPick(workflow) },
                        )
                    }
                }
            }
        }
    }
}
