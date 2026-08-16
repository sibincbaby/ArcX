package com.arcx.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.theme.MetaTextStyle

/**
 * The floating workflow panel — a small centred card of one-tap rows.
 *
 * It lives here rather than beside the bubble because two very different hosts draw it: the bubble's
 * overlay window, and the runner Activity when the user has asked for the compact list. They looked
 * alike once by coincidence and immediately drifted, which is the whole reason this file exists.
 *
 * Deliberately model-agnostic. The panel takes strings and colours, never a Workflow, so the
 * bubble — which is handed a different list from a different layer — can draw the same card.
 *
 * The glass it is drawn on is [ArcxGlassSurface], shared with the runner's answer popup rather
 * than restated here, so the two cannot disagree about how see-through the user asked for.
 */
@Composable
fun WorkflowPanelCard(
    title: String,
    modifier: Modifier = Modifier,
    /** Small mono note on the right of the title, e.g. what the panel is about to read. */
    meta: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    ArcxGlassSurface(
        modifier = modifier
            .widthIn(max = PANEL_MAX_WIDTH)
            .fillMaxWidth(),
        // The Panel tier, asked for rather than restated. This was the 24 of the 24/28 pair that
        // tier was drawn from, and it is the half of it nothing else in the app uses — so left
        // hardcoded it would be one number disagreeing with the file that names it.
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(vertical = 12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (meta != null) {
                    Text(
                        text = meta,
                        style = MetaTextStyle,
                        // `outline` until the surface became translucent, and it cannot survive
                        // that: measured against a white app behind the panel it falls to 2.4:1,
                        // and it was only 3.65:1 over the opaque card it was chosen for — under the
                        // 4.5 floor either way. It stays the quiet half of this row by being small
                        // and monospace, which is a difference that does not cost contrast.
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

/**
 * One row of the panel: icon, name, and the wiring underneath. The tap runs it — there is no
 * second confirmation anywhere in the product, and the panel is the fastest path in it.
 */
@Composable
fun WorkflowPanelRow(
    icon: String,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    container: Color = Color.Unspecified,
    content: Color = Color.Unspecified,
    /** Marks the row the panel expects to be tapped, and shows a play affordance on it. */
    highlighted: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .clip(RoundedCornerShape(14.dp))
            .then(
                if (highlighted) {
                    Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f))
                } else {
                    Modifier
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        WorkflowIcon(
            icon = icon,
            size = 36.dp,
            container = container.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.secondaryContainer,
            content = content.takeIf { it != Color.Unspecified }
                ?: MaterialTheme.colorScheme.onSecondaryContainer,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (highlighted) {
            Icon(
                imageVector = Icons.Outlined.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** The way out of the shortlist and into everything, pinned under a divider at the bottom. */
@Composable
fun WorkflowPanelFooter(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        HorizontalDivider(
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // One line of bodyMedium and 8dp of padding is a 36dp row, which is under the
                // 48dp minimum — and this is the only way out of the bubble's shortlist.
                .minimumInteractiveComponentSize()
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
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
