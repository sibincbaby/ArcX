package com.arcx.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Four corner radii, from what the app already draws.
 *
 * There were 14 distinct radii across roughly 50 sites — 2, 3, 5, 8, 9, 10, 12, 14, 16, 17, 18,
 * 20, 24, 28 — and they clustered into four groups with nothing meaningful between them. Each
 * tier below is the group's own value, not a new one:
 *
 *  - [Chip] — the 8/9/10 cluster (10 sites): filter pills, the install button, the surface chips.
 *  - [Control] — the 12/14 cluster (12 sites): every search field, the panel row, the result
 *    actions. 14 alone accounts for 10 of them.
 *  - [Card] — the 16/17/18/20 cluster, the biggest at 24 sites. 20 rather than the modal 16
 *    because a card two dp off a button is not a tier: the whole point of collapsing these is
 *    that a glance can tell a surface from a control, and 20 is what Settings' own `CardShape`
 *    already is.
 *  - [Panel] — the 24/28 pair: the floating workflow panel and the runner sheet.
 *
 * Exposed as raw [Dp] as well as shapes because a few sites need the number rather than the
 * shape — a dashed outline drawn with `drawRoundRect` cannot take a [RoundedCornerShape].
 */
object ArcXCorner {
    val Chip: Dp = 8.dp
    val Control: Dp = 14.dp
    val Card: Dp = 20.dp
    val Panel: Dp = 28.dp
}

/**
 * The four tiers mapped onto Material's five slots, wired into the theme by [ArcXTheme].
 *
 * **Read `MaterialTheme.shapes` rather than writing `RoundedCornerShape(n.dp)`.** Every radius in
 * the app was hardcoded and `MaterialTheme.shapes` was never set and never read, which is exactly
 * how one search field ended up at 14dp and its twin in the picker at 16dp. A component that asks
 * the theme cannot drift from its siblings.
 *
 * `small` and `extraSmall` share the chip tier: Material spends `extraSmall` on menus, snackbars
 * and text-field corners, all of which are the same size of thing as a pill. `extraLarge` keeps
 * Material's own 28dp, so bottom sheets and dialogs stay the shape the platform draws them.
 */
internal val ArcXShapes = Shapes(
    extraSmall = RoundedCornerShape(ArcXCorner.Chip),
    small = RoundedCornerShape(ArcXCorner.Chip),
    medium = RoundedCornerShape(ArcXCorner.Control),
    large = RoundedCornerShape(ArcXCorner.Card),
    extraLarge = RoundedCornerShape(ArcXCorner.Panel),
)
