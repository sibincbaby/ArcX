package com.arcx.core.domain.capture

import android.content.Intent

/**
 * The permission state behind ArcX's out-of-app surfaces, so the Settings screen can show and
 * request them without depending on :integration:entrypoints directly.
 *
 * Both permissions here are user-granted in system Settings — neither can be requested with a
 * runtime dialog — so every method either reports state or hands back the Intent that opens
 * the right system screen.
 */
interface SystemSurfaces {
    /** Whether the accessibility service is enabled, which is what `{{screen_text}}` needs. */
    fun isScreenReadingEnabled(): Boolean
    fun screenReadingSettingsIntent(): Intent

    /** Whether SYSTEM_ALERT_WINDOW is granted, which the floating bubble needs. */
    fun isOverlayGranted(): Boolean
    fun overlaySettingsIntent(): Intent
}
