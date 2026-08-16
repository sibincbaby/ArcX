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
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import com.arcx.core.model.SidebarSide
import com.arcx.core.model.UserSettings
import kotlin.math.roundToInt

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
    sidebarSide: SidebarSide,
    sidebarVerticalPercent: Float,
    sidebarLengthDp: Int,
    sidebarWidthDp: Int,
    sidebarOpacity: Float,
    onBack: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onLauncherIconChange: (Boolean) -> Unit,
    onAddQuickTile: () -> Unit,
    onBubbleOpensFullListChange: (Boolean) -> Unit,
    onCompactPickerChange: (Boolean) -> Unit,
    onSidebarSideChange: (SidebarSide) -> Unit,
    onSidebarVerticalPercentChange: (Float) -> Unit,
    onSidebarLengthChange: (Int) -> Unit,
    onSidebarWidthChange: (Int) -> Unit,
    onSidebarOpacityChange: (Float) -> Unit,
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
                    icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                    title = "Edge sidebar",
                    subtitle = "A thin strip down one edge — tap or swipe it from inside any app",
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
                                "one trigger besides the sidebar that can photograph the screen"
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
                // Screen reading appears twice on this screen and both are wanted. This one is the
                // capability, listed with the other ways in and counted in the number at the top,
                // so it answers "is it working". The row further down, under the disclosure, is
                // the grant behind it and answers "have I given it". Deleting either leaves a
                // question the other cannot answer — the titles carry the difference instead.
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
                    body = "Drawing over other apps is off, so the sidebar has nowhere to " +
                        "appear. Grant it and the sidebar switches on by itself.",
                    actionLabel = "Grant permission",
                    onAction = onOpenOverlaySettings,
                )
            }

            // Only while there is a strip on screen to change. Five sliders under a switch that is
            // off would be five controls with nothing at the other end of them — and the reason
            // they are worth having here at all is that the overlay is a separate window that
            // floats above this one, so a drag redraws the real strip rather than a preview of it.
            if (bubbleLive) {
                SectionLabel("Sidebar")
                SettingsGroup {
                    SidebarSideRow(side = sidebarSide, onSideChange = onSidebarSideChange)
                    SettingsSlider(
                        title = "Position",
                        value = sidebarVerticalPercent,
                        valueRange = 0f..1f,
                        step = PercentStep,
                        format = ::percentLabel,
                        supporting = "Where the middle of the strip sits, from the top of the " +
                            "screen down. Kept as a proportion rather than a distance, so it " +
                            "comes back to the same place after you rotate the phone.",
                        onValueChange = onSidebarVerticalPercentChange,
                    )
                    SettingsSlider(
                        title = "Length",
                        value = sidebarLengthDp.toFloat(),
                        valueRange = UserSettings.SIDEBAR_MIN_LENGTH_DP.toFloat()..
                            UserSettings.SIDEBAR_MAX_LENGTH_DP.toFloat(),
                        step = 1f,
                        format = ::dpLabel,
                        // The cap is Android's, not a house style, and a user who hits it deserves
                        // to know it will not move. See UserSettings.SIDEBAR_MAX_LENGTH_DP.
                        supporting = "Stops at ${UserSettings.SIDEBAR_MAX_LENGTH_DP}dp because " +
                            "Android stops there: it keeps the back gesture off this strip, and " +
                            "it will only do that for ${UserSettings.SIDEBAR_MAX_LENGTH_DP}dp of " +
                            "a screen edge. A longer strip would have a stretch at the end where " +
                            "your swipe goes back instead of opening ArcX, with nothing on screen " +
                            "to tell you where that stretch begins.",
                        onValueChange = { onSidebarLengthChange(it.roundToInt()) },
                    )
                    SettingsSlider(
                        title = "Thickness",
                        value = sidebarWidthDp.toFloat(),
                        valueRange = UserSettings.SIDEBAR_MIN_WIDTH_DP.toFloat()..
                            UserSettings.SIDEBAR_MAX_WIDTH_DP.toFloat(),
                        step = 1f,
                        format = ::dpLabel,
                        supporting = "How wide the strip is drawn — not how big a target it is. " +
                            "It takes touches across a full ${SidebarTouchWidthDp}dp whatever " +
                            "this says, so the default hairline is far easier to hit than it looks.",
                        onValueChange = { onSidebarWidthChange(it.roundToInt()) },
                    )
                    SettingsSlider(
                        title = "Opacity",
                        value = sidebarOpacity,
                        valueRange = 0f..1f,
                        step = PercentStep,
                        format = ::percentLabel,
                        supporting = "Fades the strip you can see. The band it takes touches in " +
                            "never fades with it, so even at nothing it still opens.",
                        onValueChange = onSidebarOpacityChange,
                    )
                }
                SettingsNote(
                    "Each of these applies as you drag it. The strip is its own window sitting " +
                        "above this screen, so what moves is the real thing.",
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
            // service afterwards on the OEMs that police background starts — the sidebar is simply
            // gone until the app is opened again. These two settings are the only levers a user
            // has, so say what they are for rather than listing permissions.
            Spacer(Modifier.height(Spacing.Sm))
            // Amber, not red: none of this is broken, it is the platform behaving as designed.
            NoticeCard(
                severity = NoticeSeverity.Warning,
                title = "Keeping the sidebar alive",
                icon = Icons.Outlined.BatteryAlert,
                // NoticeCard draws no outer inset of its own, so the card states the gutter the
                // groups above and below it sit on.
                modifier = Modifier.padding(horizontal = Spacing.Gutter, vertical = 6.dp),
                message = if (hasAutostartScreen) {
                    "Clearing ArcX from Recents stops the sidebar, and your phone will not bring " +
                        "it back on its own. Autostart is what allows it to return; locking ArcX " +
                        "in Recents stops it being cleared in the first place. Opening ArcX, or " +
                        "using it from the share or selection menu, always brings it back."
                } else {
                    "Clearing ArcX from Recents stops the sidebar until you open ArcX again, or " +
                        "use it from the share or selection menu."
                },
            )
            SettingsGroup {
                SettingsRow(
                    title = "Battery optimisation",
                    subtitle = if (batteryExempt) {
                        "Exempt — Android will not put the sidebar to sleep"
                    } else {
                        "The sidebar may be stopped in the background to save power"
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
                // "permission", because the row in the entry-point list above is already called
                // Screen reading and the same words twice left the user guessing which one
                // mattered. This is the grant itself: it reports whether it has been given, not
                // whether the service is currently running — that is the other row's job, and it
                // is why the two can honestly disagree when Android stops a granted service.
                //
                // The subtitle names Accessibility because that is where Android keeps this and
                // ArcX's own name for it appears nowhere in system Settings. The quoted entry is
                // arcx_accessibility_label in :integration:entrypoints; keep the two in step.
                SettingsRow(
                    title = "Screen reading permission",
                    subtitle = if (screenReadingEnabled) {
                        "Granted — Android lists it under Accessibility as \"ArcX screen reading\""
                    } else {
                        "Not granted — turn on \"ArcX screen reading\" under Accessibility to let " +
                            "workflows use what is on screen"
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
                    "the edge sidebar, which Android requires to run behind an ongoing " +
                    "notification.",
            )

            // The two lists differ because the sidebar's window may never take focus — without
            // that, the app underneath stops being readable, which is the whole point of the
            // sidebar. A non-focusable window cannot host a text field, so its panel has no
            // search. Everything else opens a normal Activity and has no such limit. Both of
            // those are defaults, not rules, so both are switchable here.
            SectionLabel("Workflow list")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Sidebar opens the full list",
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
                        "the drawer icon, so they match the sidebar's panel.",
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
 * The width the sidebar's window takes touches across, restated here for one sentence of copy.
 *
 * The real number is `SidebarTouchWidth` in :integration:entrypoints, which nothing under
 * :feature: may depend on — so it is repeated rather than imported. It is the platform's minimum
 * touch target and has no reason to move; if it ever does, this is the sentence that starts lying.
 */
private const val SidebarTouchWidthDp = 48

private fun dpLabel(value: Float): String = "${value.roundToInt()}dp"

/**
 * Which edge the strip is welded to.
 *
 * Two buttons rather than a menu: there are exactly two edges, they are never going to be three,
 * and a menu would hide half the answer behind a tap for no gain.
 */
@Composable
private fun SidebarSideRow(side: SidebarSide, onSideChange: (SidebarSide) -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md)) {
        Text("Edge", style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(Spacing.Sm))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SidebarSide.entries.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = side == option,
                    onClick = { onSideChange(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, SidebarSide.entries.size),
                ) {
                    Text(sideLabel(option))
                }
            }
        }
        Spacer(Modifier.height(Spacing.Sm))
        Text(
            text = "Left to begin with, because the right edge is usually taken — Samsung's own " +
                "Edge panel handle lives there and is on out of the box. Two handles on one edge " +
                "means one inward swipe and two things expecting it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun sideLabel(side: SidebarSide): String = when (side) {
    SidebarSide.LEFT -> "Left"
    SidebarSide.RIGHT -> "Right"
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
            // Stated rather than left to TintedIcon's default, because SettingsRow reserves this
            // same width for its bare glyph so the two anatomies share one title column. That is
            // a promise between the two rows; a default either of them could move is not.
            size = SettingsRowIconSize,
        )
        // Was 14dp, off the scale and 2dp wider than the gap SettingsRow and ArcxListRow use.
        Spacer(Modifier.width(Spacing.Md))
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
