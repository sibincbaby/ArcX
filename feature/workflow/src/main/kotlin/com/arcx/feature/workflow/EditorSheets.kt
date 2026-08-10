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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcx.core.designsystem.component.WorkflowIcon

/**
 * A curated grid rather than a system emoji picker: the point is to choose an identity in two
 * taps, and the sixty or so glyphs people actually reach for when naming an action fit on one
 * screen. Free text is still allowed, so nobody is trapped by the curation.
 */
private val ICON_CHOICES = listOf(
    "✨", "💡", "🚀", "🎯", "⚡", "🔥", "🌟", "🎨",
    "✍️", "📝", "📄", "📚", "🗒️", "📌", "🔖", "🧾",
    "💻", "🐞", "🧪", "🔧", "🧩", "🗄️", "🔀", "🖥️",
    "🔍", "🔎", "🕵️", "📊", "📈", "🧠", "🤖", "🧮",
    "💬", "📣", "✉️", "📬", "📞", "🗣️", "🤝", "💼",
    "🌍", "🈯", "🔤", "📖", "🎓", "🧑‍🏫", "❓", "✅",
    "⏱️", "📅", "🗓️", "☕", "🍳", "🛒", "⚖️", "🩺",
    "🎬", "🎵", "📷", "🏷️", "♻️", "✂️",
)

/**
 * [onChange] fires on every edit; only tapping a tile closes the sheet, so typing a glyph by
 * hand does not dismiss it after the first keystroke.
 */
@Composable
internal fun EmojiPickerSheet(
    current: String,
    onChange: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            Text("Pick an icon", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.heightIn(max = 280.dp),
                contentPadding = PaddingValues(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(ICON_CHOICES) { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 24.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clickable {
                                onChange(emoji)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp)
                            .fillMaxWidth(),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = current,
                onValueChange = onChange,
                singleLine = true,
                label = { Text("Or type any emoji") },
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
                    WorkflowIcon(emoji = template.emoji, size = 40.dp)
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
