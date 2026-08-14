package com.arcx.integration.entrypoints

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.app.StatusBarManager
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.glance.appwidget.updateAll
import com.arcx.core.domain.capture.SystemSurfaces
import com.arcx.integration.entrypoints.accessibility.ArcxAccessibility
import com.arcx.integration.entrypoints.overlay.OverlayPermission
import com.arcx.integration.entrypoints.overlay.OverlayService
import com.arcx.integration.entrypoints.tile.ArcxTileService
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

    override fun isIgnoringBatteryOptimisation(): Boolean =
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true

    /**
     * The direct request dialog, which needs REQUEST_IGNORE_BATTERY_OPTIMIZATIONS. If the OEM has
     * removed it — some have — fall back to the system list, which needs no permission and always
     * exists, rather than launching an Intent nothing handles.
     */
    override fun batteryOptimisationIntent(): Intent {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            "package:${context.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        return if (direct.resolves()) {
            direct
        } else {
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    override fun autostartIntent(): Intent? = AUTOSTART_SCREENS
        .asSequence()
        .map { (pkg, cls) ->
            Intent().setComponent(ComponentName(pkg, cls)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        // Vendor components come and go between OEM versions, so never trust the list — only ever
        // hand back something this device actually has an activity for.
        .firstOrNull { it.resolves() }

    /** DEFAULT means "whatever the manifest said", and the manifest ships it enabled. */
    override fun isLauncherIconEnabled(): Boolean =
        context.packageManager.getComponentEnabledSetting(actionsAlias()) !=
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED

    override fun setLauncherIconEnabled(enabled: Boolean) {
        context.packageManager.setComponentEnabledSetting(
            actionsAlias(),
            if (enabled) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            },
            // Without DONT_KILL_APP the platform restarts ArcX the moment the switch is flipped,
            // taking the settings screen the user is looking at — and the bubble — with it.
            PackageManager.DONT_KILL_APP,
        )
    }

    override fun isAccessibilityButtonAssigned(): Boolean =
        ArcxAccessibility.isButtonAssigned(context)

    override fun canAddQuickTile(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    override fun requestAddQuickTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) addQuickTile()
    }

    /**
     * The system owns the dialog and the outcome — it will refuse if the tile is already there, or
     * if the user has turned the prompt down often enough — so there is nothing useful to do with
     * the result callback that the dialog has not already told the user.
     */
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun addQuickTile() {
        val statusBar = context.getSystemService(StatusBarManager::class.java) ?: return
        runCatching {
            statusBar.requestAddTileService(
                ComponentName(context, ArcxTileService::class.java),
                context.getString(R.string.arcx_tile_label),
                Icon.createWithResource(context, R.drawable.ic_arcx_spark),
                { it.run() },
                { },
            )
        }
    }

    private fun actionsAlias(): ComponentName =
        ComponentName(context.packageName, ArcxDeepLinks.ACTIONS_ALIAS)

    private fun Intent.resolves(): Boolean =
        context.packageManager.resolveActivity(this, 0) != null

    private fun String.toUri(): Uri = Uri.parse(this)

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

/**
 * Vendor "autostart" / "background start" screens, most specific first. There is no API for any of
 * this; the component names are the only handle, and they are checked against the package manager
 * before use.
 */
private val AUTOSTART_SCREENS = listOf(
    // Xiaomi / MIUI / HyperOS
    "com.miui.securitycenter" to "com.miui.permcenter.autostart.AutoStartManagementActivity",
    // Oppo / Realme / ColorOS
    "com.coloros.safecenter" to "com.coloros.safecenter.permission.startup.StartupAppListActivity",
    "com.coloros.safecenter" to "com.coloros.safecenter.startupapp.StartupAppListActivity",
    // Vivo / Funtouch
    "com.vivo.permissionmanager" to "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
    // Huawei / Honor
    "com.huawei.systemmanager" to "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity",
    // Letv, Asus and others reuse these two often enough to be worth the two lines
    "com.letv.android.letvsafe" to "com.letv.android.letvsafe.AutobootManageActivity",
    "com.asus.mobilemanager" to "com.asus.mobilemanager.autostart.AutoStartActivity",
)
