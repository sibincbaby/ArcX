@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.workflow

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.model.Workflow
import com.arcx.core.model.WorkflowCategory

/**
 * The user's library. Everything they have built or installed, searchable, filterable, and
 * one tap from running.
 *
 * [onPinToHomeScreen] is a hook for the launcher shortcut, which needs a `ShortcutManager` and
 * an Activity — neither of which belongs in a feature module. It fires only when the workflow
 * is being pinned, never when it is being unpinned.
 */
@Composable
fun WorkflowListRoute(
    onRunWorkflow: (String) -> Unit,
    onEditWorkflow: (String) -> Unit,
    onCreateWorkflow: () -> Unit,
    modifier: Modifier = Modifier,
    onPinToHomeScreen: (String) -> Unit = {},
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
        onClearFilters = viewModel::clearFilters,
        onRun = { onRunWorkflow(it.id) },
        onEdit = { onEditWorkflow(it.id) },
        onCreate = onCreateWorkflow,
        onToggleFavorite = viewModel::toggleFavorite,
        onDuplicate = { workflow -> viewModel.duplicate(workflow, openEditor = workflow.isBuiltIn) },
        onTogglePin = { workflow ->
            viewModel.togglePinned(workflow)
            if (!workflow.isPinned) onPinToHomeScreen(workflow.id)
        },
        onDelete = viewModel::delete,
    )
}

@Composable
private fun WorkflowListScreen(
    state: WorkflowListUiState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (WorkflowCategory?) -> Unit,
    onClearFilters: () -> Unit,
    onRun: (Workflow) -> Unit,
    onEdit: (Workflow) -> Unit,
    onCreate: () -> Unit,
    onToggleFavorite: (Workflow) -> Unit,
    onDuplicate: (Workflow) -> Unit,
    onTogglePin: (Workflow) -> Unit,
    onDelete: (Workflow) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingDelete by remember { mutableStateOf<Workflow?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Workflows") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreate) {
                Icon(Icons.Filled.Add, contentDescription = "New workflow")
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search workflows") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            CategoryFilters(
                selected = state.category,
                onSelect = onCategoryChange,
            )

            when {
                state.loading -> Spacer(Modifier.fillMaxSize())

                state.workflows.isEmpty() && state.libraryIsEmpty -> EmptyState(
                    icon = Icons.Outlined.AutoAwesome,
                    title = "No workflows yet",
                    body = "Build an AI action once — a name, a prompt, where the text comes from — then fire it from anywhere.",
                    actionLabel = "Create one",
                    onAction = onCreate,
                )

                state.workflows.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Nothing here",
                    body = "No workflow matches that. Try another category, or a different word.",
                    actionLabel = "Clear filters",
                    onAction = onClearFilters,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.workflows, key = { it.id }) { workflow ->
                        WorkflowRow(
                            workflow = workflow,
                            onRun = { onRun(workflow) },
                            onEdit = { onEdit(workflow) },
                            onToggleFavorite = { onToggleFavorite(workflow) },
                            onDuplicate = { onDuplicate(workflow) },
                            onTogglePin = { onTogglePin(workflow) },
                            onDelete = { pendingDelete = workflow },
                        )
                    }
                }
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

@Composable
private fun CategoryFilters(
    selected: WorkflowCategory?,
    onSelect: (WorkflowCategory?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        WorkflowCategory.entries.forEach { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(if (selected == category) null else category) },
                label = { Text(category.label) },
            )
        }
    }
}

@Composable
private fun WorkflowRow(
    workflow: Workflow,
    onRun: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(
        onClick = onRun,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 12.dp, end = 4.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkflowIcon(
                emoji = workflow.icon,
                container = MaterialTheme.colorScheme.surface,
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 14.dp),
            ) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = workflowSubtitle(workflow.category, workflow.input, workflow.output),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (workflow.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                    contentDescription = if (workflow.isFavorite) "Remove from favourites" else "Add to favourites",
                    tint = if (workflow.isFavorite) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
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
                        MenuRow("Edit", Icons.Outlined.Edit) { menuOpen = false; onEdit() }
                        MenuRow("Duplicate", Icons.Outlined.ContentCopy) {
                            menuOpen = false
                            onDuplicate()
                        }
                    }

                    MenuRow(
                        label = if (workflow.isPinned) "Remove from home screen" else "Pin to home screen",
                        icon = Icons.Outlined.PushPin,
                    ) { menuOpen = false; onTogglePin() }

                    if (!workflow.isBuiltIn) {
                        MenuRow("Delete", Icons.Outlined.Delete) { menuOpen = false; onDelete() }
                    }
                }
            }
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
