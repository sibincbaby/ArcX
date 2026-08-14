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

    /**
     * Whether the app is exempt from Doze. A foreground service survives longer with it, though
     * it is not what decides whether a killed service is restarted.
     */
    fun isIgnoringBatteryOptimisation(): Boolean
    fun batteryOptimisationIntent(): Intent

    /**
     * Several OEMs — Xiaomi most aggressively — refuse to restart a killed process unless the app
     * is on their own autostart whitelist, which is why the bubble does not come back after the
     * user clears ArcX from Recents. That list is not reachable through any AOSP API, so this
     * returns the vendor screen when one is known and resolvable, and null otherwise.
     */
    fun autostartIntent(): Intent?

    /**
     * The "ArcX Actions" launcher icon, which opens the workflow list without going through the
     * app. It is what a home screen, a Samsung Edge panel, a Routine or a gesture binding can see,
     * and it is also a second icon in the drawer — so it has to be switchable.
     *
     * Not a stored preference: the component's own enabled state is the truth, and a preference
     * beside it could only ever disagree with the launcher.
     */
    fun isLauncherIconEnabled(): Boolean
    fun setLauncherIconEnabled(enabled: Boolean)

    /**
     * Whether the user has pointed the accessibility button, or the volume-key shortcut, at ArcX.
     * Assigned in system Settings like every other accessibility choice, so this only reports.
     */
    fun isAccessibilityButtonAssigned(): Boolean
}
