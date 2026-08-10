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
import androidx.compose.material.icons.outlined.Notifications
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
    screenReadingEnabled: Boolean,
    onBack: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
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
