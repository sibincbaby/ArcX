package com.arcx.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.theme.Glass
import com.arcx.core.designsystem.theme.glassColor

/**
 * A pane of glass: translucent fill, a soft top-down sheen, and a rim that is brightest along the
 * top edge.
 *
 * One implementation for every floating surface in ArcX — the workflow panel, the compact picker,
 * and the popup an answer comes back in — because these are the surfaces the user sees over their
 * own app, and near-copies of a translucent card is exactly how the panel and the picker drifted
 * apart the first time. Material's bottom sheet draws its own container and will not take a
 * [Surface], so it assembles the same three parts out of [glassColor], [GlassEdge] and
 * [glassSheen] instead.
 *
 * All of it is Compose drawing. Nothing here asks the *window* for an effect; see [Glass] for why
 * that is load-bearing rather than a preference.
 */
@Composable
fun ArcxGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    /** The colour the surface would have if it were solid. Its alpha is the user's setting. */
    base: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    shadowElevation: Dp = 12.dp,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = shape,
        color = glassColor(base),
        // Spelled out because Surface only *infers* a content colour when the container is an exact
        // colour-scheme match, and a copy carrying an alpha is not one. Left to infer it falls
        // through to LocalContentColor, which is plain black at the root of the bubble's overlay
        // composition — every row label in dark theme would have gone black on a dark card.
        contentColor = MaterialTheme.colorScheme.onSurface,
        // Drawn by Surface *over* the fill, so the rim keeps its own brightness instead of being
        // muted by the glass the way a rim painted underneath would be.
        border = GlassEdge,
        shadowElevation = shadowElevation,
        // No tonalElevation. Material only applies a tonal overlay when the container colour is
        // exactly `surface`, and it can never do anything once the colour carries an alpha.
    ) {
        // A Box, rather than folding the sheen into the container colour, because it is a gradient
        // across the glass and not a second flat layer. Surface clips it to the corners for free.
        //
        // propagateMinConstraints, because Surface's own content Box does: without it this Box
        // would swallow the minimum width on the way through and every child that had been filling
        // the card — the panel's rows, the popup's buttons — would quietly shrink to its text.
        Box(Modifier.glassSheen(), propagateMinConstraints = true) { content() }
    }
}

/**
 * The lit rim. This is what actually sells the effect — more than the translucency does — because
 * it is the one part of the surface that does not change with whatever is behind it.
 *
 * A top-level value: both ends are constants, so there is exactly one of these for the process.
 */
val GlassEdge: BorderStroke = BorderStroke(
    Glass.EdgeWidth,
    Brush.verticalGradient(listOf(Glass.EdgeTop, Glass.EdgeBottom)),
)

/** The soft top-down sheen. Public so a surface Material owns can still wear it. */
fun Modifier.glassSheen(): Modifier = background(SheenBrush)

private val SheenBrush = Brush.verticalGradient(listOf(Glass.SheenTop, Glass.SheenBottom))
