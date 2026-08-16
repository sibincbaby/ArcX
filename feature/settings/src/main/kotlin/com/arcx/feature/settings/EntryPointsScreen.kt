package com.arcx.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewSidebar
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.NoticeCard
import com.arcx.core.designsystem.component.NoticeSeverity
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.designsystem.component.TintedIcon
import com.arcx.core.designsystem.theme.Spacing

/**
 * Where ArcX appears, and nothing else.
 *
 * The count at the top is the point of the screen. Three of these can be revoked in system Settings
 * while ArcX is backgrounded and two more are switches the user forgot they never turned on, so the
 * honest answer to "why did nothing happen when I selected that text" is usually visible here and
 * nowhere else. Every permission behind it is read fresh on resume rather than remembered from when
 * the screen was built.
 *
 * This screen used to be the whole of Settings by weight — eighteen controls, no headings, and four
 * separate subjects. What left: the accessibility grant and its disclosure went to Permissions,
 * the notification grant with them, and the five sidebar sliders and the two picker switches went
 * to Appearance, which is where a user goes to ask what something looks like. What stayed is the
 * one question this screen's title asks. What arrived is the widget, a shipped entry point this
 * screen had never listed.
 */
@Composable
internal fun EntryPointsScreen(
    live: Int,
    bubbleLive: Boolean,
    overlayGranted: Boolean,
    batteryExempt: Boolean,
    hasAutostartScreen: Boolean,
    screenReadingEnabled: Boolean,
    screenReadingLive: Boolean,
    launcherIconEnabled: Boolean,
    accessibilityButtonAssigned: Boolean,
    accessibilityButtonOffered: Boolean,
    canAddQuickTile: Boolean,
    onBack: () -> Unit,
    onBubbleEnabledChange: (Boolean) -> Unit,
    onLauncherIconChange: (Boolean) -> Unit,
    onAccessibilityButtonOfferedChange: (Boolean) -> Unit,
    onAddQuickTile: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenPermissions: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAutostart: () -> Unit,
) {
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
                modifier = Modifier
                    .padding(
                        start = Spacing.Gutter,
                        end = Spacing.Gutter,
                        top = Spacing.Sm,
                    )
                    // A heading because it is the one, and polite because it is the sentence that
                    // changes when the user comes back from system Settings having granted
                    // something. Every permission on this screen is re-read on resume, so the
                    // number moves while nothing has been touched here — announcing it is the only
                    // way that reaches someone who cannot see it move.
                    .semantics {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
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

            // Above the list rather than under the sidebar row: it is the one thing on this screen
            // that stops a surface working outright, and the switch above it will not stay on
            // until it is dealt with.
            if (!overlayGranted) {
                Spacer(Modifier.height(Spacing.Md))
                PermissionPrompt(
                    body = "Drawing over other apps is off, so the sidebar has nowhere to " +
                        "appear. Grant it and the sidebar switches on by itself.",
                    actionLabel = "Grant permission",
                    onAction = onOpenOverlaySettings,
                )
            }

            SectionHeader("Ways in")
            SettingsGroup {
                SurfaceRow(
                    icon = Icons.AutoMirrored.Outlined.ViewSidebar,
                    title = "Edge sidebar",
                    subtitle = "A thin strip down one edge — tap or swipe it from inside any app",
                    live = bubbleLive,
                    modifier = Modifier.toggleable(
                        value = bubbleLive,
                        role = Role.Switch,
                        onValueChange = onBubbleEnabledChange,
                    ),
                ) {
                    // null, not a second handler: the row is the control. See SettingsSwitchRow.
                    Switch(checked = bubbleLive, onCheckedChange = null)
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
                    modifier = Modifier.toggleable(
                        value = launcherIconEnabled,
                        role = Role.Switch,
                        onValueChange = onLauncherIconChange,
                    ),
                ) {
                    Switch(checked = launcherIconEnabled, onCheckedChange = null)
                }
                SurfaceRow(
                    icon = Icons.Outlined.TouchApp,
                    title = "Accessibility button",
                    subtitle = when {
                        !screenReadingEnabled ->
                            "Needs the accessibility permission first — see Permissions"
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
                    // Only a control while there is a switch drawn in it. Without the permission
                    // this row is a sentence explaining what is missing, and a row that announces
                    // itself as a switch with nothing to flip is worse than one that does not.
                    modifier = if (screenReadingEnabled) {
                        Modifier.toggleable(
                            value = accessibilityButtonOffered,
                            role = Role.Switch,
                            onValueChange = onAccessibilityButtonOfferedChange,
                        )
                    } else {
                        Modifier
                    },
                ) {
                    if (screenReadingEnabled) {
                        Switch(checked = accessibilityButtonOffered, onCheckedChange = null)
                    }
                }
                // The capability, not the grant. It stays in this list and in the count because it
                // is the answer to "why did nothing happen when I selected that text" — the grant
                // itself now lives on Permissions, behind the disclosure, and this row's button
                // goes there rather than opening the system Accessibility screen a second way.
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
                    TextButton(onClick = onOpenPermissions) { Text("Permissions") }
                }
            }

            // Everything else about the sidebar, directly under the switch that turns it on rather
            // than at the bottom of the screen where it used to be — a user who has just found out
            // their sidebar stopped overnight should not have to scroll past the share sheet to
            // find out why.
            SectionHeader("The sidebar")
            SettingsGroup {
                SettingsRow(
                    title = "Sidebar appearance",
                    subtitle = "Which edge, how far down, how long, how thick, how faint",
                    icon = Icons.Outlined.Tune,
                    onClick = onOpenAppearance,
                )
            }
            // Clearing ArcX from Recents kills its process, and Android does not restart the
            // service afterwards on the OEMs that police background starts — the sidebar is simply
            // gone until the app is opened again. These two settings are the only levers a user
            // has, so say what they are for rather than listing permissions.
            //
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
                // The one control on this screen that is hidden rather than disabled when it cannot
                // be used, and the exception is deliberate: every other dependency here is
                // something the user can go and satisfy, while a phone with no vendor autostart
                // screen will never have one. A greyed-out row would be a permanent dead end.
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

            // Neither of these is in the count above, for the same reason: they are placed by the
            // shade and by the launcher, and Android will not say whether the user has done it.
            // A guess here would make the number at the top worthless.
            SectionHeader("Put ArcX somewhere yourself")
            SettingsGroup {
                SurfaceRow(
                    icon = Icons.Outlined.Dashboard,
                    title = "Quick Settings tile",
                    subtitle = if (canAddQuickTile) {
                        "Sits with Wi-Fi and Bluetooth, so you can open your workflows from any " +
                            "screen — even the lock screen."
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
                SurfaceRow(
                    icon = Icons.Outlined.Widgets,
                    title = "Home-screen widget",
                    subtitle = "\"ArcX favorites\" — long-press your home screen, pick Widgets, " +
                        "and drag it in. It runs your starred workflows in one tap.",
                    live = null,
                ) {}
            }
            SettingsNote(
                "One more way in needs no setup at all: hold down the ArcX icon and pick " +
                    "\"Actions\".",
            )
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }
}

/**
 * One way in. [live] null means ArcX genuinely cannot tell — the icon stays neutral rather than
 * claiming a state, because a wrong green dot here is worse than no dot.
 *
 * [modifier] is where a row with a switch in it hands in its `toggleable`, the same way
 * [SettingsSwitchRow] does: the row becomes the control, and the Switch stops owning a state whose
 * name it does not carry. Without it a screen reader reached six switches all announcing "on,
 * switch" with nothing to say which entry point each one was.
 */
@Composable
private fun SurfaceRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    live: Boolean?,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    val tint = if (live == true) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            // Live and off are otherwise carried by the icon's tint alone, which is colour as the
            // only channel. The state is said out loud instead; the icon stays decorative.
            .semantics(mergeDescendants = true) {
                // Nullable on purpose — say nothing rather than claim a state ArcX cannot tell.
                when (live) {
                    true -> stateDescription = "Live"
                    false -> stateDescription = "Off"
                    null -> Unit
                }
            }
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

/**
 * What stands in for a switch on the two rows that have nothing to switch.
 *
 * `labelMedium`, not `MetaTextStyle`. That style is monospace and reserved for what the engine
 * reads or reports — a duration, a model id, a `{{variable}}` — so that a glance separates machine
 * facts from prose. "built in" is prose: it is this screen telling the user, in its own voice, that
 * the manifest declares these two and no permission or switch is involved. In monospace it read as
 * a value ArcX had gone and looked up, and it was visibly a different typeface from every other
 * word on the screen.
 *
 * `onSurfaceVariant`, not `outline`. Measured on this theme, small text on `surfaceContainerLow`
 * read 4.05:1 in light mode against a 4.5:1 requirement — it is under the large-text threshold, so
 * the 3:1 allowance does not apply. `outline` is an M3 *boundary* role, specified for 3:1 dividers
 * and never correct for text. WorkflowPanel made the same swap for the same reason. The type change
 * only helps that: 12sp Medium where it was 11sp Medium, same colour on the same surface.
 */
@Composable
private fun BuiltInBadge() {
    Text(
        text = "built in",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
