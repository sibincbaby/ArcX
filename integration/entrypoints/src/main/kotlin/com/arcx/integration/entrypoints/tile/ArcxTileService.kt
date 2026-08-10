package com.arcx.integration.entrypoints.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.arcx.integration.entrypoints.ArcxDeepLinks

/**
 * The Quick Settings tile: two swipes from any screen, including the lock screen, to ArcX's
 * workflow picker. It carries no state of its own — there is nothing to toggle, only somewhere to
 * go — so the tile stays permanently inactive.
 */
class ArcxTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    // Lint flags the Intent overload unconditionally; below API 34 it is the only one that exists.
    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        super.onClick()
        val intent = ArcxDeepLinks.intent(this)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // On API 34 the Intent overload does not just warn, it throws
                // UnsupportedOperationException. The PendingIntent overload exists because the
                // shade needs something it can fire after it has finished collapsing.
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
    }
}
