@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.discover

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.CountPill
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.SectionLabel
import com.arcx.core.designsystem.component.WiringChips
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.component.shortLabel
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.PromptTextStyle
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.WorkflowCategory

/**
 * Ready-made workflows, plus the file end of the library: import a bundle someone sent you,
 * export your own.
 *
 * There is no server behind this. The gallery ships inside the app, and the screen says so —
 * a "community" tab that is really a JSON asset would be a lie the user eventually catches.
 */
@Composable
fun DiscoverRoute(
    onOpenWorkflow: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: DiscoverViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbars = remember { SnackbarHostState() }

    val open by rememberUpdatedState(onOpenWorkflow)
    LaunchedEffect(viewModel) { viewModel.installed.collect { open(it) } }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbars.showSnackbar(it)
            viewModel.dismissMessage()
        }
    }

    // Some file pickers report .json files as plain text or as an opaque stream, so all three
    // are accepted rather than leaving a valid export greyed out in the picker.
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::import) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::export) }

    DiscoverScreen(
        state = state,
        snackbars = snackbars,
        modifier = modifier,
        onQueryChange = viewModel::setQuery,
        onCategoryChange = viewModel::setCategory,
        onClearFilters = viewModel::clearFilters,
        onSelect = viewModel::select,
        onInstall = { viewModel.install(it) },
        onInstallAndEdit = { viewModel.install(it, openEditor = true) },
        onImport = {
            importLauncher.launch(
                arrayOf("application/json", "text/plain", "application/octet-stream"),
            )
        },
        onExport = { exportLauncher.launch(EXPORT_FILE_NAME) },
    )
}

@Composable
private fun DiscoverScreen(
    state: DiscoverUiState,
    snackbars: SnackbarHostState,
    onQueryChange: (String) -> Unit,
    onCategoryChange: (WorkflowCategory?) -> Unit,
    onClearFilters: () -> Unit,
    onSelect: (WorkflowSpec?) -> Unit,
    onInstall: (WorkflowSpec) -> Unit,
    onInstallAndEdit: (WorkflowSpec) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbars) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            item(key = "title") {
                Title(onImport = onImport, onExport = onExport)
            }

            item(key = "search") {
                SearchField(query = state.query, onQueryChange = onQueryChange)
            }

            val start = state.startHere
            if (start.isNotEmpty()) {
                item(key = "start-here-label") { SectionLabel("Start here") }
                item(key = "start-here") {
                    StartHereRow(specs = start, onSelect = onSelect)
                }
            }

            item(key = "filters") {
                CategoryFilters(
                    counts = state.categoryCounts,
                    selected = state.category,
                    // Null while the bundled gallery is still being read — see the same note
                    // in the library. "All 0" for a tenth of a second reads as an empty app.
                    total = state.gallery.size.takeIf { !state.loading },
                    onSelect = onCategoryChange,
                )
            }

            val visible = state.visible
            when {
                state.loading -> Unit

                state.galleryError != null -> item(key = "error") {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "The gallery is unavailable",
                        body = state.galleryError,
                        actionLabel = "Import a file instead",
                        onAction = onImport,
                    )
                }

                visible.isEmpty() -> item(key = "no-matches") {
                    EmptyState(
                        icon = Icons.Outlined.SearchOff,
                        title = "Nothing here",
                        body = "No ready-made workflow matches that. Try another category, or a different word.",
                        actionLabel = "Clear filters",
                        onAction = onClearFilters,
                    )
                }

                else -> galleryRows(
                    specs = visible,
                    installed = state.installedNames,
                    busy = state.busy,
                    onSelect = onSelect,
                    onInstall = onInstall,
                )
            }
        }
    }

    state.selected?.let { spec ->
        GalleryDetailSheet(
            spec = spec,
            installing = state.busy,
            installed = spec.name in state.installedNames,
            onInstall = { onInstallAndEdit(spec) },
            onDismiss = { onSelect(null) },
        )
    }
}

private fun LazyListScope.galleryRows(
    specs: List<WorkflowSpec>,
    installed: Set<String>,
    busy: Boolean,
    onSelect: (WorkflowSpec) -> Unit,
    onInstall: (WorkflowSpec) -> Unit,
) {
    itemsIndexed(specs, key = { _, spec -> spec.name }) { index, spec ->
        GalleryRow(
            modifier = Modifier.animateItem(),
            spec = spec,
            installed = spec.name in installed,
            busy = busy,
            showDivider = index < specs.lastIndex,
            onClick = { onSelect(spec) },
            onInstall = { onInstall(spec) },
        )
    }
}

/** Says plainly what this screen is, so nobody waits for a feed that is never coming. */
@Composable
private fun Title(onImport: () -> Unit, onExport: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Discover",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f),
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Import from a file") },
                        leadingIcon = { Icon(Icons.Outlined.FileDownload, null) },
                        onClick = {
                            menuOpen = false
                            onImport()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Export my workflows") },
                        leadingIcon = { Icon(Icons.Outlined.FileUpload, null) },
                        onClick = {
                            menuOpen = false
                            onExport()
                        },
                    )
                }
            }
        }
        Text(
            text = "Ships with the app. Nothing downloads, and nothing you install is sent " +
                "anywhere. Anything you install becomes yours to edit.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp),
        )
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onQueryChange,
        singleLine = true,
        placeholder = { Text("Search the gallery") },
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

@Composable
private fun StartHereRow(specs: List<WorkflowSpec>, onSelect: (WorkflowSpec) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        specs.forEach { spec -> StartHereCard(spec, onClick = { onSelect(spec) }) }
    }
}

@Composable
private fun StartHereCard(spec: WorkflowSpec, onClick: () -> Unit) {
    val tint = spec.category.tint()
    Column(
        modifier = Modifier
            .width(150.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(18.dp))
            // The category's own hue fading into the surface, so the shelf reads as a set of
            // suggestions rather than three more rows that happen to be sideways.
            .background(
                Brush.linearGradient(
                    listOf(tint.container, MaterialTheme.colorScheme.surfaceContainerLow),
                ),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        WorkflowIcon(icon = spec.icon, size = 34.dp, container = tint.container, content = tint.content)
        Column {
            Text(
                text = spec.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                // Short labels here, not the gallery's longer ones: a 150dp card truncates
                // "Selected text → Bottom sheet" to nothing worth reading.
                text = "${spec.input.shortLabel} → ${spec.output.shortLabel}",
                style = MetaTextStyle,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun CategoryFilters(
    counts: List<Pair<WorkflowCategory, Int>>,
    selected: WorkflowCategory?,
    total: Int?,
    onSelect: (WorkflowCategory?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        CountPill(
            label = "All",
            count = total,
            selected = selected == null,
            onClick = { onSelect(null) },
        )
        counts.forEach { (category, count) ->
            CountPill(
                label = category.label,
                count = count,
                selected = selected == category,
                onClick = { onSelect(category.takeIf { it != selected }) },
            )
        }
    }
}

@Composable
private fun GalleryRow(
    spec: WorkflowSpec,
    installed: Boolean,
    busy: Boolean,
    showDivider: Boolean,
    onClick: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // A floor, not a fixed height: at fontScale 2.0 the name and the wiring chips are
                // taller than 64dp together, and a fixed row cropped the chips off the bottom.
                .heightIn(min = 64.dp)
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val tint = spec.category.tint()
            WorkflowIcon(
                icon = spec.icon,
                size = 38.dp,
                container = tint.container,
                content = tint.content,
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                // The same chips, in the same words, as the library row this becomes once it is
                // installed. The sheet below still spells the wiring out in full — it has the
                // room, and it is the screen someone reads rather than scans.
                WiringChips(
                    input = spec.input.shortLabel,
                    output = spec.output.shortLabel,
                )
            }
            InstallButton(installed = installed, busy = busy, onInstall = onInstall)
        }
        if (showDivider) {
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
        }
    }
}

/**
 * One tap installs. The row still opens the detail sheet, which is where the whole prompt is —
 * a gallery entry is a local copy the user can read, edit or delete a second later, so making
 * them read it first was a ceremony that only slowed down the honest case.
 */
@Composable
private fun InstallButton(installed: Boolean, busy: Boolean, onInstall: () -> Unit) {
    val shape = RoundedCornerShape(9.dp)
    if (installed) {
        Row(
            modifier = Modifier
                .height(32.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Added",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .height(32.dp)
            .clip(shape)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(enabled = !busy, onClick = onInstall)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Install",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/** The whole prompt, for anyone who wants to read it before or after taking a copy. */
@Composable
private fun GalleryDetailSheet(
    spec: WorkflowSpec,
    installing: Boolean,
    installed: Boolean,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WorkflowIcon(
                    icon = spec.icon,
                    size = 52.dp,
                    container = spec.category.tint().container,
                    content = spec.category.tint().content,
                )
                Column(Modifier.padding(start = 14.dp)) {
                    Text(spec.name, style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${spec.category.label} · ${spec.input.label} → ${spec.output.label}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            spec.systemPrompt?.let { system ->
                SheetLabel("System prompt")
                PromptBlock(system)
            }

            SheetLabel("Prompt")
            PromptBlock(spec.prompt)

            Spacer(Modifier.height(20.dp))
            Button(
                onClick = onInstall,
                enabled = !installing && !installed,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (installed) "Already in your library" else "Add to my workflows") }
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp),
    )
}

@Composable
private fun PromptBlock(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = text, style = PromptTextStyle, modifier = Modifier.padding(14.dp))
    }
}
