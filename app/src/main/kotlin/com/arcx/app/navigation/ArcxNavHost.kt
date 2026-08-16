package com.arcx.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Timeline
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arcx.core.designsystem.component.LocalSnackbarHostState
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.lateralEnter
import com.arcx.core.designsystem.theme.lateralExit
import com.arcx.core.designsystem.theme.popEnter
import com.arcx.core.designsystem.theme.popExit
import com.arcx.core.designsystem.theme.pushEnter
import com.arcx.core.designsystem.theme.pushExit
import com.arcx.feature.discover.DiscoverRoute
import com.arcx.feature.history.HistoryRoute
import com.arcx.feature.home.HomeRoute
import com.arcx.feature.settings.SettingsRoute
import com.arcx.feature.workflow.WorkflowEditorRoute
import com.arcx.feature.workflow.WorkflowListRoute

/**
 * Four tabs, not five.
 *
 * Settings used to hold a fifth of the bar for a destination people visit about twice a year,
 * which cost every other tab width and forced the labels to compete with Workflows/Discover —
 * two names for what reads as the same thing. It is now a gear in the Home header and a
 * full-screen push, and the bar reads Home · Library · Discover · Activity.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** Filled while selected; the pill indicator alone is not enough of a state change. */
    val selectedIcon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Bolt, Icons.Filled.Bolt),
    LIBRARY("library", "Library", Icons.Outlined.GridView, Icons.Filled.GridView),
    DISCOVER("discover", "Discover", Icons.Outlined.Explore, Icons.Filled.Explore),
    ACTIVITY("activity", "Activity", Icons.Outlined.Timeline, Icons.Filled.Timeline),
}

/** True when both ends of the move are tabs, which is the only case with no hierarchy in it. */
private val AnimatedContentTransitionScope<NavBackStackEntry>.isLateral: Boolean
    get() = initialState.isTopLevel && targetState.isTopLevel

private val NavBackStackEntry.isTopLevel: Boolean
    get() = TopLevelDestination.entries.any { it.route == destination.route }

private const val EDITOR_ROUTE = "editor"
private const val EDITOR_ARG = "workflowId"
private const val SETTINGS_ROUTE = "settings"
private const val SETTINGS_ARG = "entryPoints"

@Composable
fun ArcxNavHost(
    onRunWorkflow: (String) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // The bottom bar belongs to the tabs only; the editor and settings are full-screen pushes.
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentRoute?.hierarchy?.any { it.route == destination.route } == true
    }

    fun openEditor(workflowId: String?) {
        navController.navigate(
            if (workflowId == null) EDITOR_ROUTE else "$EDITOR_ROUTE?$EDITOR_ARG=$workflowId",
        )
    }

    fun openTab(destination: TopLevelDestination) {
        navController.navigate(destination.route) {
            // Standard tab behaviour: one entry per tab, state preserved.
            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The app's one snackbar, owned here rather than by any screen. It sits above the bottom bar
    // and survives a tab switch, which is what makes it usable for an undo — a host remembered
    // inside a tab is destroyed the moment that tab leaves composition, taking the offer with it.
    val snackbars = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbars) },
        bottomBar = {
            // Animated rather than simply absent: the bar used to vanish and reappear in one
            // frame, which read as the screen flinching every time the editor opened.
            AnimatedVisibility(
                visible = showBottomBar,
                enter = slideInVertically(tween(Motion.Emphasis, easing = Motion.Decelerate)) { it } +
                    fadeIn(tween(Motion.Medium)),
                exit = slideOutVertically(tween(Motion.Medium, easing = Motion.Accelerate)) { it } +
                    fadeOut(tween(Motion.Fast)),
            ) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected =
                            currentRoute?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { openTab(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.icon
                                    },
                                    contentDescription = null,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        // Provided around the NavHost rather than around the Scaffold: every screen that
        // could have something to say is inside it, and the bottom bar is not.
        CompositionLocalProvider(LocalSnackbarHostState provides snackbars) {
            NavHost(
                navController = navController,
                startDestination = TopLevelDestination.HOME.route,
                // padding offsets the content off the bar; consumeWindowInsets tells everything
                // below that those insets are now spent. Without the second half every tab's own
                // Scaffold reads the navigation-bar inset again and pads for it a second time —
                // measured as a 48dp dead band above the bar, with the last row cut through its
                // glyphs. Both are needed: consuming alone would let content run under the bar.
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding),
                // Two transitions, picked by what the move actually means. Tabs are peers and get a
                // fast fade-through; anything pushed on top of them rises and comes back down. The
                // library's own default is one 700ms crossfade for both, which made a push look
                // exactly like a tab switch and made every one of them feel slow.
                enterTransition = { if (isLateral) lateralEnter() else pushEnter() },
                exitTransition = { if (isLateral) lateralExit() else pushExit() },
                popEnterTransition = { if (isLateral) lateralEnter() else popEnter() },
                popExitTransition = { if (isLateral) lateralExit() else popExit() },
            ) {
                composable(TopLevelDestination.HOME.route) {
                    HomeRoute(
                        onRunWorkflow = onRunWorkflow,
                        onEditWorkflow = ::openEditor,
                        onCreateWorkflow = { openEditor(null) },
                        onSeeAllWorkflows = { openTab(TopLevelDestination.LIBRARY) },
                        onSeeActivity = { openTab(TopLevelDestination.ACTIVITY) },
                        onOpenSettings = { navController.navigate(SETTINGS_ROUTE) },
                        onOpenEntryPoints = {
                            navController.navigate("$SETTINGS_ROUTE?$SETTINGS_ARG=true")
                        },
                    )
                }

                composable(TopLevelDestination.LIBRARY.route) {
                    WorkflowListRoute(
                        onRunWorkflow = onRunWorkflow,
                        onEditWorkflow = ::openEditor,
                        onCreateWorkflow = { openEditor(null) },
                    )
                }

                composable(TopLevelDestination.DISCOVER.route) {
                    DiscoverRoute(onOpenWorkflow = ::openEditor)
                }

                composable(TopLevelDestination.ACTIVITY.route) {
                    HistoryRoute(onRunWorkflow = onRunWorkflow)
                }

                composable(
                    route = "$SETTINGS_ROUTE?$SETTINGS_ARG={$SETTINGS_ARG}",
                    arguments = listOf(
                        navArgument(SETTINGS_ARG) {
                            type = NavType.BoolType
                            defaultValue = false
                        },
                    ),
                ) { entry ->
                    SettingsRoute(
                        onBack = { navController.popBackStack() },
                        startAtEntryPoints = entry.arguments?.getBoolean(SETTINGS_ARG) == true,
                    )
                }

                // One destination with an optional argument: navigating to "editor" with no
                // argument falls through to the default and means "create a new workflow".
                composable(
                    route = "$EDITOR_ROUTE?$EDITOR_ARG={$EDITOR_ARG}",
                    arguments = listOf(
                        navArgument(EDITOR_ARG) {
                            type = NavType.StringType
                            nullable = true
                            defaultValue = null
                        },
                    ),
                ) { entry ->
                    WorkflowEditorRoute(
                        workflowId = entry.arguments?.getString(EDITOR_ARG),
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
        }
    }
}
