package com.arcx.integration.entrypoints.accessibility

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * What Settings needs to explain the accessibility service to the user: is it on, and how do they
 * get to the switch.
 */
object ArcxAccessibility {

    /**
     * Reads the user's *preference* from Secure settings rather than asking the holder.
     *
     * The two answers differ, and the difference matters for UI: right after the user flips the
     * switch the setting is on but the system has not bound the service yet, and after a crash the
     * binding is gone while the setting stays on. A settings screen should reflect the switch;
     * anything about to actually read the screen should ask
     * [com.arcx.core.domain.capture.ScreenContextProvider.isAvailable] instead.
     */
    fun isEnabled(context: Context): Boolean {
        val expected = ComponentName(
            context.applicationContext.packageName,
            ArcxAccessibilityService::class.java.name,
        )
        val enabled = runCatching {
            Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            )
        }.getOrNull() ?: return false

        // The value is a colon-separated list of flattened component names, and the platform's own
        // splitter is used so an empty or malformed entry cannot throw here.
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        return splitter.any { ComponentName.unflattenFromString(it) == expected }
    }

    /**
     * There is no way to deep-link to one service's row, and no way to request the permission from
     * code — accessibility is granted by the user in Settings, on purpose. The best we can do is
     * open the list and tell them what to look for.
     */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
