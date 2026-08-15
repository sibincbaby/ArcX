package com.arcx.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * The dim behind a floating workflow panel.
 *
 * Named here because two hosts draw the same WorkflowPanelCard over it — the bubble's overlay
 * window and the runner's compact picker — and a scrim that is 0.32 in one file and something
 * else in another is exactly how the two surfaces drifted apart in the first place.
 *
 * It is deliberately a Compose colour rather than the window's `android:backgroundDimEnabled`.
 * A window dim is composited by the system, so it would still be in the frame while the runner
 * has stopped drawing for `takeScreenshot` — every vision workflow would send a dimmed picture.
 */
val PanelScrim = Color.Black.copy(alpha = 0.32f)

// Brand fallback for pre-S devices and for users who turn dynamic colour off.
private val ArcViolet = Color(0xFF5B4BD6)
private val ArcVioletLight = Color(0xFFC4BEFF)
private val ArcVioletDark = Color(0xFF241A6E)
private val ArcAmber = Color(0xFFB8621B)
private val ArcAmberLight = Color(0xFFFFB77C)

internal val ArcXLightColorScheme = lightColorScheme(
    primary = ArcViolet,
    onPrimary = Color.White,
    primaryContainer = ArcVioletLight,
    onPrimaryContainer = ArcVioletDark,
    secondary = Color(0xFF5D5C72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E0F9),
    onSecondaryContainer = Color(0xFF1A1A2C),
    tertiary = ArcAmber,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDBC7),
    onTertiaryContainer = Color(0xFF351000),
    background = Color(0xFFFCF8FF),
    onBackground = Color(0xFF1B1B21),
    surface = Color(0xFFFCF8FF),
    onSurface = Color(0xFF1B1B21),
    surfaceVariant = Color(0xFFE4E1EC),
    onSurfaceVariant = Color(0xFF47464F),
    outline = Color(0xFF787680),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

internal val ArcXDarkColorScheme = darkColorScheme(
    primary = ArcVioletLight,
    onPrimary = Color(0xFF2C2185),
    primaryContainer = Color(0xFF4335BD),
    onPrimaryContainer = Color(0xFFE4DFFF),
    secondary = Color(0xFFC6C4DD),
    onSecondary = Color(0xFF2F2F42),
    secondaryContainer = Color(0xFF454559),
    onSecondaryContainer = Color(0xFFE2E0F9),
    tertiary = ArcAmberLight,
    onTertiary = Color(0xFF552100),
    tertiaryContainer = Color(0xFF783200),
    onTertiaryContainer = Color(0xFFFFDBC7),
    background = Color(0xFF131318),
    onBackground = Color(0xFFE5E1E9),
    surface = Color(0xFF131318),
    onSurface = Color(0xFFE5E1E9),
    surfaceVariant = Color(0xFF47464F),
    onSurfaceVariant = Color(0xFFC8C5D0),
    outline = Color(0xFF928F99),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)
