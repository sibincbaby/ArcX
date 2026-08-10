package com.arcx.integration.entrypoints.overlay

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/**
 * SYSTEM_ALERT_WINDOW is a "special" permission: it is never granted by a runtime dialog, only by
 * the user finding ArcX in a system list and flipping a switch. It can also be revoked at any time
 * while the bubble is running, so every path that touches the overlay re-checks rather than
 * trusting a cached answer.
 */
object OverlayPermission {

    fun isGranted(context: Context): Boolean = Settings.canDrawOverlays(context)

    /**
     * The package-scoped URI is what lands the user on ArcX's own row instead of the full list of
     * every installed app. Some OEM builds ignore it; the screen still opens, one tap further away.
     */
    fun settingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            "package:${context.applicationContext.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
