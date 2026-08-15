@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.NoticeCard
import com.arcx.core.designsystem.component.NoticeSeverity
import com.arcx.core.designsystem.theme.PromptTextStyle
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.ProviderConfig
import kotlin.math.round

// ------------------------------------------------------------ step 3: where does it land

@Composable
internal fun OutputStep(
    state: WorkflowEditorState,
    onOutputChange: (OutputTarget) -> Unit,
    onProviderChange: (String?) -> Unit,
    onModelChange: (String) -> Unit,
    onLoadModels: () -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Float?) -> Unit,
    onMaxTokensChange: (String) -> Unit,
) {
    var advancedOpen by remember { mutableStateOf(false) }

    StepTitle("Where does it land?")

    Spacer(Modifier.height(Spacing.Lg))
    OutputTarget.entries.chunked(2).forEach { pair ->
        Row(
            // Intrinsic height, as the Home grid does: the two cards in a row carry descriptions
            // of different lengths, and left to themselves they end at different heights, which
            // reads as a broken grid. The cards fillMaxHeight to match it — a child of a
            // fixed-height Row is still measured with minHeight 0, so this alone would size the
            // Row and leave the borders ragged.
            modifier = Modifier
                .height(IntrinsicSize.Max)
                .padding(bottom = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            pair.forEach { target ->
                OutputCard(
                    target = target,
                    selected = state.output == target,
                    onClick = { onOutputChange(target) },
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }

    Spacer(Modifier.height(Spacing.Md))
    FormDropdown(
        label = "Provider",
        selected = state.providers.firstOrNull { it.id == state.providerId },
        options = listOf<ProviderConfig?>(null) + state.providers,
        optionLabel = { it?.label ?: "Default provider" },
        onSelect = { onProviderChange(it?.id) },
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(Spacing.Md))
    ModelField(
        model = state.model,
        models = state.models,
        onModelChange = onModelChange,
        onOpen = onLoadModels,
        modifier = Modifier.fillMaxWidth(),
    )

    state.modelWithoutVision?.let { name ->
        Spacer(Modifier.height(10.dp))
        // Advice, never a gate. Everything the builder can get wrong is still savable — a
        // half-right workflow the user can come back and fix beats a form that refuses to close.
        NoticeCard(
            severity = NoticeSeverity.Warning,
            message = "$name cannot look at images, so this workflow will send it a screenshot " +
                "it can never read. Pick a model with vision when you have one — " +
                "saving works either way.",
        )
    }

    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Advanced",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { advancedOpen = !advancedOpen }) {
            Text(if (advancedOpen) "Hide" else "Show")
        }
    }
    if (advancedOpen) {
        AdvancedSection(
            systemPrompt = state.systemPrompt,
            temperature = state.temperature,
            maxTokens = state.maxTokens,
            onSystemPromptChange = onSystemPromptChange,
            onTemperatureChange = onTemperatureChange,
            onMaxTokensChange = onMaxTokensChange,
        )
    }

    Spacer(Modifier.height(Spacing.Xl))
    SummaryCard(state)
}

@Composable
private fun RowScope.OutputCard(
    target: OutputTarget,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .minimumInteractiveComponentSize()
            .heightIn(min = 100.dp)
            .clip(shape)
            .then(
                if (selected) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                        .border(1.5.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                },
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = target.icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(21.dp),
        )
        Column {
            Text(target.label, style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(2.dp))
            Text(
                text = target.explanation,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** The whole workflow, written back as one sentence, immediately above the save button. */
@Composable
private fun SummaryCard(state: WorkflowEditorState) {
    val provider = state.providers.firstOrNull { it.id == state.providerId }?.label
        ?: state.providers.firstOrNull()?.label
        ?: "your provider"
    val name = state.name.trim().ifBlank { "this workflow" }

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(15.dp)) {
            Text(
                text = "SUMMARY",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(Spacing.Sm))
            Text(
                text = "Run $name on ${state.input.summaryPhrase}, and $provider answers — " +
                    "then ArcX ${state.output.summaryPhrase}.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

/**
 * The model list is the one field that depends on the network. It is fetched only when the
 * field is opened, and a failure turns it into a plain text box rather than a dead end — an
 * expired key or a flight-mode journey must not stop someone writing a workflow.
 */
@Composable
private fun ModelField(
    model: String,
    models: ModelListState,
    onModelChange: (String) -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }

    if (models is ModelListState.Unavailable) {
        OutlinedTextField(
            value = model,
            onValueChange = onModelChange,
            label = { Text("Model") },
            singleLine = true,
            placeholder = { Text("Leave blank for the provider default") },
            supportingText = { Text("${models.reason}. Type a model name if you know it.") },
            modifier = modifier.fillMaxWidth(),
        )
        return
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = {
            expanded = it
            if (it) onOpen()
        },
        modifier = modifier,
    ) {
        OutlinedTextField(
            value = model.ifBlank { "Provider default" },
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            label = { Text("Model") },
            trailingIcon = {
                if (models is ModelListState.Loading) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Outlined.UnfoldMore, contentDescription = null)
                }
            },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Provider default") },
                onClick = {
                    onModelChange("")
                    expanded = false
                },
            )
            if (models is ModelListState.Loaded) {
                models.models.forEach { info ->
                    DropdownMenuItem(
                        text = { Text(info.displayName) },
                        onClick = {
                            onModelChange(info.id)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedSection(
    systemPrompt: String,
    temperature: Float?,
    maxTokens: String,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Float?) -> Unit,
    onMaxTokensChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = systemPrompt,
        onValueChange = onSystemPromptChange,
        label = { Text("System prompt") },
        placeholder = { Text("Who the AI should be while doing this") },
        textStyle = PromptTextStyle,
        minLines = 3,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(Spacing.Md))

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Temperature", style = MaterialTheme.typography.bodyLarge)
            Spacer(Modifier.weight(1f))
            Text(
                text = temperature?.let { (round(it * 10) / 10).toString() } ?: "Provider default",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Slider(
            value = temperature ?: 0.7f,
            onValueChange = onTemperatureChange,
            valueRange = 0f..2f,
            steps = 19,
        )
        Text(
            text = "Low keeps it literal, high lets it wander.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (temperature != null) {
            TextButton(onClick = { onTemperatureChange(null) }) { Text("Use provider default") }
        }
    }

    Spacer(Modifier.height(Spacing.Md))

    OutlinedTextField(
        value = maxTokens,
        onValueChange = onMaxTokensChange,
        label = { Text("Max tokens") },
        placeholder = { Text("Provider default") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
