package com.arcx.feature.workflow

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.arcx.core.common.prompt.PromptTemplate
import com.arcx.core.common.prompt.PromptVariable
import com.arcx.core.designsystem.component.NoticeCard
import com.arcx.core.designsystem.component.NoticeSeverity
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.PromptTextStyle
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.designsystem.theme.tint
import com.arcx.core.model.InputSource
import com.arcx.core.model.WorkflowCategory

/** The icon tile beside the name field; WorkflowIcon derives its own corner from this. */
private val EditorIconSize = 52.dp

// ------------------------------------------------------------- step 2: what should it do

@Composable
internal fun PromptStep(
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

@Composable
private fun FieldError(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(start = Spacing.Xs, top = 6.dp),
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
