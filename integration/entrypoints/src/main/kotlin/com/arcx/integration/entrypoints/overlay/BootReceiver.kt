package com.arcx.integration.entrypoints.overlay

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.arcx.integration.entrypoints.di.entrypointsGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Brings the bubble back after a reboot or an app update, which both silently kill the service
 * while leaving the user's preference switched on.
 *
 * Two conditions, both required: the user still wants the bubble, and the overlay permission is
 * still granted. Restarting on the strength of the stored preference alone would put a service in
 * the foreground that can never draw anything.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }
        if (!OverlayPermission.isGranted(context)) return

        // Reading the preference is a disk hop, which is longer than a receiver's synchronous
        // budget; goAsync keeps the process alive across it.
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val settings = appContext.entrypointsGraph().settingsRepository()
                if (settings.current().bubbleEnabled) OverlayService.start(appContext)
            } catch (_: Exception) {
                // A boot broadcast that cannot reach the datastore is not worth crashing over; the
                // bubble comes back the next time the user opens ArcX.
            } finally {
                pending.finish()
            }
        }
    }
}
