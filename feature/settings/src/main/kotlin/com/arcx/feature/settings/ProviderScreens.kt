package com.arcx.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.arcx.core.designsystem.component.EmptyState
import com.arcx.core.designsystem.component.ErrorCard
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.model.ProviderType

@Composable
internal fun ProvidersScreen(
    providers: List<ProviderRow>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onSetDefault: (String) -> Unit,
) {
    SettingsScaffold(
        title = "Providers",
        onBack = onBack,
        actions = {
            IconButton(onClick = onAdd) {
                Icon(Icons.Outlined.Add, contentDescription = "Add provider")
            }
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (providers.isEmpty()) {
                EmptyState(
                    icon = Icons.Outlined.Cloud,
                    title = "No provider connected",
                    body = "ArcX runs on your own API key. Connect a provider and every " +
                        "workflow you build uses it.",
                    actionLabel = "Connect a provider",
                    onAction = onAdd,
                )
            } else {
                SettingsGroup {
                    providers.forEach { row ->
                        SettingsRow(
                            title = row.config.label,
                            subtitle = providerSubtitle(row),
                            onClick = { onEdit(row.config.id) },
                            trailing = {
                                if (row.isDefault) {
                                    Text(
                                        text = "Default",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                } else {
                                    TextButton(onClick = { onSetDefault(row.config.id) }) {
                                        Text("Make default")
                                    }
                                }
                            },
                        )
                    }
                }
            }

            SettingsNote("Keys are stored in this device's encrypted keystore and never uploaded.")
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }
}

private fun providerSubtitle(row: ProviderRow): String {
    val model = row.config.defaultModel.ifBlank { "no default model" }
    val key = when {
        row.config.type.isLocal -> "no key needed"
        row.hasKey -> "key saved"
        else -> "no key"
    }
    return "${displayName(row.config.type)} · $model · $key"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProviderEditScreen(
    providerId: String?,
    onDone: () -> Unit,
    viewModel: ProviderEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current

    LaunchedEffect(providerId) { viewModel.start(providerId) }

    SettingsScaffold(
        title = if (state.isNew) "Add provider" else "Edit provider",
        onBack = onDone,
        actions = {
            if (!state.isNew) {
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(Icons.Outlined.Delete, contentDescription = "Delete provider")
                }
            }
        },
    ) { padding ->
        // Everything lines up on the same screen gutter SettingsGroup uses, so the fields and the
        // switch card share one edge.
        val field = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Gutter)

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "Provider",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = Spacing.Gutter),
            )
            Spacer(Modifier.height(Spacing.Sm))
            FlowRow(
                modifier = Modifier.padding(horizontal = Spacing.Gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Sm),
            ) {
                ProviderType.entries.forEach { type ->
                    val supported = type in state.supportedTypes
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        // Only implemented types are selectable. Letting someone fill in a form
                        // for a backend that cannot run is a worse experience than saying so.
                        enabled = supported,
                        label = { Text(displayName(type)) },
                    )
                }
            }
            val comingSoon = ProviderType.entries.filterNot { it in state.supportedTypes }
            if (comingSoon.isNotEmpty() && !state.loading) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Coming soon: ${comingSoon.joinToString { displayName(it) }}.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter),
                )
            }

            Spacer(Modifier.height(Spacing.Xl))
            OutlinedTextField(
                value = state.label,
                onValueChange = viewModel::onLabelChange,
                label = { Text("Label") },
                singleLine = true,
                modifier = field,
            )

            Spacer(Modifier.height(Spacing.Md))
            OutlinedTextField(
                value = state.baseUrl,
                onValueChange = viewModel::onBaseUrlChange,
                label = { Text("Base URL") },
                supportingText = { Text("Change this only for a proxy or self-hosted endpoint.") },
                singleLine = true,
                modifier = field,
            )

            Spacer(Modifier.height(Spacing.Md))
            if (state.needsKey) {
                ApiKeyField(
                    value = state.apiKey,
                    hasSavedKey = state.hasSavedKey,
                    reveal = state.revealKey,
                    onValueChange = viewModel::onApiKeyChange,
                    onToggleReveal = viewModel::onToggleReveal,
                    modifier = field,
                )
                apiKeyUrl(state.type)?.let { url ->
                    TextButton(
                        onClick = { uriHandler.openUri(url) },
                        // 8, not the gutter: a TextButton carries 12dp of its own, and the label
                        // is what has to land on the gutter, not the button's invisible edge.
                        modifier = Modifier.padding(horizontal = Spacing.Sm),
                    ) {
                        Text("Get a ${displayName(state.type)} key")
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.AutoMirrored.Outlined.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                }
            } else {
                Text(
                    text = "${displayName(state.type)} runs on your own machine, so there is no " +
                        "API key to enter.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.Gutter),
                )
            }

            Spacer(Modifier.height(Spacing.Md))
            OutlinedTextField(
                value = state.defaultModel,
                onValueChange = viewModel::onDefaultModelChange,
                label = { Text("Default model") },
                supportingText = { Text("Used by any workflow that does not name its own.") },
                singleLine = true,
                modifier = field,
            )

            Spacer(Modifier.height(Spacing.Md))
            SettingsGroup {
                // A "Stream responses" switch used to sit here, bound to ProviderConfig.streaming.
                // Nothing in :core:ai reads that field — GeminiProvider always streams — so the
                // switch was a control that changed nothing. The field stays (dropping it needs a
                // Room migration); bring the switch back when a provider actually honours it.
                SettingsSwitchRow(
                    title = "Default provider",
                    subtitle = "Workflows that do not pick one use this.",
                    checked = state.makeDefault,
                    onCheckedChange = viewModel::onMakeDefaultChange,
                )
            }

            Spacer(Modifier.height(Spacing.Xl))
            TestConnection(
                state = state.test,
                enabled = state.canTest,
                onTest = viewModel::onTestConnection,
                modifier = Modifier.padding(horizontal = Spacing.Gutter),
            )

            Spacer(Modifier.height(Spacing.Xxl))
            Row(
                modifier = Modifier.padding(horizontal = Spacing.Gutter),
                horizontalArrangement = Arrangement.spacedBy(Spacing.Md),
            ) {
                Button(
                    onClick = { viewModel.onSave(onDone) },
                    enabled = state.canSave,
                ) { Text("Save") }
                OutlinedButton(onClick = onDone) { Text("Cancel") }
            }
            Spacer(Modifier.height(Spacing.Xxxl))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${state.label}?") },
            text = {
                Text(
                    "The configuration and its saved API key are removed from this device. " +
                        "Workflows that used it fall back to your default provider.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDelete(onDone)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

/**
 * A saved key is reported, never rendered. The field starts empty on every edit precisely so
 * that no screen, screenshot or accessibility dump can ever contain a stored key.
 */
@Composable
private fun ApiKeyField(
    value: String,
    hasSavedKey: Boolean,
    reveal: Boolean,
    onValueChange: (String) -> Unit,
    onToggleReveal: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("API key") },
        placeholder = { Text(if (hasSavedKey) "Saved — type to replace" else "Paste your key") },
        singleLine = true,
        visualTransformation = if (reveal) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = onToggleReveal) {
                Icon(
                    imageVector = if (reveal) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                    contentDescription = if (reveal) "Hide key" else "Show key",
                )
            }
        },
        supportingText = {
            Text(
                if (hasSavedKey) "A key is saved for this provider. Leave blank to keep it."
                else "Stored in this device's encrypted keystore.",
            )
        },
        modifier = modifier,
    )
}

@Composable
private fun TestConnection(
    state: TestState,
    enabled: Boolean,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onTest, enabled = enabled) { Text("Test connection") }
            if (state is TestState.Running) {
                Spacer(Modifier.width(Spacing.Md))
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        when (state) {
            is TestState.Success -> {
                Spacer(Modifier.height(Spacing.Sm))
                Text(
                    text = "Connected. ${state.modelCount} model${if (state.modelCount == 1) "" else "s"} available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            is TestState.Failure -> {
                Spacer(Modifier.height(Spacing.Sm))
                ErrorCard(title = "Could not connect", message = state.message)
            }

            else -> Unit
        }
    }
}
