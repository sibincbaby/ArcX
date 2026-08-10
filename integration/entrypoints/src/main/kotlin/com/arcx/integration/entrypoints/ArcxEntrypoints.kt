package com.arcx.integration.entrypoints

import android.content.Context
import android.content.Intent
import androidx.glance.appwidget.updateAll
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.integration.entrypoints.accessibility.ArcxAccessibility
import com.arcx.integration.entrypoints.overlay.OverlayPermission
import com.arcx.integration.entrypoints.overlay.OverlayService
import com.arcx.integration.entrypoints.widget.FavoritesWidget
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One injectable surface for the settings screen, so it never has to know that the bubble is a
 * service, that accessibility is a Secure setting, or that the widget is Glance.
 *
 * Nothing here caches: both permissions are revocable from system Settings while ArcX is in the
 * background, so a cached answer would be wrong exactly when the user came back to fix it.
 */
@Singleton
class ArcxEntrypoints @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemSurfaces {

    /** True when the user has switched ArcX on in Settings > Accessibility. */
    override fun isScreenReadingEnabled(): Boolean = ArcxAccessibility.isEnabled(context)

    override fun screenReadingSettingsIntent(): Intent = ArcxAccessibility.settingsIntent()

    /** True when ArcX may draw over other apps, which the bubble cannot work without. */
    override fun isOverlayGranted(): Boolean = OverlayPermission.isGranted(context)

    override fun overlaySettingsIntent(): Intent = OverlayPermission.settingsIntent(context)

    /**
     * Mirrors the bubble switch. The caller still owns persisting `UserSettings.bubbleEnabled` —
     * this only brings the running service in line with it.
     */
    fun setBubbleRunning(running: Boolean) {
        if (running) OverlayService.start(context) else OverlayService.stop(context)
    }

    /**
     * Redraws every placed widget. Worth calling after a favourite changes: the widget declares no
     * update period, so nothing else will.
     */
    suspend fun refreshFavoritesWidget() {
        runCatching { FavoritesWidget().updateAll(context) }
    }
}
