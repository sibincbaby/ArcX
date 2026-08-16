@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.workflow

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.ArcxListRow
import com.arcx.core.designsystem.component.ArcxListRowIconSize
import com.arcx.core.designsystem.component.ArcxSearchField
import com.arcx.core.designsystem.component.CountPill
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.LoadingState
import com.arcx.core.designsystem.component.LocalSnackbarHostState
import com.arcx.core.designsystem.component.SectionLabel
import com.arcx.core.designsystem.component.WiringChips
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.component.shortLabel
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory

/**
 * The user's library. Everything they have built or installed, searchable, filterable, and one
 * tap from running — long press to configure, the same two gestures as every other surface.
 *
 * "Add to home screen" goes out through the SystemSurfaces port rather than a callback: it needs
 * a ShortcutManager, which does not belong in a feature module, and the previous hook for it was
 * never passed by any caller and fired on the wrong gesture besides.
 */
@Composable
fun WorkflowListRoute(
    onRunWorkflow: (String) -> Unit,
    onEditWorkflow: (String) -> Unit,
    onCreateWorkflow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WorkflowListViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val edit by rememberUpdatedState(onEditWorkflow)
    LaunchedEffect(viewModel) {
        viewModel.editCopyRequests.collect { edit(it) }
    }

    // Deleting is the one destructive thing this screen does, so it is the one thing that gets an
    // undo. The snackbar's own result decides the outcome rather than a timer running alongside
    // it: ActionPerformed means the user tapped Undo, anything else — timeout, a swipe, or this
    // effect being cancelled because they left the tab — means the delete stands. `collect`
    // suspends inside `showSnackbar`, so two quick deletes queue up two offers instead of racing,
    // and the workflow being held is released the moment its snackbar is gone.
    val snackbars = LocalSnackbarHostState.current
    LaunchedEffect(viewModel, snackbars) {
        viewModel.deletedWorkflows.collect { workflow ->
            val result = snackbars.showSnackbar(
                message = "Deleted ${workflow.name}",
                actionLabel = "Undo",
                // Ten seconds rather than four. An offer the user has to notice, read and reach
                // for is not the same as a message they only have to read.
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) viewModel.undoDelete(workflow)
        }
    }

    WorkflowListScreen(
        state = state,
        modifier = modifier,
        onQueryChange = viewModel::setQuery,
        onCategoryChange = viewModel::setCategory,
        onSortChange = viewModel::setSort,
        onClearFilters = viewModel::clearFilters,
        onRun = { onRunWorkflow(it.id) },
        onEdit = { onEditWorkflow(it.id) },
        onCreate = onCreateWorkflow,
        onToggleFavorite = viewModel::toggleFavorite,
        onDuplicate = { workflow -> viewModel.duplicate(workflow, openEditor = workflow.isBuiltIn) },
        onTogglePin = viewModel::togglePinned,
        onAddToHomeScreen = viewModel::addToHomeScreen,
        onDelete = viewModel::delete,
    )
}

@Composable
private fun WorkflowListScreen(
    state: WorkflowListUiState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (WorkflowCategory?) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
    onClearFilters: () -> Unit,
    onRun: (Workflow) -> Unit,
    onEdit: (Workflow) -> Unit,
    onCreate: () -> Unit,
    onToggleFavorite: (Workflow) -> Unit,
    onDuplicate: (Workflow) -> Unit,
    onTogglePin: (Workflow) -> Unit,
    onAddToHomeScreen: (Workflow) -> Unit,
    onDelete: (Workflow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Workflow?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = "New workflow")
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            // Off the scale on purpose: this is the FAB's clearance, not a gap between two
            // things, so it is sized by what floats over the last row rather than by the grid.
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(
                        start = Spacing.Gutter,
                        end = Spacing.Gutter,
                        top = Spacing.Md,
                    ),
                )
            }

            item(key = "search") {
                ArcxSearchField(
                    query = state.query,
                    onQueryChange = onQueryChange,
                    // Null, not 0, until the library has actually been read. With the old
                    // half-second crossfade nobody saw the first frames of this screen; now
                    // that it arrives promptly, "Search 0 workflows" is on screen long enough
                    // to read, and a confident wrong number is worse than no number.
                    placeholder = when (val total = state.total.takeIf { !state.loading }) {
                        null -> "Search workflows"
                        1 -> "Search 1 workflow"
                        else -> "Search $total workflows"
                    },
                )
            }

            item(key = "filters") {
                FilterRow(
                    state = state,
                    onCategoryChange = onCategoryChange,
                    onSortChange = onSortChange,
                )
            }

            when {
                // A spinner rather than nothing. This branch drew a blank screen, which is what
                // an empty library looks like — so the first frames of a slow read said "you
                // have nothing" about a library that was still arriving.
                state.loading -> item(key = "loading") { LoadingState() }

                // Ahead of the empty states on purpose: a library that could not be read is also
                // an empty list, and "No workflows yet" would be a confident lie about it.
                state.error != null -> item(key = "error") {
                    ErrorCard(
                        title = "Your library could not be loaded",
                        message = state.error,
                        modifier = Modifier.padding(
                            horizontal = Spacing.Gutter,
                            vertical = Spacing.Lg,
                        ),
                    )
                }

                state.libraryIsEmpty -> item(key = "empty") {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "No workflows yet",
                        body = "Build an AI action once — a name, a prompt, where the text comes " +
                            "from — then fire it from anywhere.",
                        actionLabel = "Create one",
                        onAction = onCreate,
                    )
                }

                state.isEmpty -> item(key = "no-matches") {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "Nothing here",
                        body = "No workflow matches that. Try another category, or a different word.",
                        actionLabel = "Clear filters",
                        onAction = onClearFilters,
                    )
                }

                else -> librarySections(
                    sections = state.sections,
                    onRun = onRun,
                    onEdit = onEdit,
                    onToggleFavorite = onToggleFavorite,
                    onDuplicate = onDuplicate,
                    onTogglePin = onTogglePin,
                    onAddToHomeScreen = onAddToHomeScreen,
                    canPinShortcut = state.canPinShortcut,
                    onDelete = { pendingDelete = it },
                )
            }
        }
    }

    pendingDelete?.let { workflow ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Delete ${workflow.name}?") },
            text = { Text("This removes the workflow from your library. Runs already in your history are kept.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDelete(workflow)
                        pendingDelete = null
                    },
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
            },
        )
    }
}

private fun LazyListScope.librarySections(
    sections: List<LibrarySection>,
    onRun: (Workflow) -> Unit,
    onEdit: (Workflow) -> Unit,
    onToggleFavorite: (Workflow) -> Unit,
    onDuplicate: (Workflow) -> Unit,
    onTogglePin: (Workflow) -> Unit,
    onAddToHomeScreen: (Workflow) -> Unit,
    canPinShortcut: Boolean,
    onDelete: (Workflow) -> Unit,
) {
    sections.forEach { section ->
        section.title?.let { title ->
            item(key = "section-$title") { SectionLabel(title) }
        }
        itemsIndexed(
            items = section.workflows,
            key = { _, workflow -> "${section.title}-${workflow.id}" },
        ) { index, workflow ->
            WorkflowRow(
                // Starring a workflow moves it between sections; without this the row
                // teleports and the eye has to find it again.
                modifier = Modifier.animateItem(),
                workflow = workflow,
                showDivider = index < section.workflows.lastIndex,
                onRun = { onRun(workflow) },
                onConfigure = { onEdit(workflow) },
                onToggleFavorite = { onToggleFavorite(workflow) },
                onDuplicate = { onDuplicate(workflow) },
                onTogglePin = { onTogglePin(workflow) },
                onAddToHomeScreen = { onAddToHomeScreen(workflow) },
                canPinShortcut = canPinShortcut,
                onDelete = { onDelete(workflow) },
            )
        }
    }
}

/** Counts on the pills so "is there anything under Dev" is answered before the tap. */
@Composable
private fun FilterRow(
    state: WorkflowListUiState,
    onCategoryChange: (WorkflowCategory?) -> Unit,
    onSortChange: (LibrarySort) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            // 2dp rather than a scale step: the search field above already carries its own
            // vertical padding, and anything more here reads as a gap between two screens.
            .padding(horizontal = Spacing.Gutter, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CountPill(
            label = "All",
            count = state.total.takeIf { !state.loading },
            selected = state.category == null,
            onClick = { onCategoryChange(null) },
        )
        state.counts.forEach { entry ->
            CountPill(
                label = entry.label,
                count = entry.count,
                selected = state.category == entry.category,
                onClick = {
                    onCategoryChange(entry.category.takeIf { it != state.category })
                },
            )
        }
        SortButton(sort = state.sort, onSortChange = onSortChange)
    }
}

@Composable
private fun SortButton(sort: LibrarySort, onSortChange: (LibrarySort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = Icons.Outlined.Sort,
                contentDescription = "Sort — currently ${sort.label}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            LibrarySort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    trailingIcon = {
                        if (option == sort) {
                            Icon(Icons.Filled.Star, contentDescription = "Selected", Modifier.size(14.dp))
                        }
                    },
                    onClick = {
                        open = false
                        onSortChange(option)
                    },
                )
            }
        }
    }
}

/**
 * The library's row is the app's row: [ArcxListRow] with the wiring chips as its subtitle, the
 * star and the overflow menu as its trailing content. One tap runs, a long press configures.
 */
@Composable
private fun WorkflowRow(
    modifier: Modifier = Modifier,
    workflow: Workflow,
    showDivider: Boolean,
    onRun: () -> Unit,
    onConfigure: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePin: () -> Unit,
    onAddToHomeScreen: () -> Unit,
    canPinShortcut: Boolean,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    ArcxListRow(
        title = workflow.name,
        modifier = modifier,
        leading = {
            val tint = workflow.category.tint()
            WorkflowIcon(
                icon = workflow.icon,
                size = ArcxListRowIconSize,
                container = tint.container,
                content = tint.content,
            )
        },
        subtitle = {
            WiringChips(
                input = workflow.input.shortLabel,
                output = workflow.output.shortLabel,
            )
        },
        trailing = {
            IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (workflow.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (workflow.isFavorite) {
                        "Remove from favourites"
                    } else {
                        "Add to favourites"
                    },
                    modifier = Modifier.size(19.dp),
                    tint = if (workflow.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        modifier = Modifier.size(19.dp),
                        tint = MaterialTheme.colorScheme.outline,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    MenuRow("Run", Icons.Outlined.PlayArrow) { menuOpen = false; onRun() }

                    // Built-ins are shipped with the app and stay pristine, so editing one is
                    // really "make it yours first".
                    if (workflow.isBuiltIn) {
                        MenuRow("Duplicate to edit", Icons.Outlined.ContentCopy) {
                            menuOpen = false
                            onDuplicate()
                        }
                    } else {
                        MenuRow("Edit", Icons.Outlined.Edit) { menuOpen = false; onConfigure() }
                        MenuRow("Duplicate", Icons.Outlined.ContentCopy) {
                            menuOpen = false
                            onDuplicate()
                        }
                    }

                    // This said "Pin to home screen" and did nothing of the kind — it only sets
                    // isPinned, which floats the workflow to the top of ArcX's own lists. The
                    // home-screen icon is the separate item below.
                    MenuRow(
                        label = if (workflow.isPinned) "Unpin from top" else "Pin to top",
                        icon = Icons.Outlined.PushPin,
                    ) { menuOpen = false; onTogglePin() }

                    if (canPinShortcut) {
                        MenuRow("Add to home screen", Icons.Outlined.AddToHomeScreen) {
                            menuOpen = false
                            onAddToHomeScreen()
                        }
                    }

                    if (!workflow.isBuiltIn) {
                        MenuRow("Delete", Icons.Outlined.Delete) { menuOpen = false; onDelete() }
                    }
                }
            }
        },
        onClick = onRun,
        onLongClick = onConfigure,
        showDivider = showDivider,
    )
}

@Composable
private fun MenuRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
