package com.arcx.feature.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.model.Workflow

/** Rounded-20 throughout; the pinned tiles reuse it so both shapes read as one family. */
private val CardShape = RoundedCornerShape(20.dp)

/** The sections Home can show, in display order. */
private enum class HomeSection { PINNED, FAVOURITES, RECENT, SUGGESTIONS }

@Composable
fun HomeRoute(
    onRunWorkflow: (String) -> Unit,
    onEditWorkflow: (String) -> Unit,
    onCreateWorkflow: () -> Unit,
    onSeeAllWorkflows: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRunWorkflow = onRunWorkflow,
        onEditWorkflow = onEditWorkflow,
        onCreateWorkflow = onCreateWorkflow,
        onSeeAllWorkflows = onSeeAllWorkflows,
        onToggleFavorite = viewModel::onToggleFavorite,
        onTogglePinned = viewModel::onTogglePinned,
    )
}

@Composable
private fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onRunWorkflow: (String) -> Unit,
    onEditWorkflow: (String) -> Unit,
    onCreateWorkflow: () -> Unit,
    onSeeAllWorkflows: () -> Unit,
    onToggleFavorite: (Workflow) -> Unit,
    onTogglePinned: (Workflow) -> Unit,
) {
    var sheetTarget by remember { mutableStateOf<Workflow?>(null) }

    // "See all" hangs off whichever section happens to be first, so the full library stays one
    // tap away no matter which slices are populated.
    val seeAllOn = when {
        state.pinned.isNotEmpty() -> HomeSection.PINNED
        state.favorites.isNotEmpty() -> HomeSection.FAVOURITES
        state.recent.isNotEmpty() -> HomeSection.RECENT
        else -> HomeSection.SUGGESTIONS
    }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateWorkflow,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New workflow") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item(key = "header") {
                Header(
                    greeting = state.greeting,
                    query = state.query,
                    onQueryChange = onQueryChange,
                )
            }

            if (!state.loading && state.libraryIsEmpty) {
                item(key = "first-run") {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "No workflows yet",
                        body = "Build an AI action once — rewrite, summarise, translate — " +
                            "then fire it from anywhere on your phone.",
                        actionLabel = "Create your first workflow",
                        onAction = onCreateWorkflow,
                    )
                }
            } else if (!state.loading && !state.hasVisibleSections) {
                item(key = "no-matches") {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "No matches",
                        body = "Nothing in your library matches “${state.query.trim()}”.",
                        actionLabel = "See all workflows",
                        onAction = onSeeAllWorkflows,
                    )
                }
            }

            if (state.pinned.isNotEmpty()) {
                item(key = "pinned-header") {
                    SectionHeader(
                        title = "Pinned",
                        actionLabel = "See all".takeIf { seeAllOn == HomeSection.PINNED },
                        onAction = onSeeAllWorkflows,
                    )
                }
                item(key = "pinned-row") {
                    PinnedRow(
                        workflows = state.pinned,
                        onRun = onRunWorkflow,
                        onLongPress = { sheetTarget = it },
                    )
                }
            }

            workflowSection(
                key = "favourites",
                title = "Favourites",
                workflows = state.favorites,
                showSeeAll = seeAllOn == HomeSection.FAVOURITES,
                onSeeAll = onSeeAllWorkflows,
                onRun = onRunWorkflow,
                onLongPress = { sheetTarget = it },
            )

            workflowSection(
                key = "recent",
                title = "Recent",
                workflows = state.recent,
                showSeeAll = seeAllOn == HomeSection.RECENT,
                onSeeAll = onSeeAllWorkflows,
                onRun = onRunWorkflow,
                onLongPress = { sheetTarget = it },
            )

            workflowSection(
                key = "suggestions",
                title = "Suggestions",
                workflows = state.suggestions,
                showSeeAll = seeAllOn == HomeSection.SUGGESTIONS,
                onSeeAll = onSeeAllWorkflows,
                onRun = onRunWorkflow,
                onLongPress = { sheetTarget = it },
            )
        }
    }

    sheetTarget?.let { workflow ->
        WorkflowActionSheet(
            workflow = workflow,
            onDismiss = { sheetTarget = null },
            onEdit = {
                sheetTarget = null
                onEditWorkflow(workflow.id)
            },
            onToggleFavorite = {
                sheetTarget = null
                onToggleFavorite(workflow)
            },
            onTogglePinned = {
                sheetTarget = null
                onTogglePinned(workflow)
            },
        )
    }
}

@Composable
private fun Header(
    greeting: String,
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)) {
        Text(greeting, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(2.dp))
        Text(
            "Your workflows. One tap away.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = CardShape,
            placeholder = { Text("Search workflows") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.clickable { onQueryChange("") },
                    )
                }
            },
            // Dropping the indicator turns a form input into something that reads as a search box.
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )
    }
}

/** Header plus rows, emitting nothing at all when the slice is empty. */
private fun LazyListScope.workflowSection(
    key: String,
    title: String,
    workflows: List<Workflow>,
    showSeeAll: Boolean,
    onSeeAll: () -> Unit,
    onRun: (String) -> Unit,
    onLongPress: (Workflow) -> Unit,
) {
    if (workflows.isEmpty()) return

    item(key = "$key-header") {
        SectionHeader(
            title = title,
            actionLabel = "See all".takeIf { showSeeAll },
            onAction = onSeeAll,
        )
    }
    items(workflows, key = { "$key-${it.id}" }) { workflow ->
        WorkflowRow(
            workflow = workflow,
            onRun = { onRun(workflow.id) },
            onLongPress = { onLongPress(workflow) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun PinnedRow(
    workflows: List<Workflow>,
    onRun: (String) -> Unit,
    onLongPress: (Workflow) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(workflows, key = { it.id }) { workflow ->
            WorkflowTile(
                workflow = workflow,
                onRun = { onRun(workflow.id) },
                onLongPress = { onLongPress(workflow) },
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkflowTile(
    workflow: Workflow,
    onRun: () -> Unit,
    onLongPress: () -> Unit,
) {
    Surface(
        // Clip before clickable so the ripple stops at the rounded corner.
        modifier = Modifier
            .width(132.dp)
            .clip(CardShape)
            .combinedClickable(onClick = onRun, onLongClick = onLongPress),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(14.dp)) {
            WorkflowIcon(workflow.icon, size = 40.dp)
            Spacer(Modifier.height(10.dp))
            Text(
                text = workflow.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WorkflowRow(
    workflow: Workflow,
    onRun: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .combinedClickable(onClick = onRun, onLongClick = onLongPress),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkflowIcon(workflow.icon)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = workflow.category.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (workflow.isPinned) {
                Icon(
                    imageVector = Icons.Filled.PushPin,
                    contentDescription = "Pinned",
                    modifier = Modifier.padding(start = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (workflow.isFavorite) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Favourite",
                    modifier = Modifier.padding(start = 8.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkflowActionSheet(
    workflow: Workflow,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePinned: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkflowIcon(workflow.icon)
            Spacer(Modifier.width(14.dp))
            Text(workflow.name, style = MaterialTheme.typography.titleMedium)
        }
        ListItem(
            headlineContent = { Text("Edit") },
            leadingContent = { Icon(Icons.Outlined.Edit, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onEdit),
        )
        ListItem(
            headlineContent = {
                Text(if (workflow.isFavorite) "Remove from favourites" else "Add to favourites")
            },
            leadingContent = {
                Icon(
                    imageVector = if (workflow.isFavorite) Icons.Filled.Star else Icons.Outlined.Star,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable(onClick = onToggleFavorite),
        )
        ListItem(
            headlineContent = { Text(if (workflow.isPinned) "Unpin" else "Pin to top") },
            leadingContent = {
                Icon(
                    imageVector = if (workflow.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = null,
                )
            },
            modifier = Modifier.clickable(onClick = onTogglePinned),
        )
        Spacer(Modifier.height(24.dp))
    }
}
