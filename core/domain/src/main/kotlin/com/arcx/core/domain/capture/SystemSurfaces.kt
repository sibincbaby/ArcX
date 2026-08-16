package com.arcx.core.domain.capture

import android.content.Intent
import com.arcx.core.model.Workflow

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

    /**
     * Whether the service is not merely switched on but actually bound and running.
     *
     * These come apart, and when they do the user is stranded: several OEMs kill the process
     * without clearing the setting, so Android keeps reporting the service as enabled while
     * nothing is listening. Screen reading and screenshots then fail with the app insisting the
     * permission is granted — which it is. The remedy is to toggle it off and on, and nothing can
     * suggest that unless it can tell the two apart.
     */
    fun isScreenReadingRunning(): Boolean

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

    /**
     * Whether ArcX may post notifications at all.
     *
     * Asked of the notification manager rather than of the POST_NOTIFICATIONS permission, because
     * the permission only exists from Android 13 and the user can switch notifications off in app
     * settings on every version — the manager's answer is the one that decides whether a workflow's
     * notification output will actually appear.
     *
     * It is on the port rather than remembered in a composable because it outlives the screen that
     * asks: a refusal in one session left the next one showing "Not allowed yet" beside a button
     * that could do nothing. Home reads the same port and can show it too.
     */
    fun areNotificationsEnabled(): Boolean

    /**
     * ArcX's own page in system Settings — the only route left once Android has stopped showing
     * the permission dialog.
     */
    fun notificationSettingsIntent(): Intent

    /**
     * The Quick Settings tile. It has always been installable, but only by pulling the shade down,
     * finding the edit button and scrolling a list of unfamiliar tiles — so in practice nobody
     * found it. Android 13 added a system prompt that does it in one tap; below that there is no
     * API and [canAddQuickTile] is false, which is the screen's cue to explain the manual route
     * instead of offering a button that cannot work.
     */
    fun canAddQuickTile(): Boolean
    fun requestAddQuickTile()

    /**
     * Whether this launcher will accept a pinned shortcut at all. Some third-party ones do not, and
     * offering a menu item that can only ever do nothing is worse than not offering it.
     */
    fun canPinShortcut(): Boolean

    /**
     * Asks the launcher to add a home-screen icon that runs [workflow] directly — no picker, no
     * ArcX window in between, since the icon carries `arcx://run/{id}`.
     *
     * The launcher owns the confirmation dialog and the outcome, so there is nothing to report
     * back: returns false only when the request could not be made at all.
     */
    fun pinWorkflowShortcut(workflow: Workflow): Boolean
}
