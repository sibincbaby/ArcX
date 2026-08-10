package com.arcx.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A workflow's emoji in a tinted tile. Emoji rather than icon assets keeps user-created
 * workflows expressive without shipping an icon picker or bundling a sprite sheet.
 */
@Composable
fun WorkflowIcon(
    emoji: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = emoji.ifBlank { "✨" },
            fontSize = (size.value * 0.45f).sp,
        )
    }
}
