package com.arcx.integration.entrypoints.overlay

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.PanelListMaxHeight
import com.arcx.core.designsystem.component.WorkflowPanelCard
import com.arcx.core.designsystem.component.WorkflowPanelEmpty
import com.arcx.core.designsystem.component.WorkflowPanelFooter
import com.arcx.core.designsystem.component.WorkflowPanelRow
import com.arcx.core.designsystem.component.shortLabel
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.panelEnter
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.Workflow
import com.arcx.integration.entrypoints.R

/** Matches the collapsed window size the touch handling assumes when snapping to an edge. */
internal val BubbleSize = 56.dp

/**
 * Material colours without [com.arcx.core.designsystem.theme.ArcXTheme].
 *
 * ArcXTheme casts the local view's context to an Activity so it can tint the status bar. In an
 * overlay that context is the Service, and the cast is a ClassCastException on first composition.
 * Everything else about the theme is reproduced here so the bubble still follows Material You.
 */
@Composable
internal fun OverlayTheme(content: @Composable () -> Unit) {
    val darkTheme = isSystemInDarkTheme()
    val context = LocalContext.current
    val colorScheme: ColorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}

/**
 * The collapsed handle. It has no click or drag modifier on purpose — the gesture is owned by the
 * hosting View, which is the only layer that can see raw screen coordinates while the window it is
 * attached to moves underneath the finger.
 */
@Composable
internal fun BubbleHandle(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(BubbleSize),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shadowElevation = 6.dp,
        tonalElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(text = "✦", style = MaterialTheme.typography.titleLarge)
        }
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
            .background(Color.Black.copy(alpha = 0.32f * scrimAlpha(appearing)))
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
