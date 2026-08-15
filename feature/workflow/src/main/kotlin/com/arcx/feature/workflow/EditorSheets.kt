@file:OptIn(ExperimentalMaterial3Api::class)

package com.arcx.feature.workflow

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.SectionLabel
import com.arcx.core.designsystem.component.WorkflowIcon
import com.arcx.core.designsystem.component.WorkflowIcons
import com.arcx.core.designsystem.component.workflowIconFor

/**
 * A grid of the icons a workflow can wear, grouped so the right one is found by scanning rather
 * than by reading fifty labels.
 *
 * The text field below is the escape hatch that used to be the whole picker: workflows carried
 * an emoji before this set existed, and someone whose 🍳 means something to them should not have
 * it taken away by an update. Anything typed here is stored and drawn as-is.
 */
@Composable
internal fun IconPickerSheet(
    current: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Pick an icon", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                modifier = Modifier.heightIn(max = 320.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                WorkflowIcons.groupBy { it.group }.forEach { (group, options) ->
                    item(span = { GridItemSpan(maxLineSpan) }, key = "group-$group") {
                        SectionLabel(group, Modifier.padding(horizontal = 0.dp))
                    }
                    items(options, key = { it.key }) { option ->
                        val selected = option.key == current
                        WorkflowIcon(
                            icon = option.key,
                            size = 48.dp,
                            container = if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            content = if (selected) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClickLabel = option.label) {
                                    onChange(option.key)
                                    onDismiss()
                                },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = if (workflowIconFor(current) != null) "" else current,
                onValueChange = onChange,
                singleLine = true,
                label = { Text("Or type an emoji") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
internal fun PromptTemplateSheet(
    onPick: (PromptTemplateOption) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text("Start from a template", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Each one is a complete prompt. Pick the closest, then change whatever you like.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            PROMPT_TEMPLATES.forEach { template ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onPick(template) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WorkflowIcon(icon = template.icon, size = 40.dp)
                    Column(Modifier.padding(start = 14.dp)) {
                        Text(template.title, style = MaterialTheme.typography.titleSmall)
                        Text(
                            template.summary,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
