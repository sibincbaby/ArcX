package com.arcx.core.designsystem.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.Spacing

/**
 * A paragraph the user has to ask for.
 *
 * Settings had roughly six and a half thousand characters of prose interleaved with its controls,
 * and none of it was filler — it is where Android's 200dp gesture-exclusion cap, Samsung's Edge
 * panel owning the right edge and the 48dp touch band the sidebar always keeps are written down.
 * All of it was also being read, or skipped past, by every user on every visit to change one
 * setting. The split this component exists to draw is between a sentence that changes what someone
 * would *choose* — which stays on the screen — and the paragraph explaining why the app behaves
 * that way, which goes behind this.
 *
 * The label is the question, not a verb: "Why it stops at 200dp", not "More". Someone deciding
 * whether to spend a tap has to be able to tell from the label whether the answer is the one they
 * are missing, and "Show more" tells them nothing. It is drawn in `primary` so it reads as
 * something to press rather than as the first line of the note.
 *
 * `remember`, deliberately not `rememberSaveable`: an expansion is a question asked once, so
 * leaving the screen forgets it. Saving it would mean a user who once wondered about the 200dp cap
 * met that paragraph on every visit forever, which is the state this component was built to leave.
 *
 * Accessibility, matching what the rest of Settings already does:
 *  - [Role.Button] on the header, because a bare `clickable` announces nothing.
 *  - `stateDescription`, so the header says "expanded" or "collapsed" rather than leaving the
 *    chevron's rotation as the only channel — the same reason [ArcxPill] states `selected` and
 *    Entry points' rows state live and off.
 *  - The body is inside [AnimatedVisibility], so while collapsed it is not composed and is
 *    genuinely absent from the accessibility tree. Drawing it at zero height or zero alpha would
 *    leave a screen reader walking every hidden paragraph on the screen.
 *
 * Motion comes from [Motion] rather than from a Material default, for the reason `Motion.kt`
 * gives: the defaults are tuned for screens visited once, and this is a control on the settings
 * path people use most.
 */
@Composable
fun ExpandableNote(
    label: String,
    body: String,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    // Fast, because the chevron is only confirming a tap that already happened; the body's own
    // arrival is what the eye follows.
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = tween(Motion.Fast, easing = Motion.Standard),
        label = "ExpandableNote chevron",
    )

    Column(modifier) {
        Row(
            modifier = Modifier
                // The floor, not a height: the header is one line of labelLarge and would
                // otherwise draw a target well under 48dp. Same pairing every shared primitive
                // uses — the minimum first, the role on the clickable.
                .minimumInteractiveComponentSize()
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    role = Role.Button,
                    onClickLabel = if (expanded) "Collapse" else "Expand",
                ) { expanded = !expanded }
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" }
                .padding(end = Spacing.Xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(Spacing.Xs))
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                // The row says the state in words; a described chevron would say it twice.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .size(ChevronSize)
                    .rotate(rotation),
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(Motion.Emphasis, easing = Motion.Decelerate)) +
                fadeIn(tween(Motion.Medium, easing = Motion.Standard)),
            exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Accelerate)) +
                fadeOut(tween(Motion.Fast, easing = Motion.Accelerate)),
        ) {
            // The same type and colour the visible notes around it use, so what the tap reveals is
            // the note that used to be there rather than a different kind of text.
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.Sm),
            )
        }
    }
}

/** 18dp, the size the rest of the app draws an icon beside labelLarge text. */
private val ChevronSize = 18.dp
