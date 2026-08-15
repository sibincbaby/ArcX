package com.arcx.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.arcx.core.model.WorkflowCategory

/** A category's tinted tile: a wash for the container, a readable colour for anything on it. */
@Immutable
data class WorkflowTint(val container: Color, val content: Color)

/**
 * One hue per category, all at roughly the same chroma so no category shouts louder than the
 * rest. A list of workflows is scanned by shape and colour long before the names are read.
 *
 * Hues are fixed rather than derived from the colour scheme because Material You gives us one
 * accent family, not eight distinguishable ones — asking it for eight would produce eight
 * near-identical washes. What *does* follow the scheme is how each hue is applied: the
 * container is the hue at low alpha over the current surface, and the content is pushed toward
 * white or black depending on which side of the theme we are on, so the same eight hues sit
 * correctly in light, in dark, and under any wallpaper.
 */
@Composable
fun WorkflowCategory.tint(): WorkflowTint {
    val base = when (this) {
        WorkflowCategory.WRITING -> Color(0xFF5B4BD6)
        WorkflowCategory.DEVELOPMENT -> Color(0xFF4E9CB8)
        WorkflowCategory.TRANSLATION -> Color(0xFFC9883F)
        WorkflowCategory.BUSINESS -> Color(0xFF6BB283)
        WorkflowCategory.EDUCATION -> Color(0xFF4577CE)
        WorkflowCategory.RESEARCH -> Color(0xFF3FA192)
        WorkflowCategory.PRODUCTIVITY -> Color(0xFFC96A55)
        WorkflowCategory.CUSTOM -> Color(0xFFC674A7)
    }
    return base.asTint()
}

/**
 * Reads the theme rather than `isSystemInDarkTheme()` so a dynamic scheme, a forced light theme
 * and a surface inside an overlay window all get the treatment that actually matches what is
 * behind the tile.
 *
 * Remembered because it is called once per row and [Color.luminance] is three `pow` calls: a
 * scrolling list was recomputing the same handful of tints on every frame to arrive at the same
 * eight answers. It only changes when the hue or the theme does.
 */
@Composable
fun Color.asTint(): WorkflowTint {
    val surface = MaterialTheme.colorScheme.surface
    return remember(this, surface) {
        val dark = surface.luminance() < 0.5f
        WorkflowTint(
            container = copy(alpha = if (dark) 0.22f else 0.16f),
            content = if (dark) lerp(this, Color.White, 0.45f) else lerp(this, Color.Black, 0.30f),
        )
    }
}

/** Warm amber for "this is off / this failed" — a warning, never an error. */
@Composable
fun warningTint(): WorkflowTint = Color(0xFFC9883F).asTint()
