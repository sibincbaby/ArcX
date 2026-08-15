package com.arcx.core.designsystem.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Every gap in ArcX, on a 4dp grid.
 *
 * It is a consolidation, not a redesign. Counted across the app there were 557 `.dp` literals
 * over 49 distinct values, 41% of them off any grid — 9, 11, 13, 15, 17, 19, 21 all appear, and
 * three screens padded a card by three different amounts for the same reason. Nothing about that
 * was decided; it accumulated. The seven steps below cover it: every value in real use is within
 * 2dp of one of them, so adopting the scale re-lays nothing out in a way a user would notice.
 *
 * Stroke widths and divider thicknesses are deliberately absent. A 1dp border is not spacing and
 * putting it on this scale would make it four times too thick.
 */
object Spacing {

    /** Inside a chip, and between an icon and the word it belongs to. */
    val Xs: Dp = 4.dp

    /** Between siblings in a row of the same thing — filter pills, grid cells, action buttons. */
    val Sm: Dp = 8.dp

    /** The working gap: an icon and its label, a title and the line under it. */
    val Md: Dp = 12.dp

    /** Padding inside a card, a sheet or any surface that holds more than one line. */
    val Lg: Dp = 16.dp

    /** The screen gutter — see [Gutter]. */
    val Xl: Dp = 20.dp

    /** Between one section of a screen and the next. */
    val Xxl: Dp = 24.dp

    /** Around something that is alone on the screen: an empty state, an onboarding step. */
    val Xxxl: Dp = 32.dp

    /**
     * The distance from the edge of the screen to anything on it.
     *
     * Four gutters were in use: 20dp in Home, Library, Discover, Activity, the runner and the
     * editor; 16dp in Settings; 15dp in one Entry points row; 32dp in the settings note and
     * onboarding. 20 wins on two counts. It is the majority — six of the eight top-level screens,
     * and 69 of the 557 measured `.dp` values against 58 for 16dp, most of which are card
     * *interiors* rather than gutters. And it is the cheaper convergence: moving Settings from 16
     * to 20 is one file, while moving the six screens to 16 shifts every list row 4dp left of the
     * icon column the tiles and the panel already align to.
     */
    val Gutter: Dp = Xl
}
