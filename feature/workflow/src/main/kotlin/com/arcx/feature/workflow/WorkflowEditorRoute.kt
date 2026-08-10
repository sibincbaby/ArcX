@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.workflow

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.arcx.core.common.prompt.PromptTemplate
import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.theme.PromptTextStyle
import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.WorkflowCategory
import kotlin.math.round

/**
 * The builder. Deliberately one scrolling form and not a node editor: a workflow is a name, a
 * prompt, where the text comes from and where the answer goes. Everything else hides under
 * "Advanced" until somebody asks for it.
 *
 * [workflowId] null means create.
 */
@Composable
fun WorkflowEditorRoute(
    workflowId: String?,
    onDone: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: WorkflowEditorViewModel = hiltViewModel()
    val state = viewModel.state

    LaunchedEffect(workflowId) { viewModel.start(workflowId) }

    val done by rememberUpdatedState(onDone)
    LaunchedEffect(viewModel) { viewModel.saved.collect { done() } }

    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (viewModel.isDirty) confirmDiscard = true else onBack() }

    BackHandler(enabled = true) { leave() }

    WorkflowEditorScreen(
        state = state,
        modifier = modifier,
        onBack = leave,
        onSave = viewModel::save,
        onNameChange = viewModel::setName,
        onIconChange = viewModel::setIcon,
        onCategoryChange = viewModel::setCategory,
        onInputChange = viewModel::setInput,
        onPromptChange = viewModel::setPrompt,
        onInsertVariable = viewModel::insertVariable,
        onApplyTemplate = viewModel::applyTemplate,
        onProviderChange = viewModel::setProvider,
        onModelChange = viewModel::setModel,
        onLoadModels = viewModel::loadModels,
        onOutputChange = viewModel::setOutput,
        onSystemPromptChange = viewModel::setSystemPrompt,
        onTemperatureChange = viewModel::setTemperature,
        onMaxTokensChange = viewModel::setMaxTokens,
    )

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Discard changes?") },
            text = { Text("This workflow has edits that have not been saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDiscard = false
                        onBack()
                    },
                ) { Text("Discard") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun WorkflowEditorScreen(
    state: WorkflowEditorState,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onCategoryChange: (WorkflowCategory) -> Unit,
    onInputChange: (InputSource) -> Unit,
    onPromptChange: (TextFieldValue) -> Unit,
    onInsertVariable: (PromptVariable) -> Unit,
    onApplyTemplate: (PromptTemplateOption) -> Unit,
    onProviderChange: (String?) -> Unit,
    onModelChange: (String) -> Unit,
    onLoadModels: () -> Unit,
    onOutputChange: (OutputTarget) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Float?) -> Unit,
    onMaxTokensChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }
    var advancedOpen by remember { mutableStateOf(false) }

    val saveLabel = if (state.forksBuiltIn) "Save a copy" else "Save"

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(if (state.isNew) "New workflow" else "Edit workflow") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = onSave, enabled = !state.saving) { Text(saveLabel) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(8.dp))

            // Name and icon together: the two things that make a workflow recognisable.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WorkflowIcon(
                    emoji = state.icon,
                    size = 56.dp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .clickable { showEmojiPicker = true },
                )
                Spacer(Modifier.size(12.dp))
                OutlinedTextField(
                    value = state.name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    isError = state.nameError,
                    supportingText = if (state.nameError) {
                        { Text("Give it a name so you can find it later") }
                    } else {
                        null
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            FormDropdown(
                label = "Category",
                selected = state.category,
                options = WorkflowCategory.entries,
                optionLabel = { it.label },
                onSelect = onCategoryChange,
                modifier = Modifier.fieldPadding(),
            )

            Spacer(Modifier.height(12.dp))

            FormDropdown(
                label = "Input",
                selected = state.input,
                options = InputSource.entries,
                optionLabel = { it.label },
                optionDescription = { it.explanation },
                supportingText = state.input.explanation,
                onSelect = onInputChange,
                modifier = Modifier.fieldPadding(),
            )

            Spacer(Modifier.height(12.dp))

            SectionHeader(
                title = "Prompt",
                actionLabel = "Use a template",
                onAction = { showTemplates = true },
            )

            VariableChips(onInsert = onInsertVariable)

            OutlinedTextField(
                value = state.prompt,
                onValueChange = onPromptChange,
                label = { Text("What should the AI do?") },
                textStyle = PromptTextStyle,
                minLines = 6,
                isError = state.promptError,
                supportingText = if (state.promptError) {
                    { Text("A workflow needs a prompt") }
                } else {
                    null
                },
                modifier = Modifier.fieldPadding(),
            )

            PromptVariableSummary(prompt = state.prompt.text, input = state.input)

            Spacer(Modifier.height(12.dp))
            SectionHeader(title = "Where it runs")

            FormDropdown(
                label = "Provider",
                selected = state.providers.firstOrNull { it.id == state.providerId },
                options = listOf<ProviderConfig?>(null) + state.providers,
                optionLabel = { it?.label ?: "Default provider" },
                onSelect = { onProviderChange(it?.id) },
                modifier = Modifier.fieldPadding(),
            )

            Spacer(Modifier.height(12.dp))

            ModelField(
                model = state.model,
                models = state.models,
                onModelChange = onModelChange,
                onOpen = onLoadModels,
                modifier = Modifier.fieldPadding(),
            )

            Spacer(Modifier.height(12.dp))

            FormDropdown(
                label = "Output",
                selected = state.output,
                options = OutputTarget.entries,
                optionLabel = { it.label },
                optionDescription = { it.explanation },
                supportingText = state.output.explanation,
                onSelect = onOutputChange,
                modifier = Modifier.fieldPadding(),
            )

            Spacer(Modifier.height(12.dp))

            SectionHeader(
                title = "Advanced",
                actionLabel = if (advancedOpen) "Hide" else "Show",
                onAction = { advancedOpen = !advancedOpen },
            )

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

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onSave,
                enabled = !state.saving,
                modifier = Modifier.fieldPadding(),
            ) { Text(saveLabel) }

            Spacer(Modifier.height(40.dp))
        }
    }

    if (showEmojiPicker) {
        EmojiPickerSheet(
            current = state.icon,
            onChange = onIconChange,
            onDismiss = { showEmojiPicker = false },
        )
    }

    if (showTemplates) {
        PromptTemplateSheet(
            onPick = {
                onApplyTemplate(it)
                showTemplates = false
            },
            onDismiss = { showTemplates = false },
        )
    }
}

@Composable
private fun VariableChips(onInsert: (PromptVariable) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        PromptVariable.ALL.forEach { variable ->
            AssistChip(
                onClick = { onInsert(variable) },
                label = { Text(variable.token) },
            )
        }
    }
}

/**
 * Reports which placeholders the prompt actually uses, and warns — never blocks — when a
 * workflow that collects input never mentions it, which is the one mistake that produces a
 * confidently wrong answer instead of an error.
 */
@Composable
private fun PromptVariableSummary(prompt: String, input: InputSource) {
    val used = remember(prompt) { PromptTemplate.variablesIn(prompt) }
    val needsInput = input != InputSource.MANUAL && input != InputSource.NONE

    when {
        used.isEmpty() && needsInput && prompt.isNotBlank() -> Surface(
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fieldPadding(),
        ) {
            Text(
                text = "This prompt never mentions the input. Add {{input}} where the " +
                    "${input.label.lowercase()} should go, or the AI will answer without it.",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(14.dp),
            )
        }

        used.isNotEmpty() -> Text(
            text = "Uses ${used.joinToString { "{{$it}}" }}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
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
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded)
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
        modifier = Modifier.fieldPadding(),
    )

    Spacer(Modifier.height(12.dp))

    Column(Modifier.fieldPadding()) {
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

    Spacer(Modifier.height(12.dp))

    OutlinedTextField(
        value = maxTokens,
        onValueChange = onMaxTokensChange,
        label = { Text("Max tokens") },
        placeholder = { Text("Provider default") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fieldPadding(),
    )
}

/** One gutter for the whole form; [SectionHeader] already pads itself to the same 16dp. */
private fun Modifier.fieldPadding(): Modifier = this
    .fillMaxWidth()
    .padding(horizontal = 16.dp)
