package com.arcx.integration.entrypoints.overlay

import android.os.Build
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
import androidx.compose.foundation.lazy.items
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
import com.arcx.core.designsystem.component.WorkflowPanelRow
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.32f))
            .clickable(
                indication = null,
                interactionSource = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        WorkflowPanelCard(
            title = stringResource(R.string.arcx_bubble_title),
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
                    items(workflows, key = { it.id }) { workflow ->
                        WorkflowPanelRow(
                            emoji = workflow.icon,
                            label = workflow.name,
                            onClick = { onWorkflow(workflow) },
                        )
                    }
                }
            }

            WorkflowPanelRow(
                emoji = "⋯",
                label = stringResource(R.string.arcx_bubble_more),
                onClick = onMore,
            )
        }
    }
}
