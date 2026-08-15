package com.arcx.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

/**
 * Screen titles are set tight — display sizes at default tracking read as loose and childish
 * next to a dense list. Everything at body size keeps the platform's own metrics.
 */
internal val ArcXTypography = defaults.copy(
    headlineLarge = defaults.headlineLarge.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = defaults.headlineMedium.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = defaults.headlineSmall.copy(
        fontWeight = FontWeight.SemiBold,
        letterSpacing = (-0.3).sp,
    ),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    titleSmall = defaults.titleSmall.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** Monospace style for prompt editors and code output. */
val PromptTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)

/**
 * Monospace, small. Reserved for anything the engine reads or reports — durations, model ids,
 * variable tokens, an input→output pair — so a glance can tell machine facts from prose.
 */
val MetaTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Medium,
    fontSize = 11.sp,
    lineHeight = 14.sp,
)
