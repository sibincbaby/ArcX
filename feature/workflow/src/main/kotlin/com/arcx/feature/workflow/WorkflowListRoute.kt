@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.arcx.feature.workflow

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.AddToHomeScreen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.CountPill
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.SectionLabel
import com.arcx.core.designsystem.component.WiringChips
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.component.shortLabel
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
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "title") {
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                )
            }

            item(key = "search") {
                SearchField(
                    query = state.query,
                    // Null, not 0, until the library has actually been read. With the old
                    // half-second crossfade nobody saw the first frames of this screen; now
                    // that it arrives promptly, "Search 0 workflows" is on screen long enough
                    // to read, and a confident wrong number is worse than no number.
                    total = state.total.takeIf { !state.loading },
                    onQueryChange = onQueryChange,
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
                state.loading -> Unit

                // Ahead of the empty states on purpose: a library that could not be read is also
                // an empty list, and "No workflows yet" would be a confident lie about it.
                state.error != null -> item(key = "error") {
                    ErrorCard(
                        title = "Your library could not be loaded",
                        message = state.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
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

@Composable
private fun SearchField(query: String, total: Int?, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = {
            Text(
                when (total) {
                    null -> "Search workflows"
                    1 -> "Search 1 workflow"
                    else -> "Search $total workflows"
                },
            )
        },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                }
            }
        },
        shape = RoundedCornerShape(14.dp),
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
    )
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
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
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

    Column(modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A floor, not a fixed height: at fontScale 2.0 the name and the wiring chips are
                // taller than 64dp together, and a fixed row cropped the chips off the bottom.
                .heightIn(min = 64.dp)
                .combinedClickable(onClick = onRun, onLongClick = onConfigure),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = workflow.category.tint()
            WorkflowIcon(
                icon = workflow.icon,
                size = 38.dp,
                container = tint.container,
                content = tint.content,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                WiringChips(
                    input = workflow.input.shortLabel,
                    output = workflow.output.shortLabel,
                )
            }

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
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
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
