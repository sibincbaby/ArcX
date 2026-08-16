package com.arcx.core.designsystem.component

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The app's one way of saying something short back to the user.
 *
 * Feedback used to be three unrelated things: a [SnackbarHostState] that existed only inside
 * Discover, Toasts in the runner, and silence everywhere else — so starring, pinning and deleting
 * a workflow all happened without a word. One host, hoisted to the Scaffold that owns every tab,
 * means a screen only has to have something to say.
 *
 * A CompositionLocal rather than a parameter because the alternative is threading a host state
 * through every route, every section function and every row that might one day want to speak —
 * and a row three levels into a LazyColumn is exactly where the silence was.
 *
 * The default is a real but *unhosted* state: nothing draws it, so a composable used outside the
 * providing Scaffold — a preview, a test, RunnerActivity's separate composition — shows nothing
 * rather than crashing. It is deliberately not `error("no provider")`; feedback failing loudly is
 * worse than feedback not appearing.
 *
 * Static because the provided value is remembered once and never changes. `compositionLocalOf`
 * would make every reader observable for a change that cannot happen.
 */
val LocalSnackbarHostState = staticCompositionLocalOf { SnackbarHostState() }
