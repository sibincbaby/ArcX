@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.discover

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.theme.PromptTextStyle
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
        onInstall = viewModel::install,
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
    onImport: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbars) },
        topBar = {
            TopAppBar(
                title = { Text("Discover") },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More options")
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
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            LocalGalleryNotice()

            OutlinedTextField(
                value = state.query,
                onValueChange = onQueryChange,
                singleLine = true,
                placeholder = { Text("Search the gallery") },
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

            CategoryFilters(selected = state.category, onSelect = onCategoryChange)

            val visible = state.visible
            when {
                state.loading -> Spacer(Modifier.fillMaxSize())

                state.galleryError != null -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "The gallery is unavailable",
                    body = state.galleryError,
                    actionLabel = "Import a file instead",
                    onAction = onImport,
                )

                visible.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = "Nothing here",
                    body = "No ready-made workflow matches that. Try another category, or a different word.",
                    actionLabel = "Clear filters",
                    onAction = onClearFilters,
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.name }) { spec ->
                        GalleryRow(spec = spec, onClick = { onSelect(spec) })
                    }
                }
            }
        }
    }

    state.selected?.let { spec ->
        GalleryDetailSheet(
            spec = spec,
            installing = state.busy,
            onInstall = { onInstall(spec) },
            onDismiss = { onSelect(null) },
        )
    }
}

/** Says plainly what this screen is, so nobody waits for a feed that is never coming. */
@Composable
private fun LocalGalleryNotice() {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = "A gallery that ships with the app — nothing is downloaded, and nothing you " +
                "install is sent anywhere. Anything you install becomes yours to edit.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(14.dp),
        )
    }
}

@Composable
private fun CategoryFilters(
    selected: WorkflowCategory?,
    onSelect: (WorkflowCategory?) -> Unit,
) {
    Row(
        modifier = Modifier
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
private fun GalleryRow(spec: WorkflowSpec, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkflowIcon(emoji = spec.icon, container = MaterialTheme.colorScheme.surface)
            Column(Modifier.padding(start = 14.dp)) {
                Text(
                    text = spec.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "${spec.category.label} · ${spec.input.label} → ${spec.output.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Nothing installs unseen: the whole prompt is on screen before the Install button is. */
@Composable
private fun GalleryDetailSheet(
    spec: WorkflowSpec,
    installing: Boolean,
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
                WorkflowIcon(emoji = spec.icon, size = 52.dp)
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
                enabled = !installing,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Add to my workflows") }
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
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(text = text, style = PromptTextStyle, modifier = Modifier.padding(14.dp))
    }
}
