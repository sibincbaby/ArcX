package com.arcx.core.designsystem.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.arcx.core.model.UserSettings

/** Which theme the user picked in Settings. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

@Composable
fun ArcXTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    /**
     * How see-through ArcX's floating popups are; see [LocalPopupTransparency]. Pushed in by the
     * host like the two above, because the theme has no way to read settings from a Service.
     */
    popupTransparency: Float = UserSettings().popupTransparency,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        // Material You is only available from S; below that fall back to the brand palette.
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> ArcXDarkColorScheme
        else -> ArcXLightColorScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            // Nullable on purpose: this theme is also composed from the bubble's overlay, whose
            // view context is the Service, not an Activity. A hard cast there is a
            // ClassCastException on first composition — which is why the overlay used to carry a
            // forked copy of this whole function. An overlay window has no status bar of its own
            // to tint, so having nothing to do here is the correct outcome, not a degraded one.
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            }
        }
    }

    // Clamped here as well as in the repository. This is the last gate before the value becomes an
    // alpha, and it is the one that also covers a caller passing a literal.
    val transparency = popupTransparency.coerceIn(0f, UserSettings.POPUP_MAX_TRANSPARENCY)
    CompositionLocalProvider(LocalPopupTransparency provides transparency) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = ArcXTypography,
            shapes = ArcXShapes,
            content = content,
        )
    }
}
