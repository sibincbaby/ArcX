package com.arcx.app.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.arcx.feature.discover.DiscoverRoute
import com.arcx.feature.history.HistoryRoute
import com.arcx.feature.home.HomeRoute
import com.arcx.feature.settings.SettingsRoute
import com.arcx.feature.workflow.WorkflowEditorRoute
import com.arcx.feature.workflow.WorkflowListRoute

/** The five tabs from the PRD, plus the editor which is pushed on top of them. */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    HOME("home", "Home", Icons.Outlined.Home),
    WORKFLOWS("workflows", "Workflows", Icons.Outlined.WorkspacePremium),
    DISCOVER("discover", "Discover", Icons.Outlined.Explore),
    HISTORY("history", "History", Icons.Outlined.History),
    SETTINGS("settings", "Settings", Icons.Outlined.Settings),
}

private const val EDITOR_ROUTE = "editor"
private const val EDITOR_ARG = "workflowId"

@Composable
fun ArcxNavHost(
    onRunWorkflow: (String) -> Unit,
    navController: NavHostController = rememberNavController(),
) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination

    // The bottom bar belongs to the tabs only; the editor is a full-screen push.
    val showBottomBar = TopLevelDestination.entries.any { destination ->
        currentRoute?.hierarchy?.any { it.route == destination.route } == true
    }

    fun openEditor(workflowId: String?) {
        navController.navigate(
            if (workflowId == null) EDITOR_ROUTE else "$EDITOR_ROUTE?$EDITOR_ARG=$workflowId",
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
                        val selected =
                            currentRoute?.hierarchy?.any { it.route == destination.route } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(destination.route) {
                                    // Standard tab behaviour: one entry per tab, state preserved.
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destination.icon, contentDescription = null) },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(TopLevelDestination.HOME.route) {
                HomeRoute(
                    onRunWorkflow = onRunWorkflow,
                    onEditWorkflow = ::openEditor,
                    onCreateWorkflow = { openEditor(null) },
                    onSeeAllWorkflows = {
                        navController.navigate(TopLevelDestination.WORKFLOWS.route)
                    },
                )
            }

            composable(TopLevelDestination.WORKFLOWS.route) {
                WorkflowListRoute(
                    onRunWorkflow = onRunWorkflow,
                    onEditWorkflow = ::openEditor,
                    onCreateWorkflow = { openEditor(null) },
                )
            }

            composable(TopLevelDestination.DISCOVER.route) {
                DiscoverRoute(onOpenWorkflow = ::openEditor)
            }

            composable(TopLevelDestination.HISTORY.route) {
                HistoryRoute(onRunWorkflow = onRunWorkflow)
            }

            composable(TopLevelDestination.SETTINGS.route) {
                SettingsRoute()
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
