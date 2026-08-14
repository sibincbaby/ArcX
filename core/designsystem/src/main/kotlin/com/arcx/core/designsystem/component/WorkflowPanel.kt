package com.arcx.core.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * The floating workflow panel — a small centred card of one-tap rows.
 *
 * It lives here rather than beside the bubble because two very different hosts draw it: the bubble's
 * overlay window, and the runner Activity when the user has asked for the compact list. They looked
 * alike once by coincidence and immediately drifted, which is the whole reason this file exists.
 *
 * Deliberately model-agnostic. :core:designsystem does not depend on :core:model, and a panel that
 * only knows how to render a Workflow could not be reused by the bubble, which is handed a
 * different list from a different layer.
 */
@Composable
fun WorkflowPanelCard(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier
            .widthIn(max = PANEL_MAX_WIDTH)
            .fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 12.dp,
        tonalElevation = 3.dp,
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            content()
        }
    }
}

/** One row of the panel: emoji, name, nothing else. The tap runs it. */
@Composable
fun WorkflowPanelRow(
    emoji: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        WorkflowIcon(emoji = emoji, size = 40.dp)
        Spacer(Modifier.width(14.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clip(RoundedCornerShape(4.dp)),
        )
    }
}

/** Message shown in place of the rows when there is nothing to list. */
@Composable
fun WorkflowPanelEmpty(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 12.dp),
    )
}

/** Wide enough for a long workflow name, narrow enough to still read as a panel, not a screen. */
private val PANEL_MAX_WIDTH = 340.dp

/** Keeps a long list scrolling inside the card instead of growing it into a full screen. */
val PanelListMaxHeight = 320.dp
