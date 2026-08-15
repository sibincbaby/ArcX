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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.arcx.core.designsystem.R as DesignSystemR
import com.arcx.core.designsystem.component.NoticeCard
import com.arcx.core.designsystem.component.NoticeSeverity
import com.arcx.core.designsystem.component.SectionLabel
import com.arcx.core.designsystem.component.TintedIcon
import com.arcx.core.designsystem.theme.MetaTextStyle
import com.arcx.core.designsystem.theme.Spacing

/**
 * Everything that lets ArcX be used from outside its own window, led by how many of them are
 * actually working right now.
 *
 * The count at the top is the point of the screen. Three of these can be revoked in system
 * Settings while ArcX is backgrounded and two more are switches the user forgot they never
 * turned on, so the honest answer to "why did nothing happen when I selected that text" is
 * usually visible here and nowhere else. Every permission is read fresh on resume rather than
 * remembered from when the screen was built.
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
    screenReadingRunning: Boolean,
    launcherIconEnabled: Boolean,
    accessibilityButtonAssigned: Boolean,
    accessibilityButtonOffered: Boolean,
    onAccessibilityButtonOfferedChange: (Boolean) -> Unit,
    canAddQuickTile: Boolean,
    bubbleOpensFullList: Boolean,
    compactPicker: Boolean,
    onBack: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onLauncherIconChange: (Boolean) -> Unit,
    onAddQuickTile: () -> Unit,
    onBubbleOpensFullListChange: (Boolean) -> Unit,
    onCompactPickerChange: (Boolean) -> Unit,
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

    val bubbleLive = bubbleEnabled && overlayGranted
    // The six ArcX can actually answer for. The Quick Settings tile is deliberately not among
    // them: Android exposes no way to ask whether a tile has been added to the shade, and a
    // guess in this count would make the whole number worthless.
    // Counts running, not merely switched on: a service Android lists but is not hosting is not
    // a way in, however the setting reads.
    val screenReadingLive = screenReadingEnabled && screenReadingRunning
    val live = listOf(
        bubbleLive,
        true, // text selection menu — a manifest activity, always present
        true, // share sheet — likewise
        launcherIconEnabled,
        accessibilityButtonAssigned && accessibilityButtonOffered,
        screenReadingLive,
    ).count { it }

    SettingsScaffold(title = "Entry points", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "$live of 6 are live",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(
                    start = Spacing.Gutter,
                    end = Spacing.Gutter,
                    top = Spacing.Sm,
                ),
            )
            Text(
                text = "Each one funnels into the same runner, so a workflow behaves identically " +
                    "wherever you fire it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Spacing.Gutter,
                    end = Spacing.Gutter,
                    top = 6.dp,
                ),
            )

            Spacer(Modifier.height(18.dp))
            SettingsGroup {
                SurfaceRow(
                    icon = Icons.Outlined.PictureInPictureAlt,
                    title = "Floating bubble",
                    subtitle = "Draggable, floats over any app",
                    live = bubbleLive,
                ) {
                    Switch(
                        checked = bubbleLive,
                        onCheckedChange = onBubbleEnabledChange,
                    )
                }
                SurfaceRow(
                    icon = Icons.Outlined.TextFields,
                    title = "Text selection menu",
                    subtitle = "ArcX in the highlight popup",
                    live = true,
                ) { BuiltInBadge() }
                SurfaceRow(
                    icon = Icons.Outlined.Share,
                    title = "Share sheet",
                    subtitle = "Always available",
                    live = true,
                ) { BuiltInBadge() }
                SurfaceRow(
                    icon = Icons.Outlined.Apps,
                    title = "App drawer icon",
                    subtitle = "\"ArcX Actions\" — drop it on a home screen or an Edge panel",
                    live = launcherIconEnabled,
                ) {
                    Switch(
                        checked = launcherIconEnabled,
                        onCheckedChange = onLauncherIconChange,
                    )
                }
                SurfaceRow(
                    icon = Icons.Outlined.TouchApp,
                    title = "Accessibility button",
                    subtitle = when {
                        !screenReadingEnabled ->
                            "Needs the accessibility permission first"
                        !accessibilityButtonOffered ->
                            "Off. Turn this on to let ArcX be one of the things the " +
                                "accessibility button and the volume-key shortcut can open."
                        accessibilityButtonAssigned ->
                            "Assigned — opens your workflows from inside any app, and it is the " +
                                "one trigger besides the bubble that can photograph the screen"
                        else ->
                            "Offered. Pick ArcX under the accessibility button in system Settings " +
                                "to finish."
                    },
                    live = accessibilityButtonAssigned && accessibilityButtonOffered,
                ) {
                    if (screenReadingEnabled) {
                        Switch(
                            checked = accessibilityButtonOffered,
                            onCheckedChange = onAccessibilityButtonOfferedChange,
                        )
                    }
                }
                SurfaceRow(
                    icon = Icons.Outlined.Visibility,
                    title = "Screen reading",
                    subtitle = when {
                        screenReadingLive -> "Workflows can read and photograph what is on screen"
                        // Switched on but nothing behind it. Say the remedy, because Android's own
                        // Accessibility screen will keep insisting this is enabled.
                        screenReadingEnabled ->
                            "Switched on, but not running — Android stopped it. Turn it off and " +
                                "on again in Accessibility settings."
                        else -> "Workflows that need screen text will ask for other input"
                    },
                    live = screenReadingLive,
                ) {
                    TextButton(onClick = onOpenScreenReadingSettings) {
                        Text(if (screenReadingEnabled) "Manage" else "Enable")
                    }
                }
            }

            if (!overlayGranted) {
                PermissionPrompt(
                    body = "Drawing over other apps is off, so the bubble has nowhere to " +
                        "appear. Grant it and the bubble switches on by itself.",
                    actionLabel = "Grant permission",
                    onAction = onOpenOverlaySettings,
                )
            }

            SettingsGroup {
                SurfaceRow(
                    icon = Icons.Outlined.Dashboard,
                    title = "Quick Settings tile",
                    subtitle = if (canAddQuickTile) {
                        "Sits with Wi-Fi and Bluetooth, so you can open your workflows from any " +
                            "screen — even the lock screen. Android will not tell us whether you " +
                            "have added it, so it is not in the count above."
                    } else {
                        "Swipe down twice, tap Edit, and drag the ArcX tile in. It then works " +
                            "from any screen, even the lock screen."
                    },
                    live = null,
                ) {
                    if (canAddQuickTile) {
                        TextButton(onClick = onAddQuickTile) { Text("Add") }
                    }
                }
            }
            SettingsNote(
                "One more way in needs no setup at all: hold down the ArcX icon and pick " +
                    "\"Actions\".",
            )

            // Clearing ArcX from Recents kills its process, and Android does not restart the
            // service afterwards on the OEMs that police background starts — the bubble is simply
            // gone until the app is opened again. These two settings are the only levers a user
            // has, so say what they are for rather than listing permissions.
            Spacer(Modifier.height(Spacing.Sm))
            // Amber, not red: none of this is broken, it is the platform behaving as designed.
            NoticeCard(
                severity = NoticeSeverity.Warning,
                title = "Keeping the bubble alive",
                icon = Icons.Outlined.BatteryAlert,
                // NoticeCard draws no outer inset of its own, so the card states the gutter the
                // groups above and below it sit on.
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp),
                message = if (hasAutostartScreen) {
                    "Clearing ArcX from Recents stops the bubble, and your phone will not bring " +
                        "it back on its own. Autostart is what allows it to return; locking ArcX " +
                        "in Recents stops it being cleared in the first place. Opening ArcX, or " +
                        "using it from the share or selection menu, always brings it back."
                } else {
                    "Clearing ArcX from Recents stops the bubble until you open ArcX again, or " +
                        "use it from the share or selection menu."
                },
            )
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

            SectionLabel("Screen reading")
            // The disclosure sits above the control on purpose: Play's accessibility policy
            // wants it in front of the user before they act, not tucked under the switch.
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Spacing.Gutter, vertical = 6.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Column(Modifier.padding(Spacing.Lg)) {
                    Text(
                        "What this permission does",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(Spacing.Sm))
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

            SectionLabel("Notifications")
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

            // The two lists differ because the bubble's window may never take focus — without
            // that, the app underneath stops being readable, which is the whole point of the
            // bubble. A non-focusable window cannot host a text field, so its panel has no
            // search. Everything else opens a normal Activity and has no such limit. Both of
            // those are defaults, not rules, so both are switchable here.
            SectionLabel("Workflow list")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Bubble opens the full list",
                    subtitle = "Search included, like every other entry point. The screen is " +
                        "still read before the list appears, so workflows that use it keep " +
                        "working — but the list covers the app instead of floating over it.",
                    icon = Icons.Outlined.OpenInFull,
                    checked = bubbleOpensFullList,
                    onCheckedChange = onBubbleOpensFullListChange,
                )
                SettingsSwitchRow(
                    title = "Compact list everywhere else",
                    subtitle = "Drops the search box from the tile, share sheet, shortcuts and " +
                        "the drawer icon, so they match the bubble's panel.",
                    icon = Icons.Outlined.CloseFullscreen,
                    checked = compactPicker,
                    onCheckedChange = onCompactPickerChange,
                )
            }
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }
}

/**
 * One way in. [live] null means ArcX genuinely cannot tell — the icon stays neutral rather than
 * claiming a state, because a wrong green dot here is worse than no dot.
 */
@Composable
private fun SurfaceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    live: Boolean?,
    trailing: @Composable () -> Unit,
) {
    val tint = if (live == true) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 16, not the 15 this row alone used: it sits inside SettingsGroup's card, so this is
            // the card's interior and it has to line up with SettingsRow's, which is Lg. The
            // screen gutter is the one SettingsGroup states, further out.
            .padding(horizontal = Spacing.Lg, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedIcon(
            icon = icon,
            container = if (live == true) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerHighest
            },
            content = tint,
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (live == false) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(10.dp))
        trailing()
    }
}

@Composable
private fun BuiltInBadge() {
    Text(
        text = "built in",
        style = MetaTextStyle,
        color = MaterialTheme.colorScheme.outline,
        modifier = Modifier.padding(end = Spacing.Xs),
    )
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
            .padding(horizontal = Spacing.Gutter, vertical = 6.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(Modifier.padding(Spacing.Lg)) {
            Text(body, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Spacing.Md))
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
