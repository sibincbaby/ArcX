package com.arcx.core.designsystem.component

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.theme.Spacing

/**
 * The list row, for every list in ArcX.
 *
 * Five copies of this existed — recent runs on Home, the activity list, the library, the gallery
 * and the runner's picker — and they had drifted on every number that shows: 56, 60, 64, 64 and
 * an unconstrained height; 36, 36, 38, 38 and 38dp icons. Nobody chose those differences. They
 * are what happens when the sixth list is written by copying the second.
 *
 * Where they disagreed this takes the tallest and the largest, because those are the values the
 * two rows carrying the most content arrived at *after* being fixed for large font scales. A
 * shorter row would have to re-learn that.
 *
 * [subtitle] is a slot rather than a string because the five call sites need three different
 * things under the title: a plain sentence, a pair of wiring chips, and a meta line with an icon
 * spliced into it. Flattening those to `String` is what would push the third one back out into a
 * sixth private copy.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ArcxListRow(
    title: String,
    modifier: Modifier = Modifier,
    /** Usually a [WorkflowIcon] or a [TintedIcon] at [ArcxListRowIconSize]. */
    leading: (@Composable () -> Unit)? = null,
    subtitle: (@Composable ColumnScope.() -> Unit)? = null,
    /** Anything pinned to the right: a duration, a status, an icon button, an install pill. */
    trailing: (@Composable RowScope.() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    /** One tap runs, a long press configures — the two gestures every ArcX surface obeys. */
    onLongClick: (() -> Unit)? = null,
    /** False on the last row of a run, so a list never ends on a rule. */
    showDivider: Boolean = false,
    minHeight: Dp = ArcxListRowMinHeight,
    horizontalPadding: Dp = Spacing.Gutter,
) {
    // combinedClickable whether or not a long press is wired, rather than branching to plain
    // clickable: one node either way, so the two cases cannot end up with different semantics.
    // A row with only a long press would be a gesture nothing advertises, so it is not offered.
    val interactive = if (onClick == null) {
        Modifier
    } else {
        Modifier
            .minimumInteractiveComponentSize()
            .combinedClickable(onLongClick = onLongClick, role = Role.Button, onClick = onClick)
    }

    Column(modifier.padding(horizontal = horizontalPadding)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A floor, never a fixed height. Three of the five copies had to learn this the
                // same way: at fontScale 2.0 a title and its subtitle are taller than any of
                // 56/60/64dp, and a fixed row crops the subtitle off the bottom of exactly the
                // rows that have one.
                .heightIn(min = minHeight)
                .then(interactive),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(Spacing.Md))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(Spacing.Xs))
                    subtitle()
                }
            }
            if (trailing != null) {
                // Mirrors the leading gap, and conditional for the same reason it is: the title
                // column is weighted, so an unconditional spacer would take 12dp off it on every
                // row in all five lists — including the ones with nothing on the right, where it
                // would only move the ellipsis in for no gain.
                //
                // Without it the two collide. Measured on an S25 at fontScale 1.8 an Activity
                // title ended at x=772 and its "23.6s" began at x=781, and because trailing is
                // centred against the whole row it sits level with the gap between title and
                // subtitle — so 3dp of air reads as one run-together string rather than two
                // columns.
                Spacer(Modifier.width(Spacing.Md))
                trailing.invoke(this)
            }
        }
        if (showDivider) {
            // Inside the gutter padding, so the rule stops where the content does rather than
            // cutting the screen in half.
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
}

/**
 * 64dp, the tallest of the five. It is what the library and the gallery rows settled on once
 * their wiring chips stopped being clipped, and the two shorter rows carry the same anatomy —
 * since this is a minimum, the extra costs a short row nothing.
 */
val ArcxListRowMinHeight: Dp = 64.dp

/** 38dp, what three of the five drew. The other two were 36 and had no reason to be. */
val ArcxListRowIconSize: Dp = 38.dp
