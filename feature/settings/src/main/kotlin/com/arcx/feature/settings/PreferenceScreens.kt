package com.arcx.feature.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloseFullscreen
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.arcx.core.designsystem.component.ArcxGlassSurface
import com.arcx.core.designsystem.component.SectionHeader
import com.arcx.core.designsystem.theme.PanelScrim
import com.arcx.core.designsystem.theme.Spacing
import com.arcx.core.model.ScreenshotRetention
import com.arcx.core.model.SidebarSide
import com.arcx.core.model.ThemePreference
import com.arcx.core.model.UserSettings
import kotlin.math.roundToInt

/**
 * What ArcX looks like — including the two surfaces that are only ever seen over somebody else's
 * app.
 *
 * The sidebar's five numbers and the picker's two switches came here from Entry points. That screen
 * answers "where does ArcX appear"; how wide the strip is drawn and how far the app behind shows
 * through it are not that question. The move also puts [UserSettings.popupTransparency] and
 * [UserSettings.sidebarOpacity] on one screen for the first time: they are the same decision about
 * the same floating window, and they had been two screens apart.
 */
@Composable
internal fun AppearanceScreen(
    settings: UserSettings,
    sidebarLive: Boolean,
    onBack: () -> Unit,
    onThemeChange: (ThemePreference) -> Unit,
    onDynamicColorChange: (Boolean) -> Unit,
    onPopupTransparencyChange: (Float) -> Unit,
    onSidebarSideChange: (SidebarSide) -> Unit,
    onSidebarVerticalPercentChange: (Float) -> Unit,
    onSidebarLengthChange: (Int) -> Unit,
    onSidebarWidthChange: (Int) -> Unit,
    onSidebarOpacityChange: (Float) -> Unit,
    onBubbleOpensFullListChange: (Boolean) -> Unit,
    onCompactPickerChange: (Boolean) -> Unit,
) {
    // Material You only exists from Android 12; offering the toggle below that would be a
    // switch that does nothing.
    val dynamicColorAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    SettingsScaffold(title = "Appearance", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SectionHeader("Theme")
            // selectableGroup is what makes this a set rather than three unrelated rows: without
            // it a screen reader says "Light" and stops, with it "Light, 2 of 3". The row carries
            // the selection and the RadioButton stops owning it, for the same reason
            // SettingsSwitchRow's row carries its toggle — two focusable nodes for one choice, and
            // the one holding the state was the one with no name.
            SettingsGroup(modifier = Modifier.selectableGroup()) {
                ThemePreference.entries.forEach { preference ->
                    val chosen = settings.theme == preference
                    SettingsRow(
                        title = themeLabel(preference),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .selectable(
                                selected = chosen,
                                role = Role.RadioButton,
                                onClick = { onThemeChange(preference) },
                            ),
                        onClick = null,
                        trailing = { RadioButton(selected = chosen, onClick = null) },
                    )
                }
            }

            SectionHeader("Colour")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Dynamic colour",
                    subtitle = if (dynamicColorAvailable) {
                        "Take the palette from your wallpaper."
                    } else {
                        "Needs Android 12 or newer."
                    },
                    checked = settings.dynamicColor && dynamicColorAvailable,
                    onCheckedChange = onDynamicColorChange,
                    enabled = dynamicColorAvailable,
                )
            }

            SectionHeader("Popups")
            SettingsGroup {
                // Above the slider rather than below it: a thumb is dragged with a finger, and a
                // finger covers what is under it — which here is the only thing the control does.
                PopupPreview()
                SettingsSlider(
                    title = "Transparency",
                    value = settings.popupTransparency,
                    valueRange = 0f..UserSettings.POPUP_MAX_TRANSPARENCY,
                    step = PercentStep,
                    format = ::percentLabel,
                    // The cap is a legibility floor rather than a taste one, and a user who reaches
                    // it deserves to know why it will not go further. See POPUP_MAX_TRANSPARENCY.
                    supporting = "How much of the app behind shows through the workflow panel and " +
                        "the popup an answer comes back in. Stops at " +
                        "${(UserSettings.POPUP_MAX_TRANSPARENCY * 100).roundToInt()}% because " +
                        "past that the text stops being readable over a bright app underneath.",
                    onValueChange = onPopupTransparencyChange,
                )
            }

            SectionHeader("The sidebar")
            // Disabled rather than hidden while the sidebar is off. Hiding them is what this screen
            // inherited, and it made five controls unfindable: nothing anywhere said they existed,
            // so the only way to discover the strip could be made thinner was to turn the strip on
            // and notice that the screen had grown.
            DependentControls(
                enabled = sidebarLive,
                reason = "The edge sidebar is off, so there is nothing on screen for these to " +
                    "change. Turn it on under Entry points.",
            ) {
                SettingsGroup {
                    SidebarSideRow(side = settings.sidebarSide, onSideChange = onSidebarSideChange)
                    SettingsSlider(
                        title = "Position",
                        value = settings.sidebarVerticalPercent,
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
                        value = settings.sidebarLengthDp.toFloat(),
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
                        value = settings.sidebarWidthDp.toFloat(),
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
                        value = settings.sidebarOpacity,
                        valueRange = 0f..1f,
                        step = PercentStep,
                        format = ::percentLabel,
                        supporting = "Fades the strip you can see. The band it takes touches in " +
                            "never fades with it, so even at nothing it still opens.",
                        onValueChange = onSidebarOpacityChange,
                    )
                }
            }
            if (sidebarLive) {
                SettingsNote(
                    "Each of these applies as you drag it. The strip is its own window sitting " +
                        "above this screen, so what moves is the real thing.",
                )
            }

            // The two lists differ because the sidebar's window may never take focus — without
            // that, the app underneath stops being readable, which is the whole point of the
            // sidebar. A non-focusable window cannot host a text field, so its panel has no
            // search. Everything else opens a normal Activity and has no such limit. Both of
            // those are defaults, not rules, so both are switchable here.
            SectionHeader("Workflow list")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Sidebar opens the full list",
                    // One row, so the reason goes in the subtitle rather than into
                    // DependentControls — a note floating inside the card would read as belonging
                    // to the switch below it as well.
                    subtitle = if (sidebarLive) {
                        "Search included, like every other entry point. The screen is still read " +
                            "before the list appears, so workflows that use it keep working — but " +
                            "the list covers the app instead of floating over it."
                    } else {
                        "Turn the edge sidebar on under Entry points to choose what it opens."
                    },
                    icon = Icons.Outlined.OpenInFull,
                    checked = settings.bubbleOpensFullList,
                    onCheckedChange = onBubbleOpensFullListChange,
                    enabled = sidebarLive,
                )
                SettingsSwitchRow(
                    // "Compact list everywhere else" wrapped to two lines and stranded "else" on
                    // the second. Shortened rather than given the row more width: the title column
                    // is what every settings row on every screen gets, and a row title that only
                    // fits by taking space from the switch beside it is a title that will wrap
                    // again at the next font scale. The subtitle names the "elsewhere" anyway.
                    title = "Compact list elsewhere",
                    subtitle = "Drops the search box from the tile, share sheet, shortcuts and " +
                        "the drawer icon, so they match the sidebar's panel.",
                    icon = Icons.Outlined.CloseFullscreen,
                    checked = settings.compactPicker,
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
        // Says which edge ships selected, not which edge is selected. As "Left to begin with,
        // because…" it sat under a control reading "Right" and described it — the only line on
        // this screen that changed meaning depending on the setting above it, while being the
        // one line that never changes. The reason is worth keeping: it is why the default is
        // what it is, and it is also the warning for anyone about to move the strip to the right.
        Text(
            text = "Either edge works. Left is the default because the right one is usually " +
                "already taken — Samsung's own Edge panel handle lives there and is on out of " +
                "the box, and two handles on one edge means one inward swipe with two things " +
                "expecting it.",
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
 * A pane of the real thing, over a stand-in for someone else's app.
 *
 * Worth the twenty lines: every other setting on this screen changes something the user is already
 * looking at, and this one changes a surface that only ever appears over a *different* app — so
 * without a preview it is a slider with no visible effect until the next time they use the sidebar.
 * The backdrop is deliberately busy and bright, because that is the case transparency costs
 * something in; a preview over a flat colour would flatter the setting at every value.
 */
@Composable
private fun PopupPreview() {
    Box(
        modifier = Modifier
            .padding(horizontal = Spacing.Lg, vertical = Spacing.Sm)
            .fillMaxWidth()
            .height(112.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.tertiary,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    ),
                ),
            )
            // The same dim the real hosts paint behind a popup, so the preview is not flattering
            // the setting by leaving out the one thing that helps it.
            .background(PanelScrim),
        contentAlignment = Alignment.Center,
    ) {
        ArcxGlassSurface(
            modifier = Modifier.padding(horizontal = Spacing.Xl),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = Spacing.Lg, vertical = Spacing.Md)) {
                Text("Rewrite Professionally", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "Selection → Replace",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun PrivacyScreen(
    historyEnabled: Boolean,
    screenshotRetention: ScreenshotRetention,
    onBack: () -> Unit,
    onHistoryEnabledChange: (Boolean) -> Unit,
    onScreenshotRetentionChange: (ScreenshotRetention) -> Unit,
    onDeleteScreenshots: () -> Unit,
    onClearHistory: () -> Unit,
    onExportWorkflows: () -> Unit,
    onDeleteAllLocalData: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    var confirmWipe by remember { mutableStateOf(false) }
    var confirmDeleteScreenshots by remember { mutableStateOf(false) }

    // "& data", because the screen is not only about what ArcX does not do with your data: it is
    // where you clear it, where you set how long pictures of your screen survive, and now where
    // you take it off the device.
    SettingsScaffold(title = "Privacy & data", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Named, like every other group on this screen and on the four screens beside it. It
            // was the one group introduced by nothing but the app bar, which read as the app bar
            // labelling it — "Privacy & data" over two rows that are only about runs.
            SectionHeader("Run history")
            SettingsGroup {
                SettingsSwitchRow(
                    title = "Save run history",
                    subtitle = "Keep a local record of what you ran, with truncated previews.",
                    icon = Icons.Outlined.History,
                    checked = historyEnabled,
                    onCheckedChange = onHistoryEnabledChange,
                )
                SettingsRow(
                    title = "Clear history",
                    subtitle = "Delete every recorded run.",
                    // Every row in a group carries an icon or none of them does — the rule the
                    // retention group below (none) and Appearance's workflow-list group (both)
                    // already follow. This row had none while the switch above it did, which put
                    // two titles in one card 20dp apart. Icon rather than no icon because every
                    // other action on this screen has one, and dropping the switch's would leave
                    // Privacy the only settings card whose rows start at the card edge.
                    icon = Icons.Outlined.DeleteSweep,
                    onClick = { confirmClear = true },
                )
            }

            SectionHeader("Screenshots")
            // One choice of three, announced as one — see the theme group above.
            SettingsGroup(modifier = Modifier.selectableGroup()) {
                ScreenshotRetention.entries.forEach { retention ->
                    val chosen = screenshotRetention == retention
                    SettingsRow(
                        title = retentionLabel(retention),
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .selectable(
                                selected = chosen,
                                role = Role.RadioButton,
                                onClick = { onScreenshotRetentionChange(retention) },
                            ),
                        onClick = null,
                        trailing = { RadioButton(selected = chosen, onClick = null) },
                    )
                }
            }
            SettingsNote(retentionNote(screenshotRetention))
            // Its own group: inside the retention one it would read as a fourth way to keep
            // screenshots rather than the one control that throws them away.
            SettingsGroup {
                SettingsRow(
                    title = "Delete screenshots now",
                    subtitle = "Remove every saved screenshot, whatever its age.",
                    icon = Icons.Outlined.Screenshot,
                    onClick = { confirmDeleteScreenshots = true },
                )
            }
            SettingsNote(
                "Workflows that act on your screen save a picture of it here so History can " +
                    "show you what they were given. Those pictures are the only screen " +
                    "contents ArcX ever writes to storage.",
            )

            // The counterweight to the danger zone below, and it belongs on the same screen: a
            // user asking "what does ArcX keep about me" is the same user who wants to know they
            // can take it with them before they delete it. Discover offers the identical export
            // behind its overflow menu — same repository, same file — but nobody looking for their
            // own data looks in a gallery of other people's.
            SectionHeader("Take it with you")
            SettingsGroup {
                SettingsRow(
                    title = "Export my workflows",
                    subtitle = "Write every workflow you have to a .json file you keep.",
                    icon = Icons.Outlined.FileUpload,
                    onClick = onExportWorkflows,
                )
            }
            SettingsNote(
                "Prompts and wiring only. Your API keys are not in the file and never leave the " +
                    "encrypted store on this device.",
            )

            SectionHeader("Danger zone")
            SettingsGroup {
                SettingsRow(
                    title = "Delete all local data",
                    subtitle = "Workflows, history, providers and saved API keys.",
                    icon = Icons.Outlined.DeleteForever,
                    onClick = { confirmWipe = true },
                )
            }

            SettingsNote(
                "ArcX has no account and no backend. Nothing here is synced or backed up, so " +
                    "deleting it deletes it. The only thing that ever leaves this device is the " +
                    "text you send to the AI provider you configured, and it goes straight there.",
            )
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear history?",
            body = "Every recorded run is deleted from this device. Your workflows stay.",
            confirmLabel = "Clear",
            onConfirm = onClearHistory,
            onDismiss = { confirmClear = false },
        )
    }

    if (confirmDeleteScreenshots) {
        ConfirmDialog(
            title = "Delete screenshots?",
            body = "Every screenshot ArcX has saved is removed from this device. The runs stay " +
                "in History, they just stop showing a picture.",
            confirmLabel = "Delete",
            onConfirm = onDeleteScreenshots,
            onDismiss = { confirmDeleteScreenshots = false },
        )
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "Delete all local data?",
            body = "This removes every workflow you built, your entire run history, all " +
                "provider configurations and the API keys saved in this device's encrypted " +
                "keystore. ArcX restarts as if freshly installed. There is no backup to " +
                "restore from — nothing was ever uploaded anywhere.",
            confirmLabel = "Delete everything",
            onConfirm = onDeleteAllLocalData,
            onDismiss = { confirmWipe = false },
        )
    }
}

@Composable
internal fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty().ifBlank { "unknown" }
    }

    SettingsScaffold(title = "About", onBack = onBack) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsGroup {
                SettingsRow(title = "ArcX", subtitle = "Version $version")
            }

            SectionHeader("Bring your own key")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(
                        "ArcX has no account, no subscription and no server of its own.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You connect your own AI provider with your own API key. The key is " +
                            "encrypted on this device and used only to talk to that provider. " +
                            "You see exactly what it costs, because you are billed by them " +
                            "directly, and you can revoke it at any time without asking us.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SectionHeader("Links")
            SettingsGroup {
                SettingsRow(
                    title = "Website and source",
                    subtitle = "Not published yet — links land with the first public build.",
                )
            }
            Spacer(Modifier.height(Spacing.Xxl))
        }
    }
}

private fun retentionLabel(retention: ScreenshotRetention): String = when (retention) {
    ScreenshotRetention.WEEK -> "Keep for a week"
    ScreenshotRetention.MONTH -> "Keep for a month"
    ScreenshotRetention.FOREVER -> "Keep until I delete them"
}

/**
 * Said next to the choice rather than once above it, because "older than this are deleted" is
 * a promise the FOREVER option does not make and must not appear to.
 */
private fun retentionNote(retention: ScreenshotRetention): String = when (retention) {
    ScreenshotRetention.FOREVER ->
        "Screenshots are kept until you delete them here or clear your history."
    else -> "Screenshots older than this are deleted automatically."
}

@Composable
private fun ConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onConfirm()
            }) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
