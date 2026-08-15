@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.workflow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.arcx.core.common.prompt.PromptTemplate
import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.designsystem.component.ArcxAction
import com.arcx.core.designsystem.component.NoticeCard
import com.arcx.core.designsystem.component.NoticeSeverity
import com.arcx.core.designsystem.component.StepBar
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.PromptTextStyle
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.stepEnter
import com.arcx.core.designsystem.theme.stepExit
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.ProviderConfig
import com.arcx.core.model.WorkflowCategory
import kotlin.math.round

/**
 * The builder, as three questions rather than one long form: what sets it off, what should it
 * do, where does the answer land.
 *
 * It was a single scrolling form, and the form was honest but silent — nothing on it said what
 * a workflow *is*, so people filled in a name and a prompt and left the two fields that decide
 * whether the thing works at all on their defaults. Three steps put each decision on its own
 * screen and end with the whole workflow written back as one sentence.
 *
 * Editing is not made to walk the three steps: Save is in the header from the first one.
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

    var step by rememberSaveable { mutableIntStateOf(0) }
    var confirmDiscard by remember { mutableStateOf(false) }
    val leave = { if (viewModel.isDirty) confirmDiscard = true else onBack() }

    BackHandler(enabled = true) { if (step > 0) step-- else leave() }

    WorkflowEditorScreen(
        state = state,
        step = step,
        modifier = modifier,
        onStepChange = { step = it },
        onBack = leave,
        onSave = viewModel::save,
        onNameChange = viewModel::setName,
        onIconChange = viewModel::setIcon,
        onCategoryChange = viewModel::setCategory,
        onInputChange = viewModel::setInput,
        onPromptChange = viewModel::setPrompt,
        onInsertVariable = viewModel::insertVariable,
        onApplyTemplate = viewModel::applyTemplate,
        onSampleTextChange = viewModel::setSampleText,
        onRunTest = viewModel::runTest,
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

/** The icon tile beside the name field; WorkflowIcon derives its own corner from this. */
private val EditorIconSize = 52.dp

private const val STEP_COUNT = 3
private const val STEP_TRIGGER = 0
private const val STEP_PROMPT = 1
private const val STEP_OUTPUT = 2

@Composable
private fun WorkflowEditorScreen(
    state: WorkflowEditorState,
    step: Int,
    onStepChange: (Int) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onNameChange: (String) -> Unit,
    onIconChange: (String) -> Unit,
    onCategoryChange: (WorkflowCategory) -> Unit,
    onInputChange: (InputSource) -> Unit,
    onPromptChange: (TextFieldValue) -> Unit,
    onInsertVariable: (PromptVariable) -> Unit,
    onApplyTemplate: (PromptTemplateOption) -> Unit,
    onSampleTextChange: (String) -> Unit,
    onRunTest: () -> Unit,
    onProviderChange: (String?) -> Unit,
    onModelChange: (String) -> Unit,
    onLoadModels: () -> Unit,
    onOutputChange: (OutputTarget) -> Unit,
    onSystemPromptChange: (String) -> Unit,
    onTemperatureChange: (Float?) -> Unit,
    onMaxTokensChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showIconPicker by remember { mutableStateOf(false) }
    var showTemplates by remember { mutableStateOf(false) }

    // One scroll state serves all three steps, so moving between them has to put the new
    // question back at the top — otherwise step 3 opens halfway down, with its own heading
    // already scrolled away.
    val scrollState = rememberScrollState()
    LaunchedEffect(step) { scrollState.scrollTo(0) }

    val saveLabel = if (state.forksBuiltIn) "Save a copy" else "Save workflow"
    val incomplete = state.name.isBlank() || state.prompt.text.isBlank()

    // Save always runs the ViewModel's own validation; this only decides where the user is
    // standing when the errors light up.
    val save = {
        if (incomplete) onStepChange(STEP_PROMPT)
        onSave()
    }

    Scaffold(
        // imePadding on the Scaffold, not on the scrolling content: the footer is a bottomBar,
        // and padding only the content would leave Continue stranded behind the keyboard while
        // the form above it shrank to a slot too short to reach the prompt box in.
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        topBar = {
            EditorHeader(
                title = if (state.isNew) "New workflow" else "Edit workflow",
                step = step,
                showSave = !state.isNew && step != STEP_OUTPUT,
                onLeading = { if (step > 0) onStepChange(step - 1) else onBack() },
                onSave = save,
            )
        },
        bottomBar = {
            EditorFooter(
                step = step,
                saveLabel = saveLabel,
                saving = state.saving,
                canTest = step == STEP_PROMPT,
                testing = state.test is TestRunState.Running,
                onTest = onRunTest,
                onContinue = {
                    if (step == STEP_PROMPT && incomplete) save() else onStepChange(step + 1)
                },
                onSave = save,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = Spacing.Gutter),
        ) {
            // The three steps used to replace each other in one frame, which left the only
            // thing that says "you moved forward" — the progress bar — doing it alone. The
            // slide carries the direction, so going back reads as going back.
            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val forward = targetState > initialState
                    (stepEnter(forward) togetherWith stepExit(forward))
                        .using(SizeTransform(clip = false))
                },
                label = "editor-step",
            ) { current ->
                Column {
                    when (current) {
                        STEP_TRIGGER -> TriggerStep(state = state, onInputChange = onInputChange)

                        STEP_PROMPT -> PromptStep(
                            state = state,
                            onNameChange = onNameChange,
                            onPickIcon = { showIconPicker = true },
                            onCategoryChange = onCategoryChange,
                            onPromptChange = onPromptChange,
                            onInsertVariable = onInsertVariable,
                            onOpenTemplates = { showTemplates = true },
                            onSampleTextChange = onSampleTextChange,
                        )

                        else -> OutputStep(
                            state = state,
                            onOutputChange = onOutputChange,
                            onProviderChange = onProviderChange,
                            onModelChange = onModelChange,
                            onLoadModels = onLoadModels,
                            onSystemPromptChange = onSystemPromptChange,
                            onTemperatureChange = onTemperatureChange,
                            onMaxTokensChange = onMaxTokensChange,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Spacing.Xxxl))
        }
    }

    if (showIconPicker) {
        IconPickerSheet(
            current = state.icon,
            onChange = onIconChange,
            onDismiss = { showIconPicker = false },
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
private fun EditorHeader(
    title: String,
    step: Int,
    showSave: Boolean,
    onLeading: () -> Unit,
    onSave: () -> Unit,
) {
    Column(Modifier.padding(bottom = Spacing.Sm)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spacing.Sm, end = Spacing.Sm, top = Spacing.Sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onLeading) {
                Icon(
                    imageVector = if (step == 0) {
                        Icons.Outlined.Close
                    } else {
                        Icons.AutoMirrored.Filled.ArrowBack
                    },
                    contentDescription = if (step == 0) "Close" else "Back",
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (showSave) {
                TextButton(onClick = onSave) { Text("Save") }
            }
            Text(
                text = "${step + 1} of $STEP_COUNT",
                style = MetaTextStyle,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = Spacing.Md),
            )
        }
        StepBar(
            current = step,
            total = STEP_COUNT,
            modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = Spacing.Sm),
        )
    }
}

@Composable
private fun EditorFooter(
    step: Int,
    saveLabel: String,
    saving: Boolean,
    canTest: Boolean,
    testing: Boolean,
    onTest: () -> Unit,
    onContinue: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.Gutter)
            // 22dp bottom, off the scale: it clears the gesture bar, which is not a gap between
            // two things and so is not on the spacing grid.
            .padding(top = Spacing.Md, bottom = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (canTest) {
            ArcxAction(
                onClick = onTest,
                enabled = !testing,
                contentDescription = "Try this prompt",
                // Only the height is stated, and only because this stands beside the 50dp
                // Continue button. The width is left to ArcxAction so its 48dp floor keeps the
                // icon centred — and keeps it centred when the spinner, which is smaller, takes
                // its place mid-run.
                modifier = Modifier.height(50.dp),
            ) {
                if (testing) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
        Button(
            onClick = if (step == STEP_OUTPUT) onSave else onContinue,
            enabled = !saving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .weight(1f)
                .height(50.dp),
        ) {
            if (step == STEP_OUTPUT) {
                Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(Spacing.Sm))
                Text(saveLabel)
            } else {
                Text("Continue")
                Spacer(Modifier.width(Spacing.Sm))
                Icon(
                    Icons.AutoMirrored.Outlined.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------- step 1: what sets it off

@Composable
private fun TriggerStep(
    state: WorkflowEditorState,
    onInputChange: (InputSource) -> Unit,
) {
    StepTitle("What sets it off?")
    StepBody("Pick where this workflow's content comes from. You can change it later.")

    Spacer(Modifier.height(18.dp))
    InputSource.entries.forEach { source ->
        ChoiceCard(
            selected = state.input == source,
            onClick = { onInputChange(source) },
            modifier = Modifier.padding(bottom = 9.dp),
        ) {
            Icon(
                imageVector = source.icon,
                contentDescription = null,
                tint = if (state.input == source) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .padding(top = 1.dp)
                    .size(21.dp),
            )
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(source.label, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = source.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = if (state.input == source) {
                    Icons.Outlined.RadioButtonChecked
                } else {
                    Icons.Outlined.RadioButtonUnchecked
                },
                contentDescription = null,
                tint = if (state.input == source) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.outline
                },
                modifier = Modifier.size(20.dp),
            )
        }
    }

    Spacer(Modifier.height(Spacing.Md))
    WiringPreview(state)
}

/** The workflow's shape so far, in the order it will actually happen. */
@Composable
private fun WiringPreview(state: WorkflowEditorState) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(horizontal = 15.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = state.input.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(11.dp))
            Text(
                text = "${state.input.label}  →  prompt  →  ${state.output.label}",
                style = MetaTextStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ------------------------------------------------------------- step 2: what should it do

@Composable
private fun PromptStep(
    state: WorkflowEditorState,
    onNameChange: (String) -> Unit,
    onPickIcon: () -> Unit,
    onCategoryChange: (WorkflowCategory) -> Unit,
    onPromptChange: (TextFieldValue) -> Unit,
    onInsertVariable: (PromptVariable) -> Unit,
    onOpenTemplates: () -> Unit,
    onSampleTextChange: (String) -> Unit,
) {
    StepTitle("What should it do?")

    Spacer(Modifier.height(Spacing.Lg))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box {
            WorkflowIcon(
                icon = state.icon,
                size = EditorIconSize,
                container = state.category.tint().container,
                content = state.category.tint().content,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    // Not a theme radius: WorkflowIcon clips itself at size/3, and the ripple has
                    // to follow that same curve or it corners past the tile it is drawn on.
                    .clip(RoundedCornerShape(EditorIconSize / 3))
                    .clickable(
                        onClick = onPickIcon,
                        onClickLabel = "Change icon",
                        role = Role.Button,
                    ),
            )
            Icon(
                imageVector = Icons.Outlined.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    // Half the size: a circle, not a corner tier.
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(3.dp),
            )
        }
        Spacer(Modifier.width(11.dp))
        TextField(
            value = state.name,
            onValueChange = onNameChange,
            singleLine = true,
            placeholder = { Text("Name it") },
            isError = state.nameError,
            shape = MaterialTheme.shapes.medium,
            colors = flatFieldColors(),
            modifier = Modifier.weight(1f),
        )
    }
    if (state.nameError) {
        FieldError("Give it a name so you can find it later")
    }

    Spacer(Modifier.height(14.dp))
    FormDropdown(
        label = "Category",
        selected = state.category,
        options = WorkflowCategory.entries,
        optionLabel = { it.label },
        onSelect = onCategoryChange,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.height(Spacing.Xl))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "Prompt",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AssistChip(
            onClick = onOpenTemplates,
            shape = MaterialTheme.shapes.small,
            label = { Text("Templates") },
            leadingIcon = {
                Icon(Icons.Outlined.Bookmarks, contentDescription = null, Modifier.size(15.dp))
            },
        )
    }

    Spacer(Modifier.height(10.dp))
    PromptEditor(
        prompt = state.prompt,
        isError = state.promptError,
        onPromptChange = onPromptChange,
        onInsertVariable = onInsertVariable,
    )
    if (state.promptError) {
        FieldError("A workflow needs a prompt")
    }
    PromptVariableSummary(prompt = state.prompt.text, input = state.input)

    Spacer(Modifier.height(Spacing.Xl))
    Text(
        text = "Try it",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(Spacing.Sm))
    TestPanel(
        sampleText = state.sampleText,
        test = state.test,
        onSampleTextChange = onSampleTextChange,
    )
}

/**
 * The prompt box and its variable chips share one card, because the chips only make sense as
 * things that go *into* the box above them.
 */
@Composable
private fun PromptEditor(
    prompt: TextFieldValue,
    isError: Boolean,
    onPromptChange: (TextFieldValue) -> Unit,
    onInsertVariable: (PromptVariable) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (isError) {
            androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            TextField(
                value = prompt,
                onValueChange = onPromptChange,
                placeholder = { Text("What should the AI do?") },
                textStyle = PromptTextStyle,
                minLines = 5,
                colors = flatFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = Spacing.Md, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PromptVariable.ALL.forEach { variable ->
                    VariableChip(variable.name) { onInsertVariable(variable) }
                }
            }
        }
    }
}

@Composable
private fun VariableChip(name: String, onClick: () -> Unit) {
    Text(
        text = name,
        style = MetaTextStyle,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            // The chip stays chip-sized; the floor only reserves the 48dp around it, which is
            // what a thumb needs and what the shared primitives already give every other pill.
            .minimumInteractiveComponentSize()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(
                onClick = onClick,
                onClickLabel = "Insert {{$name}}",
                role = Role.Button,
            )
            .padding(horizontal = 9.dp, vertical = 6.dp),
    )
}

/**
 * A real run of the unsaved form, through the same use case every entry point uses. Nothing is
 * recorded — see [WorkflowEditorViewModel.runTest] — so this can be leant on freely.
 */
@Composable
private fun TestPanel(
    sampleText: String,
    test: TestRunState,
    onSampleTextChange: (String) -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(vertical = Spacing.Xs)) {
            TextField(
                value = sampleText,
                onValueChange = onSampleTextChange,
                placeholder = { Text("Paste something to run it against") },
                textStyle = MaterialTheme.typography.bodyMedium,
                minLines = 2,
                colors = flatFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )

            when (test) {
                TestRunState.Idle -> Unit

                TestRunState.Running -> Row(
                    Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Running…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                is TestRunState.Done -> Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    Row(Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md)) {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .size(15.dp),
                        )
                        Spacer(Modifier.width(9.dp))
                        Text(
                            text = test.text.ifBlank { "(nothing came back)" },
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Text(
                        text = "${test.durationMs / 1000.0}s · ${test.model}",
                        style = MetaTextStyle,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(start = Spacing.Lg, bottom = Spacing.Md),
                    )
                }

                is TestRunState.Failed -> Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainerHigh)
                    Text(
                        text = test.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(Spacing.Lg),
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------ step 3: where does it land

@Composable
private fun OutputStep(
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
            modifier = Modifier.padding(bottom = 9.dp),
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

// ------------------------------------------------------------------------------- shared

@Composable
private fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
private fun StepBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = Spacing.Xs, top = 6.dp),
    )
}

/** A card that is a radio button: the whole thing is the target, not a 20dp circle. */
@Composable
private fun ChoiceCard(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = MaterialTheme.shapes.large
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minimumInteractiveComponentSize()
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
        verticalAlignment = Alignment.Top,
        content = content,
    )
}

/** Fields that sit inside a card of their own already; the M3 underline would be a second box. */
@Composable
private fun flatFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/**
 * Reports which placeholders the prompt actually uses, and warns — never blocks — when a
 * workflow that collects input never mentions it, which is the one mistake that produces a
 * confidently wrong answer instead of an error.
 */
@Composable
private fun PromptVariableSummary(prompt: String, input: InputSource) {
    val used = remember(prompt) { PromptTemplate.variablesIn(prompt) }
    // Attachment inputs ride alongside the prompt as image or file parts rather than being
    // substituted into it, so a prompt with no placeholder is correct for them — telling
    // someone to add {{input}} to a screenshot workflow would be advice that breaks it.
    val needsInput = input !in INPUTS_WITHOUT_PLACEHOLDER

    when {
        used.isEmpty() && needsInput && prompt.isNotBlank() -> {
            Spacer(Modifier.height(10.dp))
            NoticeCard(
                severity = NoticeSeverity.Warning,
                message = "This prompt never mentions the input. Add {{input}} where the " +
                    "${input.label.lowercase()} should go, or the AI will answer without it.",
            )
        }

        used.isNotEmpty() -> Text(
            text = "Uses ${used.joinToString { "{{$it}}" }}",
            style = MetaTextStyle,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = Spacing.Xs, top = Spacing.Sm),
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

/**
 * Inputs the model receives as a separate part — an image, a document, audio — plus the two that
 * carry no content of their own. None of them belong in the prompt text.
 */
private val INPUTS_WITHOUT_PLACEHOLDER = setOf(
    InputSource.MANUAL,
    InputSource.NONE,
    InputSource.SCREENSHOT,
    InputSource.IMAGE,
    InputSource.PDF,
    InputSource.CAMERA,
    InputSource.AUDIO,
)
