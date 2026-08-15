package com.arcx.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
 * A workflow's icon in a tinted tile.
 *
 * [icon] is a key from [WorkflowIcons]. Anything else is drawn as text, which is deliberate:
 * workflows carried an emoji before this set existed, and a library that silently replaced
 * somebody's 🍳 with a generic sparkle would be worse than one that keeps drawing 🍳.
 */
@Composable
fun WorkflowIcon(
    icon: String,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size / 3))
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        val vector = workflowIconFor(icon)
        if (vector != null) {
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(size * 0.52f),
            )
        } else {
            Text(
                text = icon.ifBlank { "✨" },
                fontSize = (size.value * 0.45f).sp,
            )
        }
    }
}
