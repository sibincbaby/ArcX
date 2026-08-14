package com.arcx.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.arcx.core.designsystem.R as DesignSystemR
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.RestartAlt
import com.arcx.core.designsystem.component.SectionHeader

/**
 * Everything that lets ArcX be used from outside its own window. All three permissions here can
 * be revoked in system Settings while ArcX is backgrounded, so each one is read fresh on resume
 * rather than remembered from when the screen was built.
 */
@Composable
internal fun EntryPointsScreen(
    bubbleEnabled: Boolean,
    overlayGranted: Boolean,
    batteryExempt: Boolean,
    hasAutostartScreen: Boolean,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostart: () -> Unit,
    screenReadingEnabled: Boolean,
    launcherIconEnabled: Boolean,
    accessibilityButtonAssigned: Boolean,
    onBack: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onLauncherIconChange: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenScreenReadingSettings: () -> Unit,
) {
    val context = LocalContext.current
    var notificationsGranted by remember { mutableStateOf(context.hasNotificationPermission()) }
    // Only meaningful after a refusal: Android stops showing the dialog, so the honest next
    // step becomes the app's own notification settings.
    var notificationsBlocked by remember { mutableStateOf(false) }

    val requestNotifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsGranted = granted
        notificationsBlocked = !granted
    }

    LifecycleResumeEffect(Unit) {
        notificationsGranted = context.hasNotificationPermission()
        if (notificationsGranted) notificationsBlocked = false
        onPauseOrDispose { }
    }

    SettingsScaffold(title = "Entry points", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Floating bubble")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Floating bubble",
                    subtitle = "A draggable button that floats over any app and opens your " +
                        "workflows without leaving it.",
                    icon = Icons.Outlined.PictureInPictureAlt,
                    checked = bubbleEnabled && overlayGranted,
                    onCheckedChange = onBubbleEnabledChange,
                )
            }
            if (!overlayGranted) {
                PermissionPrompt(
                    body = "Drawing over other apps is off, so the bubble has nowhere to " +
                        "appear. Grant it and the bubble switches on by itself.",
                    actionLabel = "Grant permission",
                    onAction = onOpenOverlaySettings,
                )
            }

            // Clearing ArcX from Recents kills its process, and Android does not restart the
            // service afterwards on the OEMs that police background starts — the bubble is simply
            // gone until the app is opened again. These two settings are the only levers a user
            // has, so say what they are for rather than listing permissions.
            SectionHeader("Keeping the bubble running")
            SettingsGroup {
                SettingsRow(
                    title = "Battery optimisation",
                    subtitle = if (batteryExempt) {
                        "Exempt — Android will not put the bubble to sleep"
                    } else {
                        "The bubble may be stopped in the background to save power"
                    },
                    icon = Icons.Outlined.BatteryStd,
                    trailing = {
                        if (!batteryExempt) {
                            TextButton(onClick = onOpenBatterySettings) { Text("Allow") }
                        }
                    },
                )
                if (hasAutostartScreen) {
                    SettingsRow(
                        title = "Autostart",
                        subtitle = "Lets ArcX start itself again after it is closed",
                        icon = Icons.Outlined.RestartAlt,
                        trailing = {
                            TextButton(onClick = onOpenAutostart) { Text("Open") }
                        },
                    )
                }
            }
            SettingsNote(
                if (hasAutostartScreen) {
                    "If you clear ArcX from Recents the bubble disappears, and your phone will " +
                        "not bring it back on its own. Turning on Autostart is what allows it to " +
                        "return; locking ArcX in Recents stops it being cleared in the first " +
                        "place. Opening ArcX, or using it from the share or selection menu, " +
                        "always brings the bubble back."
                } else {
                    "If you clear ArcX from Recents the bubble disappears until you open ArcX " +
                        "again, or use it from the share or selection menu."
                },
            )

            // The workflow list is a plain Activity, so anything that can point at a launcher
            // component can open it. The switch is here because that convenience arrives as a
            // second icon in the app drawer, which not everyone wants.
            SectionHeader("Quick launch")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "App drawer icon",
                    subtitle = "Adds an \"ArcX Actions\" icon that opens your workflow list " +
                        "straight away. Drag it onto a home screen or into an Edge panel, or " +
                        "point a Routine or a side-key gesture at it.",
                    icon = Icons.Outlined.Apps,
                    checked = launcherIconEnabled,
                    onCheckedChange = onLauncherIconChange,
                )
            }
            SettingsNote(
                "Two more ways in are always available and need no setup: the ArcX tile, which " +
                    "you add once from the panel at the top of your screen, and holding down the " +
                    "ArcX icon for \"Actions\".",
            )

            SectionHeader("Screen reading")
            // The disclosure sits above the control on purpose: Play's accessibility policy
            // wants it in front of the user before they act, not tucked under the switch.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = CardShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "What this permission does",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Same string the system Accessibility screen shows, so the two can never
                    // describe the permission differently.
                    Text(
                        stringResource(DesignSystemR.string.arcx_accessibility_description),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            SettingsGroup {
                SettingsRow(
                    title = "Screen reading",
                    subtitle = if (screenReadingEnabled) {
                        "Enabled — workflows can read screen text"
                    } else {
                        "Disabled — workflows that need screen text will ask for other input"
                    },
                    icon = Icons.Outlined.Accessibility,
                    trailing = {
                        TextButton(onClick = onOpenScreenReadingSettings) {
                            Text(if (screenReadingEnabled) "Manage" else "Enable")
                        }
                    },
                )
                // Grouped under screen reading rather than with the other launch surfaces because
                // it is the same service: with screen reading off there is nothing to assign.
                SettingsRow(
                    title = "Accessibility button",
                    subtitle = when {
                        !screenReadingEnabled ->
                            "Turn screen reading on first, then ArcX can be assigned to it"
                        accessibilityButtonAssigned ->
                            "Assigned — opens your workflows from inside any app"
                        else ->
                            "Not assigned. Choose ArcX under Accessibility button to open " +
                                "workflows without leaving the app you are in."
                    },
                    icon = Icons.Outlined.TouchApp,
                    trailing = {
                        if (screenReadingEnabled) {
                            TextButton(onClick = onOpenScreenReadingSettings) {
                                Text(if (accessibilityButtonAssigned) "Manage" else "Set up")
                            }
                        }
                    },
                )
            }

            SectionHeader("Notifications")
            SettingsGroup {
                SettingsRow(
                    title = "Notifications",
                    subtitle = when {
                        notificationsGranted -> "Allowed"
                        notificationsBlocked -> "Blocked in system settings"
                        else -> "Not allowed yet"
                    },
                    icon = Icons.Outlined.Notifications,
                    trailing = {
                        if (!notificationsGranted) {
                            TextButton(
                                onClick = {
                                    if (notificationsBlocked) {
                                        context.startActivity(context.appNotificationSettings())
                                    } else {
                                        requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                },
                            ) { Text(if (notificationsBlocked) "Open settings" else "Allow") }
                        }
                    },
                )
            }
            SettingsNote(
                "Workflows that deliver their answer as a notification need this, and so does " +
                    "the floating bubble, which Android requires to run behind an ongoing " +
                    "notification.",
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionPrompt(
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = CardShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Notifications are unconditional below Android 13, where there is no permission to hold. */
private fun Context.hasNotificationPermission(): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

/** Flagged like the intents SystemSurfaces hands back, so it behaves the same from any context. */
private fun Context.appNotificationSettings(): Intent =
    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
