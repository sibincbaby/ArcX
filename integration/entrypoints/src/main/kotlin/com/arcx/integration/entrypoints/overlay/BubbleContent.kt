package com.arcx.integration.entrypoints.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.PanelListMaxHeight
import com.arcx.core.designsystem.component.WorkflowPanelCard
import com.arcx.core.designsystem.component.WorkflowPanelEmpty
import com.arcx.core.designsystem.component.WorkflowPanelFooter
import com.arcx.core.designsystem.component.WorkflowPanelRow
import com.arcx.core.designsystem.component.shortLabel
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.PanelScrim
import com.arcx.core.designsystem.theme.panelEnter
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.SidebarSide
import com.arcx.core.model.Workflow
import com.arcx.integration.entrypoints.R

/**
 * How wide the collapsed overlay window is, whatever width the strip is drawn at.
 *
 * These are two different numbers on purpose. Android delivers a whole gesture to whichever window
 * was under the ACTION_DOWN, so the window has to be wide enough to put a thumb on before any of
 * the swipe handling downstream gets a chance to run — at the 6dp default the drawn strip is a
 * hairline, and a window that size is effectively unswipeable. So the window stays a full 48dp, the
 * platform's minimum touch target, and the strip is drawn against its docked edge inside it.
 */
internal val SidebarTouchWidth = 48.dp

/**
 * The collapsed strip: a thin bar welded to one screen edge.
 *
 * It has no click or drag modifier on purpose — the gesture is owned by the hosting View, which is
 * the only layer that can see raw screen coordinates and the only one that can tell an inward swipe
 * from a stray drag before Compose has decided the touch belongs to it.
 *
 * It fills the window and aligns itself to the docked edge rather than sizing the window to the
 * drawing, so the gap between [SidebarTouchWidth] and [widthDp] stays touchable but invisible.
 */
@Composable
internal fun SidebarStrip(
    side: SidebarSide,
    widthDp: Int,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val docked = side == SidebarSide.LEFT
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = if (docked) Alignment.CenterStart else Alignment.CenterEnd,
    ) {
        Surface(
            modifier = Modifier
                .width(widthDp.dp)
                .fillMaxHeight()
                // The alpha is on the drawing and nowhere else. Putting it on the window would fade
                // the panel with it, and the touch region has no opacity to fade in the first
                // place — a transparent overlay still takes every touch that lands on it.
                .alpha(opacity),
            // Square against the screen edge, rounded on the side the user can see, so a strip
            // reads as part of the edge rather than as a floating tab that has drifted into it.
            shape = if (docked) {
                RoundedCornerShape(topEndPercent = 50, bottomEndPercent = 50)
            } else {
                RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50)
            },
            color = MaterialTheme.colorScheme.primary,
            shadowElevation = 2.dp,
            content = {},
        )
    }
}

/**
 * The expanded panel: the user's pinned and favourite workflows, plus a way through to the full
 * picker. It fills the window, because when expanded the window fills the screen — the empty area
 * is the dismiss target.
 */
@Composable
internal fun BubblePanel(
    workflows: List<Workflow>,
    onWorkflow: (Workflow) -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Animated on appear only. Collapsing tears the expanded window down in the same frame, so
    // an exit transition would be cut off half-played — worse than none.
    val appearing = remember { MutableTransitionState(false) }.apply { targetState = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PanelScrim.copy(alpha = PanelScrim.alpha * scrimAlpha(appearing)))
            .clickable(
                indication = null,
                interactionSource = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedVisibility(visibleState = appearing, enter = panelEnter()) {
            WorkflowPanelCard(
                title = stringResource(R.string.arcx_bubble_title),
                // Says what the panel is about to feed a workflow, because from here the user has
                // not selected anything — the run resolves its text from the clipboard or the
                // screen behind the bubble, and that is worth knowing before the tap, not after.
                meta = stringResource(R.string.arcx_bubble_source),
                // The card is a click target of its own, so taps inside it must not reach the scrim
                // behind and collapse the bubble mid-selection.
                modifier = Modifier
                    .padding(24.dp)
                    .clickable(indication = null, interactionSource = null, onClick = {}),
            ) {
                if (workflows.isEmpty()) {
                    WorkflowPanelEmpty(stringResource(R.string.arcx_bubble_empty))
                } else {
                    LazyColumn(Modifier.heightIn(max = PanelListMaxHeight)) {
                        itemsIndexed(workflows, key = { _, it -> it.id }) { index, workflow ->
                            WorkflowPanelRow(
                                icon = workflow.icon,
                                label = workflow.name,
                                subtitle = "${workflow.input.shortLabel} → ${workflow.output.shortLabel}",
                                container = workflow.category.tint().container,
                                content = workflow.category.tint().content,
                                // The top row is what a tap without aiming will hit, so it is the
                                // one that shows a play affordance.
                                highlighted = index == 0,
                                onClick = { onWorkflow(workflow) },
                            )
                        }
                    }
                }

                WorkflowPanelFooter(
                    label = stringResource(R.string.arcx_bubble_more),
                    onClick = onMore,
                )
            }
        }
    }
}

/** The scrim fades with the card rather than snapping to full black behind it. */
@Composable
private fun scrimAlpha(state: MutableTransitionState<Boolean>): Float {
    val alpha by animateFloatAsState(
        targetValue = if (state.targetState) 1f else 0f,
        animationSpec = tween(Motion.Emphasis, easing = Motion.Standard),
        label = "bubble-scrim",
    )
    return alpha
}
