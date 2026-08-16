package com.arcx.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.arcx.core.model.UserSettings

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

/**
 * What makes ArcX's floating surfaces read as panes of glass rather than as cards.
 *
 * Every value here is painted by Compose, inside ArcX's own content. That is not incidental: the
 * hosts that draw these surfaces blank their content so `takeScreenshot` can photograph the app
 * behind them, and anything the *system* composites — a window blur, `backgroundDimEnabled` —
 * survives that blanking and lands in the picture a vision workflow sends to the model. So the
 * glass is painted, never asked for from the window. See [PanelScrim], for the same reason.
 *
 * How far through you can see is the user's, in `UserSettings.popupTransparency`; these are the
 * two overlays that make the result read as glass at any setting. Both are deliberately quiet —
 * they have to survive being drawn over a photograph, and a strong highlight over a busy backdrop
 * reads as dirt on the screen rather than as a reflection.
 */
object Glass {

    /**
     * A soft top-down sheen across the whole surface. White rather than a scheme colour on
     * purpose — glass catches the light the same way whatever is behind it, and a tinted highlight
     * reads as a gradient someone chose rather than as a reflection.
     */
    val SheenTop = Color.White.copy(alpha = 0.07f)

    /**
     * The other end of the sheen. White at zero alpha rather than [Color.Transparent], which is
     * transparent *black* — a gradient interpolates both ends, so ending on it would drag a grey
     * through the middle of the panel.
     */
    val SheenBottom = Color.White.copy(alpha = 0f)

    /**
     * The lit edge. This is what actually sells the effect — more than the translucency does — so
     * it is brightest along the top, where a pane would catch the light, and nearly gone by the
     * bottom.
     */
    val EdgeTop = Color.White.copy(alpha = 0.42f)
    val EdgeBottom = Color.White.copy(alpha = 0.08f)

    /**
     * Hairline. Here with the colours rather than in [Spacing] because it is one half of a single
     * visual token — an edge is a width and a brightness together, and the 4dp grid has nothing to
     * say about a 1dp rim.
     */
    val EdgeWidth = 1.dp
}

/**
 * How see-through ArcX's floating surfaces are, 0 being solid.
 *
 * A composition local rather than a parameter on every popup, because the two things that need it
 * are a long way apart: the value comes from settings, which only the three hosts that call
 * [ArcXTheme] can read, and it is needed by leaf components in this module that must not know
 * settings exist. Threading it by hand meant a parameter on the panel, the picker, the popup and
 * the sheet, and one of them would have been forgotten.
 *
 * Static, not dynamic: it changes when the user drags a slider in Settings and never otherwise, so
 * re-composing the subtree that reads it is cheaper than tracking every read.
 */
val LocalPopupTransparency = staticCompositionLocalOf { UserSettings().popupTransparency }

/**
 * The colour a floating surface is filled with: its own scheme colour, opened up by whatever the
 * user set.
 *
 * Every glass surface goes through here so there is one place where transparency becomes alpha,
 * and so a host that forgets is a surface that stays solid rather than one that drifts.
 */
@Composable
@ReadOnlyComposable
fun glassColor(base: Color): Color = base.copy(alpha = 1f - LocalPopupTransparency.current)

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
