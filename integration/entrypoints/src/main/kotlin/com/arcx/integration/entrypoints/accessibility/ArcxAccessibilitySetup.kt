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
    fun isEnabled(context: Context): Boolean =
        context.listsArcx(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

    /**
     * Whether the accessibility button — floating, in the nav bar, or held on both volume keys —
     * is pointed at ArcX. Purely for display: the assignment is made in system Settings.
     *
     * These two keys have no public constants. Reading them is allowed and their names have been
     * stable since the button gained multiple targets, but an OEM that renames them makes this
     * report "not assigned", which is the harmless direction to be wrong in.
     */
    fun isButtonAssigned(context: Context): Boolean =
        context.listsArcx(BUTTON_TARGETS) || context.listsArcx(SHORTCUT_TARGET)

    /** True when [key] holds a colon-separated component list containing ArcX's service. */
    private fun Context.listsArcx(key: String): Boolean {
        val expected = ComponentName(
            applicationContext.packageName,
            ArcxAccessibilityService::class.java.name,
        )
        val value = runCatching {
            Settings.Secure.getString(contentResolver, key)
        }.getOrNull() ?: return false

        // The platform's own splitter, so an empty or malformed entry cannot throw here.
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(value)
        return splitter.any { ComponentName.unflattenFromString(it) == expected }
    }

    private const val BUTTON_TARGETS = "accessibility_button_targets"
    private const val SHORTCUT_TARGET = "accessibility_shortcut_target_service"

    /**
     * There is no way to deep-link to one service's row, and no way to request the permission from
     * code — accessibility is granted by the user in Settings, on purpose. The best we can do is
     * open the list and tell them what to look for.
     */
    fun settingsIntent(): Intent =
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
