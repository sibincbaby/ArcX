package com.arcx.core.designsystem.theme

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.graphics.TransformOrigin

/**
 * One motion vocabulary for the whole app.
 *
 * It exists because the alternative is what ArcX had: navigation-compose's default 700ms
 * crossfade on every destination, which meant switching tabs and pushing a new screen looked
 * identical and both felt like wading. Measured on device, a tab switch took about half a
 * second of visible change to settle.
 *
 * Two ideas, and every transition in the app is one of them:
 *
 *  - **Lateral** — tab to tab. Nothing is above anything else, so nothing slides. The outgoing
 *    screen leaves fast and the incoming one fades up through it with a hair of scale.
 *  - **Hierarchical** — a push onto the stack. Direction is the whole point: the new screen
 *    rises, and on the way back it goes down where it came from.
 *
 * Durations are deliberately shorter than Material's reference values. Those are tuned for
 * screens a user visits once; the fastest paths in ArcX are ones people hit dozens of times a
 * day, and a transition you see that often has to get out of the way.
 */
object Motion {

    /** State changes inside a screen: a chip flipping, a row appearing. Barely a transition. */
    const val Fast: Int = 120

    /** The working duration — list changes, fades between peers. */
    const val Medium: Int = 190

    /** Movement with a direction to read. Only pushes and pops earn it. */
    const val Emphasis: Int = 260

    /**
     * How long the outgoing screen gets before the incoming one starts. Short on purpose: the
     * overlap is what makes a crossfade feel like mush, so there is almost none.
     */
    const val Handoff: Int = 70

    /** Comes in quickly and settles — Material's emphasized decelerate. */
    val Decelerate: Easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)

    /** Leaves slowly then goes — for anything on its way off screen. */
    val Accelerate: Easing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    val Standard: Easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

// ---------------------------------------------------------------- lateral: peer to peer

/**
 * The incoming peer. Scale starts just under 1 rather than at some dramatic value — enough that
 * the screen reads as arriving, not so much that it reads as zooming.
 */
fun lateralEnter(): EnterTransition =
    fadeIn(tween(Motion.Medium, delayMillis = Motion.Handoff, easing = Motion.Standard)) +
        scaleIn(
            initialScale = 0.96f,
            animationSpec = tween(Motion.Medium, delayMillis = Motion.Handoff, easing = Motion.Standard),
        )

/** The outgoing peer, gone before the incoming one starts. */
fun lateralExit(): ExitTransition =
    fadeOut(tween(Motion.Handoff, easing = Motion.Accelerate))

// ------------------------------------------------------------ hierarchical: push and pop

/** A screen arriving on top of the stack. */
fun pushEnter(): EnterTransition =
    slideInVertically(
        initialOffsetY = { it / 12 },
        animationSpec = tween(Motion.Emphasis, easing = Motion.Decelerate),
    ) + fadeIn(tween(Motion.Medium, easing = Motion.Standard))

/**
 * The screen being covered. It stays put and dims rather than moving: it is still there,
 * underneath, and anything that slides it would be claiming otherwise.
 */
fun pushExit(): ExitTransition =
    fadeOut(tween(Motion.Medium, easing = Motion.Standard)) +
        scaleOut(targetScale = 0.98f, animationSpec = tween(Motion.Emphasis, easing = Motion.Standard))

/** Coming back to what was underneath. */
fun popEnter(): EnterTransition =
    fadeIn(tween(Motion.Medium, easing = Motion.Standard)) +
        scaleIn(initialScale = 0.98f, animationSpec = tween(Motion.Emphasis, easing = Motion.Decelerate))

/** The screen leaving the top of the stack, back the way it came. */
fun popExit(): ExitTransition =
    slideOutVertically(
        targetOffsetY = { it / 12 },
        animationSpec = tween(Motion.Emphasis, easing = Motion.Accelerate),
    ) + fadeOut(tween(Motion.Medium, easing = Motion.Accelerate))

// -------------------------------------------------------------------- within one screen

/**
 * A step of a wizard arriving. [forward] is what makes the direction readable — the same
 * transition played backwards is what tells someone they went back rather than on.
 */
fun stepEnter(forward: Boolean): EnterTransition =
    slideInHorizontallyBy(if (forward) 1 else -1) +
        fadeIn(tween(Motion.Medium, delayMillis = Motion.Handoff, easing = Motion.Standard))

fun stepExit(forward: Boolean): ExitTransition =
    slideOutHorizontallyBy(if (forward) -1 else 1) +
        fadeOut(tween(Motion.Handoff, easing = Motion.Accelerate))

/** A panel or card appearing in place — the bubble's list, a result's actions. */
fun panelEnter(): EnterTransition =
    fadeIn(tween(Motion.Medium, easing = Motion.Standard)) +
        scaleIn(initialScale = 0.94f, animationSpec = tween(Motion.Emphasis, easing = Motion.Decelerate))

// ------------------------------------------------------------------ out of a docked edge

/**
 * A panel that belongs to something welded to a screen edge — today, the sidebar's strip.
 *
 * Separate from [panelEnter] because the two say different things. A panel appearing *in place*
 * has no origin to point at; this one does, and the entire job here is to make the card read as
 * the strip opening out rather than as an unrelated card materialising over the screen.
 *
 * The scale is what carries that, not the slide: pivoting on the docked edge means that edge
 * barely moves while the rest of the card grows away from it, which is what "opening out" looks
 * like. The slide is only a nudge — the same sixth [stepEnter] uses — because a longer flight
 * reads as the card arriving from somewhere off screen, which is the impression being fixed.
 *
 * [fromStart] is the docked edge: true when the strip is on the left of the window, false on the
 * right. A Boolean rather than the model's SidebarSide so this file stays free of the model.
 */
fun edgePanelEnter(fromStart: Boolean): EnterTransition =
    slideInHorizontally(
        initialOffsetX = { if (fromStart) -it / 6 else it / 6 },
        animationSpec = tween(Motion.Emphasis, easing = Motion.Decelerate),
    ) + scaleIn(
        initialScale = 0.9f,
        transformOrigin = edgeOrigin(fromStart),
        animationSpec = tween(Motion.Emphasis, easing = Motion.Decelerate),
    ) + fadeIn(tween(Motion.Medium, easing = Motion.Standard))

/**
 * The same move played backwards, so the card retracts into the edge it grew out of.
 *
 * One duration for all three parts, unlike every other exit here. A host of this transition has
 * to wait for the whole thing before it may take the surface away — the sidebar's window shrinks
 * back to a 48dp strip on the way out — and parts that finished at different times would leave it
 * waiting on a card that is already invisible.
 */
fun edgePanelExit(fromStart: Boolean): ExitTransition =
    slideOutHorizontally(
        targetOffsetX = { if (fromStart) -it / 6 else it / 6 },
        animationSpec = tween(Motion.Medium, easing = Motion.Accelerate),
    ) + scaleOut(
        targetScale = 0.9f,
        transformOrigin = edgeOrigin(fromStart),
        animationSpec = tween(Motion.Medium, easing = Motion.Accelerate),
    ) + fadeOut(tween(Motion.Medium, easing = Motion.Accelerate))

/**
 * Pivot on the docked edge, halfway down the card — which is the strip's own centre wherever the
 * host has anchored the card to it.
 */
private fun edgeOrigin(fromStart: Boolean) =
    TransformOrigin(pivotFractionX = if (fromStart) 0f else 1f, pivotFractionY = 0.5f)

private fun slideInHorizontallyBy(sign: Int) = slideInHorizontally(
    initialOffsetX = { sign * it / 6 },
    animationSpec = tween(Motion.Emphasis, easing = Motion.Decelerate),
)

private fun slideOutHorizontallyBy(sign: Int) = slideOutHorizontally(
    targetOffsetX = { sign * it / 6 },
    animationSpec = tween(Motion.Emphasis, easing = Motion.Accelerate),
)
