package com.arcx.feature.workflow

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.designsystem.component.ArcxAction
import com.arcx.core.designsystem.component.StepBar
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.stepEnter
import com.arcx.core.designsystem.theme.stepExit
import com.arcx.core.model.InputSource
import com.arcx.core.model.OutputTarget
import com.arcx.core.model.WorkflowCategory

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

// ------------------------------------------------------------------------------- shared

@Composable
internal fun StepTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.padding(top = 14.dp),
    )
}

@Composable
internal fun StepBody(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
