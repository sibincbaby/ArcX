package com.arcx.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val defaults = Typography()

internal val ArcXTypography = defaults.copy(
    headlineSmall = defaults.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = defaults.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = defaults.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    labelLarge = defaults.labelLarge.copy(fontWeight = FontWeight.Medium),
)

/** Monospace style for prompt editors and code output. */
val PromptTextStyle: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 14.sp,
    lineHeight = 20.sp,
)
