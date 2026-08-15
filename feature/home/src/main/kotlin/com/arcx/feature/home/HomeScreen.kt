package com.arcx.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.format.formatDuration
import com.arcx.core.designsystem.format.relativeTime
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.Motion
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.designsystem.theme.warningTint
import com.arcx.core.model.RunSummary

private val TileShape = RoundedCornerShape(18.dp)
private const val GRID_COLUMNS = 3

@Composable
fun HomeRoute(
    onRunWorkflow: (String) -> Unit,
    onEditWorkflow: (String) -> Unit,
    onCreateWorkflow: () -> Unit,
    onSeeAllWorkflows: () -> Unit,
    onSeeActivity: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEntryPoints: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.onResume()
        onPauseOrDispose { }
    }

    HomeScreen(
        state = state,
        onQueryChange = viewModel::onQueryChange,
        onRunWorkflow = onRunWorkflow,
        onEditWorkflow = onEditWorkflow,
        onCreateWorkflow = onCreateWorkflow,
        onSeeAllWorkflows = onSeeAllWorkflows,
        onSeeActivity = onSeeActivity,
        onOpenSettings = onOpenSettings,
        onOpenEntryPoints = onOpenEntryPoints,
    )
}

/**
 * One tap runs. A long press configures. There is no menu in between here or anywhere else —
 * the grid, the library, the picker and the bubble all obey the same two gestures, so the
 * fastest thing in the product never costs two taps.
 */
@Composable
private fun HomeScreen(
    state: HomeUiState,
    onQueryChange: (String) -> Unit,
    onRunWorkflow: (String) -> Unit,
    onEditWorkflow: (String) -> Unit,
    onCreateWorkflow: () -> Unit,
    onSeeAllWorkflows: () -> Unit,
    onSeeActivity: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEntryPoints: () -> Unit,
) {
    var searchOpen by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateWorkflow,
                icon = { Icon(Icons.Outlined.Add, contentDescription = null) },
                text = { Text("New") },
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
                    subtitle = state.subtitle,
                    searchOpen = searchOpen,
                    onToggleSearch = {
                        searchOpen = !searchOpen
                        if (!searchOpen) onQueryChange("")
                    },
                    onOpenSettings = onOpenSettings,
                )
            }

            // Always emitted, visibility animated: adding and removing the item outright made
            // the whole grid below it jump by the height of a text field.
            item(key = "search") {
                AnimatedVisibility(
                    visible = searchOpen,
                    enter = expandVertically(tween(Motion.Emphasis, easing = Motion.Decelerate)) +
                        fadeIn(tween(Motion.Medium)),
                    exit = shrinkVertically(tween(Motion.Medium, easing = Motion.Accelerate)) +
                        fadeOut(tween(Motion.Fast)),
                ) {
                    SearchField(query = state.query, onQueryChange = onQueryChange)
                }
            }

            when {
                // Ahead of the empty states: a failed read leaves the grid just as empty as a
                // fresh install does, and "No workflows yet" would be blaming the user for it.
                state.error != null -> item(key = "error") {
                    ErrorCard(
                        title = "Couldn't load your workflows",
                        message = state.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                state.loading -> Unit

                state.libraryIsEmpty -> item(key = "first-run") {
                    EmptyState(
                        icon = Icons.Outlined.AutoAwesome,
                        title = "No workflows yet",
                        body = "Build an AI action once — rewrite, summarise, translate — " +
                            "then fire it from anywhere on your phone.",
                        actionLabel = "Create your first workflow",
                        onAction = onCreateWorkflow,
                    )
                }

                state.tiles.isEmpty() && state.searching -> item(key = "no-matches") {
                    EmptyState(
                        icon = Icons.Outlined.Search,
                        title = "No matches",
                        body = "Nothing in your library matches “${state.query.trim()}”.",
                        actionLabel = "See all workflows",
                        onAction = onSeeAllWorkflows,
                    )
                }

                else -> item(key = "grid") {
                    // A fixed handful of tiles, so a plain chunked column beats a nested
                    // LazyVerticalGrid and the scroll stays owned by one list.
                    TileGrid(
                        tiles = state.tiles,
                        onRun = onRunWorkflow,
                        onConfigure = onEditWorkflow,
                        onAdd = onCreateWorkflow,
                    )
                }
            }

            if (state.surfaces.isNotEmpty() && !state.searching) {
                item(key = "surfaces") {
                    SurfaceStrip(chips = state.surfaces, onClick = onOpenEntryPoints)
                }
            }

            if (state.recentRuns.isNotEmpty()) {
                item(key = "recent-header") {
                    RecentHeader(onSeeActivity = onSeeActivity)
                }
                recentRuns(state.recentRuns, state.nowMillis, onRunWorkflow)
            }
        }
    }
}

private fun LazyListScope.recentRuns(
    runs: List<RunSummary>,
    nowMillis: Long,
    onRerun: (String) -> Unit,
) {
    itemsIndexed(runs, key = { _, run -> "run-${run.id}" }) { index, run ->
        RunRow(
            modifier = Modifier.animateItem(),
            run = run,
            nowMillis = nowMillis,
            showDivider = index < runs.lastIndex,
            onRerun = { onRerun(run.workflowId) },
        )
    }
}

@Composable
private fun Header(
    greeting: String,
    subtitle: String,
    searchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(greeting, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onToggleSearch) {
            Icon(
                imageVector = if (searchOpen) Icons.Outlined.Close else Icons.Outlined.Search,
                contentDescription = if (searchOpen) "Close search" else "Search workflows",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = { Text("Search workflows") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        // Dropping the indicator turns a form input into something that reads as a search box.
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
private fun TileGrid(
    tiles: List<HomeTile>,
    onRun: (String) -> Unit,
    onConfigure: (String) -> Unit,
    onAdd: () -> Unit,
) {
    // The add cell is one more item in the same flow, so it lands wherever the grid happens to
    // end rather than being pinned to a corner that may be empty.
    val cells = tiles.size + 1
    Column(
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        (0 until cells step GRID_COLUMNS).forEach { start ->
            // Intrinsic height rather than a fixed one: a two-line workflow name at a large
            // font scale needs more than 104dp, and a fixed box clips the duration line off
            // the bottom of exactly the tiles that have been run most.
            Row(
                modifier = Modifier.height(IntrinsicSize.Max),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                (start until start + GRID_COLUMNS).forEach { index ->
                    when {
                        index < tiles.size -> WorkflowTile(
                            tile = tiles[index],
                            onRun = { onRun(tiles[index].workflow.id) },
                            onConfigure = { onConfigure(tiles[index].workflow.id) },
                        )

                        index == tiles.size -> AddTile(onAdd)
                        // Keeps the last row's tiles at column width instead of stretching them.
                        else -> Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RowScope.WorkflowTile(
    tile: HomeTile,
    onRun: () -> Unit,
    onConfigure: () -> Unit,
) {
    val workflow = tile.workflow
    Surface(
        // Clip before clickable so the ripple stops at the rounded corner.
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 104.dp)
            .clip(TileShape)
            .combinedClickable(onClick = onRun, onLongClick = onConfigure),
        shape = TileShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            val tint = workflow.category.tint()
            WorkflowIcon(
                icon = workflow.icon,
                size = 34.dp,
                container = tint.container,
                content = tint.content,
            )
            Column {
                Text(
                    text = workflow.name,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = tile.averageMs?.let { "${formatDuration(it)} avg" } ?: "not run yet",
                    style = MetaTextStyle,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun RowScope.AddTile(onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outlineVariant
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .heightIn(min = 104.dp)
            .clip(TileShape)
            .dashedOutline(outline)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Add,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Add tile",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dashed so the empty cell reads as a slot to fill, not a tile that failed to load. */
private fun Modifier.dashedOutline(color: Color) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(18.dp.toPx()),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

@Composable
private fun SurfaceStrip(chips: List<SurfaceChip>, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip -> SurfaceChipView(chip, onClick) }
    }
}

@Composable
private fun SurfaceChipView(chip: SurfaceChip, onClick: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    val warning = warningTint()
    val container = when (chip.state) {
        SurfaceState.LIVE -> MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
        SurfaceState.OFF -> warning.container
        SurfaceState.ALWAYS_ON -> Color.Transparent
    }
    val content = when (chip.state) {
        SurfaceState.LIVE -> MaterialTheme.colorScheme.primary
        SurfaceState.OFF -> warning.content
        SurfaceState.ALWAYS_ON -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .background(container)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when (chip.state) {
            SurfaceState.LIVE -> Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(content),
            )

            SurfaceState.OFF -> Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(14.dp),
            )

            SurfaceState.ALWAYS_ON -> Unit
        }
        Text(chip.label, style = MaterialTheme.typography.labelLarge, color = content)
    }
}

@Composable
private fun RecentHeader(onSeeActivity: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Recent runs",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "Activity",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSeeActivity)
                .padding(horizontal = 8.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun RunRow(
    run: RunSummary,
    nowMillis: Long,
    showDivider: Boolean,
    onRerun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A minimum, not a fixed height, for the same reason the tile grid above uses
                // IntrinsicSize.Max: at a large font scale these two lines are taller than 56dp,
                // and a fixed row clips the "9m ago · gemini" line off the bottom.
                .heightIn(min = 56.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkflowIcon(icon = run.workflowIcon, size = 36.dp)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = run.workflowName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = runSubtitle(run, nowMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatDuration(run.durationMs),
                style = MetaTextStyle,
                color = MaterialTheme.colorScheme.outline,
            )
            IconButton(onClick = onRerun) {
                Icon(
                    imageVector = Icons.Outlined.Replay,
                    contentDescription = "Run ${run.workflowName} again",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
}

private fun runSubtitle(run: RunSummary, nowMillis: Long): String {
    val source = run.model.ifBlank { run.providerLabel }.ifBlank { "no provider" }
    return "${relativeTime(run.startedAt, nowMillis)} · $source"
}
