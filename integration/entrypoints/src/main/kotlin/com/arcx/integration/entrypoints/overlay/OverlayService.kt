package com.arcx.integration.entrypoints.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.arcx.core.domain.repository.SettingsRepository
import com.arcx.core.domain.repository.WorkflowRepository
import com.arcx.integration.entrypoints.ArcxDeepLinks
import com.arcx.integration.entrypoints.R
import com.arcx.integration.entrypoints.accessibility.AccessibilityServiceHolder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** More than this and the panel stops being a shortcut and starts being a second app screen. */
private const val MAX_PANEL_WORKFLOWS = 8

private const val CHANNEL_ID = "arcx_bubble"
private const val NOTIFICATION_ID = 4201

/**
 * Hosts the floating bubble for as long as the user leaves it switched on.
 *
 * It has to be a foreground service: an overlay owned by a background process is killed within
 * minutes, and the bubble's whole promise is that it is there when you reach for it.
 */
@AndroidEntryPoint
class OverlayService : Service() {

    @Inject lateinit var workflowRepository: WorkflowRepository

    @Inject lateinit var settingsRepository: SettingsRepository

    // Main.immediate because every touch point on this scope ends in a WindowManager call.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var overlay: BubbleOverlay? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Unconditionally first. Anything started with startForegroundService has five seconds to
        // post its notification or the system kills the process with an explicit crash — including
        // on the paths below that immediately give up and stop.
        if (!promoteToForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ACTION_STOP) {
            // Turning it off from the notification is a real preference, not a transient stop, so
            // it has to survive a reboot the same way the switch in Settings does.
            scope.launch { settingsRepository.update { it.copy(bubbleEnabled = false) } }
            stopSelf()
            return START_NOT_STICKY
        }

        // Revocable at any moment from system settings, so it is checked here rather than trusted
        // from whenever the service was started.
        if (!OverlayPermission.isGranted(this)) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (overlay == null) startBubble()
        return START_STICKY
    }

    private fun startBubble() {
        val bubble = BubbleOverlay(
            context = this,
            onWorkflow = { workflow -> launchDeepLink(workflow.id) },
            onMore = { launchDeepLink(workflowId = null) },
        )
        if (!bubble.show()) {
            stopSelf()
            return
        }
        overlay = bubble

        scope.launch {
            combine(
                workflowRepository.observePinned(),
                workflowRepository.observeFavorites(),
            ) { pinned, favorites ->
                // Pinned first, then favourites, deduplicated — a workflow is often both.
                (pinned + favorites).distinctBy { it.id }.take(MAX_PANEL_WORKFLOWS)
            }.collect { bubble.updateWorkflows(it) }
        }

        // The switch in Settings is the source of truth; this service just follows it.
        scope.launch {
            settingsRepository.settings
                .map { it.bubbleEnabled }
                .distinctUntilChanged()
                .collect { enabled -> if (!enabled) stopSelf() }
        }
    }

    /**
     * Reads the screen *before* handing over to the runner, and does it synchronously.
     *
     * This is the one entry point that can capture what the user is actually looking at. The bubble
     * is an overlay, so the app underneath is still the top task and still readable; a moment later
     * the runner activity takes the front and accessibility stops exposing that window entirely.
     * A few milliseconds of hitch on a deliberate tap is the right trade for the workflow getting
     * live text instead of the snapshot taken when the screen was opened.
     */
    private fun launchDeepLink(workflowId: String?) {
        runCatching { AccessibilityServiceHolder.current()?.captureScreen() }
        runCatching { startActivity(ArcxDeepLinks.intent(this, workflowId)) }
    }

    override fun onDestroy() {
        scope.cancel()
        overlay?.hide()
        overlay = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /**
     * Returns false when the platform refuses the promotion — Android 12+ throws
     * ForegroundServiceStartNotAllowedException if the start came from the background, which is
     * exactly what happens if a boot broadcast arrives on a device that is still locked.
     */
    private fun promoteToForeground(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The specialUse type only exists from API 34; passing it to an older platform, whose
            // manifest cannot have declared it, is itself a crash.
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                buildNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, buildNotification())
        }
        true
    }.getOrDefault(false)

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.arcx_bubble_channel_name),
            // The user did not ask for this notification, Android did. Low importance keeps it out
            // of the shade's top section and off the lock screen.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.arcx_bubble_channel_description)
            setShowBadge(false)
        }
        runCatching {
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, ArcxDeepLinks.intent(this), flags)
        val stop = PendingIntent.getForegroundService(
            this,
            1,
            Intent(this, OverlayService::class.java).setAction(ACTION_STOP),
            flags,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_arcx_spark)
            .setContentTitle(getString(R.string.arcx_bubble_notification_title))
            .setContentText(getString(R.string.arcx_bubble_notification_text))
            .setContentIntent(open)
            .addAction(0, getString(R.string.arcx_bubble_stop), stop)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            // Deferred: the platform hides it for ten seconds, so switching the bubble on does not
            // flash a notification the user has to dismiss their way past.
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    companion object {
        internal const val ACTION_STOP = "com.arcx.integration.entrypoints.STOP_BUBBLE"

        /**
         * Silently does nothing without the overlay permission, and swallows the platform's refusal
         * to start a foreground service from the background. Both are ordinary states, not errors:
         * the caller has already decided the bubble should be on and there is nothing useful to
         * report at this layer.
         */
        fun start(context: Context) {
            if (!OverlayPermission.isGranted(context)) return
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, OverlayService::class.java),
                )
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, OverlayService::class.java)) }
        }
    }
}
