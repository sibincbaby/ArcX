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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.PanelListMaxHeight
import com.arcx.core.designsystem.component.WorkflowPanelCard
import com.arcx.core.designsystem.component.WorkflowPanelEmpty
import com.arcx.core.designsystem.component.WorkflowPanelFooter
import com.arcx.core.designsystem.component.WorkflowPanelRow
import com.arcx.core.designsystem.component.shortLabel
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.PanelScrim
import com.arcx.core.designsystem.theme.edgePanelEnter
import com.arcx.core.designsystem.theme.edgePanelExit
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.SidebarSide
import com.arcx.core.model.Workflow
import com.arcx.integration.entrypoints.R
import kotlin.math.roundToInt

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
 *
 * The card grows out of the strip and retracts back into it, which is why this composable needs
 * [side] and [verticalPercent]: they are the only description of where the strip was, and the
 * window it is drawn in is the whole screen by the time this runs.
 *
 * Animating here rather than inside `WorkflowPanelCard` is deliberate. That card is shared with
 * the runner's compact picker, which is a dialog in the middle of a screen with no edge to come
 * out of; a transition inside it would be inherited by a host it makes no sense for.
 */
@Composable
internal fun BubblePanel(
    workflows: List<Workflow>,
    side: SidebarSide,
    /** Where the strip's centre sits down the edge, 0..1 — see [StripAnchor]. */
    verticalPercent: Float,
    onWorkflow: (Workflow) -> Unit,
    onMore: () -> Unit,
    /**
     * Called once the exit transition has finished, not when the user asked to dismiss. The host
     * shrinks its window back to the strip in this callback, so calling it on the tap would clip
     * the retraction at its first frame.
     */
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fromStart = side == SidebarSide.LEFT

    // Two pieces of state, not one. [leaving] is the request — a tap outside — and [visible] is
    // what the transition is actually doing about it. The gap between them is the whole point.
    var leaving by remember { mutableStateOf(false) }
    val visible = remember { MutableTransitionState(false) }

    // Driven from an effect rather than set during composition, which is the obvious way to write
    // it and is wrong here: an effect runs *after* the first composition, so the scrim's
    // animateFloatAsState below gets to read `false` once and has something to animate from.
    // Assigned inline it would already be true on the composition that initialises the animation,
    // and the scrim would snap straight to full black — which is exactly what it used to do.
    LaunchedEffect(leaving) { visible.targetState = !leaving }

    // The window may not shrink until the card has finished retracting into the strip. Same shape
    // as RunnerHost, which holds its transparent Activity open until the bottom sheet has hidden,
    // for the same reason: a surface that goes away with its window blinks out instead of leaving.
    // Guarded on [leaving] because the opening transition is also "not idle, not current" for its
    // first frames, and would otherwise close the panel the moment it appeared.
    LaunchedEffect(leaving, visible.isIdle, visible.currentState) {
        if (leaving && visible.isIdle && !visible.currentState) onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PanelScrim.copy(alpha = PanelScrim.alpha * scrimAlpha(visible.targetState)))
            .clickable(
                indication = null,
                interactionSource = null,
                onClick = { leaving = true },
            ),
        contentAlignment = remember(verticalPercent) { StripAnchor(verticalPercent) },
    ) {
        AnimatedVisibility(
            visibleState = visible,
            enter = edgePanelEnter(fromStart),
            exit = edgePanelExit(fromStart),
        ) {
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
                                // AnimatedVisibility keeps its content composed and clickable for
                                // the length of the exit, so without this a tap that lands on the
                                // card while it is sliding away would still fire a run.
                                onClick = { if (!leaving) onWorkflow(workflow) },
                            )
                        }
                    }
                }

                WorkflowPanelFooter(
                    label = stringResource(R.string.arcx_bubble_more),
                    onClick = { if (!leaving) onMore() },
                )
            }
        }
    }
}

/**
 * Puts the card's vertical centre on the strip's, so the panel opens out of the part of the edge
 * the user actually touched.
 *
 * The strip is settable anywhere from 0 to 100% down the edge, and a permanently centred card only
 * lines up with it at 50% — everywhere else the continuity the transition is building is thrown
 * away by where it lands. The cost is that a strip near either end would hang the card off screen,
 * so the same clamp `BubbleOverlay.applyCollapsedGeometry` uses on the strip is applied here to the
 * card: it goes as far towards the strip as it can and no further.
 *
 * The percent is of [space], which is the expanded window, while the strip's is of the display —
 * and those are not the same box. Measured on device: the strip window sat at 533..871 of a 2340px
 * display while the expanded window's frame was 85..2298, so at 30% the card's centre landed at 749
 * against the strip's 702. 47px of disagreement, and the strip is 338px tall, so the anchor is
 * still well inside it. Not worth reaching for insets on a LAYOUT_NO_LIMITS overlay window to fix.
 */
private class StripAnchor(private val percent: Float) : Alignment {
    override fun align(size: IntSize, space: IntSize, layoutDirection: LayoutDirection): IntOffset {
        val y = (space.height * percent - size.height / 2f).roundToInt()
            .coerceIn(0, (space.height - size.height).coerceAtLeast(0))
        return IntOffset((space.width - size.width) / 2, y)
    }
}

/**
 * The scrim comes up and goes down with the card rather than snapping to full black behind it.
 *
 * Asymmetric like every other pair in Motion.kt: it arrives on the card's timing and leaves faster,
 * because the way out is the half the user is waiting on.
 */
@Composable
private fun scrimAlpha(visible: Boolean): Float {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = if (visible) {
            tween(Motion.Emphasis, easing = Motion.Standard)
        } else {
            tween(Motion.Medium, easing = Motion.Accelerate)
        },
        label = "bubble-scrim",
    )
    return alpha
}
